package sample.aclf;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
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

public class ACTIVSg25kBusAllCtrlSample {
	
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

		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);

		aclfAlgo.getNrMethodConfig().setNonDivergent(true);
		
		aclfAlgo.setTolerance(1.0E-6);
		aclfAlgo.setMaxIterations(50);
		
		System.out.println("MaxMismatch: " + net.maxMismatch(AclfMethodType.NR));
		
		aclfAlgo.loadflow();
	}
}
