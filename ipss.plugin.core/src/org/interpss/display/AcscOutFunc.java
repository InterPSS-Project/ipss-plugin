/*
 * @(#)AcscOutFunc.java   
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

import static com.interpss.common.util.IpssLogger.ipssLogger;

import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.Number2String;

import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.fault.AcscBranchFault;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.SimpleFaultAlgorithm;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;

/**
 * Acsc output functions
 * 
 * @author mzhou
 *
 */
public class AcscOutFunc {
	/**
	 * output Acsc analysis results
	 * 
	 * @param faultNet
	 * @param algo
	 * @return
	 */
	public static String faultResult2String(AcscNetwork faultNet, SimpleFaultAlgorithm algo) {
		StringBuffer sb = new StringBuffer();
		ipssLogger.fine(
				"# of Fault objects = " + algo.getFaultList().size());
		for (Object fault : algo.getFaultList()) {
			if (fault instanceof AcscBranchFault) {
				AcscBranchFault fBranch = (AcscBranchFault) fault;
				sb.append(branchFaultInfo(fBranch, faultNet));
			} else {
				AcscBusFault fBus = (AcscBusFault) fault;
				sb.append(busFaultInfo(fBus, faultNet));
			}
		}
		return sb.toString();
	}

	private static String branchFaultInfo(AcscBranchFault bf, AcscNetwork net) {
		StringBuffer str = new StringBuffer("");
		try {
			double baseKVA = net.getBaseKva();
			// fault branch is always a line/feeder branch
			double baseV = bf.getFaultBranch().getFromAcscBus().getBaseVoltage();

			str.append("\n\n              Branch Fault Info\n");
			str.append("              =================\n\n");

			str.append("          Fault Id:      " + bf.getId() + "\n");
			str.append("          Fault type:    " + bf.getFaultCode() + "\n");
			str.append("          Branch name:   "
					+ bf.getFaultBranch().getName() + "\n");
			str.append("          Fault type:    " + bf.getFaultCode() + "\n");

			
			str.append(faultCurrent(bf, baseKVA, baseV));

			str.append(displayBusVoltage(bf));
			str.append(displayBranchCurrent(bf));
		} catch (Exception e) {
			IpssLogger.logErr(e);
		}
		return str.toString();
	}

	private static String busFaultInfo(AcscBusFault bf, AcscNetwork net) {
		StringBuffer str = new StringBuffer("");
		try {
			double baseKVA = net.getBaseKva();
			double baseV = bf.getAcscBus().getBaseVoltage();

			str.append("\n\n              Bus Fault Info\n");
			str.append("              ==============\n\n");

			str.append("          Fault Id:      " + bf.getId() + "\n");
			str.append("          Bus name:      " + bf.getAcscBus().getName() + "\n");
			str.append("          Fault type:    " + bf.getFaultCode() + "\n");
			
			str.append(faultCurrent(bf, baseKVA, baseV));
			
			str.append(displayBusVoltage(bf));
			str.append(displayBranchCurrent(bf));
		} catch (Exception e) {
			IpssLogger.logErr(e);
		}
		return str.toString();
	}

