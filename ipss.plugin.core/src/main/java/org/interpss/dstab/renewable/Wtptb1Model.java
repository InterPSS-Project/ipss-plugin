package org.interpss.dstab.renewable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * WTPTB1 source-equation foundation, not yet a native-reader implementation.
 * PSS/E 36.7 Model Library section 24.4: state-bounded PI integrators, separately
 * clipped PI outputs and directional pitch-angle cross anti-windup. Speed
 * reference is a time-varying torque-controller input, not a frozen initial value.
 */
public final class Wtptb1Model {
    private final Wtptb1Data data;
    // Native state order: output lag, speed integral, compensation integral.
    private final double[] state = new double[3];
    private double[] start, firstRate;
    private boolean initialized;

    public Wtptb1Model(Wtptb1Data data) { this.data = Objects.requireNonNull(data); }

    /** Balanced zero-error initialization; invalid initial limits are not silently expanded. */
    public void initialize(double initialPitch) {
        if (!Double.isFinite(initialPitch) || initialPitch < data.thetaMin()
                || initialPitch > data.thetaMax() || initialPitch < data.speedMin()
                || initialPitch > data.speedMax() || data.compensationMin() > 0
                || data.compensationMax() < 0)
            throw new IllegalArgumentException("WTPTB1 equilibrium is outside the declared state limits");
        state[0] = state[1] = initialPitch;
        state[2] = 0;
        start = firstRate = null;
        initialized = true;
    }

    /** Complete modified Euler for constant input signals over the step. */
    public void step(double dt, double pOrder, double pReference,
            double turbineSpeed, double speedReference) {
        step(dt, pOrder, pReference, turbineSpeed, speedReference, 0);
        step(dt, pOrder, pReference, turbineSpeed, speedReference, 1);
    }

    /** Explicit predictor/corrector, accepting distinct old/predicted input endpoints. */
    public void step(double dt, double pOrder, double pReference,
            double turbineSpeed, double speedReference, int flag) {
        if (!initialized) throw new IllegalStateException("WTPTB1 must be initialized");
        for (double value : new double[] {dt, pOrder, pReference, turbineSpeed, speedReference})
            if (!Double.isFinite(value)) throw new IllegalArgumentException("WTPTB1 inputs must be finite");
        if (dt < 0 || flag < 0 || flag > 1)
            throw new IllegalArgumentException("WTPTB1 requires dt >= 0 and stage 0 or 1");
        if (dt == 0) return;
        if (flag == 0) {
            start = state.clone();
            firstRate = rates(pOrder, pReference, turbineSpeed, speedReference);
            for (int i = 0; i < 3; i++) state[i] = start[i] + dt * firstRate[i];
        } else {
            if (start == null) throw new IllegalStateException("WTPTB1 corrector without predictor");
            double[] lastRate = rates(pOrder, pReference, turbineSpeed, speedReference);
            for (int i = 0; i < 3; i++) state[i] = start[i] + .5 * dt * (firstRate[i] + lastRate[i]);
            start = firstRate = null;
        }
        state[0] = clip(state[0], data.thetaMin(), data.thetaMax());
        state[1] = clip(state[1], data.speedMin(), data.speedMax());
        state[2] = clip(state[2], data.compensationMin(), data.compensationMax());
        for (double value : state) if (!Double.isFinite(value))
            throw new IllegalStateException("Nonfinite WTPTB1 state");
    }

    private double[] rates(double pOrder, double pReference, double speed, double reference) {
        double powerError = pOrder - pReference;
        double speedError = speed - reference + data.kcc() * powerError;
        double speedRate = boundedRate(state[1], data.kiw() * speedError, data.speedMin(), data.speedMax());
        double compensationRate = boundedRate(state[2], data.kic() * powerError,
                data.compensationMin(), data.compensationMax());
        // The integrator's own limit depends on its state, not PI output.
        // Even with clipped output it may integrate until the state bound.
        double speedOutput = clip(state[1] + data.kpw() * speedError, data.speedMin(), data.speedMax());
        double compensationOutput = clip(state[2] + data.kpc() * powerError,
                data.compensationMin(), data.compensationMax());
        // Do not clamp the summed command before the lag: the final angle
        // constraint acts on the output state and its outward derivative.
        double pitchRate = clip((speedOutput + compensationOutput - state[0]) / data.tp(),
                data.rateMin(), data.rateMax());
        pitchRate = boundedRate(state[0], pitchRate, data.thetaMin(), data.thetaMax());
        speedRate = boundedRate(state[0], speedRate, data.thetaMin(), data.thetaMax());
        compensationRate = boundedRate(state[0], compensationRate, data.thetaMin(), data.thetaMax());
        return new double[] {pitchRate, speedRate, compensationRate};
    }

    private static double boundedRate(double state, double rate, double minimum, double maximum) {
        return state >= maximum && rate > 0 || state <= minimum && rate < 0 ? 0 : rate;
    }
    private static double clip(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
    public Wtptb1Data getData() { return data; }
    public double getPitch() { return state[0]; }
    public double[] getNativeStates() { return state.clone(); }
    public Map<String, Double> getNamedStates() {
        Map<String, Double> named = new LinkedHashMap<>();
        named.put("Pitch Angle", state[0]);
        named.put("Pitch Control Integral", state[1]);
        named.put("Pitch Compensation Integral", state[2]);
        return Collections.unmodifiableMap(named);
    }
}
