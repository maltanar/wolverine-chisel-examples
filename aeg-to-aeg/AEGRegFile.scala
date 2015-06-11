package AEGtoAEG

import Chisel._
import ConveyInterfaces._

class AEGRegFile(numRegs: Int) extends Module {
  val io = new Bundle {
    val aegIF = new AEGSlaveIF()
  }
  // drive num registers to compile-time constant
  io.aegIF.regCount := UInt(numRegs)

  // instantiate the registers
  val regFile = Vec.fill(numRegs) { Reg(init = UInt(0, width = 64)) }

  val hasReadCommand = (io.aegIF.cmd.valid && io.aegIF.cmd.bits.read)
  val hasWriteCommand = (io.aegIF.cmd.valid && io.aegIF.cmd.bits.write)

  // TODO does this work as expected?
  val regCommand = Reg(next = io.aegIF.cmd.bits)

  // TODO implement and test regfile logic
}
