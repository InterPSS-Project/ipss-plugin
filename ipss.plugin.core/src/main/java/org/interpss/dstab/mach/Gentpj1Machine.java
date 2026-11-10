package org.interpss.dstab.mach;

import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.datatype.Vector_dq;

import com.interpss.core.net.DataCheckConfiguration;
import com.interpss.core.net.Network;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.impl.DynamicSimuAdapterImpl;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.funcImpl.DStabFunction;
import com.interpss.dstab.mach.MachineIfdBase;
import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;

/**
 * Type-J synchronous machine with its own four voltage-state equations.
 *
 * <p>The Type-J realization integrates E''q, E''d, E'q and E'd directly.
 * A GENQEC winding-state realization is not interchangeable: differentiating
 * its algebraic E'' reconstruction introduces transient-state derivative
 * terms absent from Type-J. Sharing that solver can reproduce equilibrium
 * while giving a different first disturbed derivative. Only the core machine
 * interfaces, mechanical staging and fixed Norton admittance are inherited.
 *
 * <p>The electrical equations follow the Type-J derivation, equations 7-19,
 * with scaled quadratic saturation, current-dependent saturation input and
 * separate d/q saturation factors. The fixed Norton impedance is a network
 * preconditioner; the returned compensation current retains subtransient
 * saliency and saturation rather than replacing them by that impedance.
 */
