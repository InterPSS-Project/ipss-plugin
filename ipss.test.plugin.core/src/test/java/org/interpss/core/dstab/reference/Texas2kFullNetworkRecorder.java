package org.interpss.core.dstab.reference;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.zip.GZIPOutputStream;

import org.interpss.dstab.renewable.Regca1Model;

import com.google.gson.GsonBuilder;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;
import com.interpss.dstab.mach.impl.SalientPoleMachineImpl;

/** Optional read-only full-network observations for supplied-reference diagnosis. */
final class Texas2kFullNetworkRecorder implements AutoCloseable {
    private record Channel(String name, String quantity, String unit, String bus,
            String machineId, DoubleSupplier value) { }
    private record Description(String name, String quantity, String unit, String bus,
            String machineId) { }
    private final List<Channel> channels = new ArrayList<>();
    private final BufferedWriter writer;
    private final Path manifest;
    private final int busCount;
    private final int synchronousCount;
    private final int converterCount;
    private long samples;
    private double firstTime = Double.NaN;
    private double lastTime = Double.NaN;

    Texas2kFullNetworkRecorder(BaseDStabNetwork<?, ?> net, Path out) throws IOException {
        Path trace = out.resolve("full-network.csv.gz");
        manifest = out.resolve("full-network-channels.json");
        if (Files.exists(trace) || Files.exists(manifest))
            throw new IOException("Refusing to replace full-network observations");
        int buses = 0, synchronous = 0, converters = 0;
        for (var bus : net.getBusList()) {
            if (!bus.isActive()) continue;
            buses++;
            String number = bus.getId().replaceFirst("^Bus", "");
            add("bus_voltage_pu__bus_" + number, "voltage_magnitude", "pu", number, "",
                    () -> bus.isActive() ? bus.getVoltageMag() : 0.);
            add("bus_angle_deg__bus_" + number, "voltage_angle", "deg", number, "",
                    () -> bus.isActive() ? Math.toDegrees(bus.getVoltageAng()) : 0.);
            for (var generator : bus.getContributeGenList()) {
                if (!generator.isActive()) continue;
                String id = generator.getId();
                String suffix = "__bus_" + number + "__id_" + id;
                var device = generator.getDynamicGenDevice();
                if (device instanceof Machine machine) {
                    synchronous++;
                    DoubleSupplier p = () -> connected(bus.isActive(), generator.isActive(), machine.isActive())
                            ? terminalPowerWithoutCacheChanges(machine).getReal() * net.getBaseMva() : 0.;
                    DoubleSupplier q = () -> connected(bus.isActive(), generator.isActive(), machine.isActive())
                            ? terminalPowerWithoutCacheChanges(machine).getImaginary() * net.getBaseMva() : 0.;
                    add("generator_P_MW" + suffix, "terminal_active_power", "MW", number, id, p);
                    add("generator_Q_Mvar" + suffix, "terminal_reactive_power", "Mvar", number, id, q);
                    add("machine_ANGLE_deg" + suffix, "rotor_angle", "deg", number, id,
                            () -> connected(bus.isActive(), generator.isActive(), machine.isActive()) ? Math.toDegrees(machine.getAngle()) : 0.);
                    add("machine_SPEED_pu" + suffix, "speed", "pu_synchronous_speed", number, id,
                            () -> connected(bus.isActive(), generator.isActive(), machine.isActive()) ? machine.getSpeed() : 0.);
                    add("machine_PMECH_MW" + suffix, "mechanical_power", "MW", number, id,
                            () -> connected(bus.isActive(), generator.isActive(), machine.isActive()) ? machine.getPm() * machine.getRating() * net.getBaseMva() : 0.);
                    add("machine_EFD_pu" + suffix, "field_voltage", "pu_excitation_base", number, id,
                            () -> connected(bus.isActive(), generator.isActive(), machine.isActive()) ? machine.getEfd() : 0.);
                } else if (device instanceof Regca1Model converter) {
                    converters++;
                    double base = generator.getMvaBase() > 1.e-9 ? generator.getMvaBase() : net.getBaseMva();
                    // These getters evaluate terminal current without Norton admittance
                    // or the event-averaged cached power used by some state reports.
                    add("generator_P_MW" + suffix, "terminal_active_power", "MW", number, id,
                            () -> connected(bus.isActive(), generator.isActive(), converter.isActive()) ? bus.getVoltageMag() * converter.getIp() * base : 0.);
                    add("generator_Q_Mvar" + suffix, "terminal_reactive_power", "Mvar", number, id,
                            () -> connected(bus.isActive(), generator.isActive(), converter.isActive()) ? bus.getVoltageMag() * converter.getIq() * base : 0.);
                } else {
                    throw new IllegalStateException("Unsupported online generator observation: " + bus.getId() + "/" + id);
                }
            }
        }
        busCount = buses;
        synchronousCount = synchronous;
        converterCount = converters;
        writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(
                trace, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)), StandardCharsets.UTF_8));
        writer.write("time_s,sample_kind");
        for (var channel : channels) writer.write("," + channel.name());
        writer.newLine();
    }

    private static boolean connected(boolean bus, boolean generator, boolean device) {
        return bus && generator && device;
    }

    private static org.apache.commons.math3.complex.Complex terminalPowerWithoutCacheChanges(Machine machine) {
        // The terminal-current getter refreshes electrical caches and air-gap power
        // in subtransient machines. Observation must retain the solver's exact state.
        double pe = machine.getPe();
        if (machine instanceof RoundRotorMachineImpl round) {
            var state = round.captureRoundElectricalState();
            try {
                return machine.getDStabBus().getVoltage().multiply(machine.getIxy().conjugate());
            } finally {
                round.restoreRoundElectricalState(state);
                machine.setPe(pe);
            }
        }
        if (machine instanceof SalientPoleMachineImpl salient) {
            var state = salient.captureSalientElectricalState();
            try {
                return machine.getDStabBus().getVoltage().multiply(machine.getIxy().conjugate());
            } finally {
                salient.restoreSalientElectricalState(state);
                machine.setPe(pe);
            }
        }
        return machine.getDStabBus().getVoltage().multiply(machine.getIxy().conjugate());
    }

    private void add(String name, String quantity, String unit, String bus,
            String machineId, DoubleSupplier value) {
        if (name.contains(",") || name.contains("\n"))
            throw new IllegalArgumentException("Invalid channel identifier: " + name);
        channels.add(new Channel(name, quantity, unit, bus, machineId, value));
    }

    void record(double time, String kind) throws IOException {
        if (!Double.isFinite(time) || kind.contains(",") || kind.contains("\n"))
            throw new IllegalArgumentException("Invalid observation timestamp or event side");
        writer.write(Double.toString(time));
        writer.write("," + kind);
        for (var channel : channels) {
            double value = channel.value().getAsDouble();
            if (!Double.isFinite(value)) throw new IllegalStateException("Nonfinite " + channel.name() + " at " + time);
            writer.write("," + Double.toString(value));
        }
        writer.newLine();
        if (samples++ == 0) firstTime = time;
        lastTime = time;
    }

    @Override public void close() throws IOException {
        writer.close();
        var metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put("schema", "texas2k-full-network-observations-v1");
        metadata.put("initial_active_buses", busCount);
        metadata.put("initial_online_synchronous_machines", synchronousCount);
        metadata.put("initial_online_converter_generators", converterCount);
        metadata.put("samples", samples);
        if (samples > 0) {
            metadata.put("first_time_s", firstTime);
            metadata.put("last_time_s", lastTime);
        }
        metadata.put("time_convention", "Original integration times and event-pre/event-post sample sides; no resampling.");
        metadata.put("angle_convention", "Stored bus-voltage phase and machine rotor angle, in degrees; no fitted offset or rotation.");
        metadata.put("power_convention", "Instantaneous terminal injection, positive into the network; MW/Mvar. Disconnected generator channels are zero.");
        metadata.put("scope", "Optional read-only diagnostics. Original supplied-reference acceptance limits and scoring channels are unchanged.");
        metadata.put("channels", channels.stream().map(c -> new Description(c.name(), c.quantity(), c.unit(), c.bus(), c.machineId())).toList());
        Files.writeString(manifest, new GsonBuilder().setPrettyPrinting().create().toJson(metadata) + "\n",
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
}
