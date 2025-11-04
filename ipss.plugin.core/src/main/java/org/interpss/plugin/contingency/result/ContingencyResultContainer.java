package org.interpss.plugin.contingency.result;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfAdjCtrlFunction;
import com.interpss.core.net.Bus;
import com.interpss.state.aclf.AclfNetworkState;

/**
 * Result container class for contingency analysis
 * 
 * @author InterPSS Team
 */
public class ContingencyResultContainer <TR extends ContingencyResultRec> {
        private final Map<String, TR> caResults;
        private final long totalSuccessCount;
        private final int totalCases;
        private final long executionTimeMs;
        
        public ContingencyResultContainer(Map<String, TR> caResults, long totalSuccessCount, 
                               int totalCases, long executionTimeMs) {
            this.caResults = caResults;
            this.totalSuccessCount = totalSuccessCount;
            this.totalCases = totalCases;
            this.executionTimeMs = executionTimeMs;
        }
        
        public Map<String, TR> getConvergenceResults() { return caResults; }
        public long getTotalSuccessCount() { return totalSuccessCount; }
        public int getTotalCases() { return totalCases; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public double getExecutionTimeSeconds() { return executionTimeMs / 1000.0; }
        public double getSuccessRate() { return (double) totalSuccessCount / totalCases; }
}
