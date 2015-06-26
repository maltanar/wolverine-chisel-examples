import Chisel._
import ConveyInterfaces._

class MultiChanMemSum(numMemPorts: Int) extends Personality(numMemPorts) {
  // I/O is defined by the base class (Personality)

  // instantiate and connect main (ops) register file
  val regFile = Module(new RegFile(4, 18, 64))
  regFile.io.extIF <> io.disp.aeg
  // instantiate and connect the CSR register file
  val csrRegFile = Module(new RegFile(4, 16, 64))
  csrRegFile.io.extIF <> io.csr

  // TODO add dispatch and result handling logic
}
