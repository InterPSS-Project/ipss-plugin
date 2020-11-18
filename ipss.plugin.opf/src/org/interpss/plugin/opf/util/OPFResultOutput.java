package org.interpss.plugin.opf.util;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.opf.dep.BaseOpfBranch;
import com.interpss.opf.dep.BaseOpfBus;
import com.interpss.opf.dep.OpfGenBus;
import com.interpss.opf.dep.OpfNetwork;

public class OPFResultOutput {
	public static String opfResultSummary(OpfNetwork opfnet) {
		OutputHelper helper = new OutputHelper(opfnet);

		double baseKva = opfnet.getBaseKva() / 1000;
		final StringBuffer str = new StringBuffer("\n\n");
		// System characteristic summary

		str.append("========================================================================\n");
		str.append("                      DCOPF SOLUTION SUMMARY                            \n");
		str.append("========================================================================\n");
		str.append("\n");
		str.append(String.format("Objective Function Value: %6.3f",
				opfnet.getMinF())
				+ " ($/h)\n");
		str.append("\n");
		str.append("                        Minimun                   Maximum  \n");
		str.append("Voltage Angle (RAD) ");
		str.append(String.format("%8.3f", helper.getMinBusAngle()));
		str.append(String.format("%3s", "@ "));
		str.append(String.format("%3s", helper.getMinBusAngleBus()));
		str.append(String.format("%18.3f", helper.getMaxBusAngle()));
		str.append(String.format("%3s", "@ "));
		str.append(String.format("%3s", helper.getMaxBusAngleBus()));
		str.append("\n");

		str.append("LMP ($/MWh)         ");
		str.append(String.format("%8.3f", helper.getMinLMP()));
		str.append(String.format("%3s", "@ "));
		str.append(String.format("%3s", helper.getMinLMPBus()));
		str.append(String.format("%18.3f", helper.getMaxLMP()));
		str.append(String.format("%3s", "@ "));
		str.append(String.format("%3s", helper.getMaxLMPBus()));
		str.append("\n");
		str.append("                                                              \n");

		str.append("=====================================================================\n");
		str.append("                         DC NETWORK SUMMARY                         \n");
		str.append("=====================================================================\n");
		str.append(String.format("%8s", "Network summary\n\n"));
		str.append("buses  Generators  Loads  Areas  Branches  PhaseShifter        \n");
		str.append(String.format("%3d", opfnet.getNoBus()));
		str.append(String.format("%9d", helper.getNumOfGenerator()));
		str.append(String.format("%11d", helper.getNumOfLoad()));
		str.append(String.format("%7d", helper.getNumOfArea()));
		str.append(String.format("%7d", opfnet.getNoBranch()));
		str.append(String.format("%10d", helper.getNumOfPhaseShifter()));
		str.append("\n\n");
		// str.append("--------------------------------------------------------------------\n\n");		
		str.append("Gen. Capacity    On-line Gen.    Actual Gen.  Load    Losses   (in MW)\n");
		str.append(String.format("%10.2f", helper.getTotalGenCapacity()
				* baseKva));
		str.append(String.format("%14.2f", helper.getTotalOnlineGen() * baseKva));
		str.append(String.format("%17.2f", helper.getTotalActualGen() * baseKva));
		str.append(String.format("%10.2f", helper.getTotalLoad() * baseKva));
		str.append(String.format("%8.2f", helper.getTotalLoss() * baseKva));
		str.append("\n\n");
		str.append("========================================================================\n");
		str.append("                          BUS SUMMARY                           \n");
		str.append("========================================================================\n");
		str.append("busID  OPFGen  Angle(RAD)  Generation(MW)    Load(MW)   LMP($/MWh)      \n");
		str.append("      -----------------------------------------------------------------\n");

		for (Bus b : opfnet.getBusList()) {
			str.append(String.format("%4s", b.getId()));
			BaseOpfBus bus = (BaseOpfBus) b;
			if (opfnet.isOpfGenBus(b)) {
				OpfGenBus opfBus = (OpfGenBus) b;
				str.append(String.format("%8s", "True"));
			} else {
				str.append(String.format("%8s", "False"));
			}
			str.append(String.format("%11.3f", bus.getVoltageAng()));
			str.append(String.format("%14.3f", bus.getGenP() * baseKva));
			str.append(String.format("%14.3f", bus.getLoadP()*baseKva));

			// angle is converted to radian
			double lmp = bus.getLMP();
			if (lmp == 0){
				str.append(String.format("%12s", "N/A"));
			}else
				str.append(String.format("%14.2f", bus.getLMP()));
			
			str.append("\n");
		}
		str.append("\n\n");

		str.append("==========================================================================\n");
		str.append("                          BRANCH FLOW SUMMARY                             \n");
		str.append("==========================================================================\n");
		str.append("Branch#  From bus  To bus  cirId  Limit(MW)   Flow(from bus -> to bus)(MW)\n");
		/*ArrayList<AclfBranch> constrainedBranchList = helper.getConstrainedBranchList();
		int size = constrainedBranchList.size();*/
		int braNum =0 ;
		for (Branch branch: opfnet.getBranchList()){
			AclfBranch bra = (AclfBranch) branch;
			/*int braNum =  bra.getSortNumber()+1;*/
			braNum =  braNum+ 1;
			String fbus = bra.getFromPhysicalBusId();
			String tbus = bra.getToPhysicalBusId();
			String cirId = bra.getCircuitNumber();
			double flow = ((BaseOpfBranch) bra).DcPowerFrom2To()*baseKva;
			double rating = ((BaseOpfBranch) bra).getRatingMw1()*baseKva;	
			double dflow = Math.abs(Math.abs(flow)-rating);
			str.append(String.format("%4d", braNum));
			if (dflow < 0.001){				
				str.append(String.format("%1s", "*"));
			}else{
				str.append(String.format("%1s", "")); // for format purpose only
			}			
			str.append(String.format("%10s", fbus));
			str.append(String.format("%9s", tbus));
			str.append(String.format("%7s", cirId));
			str.append(String.format("%10.2f", rating));
			str.append(String.format("%20.2f", flow));
			str.append("\n");
		}
		str.append("*: Branch flow is on limit.");
		return str.toString();
	}
}
