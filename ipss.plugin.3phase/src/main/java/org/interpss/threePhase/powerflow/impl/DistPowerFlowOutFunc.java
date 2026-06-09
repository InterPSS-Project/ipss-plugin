package org.interpss.threePhase.powerflow.impl;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.threephase.IBranch3Phase;
import com.interpss.core.threephase.IBus3Phase;
import com.interpss.core.net.Branch;

public class DistPowerFlowOutFunc {

	public static String powerflowResultSummary(BaseAclfNetwork<?,?> distNet){
		StringBuffer sb = new StringBuffer();

		sb.append("\n\n==================Distribtuion power flow results============\n\n");

		sb.append("Bus results: \n");
		for(BaseAclfBus b:distNet.getBusList()){
			IBus3Phase bus = (IBus3Phase) b;
			if(!b.isActive()) {
				bus.set3PhaseVotlages(new Complex3x1());
			}

			Complex va = bus.get3PhaseVotlages().a_0;
			Complex vb = bus.get3PhaseVotlages().b_1;
			Complex vc = bus.get3PhaseVotlages().c_2;
			double va_angle = va.getArgument();
			double vb_angle = vb.getArgument();
			double vc_angle = vc.getArgument();
			sb.append(b.getId()+","+va.abs()+","+va_angle+","+vb.abs()+","+vb_angle+","+vc.abs()+","+vc_angle+",");
			sb.append(bus.get3PhaseVotlages().toString()+"\n");

		}
		sb.append("\nBranch results: \n");
		for(AclfBranch bra:distNet.getBranchList()){
			   if(bra.isActive()) {
					IBranch3Phase branch3P = (IBranch3Phase) bra;
					sb.append(bra.getId()+", Iabc (from) = "+
					branch3P.getCurrentAbcAtFromSide().toString()+", Iabc (to) = "+ branch3P.getCurrentAbcAtToSide().toString()+"\n");
					sb.append(bra.getId()+", I012 (from) = "+
							branch3P.getCurrentAbcAtFromSide().to012().toString()+", I012 (to) = "+ branch3P.getCurrentAbcAtToSide().to012().toString()+"\n\n");
				}
			   else {
				   sb.append(bra.getId()+", Iabc (from) = "+
							new Complex3x1().toString()+", Iabc (to) = "+ new Complex3x1().toString()+"\n");
							sb.append(bra.getId()+", I012 (from) = "+
									new Complex3x1().toString()+", I012 (to) = "+ new Complex3x1().toString()+"\n\n");
			   }
			}

		sb.append("\nSourcebus power (from source to the distribution systems(feeders): \n");

		for(BaseAclfBus b:distNet.getBusList()){
			if(b.isActive() && b.isSwing()) {
				IBus3Phase bus3Phase = (IBus3Phase) b;
				Complex3x1 sumOfCurrents = bus3Phase.calcLoad3PhEquivCurInj().multiply(-1);
				for(Branch bra:b.getBranchIterable()){
					IBranch3Phase branch3P = (IBranch3Phase) bra;
					if (bra.isActive()){
						if(bra.getFromBus().getId().equals(b.getId())){
							sumOfCurrents = sumOfCurrents.add(branch3P.getCurrentAbcAtFromSide());
						}
						else{
							sumOfCurrents = sumOfCurrents.subtract(branch3P.getCurrentAbcAtToSide()); // the branch current direction is defined as "from->to" as positive at both ends
						}
					}
			  }


			Complex3x1 power = bus3Phase.get3PhaseVotlages().multiply(sumOfCurrents.conjugate());
			sb.append("Swing/source bus: "+b.getId()+"\n");
			sb.append("power(ABC) on 1-phase MVA base: "+power.toString()+"\n");
			sb.append("three-phase total power on three-phase MVA base: " + power.a_0.add(power.b_1).add(power.c_2).divide(3.0).toString()+"\n\n");

			}
		}

		return sb.toString();
	}

	public static String busLfSummary(BaseAclfNetwork<?,?> net){
		StringBuffer sb = new StringBuffer();


		  sb.append("\n              ThreePhase power flow summary  \n");
		  sb.append("--------------------------------------------------------------------\n");
		  sb.append("BusId   Bus Name    BaseKv   Voltage A(Mag, Ang)      Voltage B(Mag, Ang)      Voltage C(Mag, Ang)  \n");
		  sb.append("-------------------------------------------------------------------------------------------------\n");
		 for(BaseAclfBus bus: net.getBusList()){
			  if(bus.isActive() && bus instanceof IBus3Phase){

				  IBus3Phase Bus3P = (IBus3Phase) bus;
				  Complex3x1 vabc= Bus3P.get3PhaseVotlages();

				  sb.append(bus.getId()+"   "+bus.getName()+"      "+ String.format("%4.1f    ",(bus.getBaseVoltage()/1000.0))+"    ");
				  sb.append(String.format("   %4.3f   ",vabc.a_0.abs()));

				  sb.append(String.format("%6.2f    ", vabc.a_0.getArgument()*180/Math.PI));

                  sb.append(String.format("     %4.3f   ",vabc.b_1.abs()));

				  sb.append(String.format("%6.2f    ", vabc.b_1.getArgument()*180/Math.PI));


                  sb.append(String.format("    %4.3f   ",vabc.c_2.abs()));

				  sb.append(String.format("%6.2f   \n", vabc.c_2.getArgument()*180/Math.PI));


			  }
	  	 }
		 return sb.toString();
	}

}
