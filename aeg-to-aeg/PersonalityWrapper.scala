package AEGtoAEG

import Chisel._
import ConveyInterfaces._

class PersonalityWrapper(numMemPorts: Int) extends Module {
  // the Convey wrapper itself always expects at least one memory port
  // if no mem ports are desired, we still create one and drive outputs to 0
  val numCalculatedMemPorts = if(numMemPorts == 0) 1 else numMemPorts
  val io = new TopWrapperInterface(numCalculatedMemPorts)

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
  io.dispStall := !persDispatch.instr.ready

  // handle the no memory ports case by driving a single port to zero
  if (numMemPorts == 0) {
    println("====> Zero memory ports specified - instantiating one and driving to zero")
    println("====> Remember to set NUM_MC_PORTS=1 in cae_pers.v")
    io.mcReqValid := UInt(0)
    io.mcReqRtnCtl := UInt(0)
    io.mcReqData := UInt(0)
    io.mcReqAddr := UInt(0)
    io.mcReqSize := UInt(0)
    io.mcReqCmd := UInt(0)
    io.mcReqSCmd := UInt(0)
    io.mcResStall := UInt(0)
    io.mcReqFlush := UInt(0)
  }
  // TODO add proper memory port connections
}
