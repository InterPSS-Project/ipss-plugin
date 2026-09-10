package org.interpss.fadapter.psse.dyr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Builds a deterministic coverage inventory from the WECC approved-model
 * catalog and completed PowerWorld benchmark manifests.
 *
 * <p>A benchmark counts as complete only when its manifest, referenced input
 * files, declared CSV, and canonical SHA-256 values agree. Model identities
 * come from the PSS/E DYR records rather than benchmark directory names.</p>
 */
public final class PowerWorldArtifactCoverageInventory {
    private static final String ARTIFACT_ROOT =
            "ipss.test.plugin.core/testData/reference/powerworld";

    private PowerWorldArtifactCoverageInventory() { }

    public static Report scan(Path repositoryRoot) throws IOException {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        Path artifactRoot = root.resolve(ARTIFACT_ROOT);
        Map<String, TreeSet<String>> artifactsByModel = new TreeMap<>();
        List<Artifact> artifacts = new ArrayList<>();

        try (Stream<Path> entries = Files.list(artifactRoot)) {
            for (Path directory : entries.filter(Files::isDirectory).sorted().toList()) {
                Path manifestPath = directory.resolve("manifest.json");
                if (!Files.isRegularFile(manifestPath)) continue;
                Artifact artifact = readArtifact(root, directory, manifestPath);
                artifacts.add(artifact);
                for (String model : artifact.models()) {
                    artifactsByModel.computeIfAbsent(model, ignored -> new TreeSet<>())
                            .add(artifact.name());
                }
            }
        }

        List<Row> rows = approvedRows().stream()
                .filter(row -> row.approvalStatus() == WeccModelApprovalStatus.APPROVED)
                .filter(WeccModelApproval::hasPsseModel)
                .sorted(Comparator.comparing(WeccModelApproval::category)
                        .thenComparing(WeccModelApproval::catalogName))
                .map(row -> toRow(row, artifactsByModel))
                .toList();
        Map<String, CategorySummary> categories = new LinkedHashMap<>();
        for (DynamicModelCategory category : DynamicModelCategory.values()) {
            List<Row> matching = rows.stream().filter(row -> row.category().equals(category.name()))
                    .toList();
            if (matching.isEmpty()) continue;
            categories.put(category.name(), new CategorySummary(matching.size(),
                    matching.stream().filter(Row::isExactLoadable).count(),
                    matching.stream().filter(Row::hasDirectArtifact).count()));
        }
        return new Report(WeccApprovedDynamicModelCatalog.VERSION, artifacts.size(),
                categories, List.copyOf(artifacts), rows);
    }

    private static Artifact readArtifact(Path repositoryRoot, Path directory, Path manifestPath)
            throws IOException {
        JsonObject manifest;
        try (var reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
            manifest = JsonParser.parseReader(reader).getAsJsonObject();
        }
        String name = requiredString(manifest, "benchmark", manifestPath);
        if (!name.equals(directory.getFileName().toString())) {
            throw new IOException("Benchmark/directory mismatch in " + manifestPath + ": " + name);
        }
        if (!"complete".equals(requiredString(manifest, "source_status", manifestPath))) {
            throw new IOException("Incomplete benchmark manifest: " + manifestPath);
        }
        JsonObject inputs = requiredObject(manifest, "inputs", manifestPath);
        for (Map.Entry<String, JsonElement> input : inputs.entrySet()) {
            validateDeclaredFile(repositoryRoot, input.getValue().getAsJsonObject(),
                    manifestPath + " input " + input.getKey());
        }
        JsonObject declaredArtifacts = requiredObject(manifest, "artifacts", manifestPath);
        JsonObject csvDeclaration = requiredObject(declaredArtifacts, "powerworld.csv", manifestPath);
        Path csv = directory.resolve("powerworld.csv");
        validateHash(csv, requiredString(csvDeclaration, "sha256", manifestPath));

        JsonObject dyrDeclaration = requiredObject(inputs, "dyr", manifestPath);
        Path dyr = resolveRepositoryPath(repositoryRoot,
                requiredString(dyrDeclaration, "path", manifestPath));
        TreeSet<String> models = new TreeSet<>();
        for (PsseDyrRecord record : PsseDyrRecordReader.read(dyr)) {
            DynamicModelDescriptor descriptor = DynamicModelCatalog.find(record.canonicalModelName())
                    .orElseThrow(() -> new IOException("PowerWorld artifact " + name
                            + " uses uncataloged DYR model " + record.sourceModelName()));
            models.add(descriptor.canonicalName());
        }
        if (models.isEmpty()) throw new IOException("Benchmark has no DYR models: " + name);
        return new Artifact(name, List.copyOf(models),
                repositoryRoot.relativize(manifestPath).toString().replace('\\', '/'));
    }

    private static void validateDeclaredFile(Path repositoryRoot, JsonObject declaration,
            String context) throws IOException {
        Path path = resolveRepositoryPath(repositoryRoot,
                requiredString(declaration, "path", context));
        validateHash(path, requiredString(declaration, "sha256", context));
    }

