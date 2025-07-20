package org.interpss.plugin.extDataChecker;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.aclf.PSSEExternalDataChecker;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;

public class ExternalDataCheckerTest extends CorePluginTestSetup {	
	@Test
	public void test() throws Exception {
		// load the test data
		AclfNetwork net = IpssAdapter
				.importAclfNet(
						"testData/adpter/psse/PSSE_5Bus_Test_switchShunt.raw")
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_30).load()
				.getImportedObj();
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
		algo.loadflow(new PSSEExternalDataChecker(aclfNet -> {
			// add data checker logic after the initialization here if needed
			// here we can do external data checking configuration
		}));
		
		assertTrue(net.isLfConverged());
		
		String swingId = "Bus1";
		AclfSwingBusAdapter swing = net.getBus(swingId).toSwingBus();
		//System.out.println("AclfNet Model: "+swing.getGenResults(UnitType.PU) );				
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal() - 0.2255) < 0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary() - 0.1585) < 0.0001);
	}
}

