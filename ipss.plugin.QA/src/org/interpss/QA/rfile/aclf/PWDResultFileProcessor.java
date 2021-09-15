package org.interpss.QA.rfile.aclf;

import static com.interpss.common.util.IpssLogger.ipssLogger;
import static com.interpss.common.util.NetUtilFunc.ToBranchId;

import org.interpss.QA.rfile.BaseResultFileProcessor;
import org.interpss.QA.rfile.QAFileReader;
import org.interpss.datamodel.bean.BaseBranchBean;
import org.interpss.datamodel.bean.DefaultExtBean;
import org.interpss.datamodel.bean.aclf.AclfBranchBean;
import org.interpss.datamodel.bean.aclf.AclfBranchResultBean;
import org.interpss.datamodel.bean.aclf.AclfBusBean;
import org.interpss.datamodel.bean.aclf.AclfNetResultBean;
import org.interpss.datamodel.bean.datatype.ComplexValueBean;

import com.interpss.common.exp.InterpssException;

public class PWDResultFileProcessor extends BaseResultFileProcessor {
	public static enum RecType {BusRec, BranchRec, Dclf_BusRec, Dclf_BranchRec, Dclf_XfrRec};
	
	private int lineCnt = 0;
	public int getLineCnt() { return this.lineCnt; }
	
	private RecType recTyle = RecType.BusRec;
	public void setRecType(RecType type) {
		this.recTyle = type;
		this.lineCnt = 0;
	}
	
	public PWDResultFileProcessor() {
		this.qaResultSet = new AclfNetResultBean<DefaultExtBean, DefaultExtBean>();
		this.qaResultSet.base_kva = 100000.0;
	}
	
	public void load(String busFile, String branchFile) {
		load(busFile, PWDResultFileProcessor.RecType.BusRec);

		load(branchFile, PWDResultFileProcessor.RecType.BranchRec);
	}
	
	public void load(String file, RecType type) {
		this.setRecType(type);
		new QAFileReader(file)
				.processFile(this);		
		ipssLogger.info("Total rec: " + this.getLineCnt() + " of type " + type);
	}
	
	@Override public boolean processLine(String lineStr) throws InterpssException {
		this.lineCnt++;
		if (this.recTyle == RecType.BusRec)
			procBusLine(lineStr);
		else if (this.recTyle == RecType.BranchRec)
			procBranchLine(lineStr);
		else if (this.recTyle == RecType.Dclf_BusRec)
			procDclfBusLine(lineStr);
		else if (this.recTyle == RecType.Dclf_BranchRec)
			procDclfBranchLine(lineStr);
		else if (this.recTyle == RecType.Dclf_XfrRec)
			procDclfXfrLine(lineStr);
		return true;
	}

	private void procDclfXfrLine(String lineStr) throws InterpssException {
	/*
Transformer Records,,,,,,,,,,,,,,,,,,,,
Cust String 1, Cust String 2,  Cust String 3, From Number, From Name,      To Number, To Name,       Circuit, Type,  Status, Tap Ratio, Phase (Deg), XF Auto, Reg Bus, Reg Value, Reg Error,Reg Min,Reg Max,Tap Min,Tap Max,Step Size
Transformer,   ORRINGTN_345_A, A,             31,          ORRINGTN_345_4, 61,        ORRINGTN_1_28, 1,       Fixed, Closed, 1,         0,           No,      0,       0,         0,0,0,0.975,1.025,0.025
0              1               2              3            4               5          6              7        8       9      10         11   
 */
		if (this.lineCnt > 2) {
			String[] sAry = lineStr.split(",");
			String fromId = "Bus" + sAry[3];
			String toId = "Bus" + sAry[5];
			String cirId = sAry[7];
			String shiftAng = sAry[11];

			String braId = ToBranchId.f(fromId, toId, cirId);
			AclfBranchBean<DefaultExtBean> rec = this.qaResultSet.getBranch(braId);
			if (rec == null)
				throw new InterpssException("Xfr rec not found, " + braId);
			
			rec.ang.f = Math.toRadians(getDbl(shiftAng));
		}
	}
	
