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
        double omegaBase = 2.0 * Math.PI * frequencyHz;
        if (isSingleMass()) {
            double derivative = (aerodynamicPower / nonzero(s.generatorSpeed)
                    - electricalPower / nonzero(s.generatorSpeed)
                    - data.damp() * (s.generatorSpeed - 1.0)) / (2.0 * data.h());
            return new Derivative(0.0, derivative, derivative,
                    omegaBase * (s.generatorSpeed - initialGeneratorSpeed));
        }
        double speedDifference = s.turbineSpeed - s.generatorSpeed;
        double shaftTorque = shaftStiffness() * s.shaftAngle;
        double dampingTorque = data.shaftDamping() * speedDifference;
        double turbineRate = (aerodynamicPower / nonzero(s.turbineSpeed)
                - shaftTorque - dampingTorque) / (2.0 * turbineInertia());
        double generatorRate = (shaftTorque + dampingTorque
                - electricalPower / nonzero(s.generatorSpeed)
                - data.damp() * (s.generatorSpeed - 1.0))
                / (2.0 * generatorInertia());
        return new Derivative(omegaBase * speedDifference, turbineRate,
                generatorRate, omegaBase * (s.generatorSpeed - initialGeneratorSpeed));
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
