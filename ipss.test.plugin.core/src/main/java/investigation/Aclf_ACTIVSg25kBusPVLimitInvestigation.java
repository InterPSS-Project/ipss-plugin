package sample.aclf;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.IpssCorePlugin;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfPVGenBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfAdjCtrlFunction;

public class Aclf_ACTIVSg25kBusPVLimitInvestigation {
	
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
		
		AclfBus bus1 = net.getBus("Bus33439");
		AclfBus bus2 = net.getBus("Bus33441");
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);

		// disable all the controls
		AclfAdjCtrlFunction.disableAllAdjControls.accept(aclfAlgo);
		
		aclfAlgo.setTolerance(1.0E-6);
		aclfAlgo.setMaxIterations(30);
		
		System.out.println("MaxMismatch: " + net.maxMismatch(AclfMethodType.NR));
		
		net.getBusList().forEach(bus -> {
			if (bus.isPVBusLimit()) {
				AclfPVGenBusAdapter pvBus = bus.toPVBus();
				double qGen = pvBus.getGenResults().getImaginary();
				boolean violate = bus.getQGenLimit().isViolated(qGen, 0.001);
				if (violate)
					System.out.println("PV Bus, Id, Qgen, Qmax, Qmin: " + bus.getId() + ", " + 
							pvBus.getGenResults().getImaginary() + ", " + bus.getQGenLimit());
				if (Math.abs(bus.getDesiredVoltMag() - bus.getVoltageMag()) > 0.0001) {
					System.out.println("PV Bus, Id, VMag, VSpec: " + bus.getId() + ", " + 
							bus.getVoltageMag() + ", " + bus.getDesiredVoltMag());
					bus.getPVBusLimit().changeToGenPQBus(qGen);
				}
			}
		});
		
		aclfAlgo.loadflow();
	}
}
