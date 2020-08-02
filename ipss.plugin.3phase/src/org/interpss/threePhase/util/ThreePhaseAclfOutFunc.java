package org.interpss.threePhase.util;

import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;

import com.interpss.dstab.BaseDStabBus;

public class ThreePhaseAclfOutFunc {
	
	
	public static String busLfSummary(DStabNetwork3Phase net){
		StringBuffer sb = new StringBuffer();
		  
		
		  sb.append("\n              ThreePhase power flow summary  \n");
		  sb.append("--------------------------------------------------------------------\n");
		  sb.append("BusId   Bus Name    BaseKv   Voltage A(Mag, Ang)      Voltage B(Mag, Ang)      Voltage C(Mag, Ang)  \n");
		  sb.append("-------------------------------------------------------------------------------------------------\n");
		 for(BaseDStabBus<?,?> bus: net.getBusList()){
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
