package org.interpss.dstab.control.pss.ieee.y2016.pss7c;

import java.lang.reflect.Field;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateStabilizer;
import com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock;
import com.interpss.dstab.mach.Machine;

/** IEEE Std 421.5-2016 dual-input ramp-tracking canonical-form PSS7C. */
@AnController(input="mach.speed", output="this.outputSignal", refPoint="0.0", display={})
public final class Ieee2016PSS7CStabilizer extends AnnotateStabilizer
        implements IntegrationStepAware {
    private static final double EPS = 1.0e-12;
    private static final int W11 = 0, W12 = 1, L1 = 2, W21 = 3, W22 = 4, L2 = 5;
    private static final int C1 = 6, C2 = 7, C3 = 8, C4 = 9, PGEN = 10, COMP = 11;

    private final Ieee2016PSS7CStabilizerData sourceData;
    private Ieee2016PSS7CStabilizerData effectiveData;
    private BaseDStabBus<?, ?> input1Bus;
    private BaseDStabBus<?, ?> input2Bus;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private final double[] state = new double[12];
    private final double[] trial = new double[12];
    private final double[] oldDerivative = new double[12];
    private double[] active = state;
    private FilterNthOrderBlock rampFilter;
    private double previousVoltage1;
    private double previousVoltage2;
    private double previousCompensatedAngle;
    private boolean pssActive;
    private double input1Signal;
    private double input2Signal;
    public double outputSignal;

    public Ieee2016PSS7CStabilizer(String id,
            Ieee2016PSS7CStabilizerData data, Machine machine) {
        super(id, "PSS7C", "IEEE-2016");
        sourceData = data;
        setMachine(machine);
    }

    public Ieee2016PSS7CStabilizerData getData() { return sourceData; }
    public Ieee2016PSS7CStabilizerData getEffectiveData() { return effectiveData; }
    public double getInput1Signal() { return input1Signal; }
    public double getInput2Signal() { return input2Signal; }
    public double getFilteredPgen() { return state[PGEN]; }
    public boolean isPssActive() { return pssActive; }

    public void setInputSignalBuses(BaseDStabBus<?, ?> first, BaseDStabBus<?, ?> second) {
        input1Bus = first;
        input2Bus = second;
    }

    @Override public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        if (!Double.isFinite(timeStepSec) || timeStepSec < 0.0
                || !Double.isFinite(multiplier) || multiplier < 0.0) {
            throw new IllegalArgumentException(
                    "PSS7C integration-step settings must be finite and non-negative");
        }
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        effectiveData = correctedData(sourceData);
        if (input1Bus == null) input1Bus = bus;
        if (input2Bus == null) input2Bus = bus;
        double pgen = machine.getPe();
        previousVoltage1 = input1Bus.getVoltageMag();
        previousVoltage2 = input2Bus.getVoltageMag();
        previousCompensatedAngle = effectiveData.ics1() == 7
                ? compensatedVoltageAngle(machine, effectiveData.xcomp()) : 0.0;
        input1Signal = limitedInput(effectiveData.ics1(), input1Bus, machine,
                effectiveData.vsi1max(), effectiveData.vsi1min(), 0.0, pgen);
        input2Signal = limitedInput(effectiveData.ics2(), input2Bus, machine,
                effectiveData.vsi2max(), effectiveData.vsi2min(), 0.0, pgen);
        state[W11] = input1Signal;
        state[W12] = 0.0;
        state[L1] = 0.0;
        state[W21] = input2Signal;
        state[W22] = 0.0;
        state[L2] = 0.0;
        state[C1] = state[C2] = state[C3] = state[C4] = 0.0;
        state[PGEN] = pgen;
        state[COMP] = 0.0;
        System.arraycopy(state, 0, trial, 0, state.length);
        rampFilter = new FilterNthOrderBlock(effectiveData.t8(), effectiveData.t9(),
                effectiveData.m(), effectiveData.n());
        if (!rampFilter.initStateY0(0.0)) return false;
        active = state;
        pssActive = pgen >= effectiveData.pssActivation();
        outputSignal = 0.0;
        return true;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        if (dt <= 0.0) return true;
        int stage = method == DynamicSimuMethod.MODIFIED_EULER ? flag : 2;
        double pgen = machine.getPe();
        if (effectiveData.tpgfilt() <= EPS) {
            state[PGEN] = trial[PGEN] = pgen;
        }
        double rawComp = effectiveData.ics1() == 7
                ? rawCompensatedFrequency(machine, dt) : 0.0;
        if (stage == 0) {
            updateInputSignals(machine, dt, pgen, compensatedOutput(state, rawComp));
            double rampInput = derivatives(state, oldDerivative,
                    input1Signal, input2Signal, pgen, rawComp);
            rampFilter.eulerStep1(rampInput, dt);
            for (int i = 0; i < state.length; i++) trial[i] = state[i] + oldDerivative[i] * dt;
            active = trial;
            updateInputSignals(machine, dt, pgen, compensatedOutput(trial, rawComp));
        } else if (stage == 1) {
            updateInputSignals(machine, dt, pgen, compensatedOutput(trial, rawComp));
            double[] corrected = new double[state.length];
            double rampInput = derivatives(trial, corrected,
                    input1Signal, input2Signal, pgen, rawComp);
            rampFilter.eulerStep2(rampInput, dt);
            for (int i = 0; i < state.length; i++) {
                state[i] += 0.5 * (oldDerivative[i] + corrected[i]) * dt;
            }
            active = state;
            finishStep(machine, dt, rawComp);
        } else {
            updateInputSignals(machine, dt, pgen, compensatedOutput(state, rawComp));
            double[] derivative = new double[state.length];
            double rampInput = derivatives(state, derivative,
                    input1Signal, input2Signal, pgen, rawComp);
            rampFilter.eulerStep1(rampInput, dt);
            rampFilter.eulerStep2(rampInput, dt);
            for (int i = 0; i < state.length; i++) state[i] += derivative[i] * dt;
            active = state;
            finishStep(machine, dt, rawComp);
        }
        outputSignal = calculateOutput(active);
        return true;
    }

    @Override public double getOutput(Machine machine) {
        return pssActive ? outputSignal : 0.0;
    }

    private void finishStep(Machine machine, double dt, double rawComp) {
        updateInputSignals(machine, dt, machine.getPe(), compensatedOutput(state, rawComp));
        previousVoltage1 = input1Bus.getVoltageMag();
        previousVoltage2 = input2Bus.getVoltageMag();
        if (effectiveData.ics1() == 7) {
            previousCompensatedAngle = compensatedVoltageAngle(machine, effectiveData.xcomp());
        }
        updateActivation(state[PGEN]);
    }

    /** Returns the input to the published ramp-tracking block. */
    private double derivatives(double[] x, double[] dx,
            double vsi1, double vsi2, double pgen, double rawComp) {
        Ieee2016PSS7CStabilizerData d = effectiveData;
        dx[W11] = lagDerivative(vsi1, x[W11], d.tw1());
        double w11 = washoutOutput(vsi1, x[W11], d.tw1());
        dx[W12] = lagDerivative(w11, x[W12], d.tw2());
        double w12 = washoutOutput(w11, x[W12], d.tw2());
        dx[L1] = lagDerivative(w12, x[L1], d.t6());
        double path1 = lagOutput(w12, x[L1], d.t6());

        dx[W21] = lagDerivative(vsi2, x[W21], d.tw3());
        double w21 = washoutOutput(vsi2, x[W21], d.tw3());
        dx[W22] = lagDerivative(w21, x[W22], d.tw4());
        double w22 = washoutOutput(w21, x[W22], d.tw4());
        dx[L2] = lagDerivative(d.ks2() * w22, x[L2], d.t7());
        double path2 = lagOutput(d.ks2() * w22, x[L2], d.t7());

        double canonicalInput = d.ks1() * (rampFilter.getY() - path2);
        double error = canonicalInput - x[C1] - x[C2] - x[C3] - x[C4];
        dx[C1] = integratorDerivative(error, d.ti1());
        dx[C2] = integratorDerivative(x[C1], d.ti2());
        dx[C3] = integratorDerivative(d.ki3() * x[C2], d.ti3());
        dx[C4] = integratorDerivative(d.ki4() * x[C3], d.ti4());
        dx[PGEN] = lagDerivative(pgen, x[PGEN], d.tpgfilt());
        dx[COMP] = d.tcomp() > EPS ? (rawComp - x[COMP]) / d.tcomp() : 0.0;
        return path1 + d.ks3() * path2;
    }

    private double calculateOutput(double[] x) {
        Ieee2016PSS7CStabilizerData d = effectiveData;
        double path2 = path2Output(x);
        double input = d.ks1() * (rampFilter.getY() - path2);
        double error = input - x[C1] - x[C2] - x[C3] - x[C4];
        double y = d.k0() * error + d.k1() * x[C1] + d.k2() * x[C2]
                + d.k3() * x[C3] + d.k4() * x[C4];
        return clamp(y, d.vstmax(), d.vstmin());
    }

    private double path2Output(double[] x) {
        double w21 = washoutOutput(input2Signal, x[W21], effectiveData.tw3());
        double w22 = washoutOutput(w21, x[W22], effectiveData.tw4());
        return lagOutput(effectiveData.ks2() * w22, x[L2], effectiveData.t7());
    }

    private double rawCompensatedFrequency(Machine machine, double dt) {
        double angle = compensatedVoltageAngle(machine, effectiveData.xcomp());
        return wrapAngle(angle - previousCompensatedAngle)
                / (2.0 * Math.PI * machine.getDStabBus().getNetwork().getFrequency() * dt);
    }

    private double compensatedOutput(double[] x, double raw) {
        return effectiveData.tcomp() > EPS ? x[COMP] : raw;
    }

    private void updateInputSignals(Machine machine, double dt, double pgen, double comp) {
        input1Signal = effectiveData.ics1() == 6
                ? clamp((input1Bus.getVoltageMag() - previousVoltage1) / dt,
                        effectiveData.vsi1max(), effectiveData.vsi1min())
                : limitedInput(effectiveData.ics1(), input1Bus, machine,
                        effectiveData.vsi1max(), effectiveData.vsi1min(), comp, pgen);
        input2Signal = effectiveData.ics2() == 6
                ? clamp((input2Bus.getVoltageMag() - previousVoltage2) / dt,
                        effectiveData.vsi2max(), effectiveData.vsi2min())
                : limitedInput(effectiveData.ics2(), input2Bus, machine,
                        effectiveData.vsi2max(), effectiveData.vsi2min(), 0.0, pgen);
    }

    private void updateActivation(double pgen) {
        if (pssActive) {
            if (pgen <= effectiveData.pssDeactivation()) pssActive = false;
        } else if (pgen >= effectiveData.pssActivation()) {
            pssActive = true;
        }
    }

    private Ieee2016PSS7CStabilizerData correctedData(Ieee2016PSS7CStabilizerData d) {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        double[] l1 = orderedLimits(d.vsi1max(), d.vsi1min());
        double[] l2 = orderedLimits(d.vsi2max(), d.vsi2min());
        double[] lo = signedLimits(d.vstmax(), d.vstmin());
        return new Ieee2016PSS7CStabilizerData(d.ics1(), d.remoteBus1(), d.ics2(), d.remoteBus2(),
                d.m(), d.n(), positiveOrMinimum(d.ks1(), minimum), d.ks2(), d.ks3(),
                d.t6(), d.t7(), minimumIfNonpositive(d.tw1(), minimum),
                minimumIfNonpositive(d.tw2(), minimum), minimumIfNonpositive(d.tw3(), minimum),
                minimumIfNonpositive(d.tw4(), minimum), d.t8(), halfStepBypass(d.t9(), minimum),
                positiveOrMinimum(d.k0(), minimum), d.k1(), d.k2(), d.k3(), d.k4(),
                d.ki3(), d.ki4(), minimumIfNonpositive(d.ti1(), minimum),
                minimumIfNonpositive(d.ti2(), minimum), minimumIfNonpositive(d.ti3(), minimum),
                minimumIfNonpositive(d.ti4(), minimum), l1[0], l1[1], l2[0], l2[1],
                lo[0], lo[1], d.pssActivation(), d.pssDeactivation(), d.tpgfilt(),
                d.xcomp(), d.tcomp());
    }

    private static double limitedInput(int code, BaseDStabBus<?, ?> bus, Machine machine,
            double max, double min, double compensatedFrequency, double pgen) {
        double value = switch (code) {
            case 1 -> machine.getSpeed() - 1.0;
            case 2 -> bus.getFreq() - 1.0;
            case 3 -> pgen;
            case 4 -> machine.getPm() - pgen;
            case 5 -> bus.getVoltageMag();
            case 6 -> 0.0;
            case 7 -> compensatedFrequency;
            default -> throw new IllegalArgumentException("Unsupported PSS7C input code: " + code);
        };
        return clamp(value, max, min);
    }

    private static double compensatedVoltageAngle(Machine machine, double xcomp) {
        Complex current = machine.getIxy().divide(machine.getIMultiFactor());
        return machine.getDStabBus().getVoltage()
                .add(current.multiply(new Complex(0.0, xcomp))).getArgument();
    }

    private static double lagDerivative(double input, double stateValue, double t) {
        return t > EPS ? (input - stateValue) / t : 0.0;
    }
    private static double lagOutput(double input, double stateValue, double t) {
        return t > EPS ? stateValue : input;
    }
    private static double washoutOutput(double input, double stateValue, double t) {
        return t > EPS ? input - stateValue : 0.0;
    }
    private static double integratorDerivative(double input, double t) {
        return t > EPS ? input / t : 0.0;
    }
    private static double minimumIfNonpositive(double value, double minimum) {
        return value <= 0.0 ? minimum : value;
    }
    private static double positiveOrMinimum(double value, double minimum) {
        return value <= 0.0 ? minimum : value;
    }
    private static double halfStepBypass(double value, double minimum) {
        if (value > 0.0 && value < 0.5 * minimum) return 0.0;
        if (value > 0.5 * minimum && value < minimum) return minimum;
        return value;
    }
    private static double[] orderedLimits(double max, double min) {
        return max >= min ? new double[] {max, min} : new double[] {min, max};
    }
    private static double[] signedLimits(double max, double min) {
        double[] ordered = orderedLimits(max, min);
        return new double[] {Math.abs(ordered[0]), -Math.abs(ordered[1])};
    }
    private static double clamp(double value, double max, double min) {
        return Math.max(min, Math.min(max, value));
    }
    private static double wrapAngle(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }

    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }
}
