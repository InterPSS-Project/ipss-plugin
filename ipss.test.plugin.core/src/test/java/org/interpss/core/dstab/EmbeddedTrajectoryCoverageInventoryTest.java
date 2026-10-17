package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.interpss.CorePluginTestSuite;
import org.interpss.core.dstab.reference.EmbeddedTrajectoryReference;
import org.interpss.core.dstab.reference.GeneratorGovernorEvidenceInventory;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.WeccModelApprovalStatus;
import org.junit.jupiter.api.Test;
import org.junit.platform.suite.api.SelectClasses;

/** Verifies representative categories in the embedded public checkpoint inventory. */
public class EmbeddedTrajectoryCoverageInventoryTest {
    @Test
    void everyApprovedGeneratorAndGovernorHasExplicitEvidenceLinks() throws Exception {
        var entries = GeneratorGovernorEvidenceInventory.entries();
        Set<String> required = Stream.concat(WeccApprovedDynamicModelCatalog.generators().stream(),
                WeccApprovedDynamicModelCatalog.governors().stream())
                .filter(row -> row.approvalStatus() == WeccModelApprovalStatus.APPROVED)
                .filter(row -> row.hasPsseModel())
                .map(row -> row.interpssModel()).collect(Collectors.toSet());
        assertEquals(27, required.size());
        assertEquals(required, entries.stream().map(entry -> entry.model()).collect(Collectors.toSet()));
        assertEquals(required.size(), entries.size(), "Duplicate model evidence row");
        Set<Class<?>> registered = Set.copyOf(Arrays.asList(
                CorePluginTestSuite.class.getAnnotation(SelectClasses.class).value()));
        for (var entry : entries) {
            assertTrue(DynamicModelCatalog.find(entry.model()).isPresent(), entry.model());
            assertTrue(registered.contains(entry.focusedTestClass()),
                    entry.model() + " focused test is not registered");
            assertTrue(registered.contains(entry.trajectoryTestClass()),
                    entry.model() + " trajectory comparison is not registered");
            assertTrue(PsseDyrRecordReader.read(entry.fixture()).stream()
                    .anyMatch(record -> record.canonicalModelName().equals(entry.model())),
                    entry.model() + " absent from " + entry.fixture());
            entry.requireReadableCheckpoint();
        }
        Path report = Path.of("target", "dynamic-model-evidence.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, GeneratorGovernorEvidenceInventory.markdown());
        assertEquals(Files.readString(Path.of("..", "docs", "generator-governor-evidence.md"))
                        .replace("\r\n", "\n"),
                GeneratorGovernorEvidenceInventory.markdown(), "Regenerate the evidence document");
    }

    @Test
    void embeddedInventoryCoversMachinesExcitersGovernorsAndStabilizers() throws Exception {
        for (String modelCase : List.of("smib-genqec", "smib-genrou-ac1c",
                "smib-genrou-tgov1", "smib-genrou-esst1a-pss2a")) {
            assertFalse(EmbeddedTrajectoryReference.embedded(modelCase).samples().isEmpty(), modelCase);
        }
    }
}
