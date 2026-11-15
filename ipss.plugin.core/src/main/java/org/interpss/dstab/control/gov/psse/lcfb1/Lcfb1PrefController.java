package org.interpss.dstab.control.gov.psse.lcfb1;

import java.util.Hashtable;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.dstab.control.util.AsymmetricDeadbandBlock;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;
import org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1Governor;
import org.interpss.dstab.control.gov.psse.hyg3.PsseHyg3Governor;
import org.interpss.dstab.control.gov.psse.hygovr.PsseHygovrGovernor;
import org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1Governor;
import org.interpss.dstab.mach.GenqecMachine;
import org.interpss.dstab.mach.Gentpj1Machine;
import org.interpss.dstab.mach.IeeeVcRoundRotorMachine;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.dynLoad.impl.DynLoadModelImpl;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineController;
import com.interpss.core.net.Network;

/**
 * PSS/E LCFB1 turbine load/Pref controller.
 *
 * <p>LCFB1 is a secondary controller, not a turbine governor. It senses machine
 * electrical power through {@code 1/(1+s*Tpelec)}, optionally combines power
 * and frequency errors, and applies a deadband, error clamp, PI control, and
 * symmetric reference-bias limit before driving the existing governor Pref.</p>
 */
public final class Lcfb1PrefController extends DynLoadModelImpl
        implements ICMLStateProvider {
    private static final double EPS = 1.0e-9;

    private final Lcfb1Data data;
    private final Machine machine;
    private final MachineController governor;
    private final Hashtable<String, Object> states = new Hashtable<>();

    private double powerSetpoint;
    private double powerMeasurementScale = 1.0;
    private double baseReference;
    private double governorBaseReference;
    private boolean governorReferencePending;
    private double sensedPower;
    private double integrator;
    private double referenceBias;
    private double oldSensedPower;
    private double oldIntegrator;
    private double oldPowerDerivative;
    private double oldIntegratorDerivative;
    private boolean initialized;

    public Lcfb1PrefController(BaseDStabBus<?, ?> bus, Machine machine,
            String generatorId, Lcfb1Data data) {
        if (bus == null || machine == null || data == null) {
            throw new IllegalArgumentException("LCFB1 requires a bus, machine, and data");
        }
        if (!machine.hasGovernor() || machine.getGovernor() == null) {
            throw new IllegalArgumentException("LCFB1 requires an existing turbine governor");
        }
        this.data = data;
        this.machine = machine;
        this.governor = machine.getGovernor();
        setId(generatorId);
        setName("LCFB1");
        setExtendedDeviceId("LCFB1_" + generatorId + "@" + bus.getId());
        // The current core exposes dynamic generator and dynamic load lifecycle
        // slots, but no neutral controller slot. Register through the load slot
        // with an exact zero electrical contribution so the solver advances this
        // secondary controller without changing the network model.
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

    @Override
    public boolean initStates() {
        return initStates(getDStabBus());
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine ignored) {
        return initStates(bus);
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Network<?, ?> ignored) {
        return initStates(bus);
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus) {
        if (bus == null || machine.getGovernor() != governor) return false;
        // Native LCFB1 on HYGOVR1 measures terminal MW on the entered
        // turbine rating, with machine MVA as the zero-rating fallback.
        // Establish the conversion before the governor's initialization.
        powerMeasurementScale = 1.0;
        if (governor instanceof PsseHygovrGovernor hygovr && hygovr.getData().getTrate() > EPS) {
            double machineMva = machine.getRating(UnitType.mVA, bus.getNetwork().getBaseKva());
            powerMeasurementScale = machineMva / hygovr.getData().getTrate();
        }
        // These stator models expose terminal MW separately from the swing
        // input. Seed from solved dispatch before host initialization.
        if (machine instanceof Gentpj1Machine || machine instanceof IeeeVcRoundRotorMachine
                || machine instanceof GenqecMachine) {
            sensedPower = solvedTerminalPower(bus);
        } else {
            sensedPower = machine.getPe();
            if (Math.abs(sensedPower) <= EPS) sensedPower = machine.getPm();
            if (Math.abs(sensedPower) <= EPS) sensedPower = solvedTerminalPower(bus);
        }
        sensedPower *= powerMeasurementScale;
        powerSetpoint = sensedPower;
        baseReference = sensedPower;
        // The diagram adds bias to Pref0, not to Pgen. Different governors
        // initialize Pref in different coordinates (droop, valve, or load).
        // Read the initialized host reference once; keep the measured-power
        // setpoint and the existing reporting coordinate separate from it.
        governorBaseReference = initializedGovernorReference();
        governorReferencePending = true;
        integrator = 0.0;
        referenceBias = 0.0;
        applyGovernorReference(0.0);
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        initialized = true;
        return finite(sensedPower, powerSetpoint, baseReference, governorBaseReference);
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) {
            throw new InterpssRuntimeException("LCFB1 supports MODIFIED_EULER only");
        }
        if (!initialized || dt < 0.0 || !Double.isFinite(dt)) return false;
        if (flag == 0) {
            // The network initializes bus auxiliaries before machine/governor
            // models. Capture Pref0 again at the first predictor, after that
            // host initialization has completed, then retain it for the run.
            // Later bias updates must never recapture their own output.
            if (governorReferencePending) {
                governorBaseReference = initializedGovernorReference();
                governorReferencePending = false;
            }
            oldSensedPower = sensedPower;
            oldIntegrator = integrator;
            oldPowerDerivative = powerDerivative(sensedPower);
            oldIntegratorDerivative = integratorDerivative(sensedPower, integrator);
            sensedPower = data.powerTransducerTime() <= EPS
                    ? measuredPower() : sensedPower + dt * oldPowerDerivative;
            integrator = limitPath(integrator + dt * oldIntegratorDerivative);
        } else if (flag == 1) {
            double correctedPowerDerivative = powerDerivative(sensedPower);
            double correctedIntegratorDerivative = integratorDerivative(sensedPower, integrator);
            sensedPower = data.powerTransducerTime() <= EPS ? measuredPower()
                    : oldSensedPower + 0.5 * dt * (oldPowerDerivative + correctedPowerDerivative);
            integrator = limitPath(oldIntegrator
                    + 0.5 * dt * (oldIntegratorDerivative + correctedIntegratorDerivative));
        } else {
            throw new InterpssRuntimeException("LCFB1 invalid integration flag: " + flag);
        }
        referenceBias = limitedBias(sensedPower, integrator);
        applyGovernorReference(referenceBias);
        return finite(sensedPower, integrator, referenceBias);
    }

    private double solvedTerminalPower(BaseDStabBus<?, ?> bus) {
        double baseKva = bus.getNetwork().getBaseKva();
        return machine.getParentGen().getGen().getReal() * baseKva / 1000.0
                / machine.getRating(UnitType.mVA, baseKva);
    }

    private double measuredPower() {
        if (machine instanceof Gentpj1Machine typeJ)
            return typeJ.getTerminalActivePower() * powerMeasurementScale;
        if (machine instanceof IeeeVcRoundRotorMachine roundRotor)
            return roundRotor.getTerminalActivePower() * powerMeasurementScale;
        if (machine instanceof GenqecMachine genqec)
            return genqec.getTerminalActivePower() * powerMeasurementScale;
        return machine.getPe() * powerMeasurementScale;
    }

    private double powerDerivative(double state) {
        return data.powerTransducerTime() <= EPS
                ? 0.0 : (measuredPower() - state) / data.powerTransducerTime();
    }

    private double integratorDerivative(double measuredPower, double integralState) {
        double error = conditionedError(measuredPower);
        double derivative = data.integralGain() * error;
        double limit = data.maximumReferenceBias();
        // LCFB1 limits the Kp and Ki/s paths independently before their
        // outputs are summed and limited once more.  Final-output saturation
        // therefore must not freeze an integrator that is still inside its
        // own bounds.
        if ((integralState >= limit && derivative > 0.0)
                || (integralState <= -limit && derivative < 0.0)) return 0.0;
        return derivative;
    }

    private double limitedBias(double measuredPower, double integralState) {
        double error = conditionedError(measuredPower);
        double proportional = limitPath(data.proportionalGain() * error);
        return clamp(proportional + limitPath(integralState),
                -data.maximumReferenceBias(), data.maximumReferenceBias());
    }

    private double limitPath(double value) {
        return clamp(value, -data.maximumReferenceBias(), data.maximumReferenceBias());
    }

    private double conditionedError(double measuredPower) {
        double error = 0.0;
        if (data.powerControlFlag() == 1) error += powerSetpoint - measuredPower;
        if (data.frequencyBiasFlag() == 1) {
            error += data.frequencyBias() * (1.0 - machine.getSpeed());
        }
        error = AsymmetricDeadbandBlock.apply(error, data.deadband(), -data.deadband());
        return clamp(error, -data.maximumError(), data.maximumError());
    }

    @Override public double getOutput() { return baseReference + referenceBias; }
    /** Zero current injection: LCFB1 changes only the governor reference. */
    @Override public Object getOutputObject() { return Complex.ZERO; }
    @Override public boolean updateAttributes(boolean netChange) { return true; }
    @Override public boolean afterStep(double dt) { return true; }

    @Override
    public Hashtable<String, Object> getStates(Object ref) {
        states.put("LCFB1_PELEC", sensedPower);
        states.put("LCFB1_ERROR", conditionedError(sensedPower));
        states.put("LCFB1_BIAS", referenceBias);
        states.put("LCFB1_PREF", getOutput());
        return states;
    }

    public Lcfb1Data getData() { return data; }
    public Machine getMachine() { return machine; }
    public MachineController getGovernor() { return governor; }
    public double getSensedPower() { return sensedPower; }
    public double getPowerSetpoint() { return powerSetpoint; }
    public double getReferenceBias() { return referenceBias; }
    public double getGovernorReference() { return getOutput(); }
    @Override
    public Map<String, Double> getNamedStates() {
        return Map.of("Pelec Sensed", sensedPower, "Integral", integrator);
    }

    private void applyGovernorReference(double bias) {
        // GGOV1's public setter converts from machine to turbine base, while
        // LCFB1's diagram already produces the speed/droop-reference bias.
        // Invert that setter conversion so a bias is added exactly once in
        // the governor equation even when Trate and machine MVA differ.
        double setterBias = bias;
        if (governor instanceof PsseGgov1Governor ggov1)
            setterBias *= ggov1.getGovernorBaseMva(machine)
                    / machine.getRating(UnitType.mVA, machine.getDStabBus().getNetwork().getBaseKva());
        governor.setRefPoint(governorBaseReference + setterBias);
    }

    private double initializedGovernorReference() {
        if (governor instanceof PsseTGov1SteamTurGovernor tgov1) {
            // Preserve the established TGOV1 speed-reference contract, also
            // used when this auxiliary is initialized standalone in tests.
            return baseReference * tgov1.invRatingScale * tgov1.R;
        }
        if (governor instanceof PsseGgov1Governor ggov1) return ggov1.getRefPoint();
        if (governor instanceof Wsieg1Governor wsieg1) return wsieg1.getRefPoint();
        if (governor instanceof PsseHyg3Governor hyg3) return hyg3.getRefPoint();
        if (governor instanceof PsseHygovrGovernor hygovr) return hygovr.getRefPoint();
        if (governor instanceof AbstractAnnotateController annotated)
            return annotated.getRefPoint();
        return baseReference;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
