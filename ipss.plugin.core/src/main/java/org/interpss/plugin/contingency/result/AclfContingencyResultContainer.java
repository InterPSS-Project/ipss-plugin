package org.interpss.plugin.contingency.result;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ordered batch results and diagnostics for AC contingencies.
 *
 * <p>The requested case count includes cases for which no result record is
 * available. Diagnostics describe cancellations, unavailable snapshots,
 * invalid outage actions, and solve failures.
 *
 * @param <TR> record type stored for each completed case
 * @author InterPSS Team
 */
public class AclfContingencyResultContainer <TR extends AclfContingencyResultRec> {
        private final Map<String, TR> caResults;
        private final long totalSuccessCount;
        private final int totalCases;
        private final long executionTimeMs;
        private final List<AclfContingencyDiagnostic> diagnostics;
        
        public AclfContingencyResultContainer(Map<String, TR> caResults, long totalSuccessCount, 
                               int totalCases, long executionTimeMs) {
            this(caResults, totalSuccessCount, totalCases, executionTimeMs, List.of());
        }

        public AclfContingencyResultContainer(Map<String, TR> caResults, long totalSuccessCount,
                               int totalCases, long executionTimeMs,
                               List<AclfContingencyDiagnostic> diagnostics) {
            this.caResults = new LinkedHashMap<>(caResults);
            this.totalSuccessCount = totalSuccessCount;
            this.totalCases = totalCases;
            this.executionTimeMs = executionTimeMs;
            this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
        
        /**
         * Returns the available records, keyed by case ID in study order.
         * Treat the map as read-only to keep its contents consistent with the
         * recorded batch counts.
         *
         * @return ordered map of case IDs to results
         */
        public Map<String, TR> getCAResults() { return caResults; }

        /** Returns the count of cases whose solve recorded convergence. */
        public long getTotalSuccessCount() { return totalSuccessCount; }

        /** Returns the count eligible for reporting under the default mismatch screen. */
        public long getReportableCaseCount() {
            return caResults.values().stream().filter(AclfContingencyResultRec::isReportable).count();
        }
        public int getTotalCases() { return totalCases; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public double getExecutionTimeSeconds() { return executionTimeMs / 1000.0; }
        public double getSuccessRate() { return totalCases == 0 ? 0.0 : (double) totalSuccessCount / totalCases; }
        public List<AclfContingencyDiagnostic> getDiagnostics() { return diagnostics; }
}
