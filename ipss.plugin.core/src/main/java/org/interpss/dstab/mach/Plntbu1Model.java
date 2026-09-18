package org.interpss.dstab.mach;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/**
 * Seven-state PLNTBU1 plant controller shared by the associated REAXB devices.
 * Outputs are incremental commands on the system MVA base.
 */
public final class Plntbu1Model implements ICMLStateProvider {
    private static final double EPS = 1.0e-12;

    private final Plntbu1Data data;
    private final BaseDStabBus<?, ?> attachmentBus;
    private final BaseDStabBus<?, ?> regulatedBus;
    private final AclfBranch monitoredBranch;
    private final boolean branchStoredForward;

    private double systemBaseMva = 100.0;
    private double modelBaseMva = 100.0;
    private Complex boundaryVoltage = Complex.ONE;
    private Complex boundaryBranchVoltage = Complex.ONE;
    private Complex boundaryPower = Complex.ZERO;
    private double boundaryFrequency = 1.0;
    private boolean syntheticBoundary;

    private double voltageReference;
    private double reactiveReference;
    private double powerFactorAngleReference;
    private double activeReference;
    private double frequencyReference;
    private double voltageAuxiliary;
    private double reactiveAuxiliary;
    private double activeAuxiliary;

    private double voltageMeasurement;
    private double reactiveMeasurement;
    private double reactiveIntegral;
    private double reactiveLeadLagState;
    private double activeMeasurement;
    private double activeIntegral;
    private double activeLagState;
    private double reactiveOutput;
    private double activeOutput;
    private State predictorStart;
    private Rates predictorRates;
    private boolean initialized;

    /** Standalone constructor for blockwise tests and signal play-in. */
    public Plntbu1Model(Plntbu1Data data) {
        this(data, null, null, null, true);
    }

    public Plntbu1Model(Plntbu1Data data, BaseDStabBus<?, ?> attachmentBus,
            BaseDStabBus<?, ?> regulatedBus, AclfBranch monitoredBranch,
            boolean branchStoredForward) {
        this.data = data;
        this.attachmentBus = attachmentBus;
        this.regulatedBus = regulatedBus;
        this.monitoredBranch = monitoredBranch;
        this.branchStoredForward = branchStoredForward;
        this.modelBaseMva = data.plantBaseMva() > EPS
                ? data.plantBaseMva() : systemBaseMva;
    }

    /** Set a model-base boundary for deterministic blockwise verification. */
    public void setBoundary(Complex regulatedVoltage, Complex branchVoltage,
            Complex plantBasePower, double frequency) {
        if (regulatedVoltage == null || branchVoltage == null || plantBasePower == null
                || !finite(regulatedVoltage.getReal(), regulatedVoltage.getImaginary(),
                        branchVoltage.getReal(), branchVoltage.getImaginary(),
                        plantBasePower.getReal(), plantBasePower.getImaginary(), frequency)) {
            throw new IllegalArgumentException("PLNTBU1 boundary must be finite");
        }
        boundaryVoltage = regulatedVoltage;
        boundaryBranchVoltage = branchVoltage;
        boundaryPower = plantBasePower;
        boundaryFrequency = frequency;
        syntheticBoundary = true;
    }

    public void setAuxiliaryInputs(double voltage, double reactive, double active) {
        if (!finite(voltage, reactive, active)) {
            throw new IllegalArgumentException("PLNTBU1 auxiliary inputs must be finite");
        }
        voltageAuxiliary = voltage;
        reactiveAuxiliary = reactive;
        activeAuxiliary = active;
    }

    public boolean initialize() {
        if (initialized) return true;
        if (!refreshBoundary()) return false;
        Measurement input = measurement();
        voltageMeasurement = input.voltageControl();
        reactiveMeasurement = input.reactivePower();
        activeMeasurement = input.activePower();
        voltageReference = voltageMeasurement;
        reactiveReference = reactiveMeasurement;
        powerFactorAngleReference = Math.atan2(reactiveMeasurement, activeMeasurement);
        activeReference = activeMeasurement;
        frequencyReference = input.frequency();
        reactiveIntegral = reactiveLeadLagState = activeIntegral = activeLagState = 0.0;
        reactiveOutput = activeOutput = 0.0;
        predictorStart = null;
        predictorRates = null;
        initialized = true;
        return finiteState();
    }

