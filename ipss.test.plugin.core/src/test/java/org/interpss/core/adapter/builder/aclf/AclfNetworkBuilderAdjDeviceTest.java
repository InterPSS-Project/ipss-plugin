package org.interpss.core.adapter.builder.aclf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.builder.AclfNetworkBuilder.ShuntBlock;
import org.interpss.numeric.util.NumericUtil;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.ShuntCompensator;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adj.AclfAdjustControlType;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.facts.StaticVarCompensator;
import com.interpss.core.aclf.flow.FlowInterface;
import com.interpss.core.aclf.flow.FlowInterfaceBranch;
import com.interpss.core.aclf.flow.FlowInterfaceType;

/**
 * Unit tests for AclfNetworkBuilder adjustment devices: switched shunt, SVC,
 * FACTS modes 0–3, and flow interfaces.
 */
public class AclfNetworkBuilderAdjDeviceTest extends CorePluginTestSetup {

	private static final double TOL = 1.0E-6;

	private AclfNetworkBuilder twoBusNet() throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.setNetworkInfo("adj", "adj", 100000.0, com.interpss.core.net.OriginalDataFormat.PSSE);
		builder.addBus("Bus1", "B1", 1L, 138000.0, 1.0, 0.0, null, null, null);
		builder.addBus("Bus2", "B2", 2L, 138000.0, 1.0, 0.0, null, null, null);
		return builder;
	}

	@Test
	public void addSwitchedShunt_withBlocksAndRemoteBus() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		List<ShuntBlock> blocks = Arrays.asList(
				new ShuntBlock(5, 10.0, true),
				new ShuntBlock(3, -5.0, true),
				new ShuntBlock(2, 8.0, false));

		SwitchedShunt sw = builder.addSwitchedShunt("Bus1", "1", true,
				AclfAdjustControlMode.DISCRETE, AclfAdjustControlType.RANGE_CONTROL,
				0.05, 1.05, 0.95, "Bus2", blocks);

		assertNotNull(sw);
		assertEquals("1", sw.getId());
		assertTrue(sw.isStatus());
		assertEquals(0.05, sw.getBInit(), TOL);
		assertEquals(AclfAdjustControlMode.DISCRETE, sw.getControlMode());
		assertEquals(AclfAdjustControlType.RANGE_CONTROL, sw.getAdjControlType());
		assertEquals(1.05, sw.getDesiredControlRange().getMax(), TOL);
		assertEquals(0.95, sw.getDesiredControlRange().getMin(), TOL);
		assertEquals("Bus2", sw.getRemoteBusBranchId());
		assertEquals(3, sw.getShuntCompensatorList().size());

		ShuntCompensator bank0 = sw.getShuntCompensatorList().get(0);
		assertEquals(5, bank0.getSteps());
		assertEquals(10.0, bank0.getUnitQMvar(), TOL);
		assertTrue(bank0.isStatus());

		ShuntCompensator bank2 = sw.getShuntCompensatorList().get(2);
		assertFalse(bank2.isStatus());
		// active: +5*10/100 = 0.5 max; -3*5/100 = -0.15 min
		assertEquals(0.5, sw.getBLimit().getMax(), TOL);
		assertEquals(-0.15, sw.getBLimit().getMin(), TOL);
	}

	@Test
	public void addSwitchedShunt_localRemoteDefaultsToBusId() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		SwitchedShunt sw = builder.addSwitchedShunt("Bus1", "1", true,
				AclfAdjustControlMode.FIXED, AclfAdjustControlType.POINT_CONTROL,
				0.0, 0.0, 0.0, null, null);
		assertEquals("Bus1", sw.getRemoteBusBranchId());
		assertSame(builder.getBus("Bus1"), sw.getRemoteBus());
	}

	@Test
	public void addSVC_localSetsPvGenCode() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		StaticVarCompensator svc = builder.addSVC("Bus1", "SVC1", true,
				0.5, -0.3, 1.02, null, 100.0);

		assertNotNull(svc);
		assertEquals("SVC1", svc.getId());
		assertTrue(svc.isStatus());
		assertEquals(0.5, svc.getQLimit().getMax(), TOL);
		assertEquals(-0.3, svc.getQLimit().getMin(), TOL);
		assertEquals(1.02, svc.getVSpecified(), TOL);
		assertEquals("Bus1", svc.getRemoteBusBranchId());
		assertSame(builder.getBus("Bus1"), svc.getRemoteBus());
		assertEquals(AclfGenCode.GEN_PV, builder.getBus("Bus1").getGenCode());
		assertEquals(100.0, svc.getRemoteControlPercentage(), TOL);
	}

	@Test
	public void addSVC_remoteSetsPqGenCode() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		StaticVarCompensator svc = builder.addSVC("Bus1", "SVC1", true,
				0.4, -0.2, 1.0, "Bus2", 80.0);

		assertEquals("Bus2", svc.getRemoteBusBranchId());
		assertEquals(AclfGenCode.GEN_PQ, builder.getBus("Bus1").getGenCode());
		assertEquals(80.0, svc.getRemoteControlPercentage(), TOL);
	}

	@Test
	public void addFactsDevice_mode0_seriesReactance() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		AclfBranch bra = builder.addFactsDevice("Bus1", "Bus2", "FD",
				0, 0.05, 0.0, 0.0,
				0.0, 0.0, 0.0, null, 0.0, null, true);

		assertTrue(bra.isStatus());
		assertTrue(NumericUtil.equals(bra.getZ(), new Complex(0.0, 0.05), TOL));
		assertEquals(0, builder.getBus("Bus1").getStaticVarCompensatorList().size());
	}

	@Test
	public void addFactsDevice_mode1_offlineWithSvcAndFixedLoads() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		Complex targetPQ = new Complex(0.2, 0.1);
		AclfBranch bra = builder.addFactsDevice("Bus1", "Bus2", "FD",
				1, 0.05, 0.0, 0.0,
				0.3, -0.2, 1.01, "Bus2", 100.0, targetPQ, true);

		assertFalse(bra.isStatus());
		assertTrue(NumericUtil.equals(bra.getZ(), new Complex(0.0, 0.05), TOL));
		assertEquals(1, builder.getBus("Bus1").getStaticVarCompensatorList().size());
		StaticVarCompensator factsSvc =
				(StaticVarCompensator) builder.getBus("Bus1").getStaticVarCompensatorList().get(0);
		assertEquals("SVC@FD", factsSvc.getId());

		assertEquals(1, builder.getBus("Bus1").getContributeLoadList().size());
		assertEquals(1, builder.getBus("Bus2").getContributeLoadList().size());
		AclfLoad fromLoad = (AclfLoad) builder.getBus("Bus1").getContributeLoadList().get(0);
		AclfLoad toLoad = (AclfLoad) builder.getBus("Bus2").getContributeLoadList().get(0);
		assertEquals(AclfLoadCode.CONST_P, fromLoad.getCode());
		assertTrue(NumericUtil.equals(fromLoad.getLoadCP(), targetPQ, TOL));
		assertTrue(NumericUtil.equals(toLoad.getLoadCP(), targetPQ.negate(), TOL));
	}

	@Test
	public void addFactsDevice_mode2_zeroZWithSvc() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		AclfBranch bra = builder.addFactsDevice("Bus1", "Bus2", "FD",
				2, 0.0, 0.0, 0.0,
				0.25, -0.15, 1.0, null, 100.0, null, true);

		assertTrue(bra.isStatus());
		assertTrue(NumericUtil.equals(bra.getZ(), new Complex(0.0, 0.0), TOL));
		assertEquals(1, builder.getBus("Bus1").getStaticVarCompensatorList().size());
	}

	@Test
	public void addFactsDevice_mode3_customZWithSvc() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		AclfBranch bra = builder.addFactsDevice("Bus1", "Bus2", "FD",
				3, 0.0, 0.01, 0.05,
				0.2, -0.1, 1.0, null, 100.0, null, true);

		assertTrue(NumericUtil.equals(bra.getZ(), new Complex(0.01, 0.05), TOL));
		assertEquals(1, builder.getBus("Bus1").getStaticVarCompensatorList().size());
	}

	@Test
	public void flowInterface_forwardAndReverseBranchDirection() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		AclfBranch line = builder.addLine("Bus1", "Bus2", "1",
				new Complex(0.01, 0.1), null, null, null, 100.0, 0.0, 0.0, true);

		FlowInterface intf = builder.addFlowInterface("IF1");
		assertNotNull(intf);
		assertEquals("IF1", intf.getId());

		FlowInterfaceBranch fwd = builder.addInterfaceBranch(intf, "Bus1", "Bus2", "1", 1.0);
		assertTrue(fwd.isBranchDir());
		assertEquals(1.0, fwd.getWeight(), TOL);
		assertSame(line, fwd.getBranch());

		FlowInterfaceBranch rev = builder.addInterfaceBranch(intf, "Bus2", "Bus1", "1", 0.5);
		assertFalse(rev.isBranchDir());
		assertEquals(0.5, rev.getWeight(), TOL);
		assertSame(line, rev.getBranch());
	}

	@Test
	public void setInterfaceLimit_onPeakAndOffPeak() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		builder.addLine("Bus1", "Bus2", "1",
				new Complex(0.01, 0.1), null, null, null, 100.0, 0.0, 0.0, true);
		FlowInterface intf = builder.addFlowInterface("IF1");

		builder.setInterfaceLimit(intf, true, true, FlowInterfaceType.BG, 1.5, 1.2);
		assertNotNull(intf.getOnPeakLimit());
		assertTrue(intf.getOnPeakLimit().isStatus());
		assertEquals(FlowInterfaceType.BG, intf.getOnPeakLimit().getType());
		assertEquals(1.5, intf.getOnPeakLimit().getRefDirExportLimit(), TOL);
		assertEquals(1.2, intf.getOnPeakLimit().getOppsiteRefDirImportLimit(), TOL);

		builder.setInterfaceLimit(intf, false, false, FlowInterfaceType.NG, 0.8, 0.6);
		assertNotNull(intf.getOffPeakLimit());
		assertFalse(intf.getOffPeakLimit().isStatus());
		assertEquals(0.8, intf.getOffPeakLimit().getRefDirExportLimit(), TOL);
		assertEquals(0.6, intf.getOffPeakLimit().getOppsiteRefDirImportLimit(), TOL);

		AclfNetwork net = builder.getNetwork();
		assertTrue(net.isFlowInterfaceLoaded());
	}
}
