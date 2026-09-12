package org.interpss.dstab.mach;

import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Published WT3P1 speed/power pitch controller with non-windup limits. */
public final class Wt3p1Model implements ICMLStateProvider {
    private static final double EPS = 1.0e-10;
    private final Wt3p1Data data;
    private State state;
    private State oldState;
    private Derivative predictor;
    private boolean initialized;

    public Wt3p1Model(Wt3p1Data data) { this.data = data; }

    public void initialize(double initialPitch, double generatorSpeedDeviation,
            double powerOrder) {
        double powerError = powerOrder - data.powerSetpoint();
        double compensationIntegral = 0.0;
        double speedIntegral = clamp(initialPitch
                - data.compensationProportionalGain() * powerError,
                data.thetaMin(), data.thetaMax());
        state = new State(clamp(initialPitch, data.thetaMin(), data.thetaMax()),
                speedIntegral, compensationIntegral);
        initialized = true;
    }

    public void step(double dt, double generatorSpeedDeviation,
            double speedReferenceDeviation, double powerOrder, int flag) {
        if (!initialized || dt <= 0.0 || !finite(dt, generatorSpeedDeviation,
                speedReferenceDeviation, powerOrder)
                || (flag != 0 && flag != 1)) return;
        if (flag == 0) {
            oldState = state;
            predictor = derivatives(state, generatorSpeedDeviation,
                    speedReferenceDeviation, powerOrder, dt);
            state = advance(oldState, predictor, dt);
        } else {
            Derivative corrected = derivatives(state, generatorSpeedDeviation,
                    speedReferenceDeviation, powerOrder, dt);
            state = advance(oldState, predictor.add(corrected), .5 * dt);
            oldState = null;
            predictor = null;
        }
        state = new State(clamp(state.pitch, data.thetaMin(), data.thetaMax()),
                state.speedIntegral,
                clamp(state.compensationIntegral, 0.0, data.thetaMax()));
    }

    private Derivative derivatives(State s, double generatorSpeedDeviation,
            double speedReferenceDeviation, double powerOrder, double dt) {
        double speedError = generatorSpeedDeviation - speedReferenceDeviation;
        double powerError = powerOrder - data.powerSetpoint();
        double speedRate = data.speedIntegralGain() * speedError;
        double compensationRate = data.compensationIntegralGain() * powerError;
        if ((s.compensationIntegral >= data.thetaMax() && compensationRate > 0.0)
                || (s.compensationIntegral <= 0.0 && compensationRate < 0.0)) {
            compensationRate = 0.0;
        }
        double pitchRate = publishedPitchRate(data, s.pitch, s.speedIntegral,
                s.compensationIntegral, generatorSpeedDeviation,
                speedReferenceDeviation, powerOrder, dt);
        return new Derivative(pitchRate, speedRate, compensationRate);
    }

    /** Published summed PI command, symmetric rate limit, and output integrator. */
    public static double publishedPitchRate(Wt3p1Data data, double pitch,
            double speedIntegral, double compensationIntegral,
            double generatorSpeedDeviation, double speedReferenceDeviation,
            double powerOrder, double step) {
        double target = speedIntegral
                        + data.speedProportionalGain()
                                * (generatorSpeedDeviation - speedReferenceDeviation)
                        + compensationIntegral
                        + data.compensationProportionalGain()
                                * (powerOrder - data.powerSetpoint());
        double rate = data.bladeTime() <= EPS
                ? (target - pitch) / step : (target - pitch) / data.bladeTime();
        rate = clamp(rate, -data.thetaRateMax(), data.thetaRateMax());
        if ((pitch >= data.thetaMax() && rate > 0.0)
                || (pitch <= data.thetaMin() && rate < 0.0)) return 0.0;
        return rate;
    }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("Blade output lag", state.pitch);
        values.put("Pitch control", state.speedIntegral);
        values.put("Pitch compensation", state.compensationIntegral);
        return Map.copyOf(values);
    }

    public Wt3p1Data getData() { return data; }
    public double getPitch() { return state.pitch; }
    public double getPitchControlState() { return state.speedIntegral; }
    public double getPitchCompensationState() { return state.compensationIntegral; }

    private static State advance(State s, Derivative d, double h) {
        return new State(s.pitch + h*d.pitch, s.speedIntegral + h*d.speedIntegral,
                s.compensationIntegral + h*d.compensationIntegral);
    }
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
    private record State(double pitch, double speedIntegral,
            double compensationIntegral) { }
    private record Derivative(double pitch, double speedIntegral,
            double compensationIntegral) {
        Derivative add(Derivative other) {
            return new Derivative(pitch + other.pitch,
                    speedIntegral + other.speedIntegral,
                    compensationIntegral + other.compensationIntegral);
        }
    }
}
