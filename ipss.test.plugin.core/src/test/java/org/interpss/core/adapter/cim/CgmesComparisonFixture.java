package org.interpss.core.adapter.cim;

import java.util.Map;
import org.interpss.test.TestConfig;

/** Thread-confined comparison floors for the existing static CGMES helpers. */
final class CgmesComparisonFixture implements AutoCloseable {
    private static final TestConfig BASE = TestConfig.load(
            "/test-config/ipss.test.plugin.core.json", "CgmesComparisonFixture");
    private static final ThreadLocal<TestConfig> CURRENT = new ThreadLocal<>();
    private final Thread owner = Thread.currentThread();
    private final TestConfig previous = CURRENT.get();
    private boolean closed;

    static TestConfig settings() {
        TestConfig config = CURRENT.get();
        return config == null ? BASE : config;
    }

    static CgmesComparisonFixture withOverrides(Map<String, ?> values) {
        return new CgmesComparisonFixture(values);
    }

    private CgmesComparisonFixture(Map<String, ?> values) {
        CURRENT.set(settings().withOverrides(values));
    }

    @Override
    public void close() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Comparison fixture must close on its owning thread");
        }
        if (closed) return;
        closed = true;
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
