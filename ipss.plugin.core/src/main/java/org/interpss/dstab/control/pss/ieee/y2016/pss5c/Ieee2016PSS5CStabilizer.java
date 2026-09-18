package org.interpss.dstab.control.pss.ieee.y2016.pss5c;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import org.interpss.dstab.control.pss.ieee.y2016.pss5c.Ieee2016PSS5CStabilizerData.BandData;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateStabilizer;
import com.interpss.dstab.mach.Machine;

/** IEEE Std 421.5-2016 PSS5C simplified four-band stabilizer. */
@AnController(input="mach.speed", output="this.outputSignal", refPoint="0.0", display={})
public final class Ieee2016PSS5CStabilizer extends AnnotateStabilizer
        implements IntegrationStepAware {
    private static final double EPS = 1.0e-12;

    private final Ieee2016PSS5CStabilizerData sourceData;
    private Ieee2016PSS5CStabilizerData effectiveData;
    private SecondOrderBlock lowIntermediateTransducer;
    private ThirdOrderBlock highTransducer;
    private Band veryLowBand;
    private Band lowBand;
    private Band intermediateBand;
    private Band highBand;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double initialSpeed;
    private double lowIntermediateInput;
    private double highInput;
    private double veryLowOutput;
    private double lowOutput;
    private double intermediateOutput;
    private double highOutput;
    public double outputSignal;

    public Ieee2016PSS5CStabilizer(String id,
            Ieee2016PSS5CStabilizerData data, Machine machine) {
        super(id, "PSS5C", "IEEE-2016");
        sourceData = data;
        setMachine(machine);
    }

    public Ieee2016PSS5CStabilizerData getData() { return sourceData; }
    public Ieee2016PSS5CStabilizerData getEffectiveData() { return effectiveData; }
    public double getLowIntermediateInput() { return lowIntermediateInput; }
    public double getHighInput() { return highInput; }
    public double getVeryLowOutput() { return veryLowOutput; }
    public double getLowOutput() { return lowOutput; }
    public double getIntermediateOutput() { return intermediateOutput; }
    public double getHighOutput() { return highOutput; }

    @Override public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        if (!Double.isFinite(timeStepSec) || timeStepSec < 0.0
                || !Double.isFinite(multiplier) || multiplier < 0.0) {
            throw new IllegalArgumentException(
                    "PSS5C integration-step settings must be finite and non-negative");
        }
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        effectiveData = correctedData(sourceData);
        lowIntermediateTransducer = new SecondOrderBlock(
                1.0, 1.759e-3, 0.0, 1.7823e-2, 1.2739e-4);
        // 80*s^2/(s^3 + 82*s^2 + 161*s + 80), normalized at s^0.
        highTransducer = new ThirdOrderBlock(
                0.0, 0.0, 1.0, 0.0, 161.0 / 80.0, 82.0 / 80.0, 1.0 / 80.0);
        veryLowBand = new Band(effectiveData.veryLowBand(), effectiveData);
        lowBand = new Band(effectiveData.lowBand(), effectiveData);
        intermediateBand = new Band(effectiveData.intermediateBand(), effectiveData);
        highBand = new Band(effectiveData.highBand(), effectiveData);
        initialSpeed = machine.getSpeed();
        lowIntermediateInput = highInput = 0.0;
        veryLowOutput = lowOutput = intermediateOutput = highOutput = 0.0;
        outputSignal = 0.0;
        return true;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        if (dt <= 0.0) return true;
        int stage = method == DynamicSimuMethod.MODIFIED_EULER ? flag : 2;
        double speedDeviation = machine.getSpeed() - initialSpeed;
        lowIntermediateInput = advance(lowIntermediateTransducer, speedDeviation, dt, stage);
        highInput = advance(highTransducer, speedDeviation, dt, stage);
        veryLowOutput = veryLowBand.advance(lowIntermediateInput, dt, stage);
        lowOutput = lowBand.advance(lowIntermediateInput, dt, stage);
        intermediateOutput = intermediateBand.advance(lowIntermediateInput, dt, stage);
        highOutput = highBand.advance(highInput, dt, stage);
        outputSignal = clamp(veryLowOutput + lowOutput + intermediateOutput + highOutput,
                effectiveData.vstmax(), effectiveData.vstmin());
        return true;
    }

    @Override public double getOutput(Machine machine) { return outputSignal; }

    /** Canonical memories plus published transducer and PowerWorld-native band coordinates. */
    @Override
    public Map<String, Double> getNamedStates() {
        if (lowIntermediateTransducer == null || highTransducer == null) return Map.of();
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("lowIntermediatePosition", lowIntermediateTransducer.state(0));
        states.put("lowIntermediateRate", lowIntermediateTransducer.state(1));
        states.put("highPosition", highTransducer.state(0));
        states.put("highRate", highTransducer.state(1));
        states.put("highAcceleration", highTransducer.state(2));
        states.put("lowIntermediateOutput", lowIntermediateInput);
        states.put("highFrequencyOutput", highInput);
        veryLowBand.addNamedStates(states, "veryLow");
        lowBand.addNamedStates(states, "low");
        intermediateBand.addNamedStates(states, "intermediate");
        highBand.addNamedStates(states, "high");
        double frequencyBase = getMachine().getDStabBus().getNetwork().getFrequency();
        veryLowBand.addNativeStates(states, "k3FVL", "k2FVL",
                lowIntermediateInput, frequencyBase);
        lowBand.addNativeStates(states, "k3FL", "k2FL",
                lowIntermediateInput, frequencyBase);
        intermediateBand.addNativeStates(states, "k3FI", "k2FI",
                lowIntermediateInput, frequencyBase);
        highBand.addNativeStates(states, "k3FH", "k2FH", highInput, frequencyBase);
        return Map.copyOf(states);
    }

    private Ieee2016PSS5CStabilizerData correctedData(Ieee2016PSS5CStabilizerData data) {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        return new Ieee2016PSS5CStabilizerData(
                correctedBand(data.veryLowBand(), minimum, true),
                correctedBand(data.lowBand(), minimum, false),
                correctedBand(data.intermediateBand(), minimum, false),
                correctedBand(data.highBand(), minimum, false),
                positiveOrMinimum(data.k1(), minimum),
                positiveOrMinimum(data.k2(), minimum),
                positiveOrMinimum(data.k3(), minimum),
                normalizedHigh(data.vstmax(), data.vstmin()),
                normalizedLow(data.vstmax(), data.vstmin()));
    }

    private static BandData correctedBand(BandData band, double minimum,
            boolean correctGain) {
        return new BandData(
                correctGain ? positiveOrMinimum(band.gain(), minimum) : band.gain(),
                positiveOrMinimum(band.frequency(), minimum),
                normalizedHigh(band.max(), band.min()),
                normalizedLow(band.max(), band.min()));
    }

    private static double positiveOrMinimum(double value, double minimum) {
        return value <= 0.0 ? minimum : value;
    }

    private static double normalizedHigh(double first, double second) {
        return Math.abs(Math.max(first, second));
    }

    private static double normalizedLow(double first, double second) {
        return -Math.abs(Math.min(first, second));
    }

    private static double clamp(double value, double max, double min) {
        return Math.max(min, Math.min(max, value));
    }

    private static double advance(TransferBlock block, double input, double dt, int stage) {
        if (stage == 0) block.predict(input, dt);
        else if (stage == 1) block.correct(input, dt);
        else block.euler(input, dt);
        return block.output(input);
    }

    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }

    private final class Band {
        private final BandData data;
        private final FirstOrderBlock upper;
        private final FirstOrderBlock lower;

        private Band(BandData data, Ieee2016PSS5CStabilizerData common) {
            this.data = data;
            double f = data.frequency();
            upper = new FirstOrderBlock(1.0, inverse(common.k3() * f),
                    inverse(common.k2() * f));
            lower = new FirstOrderBlock(1.0, inverse(common.k2() * f),
                    inverse(common.k1() * f));
        }

        private double advance(double input, double dt, int stage) {
            double upperOutput = Ieee2016PSS5CStabilizer.advance(upper, input, dt, stage);
            double lowerOutput = Ieee2016PSS5CStabilizer.advance(lower, input, dt, stage);
            return clamp(data.gain() * (upperOutput - lowerOutput), data.max(), data.min());
        }

        private void addNamedStates(Map<String, Double> states, String prefix) {
            states.put(prefix + "UpperLag", upper.state(0));
            states.put(prefix + "LowerLag", lower.state(0));
        }

        private void addNativeStates(Map<String, Double> states, String upperName,
                String lowerName, double input, double frequencyBase) {
            states.put(upperName, frequencyBase * upper.output(input));
            states.put(lowerName, frequencyBase * lower.output(input));
        }

        private double inverse(double value) {
            return value > EPS ? 1.0 / value : 0.0;
        }
    }

    private interface TransferBlock {
        void predict(double input, double dt);
        void correct(double input, double dt);
        void euler(double input, double dt);
        double output(double input);
    }

    private abstract static class StateBlock implements TransferBlock {
        protected double[] state;
        private double[] trial;
        private double[] oldDerivative;
        protected double[] active;

        protected StateBlock(int order) {
            state = new double[order];
            trial = new double[order];
            oldDerivative = new double[order];
            active = state;
        }

        protected abstract void derivatives(double input, double[] x, double[] dx);

        protected double state(int index) {
            return active[index];
        }

        @Override public void predict(double input, double dt) {
            derivatives(input, state, oldDerivative);
            for (int i = 0; i < state.length; i++) trial[i] = state[i] + oldDerivative[i] * dt;
            active = trial;
        }

        @Override public void correct(double input, double dt) {
            double[] corrected = new double[state.length];
            derivatives(input, trial, corrected);
            for (int i = 0; i < state.length; i++) {
                state[i] += 0.5 * (oldDerivative[i] + corrected[i]) * dt;
            }
            active = state;
        }

        @Override public void euler(double input, double dt) {
            double[] derivative = new double[state.length];
            derivatives(input, state, derivative);
            for (int i = 0; i < state.length; i++) state[i] += derivative[i] * dt;
            active = state;
        }
    }

    private static final class FirstOrderBlock extends StateBlock {
        private final double b0;
        private final double b1;
        private final double a1;

        private FirstOrderBlock(double b0, double b1, double a1) {
            super(1);
            this.b0 = b0;
            this.b1 = b1;
            this.a1 = a1;
        }

        @Override protected void derivatives(double input, double[] x, double[] dx) {
            dx[0] = a1 > EPS ? (input - x[0]) / a1 : 0.0;
        }

        @Override public double output(double input) {
            if (a1 <= EPS) return b0 * input;
            return b0 * active[0] + b1 * (input - active[0]) / a1;
        }
    }

    private static final class SecondOrderBlock extends StateBlock {
        private final double b0, b1, b2, a1, a2;

        private SecondOrderBlock(double b0, double b1, double b2, double a1, double a2) {
            super(2);
            this.b0 = b0; this.b1 = b1; this.b2 = b2; this.a1 = a1; this.a2 = a2;
        }

        @Override protected void derivatives(double input, double[] x, double[] dx) {
            dx[0] = x[1];
            dx[1] = (input - x[0] - a1 * x[1]) / a2;
        }

        @Override public double output(double input) {
            double acceleration = (input - active[0] - a1 * active[1]) / a2;
            return b0 * active[0] + b1 * active[1] + b2 * acceleration;
        }
    }

    private static final class ThirdOrderBlock extends StateBlock {
        private final double b0, b1, b2, b3, a1, a2, a3;

        private ThirdOrderBlock(double b0, double b1, double b2, double b3,
                double a1, double a2, double a3) {
            super(3);
            this.b0 = b0; this.b1 = b1; this.b2 = b2; this.b3 = b3;
            this.a1 = a1; this.a2 = a2; this.a3 = a3;
        }

        @Override protected void derivatives(double input, double[] x, double[] dx) {
            dx[0] = x[1];
            dx[1] = x[2];
            dx[2] = (input - x[0] - a1 * x[1] - a2 * x[2]) / a3;
        }

        @Override public double output(double input) {
            double jerk = (input - active[0] - a1 * active[1] - a2 * active[2]) / a3;
            return b0 * active[0] + b1 * active[1] + b2 * active[2] + b3 * jerk;
        }
    }
}
