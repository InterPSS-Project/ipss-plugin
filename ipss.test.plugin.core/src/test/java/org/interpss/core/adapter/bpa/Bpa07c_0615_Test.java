package org.interpss.core.adapter.bpa;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.bpa.BPADirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

/**
 * Regional BPA smoke: {@code 07c_0615_notBE.dat} (~141 buses).
 */
public class Bpa07c_0615_Test extends CorePluginTestSetup {

	@Test
	public void regionalNotBe_parseAndLoadflow() throws Exception {
		AclfNetwork net = new BPADirectParser().parse("testData/adpter/bpa/07c_0615_notBE.dat");

		assertNotNull(net);
		assertTrue(net.getNoActiveBus() >= 140, "expected ~141 buses, got " + net.getNoActiveBus());
		assertTrue(net.getNoActiveBranch() > 100, "expected a meshed regional network");

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.getLfAdjAlgo().getLimitCtrlConfig().setCheckGenQLimitImmediate(false);
		algo.loadflow();

		assertTrue(net.isLfConverged(), "07c_0615_notBE should converge");
	}
}
