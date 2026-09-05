package org.interpss.dstab.validation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;

/** Converts standard InterPSS state-monitor channels to canonical trace rows. */
public final class StateMonitorTraceAdapter {
    private StateMonitorTraceAdapter() {
    }

    public static List<DynamicTraceSample> standard(StateMonitor monitor) {
        if (monitor == null) throw new IllegalArgumentException("State monitor is required");
        List<DynamicTraceSample> samples = new ArrayList<>();
        append(samples, monitor.getBusVoltTable(), "BUS_VOLTAGE", "pu", "bus-voltage-base");
        append(samples, monitor.getMachSpeedTable(), "SPEED", "pu", "nominal-speed");
        return List.copyOf(samples);
    }

    private static void append(List<DynamicTraceSample> destination,
            Hashtable<String, Hashtable<Integer, MonitorRecord>> table,
            String signal, String unit, String base) {
        table.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(channel ->
                channel.getValue().entrySet().stream()
                        .sorted(Comparator.comparingInt(Map.Entry::getKey))
                        .map(Map.Entry::getValue)
                        .forEach(record -> destination.add(new DynamicTraceSample(
                                record.t, channel.getKey(), signal, record.value, unit, base))));
    }
}
