package org.interpss.display;
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


import static com.interpss.common.util.IpssLogger.ipssLogger;
import static com.interpss.core.funcImpl.AclfFunction.BranchRatingAptr;
import static com.interpss.dc.DcPluginFunction.OutputSolarNet;
import static org.interpss.CorePluginFunction.OutputBusId;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.Number2String;

import com.interpss.common.datatype.Constants;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.AdjControlType;
import com.interpss.core.aclf.adj.FunctionLoad;
import com.interpss.core.aclf.adj.PQBusLimit;
import com.interpss.core.aclf.adj.PSXfrPControl;
import com.interpss.core.aclf.adj.PVBusLimit;
import com.interpss.core.aclf.adj.RemoteQBus;
import com.interpss.core.aclf.adj.RemoteQControlType;
import com.interpss.core.aclf.adj.TapControl;
import com.interpss.core.aclf.adj.XfrTapControlType;
import com.interpss.core.aclf.adpter.AclfGenBus;
import com.interpss.core.aclf.adpter.AclfPSXformer;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.path.NetPathWalkDirectionEnum;
import com.interpss.core.algo.sec.AclfBranchRating;
import com.interpss.core.algo.sec.SecAnalysisViolationType;
import com.interpss.core.datatype.Mismatch;
import com.interpss.core.funcImpl.CoreUtilFunc;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.core.net.Network;
import com.interpss.core.net.OriginalDataFormat;
import com.interpss.dc.DcNetwork;
import com.interpss.dist.DistNetwork;

/**
 * Aclf system output functions
 * 
 * @author mzhou
 *
 */
public class AclfOutFunc {
	/**
	 *  Loadflow output format 
	 */
	public static enum LfResultFormat { 
							Summary, 
							BusStyle };

	/**
	 * Bus style output bus id option: Bus number or bus name 						
	 *
	 */
	public static enum BusIdStyle { 
							BusId_No, 
							BusId_Name };
	
	/**
	 * output LF results in the summary foramt
	 * 
	 * @param net
	 * @return
	 */
	public static StringBuffer loadFlowSummary(AclfNetwork net) {
		return loadFlowSummary(net, true);
	}
	
	/**
	 * output LF resutls in the summary format
	 * 
	 * @param net
	 * @param includeAdj
	 * @return
	 */
	public static StringBuffer loadFlowSummary(AclfNetwork net, boolean includeAdj) {
		StringBuffer str = new StringBuffer(_loadFlowSummary((AclfNetwork) net));

		try {
			if (includeAdj) {
				if (net.hasPVBusLimit())
					str.append(pvBusLimitToString(net));

				if (net.hasPQBusLimit())
					str.append(pqBusLimitToString(net));

				if (net.hasRemoteQBus())
					str.append(remoteQBusToString(net));

				if (net.hasFunctionLoad())
					str.append(aclfFuncLoadToString(net));

				if (net.hasTapControl())
					str.append(tapVControlToString(net));

				if (net.hasPSXfrPControl())
					str.append(psXfrPControlToString(net));
			}
		} catch (Exception emsg) {
			str.append(emsg.toString());
		}
		
		if (net.isContainChildNet()) {
		  	for (Network n : net.getChildNetworks()) {
	  			AclfNetwork aclfNet = null;
		  		if (n instanceof AclfNetwork) {
		  			aclfNet = (AclfNetwork)n;
		  		}
		  		if (n instanceof DistNetwork) {
		  			aclfNet = ((DistNetwork)n).getAclfNet();
		  		}
		  		str.append("\n\nChildNet : " + n.getId() + "\n");
		  		str.append("Parent net interface bus Id: " + n.getParentNetInterfaceBusId() + "\n");
		  		str.append(_loadFlowSummary(aclfNet));
		  	}
		  	
		  	for (Network n2nd : net.getChildNetworks()) {
		  		if (n2nd.isContainChildNet()) {
				  	for (Object n3rd : n2nd.getChildNetworks()) {
				  		if (n3rd instanceof DcNetwork) {
				  			try {
				  				DcNetwork dcnet = (DcNetwork)n3rd;
						  		str.append("\n\nChildNet : " + dcnet.getId() + "\n");
						  		str.append("Parent net interface bus Id: " + dcnet.getParentNetInterfaceBusId() + "\n");
						  		str.append(OutputSolarNet.fx(dcnet));
				  			} catch (InterpssException e) {
				  				ipssLogger.severe(e.toString());
				  				str.append(e.toString());
				  			}
				  		}
				  	}		  			
		  		}
		  	}		  	
		}
		return str;
	}

