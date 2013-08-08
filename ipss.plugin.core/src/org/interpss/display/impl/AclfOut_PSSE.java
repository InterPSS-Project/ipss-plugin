/*
 * @(#)AclfOutFunc.java   
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

package org.interpss.display.impl;

import static org.interpss.CorePluginFunction.FormatKVStr;

import org.apache.commons.math3.complex.Complex;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;

/**
 * Aclf output functions, PSS/E format
 * 
 * @author mzhou
 *
 */
public class AclfOut_PSSE {
	public static enum Format {
				GUI, 
				POUT};

	/**
	 * output LF result in the PSS/E format
	 * 					
	 * @param net
	 * @param format
	 * @return
	 */
	public static StringBuffer lfResults(AclfNetwork net, Format format) {
		StringBuffer str = new StringBuffer("");
		try {
			double baseKVA = net.getBaseKva();

			str.append(psseStyleTitle(net, format));

			for (Bus b : net.getBusList()) {
				AclfBus bus = (AclfBus) b;
				if (bus.isActive()) {
					if (format == Format.GUI)
						str.append("\n" + busGUIFormat(bus, baseKVA));
					else
						str.append("\n" + busResults(bus, baseKVA));
						
				}
			}
		} catch (Exception emsg) {
			str.append(emsg.toString());
		}
		return str;
	}

	private static StringBuffer busGUIFormat(AclfBus bus, double baseKVA) {
		StringBuffer str = new StringBuffer("");
		double pu2Mva = baseKVA * 0.001;
		
		String s = "";
		s += String.format(" %7s %-12s%6s %3d %6.4f %6.1f", 
						bus.getNumber(), bus.getName(),
						FormatKVStr.f(bus.getBaseVoltage()*0.001),
						bus.getArea().getNumber(),
						bus.getVoltageMag(), bus.getVoltageAng(UnitType.Deg));
		
		double pgen = 0.0, pload = 0.0, pshunt = 0.0;
		double qgen = 0.0, qload = 0.0, qshunt = 0.0;
		char qchar = ' ';
		if (bus.isGen()) {
			Complex c = bus.getGenResults();
			pgen = pu2Mva * c.getReal();
			qgen = pu2Mva * c.getImaginary();
			// R = regulating, L = low limit, H = high limit
			if (qgen != 0.0)
				qchar = 'R';  //TODO
		}
		if (bus.isLoad()) {
			Complex c = bus.getLoadResults();
			pload = pu2Mva * c.getReal();
			qload = pu2Mva * c.getImaginary();
		}
		if (bus.getShuntY() != null) {
			double factor = bus.getVoltageMag() * bus.getVoltageMag() * baseKVA * 0.001;
			if (bus.getShuntY().getReal() != 0.0)
				pshunt = -bus.getShuntY().getReal() * factor;
			if (bus.getShuntY().getImaginary() != 0.0)
				qshunt = -bus.getShuntY().getImaginary() * factor;
		}
		
		s += String.format(" %7.1f  %7.1f %7.1f", pgen, pload, pshunt);
		
		s += String.format(" -----------------------------------------------------------------------------\n");		
		s += String.format("                             %3d %6s      ",
						bus.getZone().getNumber(), FormatKVStr.f(bus.getVoltageMag(UnitType.kV)));
		
		s += String.format(" %7.1f%s %7.1f %7.1f", qgen, qchar, qload, qshunt);
		
		int cnt = 0;
		for (Branch b : bus.getBranchList()) {
			AclfBranch bra = (AclfBranch) b;
			if (bra.isActive()) {
				s += branchGUIForat(bra, cnt++, bus, baseKVA);
			}
		}		
		return str.append(s);
	}
	
