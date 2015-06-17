import Chisel._
import ConveyInterfaces._

class RegFile(numRegs: Int, idBits: Int, dataBits: Int) extends Module {
  val io = new Bundle {
    // external command interface
    val extIF = new RegFileSlaveIF(idBits, dataBits)
    // exposed values of all registers, for internal use
    val regOut = Vec.fill(numRegs) { UInt(OUTPUT, width = dataBits) }
    // internal write port (takes priority over extIF)
    val regWrInd = UInt(INPUT, width = idBits)
    val regWrEn  = Bool(INPUT)
    val regWrData = UInt(INPUT, dataBits)
  }
  // drive num registers to compile-time constant
  io.extIF.regCount := UInt(numRegs)

  // instantiate the registers in the file
  val regFile = Vec.fill(numRegs) { Reg(init = UInt(0, width = dataBits)) }

  // latch the incoming commands
  val regCommand = Reg(next = io.extIF.cmd.bits)
  val regDoCmd = Reg(init = Bool(false), next = io.extIF.cmd.valid)

  val hasReadCommand = (regDoCmd && regCommand.read)
  val hasWriteCommand = (regDoCmd && regCommand.write)

  // register read logic
  io.extIF.readData.valid := hasReadCommand
  // make sure regID stays within range for memory read
  when (regCommand.regID < UInt(numRegs)) {
    io.extIF.readData.bits  := regFile(regCommand.regID)
  } .otherwise {
    // return 0 otherwise
    io.extIF.readData.bits  := UInt(0)
  }

  // register write logic
  // single-port writes: "internal" write port (regWr*) takes priority over
  // extIF if both access simultaneously
  when (io.regWrEn) {
    regFile(io.regWrInd) := io.regWrData
  } .elsewhen (hasWriteCommand) {
    regFile(regCommand.regID) := regCommand.writeData
  }

  // expose all reg outputs
  for (i <- 0 to numRegs-1) {
    io.regOut(i) := regFile(i)
  }

  // TODO add testbench for regfile logic
}
