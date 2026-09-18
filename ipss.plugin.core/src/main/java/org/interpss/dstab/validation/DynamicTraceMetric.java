package org.interpss.dstab.validation;

import java.util.Map;

/** Comparison statistics for one device signal. */
public record DynamicTraceMetric(
        DynamicTraceKey key,
        String unit,
        String base,
        int sampleCount,
        double maximumAbsoluteError,
        double maximumChangeFromInitialError,
        double normalizedRmse,
        Double firstThresholdCrossingSeconds,
        Map<Double, Double> eventAbsoluteErrors,
        boolean passed) {

    public DynamicTraceMetric {
        eventAbsoluteErrors = Map.copyOf(eventAbsoluteErrors);
    }
}
