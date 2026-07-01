package org.interpss.plugin.contingency;

import com.interpss.core.aclf.AclfNetwork;

/**
 * @deprecated Use {@link com.interpss.core.algo.dclf.solver.ParallelDclfContingencyAnalyzer}.
 */
@Deprecated
public class ParallelDclfContingencyAnalyzer
        extends com.interpss.core.algo.dclf.solver.ParallelDclfContingencyAnalyzer {
    public ParallelDclfContingencyAnalyzer(AclfNetwork net) {
        super(net);
    }
}
