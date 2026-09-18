package org.interpss.dstab.mach;

import java.util.LinkedHashMap;
import java.util.Map;

/** Active-stall power-ramp controller defined by the WT1P_B/WT12A1B diagram. */
public final class Wt12a1bModel implements Wt12AerodynamicController {
    private static final double EPS = 1e-12;
    private final Wt12a1bData data;
    private double initialPower;
    private double rampState;
    private double outputState;
    private double voltageState;
    private double remainingTime;
    private State oldState;
    private Rates oldRates;
    private boolean rampingDown;
    private boolean armed;
    private boolean stageRampDown;
    private boolean initialized;

    public Wt12a1bModel(Wt12a1bData data) { this.data = data; }

    @Override
    public void initialize(double electricalPower, double turbineSpeedDeviation,
            double aerodynamicPower, double terminalVoltage) {
        if (!finite(electricalPower, aerodynamicPower, terminalVoltage)) {
            throw new IllegalArgumentException("WT12A1B initial boundary must be finite");
        }
        initialPower = aerodynamicPower;
        rampState = outputState = aerodynamicPower;
        voltageState = terminalVoltage;
        remainingTime = 0.0;
        rampingDown = false;
        armed = true;
        oldState = null;
        oldRates = null;
        initialized = true;
    }

    @Override
    public void step(double dt, double electricalPower, double turbineSpeedDeviation,
            double terminalVoltage, int flag) {
        if (!initialized || !finite(dt, terminalVoltage) || dt <= 0.0
                || (flag != 0 && flag != 1)) {
            throw new IllegalArgumentException("invalid WT12A1B step boundary");
        }
        if (flag == 0) {
            oldState = state();
            updateSwitch(oldState.voltage(), terminalVoltage);
            stageRampDown = rampingDown;
            oldRates = rates(oldState, terminalVoltage, stageRampDown);
            apply(withBypasses(add(oldState, oldRates, dt), terminalVoltage));
        } else {
            if (oldState == null) throw new IllegalStateException("corrector without predictor");
            Rates corrected = rates(state(), terminalVoltage, stageRampDown);
            apply(withBypasses(correct(oldState, oldRates, corrected, dt), terminalVoltage));
            if (stageRampDown) {
                remainingTime = Math.max(0.0, remainingTime - dt);
                if (remainingTime <= EPS) rampingDown = false;
            }
            oldState = null;
            oldRates = null;
        }
    }

    private void updateSwitch(double filteredVoltage, double terminalVoltage) {
        double sensed = data.voltageTime() <= EPS ? terminalVoltage : filteredVoltage;
        if (sensed >= data.voltage4()) {
            armed = true;
        } else if (armed && !rampingDown && initialPower >= data.powerThreshold()) {
            remainingTime = duration(sensed);
            rampingDown = remainingTime > EPS;
            armed = false;
        }
    }

    private Rates rates(State state, double terminalVoltage, boolean down) {
        double target = down ? data.minimumPower() : initialPower;
        double ramp = clamp(target - state.output(), data.rampDownRate(), data.rampUpRate());
        double output = data.outputTime() <= EPS ? 0.0
                : (state.ramp() - state.output()) / data.outputTime();
        double voltage = data.voltageTime() <= EPS ? 0.0
                : (terminalVoltage - state.voltage()) / data.voltageTime();
        return new Rates(ramp, output, voltage);
    }

    private State withBypasses(State state, double terminalVoltage) {
        return new State(state.ramp(), data.outputTime() <= EPS ? state.ramp() : state.output(),
                data.voltageTime() <= EPS ? terminalVoltage : state.voltage());
    }

    private double duration(double voltage) {
        if (voltage <= data.voltage1()) return data.duration1();
        if (voltage <= data.voltage2()) return data.duration2();
        if (voltage <= data.voltage3()) return data.duration3();
        return voltage < data.voltage4() ? data.duration4() : 0.0;
    }

    @Override public double getOutput() { return outputState; }
    @Override public double getSpeedReference() { return 0.0; }
    @Override public double getPowerReference() { return initialPower; }
    public double getRemainingTime() { return remainingTime; }
    public Wt12a1bData getData() { return data; }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("Mechanical power ramp integrator", rampState);
        states.put("Mechanical power output lag", outputState);
        states.put("Terminal voltage measurement filter", voltageState);
        return Map.copyOf(states);
    }

    private State state() { return new State(rampState, outputState, voltageState); }
    private void apply(State state) {
        rampState = state.ramp(); outputState = state.output(); voltageState = state.voltage();
    }
    private static State add(State s, Rates r, double dt) {
        return new State(s.ramp()+dt*r.ramp(), s.output()+dt*r.output(),
                s.voltage()+dt*r.voltage());
    }
    private static State correct(State s, Rates a, Rates b, double dt) {
        return new State(s.ramp()+.5*dt*(a.ramp()+b.ramp()),
                s.output()+.5*dt*(a.output()+b.output()),
                s.voltage()+.5*dt*(a.voltage()+b.voltage()));
    }
    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
    private record State(double ramp, double output, double voltage) { }
    private record Rates(double ramp, double output, double voltage) { }
}
