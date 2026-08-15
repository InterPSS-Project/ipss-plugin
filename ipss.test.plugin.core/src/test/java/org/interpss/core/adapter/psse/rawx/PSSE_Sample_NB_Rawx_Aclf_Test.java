package org.interpss.core.adapter.psse.rawx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEJsonDirectParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetModelType;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcLineMT;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.topo.AclfNetTopoHelper;
import com.interpss.core.funcImpl.zeroz.AclfNetZeroZBranchHelper;

/**
 * Mirrors {@link org.interpss.nbreaker.PSSE_Sample_NB_Aclf_Sample}: init mismatch,
 * activated / maintenance / activate-then-topo ACLF solves on {@code sample_nb.raw}.
 */
public class PSSE_Sample_NB_Rawx_Aclf_Test extends CorePluginTestSetup {

	private static final String CASE = "testData/private/sample_nb.rawx";

	private static final Set<String> ACTIVATE_BUS_IDS = Set.of("Bus208", "Bus209", "Bus3012");
	private static final Set<String> ACTIVATE_BRANCH_IDS = Set.of(
			"3WNDTR_205_215_208_3->Bus208(3)",
			"Bus209->3WNDTR_209_217_218_4(4)",
			"3WNDTR_3008_3012_3010_2->Bus3012(2)");

	@Test
	public void testInitCondition() throws Exception {
		AclfNetwork net = parseAndConfigure();

		assertEquals(1, net.getHvdcLineMTList().size());
		for (HvdcLineMT mt : net.getHvdcLineMTList()) {
			assertTrue(mt.initLoadflow());
		}
		net.getSpecialBranchList().forEach(branch -> {
			if (branch instanceof HvdcLine2TLCC) {
				((HvdcLine2TLCC<?>) branch).initLoadflow();
			}
		});
		net.calExternalPowerIntoNet();

		Set<String> largeMismatch = new HashSet<>();
		for (AclfBus bus : net.getBusList()) {
			if (bus.mismatch(AclfMethodType.NR).abs() > 1e-1) {
				largeMismatch.add(bus.getId());
			}
		}
		assertTrue(largeMismatch.contains("Bus3021"), largeMismatch.toString());
		assertFalse(largeMismatch.contains("Bus3022"), largeMismatch.toString());
		assertTrue(largeMismatch.contains("Bus9204"), largeMismatch.toString());
	}

	@Test
	public void testActivatedNetworkLoadflow() throws Exception {
		AclfNetwork net = parseAndConfigure();
		activateBusBranch(net);

		new AclfNetZeroZBranchHelper(net).consolidate();
		assertTrue(runSampleLoadflow(net));
		assertTrue(net.isLfConverged());

		assertEquals(49, countActiveBuses(net));
		assertEquals(55, countActiveBranches(net));
		assertSwingPower(net, "Bus301", 29.9231, 9.1823);
		assertSwingPower(net, "Bus401", 3.21, 1.4748);
		assertSwingPower(net, "Bus402", 3.21, 1.4748);
		assertSwingPower(net, "Bus3011", 11.3036, 1.2182);
	}

	@Test
	public void testMaintenanceNetworkLoadflow() throws Exception {
		AclfNetwork net = parseAndConfigure();

		new AclfNetZeroZBranchHelper(net).consolidate();
		assertTrue(runSampleLoadflow(net));
		assertTrue(net.isLfConverged());

		assertEquals(46, countActiveBuses(net));
		assertEquals(52, countActiveBranches(net));
		assertSwingPower(net, "Bus301", 29.9231, 9.1823);
		assertSwingPower(net, "Bus401", 3.21, 1.4748);
		assertSwingPower(net, "Bus402", 3.21, 1.4748);
		assertSwingPower(net, "Bus3011", 11.3036, 1.2183);
	}

	@Test
	public void testActivateThenTopoProcessingLoadflow() throws Exception {
		AclfNetwork net = parseAndConfigure();
		activateBusBranch(net);
		new AclfNetTopoHelper(net).topoProcessing();

		new AclfNetZeroZBranchHelper(net).consolidate();
		assertTrue(runSampleLoadflow(net));
		assertTrue(net.isLfConverged());

		// Same footprint as the maintenance network after topo turns off open-switch equipment
		assertEquals(46, countActiveBuses(net));
		assertEquals(52, countActiveBranches(net));
		assertSwingPower(net, "Bus301", 29.9231, 9.1823);
		assertSwingPower(net, "Bus401", 3.21, 1.4748);
		assertSwingPower(net, "Bus402", 3.21, 1.4748);
		assertSwingPower(net, "Bus3011", 11.3036, 1.2182);
	}

	private static AclfNetwork parseAndConfigure() throws Exception {
		Assumptions.assumeTrue(Files.exists(Path.of(CASE)),
				"Private RAWX fixture unavailable: " + CASE);
		AclfNetwork net = new PSSEJsonDirectParser().parse(CASE);
		net.setZeroZBranchThreshold(1.0e-3);
		net.setAclfNetModelType(AclfNetModelType.ZBR_DECONSOLIDATED);
		return net;
	}

	private static boolean runSampleLoadflow(AclfNetwork net) throws Exception {
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.getLfAdjAlgo().getVoltAdjConfig().setDQ_dVThreshold(0.4);
		algo.getLfAdjAlgo().getVoltAdjConfig().setAdjTolerance(0.05);
		algo.getLfAdjAlgo().getVoltAdjConfig().setXfrTapControl(false);
		algo.getLfAdjAlgo().getVoltAdjConfig().setHvdcTapControl(true);
		algo.getLfAdjAlgo().getPowerAdjConfig().setPsXfrPControl(false);
		algo.getNetAdjAlgo().setAreaInterchangeControlEnabled(false);
		return algo.loadflow();
	}

	private static void activateBusBranch(AclfNetwork net) {
		net.getBusList().forEach(bus -> {
			if (ACTIVATE_BUS_IDS.contains(bus.getId())) {
				bus.setStatus(true);
			}
		});
		net.getBranchList().forEach(branch -> {
			if (ACTIVATE_BRANCH_IDS.contains(branch.getId())) {
				branch.setStatus(true);
			}
		});
	}

	private static long countActiveBuses(AclfNetwork net) {
		return net.getBusList().stream().filter(AclfBus::isActive).count();
	}

	private static long countActiveBranches(AclfNetwork net) {
		return net.getBranchList().stream().filter(b -> b.isActive()).count();
	}

	private static void assertSwingPower(AclfNetwork net, String busId, double p, double q) {
		AclfBus bus = net.getBus(busId);
		assertTrue(bus.isSwing(), busId);
		Complex pq = bus.powerIntoNet();
		assertEquals(p, pq.getReal(), 1.0e-3, busId + " P");
		assertEquals(q, pq.getImaginary(), 1.0e-3, busId + " Q");
	}
}
