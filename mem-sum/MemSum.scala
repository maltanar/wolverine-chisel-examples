import Chisel._
import ConveyInterfaces._

import Chisel._

class MemSum(numMemPorts: Int) extends Personality(numMemPorts) {
  // I/O is defined by the base class (Personality)

  // instantiate and connect main (ops) register file
  val regFile = Module(new RegFile(4, 18, 64))
  regFile.io.extIF <> io.disp.aeg
  // instantiate and connect the CSR register file
  val csrRegFile = Module(new RegFile(4, 16, 64))
  csrRegFile.io.extIF <> io.csr


  // give names to registers for readability
  val paramBasePtr = regFile.io.regOut(0)
  val paramElemCnt = regFile.io.regOut(1)

  // TODO create on data pipeline per memory port
  // TODO add instruction dispatch logic
  // TODO return result
  // TODO add cycle counter
}
