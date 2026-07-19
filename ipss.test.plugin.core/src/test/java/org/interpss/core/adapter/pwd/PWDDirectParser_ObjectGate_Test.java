package org.interpss.core.adapter.pwd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.pwd.PWDDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;

/**
 * Object-type gate coverage for {@link PWDDirectParser} (legacy PowerWorld AUX DATA sections).
 */
public class PWDDirectParser_ObjectGate_Test extends CorePluginTestSetup {

	@Test
	public void ieee14_directParser_objects() throws Exception {
		AclfNetwork net = new PWDDirectParser().parse("testData/adpter/pwd/ieee14.AUX");

		assertTrue(net.getNoBus() >= 14);
		assertTrue(net.getNoBranch() >= 17);
		assertEquals(AclfGenCode.SWING, net.getBus("Bus1").getGenCode());
		assertTrue(net.getBusList().stream().anyMatch(b -> b.getGenCode() == AclfGenCode.GEN_PV));
		assertTrue(net.getBusList().stream().anyMatch(b -> b.isLoad()));
		assertFalse(net.getAreaMap().isEmpty());
	}

	@Test
	public void sixBusBase_topologyAndAreaZone() throws Exception {
		AclfNetwork net = new PWDDirectParser().parse("testData/adpter/pwd/SixBus_Base.aux");

		assertEquals(5, net.getNoBus());
		assertTrue(net.getBranchList().stream().anyMatch(b -> ((AclfBranch) b).isXfr()
				|| ((AclfBranch) b).isPSXfr()));
		assertNotNull(net.getArea("1"));
		assertNotNull(net.getArea("2"));
		assertNotNull(net.getZone("1"));
		assertNotNull(net.getZone("6"));
	}

	@Test
	public void sixBus_phaseShifterAndTap() throws Exception {
		AclfNetwork net = new PWDDirectParser().parse("testData/adpter/pwd/SixBus_2WPsXfr.aux");

		assertTrue(net.getBranchList().stream().anyMatch(b -> ((AclfBranch) b).isPSXfr()),
				"LinePhase nonzero → PS xfr");
		AclfBranch b13 = net.getBranch("Bus1->Bus3(1)");
		assertNotNull(b13);
		assertTrue(b13.isXfr() || b13.isPSXfr());
		assertTrue(Math.abs(b13.getFromTurnRatio() - 0.962) < 1.0e-3);
	}

	@Test
	public void zeroZ_smoke() throws Exception {
		AclfNetwork net = new PWDDirectParser().parse("testData/adpter/pwd/10Bus_zeroImpedance.aux");

		assertTrue(net.getNoBus() >= 8);
		assertTrue(net.getNoBranch() >= 5);
	}

	@Test
	public void skipSafety_ownerAndCaseInfo() throws Exception {
		// ieee14 contains OWNER + PWCASEINFORMATION; parse must ignore them safely
		AclfNetwork net = new PWDDirectParser().parse("testData/adpter/pwd/ieee14.AUX");
		assertTrue(net.getNoBus() >= 14);
		assertTrue(net.getNoBranch() >= 17);

		// Contingency-only AUX should not crash and should not invent buses
		AclfNetwork cntg = new PWDDirectParser().parse("testData/adpter/pwd/10Bus_zeroZ_cntg.aux");
		assertEquals(0, cntg.getNoBus());
	}

	@Test
	public void commentsIgnored_ieee14() throws Exception {
		AclfNetwork net = new PWDDirectParser().parse("testData/adpter/pwd/ieee14.AUX");
		assertTrue(net.getNoBus() >= 14);
	}

	@Test
	public void shunt_unitFixture() throws Exception {
		AclfNetwork net = new PWDDirectParser().parse("testData/adpter/pwd/unit/shunt_2bus.aux");

		assertEquals(2, net.getNoBus());
		assertEquals(1, net.getNoBranch());
		Complex y = net.getBus("Bus2").getShuntY();
		assertNotNull(y);
		assertTrue(Math.abs(y.getImaginary() - 0.2) < 1.0e-6, "20 MVAR on 100 MVA → 0.2 pu, got " + y);
	}
}
