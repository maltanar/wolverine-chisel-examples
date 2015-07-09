import Chisel._
import ConveyInterfaces._
import TidbitsOCM._
import TidbitsDMA._
import TidbitsRegFile._
import TidbitsStreams._

class BRAMTestPipeIF(p: OCMParameters) extends Bundle {
  val startFill = Bool(INPUT)
  val startDump = Bool(INPUT)
  val done = Bool(OUTPUT)
  val baseAddr = UInt(INPUT, width = 64)
  val dumpSum = UInt(OUTPUT, width = p.readWidth)
  val dumpBaseAddr = UInt(INPUT, width = 64)
  val mem = new MemMasterIF()
}

class BRAMTestPipe(p: OCMParameters) extends Module {
  val io = new BRAMTestPipeIF(p)

  val ocmc = Module(new OCMAndController(p, "ResultBRAM", true))
  // TODO make OCM user ports part of the test -- plugged to zero for now
  for(i <- 0 until 2) {
    ocmc.io.ocmUser(i).req := NullOCMRequest(p)
  }

  ocmc.io.mcif.mode := Mux(io.startDump, UInt(1), UInt(0))
  ocmc.io.mcif.start := io.startFill | io.startDump
  val mp = ConveyMemParams()

  // instantiate write generator
  val wrqgen = Module(new WriteReqGen(mp, 1))
  wrqgen.io.ctrl.start := io.startDump
  wrqgen.io.ctrl.throttle := Bool(false)
  wrqgen.io.ctrl.baseAddr := io.dumpBaseAddr
  // use 2x the number of writes since we don't have an upsizer right now
  wrqgen.io.ctrl.byteCount := UInt(2*p.bits/8)

  // instantiate response adapter and deinterleaver
  val rspad = Module(new ConveyMemRspAdp(mp))
  val deint = Module(new QueuedDeinterleaver(2, mp, 4))
  rspad.io.conveyRspIn <> io.mem.rsp
  rspad.io.genericRspOut <> deint.io.rspIn
  // just consume all write responses (to channel 1)
  deint.io.rspOut(1).ready := Bool(true)
  // add a little queue to break combinational path
  val fillQ = Module(new Queue(UInt(width=64), 8))
  fillQ.io.enq.valid := deint.io.rspOut(0).valid
  fillQ.io.enq.bits := deint.io.rspOut(0).bits.readData
  deint.io.rspOut(0).ready := fillQ.io.enq.ready

  // connect fill port to channel 0 responses, with downsizer in between
  val ds = Module(new AXIStreamDownsizer(64, p.writeWidth))
  fillQ.io.deq <> ds.io.in
  ocmc.io.mcif.fillPort <> ds.io.out

  // connect dump port to queue
  // queue feed the data write port of the req adapter plus a reducer
  val dumpQ = Module(new Queue(UInt(width = p.readWidth), 16))
  val redFxn: (UInt,UInt)=>UInt = {(a,b) => a+b}
  val reducer = Module(new StreamReducer(p.readWidth, 0, redFxn))
  ocmc.io.mcif.dumpPort <> dumpQ.io.enq

  // add request generator for fetching the fill buffer
  val reqgen = Module(new ReadReqGen(mp, 0))
  reqgen.io.ctrl.start := io.startFill
  reqgen.io.ctrl.throttle := Bool(false)
  reqgen.io.ctrl.baseAddr := io.baseAddr
  reqgen.io.ctrl.byteCount := UInt(p.bits/8)
  // interleaver for mixing r/w requests
  val ilv = Module(new ReqInterleaver(2, mp))
  ilv.io.reqIn(0) <> reqgen.io.reqs
  ilv.io.reqIn(1) <> wrqgen.io.reqs
  // connect to mem port with adapter
  val routeWrites = {x: UInt => (x-UInt(1))}
  val reqadp = Module(new ConveyMemReqAdp(mp, 1, routeWrites))
  reqadp.io.genericReqIn <> ilv.io.reqOut
  reqadp.io.writeData(0) <> dumpQ.io.deq
  reqadp.io.conveyReqOut <> io.mem.req

  // reducer only moves forward when dump writes move forward
  reducer.io.streamIn.valid := dumpQ.io.deq.valid & reqadp.io.writeData(0).ready
  reducer.io.streamIn.bits := dumpQ.io.deq.bits
  reducer.io.byteCount := UInt(p.bits/8)
  reducer.io.start := io.startDump
  // connect dump sum register directly to reducer
  io.dumpSum := reducer.io.reduced

