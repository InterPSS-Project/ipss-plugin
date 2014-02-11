package org.interpss.QA.rfile.aclf;

import org.interpss.QA.rfile.BaseResultFileProcessor;
import org.interpss.datamodel.bean.BaseBranchBean.BranchCode;
import org.interpss.datamodel.bean.aclf.AclfBranchResultBean;
import org.interpss.datamodel.bean.aclf.AclfBusBean;
import org.interpss.datamodel.bean.aclf.AclfNetResultBean;
import org.interpss.datamodel.bean.datatype.ComplexBean;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.common.util.NetUtilFunc;
import com.interpss.common.util.StringUtil;

/*
 * Class for implementing PSS/E result file loader. The info in the result file are stored in the
 * qaResultSet
 * 
 */
public class PSSEResultFileProcessor extends BaseResultFileProcessor {
	public static String BusPrefix = "Bus"; 

/*
 * Some times PSS/E result file has a " " off-set at the beginning,
 *  
 *  The following sample has a " " off-set
 0----1----0----2----0----3----0----4----0----5----0----6----0----7----0----8----0----9----0----0----0----1----0----2----0----3----0----4----0
 BUS      1 EQN-U1      13.800 CKT     MW     MVAR     MVA   % 1.0000PU    0.00  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X      1
  FROM GENERATION                   1841.7    11.7R 1841.7  84 13.800KV               MW     MVAR    1 NORTE           1 CENTRAL
  TO   1010 FIC-EQ-NORTE230.00  1   1841.7    11.7  1841.7  46 1.0000UN              0.00   16.96    1 NORTE           1 CENTRAL

 *  The following sample does not have a " " off-set
BUS      1 EQN-U1      13.800 CKT     MW     MVAR     MVA   % 1.0000PU    0.00  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X      1
 FROM GENERATION                   1841.7    11.7R 1841.7  84 13.800KV               MW     MVAR    1 NORTE           1 CENTRAL
 TO   1010 FIC-EQ-NORTE230.00  1   1841.7    11.7  1841.7  46 1.0000UN              0.00   16.96    1 NORTE           1 CENTRAL

 */
	private String Str_offset = "";   	// " " or ""
	private int Int_offset = -1;        // 0 if off_set = " " or -1 if off-set = ""
	
	private String TKN_BUSLine;
	private String TKN_BranchLine;
	private String TKN_LoadPQ 			= "TO LOAD-PQ";
	private String TKN_LoadY	 		= "TO LOAD-Y";
	private String TKN_GEN 				= "FROM GENERATION";
	private String TKN_SHUNT 			= "TO SHUNT";
	private String TKN_SWITCHED_SHUNT 	= "TO SWITCHED SHUNT";
	
	private int BusNo_Begin, BusNo_End,
	    		BusVolt_Begin, BusVolt_End,
	    		BusAng_Begin, BusAng_End,
	    		
	    		BusP_Begin, BusP_End,
	    		BusQ_Begin, BusQ_End,	    
	
				BraToBus_Begin, BraToBus_End,
				BraCirId_Begin, BraCirId_End,
				Tap_Begin, Tap_End;
	
	private boolean baseKvaProcessed = false, 
			        busDataBegin = false;
	private double mva2pu = 0.01;
	private int fromBusNo = 0,
			    totalBus = 0, 
			    busDataLineNo = 0;
	private AclfBusBean curRec = null;
	
	public PSSEResultFileProcessor(String str_offset, Version ver) {
		this.version = ver;
				
		this.Str_offset = str_offset;
		if (str_offset.equals(" "))
			this.Int_offset = 0;
		else
			this.Int_offset = -1;
			
		initTKN();
		this.qaResultSet = new AclfNetResultBean();
		this.qaResultSet.base_kva = 100000.0;
	}

