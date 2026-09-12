package org.interpss.dstab.mach;

import java.util.LinkedHashMap;
import java.util.Map;

/** PSS/E WT12A1 pseudo-governor for Type-1/2 wind generators. */
public final class Wt12a1Model implements Wt12AerodynamicController {
    private final Wt12a1Data data;
    private double speedReference;
    private double powerReference;
    private double powerFilter;
    private double integrator;
    private double outputFilter1;
    private double outputFilter2;
    private State oldState;
    private Derivative oldDerivative;
    private boolean initialized;

    public Wt12a1Model(Wt12a1Data data) { this.data = data; }

    public void initialize(double electricalPower, double turbineSpeedDeviation,
            double aerodynamicPower) {
        speedReference = turbineSpeedDeviation;
        powerReference = powerFilter = electricalPower;
        integrator = outputFilter1 = outputFilter2 = aerodynamicPower;
        initialized = true;
    }

    @Override
    public void initialize(double electricalPower, double turbineSpeedDeviation,
            double aerodynamicPower, double terminalVoltage) {
        initialize(electricalPower, turbineSpeedDeviation, aerodynamicPower);
    }

    public void step(double dt, double electricalPower, double turbineSpeedDeviation, int flag) {
        if (!initialized) throw new IllegalStateException("WT12A1 is not initialized");
        if (flag != 0 && flag != 1) throw new IllegalArgumentException("flag must be 0 or 1");
        if (flag == 0) {
            oldState = state();
            oldDerivative = derivatives(oldState, electricalPower, turbineSpeedDeviation);
            apply(oldState.advance(dt, oldDerivative));
        } else {
            if (oldState == null) throw new IllegalStateException("corrector without predictor");
            Derivative corrected = derivatives(state(), electricalPower, turbineSpeedDeviation);
            apply(oldState.advance(0.5 * dt, oldDerivative.add(corrected)));
            oldState = null;
            oldDerivative = null;
        }
    }

    @Override
    public void step(double dt, double electricalPower, double turbineSpeedDeviation,
            double terminalVoltage, int flag) {
        step(dt, electricalPower, turbineSpeedDeviation, flag);
    }

    private Derivative derivatives(State state, double electricalPower,
            double turbineSpeedDeviation) {
        double error = speedReference - turbineSpeedDeviation
                + data.droop() * (powerReference - state.powerFilter);
        double unconstrained = state.integrator + data.kp() * error;
        double limited = Math.max(data.limitMin(), Math.min(data.limitMax(), unconstrained));
        double integralDerivative = error / data.ti();
        if ((unconstrained >= data.limitMax() && integralDerivative > 0.0)
                || (unconstrained <= data.limitMin() && integralDerivative < 0.0)) {
            integralDerivative = 0.0;
        }
        return new Derivative((electricalPower - state.powerFilter) / data.tp(),
                integralDerivative, (limited - state.outputFilter1) / data.t1(),
                (state.outputFilter1 - state.outputFilter2) / data.t2());
    }

    public Wt12a1Data getData() { return data; }
    public double getOutput() { return outputFilter2; }
    public double getSpeedReference() { return speedReference; }
    public double getPowerReference() { return powerReference; }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("Power filter", powerFilter);
        states.put("PI integrator", integrator);
        states.put("Output filter 1", outputFilter1);
        states.put("Output filter 2", outputFilter2);
        return Map.copyOf(states);
    }

    private State state() { return new State(powerFilter, integrator, outputFilter1, outputFilter2); }
    private void apply(State s) {
        powerFilter = s.powerFilter;
        integrator = s.integrator;
        outputFilter1 = s.outputFilter1;
        outputFilter2 = s.outputFilter2;
    }
    private record State(double powerFilter, double integrator,
            double outputFilter1, double outputFilter2) {
        State advance(double scale, Derivative d) {
            return new State(powerFilter + scale * d.powerFilter,
                    integrator + scale * d.integrator,
                    outputFilter1 + scale * d.outputFilter1,
                    outputFilter2 + scale * d.outputFilter2);
        }
    }
    private record Derivative(double powerFilter, double integrator,
            double outputFilter1, double outputFilter2) {
        Derivative add(Derivative o) {
            return new Derivative(powerFilter + o.powerFilter, integrator + o.integrator,
                    outputFilter1 + o.outputFilter1, outputFilter2 + o.outputFilter2);
        }
    }
}
