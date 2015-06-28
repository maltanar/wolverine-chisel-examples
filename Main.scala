import Chisel._
import ConveyInterfaces._

object MainObj {
  val defaultArgs: Array[String] = Array("--v")
  val defTestArgs = Array("--compile", "--test", "--genHarness")
  val testsDir: String = "testOutput/"
  val p = ConveyMemParams()

  // number of memory ports to instantiate for Convey personalities
  // TODO command line-parametize (weakly, so we get defaults too)
  val numMemPorts = 1


  def main(args: Array[String]): Unit = {

    val functionMap: Map[String, () => Unit] = Map(
      "MemSum" -> makeMemSum,
      "MultiChanPipe" -> makeMultiChanPipe,
      "MultiChanMemSum" -> makeMultiChanSum
    )
    val moduleName = args(0)
    println("Executing task: " + moduleName)

    functionMap(moduleName)()
  }

  def makeMultiChanSum(): Unit = {
    def InstPersonality() : Personality = {new MultiChanMemSum(numMemPorts, 2)}
    chiselMain(defaultArgs, () => Module(new PersonalityWrapper(numMemPorts, InstPersonality)))
  }

  def makeMultiChanPipe(): Unit = {
    chiselMain(defaultArgs, () => Module(new MultiChanPipe(p, 2)))
  }

  def makeMemSum(): Unit = {
    // this "fabricator function" is needed due to how modules need to be
    // wrapped, as in Module(new ModuleType()), is implemented in Chisel
    def InstPersonality() : Personality = {new MemSum(numMemPorts)}
    chiselMain(defaultArgs, () => Module(new PersonalityWrapper(numMemPorts, InstPersonality)))
  }
}
