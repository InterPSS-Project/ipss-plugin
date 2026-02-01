package sample.dclf;

import org.interpss.display.AclfOutFunc;
import org.interpss.display.DclfOutFunc;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.DclfAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import com.interpss.simu.util.sample.SampleHvdcTestingCases;

public class Dclf_Hvdc2TLCC_5BusSample {
	public static void main(String args[]) throws InterpssException {
  		AclfNetwork net = SampleHvdcTestingCases.create2TSingleLCC();
		
  		/*
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.NR);
	  	algo.setMaxIterations(20);
	  	algo.setTolerance(0.0001, UnitType.PU, net.getBaseKva());
	  	algo.loadflow();			
	  	*/
	  	/*
	  	 * There is a Hvdc line from bus 4_1 to bus 5_1
	  	 */
	  	System.out.println(AclfOutFunc.loadFlowSummary(net, false, false));
		
	  	DclfAlgorithm dclfAlgo = DclfAlgoObjectFactory.createDclfAlgorithm(net, CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
		
		System.out.println(DclfOutFunc.dclfResults(dclfAlgo, false));
	}
}	
/*
     BusID          Code           Volt(pu)   Angle(deg)      Pg(pu)    Qg(pu)    Pl(pu)    Ql(pu)    Bus Name   
  ----------------------------------------------------------------------------------------------------------------
  1                   ConstP        0.83086      -36.59       0.0000    0.0000    1.6000    0.8000   1          
  2                   ConstP        1.07130      -30.00       0.0000    0.0000    2.0000    1.0000   2          
  3                   ConstP        1.04206      -10.21       0.0000    0.0000    3.7000    1.3000   3          
  4            PV                   1.05000      -28.82       5.0000    3.1163    0.0000    0.0000   4          
  5            Swing                1.05000        0.00       2.7498    3.6600    0.0000    0.0000   5          
  4_1                               1.03479      -30.62       0.0000    0.0000    0.0000    0.0000   4_1        
  5_1                               1.04073        1.86       0.0000    0.0000    0.0000    0.0000   5_1        
  
      DC Loadflow Results

With Aclf run

  Bud Id       VoltAng(deg)     Gen     Load    ShuntG
=========================================================
       1          -34.70         0.00   160.00     0.00 
       2          -28.14         0.00   200.00     0.00 
       3          -10.40         0.00   370.00     0.00 
       4          -26.88       500.00     0.00     0.00 
       5            0.00       262.94     0.00     0.00 
     4_1          -28.90      -352.00     0.00     0.00 
     5_1            1.96       341.87     0.00     0.00 

Without Aclf run 

   Bud Id       VoltAng(deg)     Gen     Load    ShuntG
=========================================================
       1          -34.70         0.00   160.00     0.00 
       2          -28.14         0.00   200.00     0.00 
       3          -10.40         0.00   370.00     0.00 
       4          -26.88       500.00     0.00     0.00 
       5            0.00       262.94     0.00     0.00 
     4_1          -28.90      -352.00     0.00     0.00 
     5_1            1.96       341.87     0.00     0.00      
*/  
