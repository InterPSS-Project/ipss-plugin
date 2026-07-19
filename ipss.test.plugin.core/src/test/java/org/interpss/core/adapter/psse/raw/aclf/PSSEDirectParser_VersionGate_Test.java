package org.interpss.core.adapter.psse.raw.aclf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.facts.StaticVarCompensator;

/**
 * Focused coverage of {@link PSSEDirectParser} version gates for RAW v30–v36.
 */
public class PSSEDirectParser_VersionGate_Test extends CorePluginTestSetup {

	@Test
	public void testV30DirectParser_noBus0() throws Exception {
		AclfNetwork net = new PSSEDirectParser(30).parse("testData/adpter/psse/PSSE_5Bus_Test.raw");
		assertNull(net.getBus("Bus0"));
		assertTrue(net.getNoActiveBus() >= 5);
		assertNotNull(net.getBus("Bus1"));
	}

	@Test
	public void testV31FixedShuntAppliedToBusY() throws Exception {
		AclfNetwork net = new PSSEDirectParser(31).parse("testData/psse/v31/sample_v31.raw");
		assertNull(net.getBus("Bus0"));

		// Fixed shunt at Bus203: F1 GL=-5 BL=30 and F2 GL=5 BL=20 => net Y = j0.50 pu on 100 MVA
		AclfBus bus203 = net.getBus("Bus203");
		assertNotNull(bus203);
		Complex y = bus203.getShuntY();
		assertNotNull(y);
		assertEquals(0.0, y.getReal(), 1.0E-6);
		assertEquals(0.50, y.getImaginary(), 1.0E-6);
	}

	@Test
	public void testV33DirectParser_sampleMapped() throws Exception {
		AclfNetwork net = new PSSEDirectParser(33).parse("testData/psse/v33/sample_v33.raw");
		assertNull(net.getBus("Bus0"));
		assertNull(net.getBus("BusGENERAL"));
		assertNull(net.getBus("BusGAUSS"));
		assertTrue(net.getNoActiveBus() > 10);
	}

	@Test
	public void testV34DgenLoadMapping() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v34/ieee9_dgen_v34.raw")
				.setFormat(FileFormat.PSSE)
				.setPsseVersion(PsseVersion.PSSE_34)
				.load()
				.getImportedObj();

		AclfBus bus5 = net.getBus("Bus5");
		assertNotNull(bus5);
		assertTrue(bus5.getContributeLoadList().size() >= 1);
		var load5 = bus5.getContributeLoadList().get(0);
		assertTrue(load5.isDistGenStatus());
		assertEquals(0.25, load5.getDistGenPower().getReal(), 1.0E-6);
		assertEquals(0.10, load5.getDistGenPower().getImaginary(), 1.0E-6);