	private void procDclfBranchLine(String lineStr) throws InterpssException {
		/*
		Line Records,,,,,,,,,,,,,,
Cust String 1, Cust String 2,       Cust String 3, From Number, From Name,      To Number, To Name,           Circuit, Status, Branch Device Type, Xfrmr, MW From, Lim MVA, % of MVA Limit (Max), MW Loss
Breaker,       CHSTRSVC_345_3015-3, 3015-3,        1,           CHSTRSVC_345_3, 2,         CHSTRSVC_345_9999, 1,       Closed, Breaker,            NO,    0,       9999,    0,                    0
0              1                    2              3            4               5          6                  7        8       9                   10     11   

Line Records											
                                                   From Number	From Name	    To Number	To Name	          Circuit	Status	Branch Device Type	Xfrmr	MW From 	Lim MVA	% of MVA Limit (Max)	MW Loss
11980	HQ_P1_P2_345_1	11976	HQ_P1_P2_345_5	1	Closed	Breaker	NO	-1524.37658	9999	15.2	0

		*/
		if (this.lineCnt > 2) {
			String[] sAry = lineStr.split(",");

			String fromId = "Bus" + sAry[3];
			String toId = "Bus" + sAry[5];
			String cirId = sAry[7];
			String status = sAry[8];
			String device_type = sAry[9];
			String frommw = sAry[11];
/*			
			String fromId = "Bus" + sAry[0];
			String toId = "Bus" + sAry[2];
			String cirId = sAry[4];
			String status = sAry[5];
			String device_type = sAry[6];
			String frommw = sAry[8];
*/
			String braId = ToBranchId.f(fromId, toId, cirId);
			AclfBranchResultBean rec = this.qaResultSet.createAclfBranchResultBean(fromId, toId, cirId);

			rec.id = braId;
			if (status.toLowerCase().endsWith("open"))
				rec.status = 0;        // by default the onStatus = true
			
			if (device_type != null) {
				rec.bra_code = device_type.equals("Line") ?   // Line, Breaker, Transformer
						BaseBranchBean.BranchCode.Line : 
							(device_type.equals("Breaker") ? BaseBranchBean.BranchCode.Breaker : 
								BaseBranchBean.BranchCode.Xfr);   
			}
			
			double baseMva = this.qaResultSet.base_kva * 0.001;

			double p = getDbl(frommw) / baseMva; 
			
			rec.flow_f2t.re = p;
			rec.info = lineStr;
		}		
	}	
	
	private void procDclfBusLine(String lineStr) throws InterpssException {
/*
Bus Records,,,,,,,,,
Number,Name,              Area Name,  Nom kV,  Angle (Deg), Load MW, Gen MW, Act G Shunt MW, Area Num, Zone Num
1,     CHSTRSVC_345_3,    NEPEX,      345,     -35.46,      ,        ,       0,              1,        1
*/
		if (this.lineCnt > 2) {
			String[] sAry = lineStr.split(",");
			String busId = "Bus" + sAry[0];
			String vmag = sAry[3];
			String vang = sAry[4];
			String loadp = sAry[5];
			String genp = sAry[6];
			String shuntg = sAry[7];
			
			AclfBusBean rec = this.qaResultSet.createAclfBusBean(busId);
			
			double baseMva = this.qaResultSet.base_kva * 0.001;
			
			// parse the line field
			double vMag = new Double(vmag).doubleValue();
			double vAngDeg = new Double(vang).doubleValue();
			double loadP_pu = getDbl(loadp) / baseMva;
			double genP_pu = getDbl(genp) / baseMva;
			double shuntG_pu = getDbl(shuntg) / baseMva;
			
			//if (vMagPu < 0.001)
			//	System.out.println(busId + " voltage = 0.0");
			
			// set the line field to the rec
			rec.v_mag = vMag;
			rec.v_ang = vAngDeg;
			rec.gen = new ComplexValueBean(genP_pu, 0.0);
			rec.load = new ComplexValueBean(loadP_pu, 0.0);
			rec.shunt = new ComplexValueBean(shuntG_pu, 0.0);
			rec.info = "BusInfo:    " + lineStr;
		}

	}
	
