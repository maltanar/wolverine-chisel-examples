import Chisel._
import ConveyInterfaces._

class MultiChanMemSum(numMemPorts: Int) extends Personality(numMemPorts) {
  // I/O is defined by the base class (Personality)
  // TODO do not hardcode for 1 mem port and pipe

  // instantiate and connect main (ops) register file
  val regFile = Module(new RegFile(8, 18, 64))
  regFile.io.extIF <> io.disp.aeg
  // instantiate and connect the CSR register file
  val csrRegFile = Module(new RegFile(4, 16, 64))
  csrRegFile.io.extIF <> io.csr

  val p = new MemReqParams(48, 64, 4, 1, 8)


  val pipes = Vec.fill(numMemPorts) { Module(new MultiChanPipe(p, 2)).io }
  val regPipeStart = Reg(init = Bool(false))
  for (i <- 0 to numMemPorts-1) {
    // connect memory port
    io.mem(i) <> pipes(i).mem
    pipes(i).start := regPipeStart
    // TODO connect base-byteCount-sum registers to regFile
  }
  val allPipesFinished = pipes.forall( pipe => (pipe.done) )

  // TODO add instruction dispatch logic

}