	private static String faultCurrent(AcscBusFault bf, double baseKVA, double baseV) throws Exception {
		StringBuffer str = new StringBuffer("");
		if (bf.getFaultCode() == SimpleFaultCode.GROUND_3P) {
			Complex ipu = bf.getFaultResult().getSCCurrent_012().b_1;
			Complex amp = bf.getFaultResult().getSCCurrent_012(UnitType.Amp, baseV,	baseKVA).b_1;

			str.append("          Fault current: " + ComplexFunc.toMagAng(ipu) + " pu");
			str.append("    " + String.format("%5.2f", amp.abs()) + " amps\n");

			if (bf.getFaultResult().getZFault(UnitType.Ohm, baseV, baseKVA).abs() > 0.0)
				str.append("          Fault z:       "
						+ String.format("%3.2f", bf.getFaultResult().getZFault(UnitType.Ohm, baseV, baseKVA).abs()) + " ohms\n");
		}
		else {
			Complex ipua = bf.getFaultResult().getSCCurrent_abc().a_0;
			Complex ampa = bf.getFaultResult().getSCCurrent_abc(UnitType.Amp, baseV,	baseKVA).a_0;
			Complex ipub = bf.getFaultResult().getSCCurrent_abc().b_1;
			Complex ampb = bf.getFaultResult().getSCCurrent_abc(UnitType.Amp, baseV,	baseKVA).b_1;
			Complex ipuc = bf.getFaultResult().getSCCurrent_abc().c_2;
			Complex ampc = bf.getFaultResult().getSCCurrent_abc(UnitType.Amp, baseV,	baseKVA).c_2;

			str.append("          Fault current (A): " + ComplexFunc.toMagAng(ipua) + " pu");
			str.append("    " + String.format("%5.2f", ampa.abs()) + " amps\n");
			str.append("          Fault current (B): " + ComplexFunc.toMagAng(ipub) + " pu");
			str.append("    " + String.format("%5.2f", ampb.abs()) + " amps\n");
			str.append("          Fault current (C): " + ComplexFunc.toMagAng(ipuc) + " pu");
			str.append("    " + String.format("%5.2f", ampc.abs()) + " amps\n");
		}
		return str.toString();
	}
	
	private static String displayBusVoltage(AcscBusFault bf) {
		AcscNetwork net = bf.getFaultResult().getAcscNet();
		try {
			bf.getFaultResult().calBranchCurrent();
			bf.getFaultResult().calContributingCurrent();
		} catch (Exception e) {
			IpssLogger.logErr(e);
			return e.toString();
		}

		StringBuffer str = new StringBuffer("");
		if (bf.getFaultCode() == SimpleFaultCode.GROUND_3P) {
			str.append("\n");
			str.append("      BusID            FaultVoltage            ContribAmps\n");
			str.append("                    (pu)          (volts)      (pu)       (amps)\n");
			str.append("     --------  ---------------   --------   --------   ----------\n");
			for (Bus b : net.getBusList()) {
				AcscBus bus = (AcscBus) b;
				str.append("     " + Number2String.toStr(-8, bus.getId()) + "   ");
				Complex v1 = bf.getFaultResult().getBusVoltage_012(bus).b_1;
				double vpu = v1.abs();
				Complex3x1 ampPu = bf.getFaultResult().getBusContriAmps_012(bus);
				Complex3x1 amps = bf.getFaultResult().getBusContriAmps_012(bus, UnitType.Amp,
						bus.getBaseVoltage(), net.getBaseKva());
				str.append(Number2String.toStr(-15, ComplexFunc.toMagAng(v1)));
				str.append(Number2String.toStr("#######0.#", vpu
						* bus.getBaseVoltage()) + " ");
				str.append(Number2String.toStr("#####0.###", ampPu.b_1.abs())
						+ "   ");
				str.append(Number2String.toStr("#######0.#", amps.b_1.abs())
						+ "\n");
			}
		} else {
			str.append("\n\n");
			str.append("      BusID            FaultVolt(1)                 FaultVolt(0)                FaultVolt(2)\n");
			str.append("                    (pu)          (volts)         (pu)         (volts)         (pu)         (volts)\n");
			str.append("     --------  ---------------   --------   ---------------   --------   ---------------   --------\n");
			
			for (Bus b : net.getBusList()) {
				AcscBus bus = (AcscBus) b;
				str.append("     " + Number2String.toStr(-8, bus.getId()) + " ");
				double vbase = bus.getBaseVoltage();
				Complex3x1 v012 = bf.getFaultResult().getBusVoltage_012(bus);
				double vpu1 = v012.b_1.abs();
				str.append(ComplexFunc.toMagAng(v012.b_1));
				str.append(Number2String.toStr("#######0.#", vpu1*vbase)	+ "   ");
				double vpu0 = v012.a_0.abs();
				str.append(ComplexFunc.toMagAng(v012.a_0));
				str.append(Number2String.toStr("#######0.#", vpu0*vbase)	+ "   ");
				double vpu2 = v012.c_2.abs();
				str.append(ComplexFunc.toMagAng(v012.c_2));
				str.append(Number2String.toStr("#######0.#", vpu2*vbase)	+ "\n");
			}

			str.append("\n\n");
			str.append("      BusID            FaultVolt(a)                 FaultVolt(b)                 FaultVolt(c)\n");
			str.append("                    (pu)          (volts)         (pu)         (volts)         (pu)         (volts)\n");
			str.append("     --------  ---------------   --------   ---------------   --------   ---------------   --------\n");
			for (Bus b : net.getBusList()) {
				AcscBus bus = (AcscBus) b;
				str.append("     " + Number2String.toStr(-8, bus.getId()) + " ");
				Complex3x1 v012 = bf.getFaultResult().getBusVoltage_012(bus);
				Complex3x1 vabc = Complex3x1.z12_to_abc(v012);
				double vphase = bus.getBaseVoltage() / NumericConstant.SqrtRoot3;
				double vpu1 = vabc.a_0.abs();
				str.append(ComplexFunc.toMagAng(vabc.b_1));
				str.append(Number2String.toStr("#######0.#", vpu1*vphase) + "   ");
				double vpu0 = vabc.b_1.abs();
				str.append(ComplexFunc.toMagAng(vabc.a_0));
				str.append(Number2String.toStr("#######0.#", vpu0*vphase) + "   ");
				double vpu2 = vabc.c_2.abs();
				str.append(ComplexFunc.toMagAng(vabc.c_2));
				str.append(Number2String.toStr("#######0.#", vpu2*vphase)	+ "\n");
			}
		}
		return str.toString();
	}

