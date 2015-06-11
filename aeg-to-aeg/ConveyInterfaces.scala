package ConveyInterfaces

import Chisel._
import Literal._
import Node._

// command bundle for read/writes to AEG registers
class RegCommand() extends Bundle {
  val regID     = UInt(width = 18)
  val read      = Bool()
  val write     = Bool()
  val writeData = UInt(width = 64)

  override def clone = { new RegCommand().asInstanceOf[this.type] }
}

// register file interface
class AEGSlaveIF() extends Bundle {
  // register read/write commands
  // the "valid" signal here should be connected to (.read OR .write)
  val cmd         = ValidIO(new RegCommand()).flip
  // returned read data
  val readData    = ValidIO(UInt(width = 64))
  // number of registers
  val regCount    = UInt(OUTPUT, width = 18)

  override def clone = { new AEGSlaveIF().asInstanceOf[this.type] }
}

// dispatch slave interface
// for accepting instructions and AEG register operations
class DispatchSlaveIF() extends Bundle {
  // instruction opcode
  // note that this interface is defined as stall-valid instead of ready-valid
  // by Convey, so the read signal should be inverted from stall
  val instr       = Decoupled(UInt(width = 4)).flip
  val aeg         = new AEGSlaveIF()

  override def clone = { new DispatchSlaveIF().asInstanceOf[this.type] }
}
