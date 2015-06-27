import Chisel._
import ConveyInterfaces._

class MultiChanMemSum(numMemPorts: Int, val chansPerPort: Int) extends Personality(numMemPorts) {
  // I/O is defined by the base class (Personality)

  // each channel needs 3 registers: base, byte count, sum
  // allocate 1 extra register for reporting cycle count
  val aegRegCount = 1 + numMemPorts * chansPerPort * 3

  // instantiate and connect main (ops) register file
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

  // TODO move this defn. to somewhere general (ConveyInterfaces?)
  val p = new MemReqParams(48, 64, 4, 1, 8)

  val pipes = Vec.fill(numMemPorts) { Module(new MultiChanPipe(p, chansPerPort)).io }
  val regPipeStart = Reg(init = Bool(false))

  for (i <- 0 to numMemPorts-1) {
    // connect memory port
    io.mem(i) <> pipes(i).mem
    pipes(i).start := regPipeStart
    // each channels gets 3 regs (base, count, sum)
    // calculate register base for this pipe
    val pipeRegBase = 1 + i*chansPerPort*3
    for(j <- 0 until chansPerPort) {
      val chanRegBase = pipeRegBase + 3*j
      pipes(i).chanBase(j) := regFile.io.regOut(chanRegBase + 0)
      pipes(i).chanByteCount(j) := regFile.io.regOut(chanRegBase + 1)
      regFile.io.regIn(chanRegBase + 2).bits := pipes(i).chanSum(j)
      // supply sum register write enables from pipe done signal
      regFile.io.regIn(chanRegBase + 2).valid := pipes(i).done
    }
  }
  val allPipesFinished = pipes.forall( {pipe: MultiChanPipeIF => pipe.done} )

  // default outputs
  // no exceptions
  io.disp.exception := UInt(0)
  // do not accept instrs
  io.disp.instr.ready := Bool(false)

  // instruction dispatch logic
  val sIdle :: sWait :: sFinished :: Nil = Enum(UInt(), 3)
  val regState = Reg(init = UInt(sIdle))

  // cycle count will be written to register 0 when finished
  val regCycleCount = Reg(init = UInt(0, width = 64))
  regFile.io.regIn(0).bits := regCycleCount

  switch(regState) {
      is(sIdle) {
        regPipeStart := Bool(false)
        io.disp.instr.ready := Bool(true)
        when (io.disp.instr.valid) {
          regState := sWait
          regCycleCount := UInt(0)
        }
      }

      is(sWait) {
        regPipeStart := Bool(true)
        regCycleCount := regCycleCount + UInt(1)
        when (allPipesFinished) { regState := sFinished}
      }

      is(sFinished) {
        // write cycle count and go back to idle
        regFile.io.regIn(0).valid := Bool(true)
        regState := sIdle
      }
  }
}
