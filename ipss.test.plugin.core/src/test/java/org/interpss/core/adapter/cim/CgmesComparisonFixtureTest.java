package org.interpss.core.adapter.cim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CgmesComparisonFixtureTest {
    @Test
    void caseSpecificFloorsAreNestedAndThreadConfined() {
        String key = "ipss.cgmes.p4.minAngMatch";
        String previous = CgmesComparisonFixture.settings().string(key, "0.95");
        try (var outer = CgmesComparisonFixture.withOverrides(Map.of(key, "0.85"))) {
            assertEquals("0.85", CgmesComparisonFixture.settings().string(key));
            try (var inner = CgmesComparisonFixture.withOverrides(Map.of(key, "0.50"))) {
                assertEquals("0.50", CgmesComparisonFixture.settings().string(key));
                assertEquals(previous, CompletableFuture.supplyAsync(
                        () -> CgmesComparisonFixture.settings().string(key, "0.95")).join());
            }
            assertEquals("0.85", CgmesComparisonFixture.settings().string(key));
        }
        assertEquals(previous, CgmesComparisonFixture.settings().string(key, "0.95"));
    }
}
