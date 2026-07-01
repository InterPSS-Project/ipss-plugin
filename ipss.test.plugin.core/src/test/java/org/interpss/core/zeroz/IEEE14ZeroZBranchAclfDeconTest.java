package org.interpss.core.zeroz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetModelType;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.zeroz.AclfNetZeroZBranchHelper;
import com.interpss.core.funcImpl.zeroz.AclfNetZeroZDeconsolidator;


// ZeroZBranch Mark : IEEE14Bus Zero Z Branch Test
public class IEEE14ZeroZBranchAclfDeconTest extends CorePluginTestSetup {
	@Test 
	public void test() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/zeroz/Ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		net.setAclfNetModelType(AclfNetModelType.ZBR_MODEL);
	  	//System.out.println(net.net2String());

		/*
		 * process zeroZ branches by merging the zeroZ branches and connected buses to the retained bus
		 */
		new AclfNetZeroZBranchHelper(net).consolidate();
				
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
	  	//Check if loadflow has converged
  		assertTrue(net.isLfConverged());
  		
  		// See IEEE14Bus_odm_Test.java for the expected values
  		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
  		AclfSwingBusAdapter swing = swingBus.toSwingBus();
		//System.out.println(swing.getGenResults(UnitType.PU).getReal());
		//System.out.println(swing.getGenResults(UnitType.PU).getImaginary());
 		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.3239)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.1654)<0.0001);
  		
		// cashe the bus and branch results for comparison after deconsolidation
		Map<String,String> results = new HashMap<>();
		Map<String,Complex> pIntoNetResults = new HashMap<>();
		net.getBusList().stream()
			.filter(bus -> bus.isActive())
			.forEach(bus -> {
				String result = AclfOutFunc.busLfSummary(bus, true);
				//System.out.println(result);
				results.put(bus.getId(), result);

				Complex pIntoNet = bus.powerIntoNet();
				pIntoNetResults.put(bus.getId(), pIntoNet);
			});

		Map<String,Complex> from2ToResults = new HashMap<>();
		Map<String,Complex> to2FromResults = new HashMap<>();
		net.getBranchList().stream()
			.filter(branch -> !branch.isActive())
			.forEach(branch -> {
				String originalId = branch.getOriginalBranchId().equals("") ?
						branch.getId() : branch.getOriginalBranchId();
				//System.out.println("Branch: " + branch.getId() + " originalId: " + originalId);
				from2ToResults.put(originalId, branch.powerFrom2To());
				to2FromResults.put(originalId, branch.powerTo2From());
			});
  		
  		//System.out.println(net.getBus("Bus4").toString(net.getBaseKva()));
  		//AclfNetInfoHelper.outputBusAclfDebugInfo(net, "Bus4", false);
  		//AclfNetInfoHelper.outputBusAclfDebugInfo(net, "Bus71", false);
  		
  		// Deconsolidate the network, i.e., restore the zeroZ branches and connected buses to the original state
  		new AclfNetZeroZDeconsolidator(net).deconsolidate(true);
  		
  		//System.out.println(net.getBus("Bus4").toString(net.getBaseKva()));
  		//AclfNetInfoHelper.outputBusAclfDebugInfo(net, "Bus4", false);
  		//AclfNetInfoHelper.outputBsAclfDebugInfo(net, "Bus73", false);
  		
  		//System.out.println(AclfOutFunc.loadFlowSummary(net));
  		net.getBusList().forEach(bus -> {
  			if (bus.isConnect2ZeroZBranch()) {
				// We need to the special function to calculate the power into net for the bus
				// connected to zeroZ branch
				Complex pIntoNet = bus.powerIntoNet();
				//System.out.println("Bus " + bus.getId() + " power into net: " + ComplexFunc.toStr(pIntoNet));
				Complex expectedPIntoNet = pIntoNetResults.get(bus.getId());
				if (expectedPIntoNet != null) {
					assertTrue(NumericUtil.equals(expectedPIntoNet, pIntoNet, 1.0e-4),
							"Bus " + bus.getId() + " pIntoNetResults do not match, expected: "
									+ ComplexFunc.toStr(expectedPIntoNet) + ", actual: "
									+ ComplexFunc.toStr(pIntoNet));
				}
			} else {
  				// For the bus not connected to zeroZ branch, we can use the regular method
  				String result = AclfOutFunc.busLfSummary(bus, true);
  				//System.out.println(AclfOutFunc.busLfSummary(bus, true));
				String expectedResult = results.get(bus.getId());
				if (expectedResult != null) {
					assertTrue(expectedResult.equals(result),
							"Bus " + bus.getId() + " results do not match");
				}
  			}
  		});
  		
		net.getBranchList().forEach(branch -> {
			// we only compare the branch results for the branches that are not zeroZ branches
			if (!branch.isZeroZBranch()) {
				//System.out.println("---Branch: " + branch.getId());
				Complex expectedFrom2To = from2ToResults.get(branch.getId());
				Complex expectedTo2From = to2FromResults.get(branch.getId());
				if (expectedFrom2To != null && expectedTo2From != null) {
					Complex actualFrom2To = branch.powerFrom2To();
					assertTrue(NumericUtil.equals(expectedFrom2To, actualFrom2To, 1.0e-4),
							"Branch " + branch.getId() + " from2to results do not match, expected: "
										+ ComplexFunc.toStr(expectedFrom2To) + ", actual: "
										+ ComplexFunc.toStr(actualFrom2To));
					Complex actualTo2From = branch.powerTo2From();
					assertTrue(NumericUtil.equals(expectedTo2From, actualTo2From, 1.0e-4),
							"Branch " + branch.getId() + " to2from results do not match, expected: "
										+ ComplexFunc.toStr(expectedTo2From) + ", actual: "
										+ ComplexFunc.toStr(actualTo2From));
				}
			}
		});
    }
}
/*
  Bus2         PV    + ConstP       1.04500       -4.98       0.4000    0.4355    0.2170    0.1270   -0.0000    0.0000   Bus 2     HV 
  Bus3         PV    + ConstP       1.01000      -12.73       0.0000    0.2507    0.9420    0.1900   -0.0000    0.0000   Bus 3     HV 
  Bus4                ConstP        1.01767      -10.31       0.0000    0.0000    0.4780   -0.0390   -0.0545   -0.1465   Bus 4     HV 
  
  Bus4                ConstP        1.01767      -10.31       0.0000    0.0000    0.4780   -0.0390    0.0000   -0.0001   Bus 4     HV  
  
  Bus5                ConstP        1.01952       -8.77       0.0000    0.0000    0.0760    0.0160   -0.0000    0.0000   Bus 5     HV 
  Bus6         PV    + ConstP       1.07000      -14.22       0.0000    0.1273    0.1120    0.0750   -0.0000    0.0000   Bus 6     LV 
  Bus8         PV                   1.09000      -13.36       0.0000    0.1762    0.0000    0.0000   -0.0000    0.0000   Bus 8     TV 
  Bus9                ConstP        1.05593      -14.94       0.0000    0.0000    0.2950    0.1660   -0.2056   -0.3942   Bus 9     LV 
  Bus10               ConstP        1.05099      -15.10       0.0000    0.0000    0.0900    0.0580    0.0000   -0.0000   Bus 10    LV 
  Bus11               ConstP        1.05691      -14.79       0.0000    0.0000    0.0350    0.0180   -0.0000   -0.0000   Bus 11    LV 
  Bus12               ConstP        1.05519      -15.08       0.0000    0.0000    0.0610    0.0160    0.0000   -0.0000   Bus 12    LV 
  Bus13               ConstP        1.05038      -15.16       0.0000    0.0000    0.1350    0.0580   -0.0617   -0.0916   Bus 13    LV 
 */


	
