package org.interpss.dstab.control.uel.psse.uel1;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.exc.UnderExcitationLimiterTarget;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.core.net.Network;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.dynLoad.impl.DynLoadModelImpl;
import com.interpss.dstab.mach.Machine;

/** IEEE 421.5 / native PSS/E UEL1 circular under-excitation limiter. */
public final class Uel1UnderExcitationLimiter extends DynLoadModelImpl
        implements ICMLStateProvider {
    private static final double EPS = 1.0e-12;

    private final Uel1Data data;
    private final Machine machine;
    private final UnderExcitationLimiterTarget exciter;
    private final Hashtable<String, Object> states = new Hashtable<>();

    private double integrator;
    private double firstLag;
    private double secondLag;
    private double oldIntegrator;
    private double oldFirstLag;
    private double oldSecondLag;
    private Derivative oldDerivative = Derivative.ZERO;
    private double output;
    private boolean initialized;

    public Uel1UnderExcitationLimiter(BaseDStabBus<?, ?> bus, Machine machine,
            String generatorId, Uel1Data data) {
        if (bus == null || machine == null || data == null) {
            throw new IllegalArgumentException("UEL1 requires a bus, machine, and data");
        }
        if (!(machine.getExciter() instanceof UnderExcitationLimiterTarget target)) {
            throw new IllegalArgumentException("UEL1 requires a compatible exciter");
        }
        this.data = data;
        this.machine = machine;
        this.exciter = target;
        setId(generatorId);
        setName("UEL1");
        setExtendedDeviceId("UEL1_" + generatorId + "@" + bus.getId());
        setLoadPercent(0.0);
        setLoadFactor(0.0);
        setLoadPQ(Complex.ZERO);
        setInitLoadPQ(Complex.ZERO);
        setEquivY(Complex.ZERO);
        setNortonCurInj(Complex.ZERO);
        setCompensateShuntY(Complex.ZERO);
        setCurrInj2Net(Complex.ZERO);
        setDStabBus(bus);
    }

    @Override public boolean initStates() { return initStates(getDStabBus()); }
    @Override public boolean initStates(BaseDStabBus<?, ?> bus, Machine ignored) { return initStates(bus); }
    @Override public boolean initStates(BaseDStabBus<?, ?> bus, Network<?, ?> ignored) { return initStates(bus); }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus) {
        if (bus == null || bus != getDStabBus() || machine.getExciter() != exciter) return false;
        initializeWithError(initialError());
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        initialized = finite(integrator, firstLag, secondLag, output);
        return initialized;
    }

    /** Reset the three controller states for a prescribed error-signal playback. */
    public void initializeWithError(double errorSignal) {
        integrator = 0.0;
        double pi = piOutput(errorSignal, integrator);
        firstLag = pi;
        secondLag = leadLagOutput(pi, firstLag, data.tu1(), data.tu2());
        output = clamp(leadLagOutput(secondLag, secondLag, data.tu3(), data.tu4()),
                data.vulmin(), data.vulmax());
        exciter.setVuel(output);
        initialized = finite(integrator, firstLag, secondLag, output);
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        return nextStepWithErrorSignal(dt, method, flag, error());
    }

    /** Advance the limiter with an externally supplied VUerr validation signal. */
    public boolean nextStepWithErrorSignal(double dt, DynamicSimuMethod method, int flag,
            double errorSignal) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) {
            throw new InterpssRuntimeException("UEL1 supports MODIFIED_EULER only");
        }
        if (!initialized || dt < 0.0 || !Double.isFinite(dt)) return false;
        if (flag == 0) {
            oldIntegrator = integrator;
            oldFirstLag = firstLag;
            oldSecondLag = secondLag;
            oldDerivative = derivative(errorSignal, integrator, firstLag, secondLag);
            integrator += dt * oldDerivative.integrator();
            firstLag = advanceLag(firstLag, oldDerivative.firstLag(), dt, data.tu2(),
                    piOutput(errorSignal, integrator));
            double firstOutput = leadLagOutput(piOutput(errorSignal, integrator), firstLag,
                    data.tu1(), data.tu2());
            secondLag = advanceLag(secondLag, oldDerivative.secondLag(), dt, data.tu4(), firstOutput);
        } else if (flag == 1) {
            Derivative corrected = derivative(errorSignal, integrator, firstLag, secondLag);
            integrator = oldIntegrator + 0.5 * dt
                    * (oldDerivative.integrator() + corrected.integrator());
            double pi = piOutput(errorSignal, integrator);
            firstLag = correctedLag(oldFirstLag, oldDerivative.firstLag(), corrected.firstLag(),
                    dt, data.tu2(), pi);
            double firstOutput = leadLagOutput(pi, firstLag, data.tu1(), data.tu2());
            secondLag = correctedLag(oldSecondLag, oldDerivative.secondLag(), corrected.secondLag(),
                    dt, data.tu4(), firstOutput);
        } else {
            throw new InterpssRuntimeException("UEL1 invalid integration flag: " + flag);
        }
        output = calculateOutput(errorSignal, integrator, firstLag, secondLag);
        exciter.setVuel(output);
        return finite(integrator, firstLag, secondLag, output);
    }

    private Derivative derivative(double err, double integral, double lag1, double lag2) {
        double rawPi = data.kul() * err + integral;
        double di = data.kui() * err;
        if ((rawPi >= data.vuimax() && di > 0.0)
                || (rawPi <= data.vuimin() && di < 0.0)) di = 0.0;
        double pi = clamp(rawPi, data.vuimin(), data.vuimax());
        double d1 = data.tu2() > EPS ? (pi - lag1) / data.tu2() : 0.0;
        double y1 = leadLagOutput(pi, lag1, data.tu1(), data.tu2());
        double d2 = data.tu4() > EPS ? (y1 - lag2) / data.tu4() : 0.0;
        return new Derivative(di, d1, d2);
    }

    private double calculateOutput(double err, double integral, double lag1, double lag2) {
        double pi = piOutput(err, integral);
        double y1 = leadLagOutput(pi, lag1, data.tu1(), data.tu2());
        return clamp(leadLagOutput(y1, lag2, data.tu3(), data.tu4()),
                data.vulmin(), data.vulmax());
    }

    private double error() {
        Complex vt = machine.getDStabBus().getVoltage();
        Complex it = machine.getIxy().divide(machine.getIMultiFactor());
        return phasorError(vt, it);
    }

    private double initialError() {
        Complex vt = machine.getDStabBus().getVoltage();
        Complex power = machine.getParentGen().getGen();
        Complex it = power.divide(vt).conjugate().divide(machine.getIMultiFactor());
        return phasorError(vt, it);
    }

    private double phasorError(Complex vt, Complex it) {
        double vuc = Math.min(data.vucmax(),
                vt.multiply(data.kuc()).subtract(Complex.I.multiply(it)).abs());
        double vur = Math.min(data.vurmax(), vt.abs() * data.kur());
        return vuc - vur - data.kuf() * exciter.getUelStabilizingSignal();
    }

    private double piOutput(double err, double integral) {
        return clamp(data.kul() * err + integral, data.vuimin(), data.vuimax());
    }

    private static double leadLagOutput(double input, double state, double lead, double lag) {
        if (lag <= EPS) return input;
        double ratio = lead / lag;
        return ratio * input + (1.0 - ratio) * state;
    }

    private static double advanceLag(double state, double derivative, double dt,
            double lag, double algebraicInput) {
        return lag <= EPS ? algebraicInput : state + dt * derivative;
    }

    private static double correctedLag(double oldState, double oldDerivative,
            double correctedDerivative, double dt, double lag, double algebraicInput) {
        return lag <= EPS ? algebraicInput
                : oldState + 0.5 * dt * (oldDerivative + correctedDerivative);
    }

    @Override public double getOutput() { return output; }
    @Override public Object getOutputObject() { return Complex.ZERO; }
    @Override public boolean updateAttributes(boolean netChange) { return true; }
    @Override public boolean afterStep(double dt) { return true; }

    @Override
    public Hashtable<String, Object> getStates(Object ref) {
        states.put("UEL1_OUTPUT", output);
        states.put("UEL1_ERROR", error());
        return states;
    }

    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("Integrator", integrator);
        result.put("First lead-lag", firstLag);
        result.put("Second lead-lag", secondLag);
        return Map.copyOf(result);
    }

    public Uel1Data getData() { return data; }
    public Machine getMachine() { return machine; }
    public double getError() { return error(); }
    public double getIntegratorState() { return integrator; }
    public double getFirstLeadLagState() { return firstLag; }
    public double getSecondLeadLagState() { return secondLag; }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private record Derivative(double integrator, double firstLag, double secondLag) {
        private static final Derivative ZERO = new Derivative(0.0, 0.0, 0.0);
    }
}
