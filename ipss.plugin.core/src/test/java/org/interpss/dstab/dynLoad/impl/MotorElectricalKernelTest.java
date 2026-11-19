package org.interpss.dstab.dynLoad.impl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;

/** The scalar path retains the numerical library's finite-input division exactly. */
class MotorElectricalKernelTest {
    @Test void bothScaledDivisionBranchesPreserveAllBitsAcrossMotorOperatingRanges() {
        var random = new Random(1977);
        double[] actual = new double[2];
        for (int i = 0; i < 10000; i++) {
            double scale = Math.pow(10, random.nextDouble() * 240 - 120);
            double a = random.nextDouble() * 4 - 2, b = random.nextDouble() * 4 - 2;
            double c = scale * (random.nextDouble() * 4 - 2), d = scale * (random.nextDouble() * 4 - 2);
            var expected = new Complex(a, b).divide(new Complex(c, d));
            MotorElectricalKernel.divide(a, b, c, d, actual);
            assertEquals(Double.doubleToLongBits(expected.getReal()), Double.doubleToLongBits(actual[0]));
            assertEquals(Double.doubleToLongBits(expected.getImaginary()), Double.doubleToLongBits(actual[1]));
        }
    }
}
