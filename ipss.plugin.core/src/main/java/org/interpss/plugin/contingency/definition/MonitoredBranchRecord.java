package org.interpss.plugin.contingency.definition;

/**
 * @deprecated Use {@link com.interpss.core.algo.dclf.definition.MonitoredBranchRecord}.
 */
@Deprecated
public class MonitoredBranchRecord extends com.interpss.core.algo.dclf.definition.MonitoredBranchRecord {
    public MonitoredBranchRecord(String fromBus, String toBus, String ckt) {
        super(fromBus, toBus, ckt);
    }

    public MonitoredBranchRecord(String fromBus, String toBus, String ckt, double coefficient) {
        super(fromBus, toBus, ckt, coefficient);
    }

    public MonitoredBranchRecord(String branchId, String fromBusArea, String toBusArea,
            double baseKv, double preContingencyFlowMW) {
        super(branchId, fromBusArea, toBusArea, baseKv, preContingencyFlowMW);
    }

    public MonitoredBranchRecord(String branchId, double coefficient) {
        super(branchId, coefficient);
    }

    public MonitoredBranchRecord(String fromBus, String toBus, String ckt,
            String fromBusArea, String toBusArea, double baseKv, double preContingencyFlowMW) {
        super(fromBus, toBus, ckt, fromBusArea, toBusArea, baseKv, preContingencyFlowMW);
    }
}
