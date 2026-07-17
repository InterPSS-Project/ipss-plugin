package org.interpss.core.adapter.ucte;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;

public class UCTEFormatIEEE14BusTest extends CorePluginTestSetup {
	@Test
	public void testCase1() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.UCTE)
				.load("testData/adpter/ucte/IEEE14.uct")
				.getAclfNet();
		
//		IpssFileAdapter adapter = PluginSpringCtx.getCustomFileAdapter("uct");
//		SimuContext simuCtx = adapter.load("testData/ucte/ieee14.uct");
//  		//System.out.println(simuCtx.getAclfNet().net2String());
	}
}