		AclfBus bus6 = net.getBus("Bus6");
		assertNotNull(bus6);
		var load6 = bus6.getContributeLoadList().get(0);
		assertFalse(load6.isDistGenStatus(), "Bus6 DGENF=0 → offline");
		assertEquals(0.10, load6.getDistGenPower().getReal(), 1.0E-6);
	}

	@Test
	public void testV35SwitchedShuntLayout() throws Exception {
		AclfNetwork net = new PSSEDirectParser(35)
				.parse("testData/psse/v35/PSSE_5Bus_Test_switchShunt_continuous_v35.raw");

		AclfBus bus4 = net.getBus("Bus4");
		assertNotNull(bus4);
		assertTrue(bus4.isSwitchedShunt());
		SwitchedShunt sw = bus4.getFirstSwitchedShunt(true);
		assertEquals(AclfAdjustControlMode.DISCRETE, sw.getControlMode());
		assertEquals(1.03, sw.getDesiredControlRange().getMax(), 1.0E-6);
		assertEquals(1.02, sw.getDesiredControlRange().getMin(), 1.0E-6);
	}

	@Test
	public void testV36XfrZTableParsed() throws Exception {
		AclfNetwork net = new PSSEDirectParser(36).parse("testData/psse/v36/sample_ztable_v36.raw");
		assertNull(net.getBus("Bus0"));
		assertNotNull(net.getXfrZTable());
		assertTrue(net.getXfrZTable().size() >= 1, "v36 complex Z-corr tables should be loaded");
	}

	@Test
	public void testV36SkipSafety_noGeneralGaussBus0() throws Exception {
		AclfNetwork net = new PSSEDirectParser(36).parse("testData/psse/v36/sample_v36.raw");
		assertNull(net.getBus("Bus0"));
		assertNull(net.getBus("BusGENERAL"));
		assertNull(net.getBus("BusGAUSS"));
		assertFalse(net.getBusList().stream().anyMatch(b ->
				b.getId() != null && (b.getId().contains("GENERAL") || b.getId().contains("GAUSS"))));
		assertTrue(net.getNoActiveBus() > 20);
	}

	@Test
	public void testWrongVersionForce_v36AsV30_noBus0() throws Exception {
		// Forcing v30 section layout on a v36 file misaligns sections; Bus0 guard must still hold.
		AclfNetwork net = new PSSEDirectParser(30).parse("testData/psse/v36/sample_v36.raw");
		assertNull(net.getBus("Bus0"));
	}

	@Test
	public void testV36MultiSwitchedShuntIds() throws Exception {
		AclfNetwork net = new PSSEDirectParser(36).parse("testData/psse/v36/sample_v36.raw");
		AclfBus bus152 = net.getBus("Bus152");
		assertNotNull(bus152);
		assertTrue(bus152.getSwitchedShuntList().size() >= 2,
				"v36 allows multiple switched shunts per bus");
		assertTrue(bus152.getSwitchedShuntList().stream()
				.anyMatch(s -> "1".equals(((SwitchedShunt) s).getId())));
		assertTrue(bus152.getSwitchedShuntList().stream()
				.anyMatch(s -> "2".equals(((SwitchedShunt) s).getId())));
	}

	@Test
	public void testV36SeriesFactsDevice() throws Exception {
		AclfNetwork net = new PSSEDirectParser(36).parse("testData/psse/v36/sample_v36.raw");

		// FACTS_DVCE_1 (J=0) → SVC; FACTS_DVCE_2 (J=155, MODE=1) → SVC + target PQ loads
		AclfBus bus153 = net.getBus("Bus153");
		assertNotNull(bus153);
		assertTrue(bus153.getStaticVarCompensatorList().size() >= 2);

		boolean hasSeriesFactsBranch = false;
		for (AclfBranch bra : net.getBranchList()) {
			if (bra.getFromBusId().equals("Bus153") && bra.getToBusId().equals("Bus155")
					&& "FD".equals(bra.getCircuitNumber())) {
				hasSeriesFactsBranch = true;
				assertFalse(bra.isActive(), "MODE=1 series FACTS branch is held inactive");
				break;
			}
		}
		assertTrue(hasSeriesFactsBranch, "series FACTS J≠0 should create Bus153–Bus155(FD)");

		StaticVarCompensator svc = bus153.getStaticVarCompensatorList().get(0);
		assertEquals(1.015, svc.getVSpecified(), 1.0E-6);
	}

	@Test
	public void testDirectParserBands_30_33_36() throws Exception {
		AclfNetwork v30 = new PSSEDirectParser(30)
				.parse("testData/adpter/psse/v30/IEEE9Bus/ieee9.raw");
		AclfNetwork v33 = new PSSEDirectParser(33)
				.parse("testData/psse/v33/ieee9_v33.raw");
		AclfNetwork v36 = new PSSEDirectParser(36)
				.parse("testData/psse/v36/ieee9_v36.raw");

		assertEquals(9, v30.getNoActiveBus());
		assertEquals(9, v33.getNoActiveBus());
		assertEquals(9, v36.getNoActiveBus());
		assertNull(v30.getBus("Bus0"));
		assertNull(v33.getBus("Bus0"));
		assertNull(v36.getBus("Bus0"));
	}
}
