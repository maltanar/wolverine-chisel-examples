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

    val p = new OCMParameters(math.pow(2, 20).toInt, 32, 1, 2, 3)
    val ocmType: String = "ResultBRAM"

    chiselMain(args, () => Module(new OCMAndController(p, ocmType)))
    //chiselMainTest(args, () => Module(new AsymDualPortRAM(p))) { c => new AsymDualPortRAMTester(c)}

  }
}
