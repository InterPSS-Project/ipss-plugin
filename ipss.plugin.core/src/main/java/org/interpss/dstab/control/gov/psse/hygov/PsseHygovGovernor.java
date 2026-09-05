package org.interpss.dstab.control.gov.psse.hygov;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;

/**
 * PSS/E HYGOV hydro turbine-governor.
 *
 * <p>The governor implements the permanent/temporary droop path, gate velocity
 * and position limits, servo lag, and nonlinear rigid-penstock turbine. The
 * temporary-droop integrator uses {@code r * Tr}; this retains the documented
 * {@code Tr} dependence that is currently absent from the ANDES HYGOV state
 * equation.</p>
 */
public class PsseHygovGovernor extends AbstractGovernor {
    private static final double EPS = 1.0e-9;

    private State state = State.zero();
    private State oldState = State.zero();
    private Derivatives oldDerivatives = Derivatives.zero();
    private double pref;
    private double effectiveGmax;
    private double effectiveGmin;
    private double committedDesiredGate;
    private double currentOutput;
    private boolean initialized;

    public PsseHygovGovernor(String id, String name, String category) {
        super(id, name, category);
        _data = new PsseHygovGovernorData();
    }

    public PsseHygovGovernorData getData() {
        return (PsseHygovGovernorData) _data;
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine mach) {
        if (!validateParameters()) return false;
        PsseHygovGovernorData d = getData();
        double q0 = mach.getPm() / d.getAt() + d.getQnl();
        if (q0 <= EPS) return false;

        // PowerWorld expands position limits when the solved initial gate lies outside them.
        effectiveGmax = Math.max(d.getGmax(), q0);
        effectiveGmin = Math.min(d.getGmin(), q0);
        pref = d.getR() * q0;
        state = new State(0.0, q0, q0, q0);
        oldState = state;
        committedDesiredGate = q0;
        currentOutput = mach.getPm();
        initialized = true;
        return true;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine mach, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) {
            throw new InterpssRuntimeException("HYGOV supports MODIFIED_EULER only");
        }
        if (!initialized) return false;

        if (flag == 0) {
            oldState = state;
            oldDerivatives = derivatives(oldState);
            state = oldState.plus(oldDerivatives, dt);
            state = constrainDesiredGate(state, committedDesiredGate, dt);
            currentOutput = output(state);
        } else if (flag == 1) {
            Derivatives corrected = derivatives(state);
            state = oldState.plusAverage(oldDerivatives, corrected, dt);
            state = constrainDesiredGate(state, committedDesiredGate, dt);
            committedDesiredGate = desiredGate(state);
            currentOutput = output(state);
        } else {
            throw new InterpssRuntimeException("HYGOV invalid integration flag: " + flag);
        }
        return true;
    }

    @Override
    public double getOutput(Machine mach) {
        return currentOutput;
    }

    @Override
    public void setRefPoint(double value) {
        pref = getData().getR() * (value / getData().getAt() + getData().getQnl());
    }

    public double getGatePosition() { return state.gate; }
    public double getWaterFlow() { return state.flow; }
    public double getDesiredGate() { return desiredGate(state); }

    public boolean validateParameters() {
        PsseHygovGovernorData d = getData();
        return d.getR() > 0.0 && d.getRtemp() > 0.0 && d.getTr() > 0.0
                && d.getTf() >= 0.0 && d.getTg() > 0.0 && d.getVelm() > 0.0
                && d.getGmax() >= d.getGmin() && d.getTw() > 0.0
                && d.getAt() > 0.0 && d.getQnl() >= 0.0;
    }

    private Derivatives derivatives(State s) {
        PsseHygovGovernorData d = getData();
        double speedDeviation = getMachine().getSpeed() - 1.0;
        double dg = desiredGate(s);
        double governorInput = pref - speedDeviation - d.getR() * dg;
        double filterOutput = d.getTf() > EPS ? s.filter : algebraicFilterOutput(s);
        double filterDot = d.getTf() > EPS
                ? (governorInput - s.filter) / d.getTf() : 0.0;

        double integratorDot = filterOutput / (d.getRtemp() * d.getTr());
        double zeroTfFactor = d.getTf() > EPS ? 1.0 : 1.0 + d.getR() / d.getRtemp();
        double desiredGateDot = d.getTf() > EPS
                ? integratorDot + filterDot / d.getRtemp()
                : integratorDot / zeroTfFactor;
        desiredGateDot = clamp(desiredGateDot, -d.getVelm(), d.getVelm());
        if ((dg >= effectiveGmax && desiredGateDot > 0.0)
                || (dg <= effectiveGmin && desiredGateDot < 0.0)) {
            desiredGateDot = 0.0;
        }
        integratorDot = d.getTf() > EPS
                ? desiredGateDot - filterDot / d.getRtemp()
                : desiredGateDot * zeroTfFactor;

        double gateDot = (clamp(dg, effectiveGmin, effectiveGmax) - s.gate) / d.getTg();
        double safeGate = Math.max(EPS, s.gate);
        double head = square(s.flow / safeGate);
        double flowDot = (1.0 - head) / d.getTw();
        return new Derivatives(filterDot, integratorDot, gateDot, flowDot);
    }

    private double desiredGate(State s) {
        double filterOutput = getData().getTf() > EPS ? s.filter : algebraicFilterOutput(s);
        return s.integrator + filterOutput / getData().getRtemp();
    }

    private double algebraicFilterOutput(State s) {
        PsseHygovGovernorData d = getData();
        return (pref - (getMachine().getSpeed() - 1.0) - d.getR() * s.integrator)
                / (1.0 + d.getR() / d.getRtemp());
    }

    private State constrainDesiredGate(State s, double priorDesiredGate, double dt) {
        double raw = desiredGate(s);
        double target = clamp(raw,
                Math.max(effectiveGmin, priorDesiredGate - getData().getVelm() * dt),
                Math.min(effectiveGmax, priorDesiredGate + getData().getVelm() * dt));
        if (Math.abs(target - raw) <= EPS) return s;
        double correctionFactor = getData().getTf() > EPS
                ? 1.0 : 1.0 + getData().getR() / getData().getRtemp();
        return new State(s.filter, s.integrator + (target - raw) * correctionFactor,
                s.gate, s.flow);
    }

    private double output(State s) {
        PsseHygovGovernorData d = getData();
        double gate = Math.max(EPS, s.gate);
        double head = square(s.flow / gate);
        return d.getAt() * head * (s.flow - d.getQnl())
                - d.getDturb() * (getMachine().getSpeed() - 1.0) * s.gate;
    }

    private static double square(double value) { return value * value; }
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Derivatives(double filter, double integrator, double gate, double flow) {
        static Derivatives zero() { return new Derivatives(0, 0, 0, 0); }
    }

    private record State(double filter, double integrator, double gate, double flow) {
        static State zero() { return new State(0, 0, 0, 0); }

        State plus(Derivatives d, double dt) {
            return new State(filter + d.filter * dt, integrator + d.integrator * dt,
                    gate + d.gate * dt, flow + d.flow * dt);
        }

        State plusAverage(Derivatives a, Derivatives b, double dt) {
            return plus(new Derivatives((a.filter + b.filter) / 2.0,
                    (a.integrator + b.integrator) / 2.0,
                    (a.gate + b.gate) / 2.0,
                    (a.flow + b.flow) / 2.0), dt);
        }
    }
}
