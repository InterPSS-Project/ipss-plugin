package org.interpss.plugin.contingency.definition.json;

import java.util.List;

public class DclfMonitoringConfigJson {
    public List<MonitoredBranchJson> monitored_branches;
    public List<MonitoredInterfaceJson> monitored_interfaces;
    public List<FlowgateJson> flowgates;
    public List<NomogramJson> nomograms;
    public List<MonitoringExceptionJson> monitoring_exceptions;
    public MetadataJson metadata;
}
