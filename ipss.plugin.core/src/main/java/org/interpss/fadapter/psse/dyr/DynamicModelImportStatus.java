package org.interpss.fadapter.psse.dyr;

/** Final disposition of one source DYR record. */
public enum DynamicModelImportStatus {
    ATTACHED,
    /** Source model is deliberately inapplicable because preprocessing disabled its device. */
    SKIPPED_GNET,
    /** Source model is deliberately removed by a BAT_PLMOD_REMOVE type-1 command. */
    SKIPPED_MODEL_REMOVE,
    /** Explicit import policy ignores an absent required physical or loaded dynamic host. */
    SKIPPED_MISSING_HOST,
    /** A subsystem default has no remaining loads after more-specific model assignment. */
    SKIPPED_LOAD_PRIORITY,
    /** A validated protection record has an explicitly out-of-service physical load target. */
    SKIPPED_INACTIVE_TARGET,
    UNSUPPORTED,
    REJECTED,
    MISSING_TARGET,
    FALLBACK,
    FAILED_INITIALIZATION,
    ERROR
}
