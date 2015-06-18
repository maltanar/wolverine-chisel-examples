import Chisel._
import ConveyInterfaces._

class MemSumPipeIF() extends Bundle {
  val start = Bool(INPUT)               // start signal
  val done  = Bool(OUTPUT)              // finished
  val base = UInt(INPUT, width = 64)    // base pointer
  val count = UInt(INPUT, width = 64)   // number of elements
  val sum   = UInt(OUTPUT, width = 64)  // returned sum
  val mem = new MemMasterIF()           // memory port
}

class MemSumPipe() extends Module {
  val io = new MemSumPipeIF()

  // internal registers
  val regSum = Reg(init = UInt(0, 64))
  val regReqPtr = Reg(init = UInt(0, 64))
  val regReqsLeft = Reg(init = UInt(0, 64))

  // default outputs -- specific
  io.done := Bool(false)
  io.sum := regSum
  // default outputs -- memory requests
  io.mem.req.valid := Bool(false)
  io.mem.req.bits.rtnCtl := UInt(0)
  io.mem.req.bits.writeData := UInt(0)
  io.mem.req.bits.addr := regReqPtr
  // size/cmd/scmd values for 8-byte reads
  // TODO test 64-byte reads for more BW
  io.mem.req.bits.size := UInt(3)
  io.mem.req.bits.cmd := UInt(1)
  io.mem.req.bits.scmd := UInt(0)
  // default outputs -- memory responses and flushing
  io.mem.rsp.ready := Bool(false)
  io.mem.flushReq := Bool(false)

  // request generation logic
  val sReqIdle :: sReqIssue :: sReqDone :: Nil = Enum(UInt(), 3)
  val regReqFSMState = Reg(init = UInt(sReqIdle))

  switch(regReqFSMState) {
      is(sReqIdle) {
        when (io.start) {
          // start the req machine
          regReqFSMState := sReqIssue
          // latch in parameters and initialize regs
          regReqPtr := io.base
          regReqsLeft := io.count
        }
      }

      is(sReqIssue) {
        // completion check
        when (regReqsLeft === UInt(0)) { regReqFSMState := sReqDone }
        .otherwise {
          io.mem.req.valid := Bool(true)
          when ( io.mem.req.ready ) {
            // update counters
            regReqPtr := regReqPtr + UInt(8)
            regReqsLeft := regReqsLeft - UInt(1)
          }
        }
        // wait in this state otherwise
      }

      is(sReqDone) {
        // wait for !start to go to idle
        when (!io.start) { regReqFSMState := sReqIdle}
      }
  }

  // response handling logic
  val regRspsLeft = Reg(init = UInt(0, 64))
  val sRspIdle :: sRspHandle :: sRspDone :: Nil = Enum(UInt(), 3)
  val regRspFSMState = Reg(init = UInt(sRspIdle))

  switch(regRspFSMState) {
      is(sRspIdle) {
        when (io.start) {
          // start the rsp machine
          regRspFSMState := sRspHandle
          // latch in parameters and initialize regs
          regRspsLeft := io.count
          regSum := UInt(0)
        }
      }

      is(sRspHandle) {
        // signal that we are ready to receive responses
        io.mem.rsp.ready := Bool(true)
        // completion check
        when ( regRspsLeft === UInt(0) ) { regRspFSMState := sRspDone}
        // check for read data
        .elsewhen ( io.mem.rsp.valid ) {
          // update sum
          regSum := regSum + io.mem.rsp.bits.readData
          // update counter
          regRspsLeft := regRspsLeft - UInt(1)
        }
        // stay in this state otherwise
      }

      is(sRspDone) {
        // pipe is done when response logic is done
        io.done := Bool(true)
        // wait for !start to go to idle
        when (!io.start) { regRspFSMState := sRspIdle}
      }
  }
}
