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
