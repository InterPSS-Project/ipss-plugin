package org.interpss.dstab.algo;

import com.interpss.dstab.algo.DynamicSimuAlgorithm;

/**
 * Default dynamic simulation solver for imported networks.
 *
 * <p>Provides a product-neutral entrypoint with the established initialization,
 * controller preparation and predictor/corrector lifecycle. The compatibility
 * superclass preserves existing callers, nested configuration types and saved
 * initialization metadata.</p>
 */
@SuppressWarnings("deprecation")
public class DefaultDynamicSimulationSolver extends org.interpss.fadapter.psse.PsseDStabSolver {
    public DefaultDynamicSimulationSolver(DynamicSimuAlgorithm algorithm) {
        super(algorithm);
    }
}
