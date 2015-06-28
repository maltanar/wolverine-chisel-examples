import Chisel._
import ConveyInterfaces._
import TidbitsOCM._

// TODO parametrize num of mem ports connected (also BRAM modules to inst.)
class BRAMTest() extends Personality(1) {
  // I/O is defined by the base class (Personality)

  // instantiate and connect main (ops) register file
  val aegRegCount = 4
  val regFile = Module(new RegFile(aegRegCount, 18, 64))
  regFile.io.extIF <> io.disp.aeg
  // plug AEG regfile write enables, will be set when needed
  for(i <- 0 until aegRegCount) {
    regFile.io.regIn(i).valid := Bool(false)
  }
  // plug CSR outputs, unused for this design
  io.csr.readData.bits := UInt(0)
  io.csr.readData.valid := Bool(false)
  io.csr.regCount := UInt(0)

  val p = new OCMParameters(math.pow(2,20).toInt, 32, 1, 2, 3)

  val ocmc = Module(new OCMAndController(p, "ResultBRAM", true))
  // TODO make OCM user ports part of the test -- plugged to zero for now
  ocmc.io.ocmUser.req.addr := UInt(0)
  ocmc.io.ocmUser.req.writeData := UInt(0)
  ocmc.io.ocmUser.req.writeEn := Bool(false)

  ocmc.io.mcif.mode := UInt(0)
  ocmc.io.mcif.start := Bool(false)
  // TODO connect fill port to memory read port
  // TODO connect dump port to queue, drain and sum contents

  // TODO add instruction logic for two instructions:
  // - fill BRAM
  // - dump BRAM and sum contents
}