    /** Modified-Euler stage; repeated calls from other turbines are idempotent. */
    public boolean step(double dt, int flag) {
        if (!initialized || !Double.isFinite(dt) || dt <= 0.0
                || (flag != 0 && flag != 1)) return false;
        if (flag == 0 && predictorStart != null) return true;
        if (flag == 1 && predictorStart == null) return true;
        if (!refreshBoundary()) return false;
        Measurement endpoint = measurement();
        if (flag == 0) {
            predictorStart = state();
            predictorRates = rates(predictorStart, endpoint);
            apply(withBypasses(add(predictorStart, predictorRates, dt), endpoint));
        } else {
            Rates corrected = rates(state(), endpoint);
            apply(withBypasses(correct(predictorStart, predictorRates, corrected, dt), endpoint));
            predictorStart = null;
            predictorRates = null;
        }
        updateOutputs();
        return finiteState();
    }

    private boolean refreshBoundary() {
        if (syntheticBoundary) return true;
        if (attachmentBus == null || regulatedBus == null) return false;
        systemBaseMva = attachmentBus.getNetwork().getBaseMva();
        modelBaseMva = data.plantBaseMva() > EPS ? data.plantBaseMva() : systemBaseMva;
        boundaryVoltage = regulatedBus.getVoltage();
        boundaryFrequency = regulatedBus.getFreq();
        if (monitoredBranch == null) {
            boundaryBranchVoltage = attachmentBus.getVoltage();
            boundaryPower = Complex.ZERO;
            return true;
        }
        Complex systemPower = branchStoredForward
                ? monitoredBranch.powerFrom2To(UnitType.PU)
                : monitoredBranch.powerTo2From(UnitType.PU);
        BaseDStabBus<?, ?> terminal = (BaseDStabBus<?, ?>) (branchStoredForward
                ? monitoredBranch.getFromBus() : monitoredBranch.getToBus());
        boundaryBranchVoltage = terminal.getVoltage();
        boundaryPower = systemPower.multiply(systemBaseMva / modelBaseMva);
        return finite(boundaryVoltage.getReal(), boundaryVoltage.getImaginary(),
                boundaryPower.getReal(), boundaryPower.getImaginary(), boundaryFrequency);
    }

    private Measurement measurement() {
        double q = boundaryPower.getImaginary();
        double controlledVoltage;
        if (data.vcFlag() == 1) {
            Complex current = boundaryPower.divide(nonzero(boundaryBranchVoltage)).conjugate();
            controlledVoltage = boundaryVoltage.subtract(new Complex(
                    data.resistanceCompensation(), data.reactanceCompensation())
                    .multiply(current)).abs();
        } else {
            controlledVoltage = boundaryVoltage.abs() + data.reactiveDroop() * q;
        }
        return new Measurement(controlledVoltage, boundaryPower.getReal(), q,
                boundaryVoltage.abs(), boundaryFrequency);
    }

    private Rates rates(State state, Measurement input) {
        double voltageRate = lagRate(input.voltageControl(), state.voltageMeasurement(),
                data.voltageFilterTime());
        double reactiveRate = lagRate(input.reactivePower(), state.reactiveMeasurement(),
                data.voltageFilterTime());
        double qError = reactiveError(state, input);
        double qIntegralRate = integralRate(state.reactiveIntegral(),
                data.reactiveIntegralGain(), qError, data.reactiveProportionalGain(),
                data.reactiveOutputMinimum(), data.reactiveOutputMaximum(),
                input.terminalVoltage() < data.freezeVoltage());
        double qPi = clamp(data.reactiveProportionalGain() * qError
                        + state.reactiveIntegral(),
                data.reactiveOutputMinimum(), data.reactiveOutputMaximum());
        double qLeadLagRate = lagRate(qPi, state.reactiveLeadLagState(),
                data.reactiveLagTime());

        double activeRate = lagRate(input.activePower(), state.activeMeasurement(),
                data.activePowerFilterTime());
        double pError = activeError(state.activeMeasurement(), input.frequency());
        double pIntegralRate = integralRate(state.activeIntegral(),
                data.activeIntegralGain(), pError, data.activeProportionalGain(),
                data.activeOutputMinimum(), data.activeOutputMaximum(), false);
        double pPi = clamp(data.activeProportionalGain() * pError + state.activeIntegral(),
                data.activeOutputMinimum(), data.activeOutputMaximum());
        double pLagRate = lagRate(pPi, state.activeLagState(), data.activeOutputLagTime());
        return new Rates(voltageRate, reactiveRate, qIntegralRate, qLeadLagRate,
                activeRate, pIntegralRate, pLagRate);
    }

