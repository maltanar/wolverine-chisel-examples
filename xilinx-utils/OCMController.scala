import Chisel._

// a definitions and helpers for FPGA On-Chip Memory (OCM)
// (typically "BRAM" for Xilinx and "embedded memory" for Altera)

// a collection of values that define the OCM
// for now we require all ports to have the same dimensions
class OCMParameters {
  var addrWidth: Int = 0
  var readDepth: Int = 0
  var readWidth: Int = 0
  var writeDepth: Int = 0
  var writeWidth: Int = 0
  var latency: Int = 0
  var portCount: Int = 0
}

// master interface for an OCM access port (read/write, possibly with different
// widths)
class OCMMasterIF(writeWidth: Int, readWidth: Int, addrBits: Int) extends Bundle {
  val addr = UInt(OUTPUT, width = addrBits)
  val dataRead = UInt(INPUT, width = readWidth)
  val writeEn = Bool(OUTPUT)
  val dataWrite = UInt(OUTPUT, width = writeWidth)

  override def clone =
    { new OCMMasterIF(writeWidth, readWidth, addrBits).asInstanceOf[this.type] }
}

// slave interface is just the master interface flipped
class OCMSlaveIF(writeWidth: Int, readWidth: Int, addrBits: Int) extends Bundle {
    OCMMasterIF(writeWidth, readWidth, addrBits).flip

    override def clone =
      { new OCMSlaveIF(writeWidth, readWidth, addrBits).asInstanceOf[this.type] }
}

// we assume the actual OCM instance is generated via vendor-provided tools
// so this is just a BlackBox (wrapper module)
class OnChipMemory(p: OCMParameters, ocmName: String) extends BlackBox {
  // TODO check if this works
  moduleName = ocmName
  val io = new Bundle {
    // TODO fix and match names to the template (add renaming function)
    val ports = Vec.fill(p.portCount) { new OCMSlaveIF(p.writeWidth, p.readWidth, p.addrWidth)}
  }

}

class OCMController(p: OCMParameters, extAddrWidth: Int) extends Module {
  val io = new Bundle {
    // control/status interface
    val mode = UInt(INPUT, 1)
    val start = Bool(INPUT)
    val done = Bool(OUTPUT)
    val baseAddr = UInt(INPUT, width = extAddrWidth)
    // TODO support partial fill/dump by start&count registers
    // fill/dump ports
    val fillPort = Decoupled(UInt(width = p.writeWidth)).flip
    val dumpPort = Decoupled(UInt(width = p.readWidth))
    // slave ports for passthrough mode
    val ports = Vec.fill(p.portCount) { new OCMSlaveIF(p.writeWidth, p.readWidth, p.addrWidth)}
    // master ports to connect to the OCM instance
    val toOCM = Vec.fill(p.portCount) { new OCMMasterIF(p.writeWidth, p.readWidth, p.addrWidth)}
  }

  // TODO add passthrough logic when controller is idle
  // TODO implement fill port functionality
  // TODO implement dump port functionality (remember ResultDumper)

}
