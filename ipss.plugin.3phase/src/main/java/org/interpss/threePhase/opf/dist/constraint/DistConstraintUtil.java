package org.interpss.threePhase.opf.dist.constraint;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;

import com.interpss.core.acsc.PhaseCode;

final class DistConstraintUtil {

	private DistConstraintUtil() {
	}

	static double p(Complex3x1 value, PhaseCode phase) {
		Complex c = phaseValue(value, phase);
		return c == null ? 0.0 : c.getReal();
	}

	static double q(Complex3x1 value, PhaseCode phase) {
		Complex c = phaseValue(value, phase);
		return c == null ? 0.0 : c.getImaginary();
	}

	private static Complex phaseValue(Complex3x1 value, PhaseCode phase) {
		if (value == null) {
			return null;
		}
		switch (phase) {
		case A:
			return value.a_0;
		case B:
			return value.b_1;
		case C:
			return value.c_2;
		default:
			return Complex.ZERO;
		}
	}
}
