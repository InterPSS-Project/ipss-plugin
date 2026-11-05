package org.interpss.dstab.mach;

import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Published WT3T1 one- or two-mass mechanical system for Type-3 generators. */
public final class Wt3t1Model implements ICMLStateProvider {
    private static final double EPS = 1.0e-10;
    private static final double MIN_SPEED = .01;

    private final Wt3t1Data data;
    private double frequencyHz;
    private double initialGeneratorSpeed;
    private double initialPitch;
    private double pitch;
    private double initialAerodynamicPower;
    private double aerodynamicPower;
    private State state;
    private State oldState;
    private Derivative predictor;
    private boolean initialized;
    private Wt3p1Model pitchController;
    private Wt3e1Model electricalController;
    private final double[] rateBuffer = new double[4];

    public Wt3t1Model(Wt3t1Data data) { this.data = data; }

    public void initialize(double electricalPower, double generatorSpeed,
            double frequencyHz) {
        if (!finite(electricalPower, generatorSpeed, frequencyHz)
                || generatorSpeed <= 0.0 || frequencyHz <= 0.0) {
            throw new IllegalArgumentException("invalid WT3T1 initial boundary");
        }
        this.frequencyHz = frequencyHz;
        initialGeneratorSpeed = generatorSpeed;
        // With no WT3P1 attached, the mechanical model initializes its
        // blade-pitch input to zero. WT3P1 supplies the coupled value.
        initialPitch = 0.0;
        pitch = initialPitch;
        double shaftAngle;
        if (isSingleMass()) {
            shaftAngle = 0.0;
            initialAerodynamicPower = electricalPower
                    + data.damp() * (generatorSpeed - 1.0) * generatorSpeed;
        } else {
            double torque = electricalPower / nonzero(generatorSpeed)
                    + data.damp() * (generatorSpeed - 1.0);
            shaftAngle = torque / shaftStiffness();
            initialAerodynamicPower = torque * generatorSpeed;
        }
        aerodynamicPower = initialAerodynamicPower;
        state = new State(shaftAngle, generatorSpeed, generatorSpeed, 0.0);
        oldState = null;
        predictor = null;
        initialized = true;
        if (pitchController != null) {
            pitchController.initialize(initialPitch, generatorSpeed - 1.0,
                    electricalController == null ? electricalPower
                            : electricalController.getPowerOrder());
        }
    }

    public void step(double dt, double electricalPower, int flag) {
        if (!initialized || dt <= 0.0 || !finite(dt, electricalPower)
                || (flag != 0 && flag != 1)) return;
        updateAerodynamicPower();
        if (pitchController != null) {
            pitchController.step(dt, state.generatorSpeed - 1.0,
                    electricalController == null ? state.generatorSpeed - 1.0
                            : electricalController.getSpeedReferenceState(),
                    electricalController == null ? electricalPower
                            : electricalController.getPowerOrder(), flag);
        }
        if (flag == 0) {
            oldState = state;
            predictor = derivatives(state, electricalPower);
            state = advance(oldState, predictor, dt);
        } else {
            Derivative corrected = derivatives(state, electricalPower);
            state = advance(oldState, predictor.add(corrected), .5 * dt);
            oldState = null;
            predictor = null;
        }
        if (pitchController != null) pitch = pitchController.getPitch();
        if (!finite(state.shaftAngle, state.turbineSpeed,
                state.generatorSpeed, state.generatorAngle)) {
            throw new IllegalStateException("WT3T1 produced a non-finite state");
        }
    }

    private Derivative derivatives(State s, double electricalPower) {
        fillRates(s, electricalPower, aerodynamicPower, rateBuffer, 0);
        return new Derivative(rateBuffer[0], rateBuffer[1], rateBuffer[2], rateBuffer[3]);
    }

    private void fillRates(State s, double electricalPower, double mechanicalPower,
            double[] rates, int offset) {
        double omegaBase = 2.0 * Math.PI * frequencyHz;
        if (isSingleMass()) {
            double derivative = (mechanicalPower / nonzero(s.generatorSpeed)
                    - electricalPower / nonzero(s.generatorSpeed)
                    - data.damp() * (s.generatorSpeed - 1.0)) / (2.0 * data.h());
            rates[offset] = 0.0;
            rates[offset + 1] = derivative;
            rates[offset + 2] = derivative;
            rates[offset + 3] = omegaBase * (s.generatorSpeed - initialGeneratorSpeed);
            return;
        }
        double speedDifference = s.turbineSpeed - s.generatorSpeed;
        double shaftTorque = shaftStiffness() * s.shaftAngle;
        double dampingTorque = data.shaftDamping() * speedDifference;
        double turbineRate = (mechanicalPower / nonzero(s.turbineSpeed)
                - shaftTorque - dampingTorque) / (2.0 * turbineInertia());
        double generatorRate = (shaftTorque + dampingTorque
                - electricalPower / nonzero(s.generatorSpeed)
                - data.damp() * (s.generatorSpeed - 1.0))
                / (2.0 * generatorInertia());
        rates[offset] = omegaBase * speedDifference;
        rates[offset + 1] = turbineRate;
        rates[offset + 2] = generatorRate;
        rates[offset + 3] = omegaBase * (s.generatorSpeed - initialGeneratorSpeed);
    }

    /** Leaf state boundary; the converter owner supplies live electrical power.
     * Pitch-controller participation is intentionally unavailable until its own
     * states share the enclosing solver's integration lifecycle. */
    public int staggeredStateCount() { return 4; }

