package org.interpss.core.adapter.ge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.ge.GEPslfDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;

/**
 * Section-type gate coverage for {@link GEPslfDirectParser} (GE PSLF EPC).
 */
public class GEPslfDirectParser_SectionGate_Test extends CorePluginTestSetup {

	@Test
	public void sample18_directParser_sections() throws Exception {
		AclfNetwork net = new GEPslfDirectParser().parse("testData/adpter/ge/Sample18Bus.epc");

		assertEquals(18, net.getNoBus());
		assertEquals(24, net.getNoBranch());
		assertEquals(AclfGenCode.SWING, net.getBus("Bus101").getGenCode());
		assertTrue(net.getBusList().stream().anyMatch(b -> b.getGenCode() == AclfGenCode.GEN_PV));
		assertTrue(net.getBusList().stream().anyMatch(b -> b.isLoad()));
		assertTrue(net.getBusList().stream()
				.anyMatch(b -> b.getContributeGenList() != null && !b.getContributeGenList().isEmpty()));
	}

	@Test
	public void sample18_areaAndZone() throws Exception {
		AclfNetwork net = new GEPslfDirectParser().parse("testData/adpter/ge/Sample18Bus.epc");

		assertNotNull(net.getArea("1"));
		assertNotNull(net.getArea("4"));
		assertNotNull(net.getZone("1"));
		assertNotNull(net.getZone("2"));
		assertFalse(net.getAreaMap().isEmpty());
		assertFalse(net.getZoneMap().isEmpty());
	}

	@Test
	public void sample18_transformerTaps() throws Exception {
		AclfNetwork net = new GEPslfDirectParser().parse("testData/adpter/ge/Sample18Bus.epc");

		boolean foundTap = net.getBranchList().stream()
				.map(b -> (AclfBranch) b)
				.filter(b -> b.isXfr() || b.isPSXfr())
				.anyMatch(b -> Math.abs(b.getFromTurnRatio() - 1.0) < 0.05
						&& Math.abs(b.getToTurnRatio() - 1.0) < 0.05);
		assertTrue(foundTap, "Sample18Bus GSU xfrs use ~1.0 pu taps");
	}

	@Test
	public void sample18_skipSafety_andComments() throws Exception {
		// Sample18Bus includes empty SVD/DC/OWNER/INTERFACE headers and # / ! comments
		AclfNetwork net = new GEPslfDirectParser().parse("testData/adpter/ge/Sample18Bus.epc");
		assertEquals(18, net.getNoBus());
		assertEquals(24, net.getNoBranch());
	}

	@Test
	public void ucte2002_summer_parseSmoke() throws Exception {
		AclfNetwork net = new GEPslfDirectParser().parse("testData/adpter/ge/UCTE_2002_Summer.EPC");

		assertTrue(net.getNoBus() >= 1200, "expected >=1200 buses, got " + net.getNoBus());
		assertTrue(net.getNoBranch() > 1500, "expected >1500 branches, got " + net.getNoBranch());
	}

	@Test
	public void shunt_unitFixture() throws Exception {
		AclfNetwork net = new GEPslfDirectParser().parse("testData/adpter/ge/unit/shunt_2bus.epc");

		assertEquals(2, net.getNoBus());
		assertEquals(1, net.getNoBranch());
		Complex y = net.getBus("Bus2").getShuntY();
		assertNotNull(y);
		assertTrue(Math.abs(y.getImaginary() - 0.2) < 1.0e-6, "20 MVAR on 100 MVA → 0.2 pu, got " + y);
	}

	@Test
	public void psXfr_unitFixture() throws Exception {
		AclfNetwork net = new GEPslfDirectParser().parse("testData/adpter/ge/unit/ps_xfr_2bus.epc");

		assertEquals(2, net.getNoBus());
		assertEquals(1, net.getNoBranch());
		assertTrue(net.getBranchList().stream().anyMatch(b -> ((AclfBranch) b).isPSXfr()),
				"nonzero transformer angle should map as PS xfr");
	}
}
