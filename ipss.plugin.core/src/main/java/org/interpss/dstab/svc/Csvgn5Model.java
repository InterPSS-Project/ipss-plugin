package org.interpss.dstab.svc;

import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.device.DynamicGenDevice;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/**
 * PSS/E CSVGN5 static var compensator.
 *
 * <p>The four stored coordinates follow the PSS/E state table: voltage-filter
 * output, first and second regulator lag coordinates, and thyristor delay.
 * Susceptance states are on machine base; {@link #getSystemBaseSusceptance()}
 * is the model output after the published MBASE/SBASE conversion.</p>
 */
public final class Csvgn5Model extends DynamicBusDeviceImpl
        implements DynamicGenDevice, ICMLStateProvider {
    private static final double EPS = 1.0e-12;

    private final Csvgn5Data data;
    private final Hashtable<String, Object> states = new Hashtable<>();
    private DStabGen parentGen;
    private BaseDStabBus<?, ?> remoteBus;
    private double deviceBaseMva;
    private double systemBaseMva;
    private double voltageReference;
    private double otherSignal;
    private double filterOutput;
    private double firstRegulatorState;
    private double secondRegulatorState;
    private double thyristorDelay;
    private ModelState predictorStart;
    private Derivatives predictorDerivatives;

    public Csvgn5Model(DStabGen parentGen, BaseDStabBus<?, ?> bus,
            BaseDStabBus<?, ?> remoteBus, String id, Csvgn5Data data) {
        this.data = data;
        this.remoteBus = remoteBus;
        setParentGen(parentGen);
        setDStabBus(bus);
        setId(id);
        setExtendedDeviceId("CSVGN5_" + id + "@" + bus.getId());
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus) {
        if (parentGen == null || bus == null || remoteBus == null
                || parentGen.getGen() == null) return false;
        systemBaseMva = bus.getNetwork().getBaseMva();
        deviceBaseMva = parentGen.getMvaBase() > EPS
                ? parentGen.getMvaBase() : systemBaseMva;
        double localVoltage = Math.max(EPS, bus.getVoltageMag());
        double initialSystemQ = parentGen.getGen().getImaginary();
        thyristorDelay = initialSystemQ * systemBaseMva / deviceBaseMva
                / (localVoltage * localVoltage);
        if (thyristorDelay < data.bmin() - EPS || thyristorDelay > data.bmax() + EPS) {
            return false;
        }

        filterOutput = remoteBus.getVoltageMag();
        double regulatorInput = thyristorDelay / data.ksvs();
        if (Math.abs(regulatorInput) > data.vemax() + EPS) return false;
        firstRegulatorState = leadLagSteadyState(regulatorInput,
                data.ts2(), data.ts3());
        secondRegulatorState = leadLagSteadyState(regulatorInput,
                data.ts4(), data.ts5());
        voltageReference = filterOutput + regulatorInput - otherSignal;
        predictorStart = null;
        predictorDerivatives = null;
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        return finiteState();
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        if (flag != 0 && flag != 1) return false;
        Endpoint endpoint = endpoint();
        if (dt <= 0.0) {
            apply(algebraicBypasses(state(), endpoint));
            return finiteState();
        }
        if (flag == 0) {
            predictorStart = state();
            predictorDerivatives = derivatives(predictorStart, endpoint);
            apply(algebraicBypasses(add(predictorStart, predictorDerivatives, dt), endpoint));
        } else {
            if (predictorStart == null || predictorDerivatives == null) return false;
            Derivatives corrected = derivatives(state(), endpoint);
            apply(algebraicBypasses(correct(predictorStart, predictorDerivatives,
                    corrected, dt), endpoint));
            predictorStart = null;
            predictorDerivatives = null;
        }
        thyristorDelay = clamp(thyristorDelay, data.bmin(), data.bmax());
        return finiteState();
    }

    private Endpoint endpoint() {
        double sensedVoltage = remoteBus == null ? filterOutput : remoteBus.getVoltageMag();
        return new Endpoint(sensedVoltage, otherSignal);
    }

    private Derivatives derivatives(ModelState state, Endpoint endpoint) {
        double filterRate = lagRate(endpoint.sensedVoltage(), state.filterOutput(), data.ts1());
        double filtered = data.ts1() <= EPS ? endpoint.sensedVoltage() : state.filterOutput();
        double voltageError = voltageReference + endpoint.otherSignal() - filtered;
        double regulatorInput = clamp(voltageError, -data.vemax(), data.vemax());
        double firstRate = leadLagStateRate(regulatorInput,
                state.firstRegulatorState(), data.ts2(), data.ts3());
        double firstOutput = leadLagOutput(regulatorInput, state.firstRegulatorState(),
                data.ts2(), data.ts3());
        double secondRate = leadLagStateRate(firstOutput,
                state.secondRegulatorState(), data.ts4(), data.ts5());
        double secondOutput = leadLagOutput(firstOutput, state.secondRegulatorState(),
                data.ts4(), data.ts5());
        double regulatorOutput = data.ksvs() * secondOutput;
        double target = fastOverride(voltageError, regulatorOutput);
        double thyristorRate = lagRate(target, state.thyristorDelay(), data.ts6());
        if ((state.thyristorDelay() >= data.bmax() && thyristorRate > 0.0)
                || (state.thyristorDelay() <= data.bmin() && thyristorRate < 0.0)) {
            thyristorRate = 0.0;
        }
        return new Derivatives(filterRate, firstRate, secondRate, thyristorRate);
    }

    private ModelState algebraicBypasses(ModelState state, Endpoint endpoint) {
        double filtered = data.ts1() <= EPS ? endpoint.sensedVoltage() : state.filterOutput();
        double voltageError = voltageReference + endpoint.otherSignal() - filtered;
        double regulatorInput = clamp(voltageError, -data.vemax(), data.vemax());
        double first = state.firstRegulatorState();
        if (data.ts3() <= EPS) first = 0.0;
        double firstOutput = leadLagOutput(regulatorInput, first, data.ts2(), data.ts3());
        double second = state.secondRegulatorState();
        if (data.ts5() <= EPS) second = 0.0;
        return new ModelState(filtered, first, second, state.thyristorDelay());
    }

    private double fastOverride(double voltageError, double regulatorOutput) {
        double lowThreshold = data.dv() == 0.0
                ? data.bPrimeMax() / data.ksvs() : data.dv();
        double highThreshold = data.dv() == 0.0
                ? data.bPrimeMin() / data.ksvs() : -data.dv();
        if (voltageError > lowThreshold) {
            return data.bPrimeMax() + data.ksd() * (voltageError - data.dv());
        }
        if (voltageError < highThreshold) return data.bPrimeMin();
        return regulatorOutput;
    }

    @Override
    public Object getOutputObject() {
        Complex voltage = getDStabBus().getVoltage();
        Complex norton = injectedCurrent(voltage);
        Complex z = parentGen.getPosGenZ();
        if (z != null) {
            z = z.multiply(parentGen.getZMultiFactor());
            if (z.abs() > EPS) norton = norton.add(voltage.divide(z));
        }
        return norton;
    }

    private Complex injectedCurrent(Complex voltage) {
        return voltage.multiply(new Complex(0.0, -getSystemBaseSusceptance()));
    }

    @Override
    public boolean updateAttributes(boolean netChange) {
        return finiteState();
    }

    @Override public boolean afterStep(double dt) { return true; }
    @Override public DStabGen getParentGen() { return parentGen; }

    @Override
    public void setParentGen(DStabGen parentGen) {
        this.parentGen = parentGen;
        if (parentGen != null) parentGen.setDynamicGenDevice(this);
    }

    @Override
    public Hashtable<String, Object> getStates(Object ref) {
        states.put("CSVGN5_FILTER", filterOutput);
        states.put("CSVGN5_REGULATOR_1", firstRegulatorState);
        states.put("CSVGN5_REGULATOR_2", secondRegulatorState);
        states.put("CSVGN5_THYRISTOR", thyristorDelay);
        states.put("CSVGN5_Y", getSystemBaseSusceptance());
        return states;
    }

    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> named = new LinkedHashMap<>();
        named.put("Filter output", filterOutput);
        named.put("First regulator state", firstRegulatorState);
        named.put("Second regulator state", secondRegulatorState);
        named.put("Thyristor delay", thyristorDelay);
        named.put("Y", getSystemBaseSusceptance());
        return Collections.unmodifiableMap(named);
    }

    public Csvgn5Data getData() { return data; }
    public BaseDStabBus<?, ?> getRemoteBus() { return remoteBus; }
    public double getVoltageReference() { return voltageReference; }
    public double getFilterOutput() { return filterOutput; }
    public double getFirstRegulatorState() { return firstRegulatorState; }
    public double getSecondRegulatorState() { return secondRegulatorState; }
    public double getThyristorDelay() { return thyristorDelay; }
    public double getSystemBaseSusceptance() {
        return systemBaseMva > EPS ? thyristorDelay * deviceBaseMva / systemBaseMva : 0.0;
    }
    public void setOtherSignal(double otherSignal) { this.otherSignal = otherSignal; }

    private ModelState state() {
        return new ModelState(filterOutput, firstRegulatorState,
                secondRegulatorState, thyristorDelay);
    }

    private void apply(ModelState state) {
        filterOutput = state.filterOutput();
        firstRegulatorState = state.firstRegulatorState();
        secondRegulatorState = state.secondRegulatorState();
        thyristorDelay = state.thyristorDelay();
    }

    private static ModelState add(ModelState state, Derivatives rates, double dt) {
        return new ModelState(state.filterOutput() + dt * rates.filterOutput(),
                state.firstRegulatorState() + dt * rates.firstRegulatorState(),
                state.secondRegulatorState() + dt * rates.secondRegulatorState(),
                state.thyristorDelay() + dt * rates.thyristorDelay());
    }

    private static ModelState correct(ModelState start, Derivatives first,
            Derivatives second, double dt) {
        return new ModelState(start.filterOutput()
                        + 0.5 * dt * (first.filterOutput() + second.filterOutput()),
                start.firstRegulatorState()
                        + 0.5 * dt * (first.firstRegulatorState()
                                + second.firstRegulatorState()),
                start.secondRegulatorState()
                        + 0.5 * dt * (first.secondRegulatorState()
                                + second.secondRegulatorState()),
                start.thyristorDelay()
                        + 0.5 * dt * (first.thyristorDelay() + second.thyristorDelay()));
    }

    private static double lagRate(double input, double state, double timeConstant) {
        return timeConstant <= EPS ? 0.0 : (input - state) / timeConstant;
    }

    private static double leadLagOutput(double input, double state,
            double numeratorTime, double denominatorTime) {
        if (denominatorTime <= EPS) return input;
        double ratio = numeratorTime / denominatorTime;
        return state + ratio * input;
    }

    private static double leadLagStateRate(double input, double state,
            double numeratorTime, double denominatorTime) {
        if (denominatorTime <= EPS) return 0.0;
        double ratio = numeratorTime / denominatorTime;
        return ((1.0 - ratio) * input - state) / denominatorTime;
    }

    private static double leadLagSteadyState(double input,
            double numeratorTime, double denominatorTime) {
        if (denominatorTime <= EPS) return 0.0;
        return (1.0 - numeratorTime / denominatorTime) * input;
    }

    private boolean finiteState() {
        return Double.isFinite(filterOutput) && Double.isFinite(firstRegulatorState)
                && Double.isFinite(secondRegulatorState)
                && Double.isFinite(thyristorDelay);
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private record Endpoint(double sensedVoltage, double otherSignal) { }
    private record ModelState(double filterOutput, double firstRegulatorState,
            double secondRegulatorState, double thyristorDelay) { }
    private record Derivatives(double filterOutput, double firstRegulatorState,
            double secondRegulatorState, double thyristorDelay) { }
}
