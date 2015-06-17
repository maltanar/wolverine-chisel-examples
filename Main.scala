package AEGtoAEG
import Chisel._

object MainObj {
  def main(args: Array[String]): Unit = {
    //chiselMain(args, () => Module(new AEGRegFile(4)))
    //chiselMain(args, () => Module(new AEGtoAEG()))
    chiselMain(args, () => Module(new PersonalityWrapper(numMemPorts = 0)))
    //chiselMainTest(args, () => Module(new AXIStreamDownsizer(64,16))) { c => new AXIStreamDownsizerTester(c)}
  }
}
