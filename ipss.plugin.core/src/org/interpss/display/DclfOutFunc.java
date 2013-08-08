/*
 * @(#)DclfOutFunc.java   
 *
 * Copyright (C) 2006 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * @Author Mike Zhou
 * @Version 1.0
 * @Date 11/27/2007
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.display;

import java.util.List;

import org.interpss.datatype.DblBusValue;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.Number2String;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.dclf.DclfAlgorithm;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;

/**
 * Dclf output functions
 * 
 * @author mzhou
 *
 */
public class DclfOutFunc {
	public static double SmallBranchFlowPU = 0.0001;
	
	/**
	 * dclf branch title 
	 * 
	 * @return
	 */
	public static String branchFlowTitle() {
		String str = "\n";
		str += "       FromId->ToId     Power Flow(Mw)   MWLimit    Loading%  Violation\n";
		str += "=========================================================================";
		return str;
		
	}
	
	/**
	 * dclf branch flow details
	 * 
	 * @param algo
	 * @return
	 */
	public static String branchFlow(DclfAlgorithm algo, double threshhold) {
		StringBuffer str = new StringBuffer("\n");
		str.append(branchFlowTitle() + "\n");
		for (Branch bra : algo.getAclfNetwork().getBranchList()) {
			if (bra.isActive()) {
				AclfBranch aclfBra = (AclfBranch)bra;
				double mwFlow = algo.getBranchFlow(aclfBra, UnitType.mW);
				String id = aclfBra.getId();
				double limitMva = aclfBra.getRatingMva1();
				double loading = Math.abs(100*(mwFlow)/limitMva);
				boolean v = Math.abs(mwFlow) > limitMva;
				if (loading >= threshhold) {
					str.append(Number2String.toFixLengthStr(22, id) + "     "	+ String.format("%8.2f",mwFlow));
					str.append("     " + String.format("%8.2f", limitMva)); 
					if (limitMva > 0.0)
						str.append("      " + String.format("%5.1f", loading) + "      " + (v? "x" : " "));
					str.append("\n");
				}
			}
		}		
		return str.toString();
	}

	/**
	 * Out put Dclf voltage angle results
	 * 
	 * @param aclfNet
	 * @param algo
	 * @return
	 */
	public static StringBuffer dclfResults(DclfAlgorithm algo, boolean branchViolation) {
		StringBuffer str = new StringBuffer("\n\n");
		str.append("      DC Loadflow Results\n\n");
		str.append("   Bud Id       VoltAng(deg)     Gen     Load    ShuntG\n");
		str.append("=========================================================\n");
		double baseMva = algo.getAclfNetwork().getBaseKva() * 0.001;
		for (Bus bus : algo.getAclfNetwork().getBusList()) {
			if (bus.isActive()) {
				AclfBus aclfBus = (AclfBus)bus; 
				int n = bus.getSortNumber();
				double angle = algo.getAclfNetwork().isRefBus(bus)?
						0.0 : Math.toDegrees(algo.getBusAngle(n));
				double pgen =  (aclfBus.isRefBus()? algo.getBusPower(aclfBus) : aclfBus.getGenP()) * baseMva; 
				double pload =  aclfBus.getLoadP() * baseMva; 
				double pshunt = aclfBus.getShuntY().getReal() * baseMva; 
				str.append(Number2String.toFixLengthStr(8, bus.getId()) + "        "
						+ String.format("%8.2f     %8.2f %8.2f %8.2f \n", angle, pgen, pload, pshunt));
			}
		}

		str.append(branchFlow(algo, branchViolation? 100.0 : 0.0));
		
		return str;
	}
	
	/**
	 * line outage analysis output title
	 * 
	 * @param name trade name
	 * @param mw trade amount 
	 * @return
	 */
	public static String lineOutageAnalysisTitle(String name, String branchId) {
		String str = "\n\n";
		str += "                   Line Outage '" + name + "'  Branch Id: " + branchId + "\n\n";

		str += "          Branch Id         ShiftFactor      MwFlow     MWLimit   DeratedLimit  Loading%  Violation\n";
		str += "========================================================================================================";
		return str;
	}

