package org.interpss.plugin.fstate.aux_fmt.bean;

import java.time.LocalDateTime;

/**
 * One {@code SchedPoint} row from a PowerWorld {@code TSSchedule} SUBDATA block.
 */
public record AuxSchedPoint(
        LocalDateTime time,
        int pointType,
        double nValue,
        String bValue) {
}
