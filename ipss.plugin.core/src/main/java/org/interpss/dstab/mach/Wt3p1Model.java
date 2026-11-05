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
    private final double[] rateBuffer = new double[3];

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
        oldState = null;
        predictor = null;
        initialized = true;
    }

    public void step(double dt, double generatorSpeedDeviation,
            double speedReferenceDeviation, double powerOrder, int flag) {
        if (!initialized || dt <= 0.0 || !Double.isFinite(dt)
                || !Double.isFinite(generatorSpeedDeviation)
                || !Double.isFinite(speedReferenceDeviation)
                || !Double.isFinite(powerOrder)
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
        fillRates(s, dt, generatorSpeedDeviation, speedReferenceDeviation,
                powerOrder, rateBuffer, 0);
        return new Derivative(rateBuffer[0], rateBuffer[1], rateBuffer[2]);
    }

    private void fillRates(State s, double dt, double generatorSpeedDeviation,
            double speedReferenceDeviation, double powerOrder, double[] out, int offset) {
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
        if (!Double.isFinite(pitchRate) || !Double.isFinite(speedRate)
                || !Double.isFinite(compensationRate)) {
            throw new IllegalStateException("WT3P1 non-finite derivative");
        }
        out[offset] = pitchRate;
        out[offset + 1] = speedRate;
        out[offset + 2] = compensationRate;
    }

    /** Leaf numerical state owned by the enclosing converter bundle. */
    public int staggeredStateCount() { return 3; }

    public void readStaggeredState(double[] values, int offset) {
        requireSharedState(values, offset);
        values[offset] = state.pitch;
        values[offset + 1] = state.speedIntegral;
        values[offset + 2] = state.compensationIntegral;
    }

    /** Pure rates from the existing pitch equations and the bundle's live inputs. */
    public void evaluateStaggered(double stepSec, double generatorSpeedDeviation,
            double speedReferenceDeviation, double powerOrder,
            double[] derivatives, int offset) {
        requireSharedState(derivatives, offset);
        if (!Double.isFinite(stepSec) || stepSec <= 0.0
                || !Double.isFinite(generatorSpeedDeviation)
                || !Double.isFinite(speedReferenceDeviation)
                || !Double.isFinite(powerOrder)) {
            throw new IllegalArgumentException("WT3P1 shared interval and inputs must be finite");
        }
        fillRates(state, stepSec, generatorSpeedDeviation,
                speedReferenceDeviation, powerOrder, derivatives, offset);
    }

    /** Accepted projection and rollback use one atomic, finite state path. */
    public void applyStaggeredState(double[] values, int offset, double stepSec) {
        requireSharedState(values, offset);
        if (!Double.isFinite(stepSec) || stepSec < 0.0) {
            throw new IllegalArgumentException("WT3P1 shared interval must be finite and nonnegative");
        }
        double pitch = values[offset];
        double speedIntegral = values[offset + 1];
        double compensationIntegral = values[offset + 2];
        if (!Double.isFinite(pitch) || !Double.isFinite(speedIntegral)
                || !Double.isFinite(compensationIntegral)) {
            throw new IllegalArgumentException("WT3P1 shared states must be finite");
        }
        state = new State(clamp(pitch, data.thetaMin(), data.thetaMax()),
                speedIntegral, clamp(compensationIntegral, 0.0, data.thetaMax()));
    }

    private void requireSharedState(double[] values, int offset) {
        if (!initialized) throw new IllegalStateException("WT3P1 is not initialized");
        if (oldState != null || predictor != null) {
            throw new IllegalStateException("WT3P1 ordinary predictor is pending");
        }
        java.util.Objects.requireNonNull(values, "values");
        java.util.Objects.checkFromIndexSize(offset, 3, values.length);
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
        double rateMagnitude = data.preparedRateMagnitude();
        rate = clamp(rate, -rateMagnitude, rateMagnitude);
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
