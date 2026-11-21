package org.interpss.dstab.validation;

/**
 * Dynamic solver entry points that validate imported model parameters before
 * initialization. Implemented by the ordinary and staggered Euler solvers, so
 * callers that select either solver can read the same report.
 */
public interface ParameterValidatingSolver {
    /** Last explicit or initialization validation; null before the first check. */
    DynamicParameterValidator.Report getParameterValidationReport();

    /** Inspect imported data without solving, initializing states or modifying parameters. */
    DynamicParameterValidator.Report validateModelParameters();
}
