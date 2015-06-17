import Chisel._
import ConveyInterfaces._

class MemSumPipe() extends Module {
  val io = new Bundle {
    val start = Bool(INPUT)
    val done  = Bool(OUTPUT)
    val start = UInt(INPUT, width = 48)
    val count = UInt(INPUT, width = 64)
    val sum   = UInt(OUTPUT, width = 64)
    val mem = new MemMasterIF()
  }

  // TODO add request generation logic
  // TODO add response handling logic
}
