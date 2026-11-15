package org.interpss.dstab.mach;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;

/** Standard round-rotor machine with an optional IEEEVC sensing boundary. */
public final class IeeeVcRoundRotorMachine extends RoundRotorMachineImpl
        implements IeeeVoltageCompensatedMachine, ICMLStateProvider {
    static { IeeeVcMachineState.register(IeeeVcRoundRotorMachine.class,IeeeVcRoundRotorMachine::new); }
    private IeeeVcData ieeeVcData;
    private boolean networkFrequencyDependenceEnabled;

    /** Configure the native Flux0 stator policy before dynamic initialization. */
    public void setNetworkFrequencyDependenceEnabled(boolean enabled) {
        networkFrequencyDependenceEnabled = enabled;
    }

    @Override
    protected double statorFrequencyRatio() {
        // The core uses this ratio for both stator reactance and flux-induced EMF.
        // Its Pe stays in torque coordinates, so the swing equation is unchanged.
        return networkFrequencyDependenceEnabled ? getDStabBus().getFreq() : 1.0;
    }
    private boolean exponentialSaturation;

    @Override public boolean supportsDeferredCurrentPublication() { return true; }

    /** GENROE changes only the saturation characteristic of the core machine. */
    public boolean isExponentialSaturation() {
        return exponentialSaturation;
    }

    public void setExponentialSaturation(boolean exponential) {
        exponentialSaturation = exponential;
    }

    @Override
    public double getSatruationFactor(double flux) {
        if (!exponentialSaturation) return super.getSatruationFactor(flux);
        double s1 = getSe100() * .01;
        double s12 = getSe120() * .01;
        if (s1 <= 0.0 || s12 <= 0.0 || flux <= 0.0) return 0.0;
        return s1 * Math.pow(flux, Math.log(s12 / s1) / Math.log(1.2));
    }

    /** Read-only native coordinates; integration remains entirely in the core. */
    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("E'q", getEq1());
        states.put("E'd", getEd1());
        states.put("Psi'd", getPsikd());
        states.put("Psi'q", getPsikq());
        states.put("Speed deviation", getSpeed() - 1.0);
        states.put("Angle", getAngle());
        return Collections.unmodifiableMap(states);
    }

    @Override
    protected double cal_dW_dt(double speed, double baseFrequency) {
        return PsseSwingEquation.speedDerivative(this, speed, baseFrequency);
    }

    @Override
    public IeeeVcData getIeeeVcData() {
        return ieeeVcData;
    }

    @Override
    public void setIeeeVcData(IeeeVcData data) {
        ieeeVcData = data;
    }
}
