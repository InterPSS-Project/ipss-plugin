package org.interpss.plugin.fstate.aux_fmt.bean;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Horizon metadata from a PowerWorld TSS timepoints CSV file.
 */
public record AuxTimepointsHorizon(
        int count,
        LocalDateTime start,
        int intervalMin,
        List<LocalDateTime> times) {
}
