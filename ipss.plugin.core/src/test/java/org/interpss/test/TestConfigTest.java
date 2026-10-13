package org.interpss.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TestConfigTest {
    private static final String RESOURCE = "/test-config/reader-test.json";

    @Test
    void readsTypedJsonSettingsAndOptionalDefaults() {
        TestConfig config = TestConfig.load(RESOURCE, "valid");
        assertEquals("fixture", config.string("name"));
        assertTrue(config.bool("enabled"));
        assertEquals(24, config.integer("steps", 0));
        assertEquals(4_000_000_000L, config.longValue("count", 0));
        assertEquals("fallback", config.string("optional", "fallback"));
        assertNull(config.string("missing"));
        assertFalse(config.bool("missing"));
    }

    @Test
    void fixtureOverridesDoNotMutateTheOriginalOrOtherFixtures() {
        TestConfig base = TestConfig.load(RESOURCE, "valid");
        TestConfig full = base.withOverrides(Map.of("steps", 48, "enabled", false));
        TestConfig shortRun = base.withOverrides(Map.of("steps", 6));
        assertEquals(24, base.integer("steps", 0));
        assertEquals(48, full.integer("steps", 0));
        assertFalse(full.bool("enabled"));
        assertEquals(6, shortRun.integer("steps", 0));
        assertTrue(shortRun.bool("enabled"));
    }

    @Test
    void failsClearlyForMissingResourcesAndInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> TestConfig.load("/missing.json", "valid"));
        assertThrows(IllegalArgumentException.class, () -> TestConfig.load(RESOURCE, "missing"));
        TestConfig config = TestConfig.load(RESOURCE, "invalid");
        assertThrows(IllegalArgumentException.class, () -> config.bool("enabled"));
        assertThrows(IllegalArgumentException.class, () -> config.integer("steps", 24));
        assertThrows(IllegalArgumentException.class, () -> config.longValue("count", 0));
        assertThrows(IllegalArgumentException.class, () -> config.string("object"));
    }
}
