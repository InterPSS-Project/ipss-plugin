package org.interpss.fadapter.psse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.BaseDStabNetwork;

import org.interpss.fadapter.psse.PsseGnetIdvProcessor.GeneratorKey;

/** Parses PSS/E {@code BAT_PLMOD_REMOVE} case-preparation commands. */
public final class PsseModelRemoveIdvProcessor {
    private static final int MACHINE_MODEL_TYPE = 1;
    private static final Pattern COMMAND = Pattern.compile(
            "(?i)^\\s*BAT_PLMOD_REMOVE\\s+([+-]?\\d+)\\s+"
                    + "(?:'([^']*)'|\"([^\"]*)\"|([^\\s,]+))\\s+([+-]?\\d+)\\s*$");

    private PsseModelRemoveIdvProcessor() { }

    /** One model-removal command, preserving the PSS/E model-type selector. */
    public record Directive(GeneratorKey generator, int modelType) {
        public Directive {
            java.util.Objects.requireNonNull(generator);
            if (modelType < 1 || modelType > 10)
                throw new IllegalArgumentException("Model-removal selector must be between 1 and 10");
        }
    }

    /** Native plant-model slot for a known runtime record; zero denotes a different model family. */
    public static int modelType(org.interpss.fadapter.psse.dyr.PsseDyrRecord record) {
        if (record.canonicalModelName().equals("LCFB1")) return 8;
        return org.interpss.fadapter.psse.dyr.DynamicModelCatalog.find(record.canonicalModelName())
                .map(model -> switch (model.category()) {
                    case SYNCHRONOUS_MACHINE, CONVERTER_MACHINE -> 1;
                    case COMPENSATOR -> 2;
                    case STABILIZER -> 3;
                    case UNDER_EXCITATION_LIMITER -> 4;
                    case OVER_EXCITATION_LIMITER -> 5;
                    case EXCITER -> 6;
                    case GOVERNOR -> 7;
                    default -> 0;
                }).orElse(0);
    }

    /** Parsed commands and generators whose machine/converter model is removed. */
    public record Result(Set<Directive> directives, Set<GeneratorKey> removedGeneratorKeys) {
        public Result {
            directives = Set.copyOf(directives);
            removedGeneratorKeys = Set.copyOf(removedGeneratorKeys);
        }
    }

    /**
     * Parse and validate model-removal commands against the already loaded RAW
     * network. A type-1 command removes the machine/converter model and makes
     * every DYR record for that generator intentionally inapplicable.
     */
    public static Result apply(BaseDStabNetwork<?, ?> network, String idvFile)
            throws InterpssException {
        Set<Directive> directives;
        try {
            directives = parse(Path.of(idvFile));
        } catch (IOException | IllegalArgumentException ex) {
            throw new InterpssException("Error reading PSS/E model-removal IDV file: "
                    + idvFile + ": " + ex.getMessage());
        }

        Set<GeneratorKey> removedGenerators = new LinkedHashSet<>();
        for (Directive directive : directives) {
            GeneratorKey key = directive.generator();
            var bus = network.getBus(key.busId());
            if (bus == null) {
                throw new InterpssException("BAT_PLMOD_REMOVE references missing bus "
                        + key.busId() + " in " + idvFile);
            }
            if (bus.getContributeGen(key.generatorId()) == null) {
                throw new InterpssException("BAT_PLMOD_REMOVE references missing generator "
                        + key.busId() + "/" + key.generatorId() + " in " + idvFile);
            }
            if (directive.modelType() == MACHINE_MODEL_TYPE) {
                removedGenerators.add(key);
            }
        }
        return new Result(directives, removedGenerators);
    }

    static Set<Directive> parse(Path idvFile) throws IOException {
        Set<Directive> directives = new LinkedHashSet<>();
        for (String original : Files.readAllLines(idvFile)) {
            String line = stripComment(original).trim();
            if (line.isEmpty() || !line.regionMatches(true, 0,
                    "BAT_PLMOD_REMOVE", 0, "BAT_PLMOD_REMOVE".length())) {
                continue;
            }
            Matcher matcher = COMMAND.matcher(line);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                        "Invalid BAT_PLMOD_REMOVE command: " + original);
            }
            String generatorId = firstNonNull(matcher.group(2), matcher.group(3),
                    matcher.group(4)).trim();
            if (generatorId.isEmpty()) {
                throw new IllegalArgumentException(
                        "Empty generator ID in BAT_PLMOD_REMOVE command: " + original);
            }
            directives.add(new Directive(
                    new GeneratorKey("Bus" + Long.parseLong(matcher.group(1)), generatorId),
                    Integer.parseInt(matcher.group(5))));
        }
        return directives;
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) return value;
        }
        return "";
    }

    private static String stripComment(String line) {
        int comment = line.indexOf("//");
        return comment >= 0 ? line.substring(0, comment) : line;
    }
}
