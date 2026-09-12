package org.interpss.dstab.mach;

import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Three-state speed/power PI pitch controller with servo limits. */
public final class Gewtptu1Model implements ICMLStateProvider {
    private static final double EPS = 1.0e-10;

    private final Gewtptu1Data data;
    private double speedReference;
    private double speedIntegral;
    private double powerIntegral;
    private double pitch;
    private State oldState;
    private Derivative predictor;
    private boolean initialized;

    public Gewtptu1Model(Gewtptu1Data data) { this.data = data; }

    public void initialize(double initialPitch, double speed) {
        if (!Double.isFinite(initialPitch) || !Double.isFinite(speed)) {
            throw new IllegalArgumentException("GEWTPTU1 initial boundary must be finite");
        }
        speedReference = speed;
        pitch = clamp(initialPitch, data.pitchMinimum(), data.pitchMaximum());
        // At equilibrium the speed path supplies the initial pitch and the
        // power compensator starts at its documented zero boundary.
        speedIntegral = pitch;
        powerIntegral = 0.0;
        initialized = true;
    }

    public void step(double dt, double speed, double powerOrder, int flag) {
        if (!initialized || !finite(dt, speed, powerOrder) || dt <= 0.0
                || (flag != 0 && flag != 1)) {
            throw new IllegalArgumentException("invalid GEWTPTU1 step boundary");
        }
        if (flag == 0) {
            oldState = state();
            predictor = derivatives(oldState, dt, speed, powerOrder);
            apply(advance(oldState, predictor, dt));
        } else {
            if (oldState == null || predictor == null) {
                throw new IllegalStateException("corrector without predictor");
            }
            Derivative corrected = derivatives(state(), dt, speed, powerOrder);
            apply(correct(oldState, predictor, corrected, dt));
            oldState = null;
            predictor = null;
        }
    }

    private Derivative derivatives(State state, double dt, double speed,
            double powerOrder) {
        double speedError = speed - speedReference;
        double powerError = powerOrder - data.powerReference();
        double rawCommand = data.speedProportionalGain() * speedError
                + state.speedIntegral
                + data.powerProportionalGain() * powerError
                + state.powerIntegral;
        double speedRate = integralRate(state.speedIntegral,
                data.speedIntegralGain(), speedError,
                data.speedProportionalGain());
        double powerRate = integralRate(state.powerIntegral,
                data.powerIntegralGain(), powerError,
                data.powerProportionalGain());
        if ((rawCommand >= data.pitchMaximum() && speedRate > 0.0)
                || (rawCommand <= data.pitchMinimum() && speedRate < 0.0)) speedRate = 0.0;
        if ((rawCommand >= data.pitchMaximum() && powerRate > 0.0)
                || (rawCommand <= data.pitchMinimum() && powerRate < 0.0)) powerRate = 0.0;
        double command = clamp(rawCommand,
                data.pitchMinimum(), data.pitchMaximum());
        double pitchRate = data.outputLagTime() <= EPS
                ? (command - state.pitch) / dt
                : (command - state.pitch) / data.outputLagTime();
        pitchRate = clamp(pitchRate, data.pitchRateMinimum(),
                data.pitchRateMaximum());
        if (blocksOutward(state.pitch, pitchRate)) pitchRate = 0.0;
        return new Derivative(speedRate, powerRate, pitchRate);
    }

    private double integralRate(double state, double gain, double error,
            double proportionalGain) {
        double pathOutput = proportionalGain * error + state;
        if ((pathOutput >= data.pitchMaximum() && error > 0.0)
                || (pathOutput <= data.pitchMinimum() && error < 0.0)) return 0.0;
        return gain * error;
    }

    private boolean blocksOutward(double value, double rate) {
        return (value >= data.pitchMaximum() && rate > 0.0)
                || (value <= data.pitchMinimum() && rate < 0.0);
    }

    private State state() { return new State(speedIntegral, powerIntegral, pitch); }
    private void apply(State state) {
        speedIntegral = state.speedIntegral;
        powerIntegral = state.powerIntegral;
        pitch = clamp(state.pitch, data.pitchMinimum(), data.pitchMaximum());
    }
    private static State advance(State state, Derivative derivative, double dt) {
        return new State(state.speedIntegral + dt * derivative.speedIntegral,
                state.powerIntegral + dt * derivative.powerIntegral,
                state.pitch + dt * derivative.pitch);
    }
    private static State correct(State state, Derivative first,
            Derivative second, double dt) {
        return new State(state.speedIntegral + .5 * dt
                        * (first.speedIntegral + second.speedIntegral),
                state.powerIntegral + .5 * dt
                        * (first.powerIntegral + second.powerIntegral),
                state.pitch + .5 * dt * (first.pitch + second.pitch));
    }

    public Gewtptu1Data getData() { return data; }
    public double getSpeedReference() { return speedReference; }
    public double getSpeedIntegral() { return speedIntegral; }
    public double getPowerIntegral() { return powerIntegral; }
    public double getPitch() { return pitch; }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("Pitch Control", speedIntegral);
        states.put("Pitch Compensator", powerIntegral);
        states.put("Output Lag", pitch);
        return Map.copyOf(states);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
    private record State(double speedIntegral, double powerIntegral, double pitch) { }
    private record Derivative(double speedIntegral, double powerIntegral, double pitch) { }
}
