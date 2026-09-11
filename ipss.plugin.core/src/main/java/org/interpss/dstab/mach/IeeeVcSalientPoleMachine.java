package org.interpss.dstab.mach;

import com.interpss.dstab.mach.impl.SalientPoleMachineImpl;

/** Standard salient-pole machine with an optional IEEEVC sensing boundary. */
public final class IeeeVcSalientPoleMachine extends SalientPoleMachineImpl
        implements IeeeVoltageCompensatedMachine {
    private IeeeVcData ieeeVcData;

    @Override
    public IeeeVcData getIeeeVcData() {
        return ieeeVcData;
    }

    @Override
    public void setIeeeVcData(IeeeVcData data) {
        ieeeVcData = data;
    }
}
