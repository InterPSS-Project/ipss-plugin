package org.interpss.core.dcsys;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.dc.DcSysObjectFactory;
import com.interpss.dc.algo.DcPowerFlowAlgorithm;
import com.interpss.dc.pv.PVDcBus;
import com.interpss.dc.pv.PVDcNetwork;

public class Inverter_2BusTest  extends CorePluginTestSetup { 
	//@Test
	public void simple2BusPSourceCase() throws Exception {
		//DcNetwork dcNet = CorePluginObjFactory.createDcNetwork("testData/odm/dcsys/simple2BusInverter.xml");
		PVDcNetwork dcNet = IpssAdapter.importAclfNet("testData/odm/dcsys/simple2BusInverter.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();			
		//System.out.println(dcNet.net2String());
		
		DcPowerFlowAlgorithm algo = DcSysObjectFactory.createDcPowerFlowAlgorithm();
		algo.calLoadflow(dcNet);
		assertTrue(dcNet.isLfConverged());
		//System.out.println(dcNet.net2String());
		
		PVDcBus bus = dcNet.getPvDcBus("Bus1");
		//System.out.println(bus.powerInjection());
		assertTrue(Math.abs(bus.powerInjection() + 3.45408) < 0.001);
		
		//System.out.println(bus.getInverter().getPac());
		assertTrue(Math.abs(bus.getInverter().getPac() - 3.26647) < 0.001);
	}
}

