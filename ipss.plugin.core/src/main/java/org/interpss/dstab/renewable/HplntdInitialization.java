package org.interpss.dstab.renewable;

import static org.interpss.dstab.renewable.HplntdData.Parameter.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.math3.complex.Complex;

/** Algebraic HPLNTDU equilibrium foundation, not a time-domain plant device.
 * Inputs have already resolved branch fallback/direction and plant-base conversion. */
public final class HplntdInitialization {
    private HplntdInitialization() {}
    public static final java.util.List<String> STATE_NAMES = java.util.List.of(
            "compensatedVoltage", "reactiveMeasurement", "reactiveIntegral",
            "reactiveLeadLag", "activeMeasurement", "activeIntegral", "activeCommand",
            "regulatedVoltage", "reactiveDroop", "frequencyDeviation",
            "plantReference", "reactiveReference", "reactiveCommand");

    public record Measurements(Complex regulatedVoltage, Complex branchCurrent,
                               double branchActive, double branchReactive,
                               double activeCommand, double reactiveCommand) {
        public Measurements {
            if (regulatedVoltage == null || branchCurrent == null
                    || !finite(regulatedVoltage) || !finite(branchCurrent)
                    || regulatedVoltage.abs() <= 0
                    || !Double.isFinite(branchActive) || !Double.isFinite(branchReactive)
                    || !Double.isFinite(activeCommand) || !Double.isFinite(reactiveCommand))
                throw new IllegalArgumentException("Finite HPLNTDU equilibrium measurements and positive voltage required");
        }
        private static boolean finite(Complex c) {
            return Double.isFinite(c.getReal()) && Double.isFinite(c.getImaginary());
        }
    }

    public record Result(int effectiveReferenceFlag, double voltageReference,
                         double reactiveReference, double plantReference,
                         double powerFactorReference, double[] states) {
        public Result { states = states.clone(); }
        @Override public double[] states() { return states.clone(); }
        public Map<String, Double> namedStates() {
            Map<String, Double> result = new LinkedHashMap<>();
            for (int i = 0; i < states.length; i++) result.put(STATE_NAMES.get(i), states[i]);
            return Collections.unmodifiableMap(result);
        }
    }

    public static Result initialize(HplntdData d, Measurements m) {
        // Shunt status/timers belong to the separate physical boundary and do
        // not alter these continuous-state equilibrium equations.
        // Proportional-only and alternate output paths remain unestablished.
        if (d.value(KI) <= 0 || d.value(KIG) <= 0
                || d.frequencyFlag() != 1 || d.reactiveFlag() != 1)
            throw new IllegalArgumentException("HPLNTDU foundation requires positive integral gains and enabled control paths");
        int mode = d.effectiveReferenceFlag(m.branchActive());
        if (mode == 2 && d.feedbackFlag() == 0)
            throw new IllegalArgumentException("HPLNTDU foundation has not established PF initialization with disabled electrical feedback");
        double[] state = new double[HplntdData.STATE_COUNT];
        state[7] = m.regulatedVoltage().abs();
        double vref = 0, qref = 0, pfref = 0;
        if (mode == 1) {
            if (d.compensationFlag() == 1) {
                state[0] = m.regulatedVoltage().subtract(
                        new Complex(d.value(RC), d.value(XC)).multiply(m.branchCurrent())).abs();
                vref = state[0];
            } else {
                state[8] = d.value(KC) * m.branchReactive();
                vref = state[7] + state[8];
            }
        } else {
            state[1] = m.branchReactive();
            if (mode == 0) qref = state[11] = m.branchReactive();
            else pfref = Math.copySign(Math.abs(m.branchActive()) /
                    Math.hypot(m.branchActive(), m.branchReactive()), m.branchReactive());
        }
        state[2] = state[3] = state[12] = m.reactiveCommand();
        state[4] = d.feedbackFlag() == 1 ? m.branchActive() : 0;
        double pref = state[4];
        state[10] = pref;
        state[5] = m.activeCommand() - (d.feedforwardFlag() == 1 ? pref : 0);
        state[6] = m.activeCommand();
        requireWithin(m.reactiveCommand(), d.value(QMIN), d.value(QMAX), "reactive output");
        requireWithin(m.activeCommand(), d.value(PMIN), d.value(PMAX), "active output");
        requireWithin(state[5], d.activePiMinimum(), d.activePiMaximum(), "active PI");
        if (mode == 1) requireWithin(vref, d.value(VREFMIN), d.value(VREFMAX), "voltage reference");
        if (mode == 0) requireWithin(qref, d.value(QREFMIN), d.value(QREFMAX), "reactive reference");
        if (mode == 2 && (pfref >= 0 ? pfref < d.value(PFMAX) : pfref > d.value(PFMIN)))
            throw new IllegalArgumentException("HPLNTDU equilibrium lies outside the configured minimum power-factor magnitude");
        return new Result(mode, vref, qref, pref, pfref, state);
    }
    private static void requireWithin(double value, double minimum, double maximum, String name) {
        if (value < minimum || value > maximum)
            throw new IllegalArgumentException("HPLNTDU equilibrium outside " + name + " bounds");
    }
}
