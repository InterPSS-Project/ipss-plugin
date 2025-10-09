package sample.aclf;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Counter;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfPVGenBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.AdjustApplyType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfAdjCtrlFunction;

public class Aclf_ACTIVSg25kBusPVLimitCtrlSample {
	
	public static void main(String args[]) throws Exception {
		IpssCorePlugin.init();
		
		//IpssLogger.getLogger().setLevel(Level.INFO);
		
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
		System.out.println(AclfAdjCtrlFunction.nOfZeroZBranch.apply(net) + " Zero-Z branches");
		
		/*
		 * Since this is a solved case, we change PV bus with limit violation to PQ bus,
		 * or bus.desiredVolt <> bus.voltage first
		 */
		double changeThreshold = 0.001;
		Counter cnt = new Counter();
		net.getBusList().forEach(bus -> {
			if (bus.isPVBusLimit()) {
				AclfPVGenBusAdapter pvBus = bus.toPVBus();
				double qGen = pvBus.getGenResults().getImaginary();
				//boolean violate = bus.getQGenLimit().isViolated(qGen, 0.001);
				//if (violate)
				//	System.out.println("PV Bus, Id, Qgen, Qmax, Qmin: " + bus.getId() + ", " + 
				//			pvBus.getGenResults().getImaginary() + ", " + bus.getQGenLimit());
				if (Math.abs(bus.getDesiredVoltMag() - bus.getVoltageMag()) > changeThreshold) {
					//System.out.println("PV Bus, Id, VMag, VSpec: " + bus.getId() + ", " + 
					//		bus.getVoltageMag() + ", " + bus.getDesiredVoltMag());
					bus.getPVBusLimit().changeToGenPQBus(qGen);
					cnt.increment();
				}
			}
		});
		System.out.println("Number of PV bus changed to PQ bus: " + cnt.getCount());
		
		System.out.println("MaxMismatch Before Aclf: " + net.maxMismatch(AclfMethodType.NR));
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);

		// disable all the controls
		AclfAdjCtrlFunction.disableAllAdjControls.accept(aclfAlgo);
		
		/*
		 * Scenario-3: in addition to Switched shunt, enable PV bus limit controls
		 * 
		 * 	Aclf diverges 
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
	}
}
