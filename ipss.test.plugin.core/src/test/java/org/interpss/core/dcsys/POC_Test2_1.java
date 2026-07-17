package org.interpss.core.dcsys;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginFunction;
import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.dc.DcBus;
import com.interpss.dc.DcSysObjectFactory;
import com.interpss.dc.algo.DcPowerFlowAlgorithm;
import com.interpss.dc.pv.PVDcNetwork;

public class POC_Test2_1  extends CorePluginTestSetup { 
	//@Test
	public void mpptTest() throws Exception {
		//DcNetwork dcNet = CorePluginObjFactory.createDcNetwork("testData/odm/dcsys/poc/Test2_1_odm.xml");
		PVDcNetwork dcNet = IpssAdapter.importAclfNet("testData/odm/dcsys/poc/Test2_1_odm.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();		
//		System.out.println(dcNet.net2String());
/*		
		DcBus bus = dcNet.getDcBus("Bus_Str1");
		System.out.println("Amp@1.13194 " + bus.getPvModule().getAmp(1.13194, UnitType.PU, UnitType.Amp));
		System.out.println("Amp@1.11194 " + bus.getPvModule().getAmp(1.11194, UnitType.PU, UnitType.Amp));
*/		
		DcBus mpptBus = dcNet.getInverterBus();
		DcPowerFlowAlgorithm algo = DcSysObjectFactory.createDcPowerFlowMppt(mpptBus);
		
		algo.calLoadflow(dcNet);
		assertTrue(dcNet.isLfConverged());		
		System.out.println(CorePluginFunction.OutputSolarNet.fx(dcNet));
	}
}

