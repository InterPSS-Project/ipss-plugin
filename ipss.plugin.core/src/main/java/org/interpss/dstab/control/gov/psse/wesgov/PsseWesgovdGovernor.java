package org.interpss.dstab.control.gov.psse.wesgov;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;
import org.interpss.dstab.control.util.AsymmetricDeadbandBlock;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.numeric.datatype.Unit.UnitType;

/**
 * PSS/E WESGOVD Westinghouse sampled-data gas-turbine governor.
 *
 * <p>The electrical-power transducer is continuous, while its output and the
 * deadbanded speed deviation have independent sample/hold periods. The held
 * error drives the documented parallel PI controller. At each control sample,
 * the controller output can change by at most {@code Alim}; two serial lags
 * then produce mechanical power.</p>
 */
public class PsseWesgovdGovernor extends AbstractGovernor implements IntegrationStepAware {
    private static final double EPS = 1.0e-9;

    private State state = State.zero();
    private State oldState = State.zero();
    private Derivatives oldDerivatives = Derivatives.zero();
    private double sampledSpeed;
    private double sampledPe;
    private double heldControl;
    private double reference;
    private double auxiliaryInput;
    private double controlElapsed;
    private double powerElapsed;
    private double governorToMachineBase = 1.0;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double effectiveTi;
    private double effectiveT1;
    private double effectiveT2;
    private double effectiveTpe;
    private boolean initialized;

    public PsseWesgovdGovernor(String id, String name, String category) {
        super(id, name, category);
        _data = new PsseWesgovdGovernorData();
    }

    public PsseWesgovdGovernorData getData() {
        return (PsseWesgovdGovernorData) _data;
    }

