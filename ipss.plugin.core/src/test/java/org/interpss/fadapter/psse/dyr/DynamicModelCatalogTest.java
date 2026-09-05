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
        assertEquals(17, DynamicModelCatalog.texas2kModels().size());
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
    void loadableDescriptorRequiresRuntimeClass() {
        assertThrows(IllegalArgumentException.class, () -> new DynamicModelDescriptor(
                "TEST", Set.of(), DynamicModelCategory.GOVERNOR, 1,
                DynamicModelSupportStatus.LOADABLE, "", URI.create("https://example.invalid")));
    }
}
