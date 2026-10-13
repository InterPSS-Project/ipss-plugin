package org.interpss.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import com.interpss.core.algo.impl.LfNrProfiler;
import com.interpss.core.sparse.solver.klu.KLUSolverProfiler;

class CoreProfilingFixtureTest {
    @Test
    void nestedProfilingFixturesRestoreTheirCallingThread() {
        try (var kluOff = KLUSolverProfiler.withEnabled(false);
             var nrOff = LfNrProfiler.withEnabled(false)) {
            assertFalse(KLUSolverProfiler.isEnabled());
            assertFalse(LfNrProfiler.isEnabled());
            try (var kluOn = KLUSolverProfiler.withEnabled(true);
                 var nrOn = LfNrProfiler.withEnabled(true)) {
                assertTrue(KLUSolverProfiler.isEnabled());
                assertTrue(LfNrProfiler.isEnabled());
            }
            assertFalse(KLUSolverProfiler.isEnabled());
            assertFalse(LfNrProfiler.isEnabled());
        }
    }

    @Test
    void concurrentProfilingFixtureDoesNotEnableThisThread() {
        try (var kluOff = KLUSolverProfiler.withEnabled(false);
             var nrOff = LfNrProfiler.withEnabled(false)) {
            CompletableFuture.runAsync(() -> {
                try (var kluOn = KLUSolverProfiler.withEnabled(true);
                     var nrOn = LfNrProfiler.withEnabled(true)) {
                    assertTrue(KLUSolverProfiler.isEnabled());
                    assertTrue(LfNrProfiler.isEnabled());
                }
            }).join();
            assertFalse(KLUSolverProfiler.isEnabled());
            assertFalse(LfNrProfiler.isEnabled());
        }
    }
}
