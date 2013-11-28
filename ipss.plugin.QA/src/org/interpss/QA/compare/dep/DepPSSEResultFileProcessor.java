package org.interpss.QA.compare.dep;

import static com.interpss.common.util.NetUtilFunc.ToBranchId;

import org.interpss.QA.result.QAAclfBranchRec;
import org.interpss.QA.result.QAAclfBusRec;
import org.interpss.QA.result.QAResultContainer;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.common.util.StringUtil;

@Deprecated
public class DepPSSEResultFileProcessor extends DepBaseResultFileProcessor<QAAclfBusRec, QAAclfBranchRec> {
	public static String BusPrefix = "Bus"; 

/*
 * Some times PSS/E result file has a " " off-set at the beginning,
 *  
 *  The following sample has a " " off-set
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
				BraCirId_Begin, BraCirId_End;
	
	private boolean baseKvaProcessed = false, 
			        busDataBegin = false;
	private double mva2pu = 0.01;
	private int fromBusNo = 0,
			    totalBus = 0, 
			    busDataLineNo = 0;
	private QAAclfBusRec curRec = null;
	
	public DepPSSEResultFileProcessor(String str_offset) {
		this.Str_offset = str_offset;
		if (str_offset.equals(" "))
			this.Int_offset = 0;
		else
			this.Int_offset = -1;
			
		initTKN();
		this.qaResultSet = new  QAResultContainer<QAAclfBusRec, QAAclfBranchRec>(100.0);
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
		BraCirId_End  = 34 + Int_offset;;
	}
	
	public int getTotalBus() {
		return this.totalBus;
	}
	
/*
BUS  12020 CLAPHAM     115.00 CKT     MW     MVAR     MVA   % 0.9937PU    7.03  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X  12020
 FROM GENERATION                      0.0    50.0H   50.0  50 114.28KV               MW     MVAR   10 NEW MEXICO    121 ZONENO
xTO LOAD-PQ                           6.2     1.3     6.3                                         
xTO SHUNT                             0.0   -18.5    18.5
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
				this.qaResultSet.setBaseMva(baseMva);
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
				else if (lineStr.contains(TKN_SHUNT)) {
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
		curRec = new QAAclfBusRec(busId);
		this.qaResultSet.setBusResult(busId, curRec);

		curRec.vmag = busVoltage;
		curRec.vang = Math.toRadians(busAngle);

		curRec.strData += lineStr + "\n";
	}
	
	private void procGenInfoLine(String lineStr) {
		if (lineStr.contains(TKN_GEN)) {
			//3:   FROM GENERATION                    980.0   187.3R  997.7 998 26.390KV               MW     MVAR    1                 3 HK
			//3:  FROM GENERATION                   9950.0  3027.0R10400.3  80 20.400KV               MW     MVAR    1                 1 ZONE-001
			double busP = this.mva2pu * new Double(lineStr.substring(BusP_Begin, BusP_End)).doubleValue();  // Load as positive direction
			double busQ = this.mva2pu * new Double(lineStr.substring(BusQ_Begin, BusQ_End)).doubleValue();
				
			curRec.genp = busP; 
			curRec.genq = busQ;

			curRec.strData += lineStr + "\n";
		}
	}
	
	private void procLoadInfoLine(String lineStr) {
		if (lineStr.contains(TKN_LoadPQ)) {
			//5:  TO LOAD-PQ                        1750.0   -56.0  1750.9
			String[] strAry = lineStr.trim().split(" +");  // X+ once or more
			double busP = this.mva2pu * new Double(strAry[2]).doubleValue();  // Load as positive direction
			double busQ = this.mva2pu * new Double(strAry[3]).doubleValue();
				
			curRec.loadp = busP; 
			curRec.loadq = busQ; 

			curRec.strData += lineStr + "\n";
		}
	}
	
	private void procShuntInfoLine(String lineStr) {
		if (lineStr.contains(TKN_SHUNT)) {
			//7:  TO SHUNT                             0.0   114.7   114.7
			double shuntQ = new Double(lineStr.substring(43, 51)).doubleValue();
				
			curRec.shuntq = shuntQ; 

			curRec.strData += lineStr + "\n";
		}
	}

	private void procLineInfoLine(String lineStr) {
		// looking for branch info
		// TO    153 MID230      230.00  1   -301.3   -88.3   314.0  92                       6.11   54.97    1 CENTRAL         3 DISCNT_IN_A1
		
		if (lineStr.startsWith(TKN_BranchLine) && 
				!lineStr.contains(TKN_LoadPQ) &&
				!lineStr.contains(TKN_LoadY) &&
				!lineStr.contains(TKN_SWITCHED_SHUNT) &&
				!lineStr.contains(TKN_SHUNT)) {
			
			String strToBusNo = lineStr.substring(BraToBus_Begin, BraToBus_End);
			String strCirId = lineStr.substring(BraCirId_Begin, BraCirId_End);
			
			if (strToBusNo.trim().equals("3WNDTR")) {
				// TODO 
			}
			else {
				int toBusNo = new Integer(strToBusNo.trim()).intValue();
				String toId = BusPrefix+toBusNo;
				String fromId = BusPrefix+this.fromBusNo;
				String cirId = strCirId.trim();
				
				QAAclfBranchRec braRec = new QAAclfBranchRec(fromId, toId, cirId);
				String braId = ToBranchId.f(fromId, toId, cirId);
				braRec.id = braId;
				
				this.qaResultSet.setBranchResult(braId, braRec);	
			}

			curRec.strData += lineStr + "\n";
		}
	}
	
}
