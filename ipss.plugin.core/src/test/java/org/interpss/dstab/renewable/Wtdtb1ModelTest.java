package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Published-diagram checks, deliberately not a claim of native trajectory parity. */
class Wtdtb1ModelTest {
    private static final double EPS = 1e-12;
    private static final double POWER = .24;

    private static Wtdtb1Model twoMass() {
        return new Wtdtb1Model(new Wtdtb1Data(4.5, 1.5, .6, 2, .4, .1), 60);
    }

    @Test void shaftFrequencyIsHertzAndNativeCoordinateIsAngle() {
        var model = twoMass();
        model.initialize(POWER);
        // K = 2 Ht Hg (2 pi Freq1)^2 / ((Ht + Hg) omegaBase).
        double k = 2 * 2.7 * 1.8 * Math.pow(4 * Math.PI, 2) / (4.5 * 120 * Math.PI);
        assertEquals(k, model.shaftStiffness(), EPS);
        assertEquals(POWER / k, model.getNativeStates()[0], EPS);
        assertNotEquals(POWER, model.getNativeStates()[0], 1e-4);
    }

    @Test void twoMassEquilibriumPreservesEveryPublishedState() {
        var model = twoMass();
        model.initialize(POWER);
        double[] initial = model.getNativeStates();
        for (int i = 0; i < 1000; i++) model.step(.001, 99, POWER);
        assertArrayEquals(initial, model.getNativeStates(), EPS);
        assertEquals(1, model.getGeneratorSpeed(), EPS);
        assertEquals(1, model.getTurbineSpeed(), EPS);
    }

    @Test void predictorAndCorrectorUseTorqueBalanceAtTheirOwnEndpoints() {
        var model = twoMass();
        model.initialize(POWER);
        double[] initial = model.getNativeStates();
        double dt = .001;
        double pe0 = .10, pe1 = .12;
        double generatorRate = (POWER - pe0) / 3.6;
        double filterRate = (pe0 - POWER) / .1;
        model.step(dt, 7, pe0, 0);
        double[] predicted = model.getNativeStates();
        assertArrayEquals(new double[] {initial[0], 0, dt * generatorRate, 0,
                POWER + dt * filterRate}, predicted, EPS);
        double wg = 1 + predicted[2];
        double damping = -.4 * predicted[2];
        double wtRate1 = (predicted[4] - POWER - damping) / 5.4;
        double wgRate1 = (POWER + damping - pe1 / wg - 1.5 * predicted[2]) / 3.6;
        model.step(dt, -9, pe1, 1);
        assertArrayEquals(new double[] {
                initial[0] - .5 * dt * 120 * Math.PI * predicted[2],
                .5 * dt * wtRate1,
                .5 * dt * (generatorRate + wgRate1),
                .5 * dt * 120 * Math.PI * predicted[2],
                POWER + .5 * dt * (filterRate + (pe1 - predicted[4]) / .1)
        }, model.getNativeStates(), EPS);
    }

    @Test void aerodynamicFallbackUsesExternalPowerAndDeactivatesFifthState() {
        var model = twoMass();
        model.setAerodynamicPresent(true);
        model.initialize(POWER);
        assertEquals(0, model.getFilteredElectricalPower(), 0);
        model.step(.001, .30, POWER, 0);
        assertEquals(.001 * (.30 - POWER) / 5.4, model.getNativeStates()[1], EPS);
        assertEquals(0, model.getNativeStates()[2], EPS);
        assertEquals(0, model.getFilteredElectricalPower(), 0);
        model.step(.001, .30, POWER, 1);
        assertEquals(0, model.getFilteredElectricalPower(), 0);
    }

