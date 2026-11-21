package org.interpss.core.dstab.reference;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.apache.commons.math3.complex.Complex;

import com.google.gson.JsonObject;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.mach.Machine;

/**
 * Fixed observation bindings for one benchmark case. Device status and values
 * remain live; a structural inventory/selection change requires a new recorder.
 */
final class Texas2kSelectedRecorder {
    /** Owned storage: consume before the next sample; do not retain or modify it. */
    static final class Sample {
        private final String header;
        private final String[] names;
        private final double[] values;
        private final StringBuilder csv;

        Sample(String[] names) {
            this.names = names;
            header = String.join(",", names);
            values = new double[names.length];
            csv = new StringBuilder(Math.addExact(64, Math.multiplyExact(32, names.length)));
        }

        String header() { return header; }
        String[] names() { return names; }
        double[] values() { return values; }

        void write(BufferedWriter writer, double time, String kind) throws IOException {
            csv.setLength(0);
            csv.append(time).append(',').append(kind);
            for (int i = 0; i < values.length; i++) {
                double value = values[i];
                if (!Double.isFinite(value))
                    throw new IllegalStateException("Nonfinite " + names[i] + " at " + time);
                csv.append(',').append(value);
            }
            writer.write(csv.toString());
            writer.newLine();
        }
    }

    private final BaseDStabNetwork<?, ?> network;
    private final JsonObject selection;
    private BaseDStabBus<?, ?>[] buses;
    private int[] busNumbers;
    private Machine[] machines;
    private Sample sample;

    Texas2kSelectedRecorder(BaseDStabNetwork<?, ?> network, JsonObject selection) {
        this.network = network;
        this.selection = selection.deepCopy();
    }

    private void bind() {
        var busMap = new LinkedHashMap<Integer, BaseDStabBus<?, ?>>();
        var machineMap = new LinkedHashMap<String, Machine>();
        var names = new ArrayList<String>();
        for (var number : selection.getAsJsonArray("nearby_buses")) {
            int id = number.getAsInt();
            var bus = network.getBus("Bus" + id);
            if (bus == null || !bus.isActive()) throw new IllegalStateException("Missing bus " + id);
            if (busMap.putIfAbsent(id, bus) == null) {
                names.add("bus_voltage_pu__bus_" + id);
                names.add("bus_angle_deg__bus_" + id);
            }
        }
        var observed = selection.has("diagnostic_generators")
                ? selection.getAsJsonArray("diagnostic_generators") : selection.getAsJsonArray("nearby_generators");
        for (var generator : observed) {
            var g = generator.getAsJsonObject();
            String id = "Bus" + g.get("bus").getAsInt() + "-mach" + g.get("id").getAsString();
            var machine = network.getMachine(id);
            if (machine == null) throw new IllegalStateException("Missing machine " + id);
            if (machineMap.putIfAbsent(id, machine) == null) {
                String suffix = "__bus_" + g.get("bus").getAsInt() + "__id_" + g.get("id").getAsString();
                for (String quantity : new String[]{"ANGLE_deg", "SPEED_dev_pu", "EFD_pu", "PMECH_pu_mbase",
                        "ETERM_pu", "AIRGAP_pu_mbase", "PELEC_pu_sbase", "QELEC_pu"})
                    names.add("machine_" + quantity + suffix);
            }
        }
        buses = busMap.values().toArray(BaseDStabBus<?, ?>[]::new);
        busNumbers = busMap.keySet().stream().mapToInt(Integer::intValue).toArray();
        machines = machineMap.values().toArray(Machine[]::new);
        sample = new Sample(names.toArray(String[]::new));
    }

    Sample sample() {
        if (sample == null) bind();
        double[] values = sample.values();
        int column = 0;
        for (int i = 0; i < buses.length; i++) {
            var bus = buses[i];
            if (!bus.isActive()) throw new IllegalStateException("Missing bus " + busNumbers[i]);
            values[column++] = bus.getVoltageMag();
            values[column++] = Math.toDegrees(bus.getVoltageAng());
        }
        for (var machine : machines) {
            boolean active = machine.isActive();
            values[column++] = active ? Math.toDegrees(machine.getAngle()) : 0.;
            values[column++] = active ? machine.getSpeed() - 1. : 0.;
            values[column++] = active ? machine.getEfd() : 0.;
            values[column++] = active ? machine.getPm() : 0.;
            values[column++] = active ? machine.getDStabBus().getVoltageMag() : 0.;
            Complex terminal = active ? Texas2kFullNetworkRecorder.terminalPowerWithoutCacheChanges(machine) : Complex.ZERO;
            values[column++] = active ? machine.getPe() : 0.;
            values[column++] = terminal.getReal();
            values[column++] = terminal.getImaginary();
        }
        return sample;
    }
}
