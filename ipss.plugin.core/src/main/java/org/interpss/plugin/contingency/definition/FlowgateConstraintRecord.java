package org.interpss.plugin.contingency.definition;

/**
 * @deprecated Use {@link com.interpss.core.algo.dclf.definition.FlowgateConstraintRecord}.
 */
@Deprecated
public class FlowgateConstraintRecord
        extends com.interpss.core.algo.dclf.definition.FlowgateConstraintRecord {
    public FlowgateConstraintRecord(String id, FlowgateLimitSet limits) {
        super(id, limits);
    }

    public static FlowgateConstraintRecord of(
            String id,
            com.interpss.core.algo.dclf.definition.FlowgateContingencyRef contingencyRef,
            FlowgateLimitSet limits) {
        FlowgateConstraintRecord record = new FlowgateConstraintRecord(id, limits);
        record.setContingencyRef(contingencyRef);
        return record;
    }
}
