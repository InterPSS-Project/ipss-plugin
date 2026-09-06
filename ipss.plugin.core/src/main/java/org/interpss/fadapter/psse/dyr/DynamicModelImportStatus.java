package org.interpss.fadapter.psse.dyr;

/** Final disposition of one source DYR record. */
public enum DynamicModelImportStatus {
    ATTACHED,
    /** Source model is deliberately inapplicable because preprocessing disabled its device. */
    SKIPPED_GNET,
    UNSUPPORTED,
    REJECTED,
    MISSING_TARGET,
    FALLBACK,
    FAILED_INITIALIZATION,
    ERROR
}