	private static StringBuffer branchGUIForat(AclfBranch branch, int braCnt, AclfBus fromBus, double baseKVA) {
		StringBuffer str = new StringBuffer("");
		
		boolean onFromSide = fromBus.getId().equals(branch.getFromBus().getId());
		AclfBus toBus = onFromSide ? branch.getToAclfBus() : branch.getFromAclfBus();
		
		String s = braCnt == 0? "" : "                                                                       ";
		s += String.format("%7s %-12s%6s %3d %2s",
						toBus.getNumber(), toBus.getName(),
						FormatKVStr.f(toBus.getBaseVoltage()*0.001),
						toBus.getArea().getNumber(), branch.getCircuitNumber());

		Complex pq = onFromSide? branch.powerFrom2To(UnitType.mVar) :
									branch.powerTo2From(UnitType.mVar);
		double p = pq.getReal(), q = pq.getImaginary();
		s += String.format("  %7.1f %7.1f", p, q);

		if (branch.isXfr()) {
			/*
			 * Transformer tap ratio is printed both when the "from bus" is the tapped side and when
				the "to bus" is the tapped side. When the "to bus" is the tapped side, the ratio is followed
				by the flag UN, regardless of whether it is on limit, regulating, or locked. The flags
				printed after the transformer ratio when the "from bus" is the tapped side are these:
			// 		LO When ratio is on low limit.
			// 		HI When ratio is on high limit.
			// 		RG When ratio is in regulating range.
			// 		LK When ratio is locked.
			 */
			double ratio = branch.getToTurnRatio();
			String tapStr = "UN";
			if (onFromSide) {
				ratio = branch.getFromTurnRatio();
				tapStr = "LK"; // TODO
			}
			s += String.format(" %6.3f%s        ", ratio, tapStr);
		}
		else
			s += String.format("                 ");
			
		double rating = branch.getRatingMva1();
		if (rating > 0.0) {
			double loading = 100.0 * pq.abs() / branch.getRatingMva1();
			s += String.format(" %3.0f %5.0f\n", loading, rating);
		}
		else
			s += "\n";
		
		return str.append(s);
	}

	/**
	 * output LF bus result in PSS/E format 
	 * 
	 * @param bus
	 * @param baseKVA
	 * @return
	 */
	public static StringBuffer busResults(AclfBus bus, double baseKVA) {
		StringBuffer str = new StringBuffer("");
		double factor = baseKVA * 0.001;
/*
BUS  10002 GZ-HLZ      220.00 CKT     MW     MVAR     MVA  %I 1.0445PU  -47.34  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X  10002
 */
		String s = String.format("BUS %6d %-12s", bus.getNumber(), bus.getName());
		double baseKV = bus.getBaseVoltage()*0.001;
		if (baseKV > 100.0)
			s += String.format("%6.2f ", baseKV);
		else	
			s += String.format("%6.3f ", baseKV);
		s += String.format("CKT     MW     MVAR     MVA  %2s %6.4fPU %7.2f  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X %6d\n",
							"%I", bus.getVoltageMag(), bus.getVoltageAng(UnitType.Deg), bus.getNumber());
		
		double vkv = bus.getVoltageMag(UnitType.kV);
/*
 FROM GENERATION                    600.0    34.4R  601.0 601 19.095KV               MW     MVAR    1 GD             19 50
 */
		if (bus.isGen() && !bus.isCapacitor()) {
			Complex pq = bus.getGenResults();
			s += formatBusLoad("FROM GENERATION", pq.getReal()*factor, pq.getImaginary()*factor, pq.abs()*factor);
			
			//double iper = 986;
			//s += String.format(" %3.0f ", iper);
			s += "     ";
			
			if (baseKV > 100.0)
				s += String.format("%6.2fKV", vkv);
			else	
				s += String.format("%6.3fKV", vkv);
			s += String.format("               MW     MVAR   %2d %2s             %2d %2s\n", 
					bus.getArea().getNumber(), bus.getArea().getName(), 
					bus.getZone().getNumber(), bus.getZone().getName());
		}
		else {
/*
            229.79KV               MW     MVAR    1 GD              2 XX
*/
			s += "                                                             ";
			if (vkv > 100.0)
				s += String.format("%7.2fKV", vkv);
			else
				s += String.format("%7.3fKV", vkv);

			s += String.format("               MW     MVAR   %2d %2s             %2d %2s\n", 
						bus.getArea().getNumber(), bus.getArea().getName(), 
						bus.getZone().getNumber(), bus.getZone().getName());
		}
/*
 TO SHUNT                             0.0   484.3   484.3
 */
		if (bus.isGen() && bus.isCapacitor() || bus.getShuntY().getImaginary() != 0.0) {
			double p = 0.0, q = 0.0;
			if (bus.isCapacitor())
				q = bus.getGenQ();
			else if (bus.getShuntY() != null) {
				if (bus.getShuntY().getReal() != 0.0)
					p = -bus.getShuntY().getReal();
				q = -bus.getShuntY().getImaginary();
			}
			p *= bus.getVoltageMag() * bus.getVoltageMag() * factor;
			q *= bus.getVoltageMag() * bus.getVoltageMag() * factor;
			s += formatBusLoad("TO SHUNT", p, q, q) + "\n";
		}

/*
 TO LOAD-PQ                           0.0  -104.0   104.0
 */
		if (bus.isLoad()) {
			Complex pq = bus.getLoadResults();
			s += formatBusLoad("TO LOAD-PQ", pq.getReal()*factor, pq.getImaginary()*factor, pq.abs()*factor) + "\n";
		}
		
		for (Branch br : bus.getBranchList()) {
			if (br.isActive()) {
				s += branchResults(br, bus, baseKVA);
			}
		}
		
		str.append(s);
		return str;
	}

