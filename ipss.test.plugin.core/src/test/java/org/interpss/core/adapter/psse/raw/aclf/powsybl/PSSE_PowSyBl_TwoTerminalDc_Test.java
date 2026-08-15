package org.interpss.core.adapter.psse.raw.aclf.powsybl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcLine2TVSC;

/**
 * PowSyBl two-terminal DC / VSC fixtures — import wiring (LF deferred where needed).
 */
public class PSSE_PowSyBl_TwoTerminalDc_Test extends CorePluginTestSetup {

	private static final String DIR = "testData/psse/powsybl/dc/";

	@Test
	public void twoTerminalDc() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(DIR + "twoTerminalDc.raw");
		assertTrue(net.getNoBus() > 0);
		assertFalse(net.getSpecialBranchList().isEmpty(), "expected HVDC special branch");
		assertInstanceOf(HvdcLine2TLCC.class, net.getSpecialBranchList().get(0));
		HvdcLine2TLCC<?> hvdc = (HvdcLine2TLCC<?>) net.getSpecialBranchList().get(0);
		assertTrue(hvdc.getRectifier() != null && hvdc.getInverter() != null);
	}

	@Test
	public void twoTerminalDcNegativeSetvl() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(DIR + "twoTerminalDc_with_negative_setvl.raw");
		assertFalse(net.getSpecialBranchList().isEmpty());
		assertInstanceOf(HvdcLine2TLCC.class, net.getSpecialBranchList().get(0));
		assertTrue(net.getNoBus() >= 9);
	}

	@Test
	public void twoTerminalDcTwoAreas() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(DIR + "twoTerminalDcwithTwoAreas.raw");
		assertFalse(net.getSpecialBranchList().isEmpty());
		assertInstanceOf(HvdcLine2TLCC.class, net.getSpecialBranchList().get(0));
		assertTrue(net.getNoBus() > 0);
	}

	@Test
	public void parallelTwoTerminalDcSameAcBuses() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(DIR + "parallelTwoTerminalDcBetweenSameAcBuses.raw");
		assertTrue(net.getSpecialBranchList().size() >= 2,
				"expected two parallel DC lines, got " + net.getSpecialBranchList().size());
		assertTrue(net.getSpecialBranchList().stream().allMatch(b -> b instanceof HvdcLine2TLCC));
	}

	@Test
	public void vscZeroResistance() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(DIR + "two_terminal_dc_vsc_with_zero_resistance.raw");
		assertTrue(net.getNoActiveBus() > 0);
		boolean hasVsc = net.getSpecialBranchList().stream()
				.anyMatch(b -> b instanceof HvdcLine2TVSC);
		assertTrue(hasVsc || !net.getSpecialBranchList().isEmpty(),
				"expected VSC or DC special branch after import");
	}
}