	private void initTKN() {
		TKN_BUSLine = Str_offset + "BUS";
		TKN_BranchLine = Str_offset + " TO";
		
		BusNo_Begin = 4 + Int_offset;
		BusNo_End = 11 + Int_offset;
		BusVolt_Begin = 63 + Int_offset;
		BusVolt_End = 69 + Int_offset;
		BusAng_Begin = 73 + Int_offset;
		BusAng_End = 79 + Int_offset;
		
		BusP_Begin = 36 + Int_offset;
    	BusP_End = 43 + Int_offset;
    	BusQ_Begin = 44 + Int_offset;
    	BusQ_End = 50 + Int_offset;	    		

    	BraToBus_Begin = 4 + Int_offset;
    	BraToBus_End  = 11 + Int_offset;
		BraCirId_Begin  = 31 + Int_offset;
		BraCirId_End  = 34 + Int_offset;
		Tap_Begin  = 63 + Int_offset;
		Tap_End  = 69 + Int_offset;
	}
	
	public int getTotalBus() {
		return this.totalBus;
	}
	
/*
BUS  12020 CLAPHAM     115.00 CKT     MW     MVAR     MVA   % 0.9937PU    7.03  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X  12020
 FROM GENERATION                      0.0    50.0H   50.0  50 114.28KV               MW     MVAR   10 NEW MEXICO    121 ZONENO
xTO LOAD-PQ                           6.2     1.3     6.3                                         
xTO SHUNT                             0.0   -18.5    18.5
xTO SWITCHED SHUNT                    0.0   -27.9    27.9
xTO  12015 CEBOLLA     69.000  1     -4.8    -0.2     4.8  15                       0.07    0.07   10 NEW MEXICO    121 ZONENO
*/
	@Override public boolean processLine(String lineStr) throws InterpssException {

		// first step is to verify Base Kva
		// format:         100.0                                     RATING   %MVA FOR TRANSFORMERS
		if (!baseKvaProcessed) {
			if (lineStr.length() > 38 && StringUtil.isDouble(lineStr.substring(0, 38).trim())) {
				double baseMva = StringUtil.getDouble(lineStr.substring(0, 38).trim());
				this.mva2pu = 1.0 / baseMva;
				IpssLogger.getLogger().info("BaseMva: " + baseMva);
				baseKvaProcessed = true;
				this.qaResultSet.base_kva = baseMva * 1000.0;
			}
		}

// BUS      2 BUS002      500.00 CKT     MW     MVAR     MVA   % 0.9920PU  -29.93  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X      2               
		if (lineStr.startsWith(TKN_BUSLine)) {
			busDataBegin = true;
			totalBus++; 
			this.busDataLineNo = 0;
			//IpssLogger.getLogger().info("Processing bus: " + totalBus);
		}

		if (busDataBegin) {
			++this.busDataLineNo;
			//System.out.println(this.busDataLineNo + ":" + lineStr);
			if (this.busDataLineNo == 1) {
				procBusInfoLine(lineStr);
			}
			else if (this.busDataLineNo > 1) {
				if (lineStr.contains(TKN_GEN)) {
					procGenInfoLine(lineStr);
				}
				else if (lineStr.contains(TKN_LoadPQ)) {
					procLoadInfoLine(lineStr);
				}
				else if (lineStr.contains(TKN_SHUNT) || lineStr.contains(TKN_SWITCHED_SHUNT)) {
					procShuntInfoLine(lineStr);
				}
				else { 
					procLineInfoLine(lineStr);
				}
			}
		}
		
		return true;
	}
	
	private void procBusInfoLine(String lineStr) {
		//1: BUS      2 BUS002      500.00 CKT     MW     MVAR     MVA   % 0.9920PU  -29.93  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X      2
		String str = lineStr.substring(BusNo_Begin, BusNo_End);
		fromBusNo = new Integer(str.trim()).intValue();
		str = lineStr.substring(BusVolt_Begin,BusVolt_End);
		double busVoltage = new Double(str.trim()).doubleValue();  // 0.9920PU
		str = lineStr.substring(BusAng_Begin,BusAng_End);
		double busAngle = new Double(str.trim()).doubleValue();    // deg
		//System.out.println(this.busNo + ", " + this.busVoltage + ", " + this.busAngle);
		
		String busId = BusPrefix+fromBusNo;
		curRec = this.qaResultSet.createAclfBusBean(busId);

		curRec.v_mag = busVoltage;
		curRec.v_ang = busAngle;

		curRec.info += lineStr + "\n";
	}
	
