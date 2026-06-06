package org.interpss.threePhase.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.DStab1PLoad;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab1PLoadImpl;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.junit.jupiter.api.Test;

import com.interpss.core.threephase.LoadConnectionType;
import com.interpss.core.aclf.AclfLoadCode;

public class DStab3PLoadModelTest {

	@Test
	public void threePhaseDeltaLoadProducesBalancedLineCurrents() {
		DStab3PLoad load = new DStab3PLoadImpl();
		load.setCode(AclfLoadCode.CONST_P);
		load.setLoadConnectionType(LoadConnectionType.THREE_PHASE_DELTA);
		load.set3PhaseLoad(new Complex3x1(new Complex(1.0, 0.0),
				new Complex(1.0, 0.0), new Complex(1.0, 0.0)));

		Complex3x1 current = load.getEquivCurrInj(new Complex3x1(
				new Complex(1.0, 0.0),
				new Complex(-0.5, -Math.sqrt(3.0) / 2.0),
				new Complex(-0.5, Math.sqrt(3.0) / 2.0)));

		assertEquals(1.0, current.a_0.abs(), 1.0e-12);
		assertEquals(1.0, current.b_1.abs(), 1.0e-12);
		assertEquals(1.0, current.c_2.abs(), 1.0e-12);
		assertEquals(0.0, current.a_0.add(current.b_1).add(current.c_2).abs(), 1.0e-12);
	}

	@Test
	public void wyeConstZLoadCurrentScalesWithVoltageMagnitude() {
		DStab3PLoad load = new DStab3PLoadImpl();
		load.setCode(AclfLoadCode.CONST_Z);
		load.setLoadConnectionType(LoadConnectionType.THREE_PHASE_WYE);
		load.set3PhaseLoad(new Complex3x1(new Complex(0.0, -0.4),
				Complex.ZERO, Complex.ZERO));

		Complex3x1 currentAtOnePu = load.getEquivCurrInj(new Complex3x1(
				new Complex(1.0, 0.0), Complex.ZERO, Complex.ZERO));
		Complex3x1 currentAtHighVoltage = load.getEquivCurrInj(new Complex3x1(
				new Complex(1.1, 0.0), Complex.ZERO, Complex.ZERO));

		assertEquals(0.4, currentAtOnePu.a_0.abs(), 1.0e-12);
		assertEquals(0.44, currentAtHighVoltage.a_0.abs(), 1.0e-12);
	}

	@Test
	public void constPLoadUsesMatchedImpedanceBelowVmin() {
		DStab1PLoad load = new DStab1PLoadImpl();
		load.setCode(AclfLoadCode.CONST_P);
		load.setLoadCP(new Complex(1.0, 0.5));
		load.setVminpu(0.88);

		Complex lowVoltageLoad = load.getLoad(0.80);
		double expectedScale = (0.80 / 0.88) * (0.80 / 0.88);

		assertEquals(expectedScale, lowVoltageLoad.getReal(), 1.0e-12);
		assertEquals(0.5 * expectedScale, lowVoltageLoad.getImaginary(), 1.0e-12);
	}

	@Test
	public void wyeConstPLoadInjectsCurrentBelowVmin() {
		DStab3PLoadImpl load = new DStab3PLoadImpl();
		load.setCode(AclfLoadCode.CONST_P);
		load.setLoadConnectionType(LoadConnectionType.THREE_PHASE_WYE);
		load.setVminpu(0.88);
		load.set3PhaseLoad(new Complex3x1(new Complex(1.0, 0.0),
				Complex.ZERO, Complex.ZERO));

		Complex3x1 current = load.getEquivCurrInj(new Complex3x1(
				new Complex(0.80, 0.0), Complex.ZERO, Complex.ZERO));

		double expectedLoad = (0.80 / 0.88) * (0.80 / 0.88);
		assertEquals(expectedLoad / 0.80, current.a_0.abs(), 1.0e-12);
	}

	@Test
	public void openDssModel4ScalesWattsAndVarsIndependently() {
		DStab1PLoad load = new DStab1PLoadImpl();
		load.setCode(AclfLoadCode.CONST_P);
		load.setLoadCP(new Complex(10.0, 5.0));
		load.setOpenDssModel4(true, 0.8, 3.0);

		Complex scaledLoad = load.getLoad(0.95);

		assertEquals(10.0 * Math.pow(0.95, 0.8), scaledLoad.getReal(), 1.0e-12);
		assertEquals(5.0 * Math.pow(0.95, 3.0), scaledLoad.getImaginary(), 1.0e-12);
	}

	@Test
	public void openDssModel4UsesMatchedImpedanceBelowVmin() {
		DStab1PLoad load = new DStab1PLoadImpl();
		load.setCode(AclfLoadCode.CONST_P);
		load.setLoadCP(new Complex(10.0, 5.0));
		load.setOpenDssModel4(true, 0.8, 3.0);
		load.setVminpu(0.88);

		Complex lowVoltageLoad = load.getLoad(0.80);
		double fallbackScale = (0.80 / 0.88) * (0.80 / 0.88);

		assertEquals(10.0 * Math.pow(0.88, 0.8) * fallbackScale,
				lowVoltageLoad.getReal(), 1.0e-12);
		assertEquals(5.0 * Math.pow(0.88, 3.0) * fallbackScale,
				lowVoltageLoad.getImaginary(), 1.0e-12);
	}

	@Test
	public void threePhaseOpenDssModel4WyeCurrentUsesPhaseVoltage() {
		DStab3PLoadImpl load = new DStab3PLoadImpl();
		load.setCode(AclfLoadCode.CONST_P);
		load.setLoadConnectionType(LoadConnectionType.THREE_PHASE_WYE);
		load.setOpenDssModel4(true, 0.8, 3.0);
		load.set3PhaseLoad(new Complex3x1(new Complex(9.0, 3.0),
				Complex.ZERO, Complex.ZERO));

		Complex3x1 current = load.getEquivCurrInj(new Complex3x1(
				new Complex(0.95, 0.0), Complex.ZERO, Complex.ZERO));
		double expectedP = 9.0 * Math.pow(0.95, 0.8);
		double expectedQ = 3.0 * Math.pow(0.95, 3.0);
		double expectedCurrent = new Complex(expectedP, expectedQ).abs() / 0.95;

		assertEquals(expectedCurrent, current.a_0.abs(), 1.0e-12);
	}
}
