package org.interpss.fadapter.psse.monitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level container for a parsed PSS/E Monitored Element (.mon) file.
 *
 * JSON structure:
 * <pre>
 * {
 *   "sourceFile"                    : "/path/to/file.mon",
 *   "monitoredFlowDirectives"       : [ { "type":"BRANCHES", "system":"Internal" }, ... ],
 *   "monitoredBusVoltageDirectives" : [ { "scope":"SYSTEM", "system":"Internal", "vMin":0.95, "vMax":1.05 }, ... ],
 *   "interfaces"                    : [ { "id":"Interface_1", "ratingMW":1000.0, "branches":[...] }, ... ]
 * }
 * </pre>
 */
public class MonElementContainer {

    private String                    sourceFile;
    private List<MonFlowDirective>    monitoredFlowDirectives       = new ArrayList<>();
    private List<MonVoltageDirective> monitoredBusVoltageDirectives = new ArrayList<>();
    private List<MonInterface>        interfaces                    = new ArrayList<>();

    public MonElementContainer(
            String sourceFile,
            List<MonFlowDirective>    monitoredFlowDirectives,
            List<MonVoltageDirective> monitoredBusVoltageDirectives,
            List<MonInterface>        interfaces) {
        this.sourceFile                    = sourceFile;
        this.monitoredFlowDirectives       = monitoredFlowDirectives       != null ? monitoredFlowDirectives       : new ArrayList<>();
        this.monitoredBusVoltageDirectives = monitoredBusVoltageDirectives != null ? monitoredBusVoltageDirectives : new ArrayList<>();
        this.interfaces                    = interfaces                    != null ? interfaces                    : new ArrayList<>();
    }

    public String                    getSourceFile()                    { return sourceFile;                    }
    public List<MonFlowDirective>    getMonitoredFlowDirectives()       { return monitoredFlowDirectives;       }
    public List<MonVoltageDirective> getMonitoredBusVoltageDirectives() { return monitoredBusVoltageDirectives; }
    public List<MonInterface>        getInterfaces()                    { return interfaces;                    }
}
