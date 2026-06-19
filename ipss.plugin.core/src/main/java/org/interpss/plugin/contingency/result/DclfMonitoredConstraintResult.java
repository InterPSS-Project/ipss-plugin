package org.interpss.plugin.contingency.result;

import com.interpss.core.contingency.BaseContingency;
import com.interpss.core.contingency.dclf.DclfMonitoringBranch;

/**
 * @deprecated Use {@link com.interpss.core.algo.dclf.result.DclfMonitoredConstraintResult}.
 */
@Deprecated
public class DclfMonitoredConstraintResult
        extends com.interpss.core.algo.dclf.result.DclfMonitoredConstraintResult {
    public DclfMonitoredConstraintResult(
            BaseContingency<DclfMonitoringBranch> contingency,
            String constraintId,
            double preValueMW,
            double shiftedValueMW,
            double limitMW) {
        super(contingency, constraintId, preValueMW, shiftedValueMW, limitMW);
    }
}
