import Chisel._
import ConveyInterfaces._
import TidbitsOCM._
import TidbitsDMA._
import TidbitsRegFile._
import TidbitsStreams._

// TODO parametrize num of mem ports connected (also BRAM modules to inst.)
class BRAMTest() extends Personality(1) {
  // I/O is defined by the base class (Personality)

  // supported instruction values
  val instrFill = UInt(101, width = 64)
  val instrDump = UInt(102, width = 64)

  // register map
  val regindInstr = 0     // instruction selector
  val regindFillPtr = 1   // pointer to fill buffer
  val regindDumpPtr = 2   // pointer to dump buffer
  val regindDumpSum = 3   // sum of dump, for checking
  val regindCycles  = 4   // cycle counter, for benchmarking

  // instantiate and connect main (ops) register file
  val aegRegCount = 5
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

  val ocmc = Module(new OCMAndController(p, "ResultBRAM", true))
  // TODO make OCM user ports part of the test -- plugged to zero for now
  ocmc.io.ocmUser.req.addr := UInt(0)
  ocmc.io.ocmUser.req.writeData := UInt(0)
  ocmc.io.ocmUser.req.writeEn := Bool(false)

  ocmc.io.mcif.mode := UInt(0)
  ocmc.io.mcif.start := Bool(false)
  // connect fill port to memory read responses, with downsizer in between
  val ds = Module(new AXIStreamDownsizer(64, p.writeWidth))
  io.mem(0).rsp.ready := ds.io.in.ready
  ds.io.in.valid := io.mem(0).rsp.valid
  ds.io.in.bits := io.mem(0).rsp.bits.readData
  ocmc.io.mcif.fillPort <> ds.io.out
  // connect dump port to queue and reducer
  val dumpQ = Module(new Queue(UInt(width = p.readWidth), 16))
  val redFxn: (UInt,UInt)=>UInt = {(a,b) => a+b}
  val reducer = Module(new StreamReducer(p.readWidth, 0, redFxn))
  ocmc.io.mcif.dumpPort <> dumpQ.io.enq
  dumpQ.io.deq <> reducer.io.streamIn
  reducer.io.byteCount := UInt(p.bits/8)
  reducer.io.start := Bool(false)
  // connect dump sum register directly to reducer
  regFile.io.regIn(regindDumpSum).bits := reducer.io.reduced
  regFile.io.regIn(regindDumpSum).valid := reducer.io.finished

  // add request generator for fetching the fill buffer
  val reqgen = Module(new ReadReqGen(ConveyMemParams(), 0))
  reqgen.io.ctrl.start := Bool(false)
  reqgen.io.ctrl.throttle := Bool(false)
  reqgen.io.ctrl.baseAddr := regFile.io.regOut(regindFillPtr)
  reqgen.io.ctrl.byteCount := UInt(p.bits/8)
  // connect to mem port with adapter
  val reqadp = Module(new ConveyMemReqAdp(ConveyMemParams()))
  reqadp.io.genericReqIn <> reqgen.io.reqs
  reqadp.io.conveyReqOut <> io.mem(0).req

  // default outputs
  // no exceptions
  io.disp.exception := UInt(0)
  // do not accept instrs
  io.disp.instr.ready := Bool(false)
  // no memory flush request
  io.mem(0).flushReq := Bool(false)

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
        // give start signal to reqgen and mcif
        reqgen.io.ctrl.start := Bool(true)
        ocmc.io.mcif.mode := UInt(0)
        ocmc.io.mcif.start := Bool(true)
        // wait until mcif says it's done
        when (ocmc.io.mcif.done) {regState := sFinished}
      }

      is(sDump) {
        // give start signal to mcif and reducer
        ocmc.io.mcif.mode := UInt(1)
        ocmc.io.mcif.start := Bool(true)
        reducer.io.start := Bool(true)
        // wait until reducer says it's done
        when (reducer.io.finished) {regState := sFinished}
      }

      is(sFinished) {
        // write cycle count and go back to idle
        regFile.io.regIn(regindCycles).valid := Bool(true)
        regState := sIdle
      }
  }
}
