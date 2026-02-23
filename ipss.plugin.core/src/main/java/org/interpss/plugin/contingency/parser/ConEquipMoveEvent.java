package org.interpss.plugin.contingency.parser;

/**
 * A bus-to-bus load (or generation / shunt) transfer event in a PSS/E contingency case.
 *
 * <p>Covers:
 * <pre>
 *   MOVE r [MW|PERCENT] [GENERATION|LOAD|SHUNT] FROM BUS i TO BUS j
 *   MOVE r [MW|PERCENT] ACTIVE   LOAD FROM BUS i TO BUS j
 *   MOVE r [MW|PERCENT] REACTIVE LOAD FROM BUS i TO BUS j
 * </pre>
 *
 * <p>The {@link ConEquipType} is derived automatically from {@code loadType}.
 */
public class ConEquipMoveEvent {

    /**
     * Equipment type being moved, derived from the {@code loadType} token:
     * {@link ConEquipType#LOAD}, {@link ConEquipType#ACTIVE_LOAD},
     * {@link ConEquipType#REACTIVE_LOAD}, {@link ConEquipType#GENERATION},
     * or {@link ConEquipType#SHUNT}.
     */
    private final ConEquipType equipType;

    /** Numeric magnitude. */
    private double value;

    /**
     * Unit: {@code "MW"}, {@code "MVAR"}, or {@code "PERCENT"}.
     */
    private String unit;

    /**
     * What is being moved: {@code "LOAD"}, {@code "GENERATION"}, {@code "SHUNT"}.
     * May also be {@code "ACTIVE LOAD"} or {@code "REACTIVE LOAD"}.
     */
    private String loadType;

    /** Source bus number. */
    private int fromBusNum;

    /** Destination bus number. */
    private int toBusNum;

    /** Derives the {@link ConEquipType} from a PSS/E load-type token. */
    private static ConEquipType equipTypeOf(String loadType) {
        if (loadType != null) {
            String u = loadType.toUpperCase();
            if (u.contains("GENERATION")) return ConEquipType.GENERATION;
            if (u.contains("SHUNT"))      return ConEquipType.SHUNT;
            if (u.contains("REACTIVE"))   return ConEquipType.REACTIVE_LOAD;
            if (u.contains("ACTIVE"))     return ConEquipType.ACTIVE_LOAD;
        }
        return ConEquipType.LOAD;
    }

    public ConEquipMoveEvent() { this.equipType = ConEquipType.LOAD; }

    public ConEquipMoveEvent(double value, String unit, String loadType,
                            int fromBusNum, int toBusNum) {
        this.equipType  = equipTypeOf(loadType);
        this.value      = value;
        this.unit       = unit;
        this.loadType   = loadType;
        this.fromBusNum = fromBusNum;
        this.toBusNum   = toBusNum;
    }

    // ---- accessors ----

    public ConEquipType getEquipType()         { return equipType;  }
    public double       getValue()             { return value;      }
    public String       getUnit()              { return unit;       }
    public String       getLoadType()          { return loadType;   }
    public int          getFromBusNum()        { return fromBusNum; }
    public int          getToBusNum()          { return toBusNum;   }

    public void setValue(double value)         { this.value     = value;     }
    public void setUnit(String unit)           { this.unit      = unit;      }
    public void setLoadType(String loadType)   { this.loadType  = loadType;  }
    public void setFromBusNum(int fromBusNum)  { this.fromBusNum = fromBusNum; }
    public void setToBusNum(int toBusNum)      { this.toBusNum   = toBusNum;   }

    @Override
    public String toString() {
        return String.format("ConEquipMoveEvent{MOVE %.4f %s %s FROM BUS %d TO BUS %d}",
                value, unit, loadType, fromBusNum, toBusNum);
    }
}