    private static void validateHash(Path path, String expected) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Missing declared file: " + path);
        String actual = canonicalSha256(path);
        if (!actual.equalsIgnoreCase(expected)) {
            throw new IOException("SHA-256 mismatch for " + path + ": expected " + expected
                    + " but found " + actual);
        }
    }

    private static Path resolveRepositoryPath(Path repositoryRoot, String value) throws IOException {
        Path path = Path.of(value);
        Path resolved = (path.isAbsolute() ? path : repositoryRoot.resolve(path))
                .toAbsolutePath().normalize();
        if (!resolved.startsWith(repositoryRoot)) {
            throw new IOException("Benchmark input is outside the repository: " + resolved);
        }
        return resolved;
    }

    private static String canonicalSha256(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String suffix = path.getFileName().toString().toLowerCase();
        if (suffix.endsWith(".aux") || suffix.endsWith(".csv") || suffix.endsWith(".dyr")
                || suffix.endsWith(".idv") || suffix.endsWith(".json")
                || suffix.endsWith(".log") || suffix.endsWith(".raw")
                || suffix.endsWith(".txt")) {
            String text = new String(bytes, StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").replace('\r', '\n');
            bytes = text.getBytes(StandardCharsets.UTF_8);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static Row toRow(WeccModelApproval approval,
            Map<String, TreeSet<String>> artifactsByModel) {
        String interpss = approval.interpssModel();
        boolean loadable = approval.isImplementedExactly();
        List<String> artifacts = interpss.isEmpty() ? List.of()
                : List.copyOf(artifactsByModel.getOrDefault(interpss, new TreeSet<>()));
        return new Row(approval.catalogName(), approval.category().name(), approval.psseModel(),
                interpss, loadable ? "EXACT_LOADABLE" : "MISSING_EXACT_IMPLEMENTATION",
                artifacts.isEmpty() ? "MISSING_DIRECT_ARTIFACT" : "COMPLETE_DIRECT_ARTIFACT",
                artifacts);
    }

    private static List<WeccModelApproval> approvedRows() {
        return Stream.of(WeccApprovedDynamicModelCatalog.generators(),
                        WeccApprovedDynamicModelCatalog.exciters(),
                        WeccApprovedDynamicModelCatalog.stabilizers(),
                        WeccApprovedDynamicModelCatalog.governors())
                .flatMap(Collection::stream).toList();
    }

    private static JsonObject requiredObject(JsonObject parent, String name, Object context)
            throws IOException {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonObject()) {
            throw new IOException("Missing object '" + name + "' in " + context);
        }
        return value.getAsJsonObject();
    }

    private static String requiredString(JsonObject parent, String name, Object context)
            throws IOException {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("Missing string '" + name + "' in " + context);
        }
        return value.getAsString();
    }

    public record Artifact(String name, List<String> models, String manifest) {
        public Artifact { models = List.copyOf(models); }
    }

    public record CategorySummary(int approvedPsseRows, long exactLoadableRows,
            long rowsWithDirectArtifact) { }

    public record Row(String approvedName, String category, String psseModel,
            String interpssModel, String implementationStatus, String artifactStatus,
            List<String> artifacts) {
        public Row { artifacts = List.copyOf(artifacts); }
        public boolean isExactLoadable() { return implementationStatus.equals("EXACT_LOADABLE"); }
        public boolean hasDirectArtifact() {
            return artifactStatus.equals("COMPLETE_DIRECT_ARTIFACT");
        }
    }

    public record Report(String approvedCatalogVersion, int completedArtifactCount,
            Map<String, CategorySummary> categories, List<Artifact> artifacts, List<Row> rows) {
        public Report {
            categories = Collections.unmodifiableMap(new LinkedHashMap<>(categories));
            artifacts = List.copyOf(artifacts);
            rows = List.copyOf(rows);
        }

        public String toJson() {
            return new GsonBuilder().setPrettyPrinting().create().toJson(this) + "\n";
        }

        public String toMarkdown() {
            StringBuilder text = new StringBuilder("# PowerWorld dynamic-model artifact coverage\n\n")
                    .append("Generated from the WECC ").append(approvedCatalogVersion)
                    .append(" catalog and validated benchmark manifests. A direct artifact means ")
                    .append("the model appears in the manifest's native PSS/E DYR input; aliases ")
                    .append("and related-model evidence are not counted.\n\n")
                    .append("Completed benchmark artifacts: `").append(completedArtifactCount)
                    .append("`.\n\n")
                    .append("| Category | Approved PSS/E rows | Exact-loadable | Direct artifact | Remaining artifact gaps |\n")
                    .append("|---|---:|---:|---:|---:|\n");
            categories.forEach((category, summary) -> text.append('|').append(' ')
                    .append(category).append(" | ").append(summary.approvedPsseRows())
                    .append(" | ").append(summary.exactLoadableRows()).append(" | ")
                    .append(summary.rowsWithDirectArtifact()).append(" | ")
                    .append(summary.approvedPsseRows() - summary.rowsWithDirectArtifact())
                    .append(" |\n"));
            text.append("\n## Remaining rows\n\n")
                    .append("| Category | WECC row | Native PSS/E | InterPSS | Implementation |\n")
                    .append("|---|---|---|---|---|\n");
            rows.stream().filter(row -> !row.hasDirectArtifact()).forEach(row -> text
                    .append("| ").append(row.category()).append(" | ").append(row.approvedName())
                    .append(" | ").append(row.psseModel()).append(" | ")
                    .append(row.interpssModel().isEmpty() ? "-" : row.interpssModel())
                    .append(" | ").append(row.implementationStatus()).append(" |\n"));
            return text.toString();
        }

        public void write(Path directory) throws IOException {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("powerworld-artifact-coverage.json"), toJson(),
                    StandardCharsets.UTF_8);
            Files.writeString(directory.resolve("powerworld-artifact-coverage.md"), toMarkdown(),
                    StandardCharsets.UTF_8);
        }
    }
}
