package org.interpss.plugin.contingency.result;

public class DclfContingencyResultRec {
        private final String branchId;
        private final String contingencyName;
        private final Double postFlowMW;
        private final Double lineRatingMW;
        private final Double loadingPercent;
        
        public DclfContingencyResultRec(String branchId, String contingencyName, Double postFlowMW, Double lineRatingMW, Double loadingPercent) {
            this.branchId = branchId;
            this.contingencyName = contingencyName;
            this.postFlowMW = postFlowMW;
            this.lineRatingMW = lineRatingMW;
            this.loadingPercent = loadingPercent;
        }
        
        public String getBranchId() { return branchId; }
        public String getContingencyName() { return contingencyName; }
        public Double getPostFlowMW() { return postFlowMW; }
        public Double getLineRatingMW() { return lineRatingMW; }
        public Double getLoadingPercent() { return loadingPercent; }

        @Override
        public String toString() {
            return String.format("{\n  \"branch_id\": \"%s\",\n  \"contingency_name\": \"%s\",\n  \"post_flow_mw\": %.2f,\n  \"line_rating_mw\": %.2f,\n  \"loading_percent\": %.2f\n}", 
                branchId, contingencyName, postFlowMW, lineRatingMW, loadingPercent);
        }
    }