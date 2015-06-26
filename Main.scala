import Chisel._

object MainObj {
  val defaultArgs: Array[String] = Array("--v")
  val defTestArgs = Array("--compile", "--test", "--genHarness")
  val testsDir: String = "testOutput/"

  def main(args: Array[String]): Unit = {

    val functionMap: Map[String, () => Unit] = Map(
      "MemSum" -> makeMemSum,
      "ReadReqGen" -> makeReadReqGen,
      "TestReadReqGen" -> testReadReqGen
    )
    val moduleName = args(0)
    println("Executing task: " + moduleName)

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

  def testReadReqGen(): Unit = {
    val args = defTestArgs ++ Array(testsDir+"TestReadReqGen")

    val compInstFxn = { () => Module(new TestReadReqGenWrapper()) }
    val testInstFxn = { c => new TestReadReqGen(c) }

    chiselMain(args, compInstFxn, testInstFxn)
  }
}
