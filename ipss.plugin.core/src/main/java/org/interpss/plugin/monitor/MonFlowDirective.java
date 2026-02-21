package org.interpss.plugin.monitor;

/**
 * One of the system-level monitoring directives that reference a subsystem by name.
 *
 * Covers three line patterns:
 * <pre>
 *   monitor branches in system xxx
 *   monitor breakers in system xxx
 *   monitor ties    from system yyy
 * </pre>
 *
 * JSON:
 * <pre>
 * { "type": "BRANCHES", "system": "xxx" }
 * { "type": "BREAKERS", "system": "xxx" }
 * { "type": "TIES",     "system": "yyy" }
 * </pre>
 */
public class MonFlowDirective {

    /**
     * What is being monitored: {@code BRANCHES}, {@code BREAKERS}, or {@code TIES}.
     */
    private String type;
    /** The subsystem label (matches a name in the .sub / JSON file). */
    private String system;

    public MonFlowDirective() {}

    public MonFlowDirective(String type, String system) {
        this.type   = type;
        this.system = system;
    }

    public String getType()   { return type;   }
    public String getSystem() { return system; }

    public void setType(String type)     { this.type   = type;   }
    public void setSystem(String system) { this.system = system; }
}
