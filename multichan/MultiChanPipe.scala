import Chisel._
import ConveyInterfaces._
import TidbitsDMA._
import TidbitsStreams._

class MultiChanPipeIF(chans: Int) extends Bundle {
  val start = Bool(INPUT)               // start signal
  val done  = Bool(OUTPUT)              // finished

  val chanBase = Vec.fill(chans) {UInt(INPUT, width = 64)}
  val chanByteCount = Vec.fill(chans) {UInt(INPUT, width = 64)}
  val chanSum = Vec.fill(chans) {UInt(OUTPUT, width = 64)}

  val mem = new MemMasterIF()           // memory port
}

class MultiChanPipe(p: MemReqParams, chans: Int) extends Module {
  val io = new MultiChanPipeIF(chans)

  // instantiate the request generators
  val reqGen = Vec.tabulate(chans) { i => Module(new ReadReqGen(p, i)).io }
  val intl = Module(new ReqInterleaver(chans, p))
  val deintl = Module(new RespDeinterleaver(chans, p))
  val rspQ = Module(new Queue(new GenericMemoryResponse(p), 16))
  val reduceQ = Vec.fill(chans) { Module(new Queue(UInt(width=64), 8)).io }
  val redFxn: (UInt,UInt)=>UInt = {(a,b)=>a+b}
  val reducers = Vec.fill(chans) { Module(new StreamReducer(64, 0, redFxn)).io }

  for(i <- 0 until chans) {
    // control to request generators
    reqGen(i).ctrl.start := io.start
    reqGen(i).ctrl.throttle := Bool(false)
    reqGen(i).ctrl.baseAddr := io.chanBase(i)
    reqGen(i).ctrl.byteCount := io.chanByteCount(i)
    // control to reducers (response handlers)
    reducers(i).start := io.start
    reducers(i).byteCount := io.chanByteCount(i)
    // status from reducers
    io.chanSum(i) := reducers(i).reduced
    // link data streams
    // req. gens to interleaver
    reqGen(i).reqs <> intl.io.reqIn(i)
    // deinterleaver to reduce queues
    reduceQ(i).enq.valid := deintl.io.rspOut(i).valid
    reduceQ(i).enq.bits := deintl.io.rspOut(i).bits.readData
    deintl.io.rspOut(i).ready := reduceQ(i).enq.ready
    // reduce queues to reducers
    reduceQ(i).deq <> reducers(i).streamIn
  }
  // AND-reduce all channels' reducers to generate done
  io.done := reducers.forall(rg => rg.finished)

  // install req adapter between interleaver and memory port
  val adpReq = Module(new ConveyMemReqAdp(p))
  adpReq.io.genericReqIn <> intl.io.reqOut
  adpReq.io.conveyReqOut <> io.mem.req

  // install resp adapter between memory port and response queue
  val adpResp = Module(new ConveyMemRspAdp(p))
  adpResp.io.conveyRspIn <> io.mem.rsp
  adpResp.io.genericRspOut <> rspQ.io.enq

  // connect response queue to deinterleaver
  rspQ.io.deq <> deintl.io.rspIn

  // default outputs
  // no memory flushes necessary
  io.mem.flushReq := Bool(false)
}
