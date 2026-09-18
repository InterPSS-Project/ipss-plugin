package org.interpss.dstab.mach;

import com.interpss.dstab.mach.impl.EConstMachineImpl;

/** Classical machine with an optional IEEEVC sensing boundary. */
public final class IeeeVcEConstMachine extends EConstMachineImpl
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
