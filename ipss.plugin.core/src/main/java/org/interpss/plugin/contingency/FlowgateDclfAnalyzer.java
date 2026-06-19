package org.interpss.plugin.contingency;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.interpss.core.aclf.AclfNetwork;

/**
 * @deprecated Use {@link com.interpss.core.algo.dclf.FlowgateDclfAnalyzer}.
 */
@Deprecated
public final class FlowgateDclfAnalyzer {
    private FlowgateDclfAnalyzer() {
    }

    public static ConcurrentLinkedQueue<com.interpss.core.algo.dclf.result.FlowgateViolationResult> executeFlowgateAnalysis(
            AclfNetwork aclfNet,
            List<com.interpss.core.algo.dclf.definition.FlowgateConstraintRecord> flowgates,
            DclfContingencyConfig config,
            int parallelismLevel) {
        return com.interpss.core.algo.dclf.FlowgateDclfAnalyzer.executeFlowgateAnalysis(
                aclfNet,
                flowgates,
                config,
                parallelismLevel);
    }
}
