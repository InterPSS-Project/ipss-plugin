package org.interpss.dstab.control.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reusable observation buffers; maps are allocated only when a client reads. */
public final class IntegrationStateMonitor {
    public enum Stage {
        NOT_EVALUATED, INITIALIZATION, PREDICTOR, CORRECTOR, STAGGERED, TWO_STEP,
        /** Two-step rate averaged across an input discontinuity before integration. */
        TWO_STEP_EVENT,
        /** Average applied change per second, rather than an instantaneous RHS. */
        APPLIED_STEP
    }

    private final String[] names;
    private final double[] states;
    private final double[] rates;
    private Stage stage = Stage.NOT_EVALUATED;

    public IntegrationStateMonitor(String... names) {
        this.names = names.clone();
        this.states = new double[names.length];
        this.rates = new double[names.length];
        if (java.util.Arrays.stream(names).distinct().count() != names.length)
            throw new IllegalArgumentException("Duplicate integration coordinate");
    }

    public void reset() { stage = Stage.NOT_EVALUATED; }
    public void begin(Stage stage) { this.stage = java.util.Objects.requireNonNull(stage); }
    public void record(int index, double state, double rate) {
        states[index] = state;
        rates[index] = rate;
    }

    /** Retain the rate used by integration without replacing its evaluation state. */
    public void recordRate(int index, double rate) { rates[index] = rate; }

    /** Copy reusable observation storage for a model-local trial checkpoint. */
    public void copyObservationTo(IntegrationStateMonitor target) {
        java.util.Objects.requireNonNull(target);
        if(!java.util.Arrays.equals(names,target.names))
            throw new IllegalArgumentException("Matching integration-coordinate metadata required");
        System.arraycopy(states,0,target.states,0,states.length);
        System.arraycopy(rates,0,target.rates,0,rates.length);
        target.stage=stage;
    }

    public Snapshot snapshot() {
        var x = new LinkedHashMap<String, Double>();
        var dx = new LinkedHashMap<String, Double>();
        if (stage != Stage.NOT_EVALUATED) for (int i = 0; i < names.length; i++) {
            x.put(names[i], states[i]);
            dx.put(names[i], rates[i]);
        }
        return new Snapshot(stage, x, dx);
    }

    public record Snapshot(Stage stage, Map<String, Double> evaluationStates,
            Map<String, Double> rates) {
        public Snapshot {
            evaluationStates = Collections.unmodifiableMap(new LinkedHashMap<>(evaluationStates));
            rates = Collections.unmodifiableMap(new LinkedHashMap<>(rates));
        }
    }
}
