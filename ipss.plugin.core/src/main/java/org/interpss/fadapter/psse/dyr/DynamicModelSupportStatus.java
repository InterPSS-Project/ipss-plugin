package org.interpss.fadapter.psse.dyr;

/**
 * Implementation maturity tracked independently from a model's WECC approval
 * status.
 */
public enum DynamicModelSupportStatus {
    /** The direct parser can construct and attach the intended runtime model. */
    LOADABLE,
    /** Some reusable code exists, but the source record cannot be loaded faithfully. */
    PARTIAL,
    /** No InterPSS implementation exists yet. */
    UNSUPPORTED
}
