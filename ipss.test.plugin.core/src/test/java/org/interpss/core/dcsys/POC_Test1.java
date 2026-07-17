package org.interpss.core.dcsys;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginFunction;
import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.dc.DcBus;
import com.interpss.dc.DcNetwork;
import com.interpss.dc.DcSysObjectFactory;
import com.interpss.dc.algo.DcPowerFlowAlgorithm;
import com.interpss.dc.algo.DcPowerFlowMethod;

public class POC_Test1  extends CorePluginTestSetup { 
	//@Test
	public void baseTest() throws Exception {
		//DcNetwork dcNet = CorePluginObjFactory.createDcNetwork("testData/odm/dcsys/poc/Test1_odm.xml");
		DcNetwork dcNet = IpssAdapter.importAclfNet("testData/odm/dcsys/poc/Test1_odm.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();		

		DcBus bus = dcNet.getDcBus("Inverter");
		bus.setVoltage(375.0/400.0);
		//System.out.println(dcNet.net2String());
		
		DcPowerFlowAlgorithm algo = DcSysObjectFactory.createDcPowerFlowAlgorithm();
		algo.setMethod(DcPowerFlowMethod.PATH);
		
		algo.calLoadflow(dcNet);
		assertTrue(dcNet.isLfConverged());		
		//System.out.println(net.net2String());
		System.out.println(CorePluginFunction.OutputSolarNet.fx(dcNet));
	}

	//@Test
	public void base1Test() throws Exception {
//		DcNetwork dcNet = CorePluginObjFactory.createDcNetwork("testData/odm/dcsys/poc/Test1_odm.xml");
		DcNetwork dcNet = IpssAdapter.importAclfNet("testData/odm/dcsys/poc/Test1_odm.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();		
		
		DcBus bus = dcNet.getDcBus("Inverter");
		bus.setVoltage(378.0/400.0);
		//System.out.println(dcNet.net2String());
		
		DcPowerFlowAlgorithm algo = DcSysObjectFactory.createDcPowerFlowAlgorithm();
		algo.setMethod(DcPowerFlowMethod.PATH);
		
		algo.calLoadflow(dcNet);
		assertTrue(dcNet.isLfConverged());		
		//System.out.println(net.net2String());
		System.out.println(CorePluginFunction.OutputSolarNet.fx(dcNet));
	}

	//@Test
	public void mpptTest() throws Exception {
		//DcNetwork dcNet = CorePluginObjFactory.createDcNetwork("testData/odm/dcsys/poc/Test1_odm.xml");
		DcNetwork dcNet = IpssAdapter.importAclfNet("testData/odm/dcsys/poc/Test1_odm.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();		
		//System.out.println(dcNet.net2String());
		
		DcPowerFlowAlgorithm algo = DcSysObjectFactory.createDcPowerFlowMppt(dcNet.getDcBus("Inverter"));
		
		algo.calLoadflow(dcNet);
		assertTrue(dcNet.isLfConverged());		
		//System.out.println(net.net2String());
		System.out.println(CorePluginFunction.OutputSolarNet.fx(dcNet));
	}
}

