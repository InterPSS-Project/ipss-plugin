package org.interpss.plugin.fstate.aux_fmt.bean;

import java.time.LocalDateTime;

/**
 * One scheduled outage row from a PowerWorld PWCSV outages file.
 */
public record AuxOutageRecord(
        String branchLabel,
        LocalDateTime startTime,
        LocalDateTime endTime) {
}
