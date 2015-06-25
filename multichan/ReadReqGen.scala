import Chisel._

// a parametrizable memory request generator

class MemReqParams(aW: Int, dW: Int, iW: Int, mW: Int) {
  // all units are "number of bits"
  val addrWidth: Int = aW       // width of memory addresses
  val dataWidth: Int = dW       // width of reads/writes
  val idWidth: Int = iW         // width of channel ID
  val metaDataWidth: Int = mW   // width of metadata (burst, prot, etc.)
}

class GenericMemoryRequest(p: MemReqParams) extends Bundle {
  val channelID = UInt(width = p.idWidth)
  val isWrite = Bool()
  val addr = UInt(width = p.addrWidth)
  val writeData = UInt(width = p.dataWidth)
  val metaData = UInt(width = p.metaDataWidth)
}

// a generic read request generator,
// only for contiguous accesses for now (no indirects, no strides)
// TODO how to gracefully handle burst generation?
class ReadReqGen(p: MemReqParams, chanID: Int) extends Module {
  val params = p
  val io = new Bundle {
    // control/status interface
    val start = Bool(INPUT)
    val finished = Bool(OUTPUT)
    val baseAddr = UInt(INPUT, width = p.addrWidth)
    // requests
    val reqs = Decoupled(new GenericMemoryRequest(p))
  }

  val sIdle :: sRun :: sFinished :: Nil = Enum(UInt(), 3)
  val regState = Reg(init = UInt(sIdle))
  val regAddr = Reg(init = UInt(0, p.addrBits))

  io.finished := Bool(false)
  io.reqs.valid := Bool(false)

  io.reqs.bits.channelID := UInt(chanID)
  io.reqs.bits.isWrite := Bool(false)
  io.reqs.bits.addr := regAddr
  io.reqs.bits.writeData := UInt(0)
  io.reqs.bits.metaData := UInt(0)  // TODO add burst info here

  // TODO perform request generation in the FSM
  switch(regState) {
      is(sIdle) {
        when (io.start) { regState := sRun }
      }

      is(sRun) {
        regState := sFinished
      }

      is(sFinished) {
        io.finished := Bool(true)
        when (!io.start) { regState := sIdle }
      }
  }
}
