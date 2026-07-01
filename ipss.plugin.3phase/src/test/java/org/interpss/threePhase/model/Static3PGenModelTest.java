package org.interpss.threePhase.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.jupiter.api.Test;

import com.interpss.core.threephase.Static3PGen;
import com.interpss.core.threephase.Static3PhaseFactory;

public class Static3PGenModelTest {

	@Test
	public void primitiveGeneratorCurrentMatchesObjectPath() {
		Static3PGen generator = Static3PhaseFactory.eINSTANCE.createStatic3PGen();
		generator.setPower3Phase(new Complex3x1(new Complex(0.20, 0.05),
				new Complex(0.10, -0.02), Complex.ZERO), UnitType.PU);
		Complex3x1 voltage = new Complex3x1(new Complex(1.02, 0.01),
				new Complex(-0.50, -0.86), new Complex(-0.49, 0.85));

		Complex3x1 expected = generator.getPower3Phase(UnitType.PU).divide(voltage).conjugate();
		double[] primitiveVoltage = {
				voltage.a_0.getReal(), voltage.a_0.getImaginary(),
				voltage.b_1.getReal(), voltage.b_1.getImaginary(),
				voltage.c_2.getReal(), voltage.c_2.getImaginary()
		};
		double[] actual = new double[6];

		generator.addEquivCurrInj(primitiveVoltage, 0, actual, 0);

		assertEquals(expected.a_0.getReal(), actual[0], 1.0e-12);
		assertEquals(expected.a_0.getImaginary(), actual[1], 1.0e-12);
		assertEquals(expected.b_1.getReal(), actual[2], 1.0e-12);
		assertEquals(expected.b_1.getImaginary(), actual[3], 1.0e-12);
		assertEquals(expected.c_2.getReal(), actual[4], 1.0e-12);
		assertEquals(expected.c_2.getImaginary(), actual[5], 1.0e-12);
	}

	@Test
	public void primitiveGeneratorCurrentHonorsActivePhaseMask() {
		Static3PGen generator = Static3PhaseFactory.eINSTANCE.createStatic3PGen();
		generator.setPower3Phase(new Complex3x1(new Complex(0.20, 0.05),
				new Complex(0.10, -0.02), new Complex(0.15, 0.01)), UnitType.PU);
		Complex3x1 voltage = new Complex3x1(new Complex(1.02, 0.01),
				new Complex(-0.50, -0.86), new Complex(-0.49, 0.85));
		double[] primitiveVoltage = {
				voltage.a_0.getReal(), voltage.a_0.getImaginary(),
				voltage.b_1.getReal(), voltage.b_1.getImaginary(),
				voltage.c_2.getReal(), voltage.c_2.getImaginary()
		};
		double[] actual = new double[6];

		generator.addEquivCurrInj(primitiveVoltage, 0, actual, 0, 0b010);

		assertEquals(0.0, actual[0], 1.0e-12);
		assertEquals(0.0, actual[1], 1.0e-12);
		assertEquals(0.0, actual[4], 1.0e-12);
		assertEquals(0.0, actual[5], 1.0e-12);
		assertTrue(Math.hypot(actual[2], actual[3]) > 0.0);
	}
}