	/**
	 * line outage analysis output details
	 * 
	 * @param aclfBra monitoring branch
	 * @param algo dclf algorithm
	 * @param mw trade amount in mw
	 * @param f trade shifting factor
	 * @param dfactor derating factor
	 * @return
	 */
	public static String lineOutageAnalysisBranchFlow(AclfBranch aclfBra, DclfAlgorithm algo, double mw, double f) {
		String str = "";
		double mwFlow = algo.getBranchFlow(aclfBra, UnitType.mW);
		
		double limitMva = aclfBra.getRatingMva1();
		double deratedLimit = limitMva - mw*f;
		boolean v = mwFlow > deratedLimit;
		str += Number2String.toFixLengthStr(22, aclfBra.getId())
					+ "      " + String.format("%9.3f", f) 
					+ "      " + String.format("%8.2f", mwFlow) 
					+ "    " + String.format("%8.2f", limitMva) 
					+ "    " + String.format("%8.2f", deratedLimit); 
			if (deratedLimit > 0.0)
				str += "       " + String.format("%5.1f", 100*(mwFlow)/deratedLimit) 
				+ "      " + (v? "x" : " "); 
		return str;
	}

	/**
	 * trade analysis output title
	 * 
	 * @param name trade name
	 * @param mw trade amount 
	 * @return
	 */
	public static String tradeAnalysisTitle(String name, double mw) {
		String str = "\n\n";
		str += "                    Trade '" + name + "'  Amount (MW) : " + String.format("%8.2f", mw) + "\n\n";

		str += "          Branch Id         ShiftFactor      BaseCaseMw   PredictedMW    MWLimit    Loading%  Violation\n";
		str += "========================================================================================================";
		return str;
	}

	/**
	 * trade analysis output details
	 * 
	 * @param aclfBra monitoring branch
	 * @param algo dclf algorithm
	 * @param mw trade amount in mw
	 * @param f trade shifting factor
	 * @param dfactor derating factor
	 * @return
	 */
	public static String tradeAnalysisBranchFlow(AclfBranch aclfBra, DclfAlgorithm algo, 
							double mw, double f, double dfactor) {
		String str = "";
		double mwFlow = algo.getBranchFlow(aclfBra, UnitType.mW);
		
		double newMva = mwFlow + mw * f;
		double limitMva = aclfBra.getRatingMva1() * dfactor;
		boolean v = newMva > limitMva;
		str += Number2String.toFixLengthStr(22, aclfBra.getId())
					+ "      " + String.format("%9.3f", f) 
					+ "        " + String.format("%8.2f", mwFlow) 
					+ "      " + String.format("%8.2f", newMva) 
					+ "    " + String.format("%8.2f", limitMva); 
			if (limitMva > 0.0)
				str += "       " + String.format("%5.1f", 100*(newMva)/limitMva) 
				+ "      " + (v? "x" : " "); 
		return str;
	}

	/**
	 * Output GSF branch flow analysis results 
	 * 
	 * @param net
	 * @param braIntId
	 * @param gsfList
	 * @return
	 */
	public static StringBuffer gsfBranchInterfaceFlow(AclfNetwork net, String braIntId, List<DblBusValue> gsfList, boolean outage) {
		StringBuffer buffer = new StringBuffer();		

		if (braIntId != null)
			buffer.append("Monitor Branch/interface : " + braIntId + "\n\n");
		
		if (outage)
			buffer.append("         Gen         Injection   EquivGSF   FlowContrib\n");
		else
			buffer.append("         Gen         Injection      GSF     FlowContrib\n");
		
		buffer.append("        BusId           (MW)                    (MW)\n");
		buffer.append("    --------------  -----------  --------   -----------\n");
		if (gsfList.size() > 0) {
			double mva = net.getBaseKva() * 0.001;
			for (DblBusValue b : gsfList) {
				double inj = mva * b.bus.getGenP(); 
				buffer.append(String.format("    %-15s   %8.2f   %8.4f    %8.2f\n", b.bus.getId(), inj, b.value, inj*b.value));
			}
		}
		else
			buffer.append("     No branch flow >= " + SmallBranchFlowPU);
			
		return buffer;
	}	
}