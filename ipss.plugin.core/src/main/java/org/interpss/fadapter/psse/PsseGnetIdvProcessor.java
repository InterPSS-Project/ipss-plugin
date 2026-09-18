package org.interpss.fadapter.psse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.dstab.BaseDStabNetwork;

/** Applies the generator-to-negative-load (GNET) command from a PSS/E IDV file. */
public final class PsseGnetIdvProcessor {
    private PsseGnetIdvProcessor() { }

    /** Stable key for a generator intentionally removed by GNET preprocessing. */
    public record GeneratorKey(String busId, String generatorId) { }

    /** Summary of one IDV application. */
    public record Result(int requestedBuses, int convertedBuses, int convertedGenerators,
            Set<GeneratorKey> convertedGeneratorKeys) {
        public Result {
            convertedGeneratorKeys = Set.copyOf(convertedGeneratorKeys);
        }
    }

    /**
     * Parse all GNET blocks in {@code idvFile} and apply them to {@code network}.
     * Each active generator at a listed bus becomes an active constant-power
     * negative load with the same solved P/Q injection.
     */
    public static Result apply(BaseDStabNetwork<?, ?> network, String idvFile)
            throws InterpssException {
        Set<String> busIds;
        try {
            busIds = parseGnetBusIds(Path.of(idvFile));
        } catch (IOException | IllegalArgumentException ex) {
            throw new InterpssException("Error reading PSS/E GNET IDV file: "
                    + idvFile + ": " + ex.getMessage());
        }

        AclfNetworkBuilder builder = new AclfNetworkBuilder(network);
        int convertedBuses = 0;
        int convertedGenerators = 0;
        Set<GeneratorKey> convertedGeneratorKeys = new LinkedHashSet<>();
        for (String busId : busIds) {
            BaseAclfBus<?, ?> bus = (BaseAclfBus<?, ?>) network.getBus(busId);
            if (bus == null) {
                throw new InterpssException("GNET references missing bus " + busId
                        + " in " + idvFile);
            }

            int convertedAtBus = 0;
            for (Object item : bus.getContributeGenList()) {
                AclfGen gen = (AclfGen) item;
                if (!gen.isActive()) {
                    continue;
                }
                Complex injection = gen.getGen() == null ? Complex.ZERO : gen.getGen();
                String loadId = uniqueLoadId(bus, "GNET-" + gen.getId());
                builder.addContributeLoad(busId, loadId, true, injection.negate(),
                        null, null, null, false);
                gen.setStatus(false);
                convertedGeneratorKeys.add(new GeneratorKey(busId, gen.getId()));
                convertedAtBus++;
            }

            if (convertedAtBus > 0) {
                convertedBuses++;
                convertedGenerators += convertedAtBus;
            }
            boolean hasActiveGenerator = bus.getContributeGenList().stream()
                    .map(AclfGen.class::cast)
                    .anyMatch(AclfGen::isActive);
            if (!hasActiveGenerator) {
                bus.setGenCode(AclfGenCode.NON_GEN);
            }
        }
        return new Result(busIds.size(), convertedBuses, convertedGenerators,
                convertedGeneratorKeys);
    }

    static Set<String> parseGnetBusIds(Path idvFile) throws IOException {
        Set<String> busIds = new LinkedHashSet<>();
        boolean inGnet = false;
        for (String original : Files.readAllLines(idvFile)) {
            String line = stripComment(original).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equalsIgnoreCase("GNET")) {
                inGnet = true;
                continue;
            }
            if (!inGnet) {
                continue;
            }
            String token = line.split("[\\s,]+", 2)[0];
            if (token.equals("0")) {
                inGnet = false;
                continue;
            }
            try {
                long number = Long.parseLong(token);
                busIds.add("Bus" + number);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid GNET bus record: " + original, ex);
            }
        }
        if (inGnet) {
            throw new IllegalArgumentException("Unterminated GNET block in " + idvFile);
        }
        return busIds;
    }

    private static String stripComment(String line) {
        int comment = line.indexOf("//");
        return comment >= 0 ? line.substring(0, comment) : line;
    }

    private static String uniqueLoadId(BaseAclfBus<?, ?> bus, String base) {
        String candidate = base;
        int suffix = 2;
        while (bus.getContributeLoad(candidate) != null) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }
}
