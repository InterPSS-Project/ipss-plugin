package org.interpss.fadapter.psse.dyr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertFalse(DynamicModelCatalog.find("unknown-model").isPresent());
    }

    @Test
    void loadableDescriptorRequiresRuntimeClass() {
        assertThrows(IllegalArgumentException.class, () -> new DynamicModelDescriptor(
                "TEST", Set.of(), DynamicModelCategory.GOVERNOR, 1,
                DynamicModelSupportStatus.LOADABLE, "", URI.create("https://example.invalid")));
    }
}