	private static StringBuffer _loadFlowSummary(AclfNetwork net) {
		final StringBuffer str = new StringBuffer("");
		try {
			str.append("\n                          Load Flow Summary\n");
			str.append(AclfOutFunc.maxMismatchToString(net, "") + "\n");
			if (net.getOriginalDataFormat() == OriginalDataFormat.CIM) {
				str.append("     BusID          Code             Volt(pu)   Angle(deg)     P(pu)     Q(pu)                RdfId\n");
				str.append("  ------------------------------------------------------------------------------------------------------------------\n");
			}
			else {
				str.append("     BusID          Code           Volt(pu)   Angle(deg)     P(pu)     Q(pu)      Bus Name   \n");
				str.append("  -------------------------------------------------------------------------------------------\n");
			}
				 
			for (Bus b : net.getBusList()) {
				AclfBus bus = (AclfBus)b;
				if (bus.isActive()) {
					if (bus.isParent()) {
						// parent bus could be the original bus or a newly created
						// holding bus. The created bus id starts with the token
						if (!bus.getId().startsWith(Constants.Token_ParentBusPrefix))
							str.append(busLfSummary(bus));
						for (Bus sec : bus.getBusSecList()) {
							AclfBus busSec = (AclfBus)sec;
							str.append(busLfSummary(busSec));
						}
					}
					else
						str.append(busLfSummary(bus));
				}
			}
		} catch (Exception emsg) {
			str.append(emsg.toString());
		}

		str.append(branchMvaRatingViolationList(net));

		return str;
	}

	private static String busLfSummary(AclfBus bus) {
		final StringBuffer str = new StringBuffer("");
		Complex busPQ = bus.getPQResults();
		if (bus.isActive())
			str.append("  ");
		else
			str.append("- ");
		str.append(String.format("%-12s ", OutputBusId.f(bus, bus.getNetwork().getOriginalDataFormat())));
		str.append(String.format("%-17s ", bus.code2String()));
		str.append(String.format("%10.5f   ", bus.getVoltageMag(UnitType.PU)));
		str.append(String.format("%9.1f   ", bus.getVoltageAng(UnitType.Deg)));
		str.append(String.format("%10.4f", busPQ.getReal()));
		str.append(String.format("%10.4f", busPQ.getImaginary()));
		str.append(String.format("   %-10s ", bus.getName()));
		if (bus.getNetwork().getOriginalDataFormat() == OriginalDataFormat.CIM) 
			str.append(String.format("   %s", bus.getId()));
		str.append("\n");
		return str.toString();
	}
	
	/**
	 * output load Loss allocation result
	 * 
	 * @param net
	 * @return
	 */
	public static StringBuffer loadLossAllocation(AclfNetwork net) {
		final StringBuffer str = new StringBuffer("");
		final double lossMW = net.totalLoss(UnitType.mVA).getReal();
		final double lossPU = net.totalLoss(UnitType.PU).getReal();

		str.append("\n                          Load Loss Allocation\n");
		str.append("\n                       Total Loss = " + Number2String.toStr("####0.00", lossMW) + " MW\n\n");
		str.append("            BusID          LossAllocFactor         Allocated Loss(MW)\n");
		str.append("  ------------------------------------------------------------------------\n");
		
		
		for (AclfBus bus : net.getBusList()) {
			if ( bus.getLossPFactor(NetPathWalkDirectionEnum.ALONG_PATH, lossPU) > 0.0 && 
						(bus.isLoad() || bus.isSwing())) { 
				str.append(lossString(bus, NetPathWalkDirectionEnum.ALONG_PATH, lossMW, lossPU));
			}
		}
		return str;
	}
	
