package org.interpss.plugin.contingency.definition.json;

import java.util.List;
import java.util.Map;

public class FlowgateJson {
    public String id;
    public String constraint_type;
    public String nerc_id;
    public String tlr_level;
    public String market_state;
    public Double shadow_price;
    public String interval;
    public String gmt_interval_end;
    public String monitored_facility_name;
    public String contingent_facility_name;
    public FlowgateContingencyRefJson contingency_ref;
    public FlowgateLimitSetJson limits;
    public List<MonitoredInterfaceBranchJson> branches;
    public Map<String, String> metadata;
}
