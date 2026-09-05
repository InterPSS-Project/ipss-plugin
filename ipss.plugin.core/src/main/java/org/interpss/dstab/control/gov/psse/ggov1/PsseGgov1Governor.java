package org.interpss.dstab.control.gov.psse.ggov1;

import java.util.ArrayDeque;
import java.util.Deque;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;
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
public class PsseGgov1Governor extends AbstractGovernor {
    private static final double EPS = 1.0e-9;

    private State state = State.zero();
    private State oldState = State.zero();
    private Derivatives oldDerivatives = Derivatives.zero();
    private double pref;
    private double pmwset;
    private double effectiveVmax;
    private double effectiveVmin;
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
        if (!validateParameters()) return false;

        double machineMva = mach.getRating(UnitType.mVA, bus.getNetwork().getBaseKva());
        governorToMachineBase = getData().getTrate() > EPS && machineMva > EPS
                ? getData().getTrate() / machineMva : 1.0;
        double pm0 = mach.getPm() / governorToMachineBase;
        double pe0 = mach.getPe() / governorToMachineBase;
        double speed = mach.getSpeed();
        double speedDeviation = speed - 1.0;
        double damping0 = damping(speedDeviation);
        double valve0 = getData().getWfnl() + (pm0 + damping0) / getData().getKturb();

        // PowerWorld expands governor position limits to contain the initialized valve.
        effectiveVmax = Math.max(getData().getVmax(), valve0);
        effectiveVmin = Math.min(getData().getVmin(), valve0);
        double droop0 = selectedDroop(pe0, valve0, valve0);
        pref = speedDeviation + getData().getR() * droop0;
        pmwset = pe0;

