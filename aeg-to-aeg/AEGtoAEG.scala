import Chisel._
import ConveyInterfaces._

class AEGtoAEG(numMemPorts: Int) extends Personality(numMemPorts) {
  val aegRegCount = 4
  val csrRegCount = 4
  // instantiate and connect main (ops) register file
  val regFile = Module(new RegFile(aegRegCount, 18, 64))
  regFile.io.extIF <> io.disp.aeg
  // instantiate and connect the CSR register file
  val csrRegFile = Module(new RegFile(csrRegCount, 16, 64))
  csrRegFile.io.extIF <> io.csr

  // register to sum values into
  val regSum = Reg(init = UInt(0,64))
  // register to keep track of op count
  val regOpCount = Reg(init = UInt(0,64))

  // no exceptions
  io.disp.exception := UInt(0)
  // do not accept instrs by default
  io.disp.instr.ready := Bool(false)

  // ops regfile internal write port
  val regIndSum = 2
  val regIndOpCount = 3
  def enableWrite(ind: Int) = { regFile.io.regIn(ind).valid := Bool(true)}
  regFile.io.regIn(regIndSum).bits := regSum
  regFile.io.regIn(regIndOpCount).bits := regOpCount

  // plug ops regfile internal write enables by default (set when needed)
  for(i <- 0 until aegRegCount) {
    regFile.io.regIn(i).valid := Bool(false)
  }

  // plug csr regfile internal write ports, we don't use them in this example
  for(i <- 0 until csrRegCount) {
    csrRegFile.io.regIn(i).valid := Bool(false)
    csrRegFile.io.regIn(i).bits := UInt(0)
  }


  // control state machine
  val sIdle :: sRead :: sWrite :: Nil = Enum(UInt(), 3)
  val regState = Reg(init = UInt(sIdle))

  switch(regState) {
      is(sIdle) {
        // ready to accept instruction
        io.disp.instr.ready := Bool(true)

        when(io.disp.instr.valid) {
          // fixed instruction (don't look at opcode for now)
          regState := sRead
          regOpCount := regOpCount + UInt(1)
        }
      }

      is(sRead) {
        // sum registers 0 and 1
        regSum := regFile.io.regOut(0) + regFile.io.regOut(1)
        regState := sWrite
      }

      is(sWrite) {
        // write results
        enableWrite(regIndSum)
        enableWrite(regIndOpCount)
        // go back to idle
        regState := sIdle
      }
  }
}
