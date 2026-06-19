package org.interpss.plugin.contingency.definition.json;

import java.util.List;

public class NomogramJson {
    public String id;
    public String axis_a_id;
    public String axis_b_id;
    public MonitoredInterfaceJson axis_a;
    public MonitoredInterfaceJson axis_b;
    public List<NomogramConstraintJson> constraints;
}
