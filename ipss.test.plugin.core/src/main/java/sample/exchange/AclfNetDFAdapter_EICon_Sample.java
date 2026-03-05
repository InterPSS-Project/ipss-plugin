package sample.exchange;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import java.util.Map;
import java.util.function.Predicate;

import org.dflib.DataFrame;
import org.dflib.csv.Csv;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.plugin.optadj.algo.AclfNetBusOptimizer;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.result.dframe.AclfNetDFrameAdapter;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.AdjustApplyType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import com.interpss.core.funcImpl.AclfAdjCtrlFunction;

public class AclfNetDFAdapter_EICon_Sample {
	//private static final String TEST_ROOT = "ipss.plugin.core/";
	private static final String TEST_ROOT = "";
	
    public static void main(String args[]) throws Exception {
		// load the test data V33
		AclfNetwork aclfNet = IpssAdapter.importAclfNet("testData/psse/v33/Base_Eastern_Interconnect_515GW.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33) 
				.load()
				.getImportedObj();	
		
		System.out.println("Buses, Branches: " + aclfNet.getNoBus() + ", " + aclfNet.getNoBranch());
		System.out.println(AclfAdjCtrlFunction.nOfPVBusLimit.apply(aclfNet) + " PV bus limit controls");
		System.out.println(AclfAdjCtrlFunction.nOfPVBusLimitWithLoad.apply(aclfNet) + " PV bus limit controls with Load");
		System.out.println(AclfAdjCtrlFunction.nOfPVBusLimitWithSwShuntSVC.apply(aclfNet) + " PV bus limit controls with Swithced Shunt or SVC");
		System.out.println(AclfAdjCtrlFunction.nOfZeroZBranch.apply(aclfNet) + " Zero-Z branches");
		
		System.out.println("MaxMismatch Before Aclf: " + aclfNet.maxMismatch(AclfMethodType.NR));
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);

		// disable all the controls
		AclfAdjCtrlFunction.disableAllAdjControls.accept(aclfAlgo);
		
		/*
		 * enable PV bus limit controls
		 * 
		 */		
		aclfAlgo.getLfAdjAlgo().getLimitCtrlConfig().setPvLimitControl(true);
		aclfAlgo.getLfAdjAlgo().getLimitCtrlConfig().setAdjustAppType(AdjustApplyType.POST_ITERATION);
		// PV limit control process starts when max mismatch is below 1.0E-6 x 100
		aclfAlgo.getLfAdjAlgo().getLimitCtrlConfig().setStartPoint(100);
		// PV limit tolerance for limit violation checking is set to 100.0 x 1.0E-6
		aclfAlgo.getLfAdjAlgo().getLimitCtrlConfig().setToleranceFactor(100.0);;
		
		aclfAlgo.setTolerance(1.0E-6);
		aclfAlgo.setMaxIterations(50);
				
		aclfAlgo.loadflow();
		
		System.out.println("MaxMismatch After Aclf: " + aclfNet.maxMismatch(AclfMethodType.NR));
		
	  	AclfNetDFrameAdapter dfAdapter = new AclfNetDFrameAdapter();
	  	dfAdapter.adapt(aclfNet);
	  	
    	DataFrame dfBus = dfAdapter.getDfBus();    	
		System.out.println("Number of rows in dfBus: " + dfBus.height());
		
		DataFrame dfBranch = dfAdapter.getDfBranch();
		System.out.println("Number of rows in dfBranch: " + dfBranch.height());
			
	  	dfAdapter.adapt(aclfNet, true);
	  	
    	dfBus = dfAdapter.getDfBus();    	
		System.out.println("Number of rows with filter in dfBus: " + dfBus.height());
		
		// write the dfBus to a csv file
		Csv.saver().save(dfBus, TEST_ROOT + "output/Base_Eastern_Interconnect_515GW_DF_bus.csv");
		System.out.println("Save to csv file: output/Base_Eastern_Interconnect_515GW_DF_bus.csv");
		
		dfBranch = dfAdapter.getDfBranch();
		System.out.println("Number of rows with filter in dfBranch: " + dfBranch.height());
		
		// write the dfBranch to a csv file
		Csv.saver().save(dfBranch, TEST_ROOT + "output/Base_Eastern_Interconnect_515GW_DF_branch.csv");
		System.out.println("Save to csv file: output/Base_Eastern_Interconnect_515GW_DF_branch.csv");
    }
}
