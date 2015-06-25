import Chisel._

class ReqInterleaver(numPipes: Int, p: MemReqParams) extends Module {
  val io = new Bundle {
    // individual request pipes
    val reqIn = Vec.fill(numPipes) {Decoupled(new GenericMemoryRequest(p)).flip}
    // interleaved request pipe
    val reqOut = Decoupled(new GenericMemoryRequest(p))
  }
  // TODO for now, we just use a round-robin arbiter
  // TODO report statistics from the interleaved mix?
  val arb = Module(new Arbiter(gen=new GenericMemoryRequest(p), n=numPipes))
  for (i <- 0 until numPipes) {
    arb.io.in(i) <> io.reqIn(i)
  }
  arb.io.out <> io.reqOut
}
