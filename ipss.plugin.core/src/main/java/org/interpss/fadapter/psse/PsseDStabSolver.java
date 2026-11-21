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
public class PsseDStabSolver extends DynamicSimulationSolverBase implements ParameterValidatingSolver, org.interpss.dstab.algo.ImportedModelPolicySolver {
    private DynamicParameterValidator.Report parameterValidationReport;

    /** Final per-source ownership, including ordinary loads with no DYR assignment. */
    public record LoadModelAssignment(String bus,String loadId,String candidateModel,
            String appliedModel,String status,java.util.List<String> reasons) { }

    private final org.interpss.fadapter.psse.GovernorPolicyReplay governorPolicy=new org.interpss.fadapter.psse.GovernorPolicyReplay();
    /** Registration is inert; numerical replay occurs only after final startup preparation. */
    public void setGovernorBlockingPolicy(java.util.List<org.interpss.fadapter.psse.PsseGovernorBsgProcessor.Entry> policy) {
        governorPolicy.register(dstabAlgo.getNetwork(),policy);
    }
    public java.util.List<org.interpss.fadapter.psse.PsseGovernorBsgProcessor.Entry> getGovernorBlockingReport(){return governorPolicy.report();}

    @Override
    protected void prepareInitialGovernorPolicy() { governorPolicy.reset(); }

    @Override
    protected boolean applyInitialGovernorPolicy(com.interpss.dstab.BaseDStabNetwork<?,?> network) {
        return governorPolicy.apply(network,getClass());
    }
    private org.interpss.plugin.dstab.config.LoadModelThresholds loadModelThresholds;
    public void setLoadModelThresholds(org.interpss.plugin.dstab.config.LoadModelThresholds thresholds) {loadModelThresholds=thresholds;}
    public org.interpss.plugin.dstab.config.LoadModelThresholds getLoadModelThresholds(){return loadModelThresholds;}
    public java.util.List<org.interpss.dstab.dynLoad.impl.Cmldznu2Model.InitializationScreen> getLoadModelScreeningReport(){
        return org.interpss.dstab.algo.ImportedLoadModelSupport.screeningReport(dstabAlgo.getNetwork());
    }
    public java.util.List<LoadModelAssignment> getLoadModelAssignmentReport() {
        return org.interpss.dstab.algo.ImportedLoadModelSupport.assignmentReport(dstabAlgo.getNetwork(),getSourceLoadIdentities());
    }

    @Override
    protected void prepareImportedLoadModels(com.interpss.dstab.BaseDStabNetwork<?,?> network) {
        org.interpss.dstab.algo.ImportedLoadModelSupport.applyThresholds(network,loadModelThresholds);
    }

    @Override
    protected void prepareDeviceLocalSubsteps(com.interpss.dstab.device.DynamicBusDevice device, String busId,
            double outerStep, java.util.List<LocalSubstepPreparation> report) {
        org.interpss.dstab.algo.ImportedLoadModelSupport.prepareMotorSubsteps(device,busId,outerStep,report);
    }

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