	/**
	 * output gen loss allocation result
	 * 
	 * @param net
	 * @return
	 */
	public static StringBuffer genLossAllocation(AclfNetwork net) {
		StringBuffer str = new StringBuffer("");
		double lossMW = net.totalLoss(UnitType.mVA).getReal();
		double lossPU = net.totalLoss(UnitType.PU).getReal();

		str.append("\n                          Gen Loss Allocation\n");
		str.append("\n                       Total Loss = " + Number2String.toStr("####0.00", lossMW) + " MW\n\n");
		str.append("            BusID          LossAllocFactor         Allocated Loss(MW)\n");
		str.append("  ------------------------------------------------------------------------\n");
		for (Bus bus : net.getBusList()) {
			AclfBus aclfBus = (AclfBus)bus;
			if (aclfBus.isGen() && aclfBus.getLossPFactor(NetPathWalkDirectionEnum.OPPOSITE_PATH, lossPU) > 0.0) { 
				str.append(lossString(aclfBus, NetPathWalkDirectionEnum.OPPOSITE_PATH, lossMW, lossPU));
			}
  		}		
		
		return str;
	}
	
	private static StringBuffer lossString(AclfBus aclfBus, NetPathWalkDirectionEnum direction, double lossMW, double lossPU) {
		StringBuffer str = new StringBuffer("");
		str.append(Number2String.toStr(12, " "));
		str.append(Number2String.toStr(-12, aclfBus.getId()) + "  ");
		str.append(Number2String.toStr(2, " "));
		str.append(Number2String.toStr("####0.000", aclfBus.getLossPFactor(direction, lossPU)));
		str.append(Number2String.toStr(17, " "));
		str.append(Number2String.toStr("####0.00", aclfBus.getLossPFactor(direction, lossPU)*lossMW));
		str.append("\n");
		return str;
	}

	/**
	 * output net max mismatch result
	 * 
	 * @param net
	 * @param prefix
	 * @return
	 */
	public static String maxMismatchToString(AclfNetwork net, String prefix) {
		try {
			double baseKVA = net.getBaseKva();
			String str = "\n"+prefix+"                         Max Power Mismatches\n"
					+ prefix+"             Bus              dPmax       Bus              dQmax\n"
					+ prefix+"            -------------------------------------------------------\n";
			Mismatch mis = net.maxMismatch(AclfMethod.NR);
			str += prefix+Number2String.toStr(12, " ");
			str += String.format("%-12s ", OutputBusId.f(mis.maxPBus, net.getOriginalDataFormat()));
			str += String.format("%12.6f  ", mis.maxMis.getReal());
			str += String.format("%-12s ", OutputBusId.f(mis.maxQBus, net.getOriginalDataFormat()));
			str += String.format("%12.6f (pu)\n", mis.maxMis.getImaginary());
			str += prefix+String.format("%24s", " ");
			str += String.format("%12.6f0 ", baseKVA* mis.maxMis.getReal());
			str += String.format("%14s", " ");
			str += String.format("%12.6f (kva)\n", baseKVA * mis.maxMis.getImaginary());
			return str;
		} catch (Exception emsg) {
			return emsg.toString();
		}
	}

