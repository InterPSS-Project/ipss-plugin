package org.interpss.dstab.svc;

import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;

import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.device.DynamicBusDeviceType;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/**
 * WECC/PSS/E SVSMO1T2 continuously controlled static-var system.
 *
 * <p>The five continuous coordinates are the two published lead-lag internal
 * coordinates, the fast non-windup PI integrator, the firing lag, and the slow
 * susceptance-regulator integrator. The network's solved switched-shunt
 * susceptance remains in the dynamic Y matrix, so this device injects only the
 * change from that solved value and cannot double count the initial MVAr.</p>
 */
public final class Svsmo1t2Model extends DynamicBusDeviceImpl
        implements ICMLStateProvider {
    private static final double EPS = 1.0e-12;

    private final Svsmo1t2Data data;
    private final SwitchedShunt switchedShunt;
    private final BaseDStabBus<?, ?> remoteBus;
    private final Hashtable<String, Object> states = new Hashtable<>();
    private double systemBaseMva;
    private double initialB;
    private double voltageReference;
    private double voltageLeadLagState;
    private double gainLeadLagState;
    private double fastIntegrator;
    private double susceptance;
    private double slowIntegrator;
    private double slowBias;
    private boolean deadbandLocked = true;
    private boolean tripped;
    private double deadbandTimer;
    private double uv1Timer;
    private double uvTripTimer;
    private double ov1Timer;
    private double ov2Timer;
    private double pllTimer;
    private double shortTimer;
    private ModelState predictorStart;
    private Derivatives predictorRates;

    public Svsmo1t2Model(BaseDStabBus<?, ?> bus, BaseDStabBus<?, ?> remoteBus,
            SwitchedShunt switchedShunt, String id, Svsmo1t2Data data) {
        this.data = data;
        this.remoteBus = remoteBus;
        this.switchedShunt = switchedShunt;
        setDeviceType(DynamicBusDeviceType.DYNAMIC_LOAD);
        setDStabBus(bus);
        setName("SVSMO1T2");
        setId(id);
        setExtendedDeviceId("SVSMO1T2_" + id + "@" + bus.getId());
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus) {
        if (bus == null || remoteBus == null || switchedShunt == null
                || !switchedShunt.isActive()) return false;
        systemBaseMva = bus.getNetwork().getBaseMva();
        initialB = switchedShunt.getBActual();
        susceptance = initialB;
        if (susceptance < data.bMin() - EPS || susceptance > data.bShort() + EPS) {
            return false;
        }
        double sensed = remoteBus.getVoltageMag();
        voltageLeadLagState = leadLagSteadyState(sensed, data.tc1(), data.tb1());
        double compensated = sensed + slope(susceptance) * bus.getVoltageMag() * susceptance;
        voltageReference = compensated;
        gainLeadLagState = leadLagSteadyState(0.0, data.tc2(), data.tb2());
        fastIntegrator = susceptance;
        slowIntegrator = 0.0;
        slowBias = 0.0;
        predictorStart = null;
        predictorRates = null;
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        return finiteState();
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        if (flag != 0 && flag != 1) return false;
        Endpoint endpoint = endpoint();
        if (dt <= 0.0) {
            apply(bypasses(state(), endpoint));
            return finiteState();
        }
        if (flag == 0) {
            predictorStart = state();
            predictorRates = derivatives(predictorStart, endpoint);
            apply(bypasses(add(predictorStart, predictorRates, dt), endpoint));
        } else {
            if (predictorStart == null || predictorRates == null) return false;
            Derivatives corrected = derivatives(state(), endpoint);
            apply(bypasses(correct(predictorStart, predictorRates, corrected, dt), endpoint));
            updateDiscreteTimers(dt, endpoint.sensedVoltage());
            predictorStart = null;
            predictorRates = null;
        }
        susceptance = clamp(susceptance, activeBMin(), activeBMax());
        return finiteState();
    }

    private Endpoint endpoint() {
        double sensed = remoteBus == null ? 1.0 : remoteBus.getVoltageMag();
        double localV = getDStabBus() == null ? sensed : getDStabBus().getVoltageMag();
        double measured = leadLagOutput(sensed, voltageLeadLagState, data.tc1(), data.tb1());
        double compensated = measured + slope(susceptance) * localV * susceptance;
        double rawError = voltageReference + slowBias - compensated;
        if (deadbandLocked && Math.abs(rawError) <= data.deadbandOuter()) rawError = 0.0;
        return new Endpoint(sensed, localV, clamp(rawError, data.veMin(), data.veMax()));
    }

    private Derivatives derivatives(ModelState state, Endpoint endpoint) {
        double voltageRate = leadLagStateRate(endpoint.sensedVoltage(),
                state.voltageLeadLagState(), data.tc1(), data.tb1());
        double gainRate = leadLagStateRate(endpoint.voltageError(),
                state.gainLeadLagState(), data.tc2(), data.tb2());
        double gainOutput = leadLagOutput(endpoint.voltageError(),
                state.gainLeadLagState(), data.tc2(), data.tb2());

        double proportional = data.kpv() * gainOutput;
        double fastRate = data.kiv() * gainOutput;
        double low = activeBMin() - proportional;
        double high = activeBMax() - proportional;
        if ((state.fastIntegrator() >= high && fastRate > 0.0)
                || (state.fastIntegrator() <= low && fastRate < 0.0)) fastRate = 0.0;
        double bCommand = clamp(state.fastIntegrator() + proportional,
                activeBMin(), activeBMax());
        if (tripped) bCommand = 0.0;
        double firingRate = lagRate(bCommand, state.susceptance(), data.firingTime());

        double epsilon = data.epsilonMvar() / Math.max(systemBaseMva, EPS);
        double smallInd = data.bSmallIndMvar() / Math.max(systemBaseMva, EPS);
        double smallCap = data.bSmallCapMvar() / Math.max(systemBaseMva, EPS);
        double bReference = state.susceptance();
        if (state.susceptance() < smallInd) bReference = smallInd + epsilon;
        else if (state.susceptance() > smallCap) bReference = smallCap - epsilon;
        double slowError = bReference - state.susceptance();
        double slowProportional = data.kps() * slowError;
        double slowRate = data.kis() * slowError;
        double slowLow = data.vrMin() - slowProportional;
        double slowHigh = data.vrMax() - slowProportional;
        if ((state.slowIntegrator() >= slowHigh && slowRate > 0.0)
                || (state.slowIntegrator() <= slowLow && slowRate < 0.0)) slowRate = 0.0;
        return new Derivatives(voltageRate, gainRate, fastRate, firingRate, slowRate);
    }

    private ModelState bypasses(ModelState state, Endpoint endpoint) {
        double voltageState = data.tb1() <= EPS
                ? endpoint.sensedVoltage() : state.voltageLeadLagState();
        double gainState = data.tb2() <= EPS
                ? endpoint.voltageError() : state.gainLeadLagState();
        return new ModelState(voltageState, gainState, state.fastIntegrator(),
                state.susceptance(), state.slowIntegrator());
    }

    private void updateDiscreteTimers(double dt, double voltage) {
        double rawError = voltageReference + slowBias - voltage
                - slope(susceptance) * getDStabBus().getVoltageMag() * susceptance;
        if (deadbandLocked) {
            if (Math.abs(rawError) > data.deadbandOuter()) {
                deadbandLocked = false;
                deadbandTimer = 0.0;
            }
        } else if (Math.abs(rawError) < data.deadbandInner()) {
            deadbandTimer += dt;
            if (deadbandTimer >= data.deadbandTime()) deadbandLocked = true;
        } else deadbandTimer = 0.0;

        uv1Timer = voltage < data.uv1() ? uv1Timer + dt : 0.0;
        uvTripTimer = voltage < data.uvTrip() ? uvTripTimer + dt : 0.0;
        ov1Timer = voltage > data.ov1() ? ov1Timer + dt : 0.0;
        ov2Timer = voltage > data.ov2() ? ov2Timer + dt : 0.0;
        if (uv1Timer >= data.uvTime1()) pllTimer += dt;
        else pllTimer = 0.0;
        shortTimer = susceptance > data.bMax() ? shortTimer + dt : 0.0;
        if ((data.uvTime2() >= 0.0 && uvTripTimer >= data.uvTime2())
                || (data.ovTime1() >= 0.0 && ov1Timer >= data.ovTime1())
                || (data.ovTime2() >= 0.0 && ov2Timer >= data.ovTime2())) tripped = true;

        double slowOutput = clamp(slowIntegrator, data.vrMin(), data.vrMax());
        slowBias = clamp(slowOutput,
                data.vrefMin() - voltageReference,
                data.vrefMax() - voltageReference);
    }

    private double activeBMax() {
        if (tripped) return 0.0;
        double voltage = remoteBus == null ? 1.0 : remoteBus.getVoltageMag();
        if (voltage < data.uv2() || voltage > data.ov1()) return data.bMin();
        if (uv1Timer >= data.uvTime1()) return Math.min(data.uvSbMax(), data.bShort());
        if (pllTimer > 0.0 && pllTimer < data.pllDelay()) return Math.min(data.uvSbMax(), data.bShort());
        return shortTimer >= data.shortTime() ? data.bMax() : data.bShort();
    }

    private double activeBMin() {
        return tripped ? 0.0 : data.bMin();
    }

    private double slope(double b) {
        if (data.slopeFlag() == 0) return data.xc1();
        double voltage = remoteBus == null ? 1.0 : remoteBus.getVoltageMag();
        if (voltage >= data.vUpper()) return data.xc1();
        if (voltage > data.vLower()) return data.xc2();
        return data.xc3();
    }

    @Override
    public Object getOutputObject() {
        Complex voltage = getDStabBus().getVoltage();
        return voltage.multiply(new Complex(0.0, -(susceptance - initialB)));
    }

    @Override public boolean updateAttributes(boolean netChange) { return finiteState(); }
    @Override public boolean afterStep(double dt) { return true; }

    @Override
    public Hashtable<String, Object> getStates(Object ref) {
        states.put("SVSMO1T2_VOLTAGE_LEAD_LAG", voltageLeadLagState);
        states.put("SVSMO1T2_GAIN_LEAD_LAG", gainLeadLagState);
        states.put("SVSMO1T2_FAST_PI", fastIntegrator);
        states.put("SVSMO1T2_B", susceptance);
        states.put("SVSMO1T2_SLOW_PI", slowIntegrator);
        return states;
    }

    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> named = new LinkedHashMap<>();
        named.put("Voltage lead-lag state", voltageLeadLagState);
        named.put("Transient-gain lead-lag state", gainLeadLagState);
        named.put("Fast voltage PI integrator", fastIntegrator);
        named.put("SVC susceptance", susceptance);
        named.put("Slow susceptance PI integrator", slowIntegrator);
        named.put("Slow voltage bias", slowBias);
        named.put("Voltage reference", voltageReference);
        return Collections.unmodifiableMap(named);
    }

    public Svsmo1t2Data getData() { return data; }
    public SwitchedShunt getSwitchedShunt() { return switchedShunt; }
    public BaseDStabBus<?, ?> getRemoteBus() { return remoteBus; }
    public double getVoltageReference() { return voltageReference; }
    public double getVoltageLeadLagState() { return voltageLeadLagState; }
    public double getGainLeadLagState() { return gainLeadLagState; }
    public double getFastIntegrator() { return fastIntegrator; }
    public double getSusceptance() { return susceptance; }
    public double getSlowIntegrator() { return slowIntegrator; }
    public double getSlowBias() { return slowBias; }
    public boolean isTripped() { return tripped; }
    public boolean isDeadbandLocked() { return deadbandLocked; }
    public double getVoltageMeasurement() {
        double sensed = remoteBus == null ? 1.0 : remoteBus.getVoltageMag();
        return leadLagOutput(sensed, voltageLeadLagState, data.tc1(), data.tb1());
    }
    public double getVoltageError() { return endpoint().voltageError(); }
    public double getGainLeadLagOutput() {
        double error = endpoint().voltageError();
        return leadLagOutput(error, gainLeadLagState, data.tc2(), data.tb2());
    }
    public double getFastControllerOutput() {
        return clamp(fastIntegrator + data.kpv() * getGainLeadLagOutput(),
                activeBMin(), activeBMax());
    }
    public double getMvarOutput() {
        double voltage = getDStabBus().getVoltageMag();
        return susceptance * voltage * voltage * systemBaseMva;
    }

    private ModelState state() {
        return new ModelState(voltageLeadLagState, gainLeadLagState,
                fastIntegrator, susceptance, slowIntegrator);
    }

    private void apply(ModelState state) {
        voltageLeadLagState = state.voltageLeadLagState();
        gainLeadLagState = state.gainLeadLagState();
        fastIntegrator = state.fastIntegrator();
        susceptance = state.susceptance();
        slowIntegrator = state.slowIntegrator();
    }

    private static ModelState add(ModelState state, Derivatives rate, double dt) {
        return new ModelState(state.voltageLeadLagState() + dt * rate.voltageLeadLagState(),
                state.gainLeadLagState() + dt * rate.gainLeadLagState(),
                state.fastIntegrator() + dt * rate.fastIntegrator(),
                state.susceptance() + dt * rate.susceptance(),
                state.slowIntegrator() + dt * rate.slowIntegrator());
    }

    private static ModelState correct(ModelState start, Derivatives first,
            Derivatives second, double dt) {
        return new ModelState(start.voltageLeadLagState() + .5 * dt
                        * (first.voltageLeadLagState() + second.voltageLeadLagState()),
                start.gainLeadLagState() + .5 * dt
                        * (first.gainLeadLagState() + second.gainLeadLagState()),
                start.fastIntegrator() + .5 * dt
                        * (first.fastIntegrator() + second.fastIntegrator()),
                start.susceptance() + .5 * dt
                        * (first.susceptance() + second.susceptance()),
                start.slowIntegrator() + .5 * dt
                        * (first.slowIntegrator() + second.slowIntegrator()));
    }

    private static double lagRate(double input, double state, double timeConstant) {
        return timeConstant <= EPS ? 0.0 : (input - state) / timeConstant;
    }

    private static double leadLagOutput(double input, double state,
            double numeratorTime, double denominatorTime) {
        if (denominatorTime <= EPS) return input;
        return state + numeratorTime / denominatorTime * input;
    }

    private static double leadLagStateRate(double input, double state,
            double numeratorTime, double denominatorTime) {
        if (denominatorTime <= EPS) return 0.0;
        double ratio = numeratorTime / denominatorTime;
        return ((1.0 - ratio) * input - state) / denominatorTime;
    }

    private static double leadLagSteadyState(double input,
            double numeratorTime, double denominatorTime) {
        return denominatorTime <= EPS ? 0.0
                : (1.0 - numeratorTime / denominatorTime) * input;
    }

    private boolean finiteState() {
        return Double.isFinite(voltageLeadLagState) && Double.isFinite(gainLeadLagState)
                && Double.isFinite(fastIntegrator) && Double.isFinite(susceptance)
                && Double.isFinite(slowIntegrator) && Double.isFinite(slowBias);
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private record Endpoint(double sensedVoltage, double localVoltage,
            double voltageError) { }
    private record ModelState(double voltageLeadLagState, double gainLeadLagState,
            double fastIntegrator, double susceptance, double slowIntegrator) { }
    private record Derivatives(double voltageLeadLagState, double gainLeadLagState,
            double fastIntegrator, double susceptance, double slowIntegrator) { }
}
