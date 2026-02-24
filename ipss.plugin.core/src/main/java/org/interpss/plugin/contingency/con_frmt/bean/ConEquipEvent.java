package org.interpss.plugin.contingency.con_frmt.bean;

/**
 * An equipment-level event in a PSSE-FORMAT contingency case.
 *
 * <p>Covers the {@code REMOVE} / {@code ADD} family of event records:
 * <pre>
 *   REMOVE MACHINE|UNIT id FROM BUS n
 *   REMOVE LOAD        id FROM BUS n
 *   REMOVE SHUNT       id FROM BUS n
 *   REMOVE SWSHUNT    [id] FROM BUS n
 *   ADD    MACHINE|UNIT id TO BUS n
 *   ADD    LOAD         id TO BUS n
 *   ADD    SHUNT        id TO BUS n
 *   ADD    SWSHUNT     [id] TO BUS n
 * </pre>
 *
 * <p>The keyword {@code UNIT} is treated as a synonym for {@code MACHINE} and
 * always stored with {@link ConEquipType#MACHINE}.
 */
public class ConEquipEvent {

    /** Action: REMOVE, ADD, or BLOCK. */
    private ConEquipAction action;

    /** Type of equipment being acted upon. */
    private ConEquipType equipType;

    /**
     * Equipment identifier as it appears in the PSSE-FORMAT data (e.g. {@code "1"}, {@code "G1"}).
     * May be {@code null} for {@code SWSHUNT} records that carry no id.
     */
    private String equipId;

    /** Bus number at which the equipment is connected. */
    private int busNum;

    public ConEquipEvent() {}

    public ConEquipEvent(ConEquipAction action, ConEquipType equipType, String equipId, int busNum) {
        this.action    = action;
        this.equipType = equipType;
        this.equipId   = equipId;
        this.busNum    = busNum;
    }

    // ---- accessors ----

    public ConEquipAction getAction()                  { return action;    }
    public ConEquipType   getEquipType()               { return equipType; }
    public String         getEquipId()                 { return equipId;   }
    public int            getBusNum()                  { return busNum;    }

    public void setAction(ConEquipAction action)       { this.action    = action;    }
    public void setEquipType(ConEquipType equipType)   { this.equipType = equipType; }
    public void setEquipId(String equipId)             { this.equipId   = equipId;   }
    public void setBusNum(int busNum)                  { this.busNum    = busNum;    }

    @Override
    public String toString() {
        return String.format("ConEquipEvent{action=%s, equipType=%s, equipId='%s', busNum=%d}",
                action, equipType, equipId, busNum);
    }
}