	private void procBusLine(String lineStr) throws InterpssException {
/*
 Bus Records,,,,,,,,,,,,,,,
Number,Name,             Area Name, Nom kV,  PU Volt,  Volt (kV),  Angle (Deg),  Load MW,Load Mvar,Gen MW,Gen Mvar,Switched Shunts Mvar,Act G Shunt MW,Act B Shunt Mvar,Area Num,Zone Num
1,     CHSTRSVC_345_3,   NEPEX,     345,     0,        0,          0,            ,,,,,0,0,1,1
5,     ORRINGTN_345_201, NEPEX,     345,     1.04562,  360.74,     -20.92,       ,,,,,0,0,1,1
*/
		if (this.lineCnt > 2) {
			String[] sAry = lineStr.split(",");
			String busId = "Bus" + sAry[0];
			String vmag = sAry[4];
			String vang = sAry[6];
			String loadp = sAry[7];
			String loadq = sAry[8];
			String genp = sAry[9];
			String genq = sAry[10];
			
			AclfBusBean rec = this.qaResultSet.createAclfBusBean(busId);
			
			double baseMva = this.qaResultSet.base_kva * 0.001;
			
			// parse the line field
			double vMagPu = new Double(vmag).doubleValue();
			double vAngDeg = new Double(vang).doubleValue();
			double loadP_pu = getDbl(loadp) / baseMva;
			double loadQ_pu = getDbl(loadq) / baseMva;
			double genP_pu = getDbl(genp) / baseMva;
			double genQ_pu = getDbl(genq) / baseMva;	
			
			//if (vMagPu < 0.001)
			//	System.out.println(busId + " voltage = 0.0");
			
			// set the line field to the rec
			rec.v_mag = vMagPu;
			rec.v_ang = vAngDeg;
			rec.gen = new ComplexValueBean(genP_pu, genQ_pu);
			rec.load = new ComplexValueBean(loadP_pu, loadQ_pu);
			rec.info = "BusInfo:    " + lineStr;
		}
	}

	private void procBranchLine(String lineStr) throws InterpssException {
/*
 Line Records,,,,,,,,,,,,,,
From Number, From Name,      To Number, To Name,           Circuit,  Status,  Branch Device Type, Xfrmr,  MW From ,Mvar From ,MVA From ,Lim MVA,% of MVA Limit (Max),MW Loss,Mvar Loss
1,           CHSTRSVC_345_3, 2,         CHSTRSVC_345_9999, 1,        Closed,  Breaker,            NO,     0,       0,         0,9999,0,0,0
765	         K_STREET_345_69 768	    K_STREET_345_65	   1	     Closed	  Transformer	      YES	  0	       -149.5	  149.5	180	93.8	0	19.29

*/
		if (this.lineCnt > 2) {
			String[] sAry = lineStr.split(",");
			String fromId = "Bus" + sAry[0];
			String toId = "Bus" + sAry[2];
			String cirId = sAry[4];
			String status = sAry[5];
			String device_type = sAry[6];
			String frommw = sAry[8];
			String frommvar = sAry[9];
			
			String braId = ToBranchId.f(fromId, toId, cirId);
			AclfBranchResultBean rec = this.qaResultSet.createAclfBranchResultBean(fromId, toId, cirId);

			rec.id = braId;
			if (status.toLowerCase().endsWith("open"))
				rec.status = 0;        // by default the status = 1
			
			if (device_type != null) {
				rec.bra_code = device_type.equals("Line") ?   // Line, Breaker, Transformer
						BaseBranchBean.BranchCode.Line : 
							(device_type.equals("Breaker") ? BaseBranchBean.BranchCode.Breaker : 
								BaseBranchBean.BranchCode.Xfr);   
			}
			
			double baseMva = this.qaResultSet.base_kva * 0.001;

			double p = getDbl(frommw) / baseMva; 
			double q = getDbl(frommvar) / baseMva; 
			
			rec.flow_f2t = new ComplexValueBean(p, q);
			rec.info = lineStr;
			
			// also add to the fromBus and toBus
			AclfBusBean fromBus = this.qaResultSet.getBus(fromId);
			if (fromBus != null)
				fromBus.info += "\nBranchInfo: " + lineStr;
			AclfBusBean toBus = this.qaResultSet.getBus(toId);
			if (toBus != null)
				toBus.info += "\nBranchInfo: " + lineStr;
		}		
	}
}