	private void procGenInfoLine(String lineStr) {
		if (lineStr.contains(TKN_GEN)) {
			//3:   FROM GENERATION                    980.0   187.3R  997.7 998 26.390KV               MW     MVAR    1                 3 HK
			//3:  FROM GENERATION                   9950.0  3027.0R10400.3  80 20.400KV               MW     MVAR    1                 1 ZONE-001
			double busP = this.mva2pu * new Double(lineStr.substring(BusP_Begin, BusP_End)).doubleValue();  // Load as positive direction
			double busQ = this.mva2pu * new Double(lineStr.substring(BusQ_Begin, BusQ_End)).doubleValue();
				
			curRec.gen = new ComplexBean(busP, busQ);

			curRec.info += lineStr + "\n";
		}
	}
	
	private void procLoadInfoLine(String lineStr) {
		if (lineStr.contains(TKN_LoadPQ)) {
			//5:  TO LOAD-PQ                        1750.0   -56.0  1750.9
			String[] strAry = lineStr.trim().split(" +");  // X+ once or more
			double busP = this.mva2pu * new Double(strAry[2]).doubleValue();  // Load as positive direction
			double busQ = this.mva2pu * new Double(strAry[3]).doubleValue();
				
			curRec.load = new ComplexBean(busP, busQ); 

			curRec.info += lineStr + "\n";
		}
	}
	
	private void procShuntInfoLine(String lineStr) {
		if (lineStr.contains(TKN_SHUNT) || lineStr.contains(TKN_SWITCHED_SHUNT)) {
			// TO SWITCHED SHUNT                    0.0   -27.9    27.9
			// TO SHUNT                             0.0   114.7   114.7
			double shuntQ = new Double(lineStr.substring(43, 51)).doubleValue();
				
			curRec.shunt = new ComplexBean(0.0, shuntQ); 

			curRec.info += lineStr + "\n";
		}
	}

	private void procLineInfoLine(String lineStr) {
		// looking for branch info
		//  TO    153 MID230      230.00  1   -301.3   -88.3   314.0  92                       6.11   54.97    1 CENTRAL         3 DISCNT_IN_A1
		//  TO 3WNDTR 3WNDSTAT1    WND 2  1   -100.3   -34.9   106.3   9 1.0500LK              0.03    1.16
		
		if (lineStr.startsWith(TKN_BranchLine) && 
				!lineStr.contains(TKN_LoadPQ) &&
				!lineStr.contains(TKN_LoadY) &&
				!lineStr.contains(TKN_SWITCHED_SHUNT) &&
				!lineStr.contains(TKN_SHUNT)) {
			
			String strToBusNo = lineStr.substring(BraToBus_Begin, BraToBus_End);
			String strCirId = lineStr.substring(BraCirId_Begin, BraCirId_End);
			
			String fromId = BusPrefix+this.fromBusNo;
			String cirId = strCirId.trim();
			String toId;
			if (strToBusNo.trim().equals("3WNDTR")) {
				toId = strToBusNo.trim();
			}
			else {
				int toBusNo = new Integer(strToBusNo.trim()).intValue();
				toId = BusPrefix+toBusNo;
			}
			
			String braId = NetUtilFunc.ToBranchId.f(fromId, toId, cirId);
			boolean existingBranch = this.qaResultSet.getBranch(braId) != null;
			AclfBranchResultBean braRec = existingBranch ? this.qaResultSet.getBranch(braId) :
											this.qaResultSet.createAclfBranchResultBean(fromId, toId, cirId);

			String tapStr1 = lineStr.substring(Tap_Begin, Tap_End);
			String tapStr2 = lineStr.substring(Tap_End, Tap_End+2); 
			
			if (tapStr1.trim().equals("") && !existingBranch) {
				braRec.bra_code = BranchCode.Line;
			}
			else {
				// TODO detect PsXfr branch
				if (existingBranch) {
					if (tapStr2.equals("UN")) {
						braRec.ratio.f = new Double(tapStr1).doubleValue();
					}
					else {
						braRec.ratio.t = new Double(tapStr1).doubleValue();
					}					
				}
				else {
					braRec.bra_code = BranchCode.Xfr;
					if (!tapStr2.equals("UN")) {
						braRec.ratio.f = new Double(tapStr1).doubleValue();
					}
					else {
						braRec.ratio.t = new Double(tapStr1).doubleValue();
					}
				}
			}
					
			curRec.info += lineStr + "\n";
		}
	}
	
}
