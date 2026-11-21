package org.interpss.dstab.algo;

import org.interpss.dstab.dynLoad.impl.Cmldznu2Model;
import org.interpss.dstab.dynLoad.impl.InductionMotorImpl;
import org.interpss.dstab.dynLoad.impl.LD1PACImpl;
import org.interpss.dstab.validation.DynamicParameterValidator;
import org.interpss.dstab.validation.ParameterValidatingSolver;

import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.dynamic.StaggeredEulerSolverBase;

/**
 * Compatibility entrypoint for fixed-step staggered Euler integration of
 * imported networks.
 *
 * <p>The integration runtime is {@link StaggeredEulerSolverBase}. This class
 * names the Plugin dynamic-load implementations the runtime may integrate
 * (induction motor, LD1PAC, and the CMLDZNU2 algebraic and tap-control
 * components) and validates imported model parameters before initialization.</p>
 * @deprecated Use {@link StaggeredEulerSolver}.
 */
@Deprecated(since="1.4.8",forRemoval=false)
public class PsseStaggeredEulerSolver extends StaggeredEulerSolverBase implements ParameterValidatingSolver, org.interpss.dstab.algo.ImportedModelPolicySolver {
    private DynamicParameterValidator.Report parameterValidationReport;

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
    public java.util.List<org.interpss.fadapter.psse.PsseDStabSolver.LoadModelAssignment> getLoadModelAssignmentReport() {
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

    public PsseStaggeredEulerSolver(DynamicSimuAlgorithm algo) {
        super(algo);
    }

    @Override
    protected boolean supportedLoad(Object model) {
        return model != null && (model.getClass() == org.interpss.dstab.dynLoad.impl.IeelLoadModel.class
                || model.getClass() == InductionMotorImpl.class
                || model.getClass() == LD1PACImpl.class || Cmldznu2Model.isAlgebraicComponent(model)
                || Cmldznu2Model.isTapControl(model));
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
