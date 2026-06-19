package org.interpss.plugin.contingency.result;

/**
 * @deprecated Use {@link com.interpss.core.algo.dclf.result.FlowgateViolationResult}.
 */
@Deprecated
public class FlowgateViolationResult
        extends com.interpss.core.algo.dclf.result.FlowgateViolationResult {
    public FlowgateViolationResult(
            com.interpss.core.algo.dclf.definition.FlowgateConstraintRecord flowgate,
            String contingencyId,
            double preValueMW,
            double shiftedValueMW,
            double postValueMW,
            double limitMW,
            com.interpss.core.algo.dclf.definition.FlowgateLimitSelection limitSelection) {
        super(flowgate, contingencyId, preValueMW, shiftedValueMW, postValueMW, limitMW, limitSelection);
    }
}
