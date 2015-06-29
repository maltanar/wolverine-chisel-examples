import Chisel._
import ConveyInterfaces._
import TidbitsRegFile._

class MemSum(numMemPorts: Int) extends Personality(numMemPorts) {
  // I/O is defined by the base class (Personality)
  val aegRegCount = 4
  val csrRegCount = 4

  // instantiate and connect main (ops) register file
  val regFile = Module(new RegFile(aegRegCount, 18, 64))
  regFile.io.extIF <> io.disp.aeg
  // instantiate and connect the CSR register file
  val csrRegFile = Module(new RegFile(csrRegCount, 16, 64))
  csrRegFile.io.extIF <> io.csr

  // give names to registers for readability
  val paramBasePtr = regFile.io.regOut(0)
  val paramElemCnt = regFile.io.regOut(1)

  // internal registers
  val regBase = Reg(init = UInt(0, 48))
  val regElemsPerPipe = Reg(init = UInt(0, 64))
  val regCycleCount = Reg(init = UInt(0, 64))
  val regSum = Reg(init = UInt(0, 64))
  val regSumIndex = Reg(init = UInt(0, 8))

  // instantiate the actual processing elements/pipelines
  val pipes = Vec.fill(numMemPorts) { Module(new MemSumPipe()).io }
  val regPipeStart = Reg(init = Bool(false))
  for (i <- 0 to numMemPorts-1) {
    // connect memory port
    io.mem(i) <> pipes(i).mem
    pipes(i).start := regPipeStart
    // pipe init logic
    // set base ptr and count regs per pipe
    pipes(i).base := regBase + (UInt(i*8) * regElemsPerPipe)
    pipes(i).count := regElemsPerPipe
  }
  val allPipesFinished = pipes.forall( (pipe: MemSumPipeIF) => (pipe.done) )

  // default outputs
  // no exceptions
  io.disp.exception := UInt(0)
  // do not accept instrs by default
  io.disp.instr.ready := Bool(false)

  // plug ops regfile internal write port by default
  for(i <- 0 until aegRegCount) {
    regFile.io.regIn(i).valid := Bool(false)
  }
  // route result data to register write ports
  // write enables will be set when we are finished
  val regIndSum = 2
  val regIndCycleCount = 3
  def enableWrite(ind: Int) = { regFile.io.regIn(ind).valid := Bool(true)}
  regFile.io.regIn(regIndSum).bits := regSum
  regFile.io.regIn(regIndCycleCount).bits := regCycleCount

  // plug csr regfile internal write ports, we don't use them in this example
  for(i <- 0 until csrRegCount) {
    csrRegFile.io.regIn(i).valid := Bool(false)
    csrRegFile.io.regIn(i).bits := UInt(0)
  }

  val sIdle :: sDispatch :: sWait :: sAccumulateSums :: sFinish :: Nil = Enum(UInt(), 5)
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
        when (allPipesFinished) {
          regState := sAccumulateSums
          // zero sum and sum read index registers
          regSum := UInt(0)
          regSumIndex := UInt(0)
        }
        // keep cycle count for BW statistics
        regCycleCount := regCycleCount + UInt(1)
      }

      is(sAccumulateSums) {
        // go to sFinish when all pipes have been summed
        when (regSumIndex === UInt(numMemPorts)) { regState := sFinish}
        .otherwise {
          // accumulate sum from next pipe otherwise
          regSum := regSum + pipes(regSumIndex).sum
          regSumIndex := regSumIndex + UInt(1)
        }
      }

      is(sFinish) {
        // write results to register file
        enableWrite(regIndSum)
        enableWrite(regIndCycleCount)
        // back to idle
        regState := sIdle
      }
  }

  // TODO add better debug support
}
