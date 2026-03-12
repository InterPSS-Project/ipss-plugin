package investigation.aclf.ei;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.util.QAUtil;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfAdjCtrlFunction;
import com.interpss.core.funcImpl.AclfNetInfoHelper;

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
		/*
		 * MaxMismatch Before Aclf: dPmax :  5.5000 at Bus : Bus58540,     dQmax :  4.60353 at Bus : Bus55247
		 * 
		 * Caused by Hvdc line not modeled in the mismatch calculation. 
		 */
		
		AclfNetwork netPsse = aclfNet.jsonCopy();
		
		AclfBus bus = aclfNet.getBus("Bus50320");
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);

		aclfAlgo.getNrMethodConfig().setNonDivergent(true);
		
		// disable all the controls
		//AclfAdjCtrlFunction.disableAllAdjControls.accept(aclfAlgo);
		
		aclfAlgo.setTolerance(1.0E-4);
		aclfAlgo.setMaxIterations(50);
				
		aclfAlgo.loadflow();
		
		System.out.println("MaxMismatch After Aclf: " + aclfNet.maxMismatch(AclfMethodType.NR));
		
		QAUtil.getMaxBusVoltageDiff(aclfNet, netPsse, true);
		
		QAUtil.getMaxGenPOutputDiff(aclfNet, netPsse);
		
		QAUtil.getMaxBranchFlowDiff(aclfNet, netPsse, 0.00001);
		
		String busId = "Bus58540";  // Bus58540, Bus55247, Bus50458
		System.out.println("\n\n===========IpssNet ===========");
		AclfNetInfoHelper.outputBusAclfDebugInfo(aclfNet, busId, false);
		
		System.out.println("\n\n===========PsseNet ===========");
		AclfNetInfoHelper.outputBusAclfDebugInfo(netPsse, busId, false);
		
		/*
		System.out.println("\n\n===========IpssNet ===========");
		AclfNetInfoHelper.outputBusAclfDebugInfo(aclfNet, "Bus50320", false);
		
		System.out.println("\n\n===========PsseNet ===========");
		AclfNetInfoHelper.outputBusAclfDebugInfo(netPsse, "Bus50320", false);
		*/
		
		/*
		System.out.println("\n\n===========IpssNet ===========");
		AclfNetInfoHelper.outputBranchAclfDebugInfo(aclfNet, "Bus27916->Bus27926(1)", false);
		
		System.out.println("\n\n===========PsseNet ===========");
		AclfNetInfoHelper.outputBranchAclfDebugInfo(netPsse, "Bus27916->Bus27926(1)", false);
		*/
		
		/*
		 *      Original PSSE Result
		 *      
		 *      Branch Bus27916->Bus27926(1) connects to two PV buses
		 *      
		 *      Bus27916
		 *      	Voltage: 1.01999998 < -0.42405903552551066 rad
		 *      	Gen Type: GenPV, genP: 9.14006, genQ: 1.156, qGenLimit: ( 12.056, -6.522 ), voltSpec: 1.02

				Bus27926
					Voltage: 1.0203141 < -0.42297337091760007 rad
					Gen Type: GenPV, genP: 6.47147, genQ: 1.15602, qGenLimit: ( 7.68, -4.13 ), voltSpec: 1.02
					
				Observation:
				
					1) There are Q rooms at bus Bus27926. However, the bus voltage is not controlled at the VSpec level.

					2) InterPSS maintains the voltage at the spec level, this the reason why we see branch flow difference
		 */
    }
}
