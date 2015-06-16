package ConveyInterfaces

import Chisel._
import Literal._
import Node._

// command bundle for read/writes to AEG/CSR registers
class RegCommand(idBits: Int, dataBits: Int) extends Bundle {
  val regID     = UInt(width = idBits)
  val read      = Bool()
  val write     = Bool()
  val writeData = UInt(width = dataBits)

  override def clone = { new RegCommand(idBits, dataBits).asInstanceOf[this.type] }
}

// register file interface
class RegFileSlaveIF(idBits: Int, dataBits: Int) extends Bundle {
  // register read/write commands
  // the "valid" signal here should be connected to (.read OR .write)
  val cmd         = Valid(new RegCommand(idBits, dataBits)).flip
  // returned read data
  val readData    = Valid(UInt(width = dataBits))
  // number of registers
  val regCount    = UInt(OUTPUT, width = idBits)

  override def clone = { new RegFileSlaveIF(idBits, dataBits).asInstanceOf[this.type] }
}

// dispatch slave interface
// for accepting instructions and AEG register operations
class DispatchSlaveIF() extends Bundle {
  // instruction opcode
  // note that this interface is defined as stall-valid instead of ready-valid
  // by Convey, so the ready signal should be inverted from stall
  val instr       = Decoupled(UInt(width = 5)).flip
  // register file access
  val aeg         = new RegFileSlaveIF(18, 64)
  // output for signalling instruction exceptions
  val exception   = UInt(OUTPUT, width = 16)

  override def clone = { new DispatchSlaveIF().asInstanceOf[this.type] }
}

class TopWrapperInterface(numMemPorts: Int) extends Bundle {
  // dispatch interface
  val dispInstValid = Bool(INPUT)
  val dispInstData  = UInt(INPUT, width = 5)
  val dispRegID     = UInt(INPUT, width = 18)
  val dispRegRead   = Bool(INPUT)
  val dispRegWrite  = Bool(INPUT)
  val dispRegWrData = UInt(INPUT, width = 64)
  val dispAegCnt    = UInt(OUTPUT, width = 18)
  val dispException = UInt(OUTPUT, width = 16)
  val dispIdle      = Bool(OUTPUT)
  val dispRtnValid  = Bool(OUTPUT)
  val dispRtnData   = UInt(OUTPUT, width = 64)
  val dispStall     = Bool(OUTPUT)
  // memory controller interface
  // request
  val mcReqValid    = UInt(OUTPUT, width = numMemPorts)
  val mcReqRtnCtl   = UInt(OUTPUT, width = 32*numMemPorts)
  val mcReqData     = UInt(OUTPUT, width = 64*numMemPorts)
  val mcReqAddr     = UInt(OUTPUT, width = 48*numMemPorts)
  val mcReqSize     = UInt(OUTPUT, width = 2*numMemPorts)
  val mcReqCmd      = UInt(OUTPUT, width = 3*numMemPorts)
  val mcReqSCmd     = UInt(OUTPUT, width = 4*numMemPorts)
  val mcReqStall    = UInt(INPUT, width = numMemPorts)
  // response
  val mcResValid    = UInt(INPUT, width = numMemPorts)
  val mcResCmd      = UInt(INPUT, width = 3*numMemPorts)
  val mcResSCmd     = UInt(INPUT, width = 4*numMemPorts)
  val mcResData     = UInt(INPUT, width = 64*numMemPorts)
  val mcResRtnCtl   = UInt(INPUT, width = 32*numMemPorts)
  val mcResStall    = UInt(OUTPUT, width = numMemPorts)
  // flush
  val mcReqFlush    = UInt(OUTPUT, width = numMemPorts)
  val mcReqFlushOK  = UInt(INPUT, width = numMemPorts)
  // control-status register interface
  val csrWrValid      = Bool(INPUT)
  val csrRdValid      = Bool(INPUT)
  val csrAddr         = UInt(INPUT, 16)
  val csrWrData       = UInt(INPUT, 64)
  val csrReadAck      = Bool(OUTPUT)
  val csrReadData     = UInt(OUTPUT, 64)
  // misc
  val aeid            = UInt(INPUT, 4)

  // rename signals
  def renameSignals() {
    dispInstValid.setName("disp_inst_vld")
    dispInstData.setName("disp_inst")
    dispRegID.setName("disp_aeg_idx")
    dispRegRead.setName("disp_aeg_rd")
    dispRegWrite.setName("disp_aeg_wr")
    dispRegWrData.setName("disp_aeg_wr_data")
    dispAegCnt.setName("disp_aeg_cnt")
    dispException.setName("disp_exception")
    dispIdle.setName("disp_idle")
    dispRtnValid.setName("disp_rtn_data_vld")
    dispRtnData.setName("disp_rtn_data")
    dispStall.setName("disp_stall")
    mcReqValid.setName("mc_rq_vld")
    mcReqRtnCtl.setName("mc_rq_rtnctl")
    mcReqData.setName("mc_rq_data")
    mcReqAddr.setName("mc_rq_vadr")
    mcReqSize.setName("mc_rq_size")
    mcReqCmd.setName("mc_rq_cmd")
    mcReqSCmd.setName("mc_rq_scmd")
    mcReqStall.setName("mc_rq_stall")
    mcResValid.setName("mc_rs_vld")
    mcResCmd.setName("mc_rs_cmd")
    mcResSCmd.setName("mc_rs_scmd")
    mcResData.setName("mc_rs_data")
    mcResRtnCtl.setName("mc_rs_rtnctl")
    mcResStall.setName("mc_rs_stall")
    mcReqFlush.setName("mc_rq_flush")
    mcReqFlushOK.setName("mc_rs_flush_cmplt")
    csrWrValid.setName("csr_wr_vld")
    csrRdValid.setName("csr_rd_vld")
    csrAddr.setName("csr_address")
    csrWrData.setName("csr_wr_data")
    csrReadAck.setName("csr_rd_ack")
    csrReadData.setName("csr_rd_data")
    aeid.setName("i_aeid")
  }
}
