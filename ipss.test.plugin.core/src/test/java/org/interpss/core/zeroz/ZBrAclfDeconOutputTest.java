package org.interpss.core.zeroz;

import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

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
public class ZBrAclfDeconOutputTest extends CorePluginTestSetup {
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
  		//System.out.println(AclfOutFunc.loadFlowSummary(net));
  		Map<String,String> results = new HashMap<>();
  		net.getBusList().stream()
  			.filter(bus -> bus.isActive())
  			.forEach(bus -> {
	  			String result = AclfOutFunc.busLfSummary(bus, true);
	  			//System.out.println(result);
	  			results.put(bus.getId(), result);
	  		});
  		
  		// Deconsolidate the network, i.e., restore the zeroZ branches and connected buses to the original state
  		new AclfNetZeroZDeconsolidator(net).deconsolidate(true);
  		
  		//System.out.println(AclfOutFunc.loadFlowSummary(net));
  		net.getBusList().forEach(bus -> {
  			if (results.get(bus.getId()) != null) {
  				String result = AclfOutFunc.busLfSummary(bus, true);
  				//System.out.println(AclfOutFunc.busLfSummary(bus, true));
  				assertTrue("Bus " + bus.getId() + " results do not match", 
  						results.get(bus.getId()).equals(result));
  			}
  		});
    }
}
