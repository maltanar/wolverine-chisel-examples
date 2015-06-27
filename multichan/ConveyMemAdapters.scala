import Chisel._
import ConveyInterfaces._
import GenericMemReqRsp._

// TODO update adapter to also work for writes
class ConveyMemReqAdp(p: MemReqParams) extends Module {
  val io = new Bundle {
    val genericReqIn = Decoupled(new GenericMemoryRequest(p)).flip
    val conveyReqOut = Decoupled(new MemRequest(32, 48, 64))
  }

  io.conveyReqOut.valid := io.genericReqIn.valid
  io.genericReqIn.ready := io.conveyReqOut.ready

  io.conveyReqOut.bits.rtnCtl := io.genericReqIn.bits.channelID
  io.conveyReqOut.bits.writeData := UInt(0)
  io.conveyReqOut.bits.addr := io.genericReqIn.bits.addr
  io.conveyReqOut.bits.size := UInt( log2Up(p.dataWidth/8) )
  io.conveyReqOut.bits.scmd := UInt(0)

  if(p.dataWidth != 64) {
    println("ConveyMemReqAdp requires p.dataWidth=64")
  } else {
    if (p.beatsPerBurst == 8) {
      io.conveyReqOut.bits.cmd := UInt(7)
    } else if (p.beatsPerBurst == 1) {
      io.conveyReqOut.bits.cmd := UInt(1)
    } else {
      println("Unsupported number of burst beats!")
    }
  }
}

class ConveyMemRspAdp(p: MemReqParams) extends Module {
  val io = new Bundle {
    val conveyRspIn = Decoupled(new MemResponse(32, 64)).flip
    val genericRspOut = Decoupled(new GenericMemoryResponse(p))
  }

  io.conveyRspIn.ready := io.genericRspOut.ready
  io.genericRspOut.valid := io.conveyRspIn.valid

  io.genericRspOut.bits.channelID := io.conveyRspIn.bits.rtnCtl
  io.genericRspOut.bits.readData := io.conveyRspIn.bits.readData
  // TODO carry cmd and scmd, if needed
  io.genericRspOut.bits.metaData := UInt(0)
}
