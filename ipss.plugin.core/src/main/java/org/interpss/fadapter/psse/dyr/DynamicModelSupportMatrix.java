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
                .append("cases are loadable and pass the strict one-second flat-run gate; the\n")
                .append("full-system independent fault-parity milestone remains open.\n\n")
                .append("Current Texas2k audit (2026-09-11): `17/17` PSS/E DYR model names are\n")
                .append("loadable, and the standard PSS/E `WTDTA1` drive train now has a parser and\n")
                .append("runtime path. Acceptance uses PSS/E RAW/DYR plus `_gnet.idv` and\n")
                .append("`_MODREMOVE.idv`. Approved-list rows without a native PSS/E model name are\n")
                .append("excluded\n")
                .append("from coverage counts and the unsupported-model TODO. GE PSLF `.dyd` files are\n")
                .append("not discovered, parsed, inventoried, or tested by this workflow.\n")
                .append("`6/6` prepared cases pass the\n")
                .append("short flat and common Bus-7159 fault execution smokes, `6/6` pass the production\n")
                .append("strict one-second flat-run gate, and all `15/15` required location-specific\n")
                .append("faults complete as execution/sanity checks. Full-stack independent trajectory\n")
                .append("acceptance remains `0/6`. REGFMA1 has a registered public PowerWorld\n")
                .append("nine-state trajectory contract, and HYGOVD has a repeatable direct four-state\n")
                .append("PowerWorld contract that reuses core GENROU and the verified HYGOV runtime;\n")
                .append("PERC1 has native PSS/E PLBVF1 short-pulse and multi-ramp contracts covering\n")
                .append("cessation and reconnection. All 28 native PSS/E references cold-regenerate\n")
                .append("with matching published manifest content. See the plan's release checklist\n")
                .append("before interpreting any\n")
                .append("`LOADABLE` row as completed model validation.\n\n")
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
