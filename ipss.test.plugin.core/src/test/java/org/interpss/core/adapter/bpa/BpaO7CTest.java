package org.interpss.core.adapter.bpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.bpa.BPADirectParser;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

/**
 * Minimal re-enabled assertions for BPA 07c cases.
 * Large {@code 07c-dc2load.dat} remains disabled (size / L+ noise).
 */
public class BpaO7CTest extends CorePluginTestSetup {

	@Test
	public void sys2010_busCount() throws Exception {
		AclfNetwork net = new BPADirectParser().parse("testData/adpter/bpa/07c_0615_notBE.dat");
		assertEquals(141, net.getBusList().size());
		assertTrue(net.getBranchList().size() > 100);

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.getLfAdjAlgo().getLimitCtrlConfig().setCheckGenQLimitImmediate(false);
		assertTrue(algo.loadflow());
		assertTrue(net.isLfConverged());
	}

	@Test
	@Disabled("Large 07c-dc2load case — keep out of default CI (encoding / L+ / runtime)")
	public void sys2011_lfTestCase() throws Exception {
		AclfNetwork net = new BPADirectParser().parse("testData/adpter/bpa/07c-dc2load.dat");
		assertEquals(536, net.getBusList().size());
		assertEquals(707, net.getBranchList().size());

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.loadflow();
		assertTrue(net.isLfConverged());
	}
}
