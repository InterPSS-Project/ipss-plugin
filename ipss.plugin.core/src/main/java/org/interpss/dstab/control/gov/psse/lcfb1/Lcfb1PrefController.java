package org.interpss.dstab.control.gov.psse.lcfb1;

import java.util.Hashtable;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.util.AsymmetricDeadbandBlock;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
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
    private double baseReference;
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
        // Secondary dynamic-load devices can initialize before the full
        // solver populates the machine Pe/Pm caches. Prefer explicit machine
        // initialization, then fall back to the solved parent-generator P.
        sensedPower = machine.getPe();
        if (Math.abs(sensedPower) <= EPS) sensedPower = machine.getPm();
        if (Math.abs(sensedPower) <= EPS)
            sensedPower = machine.getParentGen().getGen().getReal();
        powerSetpoint = sensedPower;
        baseReference = sensedPower;
        integrator = 0.0;
        referenceBias = 0.0;
        applyGovernorReference(0.0);
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        initialized = true;
        return finite(sensedPower, powerSetpoint, baseReference);
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) {
            throw new InterpssRuntimeException("LCFB1 supports MODIFIED_EULER only");
        }
        if (!initialized || dt < 0.0 || !Double.isFinite(dt)) return false;
        if (flag == 0) {
            oldSensedPower = sensedPower;
            oldIntegrator = integrator;
            oldPowerDerivative = powerDerivative(sensedPower);
            oldIntegratorDerivative = integratorDerivative(sensedPower, integrator);
            sensedPower = data.powerTransducerTime() <= EPS
                    ? machine.getPe() : sensedPower + dt * oldPowerDerivative;
            integrator = limitPath(integrator + dt * oldIntegratorDerivative);
        } else if (flag == 1) {
            double correctedPowerDerivative = powerDerivative(sensedPower);
            double correctedIntegratorDerivative = integratorDerivative(sensedPower, integrator);
            sensedPower = data.powerTransducerTime() <= EPS ? machine.getPe()
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

    private double powerDerivative(double state) {
        return data.powerTransducerTime() <= EPS
                ? 0.0 : (machine.getPe() - state) / data.powerTransducerTime();
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
        if (governor instanceof PsseTGov1SteamTurGovernor tgov1) {
            governor.setRefPoint(baseReference * tgov1.invRatingScale * tgov1.R + bias);
        } else {
            governor.setRefPoint(baseReference + bias);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
