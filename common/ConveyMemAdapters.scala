import Chisel._
import ConveyInterfaces._
import TidbitsDMA._

class ConveyMemReqAdp(p: MemReqParams, numWriteChans: Int, routeFxn: UInt => UInt) extends Module {
  val io = new Bundle {
    val genericReqIn = Decoupled(new GenericMemoryRequest(p)).flip
    val conveyReqOut = Decoupled(new MemRequest(32, 48, 64))
    val writeData = Vec.fill(numWriteChans) {Decoupled(UInt(width = p.dataWidth)).flip}
  }
  if(p.dataWidth != 64) {
    println("ConveyMemReqAdp requires p.dataWidth=64")
    System.exit(-1)
  }

  // default outputs
  io.genericReqIn.ready := Bool(false)
  io.conveyReqOut.valid := Bool(false)
  io.conveyReqOut.bits.rtnCtl := io.genericReqIn.bits.channelID
  io.conveyReqOut.bits.addr := io.genericReqIn.bits.addr
  io.conveyReqOut.bits.size := UInt( log2Up(p.dataWidth/8) )
  // TODO scmd needs to be set for write bursts
  io.conveyReqOut.bits.scmd := UInt(0)
  io.conveyReqOut.bits.cmd := UInt(0)

  // plug write data ready signals
  for(i <- 0 until numWriteChans) {
    io.writeData(i).ready := Bool(false)
  }
  // write must have both request and data ready
  val src = routeFxn(io.genericReqIn.bits.channelID)
  val validWrite = io.genericReqIn.valid & io.writeData(src).valid
  io.conveyReqOut.bits.writeData := io.writeData(src).bits

  when (validWrite && io.genericReqIn.bits.isWrite) {
    // write request
    // both request and associated channel data are valid
    io.conveyReqOut.valid := Bool(true)
    // both request and associated channel data must be ready
    io.genericReqIn.ready := io.conveyReqOut.ready
    io.writeData(src).ready := io.conveyReqOut.ready
  } .elsewhen (io.genericReqIn.valid && !io.genericReqIn.bits.isWrite) {
    // read request
    io.conveyReqOut.valid := Bool(true)
    io.genericReqIn.ready := io.conveyReqOut.ready
  }
  // command according to burst length and r/w flag
  when (io.genericReqIn.bits.numBytes === UInt(64)) {
    io.conveyReqOut.bits.cmd := Mux(io.genericReqIn.bits.isWrite, UInt(6), UInt(7))
  } .elsewhen (io.genericReqIn.bits.numBytes === UInt(8)) {
    io.conveyReqOut.bits.cmd := Mux(io.genericReqIn.bits.isWrite, UInt(2), UInt(1))
  }
}

class ConveyMemReadReqAdp(p: MemReqParams) extends Module {
  val io = new Bundle {
    val genericReqIn = Decoupled(new GenericMemoryRequest(p)).flip
    val conveyReqOut = Decoupled(new MemRequest(32, 48, 64))
  }
  if(p.dataWidth != 64) {
    println("ConveyMemReqAdp requires p.dataWidth=64")
    System.exit(-1)
  }

  // default outputs
  io.conveyReqOut.bits.rtnCtl := io.genericReqIn.bits.channelID
  io.conveyReqOut.bits.writeData := UInt(0)
  io.conveyReqOut.bits.addr := io.genericReqIn.bits.addr
  io.conveyReqOut.bits.size := UInt( log2Up(p.dataWidth/8) )
  io.conveyReqOut.bits.scmd := UInt(0)
  io.conveyReqOut.bits.cmd := UInt(0)
  // directly connected ready-valid signals
  io.genericReqIn.ready := io.conveyReqOut.ready
  io.conveyReqOut.valid := io.genericReqIn.valid

  // command according to burst length
  when (io.genericReqIn.bits.numBytes === UInt(64)) {
    io.conveyReqOut.bits.cmd := UInt(7)
  } .elsewhen (io.genericReqIn.bits.numBytes === UInt(8)) {
    io.conveyReqOut.bits.cmd := UInt(1)
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
