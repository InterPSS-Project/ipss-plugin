package org.interpss.plugin.contingency.definition;

import java.util.LinkedHashSet;
import java.util.Set;

public class MonitoredInterfaceRecord {
    private String id;
    private double ratingMW;
    private Set<MonitoredBranchRecord> branches = new LinkedHashSet<>();

    //constructor
    public MonitoredInterfaceRecord(String id, double ratingMW) {
        this.id = id;
        this.ratingMW = ratingMW;
    }
    public String getId() {
        return id;
    }
    public double getRatingMW() {
        return ratingMW;
    }
    public Set<MonitoredBranchRecord> getBranches() {
        return branches;
    }
    public void addBranch(MonitoredBranchRecord branch) {
        this.branches.add(branch);
    }
    

}
