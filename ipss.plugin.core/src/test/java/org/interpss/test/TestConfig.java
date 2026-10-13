package org.interpss.test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Immutable, classpath JSON settings for tests. Never reads JVM properties. */
public final class TestConfig {
    private final JsonObject values;

    private TestConfig(JsonObject values) {
        this.values = values.deepCopy();
    }

    public static TestConfig load(String resource, String section) {
        try (var stream = TestConfig.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalArgumentException("Missing test configuration: " + resource);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (!root.has(section) || !root.get(section).isJsonObject()) {
                    throw new IllegalArgumentException("Missing test configuration section: " + section);
                }
                return new TestConfig(root.getAsJsonObject(section));
            }
        } catch (IOException | IllegalStateException e) {
            throw new IllegalArgumentException("Invalid test configuration: " + resource, e);
        }
    }

    /** Creates a fixture without changing the loaded configuration. */
    public TestConfig withOverrides(Map<String, ?> overrides) {
        JsonObject copy = values.deepCopy();
        overrides.forEach((key, value) -> {
            if (value == null) copy.remove(key);
            else if (value instanceof Boolean b) copy.addProperty(key, b);
            else if (value instanceof Number n) copy.addProperty(key, n);
            else if (value instanceof String s) copy.addProperty(key, s);
            else throw new IllegalArgumentException("Unsupported setting: " + key);
        });
        return new TestConfig(copy);
    }

    public String string(String key) {
        return string(key, null);
    }

    public String string(String key, String fallback) {
        JsonElement value = values.get(key);
        if (value == null || value.isJsonNull()) return fallback;
        if (!value.isJsonPrimitive()) throw invalid(key);
        return value.getAsString();
    }

    public boolean bool(String key) {
        return bool(key, false);
    }

    public boolean bool(String key, boolean fallback) {
        String value = string(key);
        if (value == null) return fallback;
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw invalid(key);
    }

    public int integer(String key, int fallback) {
        String value = string(key);
        if (value == null) return fallback;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { throw invalid(key); }
    }

    public long longValue(String key, long fallback) {
        String value = string(key);
        if (value == null) return fallback;
        try { return Long.parseLong(value); }
        catch (NumberFormatException e) { throw invalid(key); }
    }

    private static IllegalArgumentException invalid(String key) {
        return new IllegalArgumentException("Invalid test setting: " + key);
    }
}
