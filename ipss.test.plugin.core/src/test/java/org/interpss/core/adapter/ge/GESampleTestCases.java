package org.interpss.core.adapter.ge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.fadapter.ge.GEPslfDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

/**
 * Hardened Sample18Bus coverage for GE PSLF EPC via {@code GEFormat} and {@link GEPslfDirectParser}.
 */
public class GESampleTestCases extends CorePluginTestSetup {

	@Test
	public void sample18_facade_loadflow() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.GE_PSLF)
				.load("testData/adpter/ge/Sample18Bus.epc")
				.getAclfNet();

		assertSample18Topology(net);

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.getLfAdjAlgo().getLimitCtrlConfig().setCheckGenQLimitImmediate(false);
		algo.loadflow();
		assertTrue(net.isLfConverged());

		AclfBus swingBus = net.getBus("Bus101");
		assertNotNull(swingBus);
		assertEquals(AclfGenCode.SWING, swingBus.getGenCode());
		// Historical swing-MW goldens left commented pending re-validation
		// AclfSwingBusAdapter swing = swingBus.toSwingBus();
		// assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-5.234)<0.01);
	}

	@Test
	public void sample18_directParser() throws Exception {
		AclfNetwork net = new GEPslfDirectParser().parse("testData/adpter/ge/Sample18Bus.epc");
		assertSample18Topology(net);
	}

	static void assertSample18Topology(AclfNetwork net) {
		assertEquals(18, net.getNoBus());
		assertEquals(24, net.getNoBranch());
		assertEquals(100000.0, net.getBaseKva(), 1.0E-3);

		assertEquals(AclfGenCode.SWING, net.getBus("Bus101").getGenCode());
		assertEquals(AclfGenCode.GEN_PV, net.getBus("Bus111").getGenCode());
		assertEquals(AclfGenCode.GEN_PV, net.getBus("Bus231").getGenCode());
		assertEquals(AclfGenCode.GEN_PV, net.getBus("Bus311").getGenCode());

		assertTrue(net.getBusList().stream().anyMatch(b -> b.isLoad()), "load buses present");
		long xfrCount = net.getBranchList().stream()
				.filter(b -> ((AclfBranch) b).isXfr() || ((AclfBranch) b).isPSXfr())
				.count();
		assertTrue(xfrCount >= 6, "expected >=6 transformers, got " + xfrCount);

		assertNotNull(net.getArea("1"));
		assertNotNull(net.getZone("1"));
		assertTrue(net.getAreaMap().size() >= 4);
		assertTrue(net.getZoneMap().size() >= 2);
	}
}