    private State withBypasses(State state, Measurement input) {
        double voltage = data.voltageFilterTime() <= EPS
                ? input.voltageControl() : state.voltageMeasurement();
        double reactive = data.voltageFilterTime() <= EPS
                ? input.reactivePower() : state.reactiveMeasurement();
        double active = data.activePowerFilterTime() <= EPS
                ? input.activePower() : state.activeMeasurement();
        State filtered = new State(voltage, reactive, state.reactiveIntegral(),
                state.reactiveLeadLagState(), active, state.activeIntegral(),
                state.activeLagState());
        double qPi = clamp(data.reactiveProportionalGain() * reactiveError(filtered, input)
                        + filtered.reactiveIntegral(),
                data.reactiveOutputMinimum(), data.reactiveOutputMaximum());
        double leadLag = data.reactiveLagTime() <= EPS
                ? qPi : filtered.reactiveLeadLagState();
        double pPi = clamp(data.activeProportionalGain()
                        * activeError(active, input.frequency()) + filtered.activeIntegral(),
                data.activeOutputMinimum(), data.activeOutputMaximum());
        double pLag = data.activeOutputLagTime() <= EPS ? pPi : filtered.activeLagState();
        return new State(voltage, reactive, filtered.reactiveIntegral(), leadLag,
                active, filtered.activeIntegral(), pLag);
    }

    private double reactiveError(State state, Measurement input) {
        double raw = switch (data.refFlag()) {
            case 0 -> reactiveReference - state.reactiveMeasurement() + reactiveAuxiliary;
            case 1 -> voltageReference - state.voltageMeasurement() + voltageAuxiliary;
            case 2 -> Math.tan(powerFactorAngleReference) * state.activeMeasurement()
                    - state.reactiveMeasurement() + reactiveAuxiliary;
            default -> throw new IllegalStateException("invalid PLNTBU1 RefFlag");
        };
        return clamp(deadband(raw, data.reactiveDeadbandLower(),
                data.reactiveDeadbandUpper()), data.reactiveErrorMinimum(),
                data.reactiveErrorMaximum());
    }

    private double activeError(double measuredPower, double frequency) {
        double frequencyContribution = 0.0;
        if (data.frequencyFlag() == 1) {
            double deviation = deadband(frequency - frequencyReference,
                    data.frequencyDeadbandLower(), data.frequencyDeadbandUpper());
            frequencyContribution = -(deviation >= 0.0
                    ? data.overFrequencyDroop() : data.underFrequencyDroop()) * deviation;
        }
        return clamp(activeReference - measuredPower + activeAuxiliary
                        + frequencyContribution,
                data.activeErrorMinimum(), data.activeErrorMaximum());
    }

    private void updateOutputs() {
        Measurement input = measurement();
        double qPi = clamp(data.reactiveProportionalGain() * reactiveError(state(), input)
                        + reactiveIntegral,
                data.reactiveOutputMinimum(), data.reactiveOutputMaximum());
        double qModel = data.reactiveLagTime() <= EPS ? qPi
                : data.reactiveLeadTime() / data.reactiveLagTime() * qPi
                        + (1.0 - data.reactiveLeadTime() / data.reactiveLagTime())
                        * reactiveLeadLagState;
        double scale = modelBaseMva / systemBaseMva;
        reactiveOutput = qModel * scale;
        activeOutput = activeLagState * scale;
    }

    private static double integralRate(double integral, double gain, double error,
            double proportionalGain, double lower, double upper, boolean frozen) {
        if (frozen) return 0.0;
        double output = proportionalGain * error + integral;
        if ((output >= upper && error > 0.0) || (output <= lower && error < 0.0)) return 0.0;
        return gain * error;
    }

    private static double deadband(double value, double lower, double upper) {
        if (value < lower) return value - lower;
        if (value > upper) return value - upper;
        return 0.0;
    }

