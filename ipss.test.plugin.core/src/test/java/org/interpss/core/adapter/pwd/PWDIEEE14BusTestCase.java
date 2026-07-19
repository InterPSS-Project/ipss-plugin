package org.interpss.core.adapter.pwd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.fadapter.pwd.PWDDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

/**
 * Hardened IEEE14 + SixBus Base ACLF coverage for PowerWorld AUX / {@code PWDDirectParser}.
 */
public class PWDIEEE14BusTestCase extends CorePluginTestSetup {

	@Test
	public void ieee14_facade_andDirectParser() throws Exception {
		AclfNetwork viaFacade = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.PWD)
				.loadAclfNet("testData/adpter/pwd/ieee14.AUX");
		assertIeee14(viaFacade);

		AclfNetwork viaDirect = new PWDDirectParser().parse("testData/adpter/pwd/ieee14.AUX");
		assertIeee14(viaDirect);

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(viaDirect);
		algo.loadflow();
		assertTrue(viaDirect.isLfConverged(), "IEEE14 PWD case should converge");
	}

	@Test
	public void sixBusBase_directParser() throws Exception {
		AclfNetwork net = new PWDDirectParser().parse("testData/adpter/pwd/SixBus_Base.aux");

		assertEquals(5, net.getNoBus());
		assertTrue(net.getNoBranch() >= 5, "lines + transformers");
		assertNotNull(net.getArea("1"));
		assertNotNull(net.getZone("1"));
		assertEquals(AclfGenCode.SWING, net.getBus("Bus1").getGenCode());
		assertTrue(net.getBranchList().stream().anyMatch(b -> ((com.interpss.core.aclf.AclfBranch) b).isXfr()
				|| ((com.interpss.core.aclf.AclfBranch) b).isPSXfr()),
				"BRANCH BranchDeviceType=Transformer should map as xfr");
	}

	private static void assertIeee14(AclfNetwork net) {
		assertNotNull(net);
		assertTrue(net.getNoBus() >= 14, "expected >=14 buses, got " + net.getNoBus());
		assertTrue(net.getNoBranch() >= 17, "expected >=17 branches, got " + net.getNoBranch());
		assertEquals(AclfGenCode.SWING, net.getBus("Bus1").getGenCode());
		assertTrue(net.getBusList().stream().anyMatch(b -> b.isGen() && b.getGenCode() != AclfGenCode.SWING),
				"generator buses present");
		assertTrue(net.getBusList().stream().anyMatch(b -> b.isLoad()), "load buses present");
		assertNotNull(net.getAreaMap());
		assertFalseEmptyArea(net);
	}

	private static void assertFalseEmptyArea(AclfNetwork net) {
		assertTrue(net.getAreaMap() != null && !net.getAreaMap().isEmpty(), "AREA section should populate areas");
	}
}
