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
import org.interpss.DynamicModelSmibTestSuite;
import org.interpss.core.dstab.reference.EmbeddedTrajectoryReference;
import org.interpss.core.dstab.reference.GeneratorGovernorEvidenceInventory;
import org.interpss.core.dstab.reference.ExciterEvidenceInventory;
import org.interpss.core.dstab.reference.StabilizerEvidenceInventory;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.WeccModelApprovalStatus;
import org.junit.jupiter.api.Test;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;

/** Verifies representative categories in the embedded public checkpoint inventory. */
public class EmbeddedTrajectoryCoverageInventoryTest {
    @Test
    void approvedStabilizerMappingsHaveExplicitEvidenceAndEquivalenceGaps() throws Exception {
        var rows=WeccApprovedDynamicModelCatalog.stabilizers().stream()
                .filter(row -> row.approvalStatus()==WeccModelApprovalStatus.APPROVED)
                .toList();
        assertEquals(15,rows.size());
        Set<String> required=rows.stream().map(row -> row.interpssModel()).collect(Collectors.toSet());
        var entries=StabilizerEvidenceInventory.entries();
        assertEquals(14,required.size());
        assertEquals(required,entries.stream().map(row -> row.model()).collect(Collectors.toSet()));
        assertEquals(required.size(),entries.size(),"Do not count source-list aliases twice");
        Set<Class<?>> registered=Set.copyOf(Arrays.asList(
                CorePluginTestSuite.class.getAnnotation(SelectClasses.class).value()));
        for(var row:entries) {
            var e=row.evidence();
            var descriptor=DynamicModelCatalog.find(e.model()).orElseThrow();
            assertTrue(registered.contains(e.focusedTestClass()),e.model()+" focused test is not registered");
            assertTrue(registered.contains(e.trajectoryTestClass()),e.model()+" comparison is not registered");
            for(Class<?> test:List.of(e.focusedTestClass(),e.trajectoryTestClass())) {
                String name=test.getName();
                assertTrue(Arrays.stream(DynamicModelSmibTestSuite.class.getAnnotation(SelectPackages.class).value())
                        .anyMatch(pkg -> name.startsWith(pkg+".")),name+" is outside SMIB discovery packages");
                assertTrue(Arrays.stream(DynamicModelSmibTestSuite.class.getAnnotation(IncludeClassNamePatterns.class).value())
                        .anyMatch(name::matches),name+" is not selected by the SMIB gate");
                assertFalse(Arrays.stream(DynamicModelSmibTestSuite.class.getAnnotation(ExcludeClassNamePatterns.class).value())
                        .anyMatch(name::matches),name+" is excluded by the SMIB gate");
            }
            var records=PsseDyrRecordReader.read(e.fixture()).stream()
                    .filter(record -> record.canonicalModelName().equals(e.model())).toList();
            assertEquals(1,records.size(),e.model()+" fixture must contain its native mapping exactly once");
            assertTrue(descriptor.recordSchema().accepts(records.get(0).parameterCount()),e.model());
            e.requireReadableCheckpoint();
            assertFalse(row.limitation().isBlank(),"An evidence link must disclose its scope limitations");
        }
        assertEquals(Set.of("PSS1A"),entries.stream()
                .filter(row -> row.scope()==StabilizerEvidenceInventory.ReferenceScope.EQUIVALENT_SIGNAL_PLAYBACK)
                .map(row -> row.model()).collect(Collectors.toSet()),
                "Equivalent play-in cannot silently become an exact-model reference");
        assertTrue(registered.contains(Pss1aPowerWorldPlaybackConformanceTest.class),
                "Supplementary exact typed PSS1A playback must remain registered");
    }

    @Test
    void stabilizerEvidenceDocumentMatchesItsReviewedInventory() throws Exception {
        String markdown=StabilizerEvidenceInventory.markdown();
        Path report=Path.of("target","stabilizer-evidence.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report,markdown);
        assertEquals(Files.readString(Path.of("..","docs","stabilizer-evidence.md")).replace("\r\n","\n"),markdown);
    }

    @Test
    void everyApprovedNativeExciterHasExplicitEvidenceLinks() throws Exception {
        var rows = WeccApprovedDynamicModelCatalog.exciters().stream()
                .filter(row -> row.approvalStatus() == WeccModelApprovalStatus.APPROVED)
                .filter(row -> row.hasPsseModel()).toList();
        assertEquals(64, rows.size());
        Set<String> required = rows.stream().map(row -> row.interpssModel())
                .collect(Collectors.toSet());
        var entries = ExciterEvidenceInventory.entries();
        assertEquals(59, required.size());
        assertEquals(required, entries.stream().map(entry -> entry.model()).collect(Collectors.toSet()));
        assertEquals(required.size(), entries.size(), "Duplicate native exciter evidence row");
        Set<Class<?>> registered = Set.copyOf(Arrays.asList(
                CorePluginTestSuite.class.getAnnotation(SelectClasses.class).value()));
        for (var entry : entries) {
            var descriptor = DynamicModelCatalog.find(entry.model()).orElseThrow();
            assertTrue(registered.contains(entry.focusedTestClass()),
                    entry.model() + " focused test is not registered");
            assertTrue(registered.contains(entry.trajectoryTestClass()),
                    entry.model() + " trajectory comparison is not registered");
            var records = PsseDyrRecordReader.read(entry.fixture()).stream()
                    .filter(record -> record.canonicalModelName().equals(entry.model())).toList();
            assertEquals(1, records.size(), entry.model() + " fixture must contain one native record");
            assertTrue(descriptor.recordSchema().accepts(records.get(0).parameterCount()),
                    entry.model() + " fixture violates its native record-length contract");
            entry.requireReadableCheckpoint();
        }
        String markdown = ExciterEvidenceInventory.markdown();
        Path report = Path.of("target", "exciter-evidence.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, markdown);
        assertEquals(Files.readString(Path.of("..", "docs", "exciter-evidence.md"))
                .replace("\r\n", "\n"), markdown, "Regenerate the exciter evidence document");
    }

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
