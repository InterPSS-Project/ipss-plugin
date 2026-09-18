package org.interpss.fadapter.psse.dyr;

/** Final disposition of one source DYR record. */
public enum DynamicModelImportStatus {
    ATTACHED,
    /** Source model is deliberately inapplicable because preprocessing disabled its device. */
    SKIPPED_GNET,
    /** Source model is deliberately removed by a BAT_PLMOD_REMOVE type-1 command. */
    SKIPPED_MODEL_REMOVE,
    UNSUPPORTED,
    REJECTED,
    MISSING_TARGET,
    FALLBACK,
    FAILED_INITIALIZATION,
    ERROR
}
