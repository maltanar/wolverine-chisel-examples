import Chisel._
import ConveyInterfaces._

// base class for writing Convey Chisel personalities

class Personality(numMemPorts: Int) extends Module {
  val io = new PersonalityIF(numMemPorts)

  override def clone = { new Personality(numMemPorts).asInstanceOf[this.type] }
}