    public void readStaggeredState(double[] values, int offset) {
        requireSharedState(values, offset);
        values[offset] = state.shaftAngle;
        values[offset + 1] = state.turbineSpeed;
        values[offset + 2] = state.generatorSpeed;
        values[offset + 3] = state.generatorAngle;
    }

    /** Pure RHS evaluation: does not step the pitch controller or publish history. */
    public void evaluateStaggered(double electricalPower, double[] rates, int offset) {
        requireSharedState(rates, offset);
        if (!Double.isFinite(electricalPower)) {
            throw new IllegalArgumentException("WT3T1 electrical power must be finite");
        }
        double mechanicalPower = initialAerodynamicPower
                + data.aerodynamicGain() * (initialPitch - pitch);
        fillRates(state, electricalPower, mechanicalPower, rateBuffer, 0);
        for (double rate : rateBuffer) {
            if (!Double.isFinite(rate)) throw new IllegalStateException("WT3T1 non-finite shared rate");
        }
        System.arraycopy(rateBuffer, 0, rates, offset, 4);
    }

    /** State projection and rollback use the same atomic path; there is no clock history. */
    public void applyStaggeredState(double[] values, int offset, double stepSec) {
        requireSharedState(values, offset);
        if (!Double.isFinite(stepSec) || stepSec < 0.0) {
            throw new IllegalArgumentException("WT3T1 shared interval must be finite and nonnegative");
        }
        for (int i = 0; i < 4; i++) {
            if (!Double.isFinite(values[offset + i])) {
                throw new IllegalArgumentException("WT3T1 shared states must be finite");
            }
        }
        double mechanicalPower = initialAerodynamicPower
                + data.aerodynamicGain() * (initialPitch - pitch);
        if (!Double.isFinite(mechanicalPower)) {
            throw new IllegalStateException("WT3T1 non-finite aerodynamic power");
        }
        state = new State(values[offset], values[offset + 1], values[offset + 2], values[offset + 3]);
        aerodynamicPower = mechanicalPower;
    }

    private void requireSharedState(double[] values, int offset) {
        if (!initialized) throw new IllegalStateException("WT3T1 is not initialized");
        if (oldState != null || predictor != null) {
            throw new IllegalStateException("WT3T1 ordinary predictor is pending");
        }
        if (pitchController != null) {
            throw new UnsupportedOperationException("WT3T1 shared pitch-controller integration is not available");
        }
        java.util.Objects.requireNonNull(values, "values");
        java.util.Objects.checkFromIndexSize(offset, 4, values.length);
    }

    private void updateAerodynamicPower() {
        aerodynamicPower = initialAerodynamicPower
                + data.aerodynamicGain() * (initialPitch - pitch);
    }

    public boolean isSingleMass() { return data.turbineInertiaFraction() <= EPS; }
    public double turbineInertia() { return data.h() * data.turbineInertiaFraction(); }
    public double generatorInertia() { return data.h() - turbineInertia(); }
    public double shaftStiffness() {
        if (isSingleMass()) return 0.0;
        double omega0 = 2.0 * Math.PI * frequencyHz;
        double torsional = 2.0 * Math.PI * data.firstShaftFrequencyHz();
        return 2.0 * turbineInertia() * generatorInertia() * torsional * torsional
                / (data.h() * omega0);
    }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("Shaft twist angle", state.shaftAngle);
        values.put("Turbine rotor speed deviation", state.turbineSpeed - 1.0);
        values.put("Generator speed deviation", state.generatorSpeed - 1.0);
        values.put("Generator rotor angle deviation", state.generatorAngle);
        if (pitchController != null) {
            values.putAll(pitchController.getNamedStates());
        }
        return Map.copyOf(values);
    }

    public Wt3t1Data getData() { return data; }
    public double getGeneratorSpeed() { return state.generatorSpeed; }
    public double getTurbineSpeed() { return state.turbineSpeed; }
    public double getShaftAngle() { return state.shaftAngle; }
    public double getGeneratorAngleDeviation() { return state.generatorAngle; }
    public double getAerodynamicPower() { return aerodynamicPower; }
    public double getInitialPitch() { return initialPitch; }
    public Wt3p1Model getPitchController() { return pitchController; }
    public void setPitchController(Wt3p1Model value, Wt3e1Model electrical) {
        pitchController = value;
        electricalController = electrical;
    }
    public void setPitch(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("pitch must be finite");
        pitch = value;
    }

    private static State advance(State s, Derivative d, double h) {
        return new State(s.shaftAngle + h*d.shaftAngle,
                s.turbineSpeed + h*d.turbineSpeed,
                s.generatorSpeed + h*d.generatorSpeed,
                s.generatorAngle + h*d.generatorAngle);
    }
    private static double nonzero(double speed) {
        return Math.abs(speed) >= MIN_SPEED ? speed
                : Math.copySign(MIN_SPEED, speed == 0.0 ? 1.0 : speed);
    }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private record State(double shaftAngle, double turbineSpeed,
            double generatorSpeed, double generatorAngle) { }
    private record Derivative(double shaftAngle, double turbineSpeed,
            double generatorSpeed, double generatorAngle) {
        Derivative add(Derivative other) {
            return new Derivative(shaftAngle + other.shaftAngle,
                    turbineSpeed + other.turbineSpeed,
                    generatorSpeed + other.generatorSpeed,
                    generatorAngle + other.generatorAngle);
        }
    }
}
