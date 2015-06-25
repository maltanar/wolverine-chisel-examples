import Chisel._

object MainObj {
  val defaultArgs: Array[String] = Array("--v")

  def main(args: Array[String]): Unit = {

    val functionMap: Map[String, () => Unit] = Map(
      "MemSum" -> makeMemSum,
      "ReadReqGen" -> makeReadReqGen
    )
    val moduleName = args(0)
    println("Building module: " + moduleName)

    functionMap(moduleName)()
  }

  def makeMemSum(): Unit = {
    // number of memory ports to instantiate
    // TODO command line-parametize (weakly, so we get defaults too)
    val numMemPorts = 1
    // this "fabricator function" is needed due to how modules need to be
    // wrapped, as in Module(new ModuleType()), is implemented in Chisel
    def InstPersonality() : Personality = {new MemSum(numMemPorts)}
    chiselMain(defaultArgs, () => Module(new PersonalityWrapper(numMemPorts, InstPersonality)))
  }

  def makeReadReqGen(): Unit = {
    val p = new MemReqParams(48, 64, 4, 1, 8)
    chiselMain(defaultArgs, () => Module(new ReadReqGen(p, 0) ))
  }
}
