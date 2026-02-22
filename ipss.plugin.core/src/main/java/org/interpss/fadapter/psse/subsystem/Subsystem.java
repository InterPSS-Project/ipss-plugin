package org.interpss.fadapter.psse.subsystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a SYSTEM or SUBSYSTEM block in a PSS/E subsystem definition file.
 */
public class Subsystem {

    private String label;
    private String type;
    private List<JoinGroup>    joinGroups       = new ArrayList<>();
    private SelectionGroup     directSelection  = new SelectionGroup();
    private List<Integer>      skipBuses        = new ArrayList<>();

    public Subsystem() {}

    public Subsystem(String label, String type) {
        this.label = label;
        this.type  = type;
    }

    public void setLabel(String label)                    { this.label = label; }
    public void setType(String type)                      { this.type = type; }
    public void setJoinGroups(List<JoinGroup> joinGroups) { this.joinGroups = joinGroups; }
    public void setDirectSelection(SelectionGroup ds)     { this.directSelection = ds; }
    public void setSkipBuses(List<Integer> skipBuses)     { this.skipBuses = skipBuses; }

    public String getLabel()                  { return label; }
    public String getType()                   { return type; }
    public List<JoinGroup> getJoinGroups()    { return joinGroups; }
    public SelectionGroup getDirectSelection(){ return directSelection; }
    public List<Integer> getSkipBuses()       { return skipBuses; }

    public void addJoinGroup(JoinGroup g) { joinGroups.add(g); }
    public void addSkipBus(int busNum)    { skipBuses.add(busNum); }
}
