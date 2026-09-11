package org.interpss.dstab.control.pss.ieee.y2005.pss4b;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.interpss.dstab.control.pss.ieee.y2005.pss4b.Ieee2005PSS4BStabilizerData.BandData;
import org.interpss.dstab.control.pss.ieee.y2005.pss4b.Ieee2005PSS4BStabilizerData.InputTransducerData;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateStabilizer;
import com.interpss.dstab.mach.Machine;

/** IEEE Std 421.5-2005 PSS4B multi-band power-system stabilizer. */
@AnController(input="mach.speed", output="this.outputSignal", refPoint="0.0", display={})
public final class Ieee2005PSS4BStabilizer extends AnnotateStabilizer
        implements IntegrationStepAware {
    private static final double EPS = 1.0e-12;

    private final Ieee2005PSS4BStabilizerData sourceData;
    private Ieee2005PSS4BStabilizerData effectiveData;

    private SecondOrderBlock lowInputFilter;
    private SecondOrderBlock lowNotch1;
    private SecondOrderBlock lowNotch2;
    private FirstOrderBlock highInputLag;
    private SecondOrderBlock highInputFilter;
    private FirstOrderBlock highRampLag;
    private SecondOrderBlock highNotch1;
    private SecondOrderBlock highNotch2;
    private Band lowBand;
    private Band intermediateBand;
    private Band highBand;

    private double initialSpeed;
    private double initialElectricalPower;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double lowIntermediateInput;
    private double highLagOutput;
    private double highFilterOutput;
    private double highInput;
    private double lowOutput;
    private double intermediateOutput;
    private double highOutput;
    public double outputSignal;

    public Ieee2005PSS4BStabilizer(String id,
            Ieee2005PSS4BStabilizerData data, Machine machine) {
        this(id, data, machine, true);
    }

    private Ieee2005PSS4BStabilizer(String id,
            Ieee2005PSS4BStabilizerData data, Machine machine,
            boolean attachToMachine) {
        super(id, "PSS4B", "IEEE-2005");
        sourceData = data;
        if (attachToMachine) setMachine(machine);
    }

    /** Create a computational engine for PSS4C without occupying the machine slot. */
    public static Ieee2005PSS4BStabilizer createDetachedEngine(String id,
            Ieee2005PSS4BStabilizerData data, Machine machine) {
        return new Ieee2005PSS4BStabilizer(id, data, machine, false);
    }

    public Ieee2005PSS4BStabilizerData getData() {
        return sourceData;
    }

    public Ieee2005PSS4BStabilizerData getEffectiveData() {
        return effectiveData;
    }

    @Override
    public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        if (!Double.isFinite(timeStepSec) || timeStepSec < 0.0
                || !Double.isFinite(multiplier) || multiplier < 0.0) {
            throw new IllegalArgumentException(
                    "PSS4B integration-step settings must be finite and non-negative");
        }
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        effectiveData = correctedData(sourceData, machine);
        InputTransducerData input = effectiveData.input();

        lowInputFilter = new SecondOrderBlock(1.0, input.dli(), input.cli(),
                input.bli(), input.ali(), false);
        lowNotch1 = notch(input.bwli1(), input.wli1());
        lowNotch2 = notch(input.bwli2(), input.wli2());

        highInputLag = new FirstOrderBlock(1.0, 0.0, input.th());
        highInputFilter = new SecondOrderBlock(0.0, 0.0, 1.0,
                input.bh(), input.ah(), false);
        highRampLag = new FirstOrderBlock(1.0, 0.0, input.h());
        highNotch1 = notch(input.bwh1(), input.wh1());
        highNotch2 = notch(input.bwh2(), input.wh2());

        lowBand = new Band(effectiveData.lowBand());
        intermediateBand = new Band(effectiveData.intermediateBand());
        highBand = new Band(effectiveData.highBand());

        initialSpeed = machine.getSpeed();
        initialElectricalPower = machine.getPe();
        lowIntermediateInput = 0.0;
        highInput = 0.0;
        lowOutput = 0.0;
        intermediateOutput = 0.0;
        highOutput = 0.0;
        outputSignal = 0.0;
        return true;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        if (dt <= 0.0) return true;
        boolean modifiedEuler = method == DynamicSimuMethod.MODIFIED_EULER;
        int stage = modifiedEuler ? flag : 2;

        double speedDeviation = machine.getSpeed() - initialSpeed;
        double powerDeviation = machine.getPe() - initialElectricalPower;

        lowIntermediateInput = advance(lowInputFilter, speedDeviation, dt, stage);
        lowIntermediateInput = advance(lowNotch1, lowIntermediateInput, dt, stage);
        lowIntermediateInput = advance(lowNotch2, lowIntermediateInput, dt, stage);

        highLagOutput = advance(highInputLag, powerDeviation, dt, stage);
        highFilterOutput = advance(highInputFilter, highLagOutput, dt, stage);
        highInput = advance(highRampLag, highFilterOutput, dt, stage);
        highInput = advance(highNotch1, highInput, dt, stage);
        highInput = advance(highNotch2, highInput, dt, stage);

        lowOutput = lowBand.advance(lowIntermediateInput, dt, stage);
        intermediateOutput = intermediateBand.advance(lowIntermediateInput, dt, stage);
        highOutput = highBand.advance(highInput, dt, stage);
        outputSignal = clamp(lowOutput + intermediateOutput + highOutput,
                effectiveData.vstmax(), effectiveData.vstmin());
        return true;
    }

    @Override
    public double getOutput(Machine machine) {
        return outputSignal;
    }

    public double getLowIntermediateInput() { return lowIntermediateInput; }
    public double getHighInput() { return highInput; }
    public double getLowOutput() { return lowOutput; }
    public double getIntermediateOutput() { return intermediateOutput; }
    public double getHighOutput() { return highOutput; }

    /** Stable semantic names for the 32 published PSS4B dynamic memories. */
    @Override
    public Map<String, Double> getNamedStates() {
        if (lowInputFilter == null) return Map.of();
        Map<String, Double> states = new LinkedHashMap<>();
        putSecondOrder(states, "First signal transducer", lowInputFilter);
        putSecondOrder(states, "First signal notch 1", lowNotch1);
        putSecondOrder(states, "First signal notch 2", lowNotch2);
        states.put("Second signal transducer lag", highInputLag.state());
        putSecondOrder(states, "Second signal transducer", highInputFilter);
        states.put("Second signal time lag", highRampLag.state());
        putSecondOrder(states, "Second signal notch 1", highNotch1);
        putSecondOrder(states, "Second signal notch 2", highNotch2);
        lowBand.putNamedStates(states, "Low band");
        intermediateBand.putNamedStates(states, "Intermediate band");
        highBand.putNamedStates(states, "High band");
        return Collections.unmodifiableMap(states);
    }

    /** PSS/E STATE-array coordinates, separate from canonical CML memories. */
    public Map<String, Double> getPsseStateCoordinates() {
        if (lowInputFilter == null) return Map.of();
        Map<String, Double> states = new LinkedHashMap<>();
        putSecondOrder(states, "First signal transducer", lowInputFilter);
        putScaledSecondOrder(states, "First signal notch 1", lowNotch1);
        putScaledSecondOrder(states, "First signal notch 2", lowNotch2);
        double th = effectiveData.input().th();
        states.put("Second signal transducer lag",
                th > EPS ? (initialElectricalPower + highInputLag.state()) / th : 0.0);
        states.put("Second signal transducer position", highInputFilter.velocity());
        states.put("Second signal transducer velocity", highFilterOutput);
        states.put("Second signal time lag", highRampLag.state());
        putScaledSecondOrder(states, "Second signal notch 1", highNotch1);
        putScaledSecondOrder(states, "Second signal notch 2", highNotch2);
        lowBand.putNamedStates(states, "Low band");
        intermediateBand.putNamedStates(states, "Intermediate band");
        highBand.putNamedStates(states, "High band");
        return Collections.unmodifiableMap(states);
    }

    private static void putSecondOrder(Map<String, Double> states, String prefix,
            SecondOrderBlock block) {
        states.put(prefix + " position", block.position());
        states.put(prefix + " velocity", block.velocity());
    }

    private static void putScaledSecondOrder(Map<String, Double> states, String prefix,
            SecondOrderBlock block) {
        states.put(prefix + " position", block.denominatorS2() * block.position());
        states.put(prefix + " velocity", block.denominatorS2() * block.velocity());
    }

    private Ieee2005PSS4BStabilizerData correctedData(
            Ieee2005PSS4BStabilizerData data, Machine machine) {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        InputTransducerData input = data.input();
        InputTransducerData correctedInput = new InputTransducerData(
                input.cli(), input.dli(), input.ali(), input.bli(),
                input.bwli1(), input.wli1(), input.bwli2(), input.wli2(),
                positiveOrMinimum(input.th(), minimum),
                positiveOrMinimum(input.ah(), minimum), input.bh(),
                input.h() == 0.0 ? 2.0 * machine.getH() : input.h(),
                input.bwh1(), input.wh1(), input.bwh2(), input.wh2());
        return new Ieee2005PSS4BStabilizerData(
                correctedInput,
                correctedBand(data.lowBand(), minimum),
                correctedBand(data.intermediateBand(), minimum),
                correctedBand(data.highBand(), minimum),
                normalizedHigh(data.vstmax(), data.vstmin()),
                normalizedLow(data.vstmax(), data.vstmin()));
    }

    public static BandData correctedBand(BandData b, double minimum) {
        return new BandData(
                positiveOrMinimum(b.k1(), minimum), b.k11(),
                b.t1(), b.t2(), b.t3(), b.t4(), b.t5(), b.t6(),
                positiveOrMinimum(b.k2(), minimum), b.k17(),
                b.t7(), b.t8(), b.t9(), b.t10(), b.t11(), b.t12(),
                b.gain(), normalizedHigh(b.max(), b.min()),
                normalizedLow(b.max(), b.min()));
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

    private static SecondOrderBlock notch(double bandwidthTimesOmega, double omega) {
        if (omega <= EPS) return SecondOrderBlock.bypass();
        double omegaSquaredInverse = 1.0 / (omega * omega);
        return new SecondOrderBlock(1.0, 0.0, omegaSquaredInverse,
                bandwidthTimesOmega * omegaSquaredInverse,
                omegaSquaredInverse, false);
    }

    private static double advance(TransferBlock block, double input, double dt, int stage) {
        if (stage == 0) block.predict(input, dt);
        else if (stage == 1) block.correct(input, dt);
        else block.euler(input, dt);
        return block.output(input);
    }

    public static double clamp(double value, double max, double min) {
        return Math.max(min, Math.min(max, value));
    }

    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }

    public static final class Band {
        private final BandData data;
        private final FirstOrderBlock upper1;
        private final FirstOrderBlock upper2;
        private final FirstOrderBlock upper3;
        private final FirstOrderBlock lower1;
        private final FirstOrderBlock lower2;
        private final FirstOrderBlock lower3;

        public Band(BandData data) {
            this.data = data;
            upper1 = new FirstOrderBlock(data.k11(), data.t1(), data.t2());
            upper2 = new FirstOrderBlock(1.0, data.t3(), data.t4());
            upper3 = new FirstOrderBlock(1.0, data.t5(), data.t6());
            lower1 = new FirstOrderBlock(data.k17(), data.t7(), data.t8());
            lower2 = new FirstOrderBlock(1.0, data.t9(), data.t10());
            lower3 = new FirstOrderBlock(1.0, data.t11(), data.t12());
        }

        public double advance(double input, double dt, int stage) {
            double upper = Ieee2005PSS4BStabilizer.advance(
                    upper1, data.k1() * input, dt, stage);
            upper = Ieee2005PSS4BStabilizer.advance(upper2, upper, dt, stage);
            upper = Ieee2005PSS4BStabilizer.advance(upper3, upper, dt, stage);
            double lower = Ieee2005PSS4BStabilizer.advance(
                    lower1, data.k2() * input, dt, stage);
            lower = Ieee2005PSS4BStabilizer.advance(lower2, lower, dt, stage);
            lower = Ieee2005PSS4BStabilizer.advance(lower3, lower, dt, stage);
            return clamp(data.gain() * (upper - lower), data.max(), data.min());
        }

        private void putNamedStates(Map<String, Double> states, String prefix) {
            states.put(prefix + " upper lead-lag 1", upper1.state());
            states.put(prefix + " upper lead-lag 2", upper2.state());
            states.put(prefix + " upper lead-lag 3", upper3.state());
            states.put(prefix + " lower lead-lag 1", lower1.state());
            states.put(prefix + " lower lead-lag 2", lower2.state());
            states.put(prefix + " lower lead-lag 3", lower3.state());
        }
    }

    private interface TransferBlock {
        void predict(double input, double dt);
        void correct(double input, double dt);
        void euler(double input, double dt);
        double output(double input);
    }

    /** (b0 + b1*s) / (1 + a1*s), with a zero-denominator bypass convention. */
    private static final class FirstOrderBlock implements TransferBlock {
        private final double b0;
        private final double b1;
        private final double a1;
        private double state;
        private double trial;
        private double oldDerivative;
        private double activeState;

        private FirstOrderBlock(double b0, double b1, double a1) {
            this.b0 = b0;
            this.b1 = b1;
            this.a1 = a1;
        }

        @Override public void predict(double input, double dt) {
            if (a1 <= EPS) return;
            oldDerivative = (input - state) / a1;
            trial = state + oldDerivative * dt;
            activeState = trial;
        }

        @Override public void correct(double input, double dt) {
            if (a1 <= EPS) return;
            double correctedDerivative = (input - trial) / a1;
            state += 0.5 * (oldDerivative + correctedDerivative) * dt;
            activeState = state;
        }

        @Override public void euler(double input, double dt) {
            if (a1 <= EPS) return;
            state += (input - state) / a1 * dt;
            activeState = state;
        }

        @Override public double output(double input) {
            if (a1 <= EPS) return b0 * input;
            return b0 * activeState + b1 * (input - activeState) / a1;
        }

        private double state() {
            return activeState;
        }
    }

    /** (b0 + b1*s + b2*s^2) / (1 + a1*s + a2*s^2). */
    private static final class SecondOrderBlock implements TransferBlock {
        private final double b0;
        private final double b1;
        private final double b2;
        private final double a1;
        private final double a2;
        private final boolean bypass;
        private double position;
        private double velocity;
        private double trialPosition;
        private double trialVelocity;
        private double oldPositionDerivative;
        private double oldVelocityDerivative;
        private double activePosition;
        private double activeVelocity;

        private SecondOrderBlock(double b0, double b1, double b2,
                double a1, double a2, boolean bypass) {
            this.b0 = b0;
            this.b1 = b1;
            this.b2 = b2;
            this.a1 = a1;
            this.a2 = a2;
            this.bypass = bypass;
        }

        private static SecondOrderBlock bypass() {
            return new SecondOrderBlock(1.0, 0.0, 0.0, 0.0, 0.0, true);
        }

        @Override public void predict(double input, double dt) {
            if (bypass || a2 <= EPS) return;
            oldPositionDerivative = velocity;
            oldVelocityDerivative = acceleration(input, position, velocity);
            trialPosition = position + oldPositionDerivative * dt;
            trialVelocity = velocity + oldVelocityDerivative * dt;
            activePosition = trialPosition;
            activeVelocity = trialVelocity;
        }

        @Override public void correct(double input, double dt) {
            if (bypass || a2 <= EPS) return;
            double newPositionDerivative = trialVelocity;
            double newVelocityDerivative = acceleration(input, trialPosition, trialVelocity);
            position += 0.5 * (oldPositionDerivative + newPositionDerivative) * dt;
            velocity += 0.5 * (oldVelocityDerivative + newVelocityDerivative) * dt;
            activePosition = position;
            activeVelocity = velocity;
        }

        @Override public void euler(double input, double dt) {
            if (bypass || a2 <= EPS) return;
            double acceleration = acceleration(input, position, velocity);
            position += velocity * dt;
            velocity += acceleration * dt;
            activePosition = position;
            activeVelocity = velocity;
        }

        @Override public double output(double input) {
            if (bypass || a2 <= EPS) return input;
            return b0 * activePosition + b1 * activeVelocity
                    + b2 * acceleration(input, activePosition, activeVelocity);
        }

        private double acceleration(double input, double x, double dx) {
            return (input - x - a1 * dx) / a2;
        }

        private double position() {
            return activePosition;
        }

        private double velocity() {
            return activeVelocity;
        }

        private double denominatorS2() {
            return a2;
        }
    }
}
