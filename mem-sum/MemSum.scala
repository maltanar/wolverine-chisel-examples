import Chisel._
import ConveyInterfaces._

class MemSum(numMemPorts: Int) extends Personality(numMemPorts) {
  // I/O is defined by the base class (Personality)

  // instantiate and connect main (ops) register file
  val regFile = Module(new RegFile(4, 18, 64))
  regFile.io.extIF <> io.disp.aeg
  // instantiate and connect the CSR register file
  val csrRegFile = Module(new RegFile(4, 16, 64))
  csrRegFile.io.extIF <> io.csr

  // give names to registers for readability
  val paramBasePtr = regFile.io.regOut(0)
  val paramElemCnt = regFile.io.regOut(1)

  // internal registers
  val regBase = Reg(init = UInt(0, 48))
  val regElemsPerPipe = Reg(init = UInt(0, 64))
  val regCycleCount = Reg(init = UInt(0, 64))

  // instantiate the actual processing elements/pipelines
  val pipes = Vec.fill(numMemPorts) { Module(new MemSumPipe()).io }
  val regPipeStart = Reg(init = Bool(false))
  for (i <- 0 to numMemPorts-1) {
    // connect memory port
    io.mem(i) <> pipes(i).mem
    pipes(i).start := regPipeStart
    // pipe init logic
    // set base ptr and count regs per pipe
    pipes(i).base := regBase + UInt(i) * regElemsPerPipe
    pipes(i).count := regElemsPerPipe
  }
  val allPipesFinished = pipes.forall( (pipe: MemSumPipeIF) => (pipe.done) )

  // default outputs
  // no exceptions
  io.disp.exception := UInt(0)
  // do not accept instrs by default
  io.disp.instr.ready := Bool(false)

  // ops regfile internal write port
  regFile.io.regWrInd := UInt(2)
  regFile.io.regWrData := pipes(0).sum // TODO sum all pipes instead
  regFile.io.regWrEn := Bool(false)

  // csr regfile internal write port
  csrRegFile.io.regWrInd := UInt(0)
  csrRegFile.io.regWrData := regCycleCount
  csrRegFile.io.regWrEn := Bool(false)

  val sIdle :: sDispatch :: sWait :: sFinish :: Nil = Enum(UInt(), 4)
  val regState = Reg(init = UInt(sIdle))

  switch(regState) {
      is(sIdle) {
        // accept instructions
        io.disp.instr.ready := Bool(true)
        regPipeStart := Bool(false)
        when(io.disp.instr.valid) {
          regState := sDispatch
        }
      }

      is(sDispatch) {
        // reset cycle counter
        regCycleCount := UInt(0)
        // latch in settings
        regBase := paramBasePtr
        regElemsPerPipe := paramElemCnt / UInt(numMemPorts)
        // per-pipe settings will be calculated and distributed by the
        // pipe init logic
        regState := sWait
      }

      is(sWait) {
        // activate pipes
        regPipeStart := Bool(true)
        // wait until all pipes are finished
        when (allPipesFinished) { regState := sFinish }
        // keep cycle count for BW statistics
        regCycleCount := regCycleCount + UInt(1)
        csrRegFile.io.regWrEn := Bool(true)
      }

      is(sFinish) {
        // write result to register file
        regFile.io.regWrEn := Bool(true)
        // back to idle
        regState := sIdle
      }
  }


  // TODO add instruction dispatch logic
  // TODO return result
  // TODO add cycle counter
  // TODO add some debug support, synthesize + test single pipe
  // TODO global sum from several pipes + test
}
