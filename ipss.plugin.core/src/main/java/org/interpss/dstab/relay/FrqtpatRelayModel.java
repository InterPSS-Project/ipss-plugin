package org.interpss.dstab.relay;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;

/** PSS/E FRQTPAT under/over-frequency generator trip relay. */
public final class FrqtpatRelayModel extends AbstractGeneratorTripRelayModel {
    public FrqtpatRelayModel(int sourceBusNumber, BaseDStabBus<?, ?> monitoredBus,
            BaseDStabBus<?, ?> targetBus, DStabGen targetGenerator,
            GeneratorTripRelayData data) {
        super("FRQTPAT", sourceBusNumber, monitoredBus, targetBus, targetGenerator, data);
    }

    @Override
    public double getMonitoredValue() {
        return getMonitoredBus().getFreq() * getMonitoredBus().getNetwork().getFrequency();
    }
}
