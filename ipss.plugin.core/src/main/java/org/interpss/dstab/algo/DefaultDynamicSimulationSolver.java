package org.interpss.dstab.algo;

import com.interpss.dstab.algo.DynamicSimuAlgorithm;

/**
 * Default dynamic simulation solver for imported networks.
 *
 * <p>Provides a product-neutral entrypoint with the established initialization,
 * controller preparation and predictor/corrector lifecycle. The compatibility
 * superclass preserves existing callers, nested configuration types and saved
 * initialization metadata.</p>
 *
 * <p>The core {@link com.interpss.dstab.DStabObjectFactory} installs its generic
 * solver. {@link org.interpss.fadapter.psse.PSSEMultiFileLoader} instead installs
 * this entrypoint automatically; retrieve the attached solver after loading
 * rather than constructing another instance. Manual algorithms can install it
 * with {@code algorithm.setSolver(new DefaultDynamicSimulationSolver(algorithm))}
 * before initialization.</p>
 *
 * <p>The selected integration method is unchanged. Additional policies cover
 * operating-point current compensation, low-voltage current-load behavior,
 * parameter/step preparation, required frequency measurements, qualified
 * controller preparation and model-specific event memory. See
 * {@code docs/dynamic-solver-selection.md} for defaults and examples.</p>
 */
@SuppressWarnings("deprecation")
public class DefaultDynamicSimulationSolver extends org.interpss.fadapter.psse.PsseDStabSolver {
    public DefaultDynamicSimulationSolver(DynamicSimuAlgorithm algorithm) {
        super(algorithm);
    }
}
