import Chisel._

// a parametrizable memory request generator

class MemReqParams(aW: Int, dW: Int, iW: Int, mW: Int, b: Int) {
  // all units are "number of bits"
  val addrWidth: Int = aW       // width of memory addresses
  val dataWidth: Int = dW       // width of reads/writes
  val idWidth: Int = iW         // width of channel ID
  val metaDataWidth: Int = mW   // width of metadata (cache, prot, etc.)
  val beatsPerBurst: Int = b    // preferred number of beats in a burst
}

// a generic memory request structure, inspired by AXI with some diffs
class GenericMemoryRequest(p: MemReqParams) extends Bundle {
  // ID of the request channel (useful for out-of-order data returns)
  val channelID = UInt(width = p.idWidth)
  // whether this request is a read (if false) or write (if true)
  val isWrite = Bool()
  // start address of the request
  val addr = UInt(width = p.addrWidth)
  // number of bytes to read/write by this request
  val numBytes = UInt(width = 8)
  // metadata information (can be protection bits, caching bits, etc.)
  val metaData = UInt(width = p.metaDataWidth)
}

// a generic read request generator,
// only for contiguous accesses for now (no indirects, no strides)
// only burst-aligned addresses and sizes (no error checking!)
// TODO do we want to support unaligned/sub-word accesses?
class ReadReqGen(p: MemReqParams, chanID: Int) extends Module {
  val params = p
  val io = new Bundle {
    // control/status interface
    val start = Bool(INPUT)
    val finished = Bool(OUTPUT)
    val baseAddr = UInt(INPUT, width = p.addrWidth)
    val byteCount = UInt(INPUT, width = p.addrWidth)
    // requests
    val reqs = Decoupled(new GenericMemoryRequest(p))
  }
  // shorthands for convenience
  val bytesPerBeat = (p.dataWidth/8)
  val bytesPerBurst = p.beatsPerBurst * bytesPerBeat
  // state machine definitions & internal registers
  val sIdle :: sRun :: sFinished :: Nil = Enum(UInt(), 3)
  val regState = Reg(init = UInt(sIdle))
  val regAddr = Reg(init = UInt(0, p.addrBits))
  val regBytesLeft = Reg(init = UInt(0, p.addrBits))
  // default outputs
  io.finished := Bool(false)
  io.reqs.valid := Bool(false)
  io.reqs.bits.channelID := UInt(chanID)
  io.reqs.bits.isWrite := Bool(false)
  io.reqs.bits.addr := regAddr
  io.reqs.bits.writeData := UInt(0)
  io.reqs.bits.metaData := UInt(0)
  io.reqs.bits.numBytes := UInt(bytesPerBurst)

  switch(regState) {
      is(sIdle) {
        regAddr := io.baseAddr
        regBytesLeft := io.byteCount
        when (io.start) { regState := sRun }
      }

      is(sRun) {
        when (regBytesLeft === UInt(0)) { regState := sFinished }
        .otherwise {
          // issue the current request
          io.reqs.valid := Bool(true)
          when (io.reqs.ready) {
            // next request: update address & left request count
            regAddr := regAddr + UInt(bytesPerBurst)
            regBytesLeft := regBytesLeft - UInt(bytesPerBurst)
          }
        }
      }

      is(sFinished) {
        io.finished := Bool(true)
        when (!io.start) { regState := sIdle }
      }
  }
}