    private static double lagRate(double input, double state, double time) {
        return time <= EPS ? 0.0 : (input - state) / time;
    }

    private static double clamp(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private static Complex nonzero(Complex value) {
        return value.abs() > EPS ? value : new Complex(EPS, 0.0);
    }

    private State state() {
        return new State(voltageMeasurement, reactiveMeasurement, reactiveIntegral,
                reactiveLeadLagState, activeMeasurement, activeIntegral, activeLagState);
    }

    private void apply(State state) {
        voltageMeasurement = state.voltageMeasurement();
        reactiveMeasurement = state.reactiveMeasurement();
        reactiveIntegral = state.reactiveIntegral();
        reactiveLeadLagState = state.reactiveLeadLagState();
        activeMeasurement = state.activeMeasurement();
        activeIntegral = state.activeIntegral();
        activeLagState = state.activeLagState();
    }

    private static State add(State state, Rates rate, double dt) {
        return new State(state.voltageMeasurement() + dt * rate.voltageMeasurement(),
                state.reactiveMeasurement() + dt * rate.reactiveMeasurement(),
                state.reactiveIntegral() + dt * rate.reactiveIntegral(),
                state.reactiveLeadLagState() + dt * rate.reactiveLeadLagState(),
                state.activeMeasurement() + dt * rate.activeMeasurement(),
                state.activeIntegral() + dt * rate.activeIntegral(),
                state.activeLagState() + dt * rate.activeLagState());
    }

    private static State correct(State start, Rates first, Rates second, double dt) {
        return new State(start.voltageMeasurement() + .5 * dt
                        * (first.voltageMeasurement() + second.voltageMeasurement()),
                start.reactiveMeasurement() + .5 * dt
                        * (first.reactiveMeasurement() + second.reactiveMeasurement()),
                start.reactiveIntegral() + .5 * dt
                        * (first.reactiveIntegral() + second.reactiveIntegral()),
                start.reactiveLeadLagState() + .5 * dt
                        * (first.reactiveLeadLagState() + second.reactiveLeadLagState()),
                start.activeMeasurement() + .5 * dt
                        * (first.activeMeasurement() + second.activeMeasurement()),
                start.activeIntegral() + .5 * dt
                        * (first.activeIntegral() + second.activeIntegral()),
                start.activeLagState() + .5 * dt
                        * (first.activeLagState() + second.activeLagState()));
    }

    private boolean finiteState() {
        return finite(voltageMeasurement, reactiveMeasurement, reactiveIntegral,
                reactiveLeadLagState, activeMeasurement, activeIntegral,
                activeLagState, reactiveOutput, activeOutput);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("Voltage measurement filter", voltageMeasurement);
        states.put("Reactive power measurement filter", reactiveMeasurement);
        states.put("Reactive power PI integrator", reactiveIntegral);
        states.put("Reactive power lead-lag state", reactiveLeadLagState);
        states.put("Real power measurement filter", activeMeasurement);
        states.put("Real power PI integrator", activeIntegral);
        states.put("Power controller output lag", activeLagState);
        return Map.copyOf(states);
    }

    public Plntbu1Data getData() { return data; }
    public double getReactiveOutput() { return reactiveOutput; }
    public double getActiveOutput() { return activeOutput; }
    public double getVoltageMeasurement() { return voltageMeasurement; }
    public double getReactiveMeasurement() { return reactiveMeasurement; }
    public double getReactiveIntegral() { return reactiveIntegral; }
    public double getReactiveLeadLagState() { return reactiveLeadLagState; }
    public double getActiveMeasurement() { return activeMeasurement; }
    public double getActiveIntegral() { return activeIntegral; }
    public double getActiveLagState() { return activeLagState; }

    private record Measurement(double voltageControl, double activePower,
            double reactivePower, double terminalVoltage, double frequency) { }
    private record State(double voltageMeasurement, double reactiveMeasurement,
            double reactiveIntegral, double reactiveLeadLagState,
            double activeMeasurement, double activeIntegral, double activeLagState) { }
    private record Rates(double voltageMeasurement, double reactiveMeasurement,
            double reactiveIntegral, double reactiveLeadLagState,
            double activeMeasurement, double activeIntegral, double activeLagState) { }
}
