package org.interpss.dstab.validation;

import java.util.List;

/** Ranked result of one cross-tool trace comparison. */
public record DynamicTraceComparison(
        String toleranceProfile,
        List<DynamicTraceMetric> metrics,
        boolean passed) {

    public DynamicTraceComparison {
        metrics = List.copyOf(metrics);
    }
}
