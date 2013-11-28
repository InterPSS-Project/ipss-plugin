package org.interpss.QA.compare.dep;

import org.interpss.QA.result.QAAclfBranchRec;
import org.interpss.QA.result.QAAclfBusRec;
import org.interpss.numeric.datatype.Unit;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.common.util.StringUtil;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;

@Deprecated
public class DepPSLFComapreFileProcessor extends DepBaseCompareFileProcessor<QAAclfBusRec, QAAclfBranchRec> {
	private boolean busDataProcessed = false,
	                busRecordBegin = false,
	                busDataEnd = true;
	private int headerLineCnt = 0;
	
	public DepPSLFComapreFileProcessor(AclfNetwork net) {
		this.net = net;
	}

	public DepPSLFComapreFileProcessor(AclfNetwork net, boolean netOnly) {
		this(net);
		this.compareNetworkOnly = netOnly;
		for (Bus b : net.getBusList())
			b.setIntFlag(0);
		for (Branch b : net.getBranchList())
			b.setIntFlag(0);
	}
	
	public void postProcessing() {
		for (Bus b : net.getBusList())
			assert(b.getIntFlag() != 0);
		for (Branch b : net.getBranchList())
			if (b.getIntFlag() == 0 && b.isActive())
				System.out.println("Active branch not found in the result file, " + b.getId());
	}
	
	@Override
	public boolean processLine(String lineStr) throws InterpssException {
/*
 GENERAL ELECTRIC INTERNATIONAL, INC. - PSLF - V17.0 
   20110729120000,CASE_D:033111-EMSDB:DB53,CASE:012411-EMSDB:DB52, 10           
 VER 26   PARAMETERS INITIALIZED ON 22-Jun-2011 16:45:56 PDT  
*/
		if (lineStr.contains("GENERAL ELECTRIC INTERNATIONAL")) {
			this.headerLineCnt = 1;
			return true;
		}
		if (this.headerLineCnt > 0) {
			this.headerLineCnt++;
			if (this.headerLineCnt == 3)
				this.headerLineCnt = 0;
			return true;
		}
		
		// first step is to verify Base Kva
		// format: System base      100.00 MVA
		if (!baseKvaProcessed) {
			if (lineStr.contains("System base")) {
				String[] strAry = lineStr.trim().split(" +");  // X+ once or more
				IpssLogger.getLogger().info("BaseMva: " + strAry[2]);
				assert("MVA".equals(strAry[3]));
				baseKvaProcessed = true;
			}
		}
		else if (!busDataProcessed) {
/*
         FROM            AR ID   MW   MVAR           TO     ARCK    MW    MVAR    TAP    STP  PLOSS  QLOSS  PCT               
       1 NORTH-01     230 1                      2 NORTH-02     230 1 1   262.4   41.7            7.269  36.34   44           
      226.64  kv                                 3 NORTH-03     230 1 1   261.1   21.3            7.066  35.33   44           
        0.9854pu                               101 NORTH-G1      16 1 1  -523.4  -63.1  230.0  0  0.000  47.71   87           
       -5.1  deg      			
 */
			if (lineStr.contains("FROM") && lineStr.contains("TO")) {
				busRecordBegin = true;
			}
			
			if (busRecordBegin) {
				/*
			    if first 8 chars are bus number, busDataBegin = true
			    if busDataBegin == true and empty line, busDataEnd = true
				*/
				if (busDataEnd && lineStr.length() > 8 &&
						StringUtil.isInt(lineStr.substring(0, 8).trim())) {
					busDataBegin = true;
					busDataEnd = false;
					totalBus++; this.busDataLineNo = 0;
					//IpssLogger.getLogger().info("Processing bus: " + totalBus);
				}
				else if (lineStr.trim().equals("") || lineStr.trim().startsWith("MISMATCH")) {
					// if empty line, start another bus record
					// also we might see
					//                                      MISMATCH            -0.0   -0.1                                                
					busDataEnd = true;
				}
				
				if (busDataBegin && !busDataEnd) {
					//System.out.println(++this.busDataLineNo + ":" + lineStr);
					++this.busDataLineNo;
					if (this.busDataLineNo == 1) {
						this.busNo = new Integer(lineStr.substring(0, 8).trim()).intValue();
					}
					else if (this.busDataLineNo == 3) {
						this.busVoltage = new Double(lineStr.substring(0, 14).trim()).doubleValue();
					}
					else if (this.busDataLineNo == 4) {
						this.busAngle = new Double(lineStr.substring(0, 13).trim()).doubleValue();
					}

					if (this.busDataLineNo == 4) {
						//System.out.println("BusNo, BusV, BusAng: " + this.busNo + ", " + this.busVoltage + ", " + this.busAngle);
						AclfBus bus = this.net.getBus("Bus"+this.busNo);
						assert(bus != null);
						bus.setIntFlag(1);
						
						if (!this.compareNetworkOnly) {
							if (!NumericUtil.equals(this.busVoltage, bus.getVoltageMag(), 0.0001)) {
								String msg = "Bus voltage mag mismatch: Bus-" + this.busNo + ", " + 
										     this.busVoltage + ", " + String.format("%5.4f", bus.getVoltageMag());
								//IpssLogger.getLogger().warning(msg);
								this.errMsgList.add("\n" + msg);
							}
							
							if (!NumericUtil.equals(this.busAngle, bus.getVoltageAng(Unit.UnitType.Deg), 0.1)) {
								String msg = "Bus voltage ang mismatch: Bus-" + this.busNo + ", " + 
												this.busAngle + ", " + String.format("%5.2f", bus.getVoltageAng(Unit.UnitType.Deg));
								//IpssLogger.getLogger().warning(msg);
								this.errMsgList.add("\n" + msg);
							}						
						}
					}
					
					// branch data
					//         TO         ARCK    MW    MVAR    TAP    STP  PLOSS  QLOSS  PCT               
					// 32218 DRUM   3 115 1  1    3.0   31.2                0.330 -1.935   36  
					
					if (!busDataEnd && lineStr.trim().length() > 50 && !lineStr.contains("MISMATCH")) {
						// we might see
						//       -21.5  deg                               MISMATCH             0.0    0.1                                                
						int toBusNo = new Integer(lineStr.substring(39, 46).trim()).intValue();
						String ck = "_"+lineStr.substring(63, 64).trim();
						//System.out.println("To Bus No: " + toBusNo );
						String toBusId = "Bus"+toBusNo,
							   fromBusId = "Bus"+this.busNo;
						if (net.getBranch(fromBusId, toBusId, ck) != null)
							net.getBranch(fromBusId, toBusId, ck).setIntFlag(1);
						else if (net.getBranch(toBusId, fromBusId, ck) != null)
							net.getBranch(toBusId, fromBusId, ck).setIntFlag(1);
					}
				}
			}
		}
		return true;
	}
}
