package org.interpss.core.adapter.bpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.bpa.BPADirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.Area;

/**
 * Card-type gate coverage for {@link BPADirectParser} (BPA IPF NETWORK_DATA).
 */
public class BPADirectParser_CardGate_Test extends CorePluginTestSetup {

	@Test
	public void ieee9_directParser_busLineXfr() throws Exception {
		AclfNetwork net = new BPADirectParser().parse("testData/adpter/bpa/IEEE9.dat");

		assertEquals(9, net.getNoActiveBus());
		assertTrue(net.getNoActiveBranch() >= 9, "6 L + 3 T");
		assertEquals(AclfGenCode.SWING, net.getBus("Bus1").getGenCode());
		assertTrue(net.getBusList().stream().anyMatch(b -> b.getGenCode() == AclfGenCode.GEN_PV),
				"BE buses map to PV");
		assertTrue(net.getBusList().stream().anyMatch(b -> b.getGenCode() == AclfGenCode.NON_GEN
				|| b.isLoad()), "PQ/load buses present");
	}

	@Test
	public void test009_bqMapsToPv() throws Exception {
		AclfNetwork net = new BPADirectParser().parse("testData/adpter/bpa/Test009bpa.DAT");

		assertEquals(9, net.getNoActiveBus());
		long pvCount = net.getBusList().stream()
				.filter(b -> b.getGenCode() == AclfGenCode.GEN_PV)
				.count();
		assertTrue(pvCount >= 2, "BQ cards should become PV buses");

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.loadflow();
		assertTrue(net.isLfConverged());
	}

	@Test
	public void ieee9cn_areaCard() throws Exception {
		AclfNetwork net = new BPADirectParser().parse("testData/adpter/bpa/IEEE9_cn.dat");

		assertEquals(9, net.getNoActiveBus());
		assertNotNull(net.getAreaMap());
		assertFalse(net.getAreaMap().isEmpty(), "live A card should create an area");
		Area nf = net.getArea("NF");
		assertNotNull(nf, "area id NF from A card");
		assertEquals("NF", nf.getName());
	}

	@Test
	public void omib_smoke() throws Exception {
		AclfNetwork net = new BPADirectParser().parse("testData/adpter/bpa/omib-2bus.dat");

		assertEquals(2, net.getNoActiveBus());
		assertEquals(1, net.getNoActiveBranch());
		assertTrue(net.getBusList().stream().anyMatch(b -> b.getGenCode() == AclfGenCode.SWING));
		assertTrue(net.getBusList().stream().anyMatch(b -> b.getGenCode() == AclfGenCode.GEN_PV));
	}

	@Test
	public void skipSafety_rTpPlus_andMvaBaseIgnored() throws Exception {
		AclfNetwork net = new BPADirectParser().parse("testData/adpter/bpa/unit/skip_r_tp_plus.dat");

		assertEquals(2, net.getNoActiveBus());
		// Only the L card should create a branch; R/TP/+ are stubs / ignored
		assertEquals(1, net.getNoActiveBranch());
		// /MVA_BASE=200 is not read — parser hardcodes 100 MVA → baseKva 100000
		assertEquals(100000.0, net.getBaseKva(), 1.0E-3);
	}

	@Test
	public void equivE_branchCard() throws Exception {
		AclfNetwork net = new BPADirectParser().parse("testData/adpter/bpa/unit/equiv_e_branch.dat");

		assertEquals(2, net.getNoActiveBus());
		assertEquals(1, net.getNoActiveBranch(), "E card maps through parseLineCard");
	}

	@Test
	public void commentsAndEndMarker() throws Exception {
		// IEEE9 has .comment lines and (END); parse must stop cleanly
		AclfNetwork net = new BPADirectParser().parse("testData/adpter/bpa/IEEE9.dat");
		assertEquals(9, net.getNoActiveBus());
		assertTrue(net.getNoActiveBranch() >= 9);
	}
}
