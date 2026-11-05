package org.interpss.dstab.renewable;

import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.IntegrationStateMonitor.Stage;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.device.DynamicGenDevice;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/** WECC REGFM_A1 droop-controlled grid-forming inverter. */
public final class Regfma1Model extends DynamicBusDeviceImpl
        implements DynamicGenDevice, ICMLStateProvider, IntegrationStateAccess,
        StaggeredEulerParticipant {
    private static final double EPS = 1.0e-9;

    private final Regfma1Data data;
    private final Hashtable<String, Object> states = new Hashtable<>();
    private DStabGen parentGen;
    private double deviceBaseMva;
    private double systemBaseMva;
    private double p;
    private double q;
    private double pMeasured;
    private double qMeasured;
    private double vMeasured;
    private double pReference;
    private double qReference;
    private double vReference;
    private double prefOffset;
    private double qvOffset;
    private double angle;
    private double eDroop;
    private double voltageIntegral;
    private double pUpperIntegral;
    private double pLowerIntegral;
    private double qUpperIntegral;
    private double qLowerIntegral;
    private double speed = 1.0;
    private boolean currentLimited;
    private Repca1Model plantController;
    private ModelState predictorStart;
    private ModelDerivatives predictorDerivatives;
    private boolean initialized;
    private final IntegrationStateMonitor stateMonitor = new IntegrationStateMonitor(
            "Angle", "Measured Active Power", "Measured Reactive Power", "Measured Voltage",
            "Voltage Integral", "Active Upper Limit Integral", "Active Lower Limit Integral",
            "Reactive Upper Limit Integral", "Reactive Lower Limit Integral");

    public Regfma1Model(DStabGen parentGen, BaseDStabBus<?, ?> bus, String id,
            Regfma1Data data) {
        this.data = data;
        setParentGen(parentGen);
        setDStabBus(bus);
        setId(id);
        setExtendedDeviceId("REGFMA1_" + id + "@" + bus.getId());
        // PSS/E stores the source impedance on machine base.  The core applies
        // getZMultiFactor() when assembling the system-base dynamic Y matrix.
        parentGen.setPosGenZ(new Complex(data.re(), data.xe()));
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus) {
        initialized = false;
        stateMonitor.reset();
        if (parentGen == null || bus == null || parentGen.getGen() == null) return false;
        systemBaseMva = bus.getNetwork().getBaseMva();
        deviceBaseMva = parentGen.getMvaBase() > EPS ? parentGen.getMvaBase() : systemBaseMva;
        // The core forms its dynamic matrix after device initialization. Do not
        // derive E0 with the untouched default multiplier and then inject through
        // a differently scaled impedance once the matrix sets the machine base.
        double impedanceBase = parentGen.getMvaBase() > EPS
                ? com.interpss.core.funcImpl.AcscFunction.zMultiFactor.apply(parentGen)
                : systemBaseMva / deviceBaseMva;
        if (!finite(impedanceBase) || impedanceBase <= 0.0) return false;
        parentGen.setZMultiFactor(impedanceBase);
        double scale = systemBaseMva / deviceBaseMva;
        Complex voltage = bus.getVoltage();
        Complex systemPower = parentGen.getGen();
        Complex power = systemPower.multiply(scale);
        Complex systemCurrent = systemPower.divide(nonzero(voltage)).conjugate();
        Complex internal = voltage.add(couplingImpedance().multiply(systemCurrent));

        p = pMeasured = pReference = power.getReal();
        q = qMeasured = power.getImaginary();
        vMeasured = voltage.abs();
        angle = internal.getArgument();
        eDroop = internal.abs();
        qReference = data.qvflag() == 0 ? q : 0.0;
        // Vflag=0 bypasses the terminal-voltage PI controller, so the direct
        // voltage command must initialize to the internal source magnitude.
        // Vflag<>0 instead initializes Vcmd to measured terminal voltage and
        // lets the PI integrator supply the required internal voltage.
        double initializedVoltageCommand = data.vflag() == 0 ? eDroop : vMeasured;
        vReference = initializedVoltageCommand
                - data.mq() * (qReference - qMeasured);
        voltageIntegral = eDroop;
        pUpperIntegral = pLowerIntegral = qUpperIntegral = qLowerIntegral = 0.0;
        speed = 1.0;
        currentLimited = false;
        predictorStart = null;
        predictorDerivatives = null;
        if (plantController != null) plantController.initialize(p, q, vMeasured);
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        initialized = finiteState();
        return initialized;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        if (flag != 0 && flag != 1) return false;
        Complex voltage = getDStabBus().getVoltage();
        if (plantController != null && flag == 0) {
            // REPCA1 is still integrated through the established composed-device
            // contract. Activating its corrector here alone would stage only one
            // part of the renewable cascade and consume inconsistent endpoints.
            plantController.step(dt, p, q, voltage.abs(), getDStabBus().getFreq());
            prefOffset = plantController.getPref();
            qvOffset = plantController.getQref();
        }
        Endpoint endpoint = new Endpoint(p, q, voltage.abs());
        if (dt <= 0.0) {
            apply(applyBypasses(state(), endpoint));
            updateAlgebraicOutputs(state());
            return finiteState();
        }
        if (flag == 0) {
            predictorStart = state();
            predictorDerivatives = derivatives(predictorStart, endpoint);
            observe(predictorStart, predictorDerivatives, Stage.PREDICTOR);
            apply(applyBypasses(add(predictorStart, predictorDerivatives, dt), endpoint));
        } else {
            if (predictorStart == null || predictorDerivatives == null) return false;
            ModelDerivatives corrected = derivatives(state(), endpoint);
            observe(state(), corrected, Stage.CORRECTOR);
            apply(applyBypasses(correct(predictorStart, predictorDerivatives,
                    corrected, dt), endpoint));
            predictorStart = null;
            predictorDerivatives = null;
        }
        updateAlgebraicOutputs(state());
        return finiteState();
    }

    /** Nine stored coordinates; bypassed filters and inactive PI storage have zero rates. */
    @Override public int staggeredStateCount() { return 9; }

    @Override public void readStaggeredState(double[] values, int offset) {
        values[offset] = angle;
        values[offset+1] = pMeasured;
        values[offset+2] = qMeasured;
        values[offset+3] = vMeasured;
        values[offset+4] = voltageIntegral;
        values[offset+5] = pUpperIntegral;
        values[offset+6] = pLowerIntegral;
        values[offset+7] = qUpperIntegral;
        values[offset+8] = qLowerIntegral;
    }

    private void requireStaggeredSupport() {
        if (!initialized) throw new IllegalStateException("Initialize REGFMA1 before evaluating states");
        if (plantController != null)
            throw new IllegalStateException("Staggered REGFMA1 plant-controller coupling is not supported");
    }

    @Override public void evaluateStaggered(double stepSec, double[] rates, int offset) {
        requireStaggeredSupport();
        // Refresh terminal power at the solved voltage without advancing any
        // differential coordinate or predictor/corrector history.
        updateAlgebraicOutputs(state());
        getOutputObject();
        ModelState effective = applyBypasses(state(), new Endpoint(p, q, getDStabBus().getVoltageMag()));
        ModelDerivatives rate = derivatives(effective, new Endpoint(p, q, getDStabBus().getVoltageMag()));
        updateAlgebraicOutputs(effective);
        rates[offset] = rate.angle();
        rates[offset+1] = rate.pMeasured();
        rates[offset+2] = rate.qMeasured();
        rates[offset+3] = rate.vMeasured();
        rates[offset+4] = rate.voltageIntegral();
        rates[offset+5] = rate.pUpperIntegral();
        rates[offset+6] = rate.pLowerIntegral();
        rates[offset+7] = rate.qUpperIntegral();
        rates[offset+8] = rate.qLowerIntegral();
        observe(effective, rate, Stage.STAGGERED);
    }

    @Override public void applyStaggeredState(double[] values, int offset, double stepSec) {
        requireStaggeredSupport();
        for (int i=0; i<staggeredStateCount(); i++)
            if (!Double.isFinite(values[offset+i]))
                throw new IllegalArgumentException("Non-finite REGFMA1 integration coordinate " + i);
        ModelState trial = new ModelState(values[offset], values[offset+1], values[offset+2],
                values[offset+3], values[offset+4], values[offset+5], values[offset+6],
                values[offset+7], values[offset+8]);
        apply(applyBypasses(trial, new Endpoint(p, q, getDStabBus().getVoltageMag())));
        updateAlgebraicOutputs(state());
    }

    private void observe(ModelState x, ModelDerivatives rate, Stage stage) {
        stateMonitor.begin(stage);
        stateMonitor.record(0, x.angle(), rate.angle());
        stateMonitor.record(1, x.pMeasured(), rate.pMeasured());
        stateMonitor.record(2, x.qMeasured(), rate.qMeasured());
        stateMonitor.record(3, x.vMeasured(), rate.vMeasured());
        stateMonitor.record(4, x.voltageIntegral(), rate.voltageIntegral());
        stateMonitor.record(5, x.pUpperIntegral(), rate.pUpperIntegral());
        stateMonitor.record(6, x.pLowerIntegral(), rate.pLowerIntegral());
        stateMonitor.record(7, x.qUpperIntegral(), rate.qUpperIntegral());
        stateMonitor.record(8, x.qLowerIntegral(), rate.qLowerIntegral());
    }

    @Override public Map<String, Double> getIntegrationStates() {
        Map<String, Double> result = new LinkedHashMap<>(getNamedStates());
        result.keySet().removeAll(java.util.List.of("Speed", "Internal Voltage", "Active Power", "Reactive Power"));
        return Collections.unmodifiableMap(result);
    }

    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot() {
        return stateMonitor.snapshot();
    }

    private ModelDerivatives derivatives(ModelState state, Endpoint endpoint) {
        double pRate = lagRate(state.pMeasured(), endpoint.p(), data.tpf());
        double qRate = lagRate(state.qMeasured(), endpoint.q(), data.tqf());
        double vRate = lagRate(state.vMeasured(), endpoint.v(), data.tvf());

        double pHighError = data.pmax() - state.pMeasured();
        double pLowError = data.pmin() - state.pMeasured();
        double pUpperRate = upperBoundedRate(state.pUpperIntegral(),
                data.kipmax() * pHighError);
        double pLowerRate = lowerBoundedRate(state.pLowerIntegral(),
                data.kipmax() * pLowError);
        double qHighError = data.qmax() - state.qMeasured();
        double qLowError = data.qmin() - state.qMeasured();
        double qUpperRate = upperBoundedRate(state.qUpperIntegral(),
                data.kiqmax() * qHighError);
        double qLowerRate = lowerBoundedRate(state.qLowerIntegral(),
                data.kiqmax() * qLowError);

        double voltageRate = 0.0;
        if (data.vflag() != 0) {
            double error = voltageCommand(state) - state.vMeasured();
            if (nativeVoltageIntegralLimits()) {
                // Native PSS/E s8 has its own Emin/Emax limiter; saturation
                // of Kpv*error+s8 must not freeze an interior integral.
                double integral=state.voltageIntegral();
                voltageRate=((integral>=data.emax() && error>0.0)
                        || (integral<=data.emin() && error<0.0)) ? 0.0 : data.kiv()*error;
            } else {
                voltageRate = limitedIntegralRate(state.voltageIntegral(), data.kiv(), error,
                        data.kpv(), data.emin(), data.emax());
            }
        }
        double angleRate = 2.0 * Math.PI * getDStabBus().getNetwork().getFrequency()
                * frequencyDeviation(state);
        return new ModelDerivatives(angleRate, pRate, qRate, vRate, voltageRate,
                pUpperRate, pLowerRate, qUpperRate, qLowerRate);
    }

    private double frequencyDeviation(ModelState state) {
        double highError = data.pmax() - state.pMeasured();
        double lowError = data.pmin() - state.pMeasured();
        double limitOutput = Math.min(0.0,
                data.kppmax() * highError + state.pUpperIntegral())
                + Math.max(0.0, data.kppmax() * lowError + state.pLowerIntegral());
        return data.mp() * (pReference + prefOffset - state.pMeasured()) + limitOutput;
    }

    private double voltageCommand(ModelState state) {
        double highError = data.qmax() - state.qMeasured();
        double lowError = data.qmin() - state.qMeasured();
        double limitOutput = Math.min(0.0,
                data.kpqmax() * highError + state.qUpperIntegral())
                + Math.max(0.0, data.kpqmax() * lowError + state.qLowerIntegral());
        double voltageOffset = data.qvflag() != 0 ? qvOffset : 0.0;
        double reactiveOffset = data.qvflag() == 0 ? qvOffset : 0.0;
        return vReference + voltageOffset
                + data.mq() * (qReference + reactiveOffset - state.qMeasured())
                + limitOutput;
    }

    private void updateAlgebraicOutputs(ModelState state) {
        double frequencyDeviation = frequencyDeviation(state);
        speed = 1.0 + frequencyDeviation;
        double vCommand = voltageCommand(state);
        if (data.vflag() == 0) {
            eDroop = limit(vCommand, data.emin(), data.emax());
        } else {
            double error = vCommand - state.vMeasured();
            eDroop = limit(data.kpv() * error + state.voltageIntegral(),
                    data.emin(), data.emax());
        }
    }

    private ModelState state() {
        return new ModelState(angle, pMeasured, qMeasured, vMeasured, voltageIntegral,
                pUpperIntegral, pLowerIntegral, qUpperIntegral, qLowerIntegral);
    }

    private void apply(ModelState state) {
        angle = state.angle();
        pMeasured = state.pMeasured();
        qMeasured = state.qMeasured();
        vMeasured = state.vMeasured();
        voltageIntegral = state.voltageIntegral();
        pUpperIntegral = state.pUpperIntegral();
        pLowerIntegral = state.pLowerIntegral();
        qUpperIntegral = state.qUpperIntegral();
        qLowerIntegral = state.qLowerIntegral();
    }

    private ModelState add(ModelState state, ModelDerivatives rate, double dt) {
        return new ModelState(
                state.angle() + dt * rate.angle(),
                state.pMeasured() + dt * rate.pMeasured(),
                state.qMeasured() + dt * rate.qMeasured(),
                state.vMeasured() + dt * rate.vMeasured(),
                state.voltageIntegral() + dt * rate.voltageIntegral(),
                state.pUpperIntegral() + dt * rate.pUpperIntegral(),
                state.pLowerIntegral() + dt * rate.pLowerIntegral(),
                state.qUpperIntegral() + dt * rate.qUpperIntegral(),
                state.qLowerIntegral() + dt * rate.qLowerIntegral());
    }

    private ModelState correct(ModelState start, ModelDerivatives first,
            ModelDerivatives second, double dt) {
        return add(start, new ModelDerivatives(
                .5 * (first.angle() + second.angle()),
                .5 * (first.pMeasured() + second.pMeasured()),
                .5 * (first.qMeasured() + second.qMeasured()),
                .5 * (first.vMeasured() + second.vMeasured()),
                .5 * (first.voltageIntegral() + second.voltageIntegral()),
                .5 * (first.pUpperIntegral() + second.pUpperIntegral()),
                .5 * (first.pLowerIntegral() + second.pLowerIntegral()),
                .5 * (first.qUpperIntegral() + second.qUpperIntegral()),
                .5 * (first.qLowerIntegral() + second.qLowerIntegral())), dt);
    }

    private ModelState applyBypasses(ModelState state, Endpoint endpoint) {
        return new ModelState(state.angle(),
                data.tpf() <= EPS ? endpoint.p() : state.pMeasured(),
                data.tqf() <= EPS ? endpoint.q() : state.qMeasured(),
                data.tvf() <= EPS ? endpoint.v() : state.vMeasured(),
                nativeVoltageIntegralLimits() && data.vflag()!=0
                        ? limit(state.voltageIntegral(),data.emin(),data.emax()) : state.voltageIntegral(),
                Math.min(0.0, state.pUpperIntegral()),
                Math.max(0.0, state.pLowerIntegral()),
                Math.min(0.0, state.qUpperIntegral()),
                Math.max(0.0, state.qLowerIntegral()));
    }

    private boolean finiteState() {
        return finite(eDroop) && finite(angle) && finite(speed)
                && finite(pMeasured) && finite(qMeasured) && finite(vMeasured)
                && finite(voltageIntegral) && finite(pUpperIntegral)
                && finite(pLowerIntegral) && finite(qUpperIntegral)
                && finite(qLowerIntegral);
    }

    private boolean nativeVoltageIntegralLimits() {
        return data.parameterRules()==Regfma1Data.ParameterRules.NATIVE_PSSE_36_7;
    }

    @Override
    public Object getOutputObject() {
        Complex voltage = getDStabBus().getVoltage();
        Complex impedance = couplingImpedance();
        Complex internal = polar(eDroop, angle);
        Complex outputCurrent = internal.subtract(voltage).divide(impedance);
        // Network injection is on system base; Imax is on inverter MVA base,
        // like the measured P/Q used by the droop and power-limit controls.
        double maximumSystemCurrent = data.imax() * deviceBaseMva / systemBaseMva;
        currentLimited = data.imax() > EPS && outputCurrent.abs() > maximumSystemCurrent;
        if (currentLimited) {
            outputCurrent = polar(maximumSystemCurrent, outputCurrent.getArgument());
            internal = voltage.add(impedance.multiply(outputCurrent));
        }
        Complex power = voltage.multiply(outputCurrent.conjugate());
        double controlBaseScale = systemBaseMva / deviceBaseMva;
        p = power.getReal() * controlBaseScale;
        q = power.getImaginary() * controlBaseScale;
        // The dynamic Y matrix contains 1/Z, so the device supplies E/Z.
        return internal.divide(impedance);
    }

    /** Current limiting refreshes electrical outputs without advancing integration coordinates. */
    @Override public boolean supportsNetworkIterationAcceleration() {
        return getClass() == Regfma1Model.class;
    }

    @Override
    public Hashtable<String, Object> getStates(Object ref) {
        states.put("REGFMA1_ANGLE", angle);
        states.put("REGFMA1_SPEED", speed);
        states.put("REGFMA1_E", eDroop);
        states.put("REGFMA1_P", p);
        states.put("REGFMA1_Q", q);
        states.put("REGFMA1_CURRENT_LIMITED", currentLimited ? 1.0 : 0.0);
        return states;
    }

    /** Incremental plant-controller inputs; zero preserves the initialized operating point. */
    public void setReferenceOffsets(double activePower, double reactiveOrVoltage) {
        prefOffset = activePower;
        qvOffset = reactiveOrVoltage;
    }

    public Repca1Model getPlantController() { return plantController; }
    public void setPlantController(Repca1Model plantController) {
        this.plantController = plantController;
    }

    private static double upperBoundedRate(double state, double rate) {
        return state >= 0.0 && rate > 0.0 ? 0.0 : rate;
    }

    private static double lowerBoundedRate(double state, double rate) {
        return state <= 0.0 && rate < 0.0 ? 0.0 : rate;
    }

    static double limitedIntegralRate(double integral, double gain, double error,
            double proportionalGain, double lower, double upper) {
        double output = proportionalGain * error + integral;
        if ((output >= upper && error > 0.0) || (output <= lower && error < 0.0)) return 0.0;
        return gain * error;
    }

    private Complex couplingImpedance() {
        return parentGen.getPosGenZ().multiply(parentGen.getZMultiFactor());
    }

    private static double lagRate(double state, double input, double timeConstant) {
        return timeConstant <= EPS ? 0.0 : (input - state) / timeConstant;
    }

    private static double limit(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private static Complex polar(double magnitude, double angle) {
        return new Complex(magnitude * Math.cos(angle), magnitude * Math.sin(angle));
    }

    private static Complex nonzero(Complex value) {
        return value.abs() > 0.01 ? value : new Complex(0.01, 0.0);
    }

    private static boolean finite(double value) { return Double.isFinite(value); }

    @Override public boolean updateAttributes(boolean netChange) { return true; }
    @Override public boolean afterStep(double dt) { return true; }
    @Override public DStabGen getParentGen() { return parentGen; }
    @Override public void setParentGen(DStabGen parentGen) {
        this.parentGen = parentGen;
        if (parentGen != null) parentGen.setDynamicGenDevice(this);
    }

    public Regfma1Data getData() { return data; }
    public double getAngle() { return angle; }
    public double getSpeed() { return speed; }
    public double getInternalVoltage() { return eDroop; }
    public double getActivePower() { return p; }
    public double getReactivePower() { return q; }
    public double getMeasuredActivePower() { return pMeasured; }
    public double getMeasuredReactivePower() { return qMeasured; }
    public double getMeasuredVoltage() { return vMeasured; }
    public double getVoltageIntegral() { return voltageIntegral; }
    public double getActiveUpperLimitIntegral() { return pUpperIntegral; }
    public double getActiveLowerLimitIntegral() { return pLowerIntegral; }
    public double getReactiveUpperLimitIntegral() { return qUpperIntegral; }
    public double getReactiveLowerLimitIntegral() { return qLowerIntegral; }
    public boolean isCurrentLimited() { return currentLimited; }

    /**
     * Published PSS/E 36.7 K..K+8 observation coordinates, in that order.
     * The bypassed voltage PI is an unused zero native state when Vflag=0;
     * the engine's constant initialization storage is not that published state.
     * Existing generic named-state keys and integration coordinates are unchanged.
     */
    public Map<String, Double> getPsseStateSnapshot() {
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("Active power measurement transducer", pMeasured);
        result.put("Active upper limit PI integrator", pUpperIntegral);
        result.put("Active lower limit PI integrator", pLowerIntegral);
        result.put("Internal source angle", angle);
        result.put("Reactive power measurement transducer", qMeasured);
        result.put("Terminal voltage measurement transducer", vMeasured);
        result.put("Reactive upper limit PI integrator", qUpperIntegral);
        result.put("Reactive lower limit PI integrator", qLowerIntegral);
        result.put("Terminal voltage PI integrator", data.vflag() == 0 ? 0.0 : voltageIntegral);
        return Collections.unmodifiableMap(result);
    }

    /** Pure L..L+7 observations; terminal current is on the device MVA base. */
    public Map<String, Double> getPsseVariableSnapshot() {
        Complex voltage = getDStabBus().getVoltage();
        Complex impedance = couplingImpedance();
        Complex current = polar(eDroop, angle).subtract(voltage).divide(impedance);
        double maximum = data.imax() * deviceBaseMva / systemBaseMva;
        if (data.imax() > EPS && current.abs() > maximum)
            current = polar(maximum, current.getArgument());
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("Voltage command", voltageCommand(state()));
        // Native L+1 retains Edroop before the transient current limit. The
        // equivalent limited Norton source is a different algebraic quantity.
        result.put("Internal source voltage magnitude before current limit", eDroop);
        result.put("Active upper limit PI output", Math.min(0.0,
                data.kppmax() * (data.pmax() - pMeasured) + pUpperIntegral));
        result.put("Active lower limit PI output", Math.max(0.0,
                data.kppmax() * (data.pmin() - pMeasured) + pLowerIntegral));
        result.put("Reactive upper limit PI output", Math.min(0.0,
                data.kpqmax() * (data.qmax() - qMeasured) + qUpperIntegral));
        result.put("Reactive lower limit PI output", Math.max(0.0,
                data.kpqmax() * (data.qmin() - qMeasured) + qLowerIntegral));
        result.put("Terminal current magnitude on machine base", current.abs() * systemBaseMva / deviceBaseMva);
        result.put("Fixed voltage reference", data.qvflag() == 0 ? vReference : 0.0);
        return Collections.unmodifiableMap(result);
    }

    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> named = new LinkedHashMap<>();
        named.put("Angle", angle);
        named.put("Speed", speed);
        named.put("Internal Voltage", eDroop);
        named.put("Active Power", p);
        named.put("Reactive Power", q);
        named.put("Measured Active Power", pMeasured);
        named.put("Measured Reactive Power", qMeasured);
        named.put("Measured Voltage", vMeasured);
        named.put("Voltage Integral", voltageIntegral);
        named.put("Active Upper Limit Integral", pUpperIntegral);
        named.put("Active Lower Limit Integral", pLowerIntegral);
        named.put("Reactive Upper Limit Integral", qUpperIntegral);
        named.put("Reactive Lower Limit Integral", qLowerIntegral);
        return Collections.unmodifiableMap(named);
    }

    private record Endpoint(double p, double q, double v) {}

    private record ModelState(double angle, double pMeasured, double qMeasured,
            double vMeasured, double voltageIntegral, double pUpperIntegral,
            double pLowerIntegral, double qUpperIntegral, double qLowerIntegral) {}

    private record ModelDerivatives(double angle, double pMeasured, double qMeasured,
            double vMeasured, double voltageIntegral, double pUpperIntegral,
            double pLowerIntegral, double qUpperIntegral, double qLowerIntegral) {}
}
