import Chisel._

object MainObj {
  def main(args: Array[String]): Unit = {
    // number of memory ports to instantiate
    val numMemPorts = 1

    // this "fabricator function" is needed due to how modules need to be
    // wrapped, as in Module(new ModuleType()), is implemented in Chisel
    //def InstPersonality() : Personality = {new AEGtoAEG(numMemPorts)}
    def InstPersonality() : Personality = {new MemSum(numMemPorts)}

    //chiselMain(args, () => Module(new PersonalityWrapper(numMemPorts, InstPersonality) ))
    //chiselMainTest(args, () => Module(new AXIStreamDownsizer(64,16))) { c => new AXIStreamDownsizerTester(c)}

    var p = new OCMParameters
    p.addrWidth = 20
    p.readDepth = 1024
    p.readWidth = 32
    p.writeDepth = 1024*32
    p.writeWidth = 1
    p.latency = 3
    p.portCount = 2

    val ocmType: String = "ResultBRAM"

    chiselMain(args, () => Module(new OCMAndController(p, ocmType)))

  }
}
