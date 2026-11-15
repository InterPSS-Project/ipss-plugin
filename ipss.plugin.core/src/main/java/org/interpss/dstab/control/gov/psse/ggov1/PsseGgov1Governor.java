package org.interpss.dstab.control.gov.psse.ggov1;

import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.IntegrationStateMonitor.Stage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;
import org.interpss.dstab.control.util.AsymmetricDeadbandBlock;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import org.interpss.numeric.datatype.Unit.UnitType;

/**
 * PSS/E GGOV1 general-purpose governor/turbine model.
 *
 * <p>The equations follow the PowerWorld GGOV1 block diagram and the OpenIPSL
 * decomposition.  The PI paths include the diagram's FSR tracking feedback so
 * controllers which lose the low-value select do not wind up. The diesel-engine
 * transport delay uses time-stamped interpolation, so its duration is independent
 * of the integration step size.</p>
 */
public class PsseGgov1Governor extends AbstractGovernor
        implements IntegrationStepAware, ICMLStateProvider, StaggeredEulerParticipant, IntegrationStateAccess {
    private static final double EPS = 1.0e-9;

    private final IntegrationStateMonitor stateMonitor = new IntegrationStateMonitor(
            "Electrical Power", "Derivative Lag", "Governor Integral", "Valve Position", "Turbine Lag", "Load Limiter Integral", "Supervisory Load Control", "Acceleration Lag", "Temperature Lead Lag", "Temperature Lag");
    private State state = State.zero();
    private State oldState = State.zero();
    private Derivatives oldDerivatives = Derivatives.zero();
    private double pref;
    private double pmwset;
    private double effectiveVmax;
    private double effectiveVmin;
    private double effectiveMaxerr;
    private double effectiveMinerr;
    private double effectiveRopen;
    private double effectiveRclose;
    private double effectiveRup;
    private double effectiveRdown;
    private double effectiveTpelec;
    private double effectiveTb;
    private double effectiveTa;
    private double effectiveTsb;
    private double effectiveTfload;
    private double effectiveKturb;
    private double effectiveKpgov;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double governorToMachineBase = 1.0;
    private double committedFsr;
    private double currentFsr;
    private double currentOutput;
    private boolean initialized;
    private final Deque<DelaySample> engineHistory = new ArrayDeque<>();
    private double simulationTime;

    public PsseGgov1Governor(String id, String name, String category) {
        super(id, name, category);
        _data = new PsseGgov1GovernorData();
    }

    public PsseGgov1GovernorData getData() {
        return (PsseGgov1GovernorData) _data;
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine mach) {
        initialized = false;
        stateMonitor.reset();
        if (!validateParameters()) return false;
        prepareEffectiveParameters();

        double machineMva = mach.getRating(UnitType.mVA, bus.getNetwork().getBaseKva());
        governorToMachineBase = getData().getTrate() > EPS && machineMva > EPS
                ? getData().getTrate() / machineMva : 1.0;
        double pm0 = mach.getPm() / governorToMachineBase;
        double pe0 = measuredMachinePower(mach) / governorToMachineBase;
        double speed = mach.getSpeed();
        double speedDeviation = speed - 1.0;
        double damping0 = damping(speedDeviation);
        double valve0 = getData().getWfnl() + (pm0 + damping0) / effectiveKturb;

        double[] valveLimits = normalizedValveLimits(getData().getVmax(), getData().getVmin());
        boolean declaredLimits = getData().isUseDeclaredValveLimits();
        effectiveVmax = declaredLimits ? valveLimits[0] : Math.max(valveLimits[0], valve0);
        effectiveVmin = declaredLimits ? valveLimits[1] : Math.min(valveLimits[1], valve0);
        double actuator0 = clamp(valve0, effectiveVmin, effectiveVmax);
        double droop0 = selectedDroop(pe0, actuator0, actuator0);
        pref = speedDeviation + getData().getR() * droop0;
        pmwset = pe0;

        double tempInput0 = valve0 * maximumPowerFactor(speedDeviation);
        // Keep the turbine and measurement history at the supplied operating
        // point. A limited actuator produces a real startup transient instead
        // of widening the declared travel range to manufacture equilibrium.
        // With Kpload = 0 the load path is a straight integrator initialized
        // at the normalized upper selector input. Seeding it from the initial
        // valve stroke would impose an artificial ceiling when Kiload is zero.
        // A nonzero Kiload can still move this selector input after initialization.
        double loadIntegrator0 = getData().getKpload() == 0.0 ? 1.0 : actuator0;
        state = new State(pe0, 0.0, valve0, actuator0, pm0 + damping0,
                loadIntegrator0, 0.0, speedDeviation, tempInput0, tempInput0);
        oldState = state;
        committedFsr = actuator0;
        currentFsr = actuator0;
        currentOutput = pm0;
        simulationTime = 0.0;
        engineHistory.clear();
        engineHistory.addLast(new DelaySample(0.0, pm0 + damping0));
        initialized = true;
        return true;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine mach, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) {
            throw new InterpssRuntimeException("GGOV1 supports MODIFIED_EULER only");
        }
        if (!initialized) return false;

        if (flag == 0) {
            oldState = state;
            Algebraic a = algebraic(oldState, dt, 0.0, committedFsr);
            oldDerivatives = derivatives(oldState, a);
            retainRates(oldState, oldDerivatives, Stage.PREDICTOR);
            state = oldState.plus(oldDerivatives, dt);
            Algebraic predicted = algebraic(state, dt, dt, committedFsr);
            currentFsr = predicted.fsr;
            currentOutput = predicted.pmech;
        } else if (flag == 1) {
            Algebraic predicted = algebraic(state, dt, dt, currentFsr);
            Derivatives correctedDerivatives = derivatives(state, predicted);
            retainRates(state, correctedDerivatives, Stage.CORRECTOR);
            state = oldState.plusAverage(oldDerivatives, correctedDerivatives, dt);
            Algebraic corrected = algebraic(state, dt, dt, committedFsr);
            currentFsr = corrected.fsr;
            committedFsr = currentFsr;
            currentOutput = corrected.pmech;
            simulationTime += dt;
            engineHistory.addLast(new DelaySample(simulationTime, corrected.rawTurbineInput));
            trimEngineHistory(dt);
        } else {
            throw new InterpssRuntimeException("GGOV1 invalid integration flag: " + flag);
        }
        return true;
    }

    @Override
    public double getOutput(Machine mach) {
        return currentOutput * governorToMachineBase;
    }

    @Override public int staggeredStateCount() { return 10; }

    @Override public void readStaggeredState(double[] values, int offset) {
        values[offset] = state.peMeasured;
        values[offset+1] = state.derivativeLag;
        values[offset+2] = state.governorIntegrator;
        values[offset+3] = state.valve;
        values[offset+4] = state.turbineLag;
        values[offset+5] = state.loadIntegrator;
        values[offset+6] = state.mwIntegrator;
        values[offset+7] = state.accelerationLag;
        values[offset+8] = state.temperatureLeadLag;
        values[offset+9] = state.temperatureLag;
    }

    @Override public void evaluateStaggered(double stepSec, double[] rates, int offset) {
        if (!initialized) throw new IllegalStateException("Initialize GGOV1 before evaluating derivatives");
        Algebraic a = algebraic(state, stepSec, 0.0, committedFsr);
        Derivatives d = derivatives(state, a);
        retainRates(state, d, Stage.STAGGERED);
        currentFsr = a.fsr;
        currentOutput = a.pmech;
        rates[offset] = d.peMeasured;
        rates[offset+1] = d.derivativeLag;
        rates[offset+2] = d.governorIntegrator;
        rates[offset+3] = d.valve;
        rates[offset+4] = d.turbineLag;
        rates[offset+5] = d.loadIntegrator;
        rates[offset+6] = d.mwIntegrator;
        rates[offset+7] = d.accelerationLag;
        rates[offset+8] = d.temperatureLeadLag;
        rates[offset+9] = d.temperatureLag;
    }

    @Override public void applyStaggeredState(double[] values, int offset, double stepSec) {
        state = new State(values[offset], values[offset+1], values[offset+2], values[offset+3],
                values[offset+4], values[offset+5], values[offset+6], values[offset+7],
                values[offset+8], values[offset+9]);
    }

    @Override public void completeStaggeredStep(double stepSec) {
        Algebraic a = algebraic(state, stepSec, stepSec, committedFsr);
        // Endpoint refresh evaluates the next selector from this derivative-endpoint value.
        committedFsr = currentFsr;
        currentOutput = a.pmech;
        simulationTime += stepSec;
        engineHistory.addLast(new DelaySample(simulationTime, a.rawTurbineInput));
        trimEngineHistory(stepSec);
    }

    @Override
    public void setRefPoint(double value) {
        pref = value / governorToMachineBase;
    }

    /** Reference in the external setter's coordinate, not electrical power.
     * Secondary controls must retain the initialized droop reference before
     * adding a bias; substituting Pe would introduce an unrequested load step. */
    public double getRefPoint() { return pref * governorToMachineBase; }

    public double getValveStroke() { return clamp(state.valve, effectiveVmin, effectiveVmax); }
    public double getFsr() { return currentFsr; }
    public double getMeasuredElectricalPower() { return state.peMeasured; }
    public double getDifferentialControlState() { return state.derivativeLag; }
    /** Output of the {@code s*Kdgov/(1+s*Tdgov)} differential-control block. */
    public double getDifferentialControlOutput() {
        return getData().getTdgov() > EPS
                ? getData().getKdgov() / getData().getTdgov()
                        * (currentGovernorError() - state.derivativeLag)
                : 0.0;
    }
    public double getGovernorIntegralState() { return state.governorIntegrator; }
    public double getTurbineLeadLagState() { return state.turbineLag; }
    public double getLoadLimiterMeasurementState() { return state.temperatureLag; }
    public double getLoadLimiterIntegralState() { return state.loadIntegrator; }
    public double getSupervisoryLoadControlState() { return state.mwIntegrator; }
    public double getAccelerationControlState() { return state.accelerationLag; }
    /** Stored acceleration-control contribution exposed by PowerWorld state 9. */
    public double getAccelerationControlOutput() {
        return -getData().getKa() * state.accelerationLag;
    }
    public double getTemperatureDetectionLeadLagState() { return state.temperatureLeadLag; }
    /** Output of the {@code (1+s*Tsa)/(1+s*Tsb)} temperature detector. */
    public double getTemperatureDetectionLeadLagOutput() {
        double speedDeviation = getMachine().getSpeed() - 1.0;
        double temperatureInput = getFuelFlow() * maximumPowerFactor(speedDeviation);
        return effectiveTsb > EPS
                ? state.temperatureLeadLag + getData().getTsa() / effectiveTsb
                        * (temperatureInput - state.temperatureLeadLag)
                : temperatureInput;
    }

    @Override public Map<String, Double> getNamedStates() {
        return Map.of(
                "Electrical Power", getMeasuredElectricalPower(),
                "Derivative Control", getDifferentialControlOutput(),
                "Governor Integral", getGovernorIntegralState(),
                "Valve Stroke", getValveStroke(),
                "Turbine Lead-Lag", getTurbineLeadLagState(),
                "Load Limiter Measurement", getLoadLimiterMeasurementState(),
                "Load Limiter Integral", getLoadLimiterIntegralState(),
                "Supervisory Load Control", getSupervisoryLoadControlState(),
                "Acceleration Control", getAccelerationControlOutput(),
                "Temperature Detection", getTemperatureDetectionLeadLagOutput());
    }

    @Override public java.util.Map<String, Double> getIntegrationStates() {
        return java.util.Map.of("Electrical Power", state.peMeasured,
                "Derivative Lag", state.derivativeLag,
                "Governor Integral", state.governorIntegrator,
                "Valve Position", state.valve,
                "Turbine Lag", state.turbineLag,
                "Load Limiter Integral", state.loadIntegrator,
                "Supervisory Load Control", state.mwIntegrator,
                "Acceleration Lag", state.accelerationLag,
                "Temperature Lead Lag", state.temperatureLeadLag,
                "Temperature Lag", state.temperatureLag);
    }
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot() {
        return stateMonitor.snapshot();
    }
    private void retainRates(State s, Derivatives d, Stage stage) {
        stateMonitor.begin(stage);
        stateMonitor.record(0, s.peMeasured, d.peMeasured);
        stateMonitor.record(1, s.derivativeLag, d.derivativeLag);
        stateMonitor.record(2, s.governorIntegrator, d.governorIntegrator);
        stateMonitor.record(3, s.valve, d.valve);
        stateMonitor.record(4, s.turbineLag, d.turbineLag);
        stateMonitor.record(5, s.loadIntegrator, d.loadIntegrator);
        stateMonitor.record(6, s.mwIntegrator, d.mwIntegrator);
        stateMonitor.record(7, s.accelerationLag, d.accelerationLag);
        stateMonitor.record(8, s.temperatureLeadLag, d.temperatureLeadLag);
        stateMonitor.record(9, s.temperatureLag, d.temperatureLag);
    }
    public double getDroopFeedback() {
        return selectedDroop(state.peMeasured, getValveStroke(), currentFsr);
    }
    public double getFuelFlow() {
        double speedDeviation = getMachine().getSpeed() - 1.0;
        return (getData().getFlag() == 1 ? 1.0 + speedDeviation : 1.0)
                * getValveStroke();
    }
    public double getEffectiveMaxerr() { return effectiveMaxerr; }
    public double getEffectiveMinerr() { return effectiveMinerr; }
    /** Apply an explicit initial travel cap after numerical initialization. Only the
     * effective upper bound changes: entered constants, states, reference, output,
     * predictor/corrector memory and any delay history remain untouched. The caller
     * owns the pre-integration lifecycle and source-located policy report. */
    public void applyInitialUpperTravelLimit(double value) {
        if (!(initialized)) throw new IllegalStateException("Apply travel policy after initialization at an idle boundary");
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Travel cap must be finite");
        effectiveVmax = value;
    }
    public double getEffectiveVmax() { return effectiveVmax; }
    public double getEffectiveVmin() { return effectiveVmin; }
    public double getEffectiveRopen() { return effectiveRopen; }
    public double getEffectiveRclose() { return effectiveRclose; }
    public double getEffectiveRup() { return effectiveRup; }
    public double getEffectiveRdown() { return effectiveRdown; }
    public double getEffectiveTpelec() { return effectiveTpelec; }
    public double getEffectiveTb() { return effectiveTb; }
    public double getEffectiveTa() { return effectiveTa; }
    public double getEffectiveTsb() { return effectiveTsb; }
    public double getEffectiveTfload() { return effectiveTfload; }
    public double getEffectiveKturb() { return effectiveKturb; }
    public double getEffectiveKpgov() { return effectiveKpgov; }
    public double getGovernorBaseMva(Machine mach) {
        return governorToMachineBase * mach.getRating(UnitType.mVA,
                mach.getDStabBus().getNetwork().getBaseKva());
    }

    /**
     * Supplies the integration step used by the PowerWorld/PSS/E validation
     * autocorrections. The default minimum-time-constant multiplier is one.
     */
    @Override
    public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec,
            double minimumTimeConstantMultiplier) {
        if (!Double.isFinite(timeStepSec) || timeStepSec < 0.0) {
            throw new IllegalArgumentException("timeStepSec must be finite and non-negative");
        }
        if (!Double.isFinite(minimumTimeConstantMultiplier)
                || minimumTimeConstantMultiplier < 0.0) {
            throw new IllegalArgumentException(
                    "minimumTimeConstantMultiplier must be finite and non-negative");
        }
        this.integrationStep = timeStepSec;
        this.minimumTimeConstantMultiplier = minimumTimeConstantMultiplier;
    }

    /** Apply the GGOV1D deadband to the input speed deviation. */
    public double applyFrequencyDeadband(double speedDeviation) {
        return AsymmetricDeadbandBlock.apply(
                speedDeviation, getData().getDbH(), getData().getDbL());
    }

    /** True when the parameter set is represented without a silent approximation. */
    public boolean validateParameters() {
        PsseGgov1GovernorData d = getData();
        boolean validSelector = d.getRselect() == 1 || d.getRselect() == 0
                || d.getRselect() == -1 || d.getRselect() == -2;
        return validSelector && (d.getFlag() == 0 || d.getFlag() == 1)
                // Droop is a multiplier, never a divisor: zero disables feedback.
                && d.getR() >= 0.0
                && d.getTpelec() >= 0.0 && d.getTdgov() >= 0.0
                // Tc is a signed lead coefficient, not a lag time constant.
                && d.getTact() > EPS && d.getTb() >= 0.0 && Double.isFinite(d.getTc())
                && (d.getTb() > EPS || Math.abs(d.getTc()) <= EPS)
                && d.getTeng() >= 0.0 && d.getTfload() >= 0.0
                && d.getTa() >= 0.0 && d.getTsa() >= 0.0 && d.getTsb() >= 0.0
                // A zero opening rate is an active directional stop, not missing data.
                && d.getRopen() >= 0.0 && d.getRclose() < 0.0
                && d.getKturb() > EPS && d.getTrate() >= 0.0
                && d.getDbH() >= 0.0 && d.getDbL() <= 0.0
                && d.getDbL() <= d.getDbH();
    }

    private Algebraic algebraic(State s, double stepSize, double evaluationOffset,
            double trackingFsr) {
        PsseGgov1GovernorData d = getData();
        double speedDeviation = getMachine().getSpeed() - 1.0;
        double pe = measuredMachinePower(getMachine()) / governorToMachineBase;
        double peMeasured = effectiveTpelec > EPS ? s.peMeasured : pe;
        double valve = clamp(s.valve, effectiveVmin, effectiveVmax);
        double droop = selectedDroop(peMeasured, valve, trackingFsr);
        double frequencySignal = applyFrequencyDeadband(speedDeviation);
        double error = clamp(deadband(pref + s.mwIntegrator - frequencySignal - d.getR() * droop),
                effectiveMinerr, effectiveMaxerr);
        double derivative = d.getTdgov() > EPS ? d.getKdgov() / d.getTdgov() * (error - s.derivativeLag) : 0.0;
        double fsrn = effectiveKpgov * error + derivative + s.governorIntegrator;

        double speedFactor = maximumPowerFactor(speedDeviation);
        double fuelFlow = (d.getFlag() == 1 ? 1.0 + speedDeviation : 1.0) * valve;
        double rawTurbineInput = effectiveKturb * (fuelFlow - d.getWfnl());
        double turbineInput = delayedEngineInput(rawTurbineInput, evaluationOffset);
        double turbinePower = effectiveTb > EPS
                ? s.turbineLag + d.getTc() / effectiveTb * (turbineInput - s.turbineLag)
                : turbineInput;
        double pmech = turbinePower - damping(speedDeviation);

        double temperatureInput = fuelFlow * speedFactor;
        double temperatureLeadLag = effectiveTsb > EPS
                ? s.temperatureLeadLag + d.getTsa() / effectiveTsb
                        * (temperatureInput - s.temperatureLeadLag)
                : temperatureInput;
        double temperatureMeasured = effectiveTfload > EPS
                ? s.temperatureLag : temperatureLeadLag;
        double loadError = loadSetpoint() - temperatureMeasured;
        double fsrt = d.getKpload() * loadError + s.loadIntegrator;

        double acceleration = effectiveTa > EPS
                ? (speedDeviation - s.accelerationLag) / effectiveTa : 0.0;
        // At zero acceleration gain the reference model excludes this path
        // from the low selector. Reusing the preceding FSR would impose a
        // one-way valve command that can fall but can never recover.
        double fsra = d.getKa() == 0.0 ? 1.0
                : trackingFsr + d.getKa() * stepSize * (d.getAset() - acceleration);
        double fsr = clamp(Math.min(1.0, Math.min(fsrn, Math.min(fsrt, fsra))),
                effectiveVmin, effectiveVmax);
        return new Algebraic(pe, peMeasured, error, fsrn, fsrt, fsr, valve, rawTurbineInput, turbineInput,
                temperatureInput, temperatureLeadLag, loadError, pmech);
    }

    private double delayedEngineInput(double currentInput, double evaluationOffset) {
        double delay = getData().getTeng();
        if (delay <= EPS) return currentInput;
        double target = simulationTime + evaluationOffset - delay;
        DelaySample first = engineHistory.getFirst();
        if (target <= first.time) return first.value;
        DelaySample previous = first;
        for (DelaySample sample : engineHistory) {
            if (sample.time >= target) return interpolate(previous, sample, target);
            previous = sample;
        }
        DelaySample future = new DelaySample(simulationTime + evaluationOffset, currentInput);
        return interpolate(previous, future, target);
    }

    private void trimEngineHistory(double dt) {
        double retainAfter = simulationTime - getData().getTeng() - Math.max(dt, EPS);
        while (engineHistory.size() > 2) {
            DelaySample first = engineHistory.removeFirst();
            if (engineHistory.getFirst().time >= retainAfter) {
                engineHistory.addFirst(first);
                break;
            }
        }
    }

    private static double interpolate(DelaySample lower, DelaySample upper, double time) {
        if (upper.time <= lower.time + EPS) return upper.value;
        double fraction = clamp((time - lower.time) / (upper.time - lower.time), 0.0, 1.0);
        return lower.value + fraction * (upper.value - lower.value);
    }

    private Derivatives derivatives(State s, Algebraic a) {
        PsseGgov1GovernorData d = getData();
        double peDot = effectiveTpelec > EPS
                ? (a.pe - s.peMeasured) / effectiveTpelec : 0.0;
        double derivativeDot = d.getTdgov() > EPS ? (a.error - s.derivativeLag) / d.getTdgov() : 0.0;
        // Track the fuel command selected by all three control paths. With the
        // speed path selected, this still includes the filtered derivative term
        // in addition to proportional error. Using only the PI error when the
        // selector agrees with the speed path drops that part of the response.
        // The zero-proportional-gain path is a straight error integrator.
        double governorIntegralDot = Math.abs(effectiveKpgov) > EPS
                ? d.getKigov() / effectiveKpgov * (a.fsr - s.governorIntegrator)
                : d.getKigov() * a.error;
        double valveDot = clamp((a.fsr - a.valve) / d.getTact(),
                effectiveRclose, effectiveRopen);
        double turbineDot = effectiveTb > EPS
                ? (a.turbineInput - s.turbineLag) / effectiveTb : 0.0;
        double loadIntegralDot = d.getKiload() * a.loadError;
        if (Math.abs(d.getKpload()) > EPS) {
            loadIntegralDot += d.getKiload() / d.getKpload() * (a.fsr - a.fsrt);
        }
        // GGOV1 uses the electrical-power transducer output for supervisory MW
        // feedback. With the transducer bypassed this equals instantaneous power.
        double mwDot = d.getKimw() * (pmwset - a.peMeasured);
        double mwLimit = 1.1 * Math.abs(d.getR());
        if ((s.mwIntegrator >= mwLimit && mwDot > 0.0)
                || (s.mwIntegrator <= -mwLimit && mwDot < 0.0)) mwDot = 0.0;
        double speedDeviation = getMachine().getSpeed() - 1.0;
        double accelerationDot = effectiveTa > EPS
                ? (speedDeviation - s.accelerationLag) / effectiveTa : 0.0;
        double temperatureLeadDot = effectiveTsb > EPS
                ? (a.temperatureInput - s.temperatureLeadLag) / effectiveTsb : 0.0;
        double temperatureLagDot = effectiveTfload > EPS
                ? (a.temperatureLeadLag - s.temperatureLag) / effectiveTfload : 0.0;
        return new Derivatives(peDot, derivativeDot, governorIntegralDot, valveDot,
                turbineDot, loadIntegralDot, mwDot, accelerationDot,
                temperatureLeadDot, temperatureLagDot);
    }

    private static double measuredMachinePower(Machine machine) {
        // The MW transducer senses terminal active power on the machine base.
        // The stator cache avoids a second evaluation that would change the
        // electrical power or torque used by the swing equation at this stage.
        return machine instanceof RoundRotorMachineImpl rotor
                ? rotor.getTerminalActivePower() : machine.getPe();
    }

    private double selectedDroop(double peMeasured, double valve, double governorOutput) {
        return switch (getData().getRselect()) {
            case 1 -> peMeasured;
            case -1 -> valve;
            case -2 -> governorOutput;
            default -> 0.0;
        };
    }

    private double currentGovernorError() {
        double speedDeviation = getMachine().getSpeed() - 1.0;
        double pe = measuredMachinePower(getMachine()) / governorToMachineBase;
        double peMeasured = effectiveTpelec > EPS ? state.peMeasured : pe;
        double droop = selectedDroop(peMeasured, getValveStroke(), currentFsr);
        double frequencySignal = applyFrequencyDeadband(speedDeviation);
        return clamp(deadband(pref + state.mwIntegrator - frequencySignal
                - getData().getR() * droop), effectiveMinerr, effectiveMaxerr);
    }

    private double damping(double speedDeviation) {
        double dm = getData().getDm();
        // Positive Dm is a power-versus-speed slope, measured from nominal
        // speed: Pm = Pturb - Dm * (omega - 1). Negative Dm instead selects
        // the fuel-limit characteristic below; it contributes no mechanical
        // damping. Applying it as a negative power offset at omega=1 can put
        // the inverse-initialized valve below zero, clip the actuator, and
        // create a large artificial pulse through the turbine lead-lag.
        return dm > 0.0 ? dm * speedDeviation : 0.0;
    }

    private double maximumPowerFactor(double speedDeviation) {
        double dm = getData().getDm();
        double absoluteSpeed = Math.max(EPS, 1.0 + speedDeviation);
        // Fuel-flow speed dependence is already selected by Flag. Only negative
        // Dm adds a speed characteristic to the temperature/load-limit branch.
        return dm >= 0.0 ? 1.0 : Math.pow(absoluteSpeed, dm);
    }

    private double loadSetpoint() {
        return getData().getLdref() / effectiveKturb + getData().getWfnl();
    }

    private void prepareEffectiveParameters() {
        PsseGgov1GovernorData d = getData();
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        effectiveTpelec = correctedTimeConstant(d.getTpelec(), minimum);
        effectiveTb = correctedTimeConstant(d.getTb(), minimum);
        effectiveTa = correctedTimeConstant(d.getTa(), minimum);
        effectiveTsb = correctedTimeConstant(d.getTsb(), minimum);
        effectiveTfload = correctedTimeConstant(d.getTfload(), minimum);
        effectiveKturb = d.getKturb() > 0.0 && d.getKturb() < minimum
                ? minimum : d.getKturb();
        effectiveKpgov = Math.abs(d.getKigov()) > EPS
                        && d.getKpgov() / d.getKigov() < minimum
                ? 0.0 : d.getKpgov();
        double[] errorLimits = normalizedSignedLimits(d.getMaxerr(), d.getMinerr());
        effectiveMaxerr = errorLimits[0];
        effectiveMinerr = errorLimits[1];
        effectiveRopen = d.isUseDeclaredValveRates() ? d.getRopen() : Math.min(d.getRopen(), 0.1);
        effectiveRclose = d.isUseDeclaredValveRates() ? d.getRclose()
                : d.getRclose() > -0.1 ? -0.1 : d.getRclose();
        effectiveRup = Math.min(d.getRup(), 99.0);
        effectiveRdown = d.getRdown() > -99.0 ? -99.0 : d.getRdown();
    }

    private static double correctedTimeConstant(double value, double minimum) {
        if (value > 0.0 && value < 0.5 * minimum) return 0.0;
        if (value > 0.5 * minimum && value < minimum) return minimum;
        return value;
    }

    private double deadband(double value) {
        double db = getData().getDb();
        if (db <= EPS) return value;
        if (value > db) return value - db;
        if (value < -db) return value + db;
        return 0.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double[] normalizedSignedLimits(double max, double min) {
        if (max < min) {
            double swap = max;
            max = min;
            min = swap;
        }
        if (max < 0.0) max = -max;
        if (min > 0.0) min = -min;
        return new double[] {max, min};
    }

    private static double[] normalizedValveLimits(double max, double min) {
        if (max < min) {
            double swap = max;
            max = min;
            min = swap;
        }
        return new double[] {Math.min(max, 1.0), Math.max(min, 0.0)};
    }

    private record Algebraic(double pe, double peMeasured, double error, double fsrn, double fsrt,
            double fsr, double valve, double rawTurbineInput, double turbineInput, double temperatureInput,
            double temperatureLeadLag, double loadError, double pmech) { }

    private record DelaySample(double time, double value) { }

    private record Derivatives(double peMeasured, double derivativeLag,
            double governorIntegrator, double valve, double turbineLag,
            double loadIntegrator, double mwIntegrator, double accelerationLag,
            double temperatureLeadLag, double temperatureLag) {
        static Derivatives zero() { return new Derivatives(0, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    }

    private record State(double peMeasured, double derivativeLag,
            double governorIntegrator, double valve, double turbineLag,
            double loadIntegrator, double mwIntegrator, double accelerationLag,
            double temperatureLeadLag, double temperatureLag) {
        static State zero() { return new State(0, 0, 0, 0, 0, 0, 0, 0, 0, 0); }

        State plus(Derivatives d, double dt) {
            return new State(peMeasured + d.peMeasured * dt,
                    derivativeLag + d.derivativeLag * dt,
                    governorIntegrator + d.governorIntegrator * dt,
                    valve + d.valve * dt, turbineLag + d.turbineLag * dt,
                    loadIntegrator + d.loadIntegrator * dt,
                    mwIntegrator + d.mwIntegrator * dt,
                    accelerationLag + d.accelerationLag * dt,
                    temperatureLeadLag + d.temperatureLeadLag * dt,
                    temperatureLag + d.temperatureLag * dt);
        }

        State plusAverage(Derivatives a, Derivatives b, double dt) {
            return plus(new Derivatives((a.peMeasured + b.peMeasured) / 2.0,
                    (a.derivativeLag + b.derivativeLag) / 2.0,
                    (a.governorIntegrator + b.governorIntegrator) / 2.0,
                    (a.valve + b.valve) / 2.0,
                    (a.turbineLag + b.turbineLag) / 2.0,
                    (a.loadIntegrator + b.loadIntegrator) / 2.0,
                    (a.mwIntegrator + b.mwIntegrator) / 2.0,
                    (a.accelerationLag + b.accelerationLag) / 2.0,
                    (a.temperatureLeadLag + b.temperatureLeadLag) / 2.0,
                    (a.temperatureLag + b.temperatureLag) / 2.0), dt);
        }
    }
}
