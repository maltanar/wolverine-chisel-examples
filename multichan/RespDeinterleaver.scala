import Chisel._

// a generic memory response structure
class GenericMemoryResponse(p: MemReqParams) extends Bundle {
  // ID of the request channel (useful for out-of-order data returns)
  val channelID = UInt(width = p.idWidth)
  // returned read data (always single beat, bursts broken down into
  // multiple beats while returning)
  val readData = UInt(width = p.dataWidth)
  // metadata information (can be status/error bits, etc.)
  val metaData = UInt(width = p.metaDataWidth)

  override def clone = {
    new GenericMemoryResponse(p).asInstanceOf[this.type]
  }
}

class RespDeinterleaver(numPipes: Int, p: MemReqParams) extends Module {
  val io = new Bundle {
    // interleaved responses in
    val rspIn = Decoupled(new GenericMemoryResponse(p)).flip
    // deinterleaved responses out
    val rspOut = Vec.fill(numPipes) {Decoupled(new GenericMemoryResponse(p))}
    // TODO add statistics?
  }

  // TODO add ability to customize routing function
  def idToPipe(x: UInt): UInt = {x}

  // TODO the current implementation is likely to cause timing problems
  // due to high-fanout signals and combinational paths
  // - to avoid high-fanout signals: implement decoding as e.g shiftreg
  // - to avoid combinational paths, pipeline the deinterleaver
  for(i <- 0 until numPipes) {
    io.rspOut(i).bits := io.rspIn.bits
    io.rspOut(i).valid := Bool(false)
  }

  io.rspIn.ready := Bool(false)
  val destPipe = idToPipe(io.rspIn.bits.channelID)
  val canProceed = io.rspIn.valid && io.rspOut(destPipe).ready

  when (canProceed) {
    io.rspIn.ready := Bool(true)
    io.rspOut(destPipe).valid := Bool(true)
  }
}
