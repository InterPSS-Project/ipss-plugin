package org.interpss.core.dcsys;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginFunction;
import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.dc.DcNetwork;
import com.interpss.dc.DcSysObjectFactory;
import com.interpss.dc.algo.DcPowerFlowAlgorithm;

public class POC_Test2_3  extends CorePluginTestSetup { 
	//@Test
	public void mpptTest() throws Exception {
		//DcNetwork dcNet = CorePluginObjFactory.createDcNetwork("testData/odm/dcsys/poc/Test2_3_odm.xml");
		DcNetwork dcNet = IpssAdapter.importAclfNet("testData/odm/dcsys/poc/Test2_3_odm.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();		
//		System.out.println(dcNet.net2String());
	
		DcPowerFlowAlgorithm algo = DcSysObjectFactory.createDcPowerFlowMppt(dcNet.getDcBus("Inverter"));
		
		algo.calLoadflow(dcNet);
		assertTrue(dcNet.isLfConverged());		
		//System.out.println(net.net2String());
		System.out.println(CorePluginFunction.OutputSolarNet.fx(dcNet));
	}
}

