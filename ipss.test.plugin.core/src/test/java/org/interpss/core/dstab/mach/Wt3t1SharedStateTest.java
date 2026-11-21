package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.*;

import com.interpss.dstab.mach.model.*;
import org.junit.jupiter.api.Test;

/** Shared leaf integration and lifecycle checks; converter bundle qualification is separate. */
public class Wt3t1SharedStateTest {
    private static Wt3t1Model model(double fraction) {
        var model = new Wt3t1Model(new Wt3t1Data(12, 4, 0, .01, 20, fraction, 1.8, 1.5));
        model.initialize(.8, 1, 60);
        return model;
    }

    @Test void sharedHeunReproducesOrdinaryEquationsAcrossElectricalAndPitchEvents() {
        for (double fraction : new double[]{0, .7}) {
            var ordinary = model(fraction);
            var shared = model(fraction);
            double[] left = new double[4], trial = new double[4], a = new double[4], z = new double[4];
            double h = .0005;
            for (int n = 0; n < 2000; n++) {
                double power = n >= 100 && n < 200 ? .2 : .8;
                double pitch = n >= 500 && n < 1000 ? .2 : 0;
                ordinary.setPitch(pitch);
                shared.setPitch(pitch);
                shared.readStaggeredState(left, 0);
                shared.evaluateStaggered(power, a, 0);
                ordinary.step(h, power, 0);
                for (int i = 0; i < 4; i++) trial[i] = left[i] + h * a[i];
                shared.applyStaggeredState(trial, 0, h);
                shared.evaluateStaggered(power, z, 0);
                for (int i = 0; i < 4; i++) trial[i] = left[i] + .5 * h * (a[i] + z[i]);
                shared.applyStaggeredState(trial, 0, h);
                ordinary.step(h, power, 1);
                assertEquals(ordinary.getNamedStates(), shared.getNamedStates());
                assertEquals(ordinary.getAerodynamicPower(), shared.getAerodynamicPower());
            }
        }
    }

    @Test void staggeredSingleMassConvergesToIndependentEnergySolution() {
        double coarse = energyError(.001);
        double fine = energyError(.0005);
        assertTrue(coarse < 2e-8, "coarse energy error " + coarse);
        assertTrue(fine < coarse * .4, "refinement: " + coarse + " -> " + fine);
    }

    private static double energyError(double h) {
        var model = model(0);
        double[] x = new double[4], rate = new double[4], previous = new double[4];
        for (int n = 0; n < Math.round(1 / h); n++) {
            model.readStaggeredState(x, 0);
            model.evaluateStaggered(.6, rate, 0);
            for (int i = 0; i < 4; i++) x[i] += h * (n == 0 ? rate[i] : 1.5 * rate[i] - .5 * previous[i]);
            model.applyStaggeredState(x, 0, h);
            System.arraycopy(rate, 0, previous, 0, 4);
        }
        return Math.abs(model.getGeneratorSpeed() - Math.sqrt(1 + (.8 - .6) / 4));
    }

    @Test void repeatedEvaluationRollbackAndInvalidProjectionPreserveState() {
        var model = model(.7);
        double[] initial = new double[6], rates = new double[6], again = new double[6];
        model.readStaggeredState(initial, 1);
        var named = model.getNamedStates();
        model.evaluateStaggered(.2, rates, 1);
        model.evaluateStaggered(.2, again, 1);
        assertArrayEquals(rates, again);
        assertEquals(named, model.getNamedStates());
        double[] invalid = initial.clone();
        invalid[3] = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> model.applyStaggeredState(invalid, 1, .001));
        assertEquals(named, model.getNamedStates());
        var trial = initial.clone();
        trial[2] += .001;
        model.applyStaggeredState(trial, 1, .001);
        model.applyStaggeredState(initial, 1, 0);
        assertEquals(named, model.getNamedStates());
        assertThrows(IllegalArgumentException.class, () -> model.evaluateStaggered(Double.NaN, rates, 1));
    }

    @Test void rejectsHiddenPitchIntegrationAndMixedOrdinaryOwnership() {
        var model = model(.7);
        double[] values = new double[4];
        model.step(.001, .8, 0);
        assertThrows(IllegalStateException.class, () -> model.readStaggeredState(values, 0));
        model.step(.001, .8, 1);
        model.readStaggeredState(values, 0);
        model.setPitchController(new Wt3p1Model(new Wt3p1Data(.1, 1, 1, 1, 1, 0, 30, 10, 1)), null);
        assertThrows(UnsupportedOperationException.class, () -> model.evaluateStaggered(.8, values, 0));
        var fresh = new Wt3t1Model(model.getData());
        assertThrows(IllegalStateException.class, () -> fresh.applyStaggeredState(values, 0, .001));
    }
}
