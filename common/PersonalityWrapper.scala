import Chisel._
import ConveyInterfaces._

class PersonalityWrapper(numMemPorts: Int, instFxn: () => Personality) extends Module {
  // the Convey wrapper itself always expects at least one memory port
  // if no mem ports are desired, we still create one and drive outputs to 0
  val numCalculatedMemPorts = if(numMemPorts == 0) 1 else numMemPorts
  val io = new TopWrapperInterface(numCalculatedMemPorts)

  io.renameSignals()

  val pers = Module(instFxn())
  val persDispatch = pers.io.disp
  val persCSR = pers.io.csr
  val persMemPorts = pers.io.mem

  // most wrapper signals connect directly
  persDispatch.instr.valid := io.dispInstValid
  persDispatch.instr.bits := io.dispInstData
  persDispatch.aeg.cmd.bits.regID := io.dispRegID
  persDispatch.aeg.cmd.bits.read := io.dispRegRead
  persDispatch.aeg.cmd.bits.write := io.dispRegWrite
  persDispatch.aeg.cmd.bits.writeData := io.dispRegWrData

  io.dispAegCnt := persDispatch.aeg.regCount
  io.dispException := persDispatch.exception
  io.dispIdle := persDispatch.instr.ready
  io.dispRtnValid := persDispatch.aeg.readData.valid
  io.dispRtnData := persDispatch.aeg.readData.bits

  persCSR.cmd.bits.write := io.csrWrValid
  persCSR.cmd.bits.read := io.csrRdValid
  persCSR.cmd.bits.regID := io.csrAddr
  persCSR.cmd.bits.writeData := io.csrWrData

  io.csrReadAck := persCSR.readData.valid
  io.csrReadData := persCSR.readData.bits

  // a few signals need simple logic adapters
  // valid == read | write for reg write interfaces
  persDispatch.aeg.cmd.valid := io.dispRegRead || io.dispRegWrite
  persCSR.cmd.valid := io.csrRdValid || io.csrWrValid
  // stall = !ready for the instr dispatch
  io.dispStall := !persDispatch.instr.ready

  // handle the no memory ports case by driving a single port to zero
  if (numMemPorts == 0) {
    println("====> Zero memory ports specified - instantiating one and driving to zero")
    io.mcReqValid := UInt(0)
    io.mcReqRtnCtl := UInt(0)
    io.mcReqData := UInt(0)
    io.mcReqAddr := UInt(0)
    io.mcReqSize := UInt(0)
    io.mcReqCmd := UInt(0)
    io.mcReqSCmd := UInt(0)
    io.mcResStall := UInt(0)
    io.mcReqFlush := UInt(0)
  } else {
    // connect the memory ports
    // note that the Convey interface specifies multiple ports as
    // a single port with wide signals (like "structure of arrays", SoA)
    // whereas our Chisel model considers multiple ports as multiple ports
    // (like "array of structures", AoS) so there is some conversion involved

    // helper functions to concatenate (converting AoS to SoA)
    def CatMemPortElems(extr: MemMasterIF => Bits,
                        i: Int, n: Int ): Bits = {
      if ( i == n-1 ) {
        return extr(persMemPorts(i))
      } else {
        return Cat(CatMemPortElems(extr, i+1, n), extr(persMemPorts(i)))
      }
    }

    def CatMemPortHelper(extr: MemMasterIF => Bits): Bits = {
      return CatMemPortElems(extr, 0, numMemPorts)
    }

    // connect Chisel outputs (AoS) to personality outputs (SoA)
    io.mcReqRtnCtl := CatMemPortHelper((m: MemMasterIF) => (m.req.bits.rtnCtl))
    io.mcReqData := CatMemPortHelper((m: MemMasterIF) => (m.req.bits.writeData))
    io.mcReqAddr := CatMemPortHelper((m: MemMasterIF) => (m.req.bits.addr))
    io.mcReqSize := CatMemPortHelper((m: MemMasterIF) => (m.req.bits.size))
    io.mcReqCmd := CatMemPortHelper((m: MemMasterIF) => (m.req.bits.cmd))
    io.mcReqSCmd := CatMemPortHelper((m: MemMasterIF) => (m.req.bits.scmd))
    io.mcReqFlush := CatMemPortHelper((m: MemMasterIF) => (m.flushReq))

    // Convey's interface semantics (stall-valid) are a bit more different than
    // just a decoupled (inverted ready)-valid:
    // X1) valid and stall asserted together can still mean a transferred element
    //     (i.e valid may not go down for up to 2 cycles after stall is asserted)
    // X2) valid on Convey IF must actually go down after stall is asserted

    // compensate for interface semantics mismatch for memory responses (X1) with
    // little queues:
    // - personality receives responses through queue
    // - Convey mem.port's stall is driven by "almost full" from queue
    val respQueElems = 16
    val respQueues = Vec.fill(numMemPorts) {
      Module(new Queue(new MemResponse(ConveyMemParams().idWidth, 64), respQueElems)).io
    }

    // an "almost full" derived from the queue count is used
    // to drive the Convey mem resp port's stall input
    // this is quite conservative (stall when FIFO is half full) but
    // it seems to work (there may be a deeper problem here)
    io.mcResStall := Cat(respQueues.map(x => (x.count >= UInt(respQueElems/2))))

    // connect personality inputs to Chisel inputs
    for (i <- 0 to numMemPorts-1) {
      respQueues(i).enq.valid := io.mcResValid(i)
      respQueues(i).enq.bits.rtnCtl := io.mcResRtnCtl(32*(i+1)-1, 32*i)
      respQueues(i).enq.bits.readData := io.mcResData(64*(i+1)-1, 64*i)
      respQueues(i).enq.bits.cmd := io.mcResCmd(3*(i+1)-1, 3*i)
      respQueues(i).enq.bits.scmd := io.mcResSCmd(4*(i+1)-1, 4*i)
      // note that we don't use the enq.ready signal from the queue

      // personality receives responses directly from the queue deq IF
      persMemPorts(i).rsp <> respQueues(i).deq

      // single-bit signals
      persMemPorts(i).flushOK := io.mcResFlushOK(i)
      // note the ~ here to convert stall to ready
      persMemPorts(i).req.ready := ~io.mcReqStall(i)
    }

    // to compensate for X2, we AND the valid with the inverse of stall before
    // outputting valid
    // TODO do not create potential combinational loop -- make queue-based sln?
    io.mcReqValid := CatMemPortHelper((m: MemMasterIF) => (m.req.valid & m.req.ready))
  }
  println(s"====> Remember to set NUM_MC_PORTS=$numCalculatedMemPorts in cae_pers.v")
  val numRtnCtlBits = ConveyMemParams().idWidth
  println(s"====> Remember to set RTNCTL_WIDTH=$numRtnCtlBits in cae_pers.v")
}
