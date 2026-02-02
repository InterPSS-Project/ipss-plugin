package org.interpss.plugin.contingency.definition;

public class MonitoredBranchRecord {
        public String fromBus;
        public String toBus;
        public String ckt;
        public String fromBusArea;
        public String toBusArea;
        public double baseKv;
        public double preContingencyFlowMW;
        public String branchId;
        
        public MonitoredBranchRecord(String fromBus, String toBus, String ckt) {
            this.fromBus = fromBus;
            this.toBus = toBus;
            this.ckt = ckt;
            this.branchId = fromBus + "->" + toBus + "(" + ckt + ")";
        }
        public MonitoredBranchRecord(String branchId,
                             String fromBusArea, String toBusArea,double baseKv, double preContingencyFlowMW) {
            this.branchId = branchId;
            //extract fromBus, toBus, ckt from branchId
            int idx1 = branchId.indexOf("->");
            int idx2 = branchId.indexOf("(");
            int idx3 = branchId.indexOf(")");
            this.fromBus = branchId.substring(0, idx1);
            this.toBus = branchId.substring(idx1 + 2, idx2); //  add 2 for "->"
            this.ckt = branchId.substring(idx2 + 1, idx3);  // add 1 for "("
            this.fromBusArea = fromBusArea;
            this.toBusArea = toBusArea;
            this.baseKv = baseKv;
            this.preContingencyFlowMW = preContingencyFlowMW;
        }
        
        public MonitoredBranchRecord(String fromBus, String toBus, String ckt,
                             String fromBusArea, String toBusArea, double baseKv, double preContingencyFlowMW) {
            this(fromBus, toBus, ckt);
            this.fromBusArea = fromBusArea;
            this.toBusArea = toBusArea;
            this.baseKv = baseKv;
            this.preContingencyFlowMW = preContingencyFlowMW;
        }
        
        public String getBranchId() {
            return branchId;
        }

        @Override
        public String toString() {
            // update this method
            return "BranchId: " + branchId + ", FromBusArea: " + fromBusArea + ", ToBusArea: " + toBusArea +
                   ", BaseKv: " + baseKv + ", PreContingencyFlowMW: " + preContingencyFlowMW;
        }
    }
