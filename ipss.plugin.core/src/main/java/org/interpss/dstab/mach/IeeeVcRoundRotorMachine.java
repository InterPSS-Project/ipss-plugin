package org.interpss.dstab.mach;

import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;

/** Standard round-rotor machine with an optional IEEEVC sensing boundary. */
public final class IeeeVcRoundRotorMachine extends RoundRotorMachineImpl
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
