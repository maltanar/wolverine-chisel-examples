package AEGtoAEG
import Chisel._

object MainObj {
  def main(args: Array[String]): Unit = {
    chiselMain(args, () => Module(new AEGRegFile(4)))
    //chiselMainTest(args, () => Module(new AXIStreamDownsizer(64,16))) { c => new AXIStreamDownsizerTester(c)}
  }
}