	private static String formatBusLoad(String label, double mw, double mvar, double mva) {
		return String.format(" %-16s                 %7.1f %7.1f %7.1f", label, mw, mvar, mva);
	}

	private static StringBuffer branchResults(Branch branch, AclfBus fromBus, double baseKVA) {
		if (branch instanceof AclfBranch) {
			return branchResults((AclfBranch)branch, fromBus, baseKVA);
		}
		else
			return new StringBuffer("");
	}
	
	private static StringBuffer branchResults(AclfBranch branch, AclfBus fromBus, double baseKVA) {
		StringBuffer str = new StringBuffer("");
	
/*
 TO  10193 SHAJIAC=    500.00  1    572.0    19.4   572.3  80 1.0000LK              0.97   72.03    1 GD              2 XX
 TO  10535 SHENZHE=    500.00  1    624.4    62.2   627.5  24                       1.41   16.95    1 GD              2 XX
 */
		boolean onFromSide = fromBus.getId().equals(branch.getFromBus().getId());
		AclfBus toBus = onFromSide ? branch.getToAclfBus() : branch.getFromAclfBus();
		
		String s = String.format(" TO %6d %-12s",	toBus.getNumber(), toBus.getName());
		double vkv = toBus.getBaseVoltage() * 0.001;;
		if (vkv > 100.0)
			s += String.format("%7.2f ", vkv);
		else	
			s += String.format("%7.3f ", vkv);

		Complex pq = onFromSide? branch.powerFrom2To(UnitType.mVar) :
						branch.powerTo2From(UnitType.mVar);
		s += String.format("%-2s %7.1f %7.1f %7.1f ", branch.getCircuitNumber(), 
					pq.getReal(), pq.getImaginary(), pq.abs());

		if (branch.getRatingMva1() > 0.0) {
			double iper = 100.0 * pq.abs() / branch.getRatingMva1();
			s += String.format("%3.0f ", iper);
		}
		else 
			s += "    ";

		// only applies to Xfr
		if (branch.isXfr()) {
			double ratio = branch.getToTurnRatio();
			String tapStr = "UN";
			if (onFromSide) {
				ratio = branch.getFromTurnRatio();
				tapStr = "LK"; // TODO
			}
			s += String.format("%6.4f%2s           ", ratio, tapStr);
		}
		else
			s += String.format("                   ");
			
		Complex loss = branch.loss(UnitType.mVA);
		s += String.format("%7.2f %7.2f   %2d %2s             %2d %2s\n", 
					loss.getReal(), loss.getImaginary(),
					branch.getArea().getNumber(), branch.getArea().getName(), 
					branch.getZone().getNumber(), branch.getZone().getName());
		
		str.append(s);
		return str;
	}

	private static String psseStyleTitle(AclfNetwork net, Format format) {
		String str = "";
		str += "\n\n                                              Load Flow Results\n\n";
		str += AclfOutFunc.maxMismatchToString(net,"                    ") + "\n";

		if (format == Format.GUI) {
			str += "  X------- FROM BUS ------X AREA  VOLT            GEN    LOAD    SHUNT  X---------- TO BUS ----------X                        TRANSFORMER    RATING A\n";
			str += "    BUS# X-- NAME --X BASKV ZONE  PU/KV  ANGLE  MW/MVAR MW/MVAR MW/MVAR   BUS# X-- NAME --X BASKV AREA CKT    MW     MVAR    RATIO   ANGLE   %I   MVA\n";
		}
		
		return str;
	}
}