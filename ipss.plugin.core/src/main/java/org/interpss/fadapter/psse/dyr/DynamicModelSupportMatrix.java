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
                .append("flat run, a representative disturbance matrix, or an independent-tool\n")
                .append("trajectory comparison. Those acceptance results are tracked in\n")
                .append("`dynamic-model-coverage-development-plan.md`. In particular, the six Texas2k\n")
                .append("cases are currently loadable, but only Cases 1 and 2 pass the strict one-second\n")
                .append("flat-run gate; the Texas2k verification milestone remains open.\n\n")
                .append("Current Texas2k audit (2026-09-07): `17/17` PSS/E DYR model names are\n")
                .append("loadable, and the standard PSS/E `WTDTA1` drive train now has a parser and\n")
                .append("runtime path. This is still not complete supplied-source coverage: the sibling\n")
                .append("PowerWorld DYD files contain `WTGT_A` records (85 per case in Cases 1-2 and\n")
                .append("184 per case in Cases 3-6) that are absent from the DYR exports and do not yet\n")
                .append("have a supplemental import path. Separately, `6/6` prepared cases pass the\n")
                .append("short flat and common Bus-7159 fault execution smokes, `2/6` pass the strict\n")
                .append("one-second flat-run gate, `0/6` have completed the required location-specific\n")
                .append("fault acceptance matrix, and `0/6` have full-stack independent trajectory\n")
                .append("acceptance. See the plan's release checklist before interpreting any `LOADABLE`\n")
                .append("row as completed model validation.\n\n")
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
