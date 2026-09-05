package org.interpss.fadapter.psse.dyr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DynamicModelCatalogTest {
    @Test
    void catalogsEveryTexas2kModelAndResolvesAliases() {
        assertEquals(22, DynamicModelCatalog.texas2kModels().size());
        assertEquals("GENROU", DynamicModelCatalog.canonicalName("genroe"));
        assertEquals("REGCA1", DynamicModelCatalog.canonicalName("regcau1"));
        assertEquals("REPCA1", DynamicModelCatalog.canonicalName("repcta1"));
        assertEquals(51, DynamicModelCatalog.find("REECA1").orElseThrow().parameterCount());
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
        assertEquals(4, implemented);
        assertTrue(DynamicModelCatalog.find("WSCCST").orElseThrow().supportStatus()
                == DynamicModelSupportStatus.LOADABLE);
        assertEquals(DynamicModelSupportStatus.LOADABLE,
                DynamicModelCatalog.find("PSS1A").orElseThrow().supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.stabilizers().stream()
                .filter(row -> row.catalogName().equals("PSS1A"))
                .findFirst().orElseThrow().isImplementedExactly());
    }

    @Test
    void catalogsEveryMay2026ExciterRowAndExposesSupportGaps() {
        var rows = WeccApprovedDynamicModelCatalog.exciters();
        assertEquals(71, rows.size());
        assertEquals(65, rows.stream()
                .filter(row -> row.approvalStatus() == WeccModelApprovalStatus.APPROVED).count());
        assertEquals(6, rows.stream().filter(WeccModelApproval::isImplementedExactly).count());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esst3a")
                .orElseThrow().isImplementedExactly());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("esdc2a")
                .orElseThrow().isImplementedExactly());
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
        assertEquals(10, rows.stream().filter(WeccModelApproval::isImplementedExactly).count());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("TGOV1"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("TGOV1D"))
                .findFirst().orElseThrow().isImplementedExactly());
        assertTrue(rows.stream().filter(row -> row.catalogName().equals("GASTD"))
                .findFirst().orElseThrow().isImplementedExactly());
    }

    @Test
    void loadableDescriptorRequiresRuntimeClass() {
        assertThrows(IllegalArgumentException.class, () -> new DynamicModelDescriptor(
                "TEST", Set.of(), DynamicModelCategory.GOVERNOR, 1,
                DynamicModelSupportStatus.LOADABLE, "", URI.create("https://example.invalid")));
    }
}