public final class Gentpj1Machine extends RoundRotorMachineImpl
        implements IeeeVoltageCompensatedMachine, ICMLStateProvider {
    private static final double EPS = 1e-12;
    private final Gentpj1Data data;
    private final TypeJEquationSolver typeJSolver = new TypeJEquationSolver();
    private IeeeVcData ieeeVcData;
    private boolean coupledStageActive;
    private double stageVd, stageVq;

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,int flag) {
        // All modified-Euler derivatives belong to one coherent stage origin.
        // The shared lifecycle advances mechanics before electrical states;
        // reading the advanced rotor angle in the electrical RHS instead makes
        // that coupling a sequential first-order split, even though each local
        // update is labelled modified Euler. Freeze only the two input scalars,
        // using the existing frame/base transform, not a duplicate state object.
        var voltage=getVdq();stageVd=voltage.d;stageVq=voltage.q;
        coupledStageActive=true;
        try { return super.nextStep(dt,method,flag); }
        finally { coupledStageActive=false; }
    }

    public Gentpj1Machine(Gentpj1Data data) {
        this.data = java.util.Objects.requireNonNull(data);
        _dEqnSolver = typeJSolver;
    }

    public Gentpj1Data getGentpj1Data() { return data; }

    /** Legacy compound-model view only; its constructor may normalize values.
     * Use getGentpj1Data() for native physical parameters. Neither the builder
     * nor native validation may consume this compatibility conversion. */
    public GenqecData getGenqecData() { return data.asGenqecData(); }

    @Override public IeeeVcData getIeeeVcData() { return ieeeVcData; }
    @Override public void setIeeeVcData(IeeeVcData value) { ieeeVcData = value; }

    /** Type-J owns its boundary; do not select the inherited Flux0 shortcut. */
    @Override public boolean supportsDeferredCurrentPublication() { return false; }

    @Override public boolean initStates(BaseDStabBus<?, ?> bus) {
        // Invalidate the previous algebraic cache BEFORE core rotor-angle
        // initialization. Reinitializing at new P/Q/V must not reuse old flux.
        if (!checkData(null)) throw new IllegalArgumentException("Invalid or unsupported Type-J winding parameters");
        typeJSolver.initialized = false;
        typeJSolver.pending = false;
        return super.initStates(bus);
    }

    @Override public boolean checkData(DataCheckConfiguration configuration) {
        return getH() > 0 && getRating() > 0 && getRatedVoltage() > 0
                && getRa() >= 0 && getXd() > getXd1() && getXq() >= getXq1()
                && getXd1() > getXl() && getXq1() > getXl()
                && getXd11() >= getXl() && getXq11() >= getXl()
                && getTd01() > 0 && getTd011() > 0 && getTq01() >= 0 && getTq011() > 0
                && getXd1() != getXd11() && getXq1() != getXq11();
    }

    /** Scaled quadratic: S(x)=B(x-A)^2/x, fitted at flux 1 and 1.2. */
    @Override public double getSatruationFactor(double flux) {
        if (data.s1() == 0 || data.s12() == 0 || flux <= 0) return 0;
        double alpha = Math.sqrt(1.2 * data.s12() / data.s1());
        // Expanded fit avoids division by alpha-1, including its finite limit.
        double numerator = (alpha - 1) * flux + 1.2 - alpha;
        if (alpha > 1 && numerator <= 0) return 0;
        return data.s1() * numerator * numerator / (.04 * flux);
    }

    protected double saturationInput(double airGapFlux, double terminalCurrentMagnitude) {
        return airGapFlux + data.kis() * terminalCurrentMagnitude;
    }

    private double initialSaturation() {
        Complex voltage = getDStabBus().getVoltage().divide(getVMultiFactor());
        Complex current = getParentGen().getGen().divide(getDStabBus().getVoltage())
                .conjugate().divide(getIMultiFactor());
        double airGap = voltage.add(new Complex(getRa(), getXl()).multiply(current)).abs();
        return getSatruationFactor(saturationInput(airGap, current.abs()));
    }

    /** Read-only algebraic result of the last electrical boundary evaluation. */
    public double getEffectiveSaturationFactor() {
        return typeJSolver.initialized ? typeJSolver.saturation : initialSaturation();
    }

    private double dFactor(double saturation) { return 1 + saturation; }
    private double qFactor(double saturation) { return 1 + getXq() / getXd() * saturation; }
    private double reactance(double unsaturated, double factor) {
        return getXl() + (unsaturated - getXl()) / factor;
    }
    @Override public double getXdAdjusted() {
        return reactance(getXd(), dFactor(getEffectiveSaturationFactor()));
    }
    @Override public double getXqAdjusted() {
        return reactance(getXq(), qFactor(getEffectiveSaturationFactor()));
    }
    public double getXdppSaturated() {
        return reactance(getXd11(), dFactor(getEffectiveSaturationFactor()));
    }
    public double getXqppSaturated() {
        return reactance(getXq11(), qFactor(getEffectiveSaturationFactor()));
    }

    /** The model has no internal RCOMP/XCOMP; optional IEEEVC owns compensation. */
    public double getCompensatedVoltage() {
        return getDStabBus().getVoltage().abs() / getVMultiFactor();
    }
    @Override public double getCmlMachineVoltage() {
        return ieeeVcData == null ? getCompensatedVoltage() : getIeeeVcVoltage();
    }

    @Override public double calculateIfd(MachineIfdBase base) {
        double xadIfd = typeJSolver.initialized ? typeJSolver.fieldCurrent() : getEfd();
        // Keep the standard core EXCITER/MACHINE base conversion. In particular
        // EXCITER current is a rotor-state output, not the latest Efd command.
        return base == MachineIfdBase.MACHINE
                ? xadIfd / (getXdAdjusted() - getXl()) : xadIfd;
    }

    /** The electrical owner publishes air-gap POWER, including copper loss.
     * The mechanical equation converts both mechanical and electrical power to
     * torque at actual shaft speed. A Flux0 helper that treats cached Pe as
     * torque is not interchangeable with this voltage-state convention. */
    @Override protected double cal_dW_dt(double speed, double baseFrequency) {
        if (!(speed > 0) || !Double.isFinite(speed))
            throw new ArithmeticException("Type-J shaft speed must remain positive");
        double dampingPower = getD() * .01 * baseFrequency * (speed - 1);
        return (getPm() - dampingPower - getPe() - getPe_NegSeq()) / (2 * getH() * speed);
    }

    /** Native coordinates in their published order, without current refresh. */
    public Map<String, Double> getNativeNamedStates() {
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("E''q", getPsid11());
        states.put("E''d", -getPsiq11());
        states.put("E'q", getEq1());
        states.put("E'd", getEd1());
        states.put("Speed deviation", getSpeed() - 1);
        states.put("Angle", getAngle());
        return Collections.unmodifiableMap(states);
    }

    @Override public Map<String, Double> getNamedStates() {
        Map<String, Double> states = new LinkedHashMap<>();
        // Retain the previously published winding-coordinate aliases for
        // consumers. They are algebraic observations, never integrated states.
        states.put("E'q", getEq1()); states.put("E'd", getEd1());
        states.put("Psi'd", getPsikd()); states.put("Psi'q", getPsikq());
        states.put("Speed deviation", getSpeed() - 1); states.put("Angle", getAngle());
        states.put("E''q", getPsid11()); states.put("E''d", -getPsiq11());
        return Collections.unmodifiableMap(states);
    }

    private final class TypeJEquationSolver extends DynamicSimuAdapterImpl {
        private double eqpp, edpp, eqp, edp;
        private double oldEqpp, oldEdpp, oldEqp, oldEdp;
        private double dEqpp, dEdpp, dEqp, dEdp;
        private double id, iq, airGapFlux, saturation;
        private double pendingDt;
        private boolean pending, initialized;

        @Override public boolean initStates(BaseDStabBus<?, ?> bus) {
            super.initStates(bus);
            Vector_dq current = getInitIdq(), voltage = getVdq();
            id = current.d; iq = current.q;
            airGapFlux = Math.hypot(voltage.q + getRa() * iq + getXl() * id,
                    voltage.d + getRa() * id - getXl() * iq);
            saturation = getSatruationFactor(saturationInput(airGapFlux, Math.hypot(id, iq)));
            double sd = dFactor(saturation), sq = qFactor(saturation);
            eqpp = voltage.q + getRa() * iq + reactance(getXd11(), sd) * id;
            edpp = voltage.d + getRa() * id - reactance(getXq11(), sq) * iq;
            eqp = eqpp + (getXd1() - getXd11()) * id / sd;
            edp = edpp - (getXq1() - getXq11()) * iq / sq;
            projectRemovedWindings();
            initialized = true; pending = false; pendingDt = 0;
            oldEqpp = eqpp; oldEdpp = edpp; oldEqp = eqp; oldEdp = edp;
            dEqpp = dEdpp = dEqp = dEdp = 0;
            publish();
            setEfd(fieldCurrent());
            return true;
        }

        /** Xad*Ifd from the d-axis rotor coordinates, including saturation. */
        private double fieldCurrent() {
            double sd = dFactor(saturation);
            return sd * ((getXd() - getXd11()) * eqp
                    - (getXd() - getXd1()) * eqpp) / (getXd1() - getXd11());
        }

        private void projectRemovedWindings() {
            if (getTq01() == 0) edp = 0;
        }

        @Override public boolean nextStepElectricalModifiedEuler(
                double dt, DynamicSimuMethod method, Network<?, ?> network, int flag) {
            if (method != DynamicSimuMethod.MODIFIED_EULER)
                throw new UnsupportedOperationException("Type-J supports modified Euler integration");
            if (!Double.isFinite(dt) || dt < 0 || (flag != 0 && flag != 1))
                throw new IllegalArgumentException("Invalid Type-J integration stage");
            if (!initialized) throw new IllegalStateException("Type-J is not initialized");
            // Same-time event evaluations refresh algebraic outputs only. Do
            // not overwrite an outstanding predictor origin or its derivative.
            if (dt == 0) { solveCurrent(); publish(); return true; }
            if (flag == 0 && pending)
                throw new IllegalStateException("Type-J predictor is already pending");
            if (flag == 1 && (!pending || Double.doubleToLongBits(dt) != Double.doubleToLongBits(pendingDt)))
                throw new IllegalStateException("Type-J corrector has no matching predictor");
            if(coupledStageActive)solveCurrent(stageVd,stageVq);
            else solveCurrent();
            double sd = dFactor(saturation), sq = qFactor(saturation);
            double fEqpp = (sd * (eqp - eqpp) - (getXd1() - getXd11()) * id) / getTd011();
            double fEdpp = (sq * (edp - edpp) + (getXq1() - getXq11()) * iq) / getTq011();
            double fEqp = (getEfd() - fieldCurrent()) / getTd01();
            double fEdp = getTq01() == 0 ? 0 : sq * ((getXq() - getXq1()) * edpp - (getXq() - getXq11()) * edp)
                            / ((getXq1() - getXq11()) * getTq01());
            if (flag == 0) {
                oldEqpp = eqpp; oldEdpp = edpp; oldEqp = eqp; oldEdp = edp;
                dEqpp = fEqpp; dEdpp = fEdpp; dEqp = fEqp; dEdp = fEdp;
                pendingDt = dt; pending = true;
                eqpp = oldEqpp + dt * fEqpp; edpp = oldEdpp + dt * fEdpp;
                eqp = oldEqp + dt * fEqp; edp = oldEdp + dt * fEdp;
            } else {
                // Replay the accepted start, not the already advanced predictor.
                eqpp = oldEqpp + .5 * dt * (dEqpp + fEqpp);
                edpp = oldEdpp + .5 * dt * (dEdpp + fEdpp);
                eqp = oldEqp + .5 * dt * (dEqp + fEqp);
                edp = oldEdp + .5 * dt * (dEdp + fEdp);
                pending = false;
            }
            projectRemovedWindings();
            solveCurrent();
            publish();
            return true;
        }

        /** Evaluate stator current for a trial nonnegative saturation factor. */
        private double currentAt(double trial, double vd, double vq) {
            double xd = reactance(getXd11(), dFactor(trial));
            double xq = reactance(getXq11(), qFactor(trial));
            // These stored E'' coordinates are internal voltages, not flux
            // states to be multiplied by speed. Off-nominal boundary captures
            // distinguish the two conventions even when equilibrium cannot.
            double dd = edpp - vd, dq = eqpp - vq;
            double determinant = getRa() * getRa() + xd * xq;
            id = (getRa() * dd + xq * dq) / determinant;
            iq = (-xd * dd + getRa() * dq) / determinant;
            // Type-J saturation input is the internal voltage behind leakage;
            // do not silently add a speed divisor to the published definition.
            airGapFlux = Math.hypot(vq + getRa() * iq + getXl() * id,
                    vd + getRa() * id - getXl() * iq);
            return trial - getSatruationFactor(saturationInput(airGapFlux, Math.hypot(id, iq)));
        }

        private void solveCurrent() {
            Vector_dq v = getVdq();solveCurrent(v.d,v.q);
        }

        private void solveCurrent(double vd,double vq) {
            double s = Math.max(0, saturation);
            // Warm Newton iteration normally needs only a few scalar boundary
            // evaluations. Safeguard it rather than stopping after a fixed
            // number of inconsistent saturation/current substitutions.
            for (int i = 0; i < 12; i++) {
                double f = currentAt(s, vd, vq);
                if (Double.isFinite(f) && Math.abs(f) <= EPS * (1 + s)) {
                    saturation = s; return;
                }
                double h = 1e-6 * (1 + s);
                double slope = (currentAt(s + h, vd, vq) - f) / h;
                double next = s - f / slope;
                if (!Double.isFinite(next) || next < 0 || next > 1e6) break;
                s = next;
            }
            double lo = 0, hi = Math.max(1, 2 * s);
            double flo = currentAt(lo, vd, vq), fhi = currentAt(hi, vd, vq);
            while (Double.isFinite(fhi) && fhi < 0 && hi < 1e6) {
                hi *= 2; fhi = currentAt(hi, vd, vq);
            }
            if (!Double.isFinite(flo) || !Double.isFinite(fhi) || flo > 0 || fhi < 0)
                throw new ArithmeticException("Type-J saturation/current boundary has no finite bracket");
            for (int i = 0; i < 80; i++) {
                s = .5 * (lo + hi);
                double f = currentAt(s, vd, vq);
                if (Double.isFinite(f) && (Math.abs(f) <= EPS * (1 + s) || hi - lo <= EPS * (1 + s))) {
                    saturation = s; return;
                }
                if (!Double.isFinite(f)) throw new ArithmeticException("Nonfinite Type-J stator boundary");
                if (f > 0) hi = s; else lo = s;
            }
            throw new ArithmeticException("Type-J saturation/current boundary did not converge");
        }

        private void publish() {
            setEq1(eqp); setEd1(edp); setPsid11(eqpp); setPsiq11(-edpp);
            double ad = (getXd11() - getXl()) / (getXd1() - getXl());
            double aq = (getXq11() - getXl()) / (getXq1() - getXl());
            setPsikd(ad == 1 ? eqp : (eqpp - ad * eqp) / (1 - ad));
            setPsikq(aq == 1 ? edp : (edpp - aq * edp) / (1 - aq));
            double sd = dFactor(saturation), sq = qFactor(saturation);
            double psiD = eqpp - reactance(getXd11(), sd) * id;
            double psiQ = -edpp - reactance(getXq11(), sq) * iq;
            // Exactly Vd*Id + Vq*Iq + Ra*(Id^2+Iq^2), in machine pu.
            // Adding a speed multiplier here would count it twice when the
            // mechanical equation converts this power to electrical torque.
            setPe(psiD * iq - psiQ * id);
        }

        @Override public Object getOutputObject() {
            if (!initialized) throw new IllegalStateException("Type-J is not initialized");
            solveCurrent(); publish();
            Complex terminal = ComplexFunc.createComplex(DStabFunction.transfer(new Vector_dq(id, iq), getAngle()))
                    .multiply(getIMultiFactor());
            Complex voltage = getDStabBus().getVoltage();
            setQGen(voltage.multiply(terminal.conjugate()).getImaginary() / getRating());
            return terminal.add(voltage.multiply(getYgen()));
        }

        @Override public Hashtable<String, Object> getStates(Object ref) {
            Hashtable<String, Object> states = new Hashtable<>();
            states.put(DStabOutSymbol.OUT_SYMBOL_MACH_EQ1, eqp);
            states.put(DStabOutSymbol.OUT_SYMBOL_MACH_ED1, edp);
            states.put(DStabOutSymbol.OUT_SYMBOL_MACH_ED11, eqpp);
            states.put(DStabOutSymbol.OUT_SYMBOL_MACH_EQ11, -edpp);
            states.put("mach.gentpj1.psiag", airGapFlux);
            states.put("mach.gentpj1.saturation", saturation);
            // Historical observation aliases do not imply GENQEC ownership.
            states.put("mach.genqec.psiag", airGapFlux);
            states.put("mach.genqec.vcomp", getCompensatedVoltage());
            return states;
        }
    }
}