    @Test void oneMassLeavesNativeInactiveCoordinatesAtZero() {
        var model = new Wtdtb1Model(new Wtdtb1Data(4.5, 1.5, 0, 0, .4, .1), 60);
        model.initialize(POWER);
        model.step(.001, 123, .10, 0);
        double[] state = model.getNativeStates();
        assertEquals(0, state[0], 0);
        assertEquals(0, state[1], 0);
        assertEquals(.001 * (.24 - .10) / 9, state[2], EPS);
        assertEquals(1, model.getTurbineSpeed(), 0);
        assertEquals(0, model.shaftStiffness(), 0);
    }

    @Test void zeroTimeConstantRefreshesAlgebraicStateWithoutAdvancingMechanics() {
        var model = new Wtdtb1Model(new Wtdtb1Data(4.5, 1.5, .6, 2, .4, 0), 60);
        model.initialize(POWER);
        double[] before = model.getNativeStates();
        model.step(0, 99, .10, 0);
        double[] refreshed = model.getNativeStates();
        for (int i = 0; i < 4; i++) assertEquals(before[i], refreshed[i], 0);
        assertEquals(.10, refreshed[4], 0);
        model.stepTwoStep(0, 99, .12);
        assertEquals(.12, model.getFilteredElectricalPower(), 0);
        model.step(0, 99, .14, 1);
        assertEquals(.14, model.getFilteredElectricalPower(), 0);
    }

    @Test void observationsAreImmutableSnapshotsAndDoNotChangeState() {
        var model = twoMass();
        model.initialize(POWER);
        Map<String, Double> observed = model.getNamedStates();
        assertEquals(5, observed.size());
        assertThrows(UnsupportedOperationException.class, () -> observed.put("extra", 1.0));
        double[] detached = model.getNativeStates();
        detached[0] = 99;
        assertNotEquals(99, model.getNativeStates()[0]);
        model.step(.001, POWER, .10);
        assertEquals(0, observed.get("Generator Speed Deviation"), 0);
        double[] before = model.getNativeStates();
        for (int i = 0; i < 100; i++) model.getNamedStates();
        assertArrayEquals(before, model.getNativeStates(), 0);
    }

    @Test void initializationDiscardsPredictorAndTwoStepHistory() {
        var model = twoMass();
        model.initialize(POWER);
        model.stepTwoStep(.001, POWER, .10);
        model.step(.001, POWER, .10, 0);
        model.initialize(POWER);
        assertThrows(IllegalStateException.class, () -> model.step(.001, POWER, .10, 1));
        var fresh = twoMass();
        fresh.initialize(POWER);
        model.stepTwoStep(.001, POWER, .10);
        fresh.stepTwoStep(.001, POWER, .10);
        assertArrayEquals(fresh.getNativeStates(), model.getNativeStates(), 0);
    }

    @Test void invalidParametersAndInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Wtdtb1Data(0, 0, .6, 2, 0, .1));
        assertThrows(IllegalArgumentException.class, () -> new Wtdtb1Data(4.5, 0, 1, 2, 0, .1));
        assertThrows(IllegalArgumentException.class, () -> new Wtdtb1Data(4.5, 0, .6, 0, 0, .1));
        assertThrows(IllegalArgumentException.class, () -> new Wtdtb1Data(4.5, 0, .6, 2, -1, .1));
        assertThrows(IllegalArgumentException.class, () -> new Wtdtb1Data(4.5, 0, .6, 2, 0, -.1));
        assertThrows(IllegalArgumentException.class, () -> new Wtdtb1Data(4.5, Double.NaN, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Wtdtb1Model(new Wtdtb1Data(4.5, 0, 0, 0, 0, 0), 0));
        var model = twoMass();
        assertThrows(IllegalArgumentException.class, () -> model.initialize(POWER, 0));
        model.initialize(POWER);
        assertThrows(IllegalArgumentException.class, () -> model.step(-.1, POWER, POWER));
        assertThrows(IllegalArgumentException.class, () -> model.step(.1, Double.NaN, POWER));
        assertThrows(IllegalArgumentException.class, () -> model.step(.1, POWER, POWER, 2));
        assertThrows(IllegalStateException.class, () -> model.step(.1, POWER, POWER, 1));
    }
}