	private static String displayBranchCurrent(AcscBusFault bf) {
		AcscNetwork net = bf.getFaultResult().getAcscNet();
		try {
			bf.getFaultResult().calBranchCurrent();
		} catch (Exception e) {
			IpssLogger.logErr(e);
			return e.toString();
		}

		StringBuffer str = new StringBuffer("");
		if (bf.getFaultCode() == SimpleFaultCode.GROUND_3P) {
			str.append("\n\n");
			str.append("        BranchID                FaultAmps\n");
			str.append("                              (pu)       (Amp) \n");
			str.append("     --------------------   --------   ----------\n");

			List<AclfBranch> branchList = net.getBranchList();
			int cnt = 0;
			for (int n = 0; n < branchList.size(); n++) {
				AcscBranch bra = (AcscBranch) branchList.get(n);
				str.append("     " + Number2String.toStr(-20, bra.getId())	+ "   ");
				try {
					Complex3x1 cpu = bf.getFaultResult().getBranchAmpsFrom2To_012(++cnt);
					Complex3x1 camp = bf.getFaultResult().getBranchAmpsFrom2To_012(cnt, UnitType.Amp,
									bra.getFromBus().getBaseVoltage(),
									net.getBaseKva());
					str.append(Number2String.toStr("###0.###", cpu.b_1.abs()) + "   "
							+ Number2String.toStr("#######0.#", camp.b_1.abs())	+ "\n");
				} catch (Exception e) {
					IpssLogger.logErr(e);
					str.append(e.toString() + "\n");
				}
			}
		} else {
			str.append("\n\n");
			str.append("        BranchID                FaultAmps(1)             FaultAmps(0)            FaultAmps(2)\n");
			str.append("                              (pu)       (Amp)        (pu)       (Amp)        (pu)       (Amp) \n");
			str.append("     --------------------   --------   ----------   --------   ----------   --------   ----------\n");

			List<AclfBranch> branchList = net.getBranchList();
			int cnt = 0;
			for (int n = 0; n < branchList.size(); n++) {
				AcscBranch bra = (AcscBranch) branchList.get(n);
				try {
					Complex3x1 cpu = bf.getFaultResult().getBranchAmpsFrom2To_012(++cnt);
					Complex3x1 camp = bf.getFaultResult().getBranchAmpsFrom2To_012(cnt, UnitType.Amp,
									bra.getFromBus().getBaseVoltage(),
									net.getBaseKva());
					str.append("     " + Number2String.toStr(-20, bra.getId()) + "   ");
					str.append(Number2String.toStr("###0.###", cpu.b_1.abs()) + "   "
							+ Number2String.toStr("#######0.#", camp.b_1.abs()) + "   ");
					str.append(Number2String.toStr("###0.###", cpu.a_0.abs()) + "   "
							+ Number2String.toStr("#######0.#", camp.a_0.abs()) + "   ");
					str.append(Number2String.toStr("###0.###", cpu.c_2.abs()) + "   "
							+ Number2String.toStr("#######0.#", camp.c_2.abs()) + "\n");

					cpu = bf.getFaultResult().getBranchAmpsTo2From_012(cnt);
					camp = bf.getFaultResult().getBranchAmpsTo2From_012(cnt,
							UnitType.Amp, bra.getToBus().getBaseVoltage(),
							net.getBaseKva());
					str.append("     " + Number2String.toStr(-9, " ") + "<-"
							+ Number2String.toStr(-9, " ") + "   ");
					str.append(Number2String.toStr("###0.###", cpu.b_1.abs()) + "   "
							+ Number2String.toStr("#######0.#", camp.b_1.abs()) + "   ");
					str.append(Number2String.toStr("###0.###", cpu.a_0.abs()) + "   "
							+ Number2String.toStr("#######0.#", camp.a_0.abs()) + "   ");
					str.append(Number2String.toStr("###0.###", cpu.c_2.abs()) + "   "
							+ Number2String.toStr("#######0.#", camp.c_2.abs()) + "\n");
				} catch (Exception e) {
					IpssLogger.logErr(e);
					str.append(e.toString() + "\n");
				}
			}

			str.append("\n\n");
			str.append("        BranchID                FaultAmps(a)             FaultAmps(b)            FaultAmps(c)\n");
			str.append("                              (pu)       (Amp)        (pu)       (Amp)        (pu)       (Amp) \n");
			str.append("     --------------------   --------   ----------   --------   ----------   --------   ----------\n");

			cnt = 0;
			for (int n = 0; n < branchList.size(); n++) {
				AcscBranch bra = (AcscBranch) branchList.get(n);
				str.append("     " + Number2String.toStr(-20, bra.getId()) + "   ");
				try {
					Complex3x1 cpu = bf.getFaultResult().getBranchAmpsFrom2To_abc(++cnt);
					double baseV = bra.getFromBus().getBaseVoltage() / NumericConstant.SqrtRoot3;
					Complex3x1 camp = bf.getFaultResult().getBranchAmpsFrom2To_abc(cnt, UnitType.Amp,
									baseV,	net.getBaseKva());
					str.append(Number2String.toStr("###0.###", cpu.a_0.abs()) + "   "
							+ Number2String.toStr("#######0.#", camp.a_0.abs())	+ "   ");
					str.append(Number2String.toStr("###0.###", cpu.b_1.abs()) + "   "
							+ Number2String.toStr("#######0.#", camp.b_1.abs())	+ "   ");
					str.append(Number2String.toStr("###0.###", cpu.c_2.abs()) + "   "
							+ Number2String.toStr("#######0.#", camp.c_2.abs()) + "\n");

					cpu = bf.getFaultResult().getBranchAmpsTo2From_abc(cnt);
					baseV = bra.getToBus().getBaseVoltage() / NumericConstant.SqrtRoot3;
					camp = bf.getFaultResult().getBranchAmpsTo2From_abc(cnt,
							UnitType.Amp, baseV, net.getBaseKva());
					str.append("     " + Number2String.toStr(-9, " ") + "<-"
							+ Number2String.toStr(-9, " ") + "   ");
					str.append(Number2String.toStr("###0.###", cpu.a_0.abs()) + "   "
							+ Number2String.toStr("#######0.#", camp.a_0.abs()) + "   ");
					str.append(Number2String.toStr("###0.###", cpu.b_1.abs()) + "   "
							+ Number2String.toStr("#######0.#", camp.b_1.abs()) + "   ");
					str.append(Number2String.toStr("###0.###", cpu.c_2.abs()) + "   "
							+ Number2String.toStr("#######0.#", camp.c_2.abs()) + "\n");
				} catch (Exception e) {
					IpssLogger.logErr(e);
					str.append(e.toString() + "\n");
				}
			}
		}
		return str.toString();
	}
}
