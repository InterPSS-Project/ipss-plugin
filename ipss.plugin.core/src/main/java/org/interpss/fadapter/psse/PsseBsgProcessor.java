package org.interpss.fadapter.psse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.interpss.dstab.renewable.ActivePowerResponseLimits;
import com.interpss.dstab.renewable.Reeca1Model;
import com.interpss.dstab.renewable.Reecb1Model;
import com.interpss.dstab.renewable.Regca1Model;
import com.interpss.dstab.renewable.RenewableElectricalController;
import org.interpss.fadapter.psse.PsseGnetIdvProcessor.GeneratorKey;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.BaseDStabNetwork;

/** Reads BASEGEN helper records: bus, quoted name, base kV, quoted unit ID, optional response code. */
public final class PsseBsgProcessor {
    private static final Pattern ROW = Pattern.compile(
            "^\\s*(\\d+)\\s+\"([^\"]*)\"\\s+(\\S+)\\s+\"([^\"]+)\""
                    + "(?:\\s+([012]))?\\s*(?://.*)?$");

    private PsseBsgProcessor() { }

    public record Entry(GeneratorKey generator, String busName, double baseKv,
            ActivePowerResponseLimits response) { }

    /** Legacy four-column records mean fixed response. Duplicate conflicting records fail. */
    public static Map<GeneratorKey, Entry> read(Path file) throws IOException {
        Map<GeneratorKey, Entry> entries = new LinkedHashMap<>();
        int lineNumber = 0;
        for (String line : Files.readAllLines(file)) {
            lineNumber++;
            if (line.isBlank() || line.stripLeading().startsWith("//")) continue;
            var match = ROW.matcher(line);
            try {
                if (!match.matches()) throw new IllegalArgumentException("Invalid BASEGEN row");
                long bus = Long.parseLong(match.group(1));
                double kv = Double.parseDouble(match.group(3));
                String id = match.group(4).trim();
                if (bus <= 0 || !Double.isFinite(kv) || kv <= 0 || id.isEmpty())
                    throw new IllegalArgumentException("Positive bus/base kV and nonempty unit ID required");
                var key = new GeneratorKey("Bus" + bus, id);
                var entry = new Entry(key, match.group(2), kv, match.group(5) == null
                        ? ActivePowerResponseLimits.FIXED
                        : ActivePowerResponseLimits.fromCode(Integer.parseInt(match.group(5))));
                var previous = entries.putIfAbsent(key, entry);
                if (previous != null && !previous.equals(entry))
                    throw new IllegalArgumentException("Conflicting BASEGEN records for " + key);
            } catch (IllegalArgumentException failure) {
                throw new IOException(file.getFileName() + ":" + lineNumber + ": " + failure.getMessage(), failure);
            }
        }
        return Collections.unmodifiableMap(entries);
    }

    /** Validate all targets before changing any controller; inactive units retain auditable entries. */
    public static Map<GeneratorKey, Entry> apply(BaseDStabNetwork<?, ?> network, Path file)
            throws InterpssException {
        if (network.isDStabNetInitialized())
            throw new InterpssException("Apply BASEGEN response limits before dynamic initialization");
        Map<GeneratorKey, Entry> entries;
        try { entries = read(file); }
        catch (IOException failure) { throw new InterpssException(failure.getMessage()); }
        Map<RenewableElectricalController, ActivePowerResponseLimits> bindings = new LinkedHashMap<>();
        for (Entry entry : entries.values()) {
            var key = entry.generator();
            var bus = network.getDStabBus(key.busId());
            var generator = bus == null ? null : bus.getContributeGen(key.generatorId());
            if (generator == null) throw new InterpssException("BASEGEN target not found: " + key);
            if (!bus.isActive() || !generator.isActive()) continue;
            var device = ((com.interpss.dstab.DStabGen) generator).getDynamicGenDevice();
            var controller = device instanceof Regca1Model converter
                    ? converter.getActiveElectricalController() : null;
            if (!(controller instanceof Reeca1Model || controller instanceof Reecb1Model
                    || controller instanceof com.interpss.dstab.renewable.ConverterCurrentCommands))
                throw new InterpssException("Unsupported BASEGEN controller (requires REGCA1 current commands or REECA1/REECB1): " + key);
            bindings.put(controller, entry.response());
        }
        Map<RenewableElectricalController, ActivePowerResponseLimits> previous = new LinkedHashMap<>();
        try {
            for (var binding : bindings.entrySet()) {
                previous.put(binding.getKey(), binding.getKey().getActivePowerResponseLimits());
                binding.getKey().setActivePowerResponseLimits(binding.getValue());
            }
        } catch (RuntimeException failure) {
            previous.forEach(RenewableElectricalController::setActivePowerResponseLimits);
            throw new InterpssException("BASEGEN response policy was not applied: " + failure.getMessage());
        }
        return entries;
    }
}
