package org.interpss.dstab.renewable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Published WECC WTGIBFFR_A response, with machine-base power increments.
 * Native 36.7 automatic/too-short recovery discrepancies are not substituted
 * for the published energy balance. Native import/coupling is a separate layer.
 */
public final class WtgibffraModel {
    public enum Mode {
        READY(-1), RISE(1), PEAK(2), FALL(3), RECOVERY(4), COOLDOWN(-2), DISABLED(-3);
        private final int code;
        Mode(int code) { this.code = code; }
        public int code() { return code; }
    }

    private final WtgibffraData data;
    // Model Library order: rise, fall, recovery, frequency measurement.
    private final double[] state = new double[4];
    private final double[] rampRoundoff = new double[3];
    private double clockRoundoff;
    private final TwoStepHistory twoStep = new TwoStepHistory(4);
    private Mode mode = Mode.READY;
    private int selectedBand = -1;
    private double capturedPower, recoveryTime, peakStart, cooldownStart, clock;
    private Snapshot start;
    private double[] firstRate;
    private boolean initialized;

    public WtgibffraModel(WtgibffraData data) { this.data = Objects.requireNonNull(data); }

    public void initialize(double frequency) { initialize(frequency, 0); }

    public void initialize(double frequency, double initialTime) {
        validate(0, 0, frequency);
        if (!Double.isFinite(initialTime)) throw new IllegalArgumentException("WTGIBFFRA time must be finite");
        state[0] = state[1] = state[2] = 0;
        java.util.Arrays.fill(rampRoundoff, 0); clockRoundoff = 0;
        state[3] = data.enabled() ? frequency : 0;
        capturedPower = recoveryTime = peakStart = cooldownStart = 0;
        selectedBand = -1; clock = initialTime;
        mode = data.enabled() ? Mode.READY : Mode.DISABLED;
        start = null; firstRate = null; twoStep.reset(); initialized = true;
    }

    public void step(double dt, double power, double frequency) {
        step(dt, power, frequency, 0); step(dt, power, frequency, 1);
    }

    /** Two explicit modified-Euler stages; provisional endpoint events are rolled back. */
    public void step(double dt, double power, double frequency, int flag) {
        requireInitialized(); validate(dt, power, frequency);
        if (flag != 0 && flag != 1) throw new IllegalArgumentException("WTGIBFFRA stage must be 0 or 1");
        if (mode == Mode.DISABLED) return;
        if (dt == 0) { refreshFrequency(frequency); resolve(power); requireFinite(); return; }
        if (flag == 0) {
            refreshFrequency(frequency); resolve(power);
            start = snapshot(); firstRate = rates(frequency);
            for (int i = 0; i < 3; i++) addRamp(i, dt * firstRate[i]);
            state[3] = start.state()[3] + dt * firstRate[3];
            advanceClock(dt); refreshFrequency(frequency); resolve(power);
        } else {
            if (start == null) throw new IllegalStateException("WTGIBFFRA corrector without predictor");
            double lastFrequencyRate = frequencyRate(frequency);
            double correctedFrequency = start.state()[3] + .5 * dt * (firstRate[3] + lastFrequencyRate);
            // Each ramp has constant derivative during its sampled phase.
            // An endpoint phase transition cannot halve that preceding ramp.
            restore(start);
            for (int i = 0; i < 3; i++) addRamp(i, dt * firstRate[i]);
            state[3] = correctedFrequency;
            advanceClock(dt); refreshFrequency(frequency); resolve(power);
            start = null; firstRate = null;
        }
        requireFinite();
    }

    /** Opt-in explicit two-step path; algebraic refreshes consume no derivative history. */
    void stepTwoStep(double dt, double power, double frequency) {
        requireInitialized(); validate(dt, power, frequency);
        if (mode == Mode.DISABLED) return;
        refreshFrequency(frequency); resolve(power);
        if (dt == 0) return;
        double[] derivative = rates(frequency); twoStep.begin(dt);
        for (int i = 0; i < 4; i++) state[i] = derivative[i] == 0
                ? twoStep.hold(i, state[i]) : twoStep.advance(i, state[i], derivative[i]);
        java.util.Arrays.fill(rampRoundoff, 0);
        advanceClock(dt); refreshFrequency(frequency); resolve(power); requireFinite();
    }

