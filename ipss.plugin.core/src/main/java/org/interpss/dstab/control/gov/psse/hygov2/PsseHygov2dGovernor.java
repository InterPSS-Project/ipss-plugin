package org.interpss.dstab.control.gov.psse.hygov2;

import org.interpss.dstab.control.util.AsymmetricDeadbandBlock;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;

/**
 * PSS/E HYGOV2D hydro turbine-governor with asymmetric speed deadband.
 *
 * <p>The realization follows the published PSS/E/PowerWorld diagram. The gate
 * position limit uses the PSS/E non-windup behavior identified by that diagram;
 * PowerWorld documents its own windup-limit behavior as a simulator-specific
 * difference.</p>
 */
public class PsseHygov2dGovernor extends AbstractGovernor implements IntegrationStepAware {
    private static final double EPS = 1.0e-9;

    private State state = State.zero();
    private State oldState = State.zero();
    private Derivatives oldDerivatives = Derivatives.zero();
    private double reference;
    private double auxiliaryInput;
    private double governorToMachineBase = 1.0;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double effectiveT3, effectiveT4, effectiveT6, effectiveTr;
    private double effectiveVgmax, effectiveGmax, effectiveGmin;
    private double currentOutput;
    private boolean initialized;

    public PsseHygov2dGovernor(String id, String name, String category) {
        super(id, name, category);
        _data = new PsseHygov2dGovernorData();
    }

    public PsseHygov2dGovernorData getData() {
        return (PsseHygov2dGovernorData) _data;
    }

    @Override public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        if (!Double.isFinite(timeStepSec) || timeStepSec < 0.0
                || !Double.isFinite(multiplier) || multiplier < 0.0) {
            throw new IllegalArgumentException(
                    "HYGOV2D integration settings must be finite and non-negative");
        }
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override public boolean initStates(BaseDStabBus<?, ?> bus, Machine mach) {
        if (!validateParameters()) return false;
        prepareEffectiveParameters();
        PsseHygov2dGovernorData d = getData();
        double machineMva = mach.getRating(UnitType.mVA, bus.getNetwork().getBaseKva());
        governorToMachineBase = d.getTrate() > EPS && machineMva > EPS
                ? d.getTrate() / machineMva : 1.0;
        double pm0 = mach.getPm() / governorToMachineBase;
        double gate0 = pm0 / d.getPmax();
        if (!finite(pm0, gate0)) return false;

        // PSS/E expands position limits to admit the solved initial gate.
        effectiveGmax = Math.max(effectiveGmax, gate0);
        effectiveGmin = Math.min(effectiveGmin, gate0);
        reference = d.getR() * gate0 - auxiliaryInput;
        state = new State(0.0, 0.0, 0.0, gate0, gate0, gate0);
        oldState = state;
        currentOutput = pm0;
        initialized = true;
        return true;
    }

