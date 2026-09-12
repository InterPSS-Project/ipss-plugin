package org.interpss.dstab.mach;

import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Rotor-resistance controller for the Type-2 wind generator. */
public final class Wt2e1Model implements ICMLStateProvider {
    private static final double EPS = 1.0e-10;

    private final Wt2e1Data data;
    private final Wt2g1Data generatorData;
    private double speedFilter;
    private double powerFilter;
    private double integrator;
    private double output;
    private State oldState;
    private Derivative oldDerivative;
    private boolean initialized;

    public Wt2e1Model(Wt2e1Data data, Wt2g1Data generatorData) {
        this.data = data;
        this.generatorData = generatorData;
    }

    public void initialize(double speedDeviation, double electricalPower,
            double rotorResistance) {
        requireFinite(speedDeviation, "speed");
        requireFinite(electricalPower, "power");
        speedFilter = speedDeviation;
        powerFilter = electricalPower;
        integrator = rotorResistance;
        output = limit(rotorResistance);
        initialized = true;
    }

    public void step(double dt, double speedDeviation, double electricalPower, int flag) {
        if (!initialized) throw new IllegalStateException("WT2E1 is not initialized");
        if (flag != 0 && flag != 1) throw new IllegalArgumentException("flag must be 0 or 1");
        if (dt < 0.0 || !Double.isFinite(dt)) {
            throw new IllegalArgumentException("invalid WT2E1 time step");
        }
        requireFinite(speedDeviation, "speed");
        requireFinite(electricalPower, "power");
        if (dt == 0.0) return;

        if (flag == 0) {
            oldState = state(speedDeviation, electricalPower);
            oldDerivative = derivatives(oldState, speedDeviation, electricalPower);
            apply(oldState.advance(dt, oldDerivative), speedDeviation, electricalPower);
        } else {
            if (oldState == null) throw new IllegalStateException("corrector without predictor");
            State predicted = state(speedDeviation, electricalPower);
            Derivative corrected = derivatives(predicted, speedDeviation, electricalPower);
            apply(oldState.advance(0.5 * dt, oldDerivative.add(corrected)),
                    speedDeviation, electricalPower);
            oldState = null;
            oldDerivative = null;
        }
        updateOutput();
    }

    private Derivative derivatives(State state, double speed, double power) {
        double filteredSpeed = data.speedFilterTime() <= EPS ? speed : state.speedFilter;
        double filteredPower = data.powerFilterTime() <= EPS ? power : state.powerFilter;
        double error = filteredPower - generatorData.powerAtSpeedDeviation(filteredSpeed);
        double unconstrained = state.integrator + data.proportionalGain() * error;
        double integralDerivative = error / data.integratorTime();
        if ((unconstrained >= data.outputMax() && integralDerivative > 0.0)
                || (unconstrained <= data.outputMin() && integralDerivative < 0.0)) {
            integralDerivative = 0.0;
        }
        return new Derivative(
                data.speedFilterTime() <= EPS ? 0.0
                        : (speed - state.speedFilter) / data.speedFilterTime(),
                data.powerFilterTime() <= EPS ? 0.0
                        : (power - state.powerFilter) / data.powerFilterTime(),
                integralDerivative);
    }

    private State state(double speed, double power) {
        return new State(data.speedFilterTime() <= EPS ? speed : speedFilter,
                data.powerFilterTime() <= EPS ? power : powerFilter, integrator);
    }

    private void apply(State state, double speed, double power) {
        speedFilter = data.speedFilterTime() <= EPS ? speed : state.speedFilter;
        powerFilter = data.powerFilterTime() <= EPS ? power : state.powerFilter;
        integrator = state.integrator;
    }

    private void updateOutput() {
        double error = powerFilter - generatorData.powerAtSpeedDeviation(speedFilter);
        output = limit(integrator + data.proportionalGain() * error);
    }

    private double limit(double value) {
        return Math.max(data.outputMin(), Math.min(data.outputMax(), value));
    }

    public Wt2e1Data getData() { return data; }
    public double getOutput() { return output; }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("Rotor speed filter", speedFilter);
        states.put("Power filter", powerFilter);
        states.put("PI integrator", integrator);
        return Map.copyOf(states);
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("WT2E1 " + name + " must be finite");
        }
    }

    private record State(double speedFilter, double powerFilter, double integrator) {
        State advance(double scale, Derivative derivative) {
            return new State(speedFilter + scale * derivative.speedFilter,
                    powerFilter + scale * derivative.powerFilter,
                    integrator + scale * derivative.integrator);
        }
    }

    private record Derivative(double speedFilter, double powerFilter, double integrator) {
        Derivative add(Derivative other) {
            return new Derivative(speedFilter + other.speedFilter,
                    powerFilter + other.powerFilter, integrator + other.integrator);
        }
    }
}
