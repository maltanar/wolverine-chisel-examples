import Chisel._

object MainObj {
  def main(args: Array[String]): Unit = {
    // number of memory ports to instantiate
    val numMemPorts = 0

    // this "fabricator function" is needed due to how modules need to be
    // wrapped, as in Module(new ModuleType()), is implemented in Chisel
    def InstPersonality() : Personality = {new AEGtoAEG(numMemPorts)}

    chiselMain(args, () => Module(new PersonalityWrapper(numMemPorts, InstPersonality) ))
    //chiselMainTest(args, () => Module(new AXIStreamDownsizer(64,16))) { c => new AXIStreamDownsizerTester(c)}
  }
}
