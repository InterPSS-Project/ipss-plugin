package investigation.aclf.ei;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.util.QAUtil;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.AdjustApplyType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfAdjCtrlFunction;

public class EInterconnectAclfInvestigation {
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
		System.out.println(AclfAdjCtrlFunction.nOfPQBusLimit.apply(aclfNet) + " PQ bus limit controls");
		System.out.println(AclfAdjCtrlFunction.nOfRemoteQBus.apply(aclfNet) + " Remote Q buses");
		System.out.println(AclfAdjCtrlFunction.nOfSwitchedShuntBus.apply(aclfNet) + " Switched shunts");
		System.out.println(AclfAdjCtrlFunction.nOfSvcBus.apply(aclfNet) + " SVCs");
		System.out.println(AclfAdjCtrlFunction.nOfTapControl.apply(aclfNet) + " Tap controls");
		System.out.println(AclfAdjCtrlFunction.nOfPSXfrPControl.apply(aclfNet) + " Phase shifting transformer P controls");

		System.out.println("MaxMismatch Before Aclf: " + aclfNet.maxMismatch(AclfMethodType.NR));
		
		AclfNetwork netPsse = aclfNet.jsonCopy();
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);

		aclfAlgo.getNrMethodConfig().setNonDivergent(true);
		
		// disable all the controls
		//AclfAdjCtrlFunction.disableAllAdjControls.accept(aclfAlgo);
		
		aclfAlgo.setTolerance(1.0E-4);
		aclfAlgo.setMaxIterations(50);
				
		aclfAlgo.loadflow();
		
		System.out.println("MaxMismatch After Aclf: " + aclfNet.maxMismatch(AclfMethodType.NR));
		
		QAUtil.getMaxBusVoltageDiff(aclfNet, netPsse);
		
		QAUtil.getMaxGenPOutputDiff(aclfNet, netPsse);
		
		QAUtil.getMaxBranchFlowDiff(aclfNet, netPsse, 0.00001);
    }
}
