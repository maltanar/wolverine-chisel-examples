import Chisel._
import ConveyInterfaces._

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
  val reducers = Vec.fill(chans) { Module(new StreamReducer(64, 0, (_+_)).io }

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
    reqGen(i).reqs <> intl.io.reqIn(i)
    // TODO add response adapter in-between here
    deintl.io.rspOut(i) <> reduceQ(i).enq
    reduceQ(i).deq <> reducers(i).streamIn
  }
  // AND-reduce all channels' reducers to generate done
  io.done := reducers.forall(rg => rg.finished)
  // TODO make adapter and connect interleaver to memory port
  // connect memory port responses to response queue
  io.mem.rsp <> rspQ.io.enq
  // connect response queue to deinterleaver
  rspQ.io.deq <> deintl.io.rspIn

  // default outputs
  // no memory flushes necessary
  io.mem.flushReq := Bool(false)
}
