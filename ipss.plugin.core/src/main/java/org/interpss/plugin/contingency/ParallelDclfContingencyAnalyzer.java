package org.interpss.plugin.contingency;

import com.interpss.core.aclf.BaseAclfNetwork;

/**
 * @deprecated Use {@link com.interpss.core.algo.dclf.solver.ParallelDclfContingencyAnalyzer}.
 */
@Deprecated
public class ParallelDclfContingencyAnalyzer
        extends com.interpss.core.algo.dclf.solver.ParallelDclfContingencyAnalyzer {
    public ParallelDclfContingencyAnalyzer(BaseAclfNetwork<?, ?> net) {
        super(net);
    }
}