    @Override public boolean nextStep(double dt, DynamicSimuMethod method,
            Machine mach, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) {
            throw new InterpssRuntimeException("HYGOV2D supports MODIFIED_EULER only");
        }
        if (!initialized) return false;
        if (flag == 0) {
            oldState = state;
            oldDerivatives = derivatives(oldState);
            state = normalize(oldState.plus(oldDerivatives, dt));
            currentOutput = output(state);
        } else if (flag == 1) {
            Derivatives corrected = derivatives(state);
            state = normalize(oldState.plusAverage(oldDerivatives, corrected, dt));
            currentOutput = output(state);
        } else {
            throw new InterpssRuntimeException("HYGOV2D invalid integration flag: " + flag);
        }
        return true;
    }

    @Override public double getOutput(Machine mach) {
        return currentOutput * governorToMachineBase;
    }

    @Override public void setRefPoint(double value) {
        reference = getData().getR()
                * (value / governorToMachineBase / getData().getPmax());
    }

    public void setAuxiliaryInput(double value) { auxiliaryInput = value; }
    public double getReference() { return reference; }
    /** PowerWorld state 1: output of {@code (Ki + s*Kp)/s}. */
    public double getPiOutput() { return piOutput(state); }
    /** PowerWorld state 2: output of {@code Ka*(1+s*T1)/(s*T3)}. */
    public double getGovernorOutput() { return governorOutput(state); }
    /** PowerWorld state 3: output of {@code (1+s*T2)/(1+s*T4)}. */
    public double getGovernorSpeed() { return governorSpeed(state); }
    /** PowerWorld state 4: output of the temporary-droop washout. */
    public double getTemporaryDroopOutput() { return temporaryDroop(state); }
    /** PowerWorld state 5. */
    public double getGatePosition() { return state.gate; }
    /** PowerWorld state 6: output of the nonminimum-phase penstock block. */
    public double getPenstockOutput() { return penstockOutput(state); }
    public double getEffectiveT3() { return effectiveT3; }
    public double getEffectiveT4() { return effectiveT4; }
    public double getEffectiveT6() { return effectiveT6; }
    public double getEffectiveTr() { return effectiveTr; }
    public double getEffectiveVgmax() { return effectiveVgmax; }
    public double getEffectiveGmax() { return effectiveGmax; }
    public double getEffectiveGmin() { return effectiveGmin; }
    public double getGovernorBaseMva(Machine mach) {
        return governorToMachineBase * mach.getRating(UnitType.mVA,
                mach.getDStabBus().getNetwork().getBaseKva());
    }
    public double applySpeedDeadband(double speedDeviation) {
        return AsymmetricDeadbandBlock.apply(speedDeviation,
                getData().getDbH(), getData().getDbL());
    }

    public boolean validateParameters() {
        PsseHygov2dGovernorData d = getData();
        return finite(d.getKp(), d.getKi(), d.getKa(), d.getT1(), d.getT2(),
                        d.getT3(), d.getT4(), d.getT5(), d.getT6(), d.getTr(),
                        d.getRtemp(), d.getR(), d.getVgmax(), d.getGmax(),
                        d.getGmin(), d.getPmax(), d.getDbH(), d.getDbL(),
                        d.getTrate())
                && d.getKp() >= 0.0 && d.getKi() >= 0.0 && d.getKa() > 0.0
                && d.getT1() >= 0.0 && d.getT2() >= 0.0 && d.getT3() > 0.0
                && d.getT4() > 0.0 && d.getT5() >= 0.0 && d.getT6() > 0.0
                && d.getTr() > 0.0 && d.getRtemp() >= 0.0 && d.getR() >= 0.0
                && Math.abs(d.getVgmax()) > EPS && d.getPmax() > EPS
                && d.getDbH() >= 0.0 && d.getDbL() <= 0.0
                && d.getDbL() <= d.getDbH() && d.getTrate() >= 0.0;
    }

    private Derivatives derivatives(State s) {
        PsseHygov2dGovernorData d = getData();
        double speed = applySpeedDeadband(getMachine().getSpeed() - 1.0);
        double piDot = d.getKi() * speed;
        double error = reference + auxiliaryInput - piOutput(s)
                - d.getR() * s.gate - temporaryDroop(s);
        double governorDot = d.getKa() * error / effectiveT3;
        double governor = s.governor + d.getKa() * d.getT1() * error / effectiveT3;
        double governorSpeedDot = (governor - s.governorSpeed) / effectiveT4;
        double rate = clamp(governorSpeed(s), -effectiveVgmax, effectiveVgmax);
        if ((s.gate >= effectiveGmax && rate > 0.0)
                || (s.gate <= effectiveGmin && rate < 0.0)) rate = 0.0;
        double droopDot = (s.gate - s.droopLag) / effectiveTr;
        double penstockDot = (s.gate - s.penstockLag) / effectiveT6;
        return new Derivatives(piDot, governorDot, governorSpeedDot, droopDot,
                rate, penstockDot);
    }

    private State normalize(State s) {
        return new State(s.piIntegrator, s.governor, s.governorSpeed,
                s.droopLag, clamp(s.gate, effectiveGmin, effectiveGmax),
                s.penstockLag);
    }

    private double piOutput(State s) {
        return s.piIntegrator + getData().getKp()
                * applySpeedDeadband(getMachine().getSpeed() - 1.0);
    }

    private double governorOutput(State s) {
        double error = reference + auxiliaryInput - piOutput(s)
                - getData().getR() * s.gate - temporaryDroop(s);
        return s.governor + getData().getKa() * getData().getT1()
                * error / effectiveT3;
    }

    private double governorSpeed(State s) {
        double input = governorOutput(s);
        double ratio = getData().getT2() / effectiveT4;
        return ratio * input + (1.0 - ratio) * s.governorSpeed;
    }

    private double temporaryDroop(State s) {
        return getData().getRtemp() * (s.gate - s.droopLag);
    }

    private double penstockOutput(State s) {
        double ratio = getData().getT5() / effectiveT6;
        return (1.0 + ratio) * s.penstockLag - ratio * s.gate;
    }

    private double output(State s) {
        return getData().getPmax() * penstockOutput(s);
    }

    private void prepareEffectiveParameters() {
        PsseHygov2dGovernorData d = getData();
        effectiveT3 = correctedPositiveTimeConstant(d.getT3());
        effectiveT4 = correctedPositiveTimeConstant(d.getT4());
        effectiveT6 = correctedPositiveTimeConstant(d.getT6());
        effectiveTr = correctedPositiveTimeConstant(d.getTr());
        effectiveVgmax = Math.abs(d.getVgmax());
        effectiveGmax = Math.max(d.getGmax(), d.getGmin());
        effectiveGmin = Math.min(d.getGmax(), d.getGmin());
    }

    private double correctedPositiveTimeConstant(double value) {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        return value > 0.0 && value < minimum ? minimum : value;
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Derivatives(double piIntegrator, double governor,
            double governorSpeed, double droopLag, double gate,
            double penstockLag) {
        static Derivatives zero() { return new Derivatives(0, 0, 0, 0, 0, 0); }
    }

    private record State(double piIntegrator, double governor,
            double governorSpeed, double droopLag, double gate,
            double penstockLag) {
        static State zero() { return new State(0, 0, 0, 0, 0, 0); }

        State plus(Derivatives d, double dt) {
            return new State(piIntegrator + d.piIntegrator * dt,
                    governor + d.governor * dt,
                    governorSpeed + d.governorSpeed * dt,
                    droopLag + d.droopLag * dt,
                    gate + d.gate * dt,
                    penstockLag + d.penstockLag * dt);
        }

        State plusAverage(Derivatives a, Derivatives b, double dt) {
            return plus(new Derivatives((a.piIntegrator + b.piIntegrator) / 2.0,
                    (a.governor + b.governor) / 2.0,
                    (a.governorSpeed + b.governorSpeed) / 2.0,
                    (a.droopLag + b.droopLag) / 2.0,
                    (a.gate + b.gate) / 2.0,
                    (a.penstockLag + b.penstockLag) / 2.0), dt);
        }
    }
}
