package org.interpss.plugin.contingency.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.interpss.core.algo.dclf.check.MonitoringExceptionRecord;
import com.interpss.core.algo.dclf.check.NomogramMwBoundaryCheck;
import com.interpss.core.algo.dclf.definition.FlowgateConstraintRecord;

public class DclfMonitoringConfigRecord {
    private final List<MonitoredBranchRecord> monitoredBranches;
    private final List<MonitoredInterfaceRecord> monitoredInterfaces;
    private final List<FlowgateConstraintRecord> flowgates;
    private final List<NomogramMwBoundaryCheck.Facet> nomogramFacets;
    private final List<MonitoringExceptionRecord> monitoringExceptions;

    public DclfMonitoringConfigRecord(
            List<MonitoredBranchRecord> monitoredBranches,
            List<MonitoredInterfaceRecord> monitoredInterfaces,
            List<FlowgateConstraintRecord> flowgates,
            List<NomogramMwBoundaryCheck.Facet> nomogramFacets,
            List<MonitoringExceptionRecord> monitoringExceptions) {
        this.monitoredBranches = copy(monitoredBranches);
        this.monitoredInterfaces = copy(monitoredInterfaces);
        this.flowgates = copy(flowgates);
        this.nomogramFacets = copy(nomogramFacets);
        this.monitoringExceptions = copy(monitoringExceptions);
    }

    public List<MonitoredBranchRecord> getMonitoredBranches() {
        return monitoredBranches;
    }

    public List<MonitoredInterfaceRecord> getMonitoredInterfaces() {
        return monitoredInterfaces;
    }

    public List<FlowgateConstraintRecord> getFlowgates() {
        return flowgates;
    }

    public List<NomogramMwBoundaryCheck.Facet> getNomogramFacets() {
        return nomogramFacets;
    }

    public List<MonitoringExceptionRecord> getMonitoringExceptions() {
        return monitoringExceptions;
    }

    private static <T> List<T> copy(List<T> source) {
        return Collections.unmodifiableList(source == null ? List.of() : new ArrayList<>(source));
    }
}