    // Compensate repeated additions, not the integration method. Otherwise a
    // constant-rate ramp can miss its mathematical endpoint by an entire step.
    private void addRamp(int index, double increment) {
        double adjusted = increment - rampRoundoff[index];
        double sum = state[index] + adjusted;
        rampRoundoff[index] = (sum - state[index]) - adjusted;
        state[index] = sum;
    }

    private void advanceClock(double dt) {
        double adjusted = dt - clockRoundoff;
        double sum = clock + adjusted;
        clockRoundoff = (sum - clock) - adjusted;
        clock = sum;
    }

    /** Only floating-point endpoint equality, never a timestep-sized deadband. */
    private static boolean reached(double value, double endpoint) {
        return value >= endpoint || endpoint - value <= 4 * (Math.ulp(value) + Math.ulp(endpoint));
    }

    private double[] rates(double frequency) {
        double[] result = new double[4]; result[3] = frequencyRate(frequency);
        if (selectedBand < 0) return result;
        var b = data.bands().get(selectedBand);
        if (mode == Mode.RISE) result[0] = b.boost() * capturedPower / b.riseTime();
        if (mode == Mode.FALL) result[1] = -(b.boost() + b.deficit()) * capturedPower / b.fallTime();
        if (mode == Mode.RECOVERY && recoveryTime > 0) result[2] = b.deficit() * capturedPower / recoveryTime;
        return result;
    }

    private double frequencyRate(double frequency) {
        return data.frequencyFilterTime() == 0 ? 0 : (frequency - state[3]) / data.frequencyFilterTime();
    }

    private void refreshFrequency(double frequency) {
        if (data.frequencyFilterTime() == 0) state[3] = frequency;
    }

    private void resolve(double power) {
        if (mode == Mode.COOLDOWN) {
            // Nonpositive Tlapse means unavailable for the rest of the run.
            if (data.cooldownTime() > 0 && reached(clock, cooldownStart + data.cooldownTime())) {
                selectedBand = -1; capturedPower = recoveryTime = peakStart = cooldownStart = 0;
                mode = Mode.READY;
            }
            return; // Enter READY before a subsequent callback may initiate again.
        }
        if (mode == Mode.READY) {
            if (1 - state[3] < data.deadband()) return;
            int candidate = data.selectBand(power);
            if (candidate < 0) return;
            var b = data.bands().get(candidate);
            if (b.boost() <= 0 || b.deficit() <= 0 || b.riseTime() <= 0 || b.peakTime() < 0 || b.fallTime() <= 0) return;
            double recovery = b.effectiveRecoveryTime();
            for (double value : new double[]{b.boost() * power, (b.boost() + b.deficit()) * power,
                    b.deficit() * power, b.boost() * power / b.riseTime(),
                    (b.boost() + b.deficit()) * power / b.fallTime(),
                    recovery == 0 ? 0 : b.deficit() * power / recovery})
                if (!Double.isFinite(value)) throw new IllegalArgumentException("WTGIBFFRA response exceeds finite range");
            selectedBand = candidate; capturedPower = power;
            recoveryTime = recovery;
            state[0] = state[1] = state[2] = 0;
            java.util.Arrays.fill(rampRoundoff, 0);
            for (int i = 0; i < 3; i++) twoStep.hold(i, 0);
            peakStart = cooldownStart = 0; mode = Mode.RISE;
        }
        if (selectedBand < 0) return;
        var b = data.bands().get(selectedBand);
        double riseMaximum = b.boost() * capturedPower;
        double fallMinimum = -(b.boost() + b.deficit()) * capturedPower;
        double recoveryMaximum = b.deficit() * capturedPower;
        state[0] = Math.max(0, Math.min(riseMaximum, state[0]));
        state[1] = Math.max(fallMinimum, Math.min(0, state[1]));
        state[2] = Math.max(0, Math.min(recoveryMaximum, state[2]));
        if (mode == Mode.RISE && reached(state[0], riseMaximum)) {
            state[0] = riseMaximum; rampRoundoff[0] = 0;
            mode = Mode.PEAK; peakStart = clock; twoStep.hold(0, state[0]);
        }
        if (mode == Mode.PEAK && reached(clock, peakStart + b.peakTime())) {
            mode = Mode.FALL; peakStart = 0;
        }
        if (mode == Mode.FALL && reached(-state[1], -fallMinimum)) {
            state[1] = fallMinimum; rampRoundoff[1] = 0;
            mode = Mode.RECOVERY; twoStep.hold(1, state[1]);
            if (recoveryTime == 0) state[2] = recoveryMaximum;
        }
        if (mode == Mode.RECOVERY && reached(state[2], recoveryMaximum)) {
            state[0] = state[1] = state[2] = 0;
            java.util.Arrays.fill(rampRoundoff, 0);
            for (int i = 0; i < 3; i++) twoStep.hold(i, 0);
            peakStart = 0; cooldownStart = clock; mode = Mode.COOLDOWN;
        }
    }

