package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.interpss.fadapter.psse.dyr.PsseDyrRecord;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.interpss.fadapter.psse.dyr.PsseDyrRepresentativeSelector;
import org.interpss.fadapter.psse.dyr.PsseDyrRepresentativeSelector.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PsseDyrRepresentativeSelectorTest {
    @Test
    void greedySelectionIsDeterministicAndCoversObservedPairs() throws Exception {
        List<Profile> ordered = profiles("");
        List<Profile> shuffled = new ArrayList<>(ordered);
        Collections.reverse(shuffled);

        var first = PsseDyrRepresentativeSelector.select(ordered);
        var second = PsseDyrRepresentativeSelector.select(shuffled);

        assertEquals(List.of("A", "B", "C", "D"), ids(first));
        assertEquals(ids(first), ids(second));
        assertEquals(5, first.candidateCount());
        assertEquals(4, first.selectedProfiles().size());
        assertEquals(18, first.observedCoverageItemCount());
        assertEquals(3, first.maximumDimensionCount());
        assertFalse(ids(first).contains("Z"),
                "a duplicate profile must not enlarge the permanent set");
        assertTrue(first.selectedProfiles().stream()
                .flatMap(selected -> selected.reasons().stream())
                .anyMatch(reason -> reason.contains("PFlag=0 & QFlag=1")));
    }

    @Test
    void failedFullCaseProfileIsPromotedAndPersisted(@TempDir Path tempDir) throws Exception {
        var selection = PsseDyrRepresentativeSelector.select(
                profiles("Case 5 voltage drift leader"));

        assertEquals("Z", selection.selectedProfiles().get(0).id());
        assertTrue(selection.selectedProfiles().get(0).reasons().get(0)
                .contains("Case 5 voltage drift leader"));
        assertFalse(ids(selection).contains("A"),
                "the promoted equivalent must replace the redundant profile");

        Path output = tempDir.resolve("representatives.json");
        selection.writeJson(output);
        String json = Files.readString(output);
        assertEquals(selection.toJson(), json);
        assertFalse(json.contains("\"promotionReason\""),
                "persist only the auditable selection reason, not input-only state");
        assertTrue(json.contains("promoted failure: Case 5 voltage drift leader"));
        assertTrue(json.contains("\"startLine\": 5"));
    }

    @Test
    void rejectsDuplicateIdsAndProfilesWithoutFeatures() throws Exception {
        PsseDyrRecord record = records().get(0);
        assertThrows(IllegalArgumentException.class, () ->
                PsseDyrRepresentativeSelector.select(List.of(
                        new Profile("same", record, Map.of("flag", "0")),
                        new Profile("same", record, Map.of("flag", "1")))));
        assertThrows(IllegalArgumentException.class, () ->
                PsseDyrRepresentativeSelector.select(List.of(
                        new Profile("empty", record, Map.of()))));
    }

    private static List<Profile> profiles(String promotion) throws Exception {
        List<PsseDyrRecord> records = records();
        return List.of(
                profile("A", records.get(0), "0", "0", "zero", ""),
                profile("B", records.get(1), "0", "1", "finite", ""),
                profile("C", records.get(2), "1", "0", "finite", ""),
                profile("D", records.get(3), "1", "1", "zero", ""),
                profile("Z", records.get(4), "0", "0", "zero", promotion));
    }

    private static Profile profile(String id, PsseDyrRecord record, String pFlag,
            String qFlag, String timeClass, String promotion) {
        return new Profile(id, record, Map.of(
                "PFlag", pFlag, "QFlag", qFlag, "Tpord", timeClass), promotion);
    }

    private static List<PsseDyrRecord> records() throws Exception {
        StringBuilder dyr = new StringBuilder();
        for (int bus = 1; bus <= 5; bus++) {
            dyr.append(bus).append(" 'REECA1' 1 0 0 0 /\n");
        }
        return PsseDyrRecordReader.read(new StringReader(dyr.toString()), "profiles.dyr");
    }

    private static List<String> ids(PsseDyrRepresentativeSelector.Selection selection) {
        return selection.selectedProfiles().stream().map(selected -> selected.id()).toList();
    }
}
