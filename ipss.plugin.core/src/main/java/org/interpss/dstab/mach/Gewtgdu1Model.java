package org.interpss.dstab.mach;

import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Deterministic wind-speed gust and ramp signal source. */
public final class Gewtgdu1Model implements ICMLStateProvider {
    private final Gewtgdu1Data data;
    private double initialWindSpeed;
    private double time;
    private double gust;
    private double ramp;
    private double windSpeed;
    private boolean initialized;

    public Gewtgdu1Model(Gewtgdu1Data data) { this.data = data; }

    public void initialize(double baseWindSpeed) {
        if (!Double.isFinite(baseWindSpeed) || baseWindSpeed <= 0.0) {
            throw new IllegalArgumentException("GEWTGDU1 base wind speed must be positive");
        }
        initialWindSpeed = windSpeed = baseWindSpeed;
        time = gust = ramp = 0.0;
        initialized = true;
    }

    /** Predictor evaluates the current boundary; corrector evaluates and commits the endpoint. */
    public void step(double dt, int flag) {
        if (!initialized) throw new IllegalStateException("GEWTGDU1 is not initialized");
        if (!Double.isFinite(dt) || dt < 0.0 || (flag != 0 && flag != 1)) {
            throw new IllegalArgumentException("invalid GEWTGDU1 step boundary");
        }
        double sampleTime = flag == 0 ? time : time + dt;
        gust = gustAt(sampleTime);
        ramp = rampAt(sampleTime);
        windSpeed = initialWindSpeed + gust + ramp;
        if (windSpeed <= 0.0) windSpeed = 1.0e-6;
        if (flag == 1) time = sampleTime;
    }

    public double gustAt(double sampleTime) {
        if (sampleTime < data.gustStart()
                || sampleTime > data.gustStart() + data.gustDuration()
                || data.gustDuration() == 0.0) return 0.0;
        double phase = 2.0 * Math.PI * (sampleTime - data.gustStart())
                / data.gustDuration();
        return 0.5 * data.gustMaximum() * (1.0 - Math.cos(phase));
    }

    public double rampAt(double sampleTime) {
        if (sampleTime <= data.rampStart()) return 0.0;
        if (sampleTime >= data.rampEnd()) return data.rampMaximum();
        return data.rampMaximum() * (sampleTime - data.rampStart())
                / (data.rampEnd() - data.rampStart());
    }

    public Gewtgdu1Data getData() { return data; }
    public double getTime() { return time; }
    public double getInitialWindSpeed() { return initialWindSpeed; }
    public double getGust() { return gust; }
    public double getRamp() { return ramp; }
    public double getWindSpeed() { return windSpeed; }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("Effective wind speed", windSpeed);
        values.put("Gust component", gust);
        values.put("Ramp component", ramp);
        values.put("Initial wind speed", initialWindSpeed);
        return Map.copyOf(values);
    }
}