	/**
	 * output voltage violation results
	 * 
	 * @param net
	 * @param max
	 * @param min
	 * @return
	 */
	public static StringBuffer voltageViolationReport(AclfNetwork net, double max, double min) {
		return voltageViolationReport(net, max, min, false);
	}
	public static StringBuffer voltageViolationReport(AclfNetwork net, double max, double min, boolean disLfAssistGen) {
		StringBuffer buf = new StringBuffer("");
		buf.append("\n");
		buf.append("                   Bus Voltage Violation Report   \n\n");
		buf.append(String.format("              Bus Voltage Limit: [%4.2f, %4.2f]\n", max, min));
		buf.append("\n");

		buf.append("         Bus Id      Area   Zone    Voltage     BaseCase   Dispatched      \n");
		buf.append("       ====================================================================\n");
		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus)b;
			if (bus.isActive() &&
					(bus.getVoltageMag() > max || bus.getVoltageMag() < min))
				buf.append(String.format("     %12s  %6d  %6d  %8.4f    %8s    %8s\n", 
						bus.getId(), bus.getArea().getNumber(), bus.getZone().getNumber(),
						bus.getVoltageMag(), bus.getGenCodeBaseCase(), bus.getGenCode()));
  		}		
		
		buf.append("\n\nLF Assistance Gen List\nAreaNo,  ZoneNo,  GenNo\n");

		if (disLfAssistGen) {
			for (Bus b : net.getBusList()) {
				AclfBus bus = (AclfBus)b;
				if (bus.isActive() &&
						(bus.getVoltageMag() > max || bus.getVoltageMag() < min) &&
						(bus.getGenCodeBaseCase() ==  AclfGenCode.GEN_PV && !bus.isGen())) {
					buf.append(String.format("%d,  %d,  %s, ", 
							bus.getArea().getNumber(), bus.getZone().getNumber(), bus.getId().substring(3)));
					buf.append(" voltage, QLimit: " + 
							Number2String.toDebugStr(bus.getVoltageMag()) + " " +  
							(bus.getQGenLimit()!=null? bus.getQGenLimit():"not defined") + "\n");
				}
	  		}		
		}
		
		buf.append("\n");
		return buf;
	}

	/**
	 * output branch rating violation results
	 * 
	 * @param net
	 * @return
	 */
	public static StringBuffer branchMvaRatingViolationList(AclfNetwork net) {
		StringBuffer str = new StringBuffer("");
		if (net.hasBranchMavRatingViolation()) {
			str.append("\n\n");
			str
					.append("                        Branch MvaRating Violation List\n\n");
			str
					.append("         BranchID            MvaFlow       PF     Side    MvaRating1   MvaRating2   MvaRating3\n");
			str
					.append(" ---------------------     ------------ ------ -------- ------------ ------------ ------------\n");
			for (Branch b : net.getBranchList()) {
				if (b instanceof AclfBranch) {
					// branch Mva rating violation only applies to 2W xfr or
					// line branch
					AclfBranch bra = (AclfBranch) b;
					processBranchMvaRatingViolation(net, str, bra);
				}
			}
		}
		return str;
	}

	private static void processBranchMvaRatingViolation(AclfNetwork net,
			StringBuffer str, AclfBranch bra) {
		if (bra.isActive()) {
			AclfBranchRating adapter = BranchRatingAptr.f(bra);
			if (adapter.isRatingViolated(SecAnalysisViolationType.BRANCH_THERMAL_MVA_RATING, net.getBaseKva())) {
				str.append(Number2String.toStr(-25, bra.getId()));
				Complex mva = bra.powerFrom2To(UnitType.mVA);
				String side = "From";
				if (bra.powerFrom2To(UnitType.mVA).abs() < bra
						.powerTo2From(UnitType.mVA).abs()) {
					mva = bra.powerTo2From(UnitType.mVA);
					side = "To";
				}

				str.append("     " + Number2String.toStr("####0.0", mva.abs()));
				str.append("   " + Number2String.toStr("##0.0", 100.0*CoreUtilFunc.calPFactor(mva.getReal(), mva.getImaginary())) + "%");
				str.append("    " + Number2String.toStr(-4, side));
				str.append("      " + Number2String.toStr("####0.0", bra.getRatingMva1()));
				str.append("      " + Number2String.toStr("####0.0", bra.getRatingMva2()));
				str.append("      " + Number2String.toStr("####0.0", bra.getRatingMva3()));
				str.append("\n");
			}
		}
	}

	/**
	 * output PV bus limit adjustment result
	 * 
	 * @param net
	 * @return
	 * @throws Exception
	 */
	public static StringBuffer pvBusLimitToString(AclfNetwork net) throws Exception {
		final StringBuffer str = new StringBuffer("");

		str.append("\n\n");
		str.append("                  PV Bus Limit Adjustment/Control\n\n");
		str
				.append("      BusID     Vact     Vspec      Q      Qmax     Qmin   Status\n");
		str
				.append("     -------- -------- -------- -------- -------- -------- ------\n");

		for( AclfBus bus : net.getBusList()) {
			if (bus.isPVBusLimit()) {
				PVBusLimit pv = bus.getPVBusLimit();
				AclfGenBus genBus = pv.getParentBus().toGenBus();
				str.append(Number2String.toStr(5, " "));
				str.append(Number2String.toStr(-8, OutputBusId.f(pv.getParentBus(), 
						pv.getParentBus().getNetwork().getOriginalDataFormat())));
				str.append(Number2String.toStr("###0.0000", pv.getParentBus()
						.getVoltageMag(UnitType.PU)));
				str.append(Number2String.toStr("###0.0000", pv
						.getVSpecified(UnitType.PU)));
				str.append(Number2String.toStr("#####0.00", genBus.getGenResults(
						UnitType.PU).getImaginary()));
				str.append(Number2String.toStr("#####0.00", pv.getQLimit(
						UnitType.PU).getMax()));
				str.append(Number2String.toStr("#####0.00", pv.getQLimit(
						UnitType.PU).getMin()));
				str.append(Number2String.toStr(6, pv.isActive() ? "on" : "off")
						+ "\n");
			}
		}
		
		return str;
	}

	/**
	 * output PQ bus limit adjustment result
	 * 
	 * @param net
	 * @return
	 * @throws Exception
	 */
	public static StringBuffer pqBusLimitToString(AclfNetwork net) 	throws Exception {
		StringBuffer str = new StringBuffer("");

		str.append("\n\n");
		str.append("                  PQ Bus Limit Adjustment/Control\n\n");
		str
				.append("      BusID     Qact     Qspec      V      Vmax     Vmin   Status\n");
		str
				.append("     -------- -------- -------- -------- -------- -------- ------\n");

		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus)b;
			if (bus.isPQBusLimit()) {
				PQBusLimit pq = bus.getPQBusLimit();
				AclfGenBus genBus = pq.getParentBus().toGenBus();
				str.append(Number2String.toStr(5, " "));
				str.append(Number2String.toStr(-8, OutputBusId.f(pq.getParentBus(), net.getOriginalDataFormat())) + " ");
				str.append(Number2String.toStr("####0.00", genBus.getGenResults(
						UnitType.PU).getImaginary())
						+ " ");
				str.append(Number2String.toStr("####0.00", pq.getQSpecified(
						UnitType.PU))
						+ " ");
				str.append(Number2String.toStr("##0.0000", pq.getParentBus()
						.getVoltageMag(UnitType.PU))
						+ " ");
				str.append(Number2String.toStr("##0.0000", pq
						.getVLimit(UnitType.PU).getMax())
						+ " ");
				str.append(Number2String.toStr("##0.0000", pq
						.getVLimit(UnitType.PU).getMin())
						+ " ");
				str.append(Number2String.toStr(5, pq.isActive() ? "on" : "off")
						+ "\n");
			}
		}
		return str;
	}

	/**
	 * output RemoteQBus adjustment result
	 * 
	 * @param net
	 * @return
	 * @throws Exception
	 */
	public static StringBuffer remoteQBusToString(AclfNetwork net) throws Exception {
		StringBuffer str = new StringBuffer("");

		str.append("\n\n");
		str.append("                Remote Q Voltage Adjustment/Control\n\n");
		str
				.append("       VcBus    Type    ReQBus/Branch   Actual    Spec       Q      Qmax     Qmin   Status\n");
		str
				.append("     -------- -------- --------------- -------- -------- -------- -------- -------- ------\n");

		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus)b;
			if (bus.isRemoteQBus()) {
				RemoteQBus re = bus.getRemoteQBus();
				AclfGenBus genBus = re.getParentBus().toGenBus();
				str.append(Number2String.toStr(5, " "));
				str.append(Number2String.toStr(-9, OutputBusId.f(re.getParentBus(), net.getOriginalDataFormat())));
				str.append(Number2String.toStr(-9,
										(re.getControlType() == RemoteQControlType.BUS_VOLTAGE ? " Voltage"
												: "MvarFlow")));
				str.append(Number2String.toStr(15,
						re.getControlType() == RemoteQControlType.BUS_VOLTAGE ? re
								.getRemoteBus().getId() : re.getRemoteBranch()
								.getId()));
				str.append(Number2String.toStr("###0.0000",
						re.getControlType() == RemoteQControlType.BUS_VOLTAGE ? re
								.getRemoteBus().getVoltageMag(UnitType.PU) : re
								.getMvarFlowCalculated(re.getRemoteBranch(), UnitType.PU)));
				str.append(Number2String.toStr("###0.0000", re.getVSpecified(UnitType.PU)));
				str.append(Number2String.toStr("#####0.00", genBus.getGenResults(
						UnitType.PU).getImaginary()));
				str.append(Number2String.toStr("#####0.00", re.getQLimit(
						UnitType.PU).getMax()));
				str.append(Number2String.toStr("#####0.00", re.getQLimit(
						UnitType.PU).getMin()));
				str.append(Number2String.toStr(6, re.isActive() ? "on" : "off")	+ "\n");
			}
		}
		return str;
	}

	/**
	 * output functional load adjustment result
	 * 
	 * @param net
	 * @return
	 * @throws Exception
	 */
	public static StringBuffer aclfFuncLoadToString(AclfNetwork net) throws Exception {
		StringBuffer str = new StringBuffer("");

		double baseKVA = net.getBaseKva();

		str.append("\n\n");
		str.append("                  Aclf FuncLoad Adjustment/Control\n\n");
		str
				.append("      BusID     Pact     Qact       V      P(0)     Q(0)   Status\n");
		str
				.append("     -------- -------- -------- -------- -------- -------- ------\n");

		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus)b;
			if (bus.getFunctionLoad() != null) {
				FunctionLoad x = bus.getFunctionLoad();
				str.append(Number2String.toStr(5, " "));
				str.append(Number2String.toStr(-8, x.getParentBus().getId()) + " ");
				double vpu = x.getParentBus().getVoltage().abs();
				str.append(Number2String.toStr("##0.0000", x.getP().getLoad(vpu,
						UnitType.PU, baseKVA))
						+ " ");
				str.append(Number2String.toStr("##0.0000", x.getQ().getLoad(vpu,
						UnitType.PU, baseKVA))
						+ " ");
				str.append(Number2String.toStr("##0.0000", x.getParentBus()
						.getVoltageMag(UnitType.PU))
						+ " ");
				str.append(Number2String.toStr("##0.0000", x.getP().getLoad0(
						UnitType.PU, baseKVA))
						+ " ");
				str.append(Number2String.toStr("##0.0000", x.getQ().getLoad0(
						UnitType.PU, baseKVA))
						+ " ");
				str.append(Number2String.toStr(5, x.isActive() ? "on" : "off")
						+ "\n");
			}
		}
		return str;
	}

	/**
	 * output Tap voltage adjustment result
	 * 
	 * @param net
	 * @return
	 * @throws Exception
	 */
	public static StringBuffer tapVControlToString(AclfNetwork net) throws Exception {
		StringBuffer str = new StringBuffer("");

		double baseKva = net.getBaseKva();

		str.append("\n\n");
		str
				.append("                          Tap Voltage Adjustment/Control\n\n");
		str
				.append("          BranchID     VC BusID  Actual   Spec/Range     Tap  Tmax  Tmin  StepSize Status\n");
		str
				.append("     ----------------- -------- -------- -------------- ----- ----- -----   -----  ------\n");

		for (Branch b : net.getBranchList()) {
			AclfBranch branch = (AclfBranch)b;
			if (branch.isTapControl()) {
				TapControl x = branch.getTapControl();
				str.append(Number2String.toStr(5, " "));
				str.append(Number2String.toStr(-17, x.getParentBranch().getId())
						+ " ");

				if (x.getControlType() == XfrTapControlType.BUS_VOLTAGE) {
					str.append(Number2String.toStr(-8, x.getVcBus().getId()) + " ");
					str.append(Number2String.toStr("##0.0000", x.getVcBus()
							.getVoltageMag(UnitType.PU))
							+ " ");
					if (x.getFlowControlType() == AdjControlType.POINT_CONTROL)
						str.append(Number2String.toStr("##0.0000", x
								.getVSpecified(UnitType.PU))
								+ " ");
					else
						str.append(x.getControlRange() + " ");
				} else {
					str.append(Number2String.toStr(-8, " "));
					str.append(Number2String.toStr("##0.0000", x
							.getMvarFlowCalculated(UnitType.PU, baseKva))
							+ " ");
					if (x.getFlowControlType() == AdjControlType.POINT_CONTROL)
						str.append("   "
								+ Number2String.toStr("##0.0000", x
										.getMvarSpecified(UnitType.PU, baseKva))
								+ "    ");
					else
						str.append(x.getControlRange() + " ");
				}

				str.append(Number2String.toStr("0.000",
						(x.isControlOnFromSide() ? x.getParentBranch()
								.getFromTurnRatio() : x.getParentBranch()
								.getToTurnRatio()))
						+ " ");
				str.append(Number2String.toStr("0.000", x.getTurnRatioLimit().getMax())
						+ " ");
				str.append(Number2String.toStr("0.000", x.getTurnRatioLimit().getMin())
						+ "   ");
				str.append(Number2String.toStr("####0", x.getTapStepSize()) + "  ");
				str.append(Number2String.toStr(6, x.isActive() ? "on" : "off")
						+ "\n");
			}
		}
		return str;
	}

	/**
	 * output PS xfr power adjustment result
	 * 
	 * @param net
	 * @return
	 * @throws Exception
	 */
	public static StringBuffer psXfrPControlToString(AclfNetwork net) throws Exception {
		StringBuffer str = new StringBuffer("");

		double baseKVA = net.getBaseKva();

		str.append("\n\n");
		str.append("                  PSXfr Power Adjustment/Control\n\n");
		str
				.append("          BranchID       Pact     Spec/Range    Ang   Max   Min  Status\n");
		str
				.append("     ----------------- -------- -------------- ----- ----- ----- ------\n");

		for (Branch b : net.getBranchList()) {
			AclfBranch branch = (AclfBranch)b;
			if (branch.isTapControl()) {
				PSXfrPControl x = branch.getPSXfrPControl();
				str.append(Number2String.toStr(5, " "));
				str.append(Number2String.toStr(-17, x.getParentBranch().getId())
						+ " ");
				str.append(Number2String.toStr("##0.0000",
						(x.isControlOnFromSide() ? x.getParentBranch().powerFrom2To(
								UnitType.PU).getReal() : x.getParentBranch()
								.powerTo2From(UnitType.PU).getReal()))
						+ " ");

				if (x.getFlowControlType() == AdjControlType.POINT_CONTROL)
					str.append(Number2String.toStr("   " + "##0.0000", x
							.getPSpecified(UnitType.PU, baseKVA))
							+ "    ");
				else
					str.append(x.getControlRange() + " ");

				AclfPSXformer psXfr = x.getParentBranch().toPSXfr();
				str.append(Number2String.toStr("#0.00", psXfr
						.getFromAngle(UnitType.Deg))
						+ " ");
				str.append(Number2String.toStr("#0.00", x.getAngLimit(UnitType.Deg)
						.getMax())
						+ " ");
				str.append(Number2String.toStr("#0.00", x.getAngLimit(UnitType.Deg)
						.getMin())
						+ " ");
				str.append(Number2String.toStr(6, x.isActive() ? "on" : "off")
						+ "\n");
			}
		}
		return str;
	}
}