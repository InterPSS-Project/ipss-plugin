package sample.aclf;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;


import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.AdjustApplyType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.impl.solver.optStep.CubicEqnStepSizeCalculator;
import com.interpss.core.funcImpl.AclfAdjCtrlFunction;

public class Aclf_ACTIVSg25kBusSample {
	
	public static void main(String args[]) throws Exception {
		
		//String filename = "ipss-plugin/ipss.test.plugin.core/testData/psse/v33/ACTIVSg25k.RAW";
		String filename = "testData/psse/v33/ACTIVSg25k.RAW";
		
		// load the test data V33
		AclfNetwork net = IpssAdapter.importAclfNet(filename)
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33) 
				.load()
				.getImportedObj();
		
		System.out.println("Buses, Branches: " + net.getNoBus() + ", " + net.getNoBranch());
		System.out.println(AclfAdjCtrlFunction.nOfPVBusLimit.apply(net) + " PV bus limit controls");
		System.out.println(AclfAdjCtrlFunction.nOfPVBusLimitWithLoad.apply(net) + " PV bus limit controls with Load");
		System.out.println(AclfAdjCtrlFunction.nOfPVBusLimitWithSwShuntSVC.apply(net) + " PV bus limit controls with Swithced Shunt or SVC");
		System.out.println(AclfAdjCtrlFunction.nOfPQBusLimit.apply(net) + " PQ bus limit controls");
		System.out.println(AclfAdjCtrlFunction.nOfRemoteQBus.apply(net) + " Remote Q buses");
		System.out.println(AclfAdjCtrlFunction.nOfSwitchedShuntBus.apply(net) + " Switched shunts");
		System.out.println(AclfAdjCtrlFunction.nOfSvcBus.apply(net) + " SVCs");
		System.out.println(AclfAdjCtrlFunction.nOfTapControl.apply(net) + " Tap controls");
		System.out.println(AclfAdjCtrlFunction.nOfPSXfrPControl.apply(net) + " Phase shifting transformer P controls");

		double stepSize = CubicEqnStepSizeCalculator.calStepSize(net);
		System.out.println("Step size(1): " + stepSize);
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);

		aclfAlgo.getLfAdjAlgo().getLimitCtrlConfig().setCheckGenQLimitImmediate(false);

		// disable all the controls
		AclfAdjCtrlFunction.disableAllAdjControls.accept(aclfAlgo);
		
		/*
		 * Scenario-1: enable switched shunt controls, continuous adjustment mode
		 * 
		 *    Aclf converges in 19 iterations
		 */
		aclfAlgo.getLfAdjAlgo().getVoltAdjConfig().setSwitchedShuntAdjust(true);
		//aclfAlgo.getLfAdjAlgo().getVoltAdjConfig().setDiscreteAdjust(false);
		
		/*
		 * Scenario-2: switched shunt control in continuous adjustment mode
		 * 
		 *   Aclf does not converge in 20 iterations 
		 */
		//aclfAlgo.getLfAdjAlgo().getVoltAdjConfig().setDiscreteAdjust(true);
		
		/*
		 * Scenario-3: in addition to Switched shunt, enable PV bus limit controls
		 * 
		 */
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

		/*
		 * Change PV bus to PQ bus with limit violation in the init process
		 */
		//aclfAlgo.getLfAdjAlgo().getLimitCtrlConfig().setCheckGenQLimitImmediate(true);
		
		aclfAlgo.setTolerance(1.0E-6);
		aclfAlgo.setMaxIterations(50);
		
		System.out.println("MaxMismatch: " + net.maxMismatch(AclfMethodType.NR));
		
		aclfAlgo.loadflow();
		
		stepSize = CubicEqnStepSizeCalculator.calStepSize(net);
		System.out.println("Step size(2): " + stepSize);
		
	  	AclfBus swingBus = net.getBus("Bus62120");
	  	AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		System.out.println("Swing bus Gen Results: " + p);	
	}
}
