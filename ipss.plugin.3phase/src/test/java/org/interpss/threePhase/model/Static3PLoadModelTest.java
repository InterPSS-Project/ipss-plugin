package org.interpss.threePhase.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.LoadConnectionType;
import com.interpss.core.threephase.Static3PLoad;
import com.interpss.core.threephase.Static3PhaseFactory;

public class Static3PLoadModelTest {

	@Test
	public void threePhaseDeltaLoadProducesBalancedLineCurrents() {
		Static3PLoad load = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
		load.setCode(AclfLoadCode.CONST_P);
		load.setLoadConnectionType(LoadConnectionType.THREE_PHASE_DELTA);
		load.set3PhaseLoad(new Complex3x1(new Complex(1.0, 0.0),
				new Complex(1.0, 0.0), new Complex(1.0, 0.0)));

		Complex3x1 current = load.getEquivCurrInj(balancedVoltage());

		assertEquals(1.0, current.a_0.abs(), 1.0e-12);
		assertEquals(1.0, current.b_1.abs(), 1.0e-12);
		assertEquals(1.0, current.c_2.abs(), 1.0e-12);
		assertEquals(0.0, current.a_0.add(current.b_1).add(current.c_2).abs(), 1.0e-12);
	}

	@Test
	public void wyeConstZLoadCurrentScalesWithVoltageMagnitude() {
		Static3PLoad load = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
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
	public void primitiveWyeLoadCurrentHonorsActivePhaseMask() {
		Static3PLoad load = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
		load.setCode(AclfLoadCode.CONST_P);
		load.setPhaseCode(PhaseCode.B);
		load.setLoadConnectionType(LoadConnectionType.THREE_PHASE_WYE);
		load.set3PhaseLoad(new Complex3x1(new Complex(1.0, 0.5),
				new Complex(2.0, 0.25), new Complex(3.0, 0.75)));
		double[] voltage = balancedVoltageArray();
		double[] current = new double[6];

		load.addEquivCurrInj(voltage, 0, current, 0, 0b010);

		assertEquals(0.0, current[0], 1.0e-12);
		assertEquals(0.0, current[1], 1.0e-12);
		assertEquals(0.0, current[4], 1.0e-12);
		assertEquals(0.0, current[5], 1.0e-12);
		assertTrue(Math.hypot(current[2], current[3]) > 0.0);
	}

	@Test
	public void constPLoadUsesMatchedImpedanceBelowVmin() {
		Static3PLoad load = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
		load.setCode(AclfLoadCode.CONST_P);
		load.setLoadConnectionType(LoadConnectionType.THREE_PHASE_WYE);
		load.setVminpu(0.88);
		load.set3PhaseLoad(new Complex3x1(new Complex(1.0, 0.5),
				Complex.ZERO, Complex.ZERO));

		Complex lowVoltageLoad = load.get3PhaseLoad(new Complex3x1(
				new Complex(0.80, 0.0), Complex.ZERO, Complex.ZERO)).a_0;
		double expectedScale = (0.80 / 0.88) * (0.80 / 0.88);

		assertEquals(expectedScale, lowVoltageLoad.getReal(), 1.0e-12);
		assertEquals(0.5 * expectedScale, lowVoltageLoad.getImaginary(), 1.0e-12);
	}

	@Test
	public void openDssModel4ScalesWattsAndVarsIndependently() {
		Static3PLoad load = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
		load.setCode(AclfLoadCode.CONST_P);
		load.setOpenDssModel4(true, 0.8, 3.0);
		load.set3PhaseLoad(new Complex3x1(new Complex(10.0, 5.0),
				Complex.ZERO, Complex.ZERO));

		Complex scaledLoad = load.get3PhaseLoad(new Complex3x1(
				new Complex(0.95, 0.0), Complex.ZERO, Complex.ZERO)).a_0;

		assertEquals(10.0 * Math.pow(0.95, 0.8), scaledLoad.getReal(), 1.0e-12);
		assertEquals(5.0 * Math.pow(0.95, 3.0), scaledLoad.getImaginary(), 1.0e-12);
	}

	@Test
	public void openDssModel3KeepsPConstantAndScalesQAsImpedance() {
		Static3PLoad load = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
		load.setCode(AclfLoadCode.CONST_P);
		load.setOpenDssLoadModel(3, 1.0, 2.0, null);
		load.set3PhaseLoad(new Complex3x1(new Complex(10.0, 5.0),
				Complex.ZERO, Complex.ZERO));

		Complex scaledLoad = load.get3PhaseLoad(new Complex3x1(
				new Complex(0.90, 0.0), Complex.ZERO, Complex.ZERO)).a_0;

		assertEquals(10.0, scaledLoad.getReal(), 1.0e-12);
		assertEquals(5.0 * 0.90 * 0.90, scaledLoad.getImaginary(), 1.0e-12);
	}

	@Test
	public void openDssModel6KeepsPAndQFixedInsideVoltageBand() {
		Static3PLoad load = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
		load.setCode(AclfLoadCode.CONST_P);
		load.setOpenDssLoadModel(6, 1.0, 2.0, null);
		load.set3PhaseLoad(new Complex3x1(new Complex(10.0, 5.0),
				Complex.ZERO, Complex.ZERO));

		Complex scaledLoad = load.get3PhaseLoad(new Complex3x1(
				new Complex(0.90, 0.0), Complex.ZERO, Complex.ZERO)).a_0;

		assertEquals(10.0, scaledLoad.getReal(), 1.0e-12);
		assertEquals(5.0, scaledLoad.getImaginary(), 1.0e-12);
	}

	@Test
	public void openDssModel8UsesZipvCoefficientsAndCutoff() {
		Static3PLoad load = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
		load.setCode(AclfLoadCode.CONST_P);
		double[] zipv = {0.2, 0.3, 0.5, 0.1, 0.2, 0.7, 0.70};
		load.setOpenDssLoadModel(8, 1.0, 2.0, zipv);
		load.set3PhaseLoad(new Complex3x1(new Complex(10.0, 5.0),
				Complex.ZERO, Complex.ZERO));

		Complex scaledLoad = load.get3PhaseLoad(new Complex3x1(
				new Complex(0.90, 0.0), Complex.ZERO, Complex.ZERO)).a_0;
		double expectedPScale = 0.2 * 0.90 * 0.90 + 0.3 * 0.90 + 0.5;
		double expectedQScale = 0.1 * 0.90 * 0.90 + 0.2 * 0.90 + 0.7;

		assertEquals(10.0 * expectedPScale, scaledLoad.getReal(), 1.0e-12);
		assertEquals(5.0 * expectedQScale, scaledLoad.getImaginary(), 1.0e-12);
		assertEquals(0.0, load.get3PhaseLoad(new Complex3x1(
				new Complex(0.60, 0.0), Complex.ZERO, Complex.ZERO)).a_0.abs(), 1.0e-12);
	}

	@Test
	public void set3PhaseLoadPreservesUnbalancedPerPhaseValues() {
		Static3PLoad load = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
		Complex3x1 unbalancedLoad = new Complex3x1(new Complex(1.0, 0.1),
				new Complex(2.0, 0.2), new Complex(3.0, 0.3));

		load.set3PhaseLoad(unbalancedLoad);

		Complex3x1 storedLoad = load.getInit3PhaseLoad();
		assertEquals(1.0, storedLoad.a_0.getReal(), 1.0e-12);
		assertEquals(2.0, storedLoad.b_1.getReal(), 1.0e-12);
		assertEquals(3.0, storedLoad.c_2.getReal(), 1.0e-12);
		assertEquals(6.0, load.getLoadCP().getReal(), 1.0e-12);
	}

	private static Complex3x1 balancedVoltage() {
		return new Complex3x1(
				new Complex(1.0, 0.0),
				new Complex(-0.5, -Math.sqrt(3.0) / 2.0),
				new Complex(-0.5, Math.sqrt(3.0) / 2.0));
	}

	private static double[] balancedVoltageArray() {
		Complex3x1 voltage = balancedVoltage();
		return new double[] {
				voltage.a_0.getReal(), voltage.a_0.getImaginary(),
				voltage.b_1.getReal(), voltage.b_1.getImaginary(),
				voltage.c_2.getReal(), voltage.c_2.getImaginary()
		};
	}
}
