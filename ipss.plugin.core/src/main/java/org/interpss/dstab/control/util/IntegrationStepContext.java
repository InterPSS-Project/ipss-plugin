package org.interpss.dstab.control.util;

/**
 * Physical clock of the next integration interval. Preparing this context must
 * not sample measurements, integrate states or commit discrete history. The
 * solver prepares active bus-device owners once before each step, outside network/RHS
 * iteration; the model consumes the clock at its accepted history boundary.
 * Low-level callers must supply this context when using negative startup time.
 */
public interface IntegrationStepContext {
    void prepareIntegrationStep(double startTimeSec, double stepSec);
}
