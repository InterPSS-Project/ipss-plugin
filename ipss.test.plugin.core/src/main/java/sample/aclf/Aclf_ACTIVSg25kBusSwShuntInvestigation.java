package sample.aclf;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.AclfAdjustControlType;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfAdjCtrlFunction;

public class Aclf_ACTIVSg25kBusSwShuntInvestigation {
	
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
		System.out.println(AclfAdjCtrlFunction.nOfSwitchedShuntBus.apply(net) + " Switched shunts");
		System.out.println(AclfAdjCtrlFunction.nOfSvcBus.apply(net) + " SVCs");
		
		net.getBusList().forEach(bus -> {
			if (bus.isSwitchedShunt()) {
				bus.getSwitchedShuntList().forEach(swShunt -> {
					if (swShunt.getAdjControlType() == AclfAdjustControlType.RANGE_CONTROL) {
						boolean violate = swShunt.getDesiredControlRange().isViolated(bus.getVoltageMag());
						if (violate) {
							//System.out.println("Switched shunt range control violated: " + bus.getId() + ", " + swShunt.getId() + ", " + 
							//	swShunt.getAdjControlType() + ", " + swShunt.getControlMode());
							//System.out.println(" volt range: " + bus.getVoltageMag() + ", " + swShunt.getDesiredControlRange());
						}
					}
					else {
						//System.out.println("Switched shunt point control: " + bus.getId() + ", " + swShunt.getId() + ", " + 
						//		swShunt.getAdjControlType() + ", " + swShunt.getControlMode());
					}
				});
			}
		});
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);

		// disable all the controls
		AclfAdjCtrlFunction.disableAllAdjControls.accept(aclfAlgo);
		
		/*
		 * Scenario-1: enable switched shunt controls, continuous adjustment mode
		 * 
		 *    Aclf converges in 19 iterations
		 */
		aclfAlgo.getLfAdjAlgo().getVoltAdjConfig().setSwitchedShuntAdjust(true);
		aclfAlgo.getLfAdjAlgo().getVoltAdjConfig().setDiscreteAdjust(false);
		
		/*
		 * Scenario-2: switched shunt control in continuous adjustment mode
		 * 
		 *   Aclf does not converges in 20 iterations 
		 */
		//aclfAlgo.getLfAdjAlgo().getVoltAdjConfig().setDiscreteAdjust(true);
		
		aclfAlgo.setTolerance(1.0E-6);
		aclfAlgo.setMaxIterations(100);
		
		System.out.println("MaxMismatch: " + net.maxMismatch(AclfMethodType.NR));
		
		aclfAlgo.loadflow();
		
	  	AclfBus swingBus = net.getBus("Bus62120");
	  	AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		System.out.println("Swing bus Gen Results: " + p);	
	}
}