        double tempInput0 = valve0 * maximumPowerFactor(speedDeviation);
        double loadIntegrator0 = valve0;
        state = new State(pe0, 0.0, valve0, valve0, pm0 + damping0,
                loadIntegrator0, 0.0, speedDeviation, tempInput0, tempInput0);
        oldState = state;
        committedFsr = valve0;
        currentFsr = valve0;
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
            state = oldState.plus(oldDerivatives, dt);
            Algebraic predicted = algebraic(state, dt, dt, committedFsr);
            currentFsr = predicted.fsr;
            currentOutput = predicted.pmech;
        } else if (flag == 1) {
            Algebraic predicted = algebraic(state, dt, dt, currentFsr);
            Derivatives correctedDerivatives = derivatives(state, predicted);
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

    @Override
    public void setRefPoint(double value) {
        pref = value / governorToMachineBase;
    }

    public double getValveStroke() { return clamp(state.valve, effectiveVmin, effectiveVmax); }
    public double getFsr() { return currentFsr; }
    public double getMeasuredElectricalPower() { return state.peMeasured; }
    public double getGovernorBaseMva(Machine mach) {
        return governorToMachineBase * mach.getRating(UnitType.mVA,
                mach.getDStabBus().getNetwork().getBaseKva());
    }

    /** True when the parameter set is represented without a silent approximation. */
    public boolean validateParameters() {
        PsseGgov1GovernorData d = getData();
        boolean validSelector = d.getRselect() == 1 || d.getRselect() == 0
                || d.getRselect() == -1 || d.getRselect() == -2;
        return validSelector && (d.getFlag() == 0 || d.getFlag() == 1)
                && (d.getRselect() == 0 ? d.getR() >= 0.0 : d.getR() > 0.0)
                && d.getTpelec() >= 0.0 && d.getTdgov() >= 0.0
                && d.getTact() > EPS && d.getTb() >= 0.0 && d.getTc() >= 0.0
                && (d.getTb() > EPS || d.getTc() <= EPS)
                && d.getTeng() >= 0.0 && d.getTfload() >= 0.0
                && d.getTa() >= 0.0 && d.getTsa() >= 0.0 && d.getTsb() >= 0.0
                && d.getMaxerr() >= d.getMinerr() && d.getVmax() >= d.getVmin()
                && d.getRopen() > 0.0 && d.getRclose() < 0.0
                && d.getKturb() > EPS && d.getTrate() >= 0.0;
    }

    private Algebraic algebraic(State s, double stepSize, double evaluationOffset,
            double trackingFsr) {
        PsseGgov1GovernorData d = getData();
        double speedDeviation = getMachine().getSpeed() - 1.0;
        double pe = getMachine().getPe() / governorToMachineBase;
        double peMeasured = d.getTpelec() > EPS ? s.peMeasured : pe;
        double valve = clamp(s.valve, effectiveVmin, effectiveVmax);
        double droop = selectedDroop(peMeasured, valve, trackingFsr);
        double error = clamp(deadband(pref + s.mwIntegrator - speedDeviation - d.getR() * droop),
                d.getMinerr(), d.getMaxerr());
        double derivative = d.getTdgov() > EPS ? d.getKdgov() / d.getTdgov() * (error - s.derivativeLag) : 0.0;
        double fsrn = d.getKpgov() * error + derivative + s.governorIntegrator;

        double speedFactor = maximumPowerFactor(speedDeviation);
        double fuelFlow = (d.getFlag() == 1 ? 1.0 + speedDeviation : 1.0) * valve;
        double rawTurbineInput = d.getKturb() * (fuelFlow - d.getWfnl());
        double turbineInput = delayedEngineInput(rawTurbineInput, evaluationOffset);
        double turbinePower = d.getTb() > EPS
                ? s.turbineLag + d.getTc() / d.getTb() * (turbineInput - s.turbineLag)
                : turbineInput;
        double pmech = turbinePower - damping(speedDeviation);

        double temperatureInput = fuelFlow * speedFactor;
        double temperatureLeadLag = d.getTsb() > EPS
                ? s.temperatureLeadLag + d.getTsa() / d.getTsb()
                        * (temperatureInput - s.temperatureLeadLag)
                : temperatureInput;
        double temperatureMeasured = d.getTfload() > EPS ? s.temperatureLag : temperatureLeadLag;
        double loadError = loadSetpoint() - temperatureMeasured;
        double fsrt = d.getKpload() * loadError + s.loadIntegrator;

        double acceleration = d.getTa() > EPS
                ? (speedDeviation - s.accelerationLag) / d.getTa() : 0.0;
        double fsra = trackingFsr + d.getKa() * stepSize * (d.getAset() - acceleration);
        double fsr = clamp(Math.min(1.0, Math.min(fsrn, Math.min(fsrt, fsra))),
                effectiveVmin, effectiveVmax);
        return new Algebraic(pe, error, fsrn, fsrt, fsr, valve, rawTurbineInput, turbineInput,
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
        double peDot = d.getTpelec() > EPS ? (a.pe - s.peMeasured) / d.getTpelec() : 0.0;
        double derivativeDot = d.getTdgov() > EPS ? (a.error - s.derivativeLag) / d.getTdgov() : 0.0;
        double governorIntegralDot = d.getKigov() * a.error;
        if (Math.abs(d.getKpgov()) > EPS) {
            governorIntegralDot += d.getKigov() / d.getKpgov() * (a.fsr - a.fsrn);
        }
        double valveDot = clamp((a.fsr - a.valve) / d.getTact(), d.getRclose(), d.getRopen());
        double turbineDot = d.getTb() > EPS ? (a.turbineInput - s.turbineLag) / d.getTb() : 0.0;
        double loadIntegralDot = d.getKiload() * a.loadError;
        if (Math.abs(d.getKpload()) > EPS) {
            loadIntegralDot += d.getKiload() / d.getKpload() * (a.fsr - a.fsrt);
        }
        double mwDot = d.getKimw() * (pmwset - a.pe);
        double mwLimit = 1.1 * Math.abs(d.getR());
        if ((s.mwIntegrator >= mwLimit && mwDot > 0.0)
                || (s.mwIntegrator <= -mwLimit && mwDot < 0.0)) mwDot = 0.0;
        double speedDeviation = getMachine().getSpeed() - 1.0;
        double accelerationDot = d.getTa() > EPS
                ? (speedDeviation - s.accelerationLag) / d.getTa() : 0.0;
        double temperatureLeadDot = d.getTsb() > EPS
                ? (a.temperatureInput - s.temperatureLeadLag) / d.getTsb() : 0.0;
        double temperatureLagDot = d.getTfload() > EPS
                ? (a.temperatureLeadLag - s.temperatureLag) / d.getTfload() : 0.0;
        return new Derivatives(peDot, derivativeDot, governorIntegralDot, valveDot,
                turbineDot, loadIntegralDot, mwDot, accelerationDot,
                temperatureLeadDot, temperatureLagDot);
    }

    private double selectedDroop(double peMeasured, double valve, double governorOutput) {
        return switch (getData().getRselect()) {
            case 1 -> peMeasured;
            case -1 -> valve;
            case -2 -> governorOutput;
            default -> 0.0;
        };
    }

    private double damping(double speedDeviation) {
        double dm = getData().getDm();
        double absoluteSpeed = Math.max(EPS, 1.0 + speedDeviation);
        return dm >= 0.0 ? dm * absoluteSpeed : dm * Math.pow(absoluteSpeed, dm);
    }

    private double maximumPowerFactor(double speedDeviation) {
        double dm = getData().getDm();
        double absoluteSpeed = Math.max(EPS, 1.0 + speedDeviation);
        return dm >= 0.0 ? absoluteSpeed : Math.pow(absoluteSpeed, dm);
    }

    private double loadSetpoint() {
        return getData().getLdref() / getData().getKturb() + getData().getWfnl();
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

    private record Algebraic(double pe, double error, double fsrn, double fsrt,
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
