package org.interpss.dstab.control.pss.ieee.y2016.pss6c;

import java.lang.reflect.Field;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateStabilizer;
import com.interpss.dstab.mach.Machine;

/** IEEE Std 421.5-2016 PSS6C dual-input canonical-form stabilizer. */
@AnController(input="mach.speed", output="this.outputSignal", refPoint="0.0", display={})
public final class Ieee2016PSS6CStabilizer extends AnnotateStabilizer
        implements IntegrationStepAware {
    private static final double EPS = 1.0e-12;
    private static final int X1 = 0, X1K = 1, X2K = 2, X2W = 3, UW = 4;
    private static final int C1 = 5, C2 = 6, C3 = 7, C4 = 8, PGEN = 9, COMP = 10;

    private final Ieee2016PSS6CStabilizerData sourceData;
    private Ieee2016PSS6CStabilizerData effectiveData;
    private BaseDStabBus<?, ?> input1Bus;
    private BaseDStabBus<?, ?> input2Bus;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private final double[] state = new double[11];
    private final double[] trial = new double[11];
    private final double[] oldDerivative = new double[11];
    private double[] active = state;
    private double previousVoltage1;
    private double previousVoltage2;
    private double previousCompensatedAngle;
    private boolean pssActive;
    private double input1Signal;
    private double input2Signal;
    public double outputSignal;

    public Ieee2016PSS6CStabilizer(String id,
            Ieee2016PSS6CStabilizerData data, Machine machine) {
        super(id, "PSS6C", "IEEE-2016");
        sourceData = data;
        setMachine(machine);
    }

    public Ieee2016PSS6CStabilizerData getData() { return sourceData; }
    public Ieee2016PSS6CStabilizerData getEffectiveData() { return effectiveData; }
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
                    "PSS6C integration-step settings must be finite and non-negative");
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
        state[X1] = input1Signal;
        state[X1K] = effectiveData.ks1() * input1Signal;
        state[X2K] = effectiveData.ks2() * input2Signal;
        state[X2W] = state[X2K];
        state[UW] = state[X1] - state[X1K];
        state[C1] = state[C2] = state[C3] = state[C4] = 0.0;
        state[PGEN] = pgen;
        state[COMP] = 0.0;
        System.arraycopy(state, 0, trial, 0, state.length);
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
        double rawComp = effectiveData.ics1() == 7
                ? rawCompensatedFrequency(machine, dt) : 0.0;

        if (stage == 0) {
            updateInputSignals(machine, dt, pgen, compensatedOutput(state, rawComp));
            derivatives(state, oldDerivative, input1Signal, input2Signal, pgen, rawComp);
            for (int i = 0; i < state.length; i++) trial[i] = state[i] + oldDerivative[i] * dt;
            active = trial;
            updateInputSignals(machine, dt, pgen, compensatedOutput(trial, rawComp));
        } else if (stage == 1) {
            updateInputSignals(machine, dt, pgen, compensatedOutput(trial, rawComp));
            double[] corrected = new double[state.length];
            derivatives(trial, corrected, input1Signal, input2Signal, pgen, rawComp);
            for (int i = 0; i < state.length; i++) {
                state[i] += 0.5 * (oldDerivative[i] + corrected[i]) * dt;
            }
            active = state;
            updateInputSignals(machine, dt, pgen, compensatedOutput(state, rawComp));
            previousVoltage1 = input1Bus.getVoltageMag();
            previousVoltage2 = input2Bus.getVoltageMag();
            if (effectiveData.ics1() == 7) {
                previousCompensatedAngle = compensatedVoltageAngle(machine, effectiveData.xcomp());
            }
            updateActivation(state[PGEN]);
        } else {
            updateInputSignals(machine, dt, pgen, compensatedOutput(state, rawComp));
            double[] derivative = new double[state.length];
            derivatives(state, derivative, input1Signal, input2Signal, pgen, rawComp);
            for (int i = 0; i < state.length; i++) state[i] += derivative[i] * dt;
            active = state;
            updateInputSignals(machine, dt, pgen, compensatedOutput(state, rawComp));
            previousVoltage1 = input1Bus.getVoltageMag();
            previousVoltage2 = input2Bus.getVoltageMag();
            updateActivation(state[PGEN]);
        }
        outputSignal = calculateOutput(active, input1Signal, input2Signal);
        return true;
    }

    @Override public double getOutput(Machine machine) {
        return pssActive ? outputSignal : 0.0;
    }

    private void derivatives(double[] x, double[] dx,
            double vsi1, double vsi2, double pgen, double rawComp) {
        Ieee2016PSS6CStabilizerData d = effectiveData;
        double x1 = lagOutput(vsi1, x[X1], d.t1());
        dx[X1] = lagDerivative(vsi1, x[X1], d.t1());
        double x1kTarget = d.ks1() * x1;
        double x1k = lagOutput(x1kTarget, x[X1K], d.t3());
        dx[X1K] = lagDerivative(x1kTarget, x[X1K], d.t3());
        double x2kTarget = d.ks2() * vsi2;
        double x2k = lagOutput(x2kTarget, x[X2K], d.t2());
        dx[X2K] = lagDerivative(x2kTarget, x[X2K], d.t2());
        dx[X2W] = lagDerivative(x2k, x[X2W], d.t4());
        double x2Wash = washoutOutput(x2k, x[X2W], d.macc(), d.t4());
        double combined = x1 - x1k - x2Wash;
        dx[UW] = lagDerivative(combined, x[UW], d.td());
        double u = washoutOutput(combined, x[UW], d.td(), d.td());
        double error = u - x[C1] - x[C2] - x[C3] - x[C4];
        dx[C1] = integratorDerivative(error, d.ti1());
        dx[C2] = integratorDerivative(x[C1], d.ti2());
        dx[C3] = integratorDerivative(d.ki3() * x[C2], d.ti3());
        dx[C4] = integratorDerivative(d.ki4() * x[C3], d.ti4());
        dx[PGEN] = lagDerivative(pgen, x[PGEN], d.tpgfilt());
        dx[COMP] = d.tcomp() > EPS ? (rawComp - x[COMP]) / d.tcomp() : 0.0;
    }

    private double calculateOutput(double[] x, double vsi1, double vsi2) {
        Ieee2016PSS6CStabilizerData d = effectiveData;
        double x1 = lagOutput(vsi1, x[X1], d.t1());
        double x1k = lagOutput(d.ks1() * x1, x[X1K], d.t3());
        double x2k = lagOutput(d.ks2() * vsi2, x[X2K], d.t2());
        double combined = x1 - x1k - washoutOutput(x2k, x[X2W], d.macc(), d.t4());
        double u = washoutOutput(combined, x[UW], d.td(), d.td());
        double error = u - x[C1] - x[C2] - x[C3] - x[C4];
        double y = d.k0() * error + d.k1() * x[C1] + d.k2() * x[C2]
                + d.k3() * x[C3] + d.k4() * x[C4];
        return clamp(d.ks() * y, d.vstmax(), d.vstmin());
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

    private Ieee2016PSS6CStabilizerData correctedData(Ieee2016PSS6CStabilizerData d) {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        double[] l1 = signedLimits(d.vsi1max(), d.vsi1min());
        double[] l2 = signedLimits(d.vsi2max(), d.vsi2min());
        double[] lo = signedLimits(d.vstmax(), d.vstmin());
        return new Ieee2016PSS6CStabilizerData(d.ics1(), d.remoteBus1(), d.ics2(), d.remoteBus2(),
                d.ks1(), d.t1(), d.t3(), d.ks2(), d.macc(), d.t2(), d.t4(), d.td(),
                positiveOrMinimum(d.k0(), minimum), d.k1(), d.k2(), d.k3(), d.k4(),
                d.ki3(), d.ki4(), positiveOrMinimum(d.ks(), minimum),
                d.ti1(), d.ti2(), d.ti3(), d.ti4(),
                l1[0], l1[1], l2[0], l2[1], lo[0], lo[1],
                d.pssActivation(), d.pssDeactivation(), d.tpgfilt(), d.xcomp(), d.tcomp());
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
            default -> throw new IllegalArgumentException("Unsupported PSS6C input code: " + code);
        };
        return clamp(value, max, min);
    }

    private static Complex compensatedVoltage(Machine machine, double xcomp) {
        Complex terminalCurrentMachineBase = machine.getIxy().divide(machine.getIMultiFactor());
        return machine.getDStabBus().getVoltage()
                .add(terminalCurrentMachineBase.multiply(new Complex(0.0, xcomp)));
    }

    private static double compensatedVoltageAngle(Machine machine, double xcomp) {
        return compensatedVoltage(machine, xcomp).getArgument();
    }

    private static double lagDerivative(double input, double stateValue, double timeConstant) {
        return timeConstant > EPS ? (input - stateValue) / timeConstant : 0.0;
    }

    private static double lagOutput(double input, double stateValue, double timeConstant) {
        return timeConstant > EPS ? stateValue : input;
    }

    private static double washoutOutput(double input, double stateValue,
            double numeratorTime, double denominatorTime) {
        return denominatorTime > EPS ? numeratorTime * (input - stateValue) / denominatorTime : 0.0;
    }

    private static double integratorDerivative(double input, double timeConstant) {
        return timeConstant > EPS ? input / timeConstant : 0.0;
    }

    private static double positiveOrMinimum(double value, double minimum) {
        return value <= 0.0 ? minimum : value;
    }

    private static double[] signedLimits(double max, double min) {
        if (max < min) { double swap = max; max = min; min = swap; }
        return new double[] {Math.abs(max), -Math.abs(min)};
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
