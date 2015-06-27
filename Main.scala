import Chisel._

object MainObj {
  val defaultArgs: Array[String] = Array("--v")
  val defTestArgs = Array("--compile", "--test", "--genHarness")
  val testsDir: String = "testOutput/"

  // number of memory ports to instantiate for Convey personalities
  // TODO command line-parametize (weakly, so we get defaults too)
  val numMemPorts = 1

  val p = new MemReqParams(48, 64, 4, 1, 8)

  def main(args: Array[String]): Unit = {

    val functionMap: Map[String, () => Unit] = Map(
      "MemSum" -> makeMemSum,
      "ReadReqGen" -> makeReadReqGen,
      "MultiChanPipe" -> makeMultiChanPipe,
      "MultiChanMemSum" -> makeMultiChanSum,
      "StreamReducerAdd" -> makeStreamReducerAdd,
      "TestReadReqGen" -> testReadReqGen,
      "TestReqInterleaver" -> testReqInterleaver
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

  def makeStreamReducerAdd(): Unit = {
    val redFxn: (UInt,UInt)=>UInt = {(a,b)=>a+b}
    chiselMain(defaultArgs, () => Module(new StreamReducer(64, 0, redFxn)))
  }

  def makeMemSum(): Unit = {
    // this "fabricator function" is needed due to how modules need to be
    // wrapped, as in Module(new ModuleType()), is implemented in Chisel
    def InstPersonality() : Personality = {new MemSum(numMemPorts)}
    chiselMain(defaultArgs, () => Module(new PersonalityWrapper(numMemPorts, InstPersonality)))
  }

  def makeReadReqGen(): Unit = {
    chiselMain(defaultArgs, () => Module(new ReadReqGen(p, 0) ))
  }

  def testReadReqGen(): Unit = {
    val args = defTestArgs ++ Array("--targetDir", testsDir+"TestReadReqGen")

    val compInstFxn = { () => Module(new TestReadReqGenWrapper()) }
    val testInstFxn = { c => new TestReadReqGen(c) }

    chiselMain(args, compInstFxn, testInstFxn)
  }

  def testReqInterleaver(): Unit = {
    val args = defTestArgs ++ Array("--targetDir", testsDir+"TestReqInterleaver")

    val compInstFxn = { () => Module(new TestReqInterleaverWrapper()) }
    val testInstFxn = { c => new TestReqInterleaver(c) }

    chiselMain(args, compInstFxn, testInstFxn)
  }
}