  val fillDone = (io.startFill & ocmc.io.mcif.done)
  val dumpDone = (io.startDump & wrqgen.io.stat.finished)
  io.done := fillDone | dumpDone

  io.mem.flushReq := Bool(false)
}

class BRAMTest(numPipes: Int) extends Personality(numPipes) {
  // I/O is defined by the base class (Personality)

  // supported instruction values
  val instrFill = UInt(101, width = 64)
  val instrDump = UInt(102, width = 64)

  // register map
  val regindInstr = 0     // instruction selector
  val regindCycles  = 1   // cycle counter, for benchmarking

  def regindFillPtr(i: Int) = {2 + i*3 + 0}
  def regindDumpPtr(i: Int) = {2 + i*3 + 1}
  def regindDumpSum(i: Int) = {2 + i*3 + 2}

  // instantiate and connect main (ops) register file
  val aegRegCount = 2 + 3*numPipes
  val regFile = Module(new RegFile(aegRegCount, 18, 64))
  regFile.io.extIF <> io.disp.aeg
  // plug AEG regfile write enables, will be set when needed
  for(i <- 0 until aegRegCount) {
    regFile.io.regIn(i).valid := Bool(false)
  }
  // plug CSR outputs, unused for this design
  io.csr.readData.bits := UInt(0)
  io.csr.readData.valid := Bool(false)
  io.csr.regCount := UInt(0)

  val p = new OCMParameters(math.pow(2,20).toInt, 32, 1, 2, 3)

  val pipes = Vec.fill(numPipes) { Module(new BRAMTestPipe(p)).io}
  val allFinished = pipes.forall({x: BRAMTestPipeIF => x.done})
  val regAllStartFill = Reg(init = Bool(false))
  val regAllStartDump = Reg(init = Bool(false))

  for(i <- 0 until numPipes) {
    pipes(i).mem <> io.mem(i)
    pipes(i).startFill := regAllStartFill
    pipes(i).startDump := regAllStartDump
    pipes(i).baseAddr := regFile.io.regOut( regindFillPtr(i) )
    pipes(i).dumpBaseAddr := regFile.io.regOut( regindDumpPtr(i) )
    regFile.io.regIn( regindDumpSum(i) ).bits := pipes(i).dumpSum
    regFile.io.regIn( regindDumpSum(i) ).valid := pipes(i).done
  }


  // default outputs
  // no exceptions
  io.disp.exception := UInt(0)
  // do not accept instrs
  io.disp.instr.ready := Bool(false)

  // instruction logic for two instructions:
  // - fill BRAM
  // - dump BRAM and sum contents

  // instruction dispatch logic
  val sIdle :: sDecode :: sFill :: sDump :: sFinished :: Nil = Enum(UInt(), 5)
  val regState = Reg(init = UInt(sIdle))

  // cycle count will be written when finished
  val regCycleCount = Reg(init = UInt(0, width = 64))
  regFile.io.regIn(regindCycles).bits := regCycleCount
  when (regState != sIdle) { regCycleCount := regCycleCount + UInt(1) }

  switch(regState) {
      is(sIdle) {
        // accept instruction
        regAllStartFill := Bool(false)
        regAllStartDump := Bool(false)
        io.disp.instr.ready := Bool(true)
        when (io.disp.instr.valid) {
          regState := sDecode
          regCycleCount := UInt(0)
        }
      }

      is(sDecode) {
        when (regFile.io.regOut(regindInstr) === instrFill) {regState := sFill}
        .elsewhen (regFile.io.regOut(regindInstr) === instrDump) {regState := sDump}
        .otherwise {io.disp.exception := UInt(1) }
      }

      is(sFill) {
        // give start signal for fill
        regAllStartFill := Bool(true)
        // wait until done
        when (allFinished) {regState := sFinished}
      }

      is(sDump) {
        // give start signal for fill
        regAllStartDump := Bool(true)
        // wait until done
        when (allFinished) {regState := sFinished}
      }

      is(sFinished) {
        // write cycle count and go back to idle
        regFile.io.regIn(regindCycles).valid := Bool(true)
        regState := sIdle
      }
  }
}
