package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Guards the portable paths and content hashes of every checked-in simulator artifact. */
public class ReferenceArtifactManifestIntegrityTest {
    private static final Set<String> CANONICAL_TEXT_SUFFIXES = Set.of(
            ".aux", ".csv", ".dyr", ".idv", ".json", ".log", ".raw", ".txt");

    @Test
    void everyReferenceManifestUsesPortablePathsAndMatchingHashes() throws Exception {
        Path module = moduleRoot();
        Path repository = module.getParent().toRealPath();
        Path references = module.resolve("testData/reference");
        List<String> errors = new ArrayList<>();
        int[] checked = {0};
        int manifestCount;

        try (Stream<Path> paths = Files.walk(references)) {
            List<Path> manifests = paths
                    .filter(path -> path.getFileName().toString().equals("manifest.json"))
                    .sorted()
                    .toList();
            manifestCount = manifests.size();
            for (Path manifest : manifests) {
                JsonObject root = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
                boolean canonicalText = root.has("schema")
                        && root.get("schema").getAsString().equals("interpss-powerworld-transient-benchmark-v1");
                validateNamedArtifacts(root, manifest, repository, canonicalText, errors, checked);
                validatePathHashObjects(root, manifest, repository, canonicalText, errors, checked);
            }
        }

        assertEquals(118, manifestCount, "reference manifest inventory changed");
        assertEquals(487, checked[0], "declared artifact/input hash inventory changed");
        assertTrue(errors.isEmpty(), () -> "Reference artifact manifest integrity failures:\n"
                + String.join("\n", errors));
    }

    private static void validateNamedArtifacts(JsonObject root, Path manifest, Path repository,
            boolean canonicalText, List<String> errors, int[] checked) throws Exception {
        JsonElement artifacts = root.get("artifacts");
        if (artifacts == null || !artifacts.isJsonObject()) return;
        for (var entry : artifacts.getAsJsonObject().entrySet()) {
            if (entry.getValue().isJsonObject()) {
                validate(entry.getKey(), entry.getValue().getAsJsonObject(), manifest,
                        repository, canonicalText, errors, checked);
            }
        }
    }

    private static void validatePathHashObjects(JsonElement element, Path manifest, Path repository,
            boolean canonicalText, List<String> errors, int[] checked) throws Exception {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray())
                validatePathHashObjects(child, manifest, repository, canonicalText, errors, checked);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        if (object.has("path") && object.has("sha256")) {
            validate(object.get("path").getAsString(), object, manifest, repository,
                    canonicalText, errors, checked);
        }
        for (var entry : object.entrySet())
            validatePathHashObjects(entry.getValue(), manifest, repository, canonicalText, errors, checked);
    }

    private static void validate(String declaredPath, JsonObject declaration, Path manifest,
            Path repository, boolean canonicalText, List<String> errors, int[] checked) throws Exception {
        Path relative;
        try {
            relative = Path.of(declaredPath);
        } catch (RuntimeException ex) {
            errors.add(label(manifest, repository) + ": invalid path " + declaredPath);
            return;
        }
        if (relative.isAbsolute()) {
            errors.add(label(manifest, repository) + ": absolute path discloses its host: " + declaredPath);
            return;
        }

        Path local = manifest.getParent().resolve(relative).normalize();
        Path repositoryPath = repository.resolve(relative).normalize();
        Path target = Files.isRegularFile(local) ? local : repositoryPath;
        if (!target.startsWith(repository) || !Files.isRegularFile(target)) {
            errors.add(label(manifest, repository) + ": missing or escaping path " + declaredPath);
            return;
        }

        String expected = declaration.get("sha256").getAsString().toLowerCase();
        String actual = sha256(target, canonicalText);
        if (!actual.equals(expected)) {
            errors.add(label(manifest, repository) + ": hash mismatch for " + declaredPath
                    + " expected=" + expected + " actual=" + actual);
        }
        checked[0]++;
    }

    private static String sha256(Path path, boolean canonicalText) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        String filename = path.getFileName().toString().toLowerCase();
        if (canonicalText && CANONICAL_TEXT_SUFFIXES.stream().anyMatch(filename::endsWith)) {
            ByteArrayOutputStream canonical = new ByteArrayOutputStream(bytes.length);
            for (int index = 0; index < bytes.length; index++) {
                if (bytes[index] == '\r') {
                    if (index + 1 < bytes.length && bytes[index + 1] == '\n') index++;
                    canonical.write('\n');
                } else {
                    canonical.write(bytes[index]);
                }
            }
            bytes = canonical.toByteArray();
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String label(Path manifest, Path repository) {
        return repository.relativize(manifest.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static Path moduleRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        return Files.isDirectory(current.resolve("testData/reference"))
                ? current : current.resolve("ipss.test.plugin.core");
    }
}
