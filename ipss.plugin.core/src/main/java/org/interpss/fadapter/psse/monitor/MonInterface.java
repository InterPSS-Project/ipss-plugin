package org.interpss.fadapter.psse.monitor;

import java.util.ArrayList;
import java.util.List;

/**
 * A MONITOR INTERFACE block.
 *
 * <pre>
 * MONITOR INTERFACE 'interface_1' RATING 1000.0 MW
 * MONITOR BRANCH FROM BUS 524 TO BUS 523 CKT 1
 * ...
 * END
 * </pre>
 *
 * JSON:
 * <pre>
 * {
 *   "id"       : "interface_1",
 *   "ratingMW" : 1000.0,
 *   "branches" : [ { "fromBusNum": 524, "toBusNum": 523, "ckt": "1", "comment": "..." }, ... ]
 * }
 * </pre>
 */
public class MonInterface {

    private String               id;
    private double               ratingMW;
    private List<MonBranchEntry> branches = new ArrayList<>();

    public MonInterface() {}

    public MonInterface(String id, double ratingMW) {
        this.id       = id;
        this.ratingMW = ratingMW;
    }

    public String               getId()       { return id;       }
    public double               getRatingMW() { return ratingMW; }
    public List<MonBranchEntry> getBranches() { return branches; }

    public void setId(String id)                           { this.id       = id;       }
    public void setRatingMW(double ratingMW)               { this.ratingMW = ratingMW; }
    public void setBranches(List<MonBranchEntry> branches) { this.branches = branches; }

    public void addBranch(MonBranchEntry b) { branches.add(b); }
}
