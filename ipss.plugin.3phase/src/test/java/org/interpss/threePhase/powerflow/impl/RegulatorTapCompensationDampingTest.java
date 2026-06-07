package org.interpss.threePhase.powerflow.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.junit.jupiter.api.Test;

class RegulatorTapCompensationDampingTest {
	private static final double TOLERANCE = 1.0e-12;

	@Test
	void dampingStartsFromFractionOfCalculatedCompensation() {
		Complex3x1 calculated = vector(10.0, -6.0, 2.0);

		Complex3x1 damped = DistributionPowerFlowAlgorithmImpl
				.dampCompensationCurrentForTesting(null, calculated, 0.25);

		assertVectorEquals(vector(2.5, -1.5, 0.5), damped);
	}

	@Test
	void dampingMovesPreviousCompensationTowardCalculatedCompensation() {
		Complex3x1 previous = vector(2.0, -2.0, 1.0);
		Complex3x1 calculated = vector(10.0, -6.0, 5.0);

		Complex3x1 damped = DistributionPowerFlowAlgorithmImpl
				.dampCompensationCurrentForTesting(previous, calculated, 0.25);

		assertVectorEquals(vector(4.0, -3.0, 2.0), damped);
	}

	private static Complex3x1 vector(double a, double b, double c) {
		return new Complex3x1(new Complex(a, 0.0), new Complex(b, 0.0), new Complex(c, 0.0));
	}

	private static void assertVectorEquals(Complex3x1 expected, Complex3x1 actual) {
		assertEquals(expected.a_0.getReal(), actual.a_0.getReal(), TOLERANCE);
		assertEquals(expected.b_1.getReal(), actual.b_1.getReal(), TOLERANCE);
		assertEquals(expected.c_2.getReal(), actual.c_2.getReal(), TOLERANCE);
	}
}
