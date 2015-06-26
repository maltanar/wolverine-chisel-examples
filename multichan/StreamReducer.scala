import Chisel._


class StreamReducer(w: Int, initVal: Int, fxn: (UInt,UInt)=>UInt) extends Module {

  val io = new Bundle {
    val start = Bool(INPUT)
    val finished = Bool(OUTPUT)
    val initial = UInt(INPUT, width = w )
    val reduced = UInt(OUTPUT, width = w )
    val count = UInt(INPUT, width = 32)
    val streamIn = Decoupled(UInt(width = w)).flip
  }

  val sIdle :: sRunning :: sFinished :: Nil = Enum(UInt(), 3)
  val regState = Reg(init = UInt(sIdle))
  val regReduced = Reg(init = UInt(initVal, width = w))
  val regElemsLeft = Reg(init = UInt(0, 32))

  io.finished := Bool(false)
  io.reduced := regReduced
  io.streamIn.ready := Bool(false)

  switch(regState) {
      is(sIdle) {
        regReduced := io.initial
        regElemsLeft := io.count

        when (io.start) { regState := sRunning }
      }

      is(sRunning) {
        when (regElemsLeft === UInt(0)) { regState := sFinished}
        .otherwise {
          io.streamIn.ready := Bool(true)
          when (io.streamIn.valid) {
            regReduced := fxn(regReduced, io.streamIn.bits)
            regElemsLeft := regElemsLeft - UInt(1)
          }
        }
      }

      is(sFinished) {
        io.finished := Bool(true)
        when (!io.start) { regState := sIdle}
      }
  }
}
