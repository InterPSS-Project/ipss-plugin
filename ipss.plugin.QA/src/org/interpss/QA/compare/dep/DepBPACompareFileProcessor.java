package org.interpss.QA.compare.dep;

import org.ieee.odm.adapter.bpa.lf.BPABusRecord;
import org.ieee.odm.common.ODMException;
import org.interpss.QA.result.QAAclfBranchRec;
import org.interpss.QA.result.QAAclfBusRec;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;

@Deprecated
public class DepBPACompareFileProcessor extends DepBaseCompareFileProcessor<QAAclfBusRec, QAAclfBranchRec> {
	private boolean busDataProcessed = false,
	                busRecordBegin = false,
	                busRecordEnd = false;
	
	private AclfBus bus = null;
	
	public DepBPACompareFileProcessor(AclfNetwork net) {
		this.net = net;
	}

	public DepBPACompareFileProcessor(AclfNetwork net, boolean netOnly) {
		this(net);
		this.compareNetworkOnly = netOnly;
	}
	
	@Override
	public boolean processLine(String lineStr) throws InterpssException {
		if (!busDataProcessed) {
			if (lineStr.contains("kV        MW      MVAR")) {
				this.busRecordBegin = true;
				
			}
			else if (lineStr.contains("-------  -------          -------  -------  ------  ------  ------")) {
				this.busRecordEnd = true;
				this.busDataProcessed = true;
			}
			else if (this.busRecordBegin && !this.busRecordEnd) {
				if (!lineStr.trim().equals("")) {
/*
                     kV        MW      MVAR 功率因数     MW     MVAR    使用的  存在的  未安排                      PU/度           
   bus-1    100.0   104.0     71.6     27.0  0.94        0.0      0.0     0.0     0.0     0.0    S           1   1.040/   0.0
   ANSH-HLZ 525.0   538.0      0.0      0.0              0.0      0.0  1208.9  1208.9     0.0               DC   1.025/  10.3
 */
					this.totalBus++;
					String busName = lineStr.substring(0, 11);
					try {
						this.busId = BPABusRecord.getBusId(busName);
					} catch (ODMException e) {
						e.printStackTrace();
					}
					//System.out.println(busid.trim() + ":" + lineStr);
					String str = lineStr.substring(113,125);
					String[] ary = str.split("/");
					this.busVoltage = new Double(ary[0].trim()).doubleValue();  // 0.9920PU
					this.busAngle = new Double(ary[1].trim()).doubleValue();
					//System.out.println(this.busId + ", " + this.busVoltage + ", " + this.busAngle);
					
					bus = this.net.getBus(this.busId);
					if (bus == null) {
						IpssLogger.getLogger().severe("bus = null, " + lineStr);
					}
					
					double volt = bus.getVoltageMag();
					if (!NumericUtil.equals(this.busVoltage, volt, 0.001)) {
						String msg = "Bus voltage mag mismatch: " + this.busId + ", " + 
								     this.busVoltage + ", " + String.format("%5.4f(ipss), %4.3f", volt,  
								     Math.abs(100.0*(this.busVoltage - volt)/this.busVoltage)) + "%";
						//IpssLogger.getLogger().warning(msg);
						addErrMsg(msg, lineStr);
					}

					double ang = bus.getVoltageAng(UnitType.Deg);
					if (!NumericUtil.equals(this.busAngle, ang, 0.1)) {
						String msg = "Bus voltage ang mismatch: " + this.busId + ", " + 
										this.busAngle + ", " + String.format("%5.2f(ipss), %4.3f", ang,
										Math.abs(100.0*(this.busAngle - ang)/this.busAngle)) + "%";
						//IpssLogger.getLogger().warning(msg);
						addErrMsg(msg, lineStr);
					}					
				}
			}
		}
		return true;
	}
}
