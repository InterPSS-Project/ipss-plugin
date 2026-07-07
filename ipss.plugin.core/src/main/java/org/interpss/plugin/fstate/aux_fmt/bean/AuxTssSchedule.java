package org.interpss.plugin.fstate.aux_fmt.bean;

import java.util.List;

/**
 * Parsed PowerWorld {@code TSSchedule} with its {@code SchedPoint} subdata.
 */
public record AuxTssSchedule(
        String scheduleName,
        String valueType,
        List<AuxSchedPoint> points,
        int lineNumber) {

    public boolean isNumeric() {
        return "numeric".equalsIgnoreCase(valueType);
    }

    public boolean isBoolean() {
        return "yes/no".equalsIgnoreCase(valueType);
    }
}
