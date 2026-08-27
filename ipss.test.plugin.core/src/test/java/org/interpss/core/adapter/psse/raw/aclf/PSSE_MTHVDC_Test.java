package org.interpss.core.adapter.psse.raw.aclf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetModelType;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.HvdcControlMode;
import com.interpss.core.aclf.hvdc.HvdcLineMT;
import com.interpss.core.aclf.hvdc.HvdcMTConverter;
import com.interpss.core.aclf.hvdc.HvdcMTDcBus;
import com.interpss.core.aclf.hvdc.HvdcMTDcLink;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

/**
 * Parser / model wiring and ACLF solve for PSS/E multi-terminal DC using {@code psse_mthvdc.raw}
 * (same case as {@code org.interpss.mthvdc.PSSE_MTHVDC_Sample}).
 */
public class PSSE_MTHVDC_Test extends CorePluginTestSetup {

	private static final String RAW = "testData/psse/v30/psse_mthvdc.raw";

	@Test
	public void testParseMultiTerminalDcSection() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(RAW);

		assertEquals(1, net.getHvdcLineMTList().size());
		HvdcLineMT mt = net.getHvdcLineMT("1");
		assertNotNull(mt);
		assertEquals("1", mt.getName());
		assertEquals(HvdcControlMode.DC_POWER, mt.getControlMode());
		assertEquals("Bus212", mt.getVConvBusId());
		assertEquals("", mt.getVConvNBusId());
		assertEquals(400.0, mt.getVcMod(), 1.0e-6);
		assertTrue(mt.isStatus());

		assertEquals(4, mt.getConverterList().size());
		assertEquals(5, mt.getDcBusList().size());
		assertEquals(4, mt.getDcLinkList().size());

		HvdcMTConverter vconv = mt.getConverterByAcBusId("Bus212");
		assertNotNull(vconv);
		assertEquals(500.0, vconv.getSetValue(), 1.0e-6);
		assertEquals(4, vconv.getNBridges());
		assertEquals(20.0, vconv.getFiringAngLimit().getMax(), 1.0e-6);
		assertEquals(18.0, vconv.getFiringAngLimit().getMin(), 1.0e-6);

		HvdcMTConverter inv = mt.getConverterByAcBusId("Bus213");
		assertNotNull(inv);
		assertEquals(-303.80, inv.getSetValue(), 1.0e-6);

		HvdcMTConverter rec401 = mt.getConverterByAcBusId("Bus401");
		assertNotNull(rec401);
		assertEquals(321.0, rec401.getSetValue(), 1.0e-6);
		assertEquals(0.15, rec401.getMarg(), 1.0e-6);

		HvdcMTDcBus pureDc = mt.getDcBus(5);
		assertNotNull(pureDc);
		assertEquals("", pureDc.getAcBusId());
		assertEquals("DC5", pureDc.getDcName().trim());

		HvdcMTDcBus dc2 = mt.getDcBus(2);
		assertNotNull(dc2);
		assertEquals("Bus212", dc2.getAcBusId());

		HvdcMTDcLink link = mt.getDcLinkList().get(0);
		assertEquals(1, link.getFromIdc());
		assertEquals(5, link.getToIdc());
		assertEquals(29.0, link.getRdc(), 1.0e-6);
		assertEquals(1, link.getMet()); // v30 omits MET → default metered from-end

		assertNull(mt.validateTopology());
		assertEquals(2, mt.getConverterByAcBusId("Bus212").getDcBusNumber());
	}

	/**
	 * Full NR loadflow with MTDC AC P/Q boundary conditions (same setup as the sample).
	 * Uses the explicit ZBR model so the 153–3006 small-X branch is handled correctly.
	 */
	@Test
	public void testMultiTerminalDcLoadflow() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(RAW);

		net.setZeroZBranchThreshold(1.0e-3);
		net.setAclfNetModelType(AclfNetModelType.ZBR_MODEL);

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.getNrMethodConfig().setNonDivergent(false);
		algo.setTolerance(1.0e-6);
		algo.setMaxIterations(100);

		assertTrue(algo.loadflow(), () -> "Final mismatch: " + net.maxMismatch(AclfMethodType.NR));
		assertTrue(net.isLfConverged());

		HvdcLineMT mt = net.getHvdcLineMT("1");
		assertNotNull(mt);
		assertNull(mt.validateTopology());

		// MTDC-only AC terminals must stay active (not treated as island buses)
		assertTrue(net.getBus("Bus401").isActive());
		assertTrue(net.getBus("Bus402").isActive());
		assertFalse(net.getBus("Bus401").isIslandBus());
		assertFalse(net.getBus("Bus402").isIslandBus());

		HvdcMTConverter vconv = mt.getConverterByAcBusId("Bus212");
		HvdcMTConverter inv = mt.getConverterByAcBusId("Bus213");
		HvdcMTConverter rec401 = mt.getConverterByAcBusId("Bus401");
		assertNotNull(vconv);
		assertNotNull(inv);
		assertNotNull(rec401);
		assertTrue(vconv.getPac() < 0.0); // voltage-controlling inverter absorbs P from DC
		assertEquals(-303.8, inv.getPac(), 1.0); // power-order inverter ~ SETVL MW
		assertTrue(rec401.getPac() > 0.0); // rectifier infeed from Bus401
		assertTrue(vconv.getQac() > 0.0);
		assertTrue(inv.getQac() > 0.0);
	}
}
