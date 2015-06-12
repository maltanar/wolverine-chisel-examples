package AEGtoAEG

import Chisel._
import ConveyInterfaces._

class AEGtoAEG() extends Module {
  val io = new Bundle {
    val disp = new DispatchSlaveIF()
    val csr  = new RegFileSlaveIF(16, 64)
  }

  // instantiate and connect main (ops) register file
  val regFile = Module(new RegFile(4, 18, 64))
  regFile.io.extIF <> io.disp.aeg
  // instantiate and connect the CSR register file
  val csrRegFile = Module(new RegFile(4, 16, 64))
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
  regFile.io.regWrInd := UInt(2)
  regFile.io.regWrData := regSum
  regFile.io.regWrEn := Bool(false)

  // csr regfile internal write port
  csrRegFile.io.regWrInd := UInt(0)
  csrRegFile.io.regWrData := regOpCount
  csrRegFile.io.regWrEn := Bool(false)

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
        // write result (into register 2, addr hardcoded)
        regFile.io.regWrEn := Bool(true)
        // also update CSR (regOpCount)
        csrRegFile.io.regWrEn := Bool(true)
        // go back to idle
        regState := sIdle
      }
  }
}
