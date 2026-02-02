package org.interpss.plugin.contingency.definition;

public class BranchContingencyRecord {
        public String name;
        public String elementType;
        public String actionType;
        public String fromBus;
        public String toBus;
        public String ckt;
        public String fromBusArea;
        public String toBusArea;
        public double baseKv;
        public double preContingencyFlowMW;
        
        public BranchContingencyRecord(String name, String elementType, String actionType, String fromBus, String toBus, String ckt) {
            this.name = name;
            this.elementType = elementType;
            this.actionType = actionType;
            this.fromBus = fromBus;
            this.toBus = toBus;
            this.ckt = ckt;
        }
        
        public BranchContingencyRecord(String name, String elementType, String actionType, String fromBus, String toBus, String ckt,
                         String fromBusArea, String toBusArea, double baseKv, double preContingencyFlowMW) {
            this(name, elementType, actionType, fromBus, toBus, ckt);
            this.fromBusArea = fromBusArea;
            this.toBusArea = toBusArea;
            this.baseKv = baseKv;
            this.preContingencyFlowMW = preContingencyFlowMW;
        }
        
        @Override
        public String toString() {
            return String.format("{\n  \"action_type\": \"%s\",\n  \"element_type\": \"%s\",\n  \"from_bus\": %s,\n  \"to_bus\": %s,\n  \"ckt\": \"%s\"\n}", 
                actionType, elementType, fromBus, toBus, ckt);
        }
    }