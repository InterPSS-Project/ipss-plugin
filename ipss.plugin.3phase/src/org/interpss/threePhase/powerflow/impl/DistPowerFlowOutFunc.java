package org.interpss.threePhase.powerflow.impl;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.net.Branch;
import com.interpss.dstab.BaseDStabBus;

public class DistPowerFlowOutFunc {
	
	public static String powerflowResultSummary(BaseAclfNetwork<? extends BaseAclfBus,? extends AclfBranch> distNet){
		StringBuffer sb = new StringBuffer();
		DStab3PBus swingBus = null;
		
		sb.append("\n\n==================Distribtuion power flow results============\n\n");
		
		sb.append("Bus results: \n");
		for(BaseAclfBus b:distNet.getBusList()){
			DStab3PBus bus = (DStab3PBus) b;
			if(!bus.isActive())
				bus.set3PhaseVotlages(new Complex3x1());
		
			Complex va = bus.get3PhaseVotlages().a_0;
			Complex vb = bus.get3PhaseVotlages().b_1;
			Complex vc = bus.get3PhaseVotlages().c_2;
			double va_angle = ComplexFunc.arg(va);
			double vb_angle = ComplexFunc.arg(vb);
			double vc_angle = ComplexFunc.arg(vc);
			sb.append(bus.getId()+","+va.abs()+","+va_angle+","+vb.abs()+","+vb_angle+","+vc.abs()+","+vc_angle+",");
			sb.append(bus.get3PhaseVotlages().toString()+"\n");
			
			if (bus.isSwing()){
				swingBus = bus;
			}
			
		}
		sb.append("\nBranch results: \n");
		for(AclfBranch bra:distNet.getBranchList()){
			   if(bra.isActive()) {
					DStab3PBranch branch3P = (DStab3PBranch) bra;
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
				DStab3PBus bus3Phase = (DStab3PBus) b;
				Complex3x1 sumOfCurrents = bus3Phase.calcLoad3PhEquivCurInj().multiply(-1);
				for(Branch bra:b.getBranchList()){
					DStab3PBranch branch3P = (DStab3PBranch) bra;
					if (bra.isActive()){
						if(bra.getFromBus().getId().equals(bus3Phase.getId())){
							sumOfCurrents = sumOfCurrents.add(branch3P.getCurrentAbcAtFromSide());
						}
						else{
							sumOfCurrents = sumOfCurrents.subtract(branch3P.getCurrentAbcAtToSide()); // the branch current direction is defined as "from->to" as positive at both ends
						}
					}
			  }
			
			
			Complex3x1 power = bus3Phase.get3PhaseVotlages().multiply(sumOfCurrents.conjugate());
			sb.append("Swing/source bus: "+bus3Phase.getId()+"\n");
			sb.append("power(ABC) on 1-phase MVA base: "+power.toString()+"\n");
			sb.append("three-phase total power on three-phase MVA base: " + power.a_0.add(power.b_1).add(power.c_2).divide(3.0).toString()+"\n\n");
		
			}
		}
		
		return sb.toString();
	}
	
	public static String busLfSummary(DStabNetwork3Phase net){
		StringBuffer sb = new StringBuffer();
		  
		
		  sb.append("\n              ThreePhase power flow summary  \n");
		  sb.append("--------------------------------------------------------------------\n");
		  sb.append("BusId   Bus Name    BaseKv   Voltage A(Mag, Ang)      Voltage B(Mag, Ang)      Voltage C(Mag, Ang)  \n");
		  sb.append("-------------------------------------------------------------------------------------------------\n");
		 for(BaseDStabBus bus: net.getBusList()){
			  if( bus.isActive() && bus instanceof DStab3PBus){
				  
				  DStab3PBus Bus3P = (DStab3PBus) bus;
				  Complex3x1 vabc= Bus3P.get3PhaseVotlages();
				 
				  sb.append(bus.getId()+"   "+bus.getName()+"      "+ String.format("%4.1f    ",(bus.getBaseVoltage()/1000.0))+"    ");
				  sb.append(String.format("   %4.3f   ",vabc.a_0.abs()));
				  
				  sb.append(String.format("%6.2f    ",ComplexFunc.arg(vabc.a_0)*180/Math.PI));
				  
                  sb.append(String.format("     %4.3f   ",vabc.b_1.abs()));
				  
				  sb.append(String.format("%6.2f    ",ComplexFunc.arg(vabc.b_1)*180/Math.PI));
				  
				  
                  sb.append(String.format("    %4.3f   ",vabc.c_2.abs()));
				  
				  sb.append(String.format("%6.2f   \n",ComplexFunc.arg(vabc.c_2)*180/Math.PI));
				  
                
			  }
	  	 }
		 return sb.toString();
	}

}
