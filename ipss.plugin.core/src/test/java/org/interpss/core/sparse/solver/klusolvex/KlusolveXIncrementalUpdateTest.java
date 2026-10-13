package org.interpss.core.sparse.solver.klusolvex;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.math3.complex.Complex;
import org.interpss.core.sparse.impl.klusolvex.KlusolveXSparseEqnComplexImpl;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class KlusolveXIncrementalUpdateTest {
    @Test
    void repeatedValueUpdatesAndPatternChangesMatchAnalyticalComplexSolution() throws Exception {
        String library = TestConfig.load("/test-config/native-solver-test.json",
                "KlusolveXIncrementalUpdateTest").string("libraryPath");
        Assumptions.assumeTrue(library != null, "Configure the native library through the JSON fixture");
        KlusolveXLibrary.configureLibrary(library.replace('\\', '/'));
        try {
            var equation = new KlusolveXSparseEqnComplexImpl(2);
            Complex rhs0 = new Complex(1, 2), rhs1 = new Complex(.5, -.2);
            for(int iteration = 0; iteration < 100; iteration++) {
                Complex a = new Complex(4 + .03 * iteration, .1);
                Complex b = iteration >= 40 && iteration < 50 ? Complex.ZERO : new Complex(1, .3);
                Complex c = new Complex(.5, -.1), d = new Complex(3, .2);
                equation.setA(a, 0, 0);
                equation.setA(b, 0, 1);
                equation.setA(c, 1, 0);
                equation.setA(d, 1, 1);
                equation.setBi(rhs0, 0);
                equation.setBi(rhs1, 1);
                equation.solveEqn(1e-20);
                Complex determinant = a.multiply(d).subtract(b.multiply(c));
                Complex expected0 = rhs0.multiply(d).subtract(b.multiply(rhs1)).divide(determinant);
                Complex expected1 = a.multiply(rhs1).subtract(rhs0.multiply(c)).divide(determinant);
                assertEquals(0, expected0.subtract(equation.getX(0)).abs(), 1e-10);
                assertEquals(0, expected1.subtract(equation.getX(1)).abs(), 1e-10);
            }
        } finally {
            KlusolveXLibrary.configureLibrary(null);
        }
    }
}
