package sample.dclf;

import org.interpss.display.DclfOutFunc;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.DclfAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import com.interpss.simu.util.sample.SampleHvdcTestingCases;

public class Dclf_Hvdc2TVSC_5BusSample {
	public static void main(String args[]) throws InterpssException {
		AclfNetwork net =  SampleHvdcTestingCases.create2TVSC();

  		//HvdcLine2TVSC<AclfBus> vscHVDC = (HvdcLine2TVSC<AclfBus>) net.getSpecialBranchList().get(0);
  		//System.out.println(vscHVDC.toString(net.getBaseKva()));
  		/*
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.NR);
	  	algo.setMaxIterations(20);
	  	algo.setTolerance(0.0001, UnitType.PU, net.getBaseKva());
	  	algo.loadflow();		
	  	
	  	System.out.println(AclfOutFunc.loadFlowSummary(net, false, false));
		*/
	  	DclfAlgorithm dclfAlgo = DclfAlgoObjectFactory.createDclfAlgorithm(net, CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
		
		System.out.println(DclfOutFunc.dclfResults(dclfAlgo, false));
	}
}	
/*
     BusID          Code           Volt(pu)   Angle(deg)      Pg(pu)    Qg(pu)    Pl(pu)    Ql(pu)    Bus Name   
  ----------------------------------------------------------------------------------------------------------------
  1            PV    + ConstP       1.00000        1.95       0.9969    0.2125    1.6000    0.8000   1          
  2            PQ    + ConstP       1.08865       12.99      -1.0000   -0.0000    2.0000    1.0000   2          
  3                   ConstP        1.05048       -3.98       0.0000    0.0000    3.7000    1.3000   3          
  4            PV                   1.05000       16.94       5.0000    1.0960    0.0000    0.0000   4          
  5            Swing                1.05000        0.00       2.4274    1.8182    0.0000    0.0000   5  
 
      DC Loadflow Results

With Aclf run

   Bud Id       VoltAng(deg)     Gen     Load    ShuntG
=========================================================
       1            1.10        99.69   160.00     0.00 
       2           13.81      -100.00   200.00     0.00 
       3           -4.20         0.00   370.00     0.00 
       4           18.11       500.00     0.00     0.00 
       5            0.00       244.11     0.00     0.00  

Without Aclf run 

   Bud Id       VoltAng(deg)     Gen     Load    ShuntG
=========================================================
       1            1.10        99.69   160.00     0.00 
       2           13.81      -100.00   200.00     0.00 
       3           -4.20         0.00   370.00     0.00 
       4           18.11       500.00     0.00     0.00 
       5            0.00       244.11     0.00     0.00       
*/  
