/*
 * @(#)DStabOutFunc.java   
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

package org.interpss.dstab.output;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.Number2String;

import com.interpss.common.datatype.Constants;
import com.interpss.core.aclf.adpter.AclfCapacitorBus;
import com.interpss.core.aclf.adpter.AclfGenBus;
import com.interpss.core.net.Bus;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineType;

public class DStabOutFunc {
	public static String getStateTitleStr() {
		String str = "Time        MachId         Angle        Speed          Pe           Pm        Voltage        E/Eq1        Efd        Vs(pss)\n"
				   + "-----    -------------   ----------   ----------   ----------   ----------   ----------   ----------   ----------   ----------";
		return str;
	}

	@SuppressWarnings("unchecked")
	public static String getStateStr(Hashtable<String, Object> table)
			throws Exception {
		boolean strFmt = true;
		if (table.get(DStabOutSymbol.OUT_SYMBOL_TIME) instanceof Double)
			strFmt = false;

		String str = "";
		double time = strFmt ? new Double((String) table
				.get(DStabOutSymbol.OUT_SYMBOL_TIME)).doubleValue()
				: ((Double) table.get(DStabOutSymbol.OUT_SYMBOL_TIME))
						.doubleValue();
		str += Number2String.toStr(time, "00.000") + " ";

		str += Number2String.toStr(15, (String) table
				.get(DStabOutSymbol.OUT_SYMBOL_MACH_ID))
				+ " ";

		double angle = strFmt ? new Double((String) table
				.get(DStabOutSymbol.OUT_SYMBOL_MACH_ANG)).doubleValue()
				: ((Double) table.get(DStabOutSymbol.OUT_SYMBOL_MACH_ANG))
						.doubleValue();
		str += Number2String.toStr(10, Number2String.toStr(angle, "0.0000"))
				+ "   ";

		double speed = strFmt ? new Double((String) table
				.get(DStabOutSymbol.OUT_SYMBOL_MACH_SPEED)).doubleValue()
				: ((Double) table.get(DStabOutSymbol.OUT_SYMBOL_MACH_SPEED))
						.doubleValue();
		str += Number2String.toStr(10, Number2String.toStr(speed, "0.0000"))
				+ "   ";

		double pe = strFmt ? new Double((String) table
				.get(DStabOutSymbol.OUT_SYMBOL_MACH_PE)).doubleValue()
				: ((Double) table.get(DStabOutSymbol.OUT_SYMBOL_MACH_PE))
						.doubleValue();
		str += Number2String.toStr(10, Number2String.toStr(pe, "0.0000"))
				+ "   ";

		double pm = strFmt ? new Double((String) table
				.get(DStabOutSymbol.OUT_SYMBOL_MACH_PM)).doubleValue()
				: ((Double) table.get(DStabOutSymbol.OUT_SYMBOL_MACH_PM))
						.doubleValue();
		str += Number2String.toStr(10, Number2String.toStr(pm, "0.0000"))
				+ "   ";

		double volt = strFmt ? new Double((String) table
				.get(DStabOutSymbol.OUT_SYMBOL_BUS_VMAG)).doubleValue()
				: ((Double) table.get(DStabOutSymbol.OUT_SYMBOL_BUS_VMAG))
						.doubleValue();
		str += Number2String.toStr(10, Number2String.toStr(volt, "0.0000"))
				+ "   ";

		if (table.get(DStabOutSymbol.OUT_SYMBOL_MACH_E) != null) {
			double e = strFmt ? new Double((String) table
					.get(DStabOutSymbol.OUT_SYMBOL_MACH_E)).doubleValue()
					: ((Double) table.get(DStabOutSymbol.OUT_SYMBOL_MACH_E))
							.doubleValue();
			str += Number2String.toStr(10, Number2String.toStr(e, "0.0000"))
					+ "   ";
		} else if (table.get(DStabOutSymbol.OUT_SYMBOL_MACH_EQ1) != null) {
			double eq1 = strFmt ? new Double((String) table
					.get(DStabOutSymbol.OUT_SYMBOL_MACH_EQ1)).doubleValue()
					: ((Double) table.get(DStabOutSymbol.OUT_SYMBOL_MACH_EQ1))
							.doubleValue();
			str += Number2String.toStr(10, Number2String.toStr(eq1, "0.0000"))
					+ "   ";
		} else
			str += "     -       ";

		if (table.get(Constants.Token_ExciterState) != null) {
			Hashtable<String, Object> excStatess = (Hashtable<String, Object>) table
					.get(Constants.Token_ExciterState);
			double efd = strFmt ? new Double((String) excStatess
					.get(DStabOutSymbol.OUT_SYMBOL_EXC_EFD)).doubleValue()
					: ((Double) excStatess
							.get(DStabOutSymbol.OUT_SYMBOL_EXC_EFD))
							.doubleValue();
			str += Number2String.toStr(10, Number2String.toStr(efd, "0.0000"))
					+ "   ";
		} else
			str += "     -       ";

		if (table.get(Constants.Token_StabilizerState) != null) {
			Hashtable<String, Object> pssStatess = (Hashtable<String, Object>) table
					.get(Constants.Token_StabilizerState);
			double pssVs = strFmt ? new Double((String) pssStatess
					.get(DStabOutSymbol.OUT_SYMBOL_PSS_VS)).doubleValue()
					: ((Double) pssStatess
							.get(DStabOutSymbol.OUT_SYMBOL_PSS_VS))
							.doubleValue();
			str += Number2String
					.toStr(10, Number2String.toStr(pssVs, "0.0000"))
					+ "   ";
		} else
			str += "     -       ";

		str += "\n";
		return str;
	}

	public static String initConditionSummary(DynamicSimuAlgorithm algo) {
		DStabilityNetwork net = algo.getDStabNet();
		StringBuffer str = new StringBuffer("");
		try {
			double refAng = 0.0;
			Machine refMach = algo.getRefMachine();
			if (refMach != null)
				refAng = Math.toDegrees(refMach.getAngle());

			str.append("\n                          Initial Condition Summary\n");
			str.append("     BusID     Volt(pu)     Angle(deg)   P(pu)     Q(pu)   Mach Model     PowerAng(deg)\n");
			str.append("  -------------------------------------------------------------------------------------\n");

			for (Bus b : net.getBusList()) {
				DStabBus bus = (DStabBus) b;
				AclfGenBus genBus = bus.toGenBus();
				Complex busPQ = genBus.getGenResults(UnitType.PU);
				busPQ = busPQ.subtract(genBus.getLoadResults(UnitType.PU));
				if (bus.isCapacitor()) {
					AclfCapacitorBus cap = bus.toCapacitorBus();
					busPQ = busPQ.add(new Complex(0.0, cap.getQResults(UnitType.PU)));
				}
				str.append(Number2String.toStr(2, " "));
				str.append(Number2String.toStr(-12, bus.getId()) + "  ");
				str.append(Number2String.toStr(bus.getVoltageMag(UnitType.PU), "###0.000") + " ");
				str.append(Number2String.toStr((bus.getVoltageAng(UnitType.Deg))- refAng, "######0.0")	+ "  ");
				str.append(Number2String.toStr(busPQ.getReal(), "####0.0000"));
				str.append(Number2String.toStr(busPQ.getImaginary(), "####0.0000")
						+ "  ");
				if (bus.getMachine() != null) {
					Machine mach = bus.getMachine();
					str.append(machModelStr(mach) + "   ");
					str.append(Number2String.toStr(Math.toDegrees(mach.getAngle()) - refAng, "####0.0"));
				} else if (bus.getScriptDynamicBusDevice() != null) {
					// Machine mach = bus.getMachine();
					str.append("Dyn Bus Device   " + " ");
				}
				/*
				 * else if (bus.getScriptDBusDevice() != null) { //Machine mach =
				 * bus.getMachine(); str.append("Script Bus Device" + " "); }
				 */
				str.append("\n");
			}
		} catch (Exception emsg) {
			str.append(emsg.toString());
		}
		return str.toString();
	}

	private static String machModelStr(Machine mach) {
		if (mach.getMachType() == MachineType.ECONSTANT)
			return "     E-Constant";
		else if (mach.getMachType() == MachineType.EQ1_MODEL)
			return "      Eq1 Model";
		else if (mach.getMachType() == MachineType.EQ1_ED1_MODEL)
			return "  Eq1 Ed1 Model";
		else if (mach.getMachType() == MachineType.EQ11_SALIENT_POLE)
			return "E11 SalinetPole";
		else if (mach.getMachType() == MachineType.EQ11_ED11_ROUND_ROTOR)
			return " E11 RoundRotor";
		return "    Not Defined";
	}
}
