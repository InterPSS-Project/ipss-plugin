package org.interpss.dstab.mach;

import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** PSS/E WT12T1 one- or two-mass mechanical model for WT1G1. */
public final class Wt12t1Model implements ICMLStateProvider {
    private static final double EPS = 1.0e-10;
    private static final double MIN_SPEED = 0.01;

    private final Wt12t1Data data;
    private double frequencyHz;
    private double initialGeneratorSpeed;
    private double aerodynamicPower;
    private double shaftAngle;
    private double turbineSpeed;
    private double generatorSpeed;
    private double generatorAngleDeviation;
    private State oldState;
    private Derivative oldDerivative;
    private boolean initialized;

    public Wt12t1Model(Wt12t1Data data) { this.data = data; }

    public void initialize(double electricalPower, double speed, double frequencyHz) {
        if (!Double.isFinite(electricalPower) || !Double.isFinite(speed)
                || speed <= 0.0 || !Double.isFinite(frequencyHz) || frequencyHz <= 0.0) {
            throw new IllegalArgumentException("invalid WT12T1 initial boundary");
        }
        this.frequencyHz = frequencyHz;
        initialGeneratorSpeed = turbineSpeed = generatorSpeed = speed;
        generatorAngleDeviation = 0.0;
        if (isSingleMass()) {
            shaftAngle = 0.0;
            aerodynamicPower = electricalPower
                    + data.damp() * (speed - 1.0) * speed;
        } else {
            double torque = electricalPower / nonzero(speed)
                    + data.damp() * (speed - 1.0);
            shaftAngle = torque / shaftStiffness();
            aerodynamicPower = torque * speed;
        }
        initialized = true;
    }

    public void step(double dt, double electricalPower, int flag) {
        if (!initialized) throw new IllegalStateException("WT12T1 is not initialized");
        if (flag != 0 && flag != 1) throw new IllegalArgumentException("flag must be 0 or 1");
        if (dt < 0.0 || !Double.isFinite(dt) || !Double.isFinite(electricalPower)) {
            throw new IllegalArgumentException("invalid WT12T1 step boundary");
        }
        if (dt == 0.0) return;
        if (flag == 0) {
            oldState = state();
            oldDerivative = derivatives(oldState, electricalPower);
            apply(oldState.advance(dt, oldDerivative));
        } else {
            if (oldState == null) throw new IllegalStateException("corrector without predictor");
            Derivative corrected = derivatives(state(), electricalPower);
            apply(oldState.advance(0.5 * dt, oldDerivative.add(corrected)));
            oldState = null;
            oldDerivative = null;
        }
        requireFinite();
    }

    private Derivative derivatives(State state, double electricalPower) {
        double omegaBase = 2.0 * Math.PI * frequencyHz;
        if (isSingleMass()) {
            double dw = (aerodynamicPower / nonzero(state.generatorSpeed)
                    - electricalPower / nonzero(state.generatorSpeed)
                    - data.damp() * (state.generatorSpeed - 1.0)) / (2.0 * data.h());
            return new Derivative(0.0, dw, dw,
                    omegaBase * (state.generatorSpeed - initialGeneratorSpeed));
        }
        double speedDifference = state.turbineSpeed - state.generatorSpeed;
        double shaftTorque = shaftStiffness() * state.shaftAngle;
        double dampingTorque = data.dshaft() * speedDifference;
        double dTurbine = (aerodynamicPower / nonzero(state.turbineSpeed)
                - shaftTorque - dampingTorque) / (2.0 * turbineInertia());
        double dGenerator = (shaftTorque + dampingTorque
                - electricalPower / nonzero(state.generatorSpeed)
                - data.damp() * (state.generatorSpeed - 1.0))
                / (2.0 * generatorInertia());
        return new Derivative(omegaBase * speedDifference, dTurbine, dGenerator,
                omegaBase * (state.generatorSpeed - initialGeneratorSpeed));
    }

    public boolean isSingleMass() { return data.htfrac() <= EPS; }
    public double turbineInertia() { return data.h() * data.htfrac(); }
    public double generatorInertia() { return data.h() - turbineInertia(); }
    public double shaftStiffness() {
        if (isSingleMass()) return 0.0;
        double omega0 = 2.0 * Math.PI * frequencyHz;
        double torsional = 2.0 * Math.PI * data.freq1Hz();
        return 2.0 * turbineInertia() * generatorInertia() * torsional * torsional
                / (data.h() * omega0);
    }

    public Wt12t1Data getData() { return data; }
    public double getAerodynamicPower() { return aerodynamicPower; }
    public void setAerodynamicPower(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Paero must be finite");
        aerodynamicPower = value;
    }
    public double getShaftAngle() { return shaftAngle; }
    public double getTurbineSpeed() { return turbineSpeed; }
    public double getGeneratorSpeed() { return generatorSpeed; }
    public double getGeneratorAngleDeviation() { return generatorAngleDeviation; }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("Shaft twist angle", shaftAngle);
        states.put("Turbine rotor speed deviation", turbineSpeed - 1.0);
        states.put("Generator speed deviation", generatorSpeed - 1.0);
        states.put("Generator rotor angle deviation", generatorAngleDeviation);
        return Map.copyOf(states);
    }

    private State state() {
        return new State(shaftAngle, turbineSpeed, generatorSpeed, generatorAngleDeviation);
    }
    private void apply(State state) {
        shaftAngle = state.shaftAngle;
        turbineSpeed = state.turbineSpeed;
        generatorSpeed = state.generatorSpeed;
        generatorAngleDeviation = state.generatorAngleDeviation;
    }
    private void requireFinite() {
        if (!Double.isFinite(shaftAngle) || !Double.isFinite(turbineSpeed)
                || !Double.isFinite(generatorSpeed) || !Double.isFinite(generatorAngleDeviation)) {
            throw new IllegalStateException("WT12T1 produced a non-finite state");
        }
    }
    private static double nonzero(double speed) {
        return Math.abs(speed) >= MIN_SPEED ? speed : Math.copySign(MIN_SPEED, speed == 0.0 ? 1.0 : speed);
    }

    private record State(double shaftAngle, double turbineSpeed,
            double generatorSpeed, double generatorAngleDeviation) {
        State advance(double scale, Derivative d) {
            return new State(shaftAngle + scale * d.shaftAngle,
                    turbineSpeed + scale * d.turbineSpeed,
                    generatorSpeed + scale * d.generatorSpeed,
                    generatorAngleDeviation + scale * d.generatorAngleDeviation);
        }
    }
    private record Derivative(double shaftAngle, double turbineSpeed,
            double generatorSpeed, double generatorAngleDeviation) {
        Derivative add(Derivative other) {
            return new Derivative(shaftAngle + other.shaftAngle,
                    turbineSpeed + other.turbineSpeed,
                    generatorSpeed + other.generatorSpeed,
                    generatorAngleDeviation + other.generatorAngleDeviation);
        }
    }
}