    @Override
    public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        if (!Double.isFinite(timeStepSec) || timeStepSec < 0.0
                || !Double.isFinite(multiplier) || multiplier < 0.0) {
            throw new IllegalArgumentException(
                    "WESGOVD integration settings must be finite and non-negative");
        }
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine mach) {
        if (!validateParameters()) return false;
        PsseWesgovdGovernorData d = getData();
        double minimum = integrationStep * minimumTimeConstantMultiplier;
        effectiveTi = correctedRequiredTime(d.getTi(), minimum);
        effectiveT1 = correctedOptionalTime(d.getT1(), minimum);
        effectiveT2 = correctedOptionalTime(d.getT2(), minimum);
        effectiveTpe = correctedOptionalTime(d.getTpe(), minimum);

        double machineMva = mach.getRating(UnitType.mVA, bus.getNetwork().getBaseKva());
        governorToMachineBase = d.getTrate() > EPS && machineMva > EPS
                ? d.getTrate() / machineMva : 1.0;
        double pm0 = mach.getPm() / governorToMachineBase;
        double pe0 = mach.getPe() / governorToMachineBase;
        if (!Double.isFinite(pm0) || !Double.isFinite(pe0)) return false;

        sampledSpeed = applySpeedDeadband(mach.getSpeed() - 1.0);
        sampledPe = pe0;
        reference = sampledSpeed + d.getDroop() * sampledPe - auxiliaryInput;
        heldControl = pm0;
        state = new State(pe0, pm0, pm0, pm0);
        oldState = state;
        controlElapsed = 0.0;
        powerElapsed = 0.0;
        initialized = true;
        return true;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine mach, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) {
            throw new InterpssRuntimeException("WESGOVD supports MODIFIED_EULER only");
        }
        if (!initialized) return false;
        if (flag == 0) {
            oldState = state;
            oldDerivatives = derivatives(oldState);
            state = normalize(oldState.plus(oldDerivatives, dt));
        } else if (flag == 1) {
            Derivatives corrected = derivatives(state);
            state = normalize(oldState.plusAverage(oldDerivatives, corrected, dt));
            commitSampleBoundaries(dt);
        } else {
            throw new InterpssRuntimeException("WESGOVD invalid integration flag: " + flag);
        }
        return true;
    }

    @Override
    public double getOutput(Machine mach) {
        return outputOnGovernorBase(state) * governorToMachineBase;
    }

    @Override
    public void setRefPoint(double value) {
        reference = getData().getDroop() * value / governorToMachineBase
                - auxiliaryInput;
    }

    public void setAuxiliaryInput(double value) { auxiliaryInput = value; }
    public double getAuxiliaryInput() { return auxiliaryInput; }
    public double getReference() { return reference; }
    public double getSampledSpeedDeviation() { return sampledSpeed; }
    public double getSampledElectricalPower() { return sampledPe; }
    public double getHeldControl() { return heldControl; }
    public double getIntegratorState() { return state.integrator; }
    public double getValveState() { return effectiveT1 > EPS ? state.valve : heldControl; }
    public double getEffectiveTi() { return effectiveTi; }
    public double getEffectiveT1() { return effectiveT1; }
    public double getEffectiveT2() { return effectiveT2; }
    public double getEffectiveTpe() { return effectiveTpe; }
    public double getGovernorBaseMva(Machine mach) {
        return governorToMachineBase * mach.getRating(
                UnitType.mVA, mach.getDStabBus().getNetwork().getBaseKva());
    }
    public double applySpeedDeadband(double speedDeviation) {
        return AsymmetricDeadbandBlock.apply(
                speedDeviation, getData().getDbH(), getData().getDbL());
    }

    public boolean validateParameters() {
        PsseWesgovdGovernorData d = getData();
        return finite(d.getDeltaTc(), d.getDeltaTp(), d.getDroop(), d.getKp(),
                d.getTi(), d.getT1(), d.getT2(), d.getAlim(), d.getTpe(),
                d.getDbH(), d.getDbL(), d.getTrate())
                && d.getDeltaTc() >= 0.0 && d.getDeltaTp() >= 0.0
                && d.getDroop() >= 0.0 && d.getTi() > 0.0
                && d.getT1() >= 0.0 && d.getT2() >= 0.0
                && d.getAlim() >= 0.0 && d.getTpe() >= 0.0
                && d.getDbH() >= 0.0 && d.getDbL() <= 0.0
                && d.getDbL() <= d.getDbH() && d.getTrate() >= 0.0;
    }

    private Derivatives derivatives(State s) {
        double peDot = effectiveTpe > EPS
                ? (machinePowerOnGovernorBase() - s.peMeasured) / effectiveTpe : 0.0;
        double error = heldError(s);
        double integratorDot = error / effectiveTi;
        double valveInput = heldControl;
        double valveDot = effectiveT1 > EPS ? (valveInput - s.valve) / effectiveT1 : 0.0;
        double valve = effectiveT1 > EPS ? s.valve : valveInput;
        double pmechDot = effectiveT2 > EPS ? (valve - s.pmech) / effectiveT2 : 0.0;
        return new Derivatives(peDot, integratorDot, valveDot, pmechDot);
    }

    private State normalize(State s) {
        double pe = effectiveTpe > EPS ? s.peMeasured : machinePowerOnGovernorBase();
        double valve = effectiveT1 > EPS ? s.valve : heldControl;
        double pmech = effectiveT2 > EPS ? s.pmech : valve;
        return new State(pe, s.integrator, valve, pmech);
    }

    private void commitSampleBoundaries(double dt) {
        PsseWesgovdGovernorData d = getData();
        powerElapsed += dt;
        controlElapsed += dt;

        boolean powerSample = d.getDeltaTp() <= EPS
                || powerElapsed + EPS >= d.getDeltaTp();
        if (powerSample) {
            sampledPe = effectiveTpe > EPS ? state.peMeasured : machinePowerOnGovernorBase();
            powerElapsed = remainder(powerElapsed, d.getDeltaTp());
        }

        boolean controlSample = d.getDeltaTc() <= EPS
                || controlElapsed + EPS >= d.getDeltaTc();
        if (controlSample) {
            sampledSpeed = applySpeedDeadband(getMachine().getSpeed() - 1.0);
            double requested = state.integrator + d.getKp() * heldError(state);
            double maxChange = d.getAlim();
            heldControl += clamp(requested - heldControl, -maxChange, maxChange);
            controlElapsed = remainder(controlElapsed, d.getDeltaTc());
        }
    }

    private double heldError(State s) {
        double speed = getData().getDeltaTc() <= EPS
                ? applySpeedDeadband(getMachine().getSpeed() - 1.0) : sampledSpeed;
        double pe = getData().getDeltaTp() <= EPS
                ? (effectiveTpe > EPS ? s.peMeasured : machinePowerOnGovernorBase()) : sampledPe;
        return reference - speed - getData().getDroop() * pe + auxiliaryInput;
    }

    private double outputOnGovernorBase(State s) {
        if (effectiveT2 > EPS) return s.pmech;
        return effectiveT1 > EPS ? s.valve : heldControl;
    }

    private double machinePowerOnGovernorBase() {
        return getMachine().getPe() / governorToMachineBase;
    }

    private static double correctedRequiredTime(double value, double minimum) {
        return value > 0.0 && value < minimum ? minimum : value;
    }

    private static double correctedOptionalTime(double value, double minimum) {
        if (value <= 0.0 || minimum <= 0.0) return value;
        if (value < 0.5 * minimum) return 0.0;
        return value < minimum ? minimum : value;
    }

    private static double remainder(double elapsed, double period) {
        if (period <= EPS) return 0.0;
        double result = elapsed % period;
        return result < EPS || period - result < EPS ? 0.0 : result;
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record State(double peMeasured, double integrator, double valve, double pmech) {
        static State zero() { return new State(0.0, 0.0, 0.0, 0.0); }

        State plus(Derivatives d, double dt) {
            return new State(peMeasured + d.peMeasured * dt,
                    integrator + d.integrator * dt,
                    valve + d.valve * dt,
                    pmech + d.pmech * dt);
        }

        State plusAverage(Derivatives a, Derivatives b, double dt) {
            return plus(new Derivatives((a.peMeasured + b.peMeasured) / 2.0,
                    (a.integrator + b.integrator) / 2.0,
                    (a.valve + b.valve) / 2.0,
                    (a.pmech + b.pmech) / 2.0), dt);
        }
    }

    private record Derivatives(double peMeasured, double integrator,
            double valve, double pmech) {
        static Derivatives zero() { return new Derivatives(0.0, 0.0, 0.0, 0.0); }
    }
}
