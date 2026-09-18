package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.interpss.fadapter.psse.dyr.PsseDyrRecord;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.Test;

/** Fleet-wide schema/profile gate for existing Texas2k legacy controllers. */
public class Texas2kLegacyControllerProfileTest {
    private static final Path ROOT = Path.of(System.getProperty("texas2k.case.root",
            Path.of("testData", "private", "texas2k").toString()));
    private static final Set<String> MODELS = Set.of("EXST1", "IEEET1", "IEEEG1");
    private static final Map<String, Integer> EXPECTED_RECORDS = Map.of(
            "EXST1", 756, "IEEET1", 96, "IEEEG1", 258);
    private static final Map<String, Integer> EXPECTED_PROFILES = Map.of(
            "EXST1", 126, "IEEET1", 16, "IEEEG1", 43);
    private static final Map<String, Integer> EXPECTED_PARAMETERS = Map.of(
            "EXST1", 12, "IEEET1", 14, "IEEEG1", 22);

    @Test
    void coversEveryTexas2kLegacyControllerProfileAndSelector() throws Exception {
        assumeTrue(Files.isDirectory(ROOT), "Missing private Texas2k root: " + ROOT);
        List<PsseDyrRecord> records = new ArrayList<>();
        try (var directories = Files.list(ROOT)) {
            for (Path directory : directories.filter(Files::isDirectory).sorted().toList()) {
                try (var files = Files.list(directory)) {
                    Path dyr = files.filter(path -> path.getFileName().toString().endsWith(".dyr"))
                            .findFirst().orElse(null);
                    if (dyr != null) {
                        PsseDyrRecordReader.read(dyr).stream()
                                .filter(record -> MODELS.contains(record.canonicalModelName()))
                                .forEach(records::add);
                    }
                }
            }
        }

        for (String model : MODELS) {
            List<PsseDyrRecord> modelRecords = records.stream()
                    .filter(record -> record.canonicalModelName().equals(model)).toList();
            assertEquals(EXPECTED_RECORDS.get(model), modelRecords.size(), model);
            assertTrue(modelRecords.stream().allMatch(
                    record -> record.parameterCount() == EXPECTED_PARAMETERS.get(model)),
                    model + " parameter count");
            assertEquals(EXPECTED_PROFILES.get(model), modelRecords.stream()
                    .map(PsseDyrRecord::parameters).map(List::copyOf)
                    .collect(Collectors.toSet()).size(), model + " profiles");
        }

        assertEquals(Set.of("0"), records.stream()
                .filter(record -> record.canonicalModelName().equals("IEEET1"))
                .map(record -> record.parameters().get(9)).collect(Collectors.toSet()),
                "IEEET1 Switch values represented by Texas2k");
        assertEquals(Set.of("0:0"), records.stream()
                .filter(record -> record.canonicalModelName().equals("IEEEG1"))
                .map(record -> record.parameters().get(0) + ":" + record.parameters().get(1))
                .collect(Collectors.toSet()),
                "IEEEG1 JBUS:M selectors represented by Texas2k");
    }
}
