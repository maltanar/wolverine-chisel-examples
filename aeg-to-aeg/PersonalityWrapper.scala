package AEGtoAEG

import Chisel._
import ConveyInterfaces._

class PersonalityWrapper() extends Module {
  val io = new TopWrapperInterface()

  io.renameSignals()

  // TODO can we directly parametrize this?
  val pers = Module(new AEGtoAEG())
  val persDispatch = pers.io.disp
  val persCSR = pers.io.csr

  // most wrapper signals c
  persDispatch.instr.valid := io.dispInstValid
  persDispatch.instr.bits := io.dispInstData
  persDispatch.aeg.cmd.bits.regID := io.dispRegID
  persDispatch.aeg.cmd.bits.read := io.dispRegRead
  persDispatch.aeg.cmd.bits.write := io.dispRegWrite
  persDispatch.aeg.cmd.bits.writeData := io.dispRegWrData

  io.dispAegCnt := persDispatch.aeg.regCount
  io.dispException := persDispatch.exception
  io.dispIdle := persDispatch.instr.ready
  io.dispRtnValid := persDispatch.aeg.readData.valid
  io.dispRtnData := persDispatch.aeg.readData.bits

  persCSR.cmd.bits.write := io.csrWrValid
  persCSR.cmd.bits.read := io.csrRdValid
  persCSR.cmd.bits.regID := io.csrAddr
  persCSR.cmd.bits.writeData := io.csrWrData

  io.csrReadAck := persCSR.readData.valid
  io.csrReadData := persCSR.readData.bits

  // a few signals need simple logic adapters
  // valid == read | write for reg write interfaces
  persDispatch.aeg.cmd.valid := io.dispRegRead || io.dispRegWrite
  persCSR.cmd.valid := io.csrRdValid || io.csrWrValid
  // stall = !ready for the instr dispatch
  io.disp_stall := !persDispatch.instr.ready

  // TODO add memory port connections
}
