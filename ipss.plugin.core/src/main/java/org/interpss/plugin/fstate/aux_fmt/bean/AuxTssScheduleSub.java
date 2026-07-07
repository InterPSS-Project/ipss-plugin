package org.interpss.plugin.fstate.aux_fmt.bean;

/**
 * Parsed PowerWorld {@code TSScheduleSub} subscription row.
 */
public record AuxTssScheduleSub(
        String objectType,
        String objectIdentifier,
        String objectField,
        String scheduleName,
        int lineNumber) {
}
