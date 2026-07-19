package org.interpss.plugin.contingency.result;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result container class for contingency analysis
 * 
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
        
        public Map<String, TR> getCAResults() { return caResults; }
        public long getTotalSuccessCount() { return totalSuccessCount; }
        public int getTotalCases() { return totalCases; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public double getExecutionTimeSeconds() { return executionTimeMs / 1000.0; }
        public double getSuccessRate() { return totalCases == 0 ? 0.0 : (double) totalSuccessCount / totalCases; }
        public List<AclfContingencyDiagnostic> getDiagnostics() { return diagnostics; }
}
