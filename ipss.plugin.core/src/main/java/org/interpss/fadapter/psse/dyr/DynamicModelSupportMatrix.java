package org.interpss.fadapter.psse.dyr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Collectors;

/** Generates human-readable support documentation from {@link DynamicModelCatalog}. */
public final class DynamicModelSupportMatrix {
    private DynamicModelSupportMatrix() {
    }

    public static String generateMarkdown() {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# InterPSS PSS/E dynamic-model support matrix\n\n")
                .append("Generated from `DynamicModelCatalog`; do not edit model rows manually.\n\n")
                .append("`LOADABLE` means that a catalog entry, parser path, and runtime class exist. It\n")
                .append("does **not** mean that the model has passed equation conformance, a stationary\n")
                .append("flat run, disturbance coverage, or independent trajectory comparison.\n")
                .append("Verification status is deliberately kept separate from this generated\n")
                .append("loadability inventory.\n\n")
                .append("| Model | Category | Aliases | Parameters | Support | Runtime class | Reference |\n")
                .append("|---|---|---|---:|---|---|---|\n");
        DynamicModelCatalog.allModels().stream()
                .sorted(Comparator.comparing(DynamicModelDescriptor::category)
                        .thenComparing(DynamicModelDescriptor::canonicalName))
                .forEach(model -> markdown.append("| ")
                        .append(model.canonicalName()).append(" | ")
                        .append(model.category()).append(" | ")
                        .append(model.aliases().stream().sorted()
                                .collect(Collectors.joining(", ")))
                        .append(" | ").append(model.parameterCount())
                        .append(" | ").append(model.supportStatus())
                        .append(" | `").append(model.runtimeClassName()).append("` | ")
                        .append("[PowerWorld](").append(model.reference()).append(") |\n"));
        return markdown.toString();
    }

    /** Writes the generated matrix to the single path supplied on the command line. */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one output Markdown path");
        }
        Path output = Path.of(args[0]);
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, generateMarkdown(), StandardCharsets.UTF_8);
    }
}
