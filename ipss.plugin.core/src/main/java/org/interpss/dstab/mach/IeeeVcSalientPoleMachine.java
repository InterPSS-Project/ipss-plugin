package org.interpss.dstab.mach;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.mach.impl.SalientPoleMachineImpl;

/** Standard salient-pole machine with an optional IEEEVC sensing boundary. */
public final class IeeeVcSalientPoleMachine extends SalientPoleMachineImpl
        implements IeeeVoltageCompensatedMachine, ICMLStateProvider {
    static { IeeeVcMachineState.register(IeeeVcSalientPoleMachine.class,IeeeVcSalientPoleMachine::new); }
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

    @Override public boolean supportsDeferredCurrentPublication() { return true; }

    @Override
    public Map<String,Double> getNamedStates() {
        Map<String,Double> states=new LinkedHashMap<>();
        states.put("E'q",getEq1());states.put("Psi'd",getPsikd());states.put("Psi''q",-getPsiq11());
        states.put("Speed deviation",getSpeed()-1);states.put("Angle",getAngle());
        return Collections.unmodifiableMap(states);
    }

    @Override
    protected double cal_dW_dt(double speed, double baseFrequency) {
        // Native GENSAL uses the same Flux0 torque convention as GENROU.
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
