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
		//aclfAlgo.getLfAdjAlgo().getVoltAdjConfig().setDiscreteAdjust(false);
		
		/*
		 * Scenario-2: switched shunt control in continuous adjustment mode
		 * 
		 *   Aclf does not converges in 20 iterations 
		 */
		aclfAlgo.getLfAdjAlgo().getVoltAdjConfig().setDiscreteAdjust(true);
		
		aclfAlgo.setTolerance(1.0E-6);
		aclfAlgo.setMaxIterations(50);
		
		System.out.println("MaxMismatch: " + net.maxMismatch(AclfMethodType.NR));
		
		aclfAlgo.loadflow();
		
	  	AclfBus bus = net.getBus("Bus51072");
  		//System.out.println("Bus51072: " + bus);	
  		//System.out.println("Bus51072: " + bus.getSwitchedShuntList());	
  		
	  	AclfBus swingBus = net.getBus("Bus62120");
	  	AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		System.out.println("Swing bus Gen Results: " + p);	
	}
}

/*
Bus51072: com.interpss.core.aclf.impl.AclfBusImpl@29d334c (id: Bus51072, name: POUND 1, desc: null, number: 51072, status: true, 
statusChangeInfo: NoChange) (booleanFlag: false, intFlag: -1, weight: (0.0, 0.0), sortNumber: 16081, extSeqNumber: 0, 
areaId: 51, zoneId: 1, ownerId: ) (extensionObject: null) (baseVoltage: 69000.0, merge2BusId: , subAreaFlag: -1, substationId: ) 
(genCode: NonGen, loadCode: ConstP, voltageMag: 1.050462827850538, voltageAng: -0.5072532694481307, desiredVoltMag: 1.03225, 
desiredVoltAng: 0.0, genP: 0.0, genQ: 0.0, loadP: 0.14407, loadQ: 0.05068, shuntY: (0.0, 0.0), expLoadP: 0.0, 
expLoadQ: 0.0, qGenLimit: ( 0.0, 0.0 ), pGenLimit: ( 0.0, 0.0 ), vLimit: ( 0.0, 0.0 ), externalPowerIntoNet: null)
*/
