package org.interpss.dstab.relay;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;

/** PSS/E VTGTPAT under/over-voltage generator trip relay. */
public final class VtgtpatRelayModel extends AbstractGeneratorTripRelayModel {
    public VtgtpatRelayModel(int sourceBusNumber, BaseDStabBus<?, ?> monitoredBus,
            BaseDStabBus<?, ?> targetBus, DStabGen targetGenerator,
            GeneratorTripRelayData data) {
        super("VTGTPAT", sourceBusNumber, monitoredBus, targetBus, targetGenerator, data);
    }

    @Override
    public double getMonitoredValue() {
        return getMonitoredBus().getVoltageMag();
    }
}
