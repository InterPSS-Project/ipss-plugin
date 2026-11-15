package org.interpss.fadapter.psse;

import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.dynamic.DynamicSimulationSolverBase;
import org.interpss.dstab.validation.DynamicParameterValidator;
import org.interpss.dstab.validation.ParameterValidatingSolver;

/**
 * Compatibility solver that preserves a solved RAW initial condition
 * when the load-flow and dynamic network representations differ slightly.
 *
 * <p>The ordinary dynamic network is a Norton {@code YV=I} system and may not
 * use control and zero-impedance-branch equations present in the load-flow
 * solution but absent from the dynamic Norton matrix. Rounded RAW data can
 * therefore leave a small, but
 * dynamically visible, KCL residual when the representation changes. Capture
 * that residual as a fixed Norton compensation current before the first
 * integration step.</p>
 * @deprecated Use {@link org.interpss.dstab.algo.DefaultDynamicSimulationSolver}.
 */
@Deprecated(since="1.4.8",forRemoval=false)
public class PsseDStabSolver extends DynamicSimulationSolverBase implements ParameterValidatingSolver {
    private DynamicParameterValidator.Report parameterValidationReport;

    public PsseDStabSolver(DynamicSimuAlgorithm algorithm) {
        super(algorithm);
    }

    @Override
    public DynamicParameterValidator.Report getParameterValidationReport() { return parameterValidationReport; }

    @Override
    public DynamicParameterValidator.Report validateModelParameters() {
        parameterValidationReport=DynamicParameterValidator.validate(dstabAlgo.getNetwork(),
                dstabAlgo.getSimuStepSec(),getParameterTimeStepMultiple(),this::parameterValidationSubsteps);
        return parameterValidationReport;
    }

    @Override
    protected void validateBeforeInitialization() {
        var validation=validateModelParameters();
        validation.requireNoErrors();
        if(validation.count(DynamicParameterValidator.Severity.WARNING)>0||!validation.uncoveredModels().isEmpty())
            org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                    "Dynamic parameter validation: {} warnings; {} model families need additional rules. Inspect getParameterValidationReport().",
                    validation.count(DynamicParameterValidator.Severity.WARNING),validation.uncoveredModels().size());
    }
}
