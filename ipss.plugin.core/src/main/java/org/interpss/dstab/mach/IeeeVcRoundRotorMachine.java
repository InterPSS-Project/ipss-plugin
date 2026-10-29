package org.interpss.dstab.mach;

import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;

/** Standard round-rotor machine with an optional IEEEVC sensing boundary. */
public final class IeeeVcRoundRotorMachine extends RoundRotorMachineImpl
        implements IeeeVoltageCompensatedMachine {
    static { IeeeVcMachineState.register(IeeeVcRoundRotorMachine.class,IeeeVcRoundRotorMachine::new); }
    private IeeeVcData ieeeVcData;

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
