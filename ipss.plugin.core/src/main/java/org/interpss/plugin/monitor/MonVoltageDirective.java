package org.interpss.plugin.monitor;

/**
 * A voltage-range monitoring directive.
 *
 * Covers two patterns:
 * <pre>
 *   monitor voltage range system xxx 0.95 1.05
 *   monitor voltage range bus 524 0.99 1.03
 * </pre>
 *
 * JSON (system-level):
 * <pre>
 * { "scope": "SYSTEM", "system": "xxx", "vMin": 0.95, "vMax": 1.05 }
 * </pre>
 * JSON (bus-level):
 * <pre>
 * { "scope": "BUS", "busNum": 524, "vMin": 0.99, "vMax": 1.03 }
 * </pre>
 *
 * Null fields ({@code system} for BUS scope, {@code busNum} for SYSTEM scope) are omitted
 * from JSON output by Gson's default behaviour.
 */
public class MonVoltageDirective {

    /**
     * {@code "SYSTEM"} when the directive targets a subsystem by name,
     * {@code "BUS"} when it targets a single bus by number.
     */
    private String  scope;
    /** Subsystem label; non-null only when scope is SYSTEM. */
    private String  system;
    /** Bus number; non-null only when scope is BUS. */
    private Integer busNum;
    private double  vMin;
    private double  vMax;

    public MonVoltageDirective() {}

    public static MonVoltageDirective forSystem(String system, double vMin, double vMax) {
        MonVoltageDirective d = new MonVoltageDirective();
        d.scope  = "SYSTEM";
        d.system = system;
        d.vMin   = vMin;
        d.vMax   = vMax;
        return d;
    }

    public static MonVoltageDirective forBus(int busNum, double vMin, double vMax) {
        MonVoltageDirective d = new MonVoltageDirective();
        d.scope  = "BUS";
        d.busNum = busNum;
        d.vMin   = vMin;
        d.vMax   = vMax;
        return d;
    }

    public String  getScope()  { return scope;  }
    public String  getSystem() { return system; }
    public Integer getBusNum() { return busNum; }
    public double  getVMin()   { return vMin;   }
    public double  getVMax()   { return vMax;   }

    public void setScope(String scope)    { this.scope  = scope;  }
    public void setSystem(String system)  { this.system = system; }
    public void setBusNum(Integer busNum) { this.busNum = busNum; }
    public void setVMin(double vMin)      { this.vMin   = vMin;   }
    public void setVMax(double vMax)      { this.vMax   = vMax;   }
}
