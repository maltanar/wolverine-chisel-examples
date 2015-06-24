import Chisel._

// a definitions and helpers for FPGA On-Chip Memory (OCM)
// (typically "BRAM" for Xilinx and "embedded memory" for Altera)

// a collection of values that define the OCM
// for now we require all ports to have the same dimensions
class OCMParameters {
  var addrWidth: Int = 0
  var readDepth: Int = 0
  var readWidth: Int = 0
  var readAddrShift: Int = 0
  var writeDepth: Int = 0
  var writeWidth: Int = 0
  var writeAddrShift: Int = 0
  var latency: Int = 0
  var portCount: Int = 0
}

class OCMRequest(writeWidth: Int, addrWidth: Int) extends Bundle {
  val addr = UInt(width = addrWidth)
  val writeData = UInt(width = writeWidth)
  val writeEn = Bool()

  override def clone = {new OCMRequest(writeWidth, addrWidth).asInstanceOf[this.type]}
}

class OCMResponse(readWidth: Int) extends Bundle {
  val readData = UInt(width = readWidth)

  override def clone = {new OCMResponse(readWidth).asInstanceOf[this.type]}
}

// master interface for an OCM access port (read/write, possibly with different
// widths)
class OCMMasterIF(writeWidth: Int, readWidth: Int, addrWidth: Int) extends Bundle {
  val req = new OCMRequest(writeWidth, addrWidth).asOutput()
  val rsp = new OCMResponse(readWidth).asInput()

  override def clone =
    { new OCMMasterIF(writeWidth, readWidth, addrWidth).asInstanceOf[this.type] }
}

// slave interface is just the master interface flipped
class OCMSlaveIF(writeWidth: Int, readWidth: Int, addrWidth: Int) extends Bundle {
  val req = new OCMRequest(writeWidth, addrWidth).asInput()
  val rsp = new OCMResponse(readWidth).asOutput()

  override def clone =
    { new OCMSlaveIF(writeWidth, readWidth, addrWidth).asInstanceOf[this.type] }
}

// we assume the actual OCM instance is generated via vendor-provided tools
// so this is just a BlackBox (wrapper module)
class OnChipMemory(p: OCMParameters, ocmName: String) extends BlackBox {
  moduleName = ocmName
  val io = new Bundle {
    // TODO fix and match names to the template (add renaming function)
    val ports = Vec.fill(p.portCount) {
      new OCMSlaveIF(p.writeWidth, p.readWidth, p.addrWidth)}
  }
}

class OCMControllerIF(p: OCMParameters) extends Bundle {
  // control/status interface
  val mode = UInt(INPUT, 1)
  val start = Bool(INPUT)
  val done = Bool(OUTPUT)
  val fillPort = Decoupled(UInt(width = p.writeWidth)).flip
  val dumpPort = Decoupled(UInt(width = p.readWidth))
}

// TODO support partial fill/dump by start&count registers
// fill/dump ports
// TODO support fill/dump through all ports (width*count)
// TODO slave ports for passthrough mode

class OCMController(p: OCMParameters) extends Module {
  val io = new Bundle {
    val mcif = new OCMControllerIF(p)
    // master port to connect to the OCM instance
    val ocm = new OCMMasterIF(p.writeWidth, p.readWidth, p.addrWidth)
  }
  // TODO implement fill port functionality
  // TODO implement dump port functionality (remember ResultDumper)

  val regAddr = Reg(init = UInt(0, p.addrWidth))

  // default outputs
  io.mcif.done := Bool(false)
  io.mcif.fillPort.ready := Bool(false)
  io.mcif.dumpPort.valid := Bool(false)
  io.mcif.dumpPort.bits := UInt(0)  // TODO remove for dump port
  io.ocm.req.addr := UInt(0)
  io.ocm.req.writeEn := Bool(false)
  io.ocm.req.writeData := io.mcif.fillPort.bits

  val sIdle :: sFill :: sDump :: sFinished :: Nil = Enum(UInt(), 4)
  val regState = Reg(init = UInt(sIdle))

  switch(regState) {
      is(sIdle) {
        regAddr := UInt(0)
        when(io.mcif.start) {
          when (io.mcif.mode === UInt(0)) { regState := sFill }
          .elsewhen (io.mcif.mode === UInt(1)) { regState := sDump }
        }
      }

      is(sFill) {
        io.mcif.fillPort.ready := Bool(true)
        io.ocm.req.addr := (regAddr << UInt(p.writeAddrShift))

        when (regAddr === UInt(p.writeDepth)) {regState := sFinished}
        .elsewhen (io.mcif.fillPort.valid) {
          io.ocm.req.writeEn := Bool(true)
          regAddr := regAddr + UInt(1)
        }
      }

      is(sDump) {
        // TODO
        regState := sFinished
      }

      is(sFinished) {
        io.mcif.done := Bool(true)
        when (!io.mcif.start) {regState := sIdle}
      }
  }
}

// convenience module for instantiating an OCM and a coupled controller
class OCMAndController(p: OCMParameters, ocmName: String) extends Module {
  val io = new Bundle {
    val mcif = new OCMControllerIF(p)
    // TODO add support for several "external ports" (not connected to the MC)
    val ocmUser = new OCMSlaveIF(p.writeWidth, p.readWidth, p.addrWidth)
  }

  // instantiate the OCM controller
  val ocmControllerInst = Module(new OCMController(p))
  // instantiate the OCM
  val ocmInst = Module(new OnChipMemory(p, ocmName))
  // connect the interfaces
  io.mcif <> ocmControllerInst.io.mcif
  // TODO do not hardcode port connections
  ocmControllerInst.io.ocm <> ocmInst.io.ports(0)
  ocmInst.io.ports(1) <> io.ocmUser
}
