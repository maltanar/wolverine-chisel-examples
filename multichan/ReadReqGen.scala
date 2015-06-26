import Chisel._

// a parametrizable memory request generator

class MemReqParams(aW: Int, dW: Int, iW: Int, mW: Int, b: Int) {
  // all units are "number of bits"
  val addrWidth: Int = aW       // width of memory addresses
  val dataWidth: Int = dW       // width of reads/writes
  val idWidth: Int = iW         // width of channel ID
  val metaDataWidth: Int = mW   // width of metadata (cache, prot, etc.)
  val beatsPerBurst: Int = b    // number of beats in a burst

  override def clone = {
    new MemReqParams(aW, dW, iW, mW, b).asInstanceOf[this.type]
  }
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

  override def clone = {
    new GenericMemoryRequest(p).asInstanceOf[this.type]
  }
}

class ReadReqGenCtrl(addrWidth: Int) extends Bundle {
  val start = Bool(INPUT)
  val throttle = Bool(INPUT)
  val baseAddr = UInt(INPUT, width = addrWidth)
  val byteCount = UInt(INPUT, width = addrWidth)
}

class ReadReqGenStatus() extends Bundle {
  val finished = Bool(OUTPUT)
  val active = Bool(OUTPUT)
}

// a generic read request generator,
// only for contiguous accesses for now (no indirects, no strides)
// only burst-aligned addresses and sizes (no error checking!)
// TODO do we want to support unaligned/sub-word accesses?
class ReadReqGen(p: MemReqParams, chanID: Int) extends Module {
  val reqGenParams = p
  val io = new Bundle {
    // control/status interface
    val ctrl = new ReadReqGenCtrl(p.addrWidth)
    val stat = new ReadReqGenStatus()
    // requests
    val reqs = Decoupled(new GenericMemoryRequest(p))
  }
  // shorthands for convenience
  val bytesPerBeat = (p.dataWidth/8)
  val bytesPerBurst = p.beatsPerBurst * bytesPerBeat
  // state machine definitions & internal registers
  val sIdle :: sRun :: sFinished :: Nil = Enum(UInt(), 3)
  val regState = Reg(init = UInt(sIdle))
  val regAddr = Reg(init = UInt(0, p.addrWidth))
  val regBytesLeft = Reg(init = UInt(0, p.addrWidth))
  // default outputs
  io.stat.finished := Bool(false)
  io.stat.active := (regState != sIdle)
  io.reqs.valid := Bool(false)
  io.reqs.bits.channelID := UInt(chanID)
  io.reqs.bits.isWrite := Bool(false)
  io.reqs.bits.addr := regAddr
  io.reqs.bits.metaData := UInt(0)
  io.reqs.bits.numBytes := UInt(bytesPerBurst)

  switch(regState) {
      is(sIdle) {
        regAddr := io.ctrl.baseAddr
        regBytesLeft := io.ctrl.byteCount
        when (io.ctrl.start) { regState := sRun }
      }

      is(sRun) {
        when (regBytesLeft === UInt(0)) { regState := sFinished }
        .elsewhen (!io.ctrl.throttle) {
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
        io.stat.finished := Bool(true)
        when (!io.ctrl.start) { regState := sIdle }
      }
  }
}

class TestReadReqGenWrapper() extends Module {
  val p = new MemReqParams(48, 64, 4, 1, 8)

  val io = new Bundle {
    val ctrl = new ReadReqGenCtrl(p.addrWidth)
    val stat = new ReadReqGenStatus()
    val reqQOut = Decoupled(new GenericMemoryRequest(p))
  }

  val dut = Module(new ReadReqGen(p, 0))
  val reqQ = Module(new Queue(new GenericMemoryRequest(p), 4096))
  dut.io.reqs <> reqQ.io.enq
  reqQ.io.deq <> io.reqQOut
  io.ctrl <> dut.io.ctrl
  io.stat <> dut.io.stat
}

class TestReadReqGen(c: TestReadReqGenWrapper) extends Tester(c) {

  c.io.reqQOut.ready := Bool(false)

  val byteCount = 1024
  val baseAddr = 100

  val expectedReqCount = byteCount / (c.dut.bytesPerBurst)

  def waitUntilFinished(): Unit = {
    while(peek(c.io.stat.finished) != 1) {
      peek(c.reqQ.io.enq.valid)
      peek(c.reqQ.io.enq.bits)
      step(1)
      peek(c.reqQ.io.count)
    }
  }

  // Test 1: check request count and addresses, no throttling
  // set up the reqgen
  poke(c.io.ctrl.start, 0)
  poke(c.io.ctrl.throttle, 0)
  poke(c.io.ctrl.baseAddr, baseAddr)
  poke(c.io.ctrl.byteCount, byteCount)
  poke(c.io.reqQOut.ready, 0)
  step(1)
  expect(c.io.stat.finished, 0)
  expect(c.io.stat.active, 0)
  // activate and checki
  poke(c.io.ctrl.start, 1)
  step(1)
  expect(c.io.stat.finished, 0)
  expect(c.io.stat.active, 1)
  waitUntilFinished()
  // check number of emitted requests
  expect(c.reqQ.io.count, expectedReqCount)
  var expAddr = baseAddr
  // pop requests and check addresses
  while(peek(c.io.reqQOut.valid) == 1) {
    expect(c.io.reqQOut.bits.isWrite, 0)
    expect(c.io.reqQOut.bits.addr, expAddr)
    expect(c.io.reqQOut.bits.numBytes, c.dut.bytesPerBurst)
    poke(c.io.reqQOut.ready, 1)
    step(1)
    expAddr += c.dut.bytesPerBurst
  }
  // deinitialize and check
  poke(c.io.ctrl.start, 0)
  poke(c.io.reqQOut.ready, 0)
  step(1)
  expect(c.io.stat.finished, 0)
  expect(c.io.stat.active, 0)
  expect(c.reqQ.io.count, 0)

  // Test 2: repeat Test 1 with throttling
  poke(c.io.ctrl.start, 1)
  poke(c.io.ctrl.throttle, 1)
  step(1)
  expect(c.io.stat.finished, 0)
  expect(c.io.stat.active, 1)
  step(10)
  // verify that no requests appear
  expect(c.reqQ.io.count, 0)
  // remove throttling
  poke(c.io.ctrl.throttle, 0)
  waitUntilFinished()
  // check number of emitted requests
  expect(c.reqQ.io.count, expectedReqCount)
  expAddr = baseAddr
  // pop requests and check addresses
  while(peek(c.io.reqQOut.valid) == 1) {
    expect(c.io.reqQOut.bits.isWrite, 0)
    expect(c.io.reqQOut.bits.addr, expAddr)
    expect(c.io.reqQOut.bits.numBytes, c.dut.bytesPerBurst)
    poke(c.io.reqQOut.ready, 1)
    step(1)
    expAddr += c.dut.bytesPerBurst
  }
  // deinitialize and check
  poke(c.io.ctrl.start, 0)
  poke(c.io.reqQOut.ready, 0)
  step(1)
  expect(c.io.stat.finished, 0)
  expect(c.io.stat.active, 0)
  expect(c.reqQ.io.count, 0)
}
