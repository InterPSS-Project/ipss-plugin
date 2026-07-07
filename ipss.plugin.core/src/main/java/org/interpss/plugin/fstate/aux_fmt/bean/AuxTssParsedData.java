package org.interpss.plugin.fstate.aux_fmt.bean;

import java.util.List;

/**
 * Result of parsing a PowerWorld TSS schedules AUX file.
 */
public record AuxTssParsedData(
        List<AuxTssSchedule> schedules,
        List<AuxTssScheduleSub> subscriptions) {
}
