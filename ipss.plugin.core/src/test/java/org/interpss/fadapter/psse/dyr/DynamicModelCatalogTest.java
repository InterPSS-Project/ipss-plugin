package org.interpss.fadapter.psse.dyr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DynamicModelCatalogTest {
    @Test
    void catalogsEveryTexas2kModelAndResolvesAliases() {
        assertEquals(23, DynamicModelCatalog.texas2kModels().size());
        assertEquals("GENROU", DynamicModelCatalog.canonicalName("genroe"));
        assertEquals("REGCA1", DynamicModelCatalog.canonicalName("regcau1"));
        assertEquals("REPCA1", DynamicModelCatalog.canonicalName("repcta1"));
        assertEquals(51, DynamicModelCatalog.find("REECA1").orElseThrow().parameterCount());
        assertEquals(30, DynamicModelCatalog.find("REECB1").orElseThrow().parameterCount());
        assertEquals(DynamicModelCategory.DRIVE_TRAIN,
                DynamicModelCatalog.find("WTDTA1").orElseThrow().category());
        assertEquals(5, DynamicModelCatalog.find("WTDTAU1").orElseThrow().parameterCount());
        assertEquals("WTDTA1", DynamicModelCatalog.canonicalName("WTDAT1"));
        assertEquals("GENQEJ", DynamicModelCatalog.canonicalName("genqeju"));
        assertEquals(20, DynamicModelCatalog.find("GENQEC").orElseThrow().parameterCount());
        assertFalse(DynamicModelCatalog.find("unknown-model").isPresent());
    }

    @Test
    void keepsMay2026GeneratorApprovalSeparateFromImplementationStatus() {
        assertEquals("May 2026", WeccApprovedDynamicModelCatalog.VERSION);
        assertEquals(8, WeccApprovedDynamicModelCatalog.generators().size());
        WeccModelApproval genqej = WeccApprovedDynamicModelCatalog.findGenerator("genqej")
                .orElseThrow();
        assertEquals(WeccModelApprovalStatus.APPROVED, genqej.approvalStatus());
        assertEquals("2026-01-30", genqej.statusEffective());
        assertTrue(genqej.isImplementedExactly());

        WeccModelApproval gentpj = WeccApprovedDynamicModelCatalog.findGenerator("GENTPJ")
                .orElseThrow();
        assertEquals(WeccModelApprovalStatus.UNAPPROVED, gentpj.approvalStatus());
        assertFalse(gentpj.isImplementedExactly());
    }

    @Test
    void catalogsEveryMay2026StabilizerRowAndExposesSupportGaps() {
        assertEquals(16, WeccApprovedDynamicModelCatalog.stabilizers().size());
        long approved = WeccApprovedDynamicModelCatalog.stabilizers().stream()
                .filter(row -> row.approvalStatus() == WeccModelApprovalStatus.APPROVED)
                .count();
        long implemented = WeccApprovedDynamicModelCatalog.stabilizers().stream()
                .filter(WeccModelApproval::isImplementedExactly)
                .count();
        assertEquals(15, approved);
        assertEquals(13, implemented);
        assertTrue(DynamicModelCatalog.find("WSCCST").orElseThrow().supportStatus()
                == DynamicModelSupportStatus.LOADABLE);
        assertEquals(DynamicModelSupportStatus.LOADABLE,
                DynamicModelCatalog.find("PSS1A").orElseThrow().supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.stabilizers().stream()
                .filter(row -> row.catalogName().equals("PSS1A"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.stabilizers().stream()
                .filter(row -> row.catalogName().equals("PSS2B"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertEquals(Set.of(27, 31), DynamicModelCatalog.find("PSS2B").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertTrue(WeccApprovedDynamicModelCatalog.stabilizers().stream()
                .filter(row -> row.catalogName().equals("PSS2C"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertEquals(Set.of(35), DynamicModelCatalog.find("PSS2C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertTrue(WeccApprovedDynamicModelCatalog.stabilizers().stream()
                .filter(row -> row.catalogName().equals("PSS3B"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertEquals(Set.of(19), DynamicModelCatalog.find("PSS3B").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertTrue(WeccApprovedDynamicModelCatalog.stabilizers().stream()
                .filter(row -> row.catalogName().equals("PSS4B"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertEquals(Set.of(75), DynamicModelCatalog.find("PSS4B").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertTrue(WeccApprovedDynamicModelCatalog.stabilizers().stream()
                .filter(row -> row.catalogName().equals("PSS3C"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertEquals(Set.of(24), DynamicModelCatalog.find("PSS3C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertTrue(WeccApprovedDynamicModelCatalog.stabilizers().stream()
                .filter(row -> row.catalogName().equals("PSS4C"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertEquals(Set.of(94), DynamicModelCatalog.find("PSS4C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertTrue(WeccApprovedDynamicModelCatalog.stabilizers().stream()
                .filter(row -> row.catalogName().equals("PSS5C"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertEquals(Set.of(21), DynamicModelCatalog.find("PSS5C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertTrue(WeccApprovedDynamicModelCatalog.stabilizers().stream()
                .filter(row -> row.catalogName().equals("PSS6C"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertEquals(Set.of(34, 35), DynamicModelCatalog.find("PSS6C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertTrue(WeccApprovedDynamicModelCatalog.stabilizers().stream()
                .filter(row -> row.catalogName().equals("PSS7C"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertEquals(Set.of(38, 39), DynamicModelCatalog.find("PSS7C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
    }

    @Test
    void catalogsEveryMay2026ExciterRowAndExposesSupportGaps() {
        var rows = WeccApprovedDynamicModelCatalog.exciters();
        assertEquals(71, rows.size());
        assertEquals(65, rows.stream()
                .filter(row -> row.approvalStatus() == WeccModelApprovalStatus.APPROVED).count());
        assertEquals(44, rows.stream().filter(WeccModelApproval::isImplementedExactly).count());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("EXELI")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("exac1m")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esac1c")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esac2c")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esac3c")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esac4c")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esac5c")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esac6c")
                .orElseThrow().isImplementedExactly());
        var esac10c=WeccApprovedDynamicModelCatalog.findExciter("esac10c").orElseThrow();
        assertEquals("esac10c",esac10c.pslfModel());
        assertEquals("",esac10c.psseModel());
        assertEquals("AC10C",esac10c.powerWorldModel());
        assertFalse(esac10c.isImplementedExactly());
        assertTrue(DynamicModelCatalog.find("AC10C").isEmpty());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esst3a")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esdc1a")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esdc2a")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esdc1c")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esdc2c")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esdc4c")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esac5a")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("scrx")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("rexs")
                .orElseThrow().isImplementedExactly());
        assertEquals(DynamicModelSupportStatus.LOADABLE,
                DynamicModelCatalog.find("ESDC1A").orElseThrow().supportStatus());
        assertEquals(Set.of(19), DynamicModelCatalog.find("DC1C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertEquals(Set.of(19), DynamicModelCatalog.find("DC2C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertEquals(Set.of(28), DynamicModelCatalog.find("DC4C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertEquals(DynamicModelSupportStatus.LOADABLE,
                DynamicModelCatalog.find("ESAC5A").orElseThrow().supportStatus());
        assertEquals(DynamicModelSupportStatus.LOADABLE,
                DynamicModelCatalog.find("EXAC1").orElseThrow().supportStatus());
        assertEquals(DynamicModelSupportStatus.LOADABLE,
                DynamicModelCatalog.find("EXAC1A").orElseThrow().supportStatus());
        assertEquals(DynamicModelSupportStatus.LOADABLE,
                DynamicModelCatalog.find("EXAC2").orElseThrow().supportStatus());
        assertEquals(DynamicModelSupportStatus.LOADABLE,
                DynamicModelCatalog.find("ESAC1A").orElseThrow().supportStatus());
        assertEquals(Set.of(23), DynamicModelCatalog.find("AC1C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertEquals(Set.of(25), DynamicModelCatalog.find("AC2C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertEquals(Set.of(30), DynamicModelCatalog.find("AC3C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertEquals(Set.of(12), DynamicModelCatalog.find("AC4C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertEquals(Set.of(21), DynamicModelCatalog.find("AC5C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertEquals(Set.of(27), DynamicModelCatalog.find("AC6C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertEquals(Set.of(38), DynamicModelCatalog.find("AC7C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertEquals(Set.of(31), DynamicModelCatalog.find("AC8C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertEquals(Set.of(45), DynamicModelCatalog.find("AC9C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertEquals(Set.of(40), DynamicModelCatalog.find("AC11C").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertEquals(Set.of(11), DynamicModelCatalog.find("BBSEX1").orElseThrow()
                .recordSchema().acceptedParameterCounts());
        assertTrue(DynamicModelCatalog.find("ESAC1C").isEmpty());
        assertTrue(DynamicModelCatalog.find("ESAC2C").isEmpty());
        assertTrue(DynamicModelCatalog.find("ESAC3C").isEmpty());
        assertTrue(DynamicModelCatalog.find("ESAC4C").isEmpty());
        assertTrue(DynamicModelCatalog.find("ESAC5C").isEmpty());
        assertTrue(DynamicModelCatalog.find("ESAC6C").isEmpty());
        assertTrue(DynamicModelCatalog.find("ESAC7C").isEmpty());
        assertTrue(DynamicModelCatalog.find("ESAC8C").isEmpty());
        assertTrue(DynamicModelCatalog.find("ESAC9C").isEmpty());
        assertTrue(DynamicModelCatalog.find("ESAC11C").isEmpty());
        assertTrue(DynamicModelCatalog.find("EXBBC").isEmpty());
        assertEquals(DynamicModelSupportStatus.LOADABLE,
                DynamicModelCatalog.find("ESAC2A").orElseThrow().supportStatus());
        assertEquals(DynamicModelSupportStatus.LOADABLE,
                DynamicModelCatalog.find("IEEEX1").orElseThrow().supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("exdc1")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("exdc2")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("exdc2a")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("exdc4")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esac8b")
                .orElseThrow().isImplementedExactly());
        assertEquals(21, DynamicModelCatalog.find("AC8B").orElseThrow().parameterCount());
        assertEquals(27, DynamicModelCatalog.find("AC7B").orElseThrow().parameterCount());
        assertEquals("AC7B", DynamicModelCatalog.canonicalName("ESAC7B"));
        assertTrue(DynamicModelCatalog.find("ESAC7B").orElseThrow()
                .recordSchema().accepts(28));
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esac7b")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esdc4b")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esst6b")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esst2a")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("exst2")
                .orElseThrow().isImplementedExactly());
        assertEquals("IEEET4", DynamicModelCatalog.canonicalName("EXDC4"));
        assertFalse(WeccApprovedDynamicModelCatalog.findExciter("exst4b")
                .orElseThrow().isImplementedExactly());
        assertFalse(DynamicModelCatalog.find("EXST4B").isPresent());
    }

    @Test
    void catalogsEveryMay2026GovernorRowAndExposesSupportGaps() {
        var rows = WeccApprovedDynamicModelCatalog.governors();
        assertEquals(32, rows.size());
        assertEquals(25, rows.stream()
                .filter(row -> row.approvalStatus() == WeccModelApprovalStatus.APPROVED).count());
        assertEquals(25, rows.stream().filter(WeccModelApproval::isImplementedExactly).count());
        assertEquals(24, rows.stream()
                .filter(row -> row.approvalStatus() == WeccModelApprovalStatus.APPROVED)
                .filter(WeccModelApproval::isImplementedExactly).count());
        var ggov3 = rows.stream().filter(row -> row.catalogName().equals("GGOV3"))
                .findFirst().orElseThrow();
        assertEquals("ggov3", ggov3.pslfModel());
        assertEquals("", ggov3.psseModel());
        assertEquals("GGOV3", ggov3.powerWorldModel());
        assertFalse(ggov3.isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("TGOV1"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("TGOV1D"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("GASTD"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("HYG3"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("H6E"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("HYGOVR"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("HYGOV4"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("IEEEG3"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("IEEEG3D"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("WESGOVD"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("DEGOV1D"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("PIDGOVD"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("TGOV3D"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("HYGOV2D"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("WPIDHYD"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("GASTWDD"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("GAST2AD"))
                .findFirst().orElseThrow().isImplementedExactly());
    }

    @Test
    void loadableDescriptorRequiresRuntimeClass() {
        assertThrows(IllegalArgumentException.class, () -> new DynamicModelDescriptor(
                "TEST", Set.of(), DynamicModelCategory.GOVERNOR,
                DynamicModelRecordSchema.exact(1),
                DynamicModelSupportStatus.LOADABLE, "", URI.create("https://example.invalid")));
    }

    @Test
    void catalogNamesReferencesAndRuntimeClassesAreValid() throws Exception {
        Set<String> names = new HashSet<>();
        for (DynamicModelDescriptor model : DynamicModelCatalog.allModels()) {
            assertTrue(names.add(model.canonicalName()),
                    "Duplicate canonical name " + model.canonicalName());
            assertTrue(model.reference().isAbsolute(),
                    "Non-absolute reference for " + model.canonicalName());
            assertEquals("https", model.reference().getScheme(),
                    "Non-HTTPS reference for " + model.canonicalName());
            assertTrue(model.parameterCount() > 0,
                    "Missing parameter schema length for " + model.canonicalName());
            assertTrue(model.recordSchema().accepts(model.parameterCount()),
                    "Primary layout is not accepted for " + model.canonicalName());
            if (model.supportStatus() == DynamicModelSupportStatus.LOADABLE) {
                assertEquals(model.runtimeClassName(),
                        Class.forName(model.runtimeClassName()).getName(),
                        "Runtime class is not loadable for " + model.canonicalName());
            }
            for (String name : model.allNames()) {
                assertTrue(names.add(name) || name.equals(model.canonicalName()),
                        "Duplicate model name or alias " + name);
                assertSame(model, DynamicModelCatalog.find(name).orElseThrow(),
                        "Catalog lookup mismatch for " + name);
            }
        }
    }

    @Test
    void versionedRecordSchemasAcceptOnlyReviewedLayouts() {
        var ieeet1 = DynamicModelCatalog.find("IEEET1").orElseThrow().recordSchema();
        assertEquals(Set.of(14, 15), ieeet1.acceptedParameterCounts());
        assertTrue(ieeet1.accepts(14));
        assertTrue(ieeet1.accepts(15));
        assertFalse(ieeet1.accepts(16));

        var repca1 = DynamicModelCatalog.find("REPCA1").orElseThrow().recordSchema();
        assertEquals(Set.of(34, 35), repca1.acceptedParameterCounts());
        assertFalse(repca1.accepts(36));
        assertEquals("34 or 35", repca1.expectedCountsDescription());
    }

    @Test
    void markdownSupportMatrixContainsEveryCatalogModel() {
        String markdown = DynamicModelSupportMatrix.generateMarkdown();
        assertTrue(markdown.startsWith("# InterPSS PSS/E dynamic-model support matrix"));
        assertEquals(DynamicModelCatalog.allModels().size(), markdown.lines()
                .filter(line -> line.startsWith("| ") && !line.startsWith("| Model"))
                .count());
        for (DynamicModelDescriptor model : DynamicModelCatalog.allModels()) {
            assertTrue(markdown.contains("| " + model.canonicalName() + " |"),
                    "Missing support-matrix row for " + model.canonicalName());
        }
    }

    @Test
    void checkedInSupportMatrixMatchesCatalog() throws Exception {
        Path matrix = Path.of("docs", "dynamic-model-support-matrix.md");
        if (!Files.isRegularFile(matrix)) {
            matrix = Path.of("..", "docs", "dynamic-model-support-matrix.md");
        }
        assertTrue(Files.isRegularFile(matrix), "Missing generated support matrix");
        assertEquals(DynamicModelSupportMatrix.generateMarkdown(), Files.readString(matrix));
    }
}
