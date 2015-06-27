import Chisel._
import ConveyInterfaces._

class RegFile(numRegs: Int, idBits: Int, dataBits: Int) extends Module {
  val io = new Bundle {
    // external command interface
    val extIF = new RegFileSlaveIF(idBits, dataBits)
    // exposed values of all registers, for internal use
    val regOut = Vec.fill(numRegs) { UInt(OUTPUT, width = dataBits) }
    // valid pipes for writing new values for all registers, for internal use
    // (extIF takes priority over this)
    val regIn = Vec.fill(numRegs) { Valid(UInt(width = dataBits)).flip }
  }
  // drive num registers to compile-time constant
  io.extIF.regCount := UInt(numRegs)

  // instantiate the registers in the file
  val regFile = Vec.fill(numRegs) { Reg(init = UInt(0, width = dataBits)) }

  // latch the incoming commands
  val regCommand = Reg(next = io.extIF.cmd.bits)
  val regDoCmd = Reg(init = Bool(false), next = io.extIF.cmd.valid)

  val hasExtReadCommand = (regDoCmd && regCommand.read)
  val hasExtWriteCommand = (regDoCmd && regCommand.write)

  // register read logic
  io.extIF.readData.valid := hasExtReadCommand
  // make sure regID stays within range for memory read
  when (regCommand.regID < UInt(numRegs)) {
    io.extIF.readData.bits  := regFile(regCommand.regID)
  } .otherwise {
    // return 0 otherwise
    io.extIF.readData.bits  := UInt(0)
  }

  // register write logic
  // to avoid multiple ports, we prioritize the extIF writes over the internal
  // ones (e.g if there is an external write present, the internal write will
  // be ignored if it arrives simultaneously)
  when (hasExtWriteCommand) {
    regFile(regCommand.regID) := regCommand.writeData
  } .otherwise {
    for(i <- 0 until numRegs) {
      when (io.regIn(i).valid) { regFile(i) := io.regIn(i).bits }
    }
  }

  // expose all reg outputs for personality's access
  for (i <- 0 to numRegs-1) {
    io.regOut(i) := regFile(i)
  }

  // TODO add testbench for regfile logic
}
