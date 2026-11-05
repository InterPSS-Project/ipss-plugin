package org.interpss.dstab.algo;

import com.interpss.dstab.algo.DynamicSimuAlgorithm;

/**
 * Optional fixed-step staggered Euler solver using the qualified model lifecycle.
 *
 * <p>Retains the existing supported-model checks, derivative histories, limiter
 * projection and event policies. Select before initialization and keep the
 * algorithm's underlying derivative method set to MODIFIED_EULER.</p>
 */
@SuppressWarnings("deprecation")
public class StaggeredEulerSolver extends PsseStaggeredEulerSolver {
    public StaggeredEulerSolver(DynamicSimuAlgorithm algorithm) {
        super(algorithm);
    }
}
