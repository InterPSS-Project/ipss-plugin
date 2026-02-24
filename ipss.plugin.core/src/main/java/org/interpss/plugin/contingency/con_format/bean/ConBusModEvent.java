package org.interpss.plugin.contingency.con_format.bean;

/**
 * A bus-level quantity modification event in a PSS/E contingency case.
 *
 * <p>Covers:
 * <pre>
 *   SET      BUS n [GENERATION|LOAD|SHUNT] TO r [MW|PERCENT] [DISPATCH]
 *   CHANGE   BUS n [GENERATION|LOAD|SHUNT] BY r [MW|PERCENT] [DISPATCH]
 *   INCREASE BUS n [GENERATION|LOAD|SHUNT] BY r [MW|PERCENT] [DISPATCH]
 *   DECREASE BUS n [GENERATION|LOAD|SHUNT] BY r [MW|PERCENT] [DISPATCH]
 * </pre>
 *
 * <p>A missing attribute defaults to {@code null} (meaning all attached devices).
 */
public class ConBusModEvent {

    /** Modification verb (SET, CHANGE, INCREASE, DECREASE). */
    private ConBusModAction action;

    /** Bus number being modified. */
    private int busNum;

    /**
     * The PSS/E attribute being modified: {@code "GENERATION"}, {@code "LOAD"},
     * {@code "SHUNT"}, or {@code null} if not specified.
     */
    private String attribute;

    /** Numeric magnitude (absolute MW or percentage). */
    private double value;

    /**
     * Unit of the value: {@code "MW"}, {@code "MVAR"}, or {@code "PERCENT"}.
     */
    private String unit;

    public ConBusModEvent() {}

    public ConBusModEvent(ConBusModAction action, int busNum, String attribute,
                          double value, String unit) {
        this.action    = action;
        this.busNum    = busNum;
        this.attribute = attribute;
        this.value     = value;
        this.unit      = unit;
    }

    // ---- accessors ----

    public ConBusModAction getAction()         { return action;    }
    public int             getBusNum()         { return busNum;    }
    public String       getAttribute()         { return attribute; }
    public double       getValue()             { return value;     }
    public String       getUnit()              { return unit;      }

    public void setAction(ConBusModAction action)  { this.action    = action;    }
    public void setBusNum(int busNum)              { this.busNum    = busNum;    }
    public void setAttribute(String attribute)     { this.attribute = attribute; }
    public void setValue(double value)             { this.value     = value;     }
    public void setUnit(String unit)               { this.unit      = unit;      }

    @Override
    public String toString() {
        return String.format("ConBusModEvent{action=%s, busNum=%d, attr=%s, value=%s %s}",
                action, busNum, attribute, value, unit);
    }
}
