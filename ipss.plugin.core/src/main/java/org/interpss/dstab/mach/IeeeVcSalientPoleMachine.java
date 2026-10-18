package org.interpss.dstab.mach;

import com.interpss.dstab.mach.impl.SalientPoleMachineImpl;

/** Standard salient-pole machine with an optional IEEEVC sensing boundary. */
public final class IeeeVcSalientPoleMachine extends SalientPoleMachineImpl
        implements IeeeVoltageCompensatedMachine {
    static { IeeeVcMachineState.register(IeeeVcSalientPoleMachine.class,IeeeVcSalientPoleMachine::new); }
    private IeeeVcData ieeeVcData;

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
