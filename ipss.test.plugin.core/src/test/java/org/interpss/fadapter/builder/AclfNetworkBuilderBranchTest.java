package org.interpss.fadapter.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.datatype.XfrZCorrection;
import org.interpss.numeric.util.NumericUtil;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.AclfAdjustControlType;
import com.interpss.core.aclf.adj.PSXfrPControl;
import com.interpss.core.aclf.adj.TapControl;
import com.interpss.core.aclf.adpter.AclfPSXformerAdapter;
import com.interpss.core.aclf.adpter.AclfXformerAdapter;
import com.interpss.core.net.BranchBusSide;

/**
 * Unit tests for AclfNetworkBuilder branch APIs: lines, breakers, 2W/PS transformers,
 * tap/angle controls, switching devices, and Z-correction tables.
 */
public class AclfNetworkBuilderBranchTest extends CorePluginTestSetup {

	private static final double TOL = 1.0E-6;

	private AclfNetworkBuilder twoBusNet() throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.addBus("Bus1", "B1", 1L, 138000.0, 1.0, 0.0, null, null, null);
		builder.addBus("Bus2", "B2", 2L, 138000.0, 1.0, 0.0, null, null, null);
		return builder;
	}

	@Test
	public void addLine_setsZShuntsRatingsAndName() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		AclfBranch bra = builder.addLine("Bus1", "Bus2", "1",
				new Complex(0.01, 0.1),
				new Complex(0.0, 0.05),
				new Complex(0.001, 0.002),
				new Complex(0.003, 0.004),
				100.0, 110.0, 120.0, true);

		assertEquals(AclfBranchCode.LINE, bra.getBranchCode());
		assertTrue(bra.isStatus());
		assertEquals("Bus1_to_Bus2_cirId_1", bra.getName());
		assertTrue(NumericUtil.equals(bra.getZ(), new Complex(0.01, 0.1), TOL));
		assertTrue(NumericUtil.equals(bra.getHShuntY(), new Complex(0.0, 0.05), TOL));
		assertTrue(NumericUtil.equals(bra.getFromShuntY(), new Complex(0.001, 0.002), TOL));
		assertTrue(NumericUtil.equals(bra.getToShuntY(), new Complex(0.003, 0.004), TOL));
		assertEquals(100.0, bra.getRatingMva1(), TOL);
		assertEquals(110.0, bra.getRatingMva2(), TOL);
		assertEquals(120.0, bra.getRatingMva3(), TOL);
		assertSame(bra, builder.getBranch(bra.getId()));
		assertEquals(1, builder.getNetwork().getNoBranch());
	}

	@Test
	public void addBreaker_andSwitchingDevice() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		builder.addBus("Bus3", "B3", 3L, 138000.0, 1.0, 0.0, null, null, null);

		AclfBranch breaker = builder.addBreaker("Bus1", "Bus2", "1",
				new Complex(0.0, 0.0001), true, AclfBranchCode.BREAKER);
		assertEquals(AclfBranchCode.BREAKER, breaker.getBranchCode());
		assertTrue(breaker.isStatus());
		assertTrue(NumericUtil.equals(breaker.getZ(), new Complex(0.0, 0.0001), TOL));

		AclfBranch sw = builder.addSwitchingDevice("Bus2", "Bus3", "1",
				false, AclfBranchCode.BREAKER);
		assertEquals(AclfBranchCode.BREAKER, sw.getBranchCode());
		assertFalse(sw.isStatus());
		assertTrue(NumericUtil.equals(sw.getZ(), new Complex(0.0, 0.0), TOL));
		assertEquals("Bus2_to_Bus3_cirId_1", sw.getName());
	}

	@Test
	public void addXformer2W() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		AclfBranch bra = builder.addXformer2W("Bus1", "Bus2", "1",
				new Complex(0.0, 0.1), 1.05, 0.98,
				new Complex(0.0, 0.01), new Complex(0.0, 0.02),
				100.0, 0.0, 0.0, 3, true);

		assertEquals(AclfBranchCode.XFORMER, bra.getBranchCode());
		assertTrue(NumericUtil.equals(bra.getZ(), new Complex(0.0, 0.1), TOL));
		AclfXformerAdapter xfr = bra.toXfr();
		assertEquals(1.05, xfr.getFromTurnRatio(), TOL);
		assertEquals(0.98, xfr.getToTurnRatio(), TOL);
		assertTrue(NumericUtil.equals(bra.getFromShuntY(), new Complex(0.0, 0.01), TOL));
		assertTrue(NumericUtil.equals(bra.getToShuntY(), new Complex(0.0, 0.02), TOL));
		assertEquals(3, bra.getXfrZTableNumber());
		assertEquals(100.0, bra.getRatingMva1(), TOL);
	}

	@Test
	public void addPsXformer() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		AclfBranch bra = builder.addPsXformer("Bus1", "Bus2", "1",
				new Complex(0.0, 0.08), 1.0, 1.0,
				5.0, -2.0,
				null, null,
				100.0, 0.0, 0.0, 0, true);

		assertEquals(AclfBranchCode.PS_XFORMER, bra.getBranchCode());
		AclfPSXformerAdapter ps = bra.toPSXfr();
		assertEquals(5.0, ps.getFromAngle(UnitType.Deg), 1.0E-5);
		assertEquals(-2.0, ps.getToAngle(UnitType.Deg), 1.0E-5);
	}

	@Test
	public void addTapVoltageControl() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		AclfBranch bra = builder.addXformer2W("Bus1", "Bus2", "1",
				new Complex(0.0, 0.1), 1.0, 1.0, null, null,
				100.0, 0.0, 0.0, 0, true);

		TapControl tap = builder.addTapVoltageControl(bra.getId(),
				AclfAdjustControlType.POINT_CONTROL, "Bus2", true,
				1.02, UnitType.PU, 1.1, 0.9, true, false, 0.00625, 32);

		assertNotNull(tap);
		assertTrue(tap.isStatus());
		assertEquals(AclfAdjustControlType.POINT_CONTROL, tap.getAdjControlType());
		assertEquals(1.02, tap.getVSpecified(), TOL);
		assertEquals(1.1, tap.getTurnRatioLimit().getMax(), TOL);
		assertEquals(0.9, tap.getTurnRatioLimit().getMin(), TOL);
		assertTrue(tap.isControlOnFromSide());
		assertFalse(tap.isVcBusOnFromSide());
		assertEquals(0.00625, tap.getTapStepSize(), TOL);
		assertEquals(32, tap.getTapSteps());
		assertTrue(bra.isTapControl());
	}

	@Test
	public void addTapVoltageRangeControl() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		AclfBranch bra = builder.addXformer2W("Bus1", "Bus2", "1",
				new Complex(0.0, 0.1), 1.0, 1.0, null, null,
				100.0, 0.0, 0.0, 0, true);

		TapControl tap = builder.addTapVoltageRangeControl(bra.getId(), "Bus2", true,
				1.05, 0.95, 1.1, 0.9, false, true, 0.0125, 16);

		assertNotNull(tap);
		assertEquals(AclfAdjustControlType.RANGE_CONTROL, tap.getAdjControlType());
		assertEquals(1.05, tap.getDesiredControlRange().getMax(), TOL);
		assertEquals(0.95, tap.getDesiredControlRange().getMin(), TOL);
		assertFalse(tap.isControlOnFromSide());
		assertTrue(tap.isVcBusOnFromSide());
	}

	@Test
	public void addTapMvarControl() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		AclfBranch bra = builder.addXformer2W("Bus1", "Bus2", "1",
				new Complex(0.0, 0.1), 1.0, 1.0, null, null,
				100.0, 0.0, 0.0, 0, true);

		TapControl tap = builder.addTapMvarControl(bra.getId(),
				AclfAdjustControlType.POINT_CONTROL, true,
				20.0, UnitType.mVar, 1.1, 0.9, true, true, null, null);

		assertNotNull(tap);
		assertTrue(tap.isStatus());
		assertTrue(tap.isControlOnFromSide());
		assertTrue(tap.isMeteredOnFromSide());
		assertEquals(1.1, tap.getTurnRatioLimit().getMax(), TOL);
	}

	@Test
	public void addPsXfrAngleControl() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		AclfBranch bra = builder.addPsXformer("Bus1", "Bus2", "1",
				new Complex(0.0, 0.08), 1.0, 1.0, 0.0, 0.0,
				null, null, 100.0, 0.0, 0.0, 0, true);

		PSXfrPControl ctrl = builder.addPsXfrAngleControl(bra.getId(),
				AclfAdjustControlType.POINT_CONTROL, true,
				0.5, UnitType.PU, 30.0, -30.0, true, true, true);

		assertNotNull(ctrl);
		assertTrue(ctrl.isStatus());
		assertEquals(0.5, ctrl.getPSpecified(), TOL);
		assertEquals(30.0, ctrl.getAngLimit(UnitType.Deg).getMax(), TOL);
		assertEquals(-30.0, ctrl.getAngLimit(UnitType.Deg).getMin(), TOL);
		assertTrue(ctrl.isControlOnFromSide());
		assertTrue(ctrl.isFlowFrom2To());
		assertTrue(ctrl.isMeteredOnFromSide());
	}

	@Test
	public void addPsXfrAngleRangeControl() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		AclfBranch bra = builder.addPsXformer("Bus1", "Bus2", "1",
				new Complex(0.0, 0.08), 1.0, 1.0, 0.0, 0.0,
				null, null, 100.0, 0.0, 0.0, 0, true);

		PSXfrPControl ctrl = builder.addPsXfrAngleRangeControl(bra.getId(), true,
				0.6, 0.4, 0.5, UnitType.PU, 25.0, -25.0, false, false, false);

		assertNotNull(ctrl);
		assertEquals(AclfAdjustControlType.RANGE_CONTROL, ctrl.getAdjControlType());
		assertEquals(0.6, ctrl.getDesiredControlRange().getMax(), TOL);
		assertEquals(0.4, ctrl.getDesiredControlRange().getMin(), TOL);
		assertFalse(ctrl.isControlOnFromSide());
		assertFalse(ctrl.isFlowFrom2To());
	}

	@Test
	public void xfrZTableEntry() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		builder.setXfrZTableAdjustSide(BranchBusSide.FROM_SIDE);
		builder.addXfrZTableEntry(1, Arrays.asList(
				new XfrZCorrection(0.9, 1.1),
				new XfrZCorrection(1.0, 1.0),
				new XfrZCorrection(1.1, 0.9)));

		AclfNetwork net = builder.getNetwork();
		assertEquals(BranchBusSide.FROM_SIDE, net.getXfrZAdjustSide());
		assertEquals(1, net.getXfrZTable().size());
		assertEquals(3, net.getXfrZTable().get(0).getPointSet().getPoints().size());
	}
}
