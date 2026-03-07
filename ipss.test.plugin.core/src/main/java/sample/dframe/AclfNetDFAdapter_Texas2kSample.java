package sample.dframe;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.dflib.DataFrame;
import org.dflib.csv.Csv;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.result.dframe.AclfNetDFrameAdapter;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.AdjustApplyType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfAdjCtrlFunction;

public class AclfNetDFAdapter_Texas2kSample {
	//private static final String TEST_ROOT = "ipss.plugin.core/";
	private static final String TEST_ROOT = "";
	
	public static void main(String args[]) throws Exception {
		
		//String filename = "ipss-plugin/ipss.test.plugin.core/testData/psse/v33/ACTIVSg25k.RAW";
		String filename = "testData/psse/v36/texas2k/Texas2k_series24_case1_2016summerPeak_v36.RAW";
		
		// load the test data V33
		AclfNetwork net = IpssAdapter.importAclfNet(filename)
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_36) 
				.load()
				.getImportedObj();
		
		System.out.println("Buses, Branches: " + net.getNoBus() + ", " + net.getNoBranch());
		System.out.println(AclfAdjCtrlFunction.nOfPVBusLimit.apply(net) + " PV bus limit controls");
		System.out.println(AclfAdjCtrlFunction.nOfPVBusLimitWithLoad.apply(net) + " PV bus limit controls with Load");
		System.out.println(AclfAdjCtrlFunction.nOfPVBusLimitWithSwShuntSVC.apply(net) + " PV bus limit controls with Swithced Shunt or SVC");
		System.out.println(AclfAdjCtrlFunction.nOfZeroZBranch.apply(net) + " Zero-Z branches");
		
		System.out.println("MaxMismatch Before Aclf: " + net.maxMismatch(AclfMethodType.NR));
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);

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
		aclfAlgo.setMaxIterations(30);
				
		aclfAlgo.loadflow();
		
		System.out.println("MaxMismatch After Aclf: " + net.maxMismatch(AclfMethodType.NR));
		
	  	AclfNetDFrameAdapter dfAdapter = new AclfNetDFrameAdapter();
	  	dfAdapter.adapt(net);
	  	
    	DataFrame dfBus = dfAdapter.getDfBus();    	
		System.out.println("Number of rows in dfBus: " + dfBus.height());
		
		DataFrame dfBranch = dfAdapter.getDfBranch();
		System.out.println("Number of rows in dfBranch: " + dfBranch.height());
			
	  	dfAdapter.adapt(net, 
	  			bus -> (bus.getVoltageMag() > 1.04 || bus.getVoltageMag() < 0.94),
				gen -> true,
				load -> true,
				branch -> {
					double ratingMVA = branch.getRatingMva1();
					if (ratingMVA <= 0) return false; // skip branches with non-positive rating
					
					double powerFlowMW = branch.powerFrom2To(UnitType.mVA).abs();
					double loadingPercent = Math.abs(powerFlowMW) / ratingMVA * 100.0;
					if (loadingPercent > 70.0) 
						return true;
					else
						return false;
				});
	  	
    	dfBus = dfAdapter.getDfBus();    	
		System.out.println("Number of rows with filter in dfBus: " + dfBus.height());
		
		// write the dfBus to a csv file
		Csv.saver().save(dfBus, TEST_ROOT + "output/Texas2k_DF_bus.csv");
		System.out.println("Save to csv file: output/Texas2k_DF_bus.csv");
		
		dfBranch = dfAdapter.getDfBranch();
		System.out.println("Number of rows with filter in dfBranch: " + dfBranch.height());
		
		// write the dfBranch to a csv file
		Csv.saver().save(dfBranch, TEST_ROOT + "output/Texas2k_DF_branch.csv");
		System.out.println("Save to csv file: output/Texas2k_DF_branch.csv");
	}
}