    private Snapshot snapshot() {
        return new Snapshot(state.clone(), rampRoundoff.clone(), clockRoundoff, mode, selectedBand, capturedPower, recoveryTime, peakStart, cooldownStart, clock);
    }
    private void restore(Snapshot s) {
        System.arraycopy(s.state(), 0, state, 0, 4); mode = s.mode(); selectedBand = s.band();
        System.arraycopy(s.roundoff(), 0, rampRoundoff, 0, 3); clockRoundoff = s.clockRoundoff();
        capturedPower = s.power(); recoveryTime = s.recovery(); peakStart = s.peak(); cooldownStart = s.cooldown(); clock = s.clock();
    }
    private record Snapshot(double[] state, double[] roundoff, double clockRoundoff, Mode mode, int band, double power, double recovery, double peak, double cooldown, double clock) {}
    private static void validate(double dt, double power, double frequency) {
        if (!Double.isFinite(dt) || dt < 0 || !Double.isFinite(power) || !Double.isFinite(frequency) || frequency < 0)
            throw new IllegalArgumentException("WTGIBFFRA requires finite inputs, dt >= 0 and frequency >= 0");
    }
    private void requireInitialized() { if (!initialized) throw new IllegalStateException("WTGIBFFRA must be initialized"); }
    private void requireFinite() {
        for (double value : state) if (!Double.isFinite(value)) throw new IllegalStateException("Nonfinite WTGIBFFRA state");
        if (!Double.isFinite(clock) || !Double.isFinite(recoveryTime)) throw new IllegalStateException("Nonfinite WTGIBFFRA timing");
    }

    public WtgibffraData getData() { return data; }
    public Mode getMode() { return mode; }
    public int getSelectedBand() { return selectedBand; }
    public double getAuxiliaryPower() { return state[0] + state[1] + state[2]; }
    public double getCapturedPower() { return capturedPower; }
    public double getEffectiveRecoveryTime() { return recoveryTime; }
    public double[] getNativeStates() { return state.clone(); }

    /** Model Library VAR order; derived flags are not exact vendor callback storage. */
    public double[] getVariableSnapshot() {
        double[] vars = new double[17]; vars[16] = mode.code();
        if (mode == Mode.DISABLED) return vars;
        vars[0] = 1 - state[3]; vars[1] = capturedPower;
        if (selectedBand >= 0) {
            var b = data.bands().get(selectedBand);
            boolean active = mode != Mode.COOLDOWN;
            vars[2] = active ? 1 : 0;
            vars[3] = active && mode != Mode.RISE ? 1 : 0;
            vars[4] = mode == Mode.FALL || mode == Mode.RECOVERY ? 1 : 0;
            vars[5] = mode == Mode.RECOVERY ? 1 : 0;
            vars[6] = b.boost(); vars[7] = b.deficit(); vars[8] = b.riseTime(); vars[9] = b.peakTime();
            vars[10] = b.fallTime(); vars[11] = recoveryTime;
            vars[12] = (1 + b.boost()) * capturedPower; vars[13] = (1 - b.deficit()) * capturedPower;
            vars[14] = mode == Mode.PEAK ? peakStart : 0; vars[15] = mode == Mode.COOLDOWN ? cooldownStart : 0;
        }
        return vars;
    }

    public Map<String, Double> getNamedStates() {
        var values = new LinkedHashMap<String, Double>();
        values.put("Rise Power Increment", state[0]); values.put("Fall Power Increment", state[1]);
        values.put("Recovery Power Increment", state[2]); values.put("Filtered Frequency", state[3]);
        values.put("Auxiliary Active Power", getAuxiliaryPower()); values.put("Captured Generator Power", capturedPower);
        values.put("Effective Recovery Time", recoveryTime); values.put("Peak Start Time", peakStart);
        values.put("Cooldown Start Time", cooldownStart); values.put("Response Mode", (double) mode.code());
        return Collections.unmodifiableMap(values);
    }
}
