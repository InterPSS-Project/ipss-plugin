package org.interpss.plugin.contingency.result;

/** Structured diagnostic emitted by an AC contingency batch. */
public record AclfContingencyDiagnostic(
        String branchId,
        String severity,
        String code,
        String message) {
}
