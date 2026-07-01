package org.interpss.plugin.contingency.definition.json;

import java.util.List;

public class MonitoredInterfaceJson {
    public String id;
    public Double limit_mw;
    public Double rating_mw;
    public List<MonitoredInterfaceBranchJson> branches;
}
