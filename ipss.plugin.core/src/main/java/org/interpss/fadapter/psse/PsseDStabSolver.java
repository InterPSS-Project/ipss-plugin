package org.interpss.fadapter.psse;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.ConstantCurrentLoadVoltageCurve;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.defaultImpl.DStabSolverImpl;
import com.interpss.dstab.device.DynamicDevice;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.controller.deqn.measure.BusFreqMeasurementImpl;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.dstab.validation.DynamicParameterValidator;

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
public class PsseDStabSolver extends DStabSolverImpl {
    protected final org.interpss.dstab.algo.MachineReactivationLifecycle reactivation =
            new org.interpss.dstab.algo.MachineReactivationLifecycle();
    private java.util.Set<String> hotReactivationIds=java.util.Set.of();
    private boolean hotReactivationEvent;
    /** Explicitly select qualified machine IDs before initialization; empty preserves existing behavior. */
    private Boolean sourceImpedanceReconciliationEnabled;
    private org.interpss.plugin.dstab.config.LoadModelThresholds loadModelThresholds;
    public record SourceImpedanceReconciliation(String bus,String generator,boolean enabled,
            double originalResistance,double originalReactance,double effectiveResistance,
            double effectiveReactance,boolean changed) { }
    private java.util.List<SourceImpedanceReconciliation> sourceImpedanceReport=java.util.List.of();
    public void setSourceImpedanceReconciliationEnabled(boolean enabled) {sourceImpedanceReconciliationEnabled=enabled;}
    public void setLoadModelThresholds(org.interpss.plugin.dstab.config.LoadModelThresholds thresholds) {loadModelThresholds=thresholds;}
    public org.interpss.plugin.dstab.config.LoadModelThresholds getLoadModelThresholds(){return loadModelThresholds;}
    public java.util.List<SourceImpedanceReconciliation> getSourceImpedanceReconciliationReport(){return sourceImpedanceReport;}
    public java.util.List<org.interpss.dstab.dynLoad.impl.Cmldznu2Model.InitializationScreen> getLoadModelScreeningReport(){
        var report=new java.util.ArrayList<org.interpss.dstab.dynLoad.impl.Cmldznu2Model.InitializationScreen>();
        for(var bus:dstabAlgo.getNetwork().getBusList())
            if(bus.getInfoOnlyDynModel() instanceof org.interpss.dstab.dynLoad.impl.Cmldznu2Model first)
                for(var model:first.getSourceModels())if(model.getInitializationScreen()!=null)report.add(model.getInitializationScreen());
        return java.util.List.copyOf(report);
    }
    /** Final per-source ownership, including ordinary loads with no DYR assignment.
     * Capture identities before composite expansion removes source records. The
     * query then resolves the actual initialization result, rather than mistaking
     * an attached-but-screened composite for a live feeder/motor injection.
     */
    public record LoadModelAssignment(String bus,String loadId,String candidateModel,
            String appliedModel,String status,java.util.List<String> reasons) { }
    private record SourceLoadIdentity(String bus,String loadId,boolean active) { }
    private java.util.List<SourceLoadIdentity> sourceLoadIdentities=java.util.List.of();
    public java.util.List<LoadModelAssignment> getLoadModelAssignmentReport() {
        var result=new java.util.ArrayList<LoadModelAssignment>();
        var network=dstabAlgo.getNetwork();
        String fallback=network.isStaticLoadIncludedInYMatrix()?"CONST_Z/CONST_Z":
                network.getStaticLoadModel().name()+"/"+network.getReactiveStaticLoadModel().name();
        for(var source:sourceLoadIdentities) {
            var bus=network.getDStabBus(source.bus());
            String candidate="NONE",applied=source.active()?fallback:"NONE",status=source.active()?"STATIC_DEFAULT":"INACTIVE";
            java.util.List<String> reasons=java.util.List.of();
            if(source.active()) {
                org.interpss.dstab.dynLoad.impl.Cmldznu2Model owner=null;
                if(bus.getInfoOnlyDynModel() instanceof org.interpss.dstab.dynLoad.impl.Cmldznu2Model first)
                    for(var model:first.getSourceModels())if(model.getTargetLoadIds().contains(source.loadId()))owner=model;
                if(owner!=null) {
                    candidate=owner instanceof org.interpss.dstab.dynLoad.impl.Cmldblu1Model?"CMLDBLU1":"CMLDZNU2";
                    var screen=owner.getInitializationScreen();
                    if(screen!=null&&screen.bypassed()){status="SCREENED_STATIC";reasons=screen.violations();}
                    else {applied=candidate;status=screen==null?"PENDING_INITIALIZATION":"DYNAMIC";}
                } else if(bus.getDynLoadModelList()!=null) {
                    for(var model:bus.getDynLoadModelList())if(model.isActive()&&model.getId().equals(source.loadId())) {
                        candidate=model instanceof org.interpss.dstab.dynLoad.impl.IeelLoadModel ieel
                                ?ieel.getSourceModelName():model.getClass().getSimpleName();
                        applied=candidate;status="DYNAMIC";break;
                    }
                }
            }
            result.add(new LoadModelAssignment(source.bus(),source.loadId(),candidate,applied,status,reasons));
        }
        return java.util.List.copyOf(result);
    }
    public void setNetworkVoltageTolerance(double tolerance) {
        if(!Double.isFinite(tolerance)||tolerance<=0)throw new IllegalArgumentException("Invalid network voltage tolerance");
        converge_tol=tolerance;
    }
    public double getNetworkVoltageTolerance(){return converge_tol;}
    private void prepareImportedInitializationSettings(BaseDStabNetwork<?,?> network) {
        var report=new java.util.ArrayList<SourceImpedanceReconciliation>();
        var sources=new java.util.ArrayList<SourceLoadIdentity>();
        for(var bus:network.getBusList())for(var load:bus.getContributeLoadList())
            sources.add(new SourceLoadIdentity(bus.getId(),load.getId(),bus.isActive()&&load.isActive()));
        sourceLoadIdentities=java.util.List.copyOf(sources);
        for(var bus:network.getBusList())if(bus.isActive()) {
            if(bus.getInfoOnlyDynModel() instanceof org.interpss.dstab.dynLoad.impl.Cmldznu2Model first)
                for(var model:first.getSourceModels())model.setInitializationThresholds(loadModelThresholds);
            if(sourceImpedanceReconciliationEnabled==null)continue;
            for(var generator:bus.getContributeGenList())if(generator.isActive()
                    &&generator.getDynamicGenDevice() instanceof com.interpss.dstab.mach.Machine machine) {
                var before=generator.getSourceZ();if(before==null)continue;
                double reactance=machine instanceof com.interpss.dstab.mach.DynamicMachine dynamic
                        ?dynamic.getMachData().getXd11():machine instanceof com.interpss.dstab.mach.EConstMachine constant?constant.getXd1():Double.NaN;
                if(!(reactance>0)&&machine instanceof com.interpss.dstab.mach.DynamicMachine dynamic)reactance=dynamic.getMachData().getXd1();
                if(!(reactance>0))continue;
                // Both stored source impedances are on the generator MVA base;
                // the dynamic machine may have a different rating/base factor.
                double conversion=machine.getZMultiFactor()/generator.getZMultiFactor();
                Complex after=new Complex(machine.getRa(),reactance).multiply(conversion);
                boolean changed=sourceImpedanceReconciliationEnabled&&before.subtract(after).abs()>1e-12;
                if(changed){generator.setSourceZ(after);generator.setPosGenZ(after);}
                else after=before;
                report.add(new SourceImpedanceReconciliation(bus.getId(),generator.getId(),sourceImpedanceReconciliationEnabled,
                        before.getReal(),before.getImaginary(),after.getReal(),after.getImaginary(),changed));
            }
        }
        sourceImpedanceReport=java.util.List.copyOf(report);
    }

    public void setHotReactivationMachineIds(java.util.Set<String> ids) {
        if(dstabAlgo.getNetwork().isDStabNetInitialized())throw new IllegalStateException("Configure before initialization");
        hotReactivationIds=java.util.Set.copyOf(ids);
    }
    protected final boolean ownsHotReactivation(Object model){return reactivation.owns(model);}
    protected Object captureHotReactivationDiscontinuity(){return null;}
    protected void restoreHotReactivationDiscontinuity(Object saved){}
    protected void markHotReactivationDiscontinuity(){hotReactivationEvent=true;}
    /** Hot reconnection preserves differential states; ordinary energization remains unchanged. */
    public void reactivateMachineAtAcceptedEndpoint(String id,double time) throws DStabSimuException {
        if(time!=dstabAlgo.getSimuTime())throw new IllegalArgumentException("Reactivation time differs from accepted algorithm time");
        reactivation.validateReactivationRequest(id,time);
        reactivation.sampleBeforeEvents(time);
        if(reactivation.alreadyReactivated(id,time))return;
        // Capture surviving accepted machine rates before changing physical status.
        Object eventHistory=captureHotReactivationDiscontinuity();
        var machine=reactivation.beginReactivation(id,time);
        if(machine==null){restoreHotReactivationDiscontinuity(eventHistory);return;}
        var buses=dstabAlgo.getNetwork().getBusList();
        Complex[] voltages=new Complex[buses.size()];
        double[] magnitudes=new double[buses.size()],angles=new double[buses.size()];
        for(int i=0;i<voltages.length;i++){voltages[i]=buses.get(i).getVoltage();magnitudes[i]=buses.get(i).getVoltageMag();angles[i]=buses.get(i).getVoltageAng();}
        var electrical=machine.captureRoundElectricalState();
        var mechanical=machine.captureMechanicalIntegrationState();
        double pe=machine.getPe(),pm=machine.getPm(),efd=machine.getEfd();
        boolean accepted=false,previousEvent=eventBoundaryThisStep;
        try {
            dstabAlgo.getNetwork().formYMatrix4DStab();dstabAlgo.getNetwork().setYMatrixDirty(true);
            eventBoundaryThisStep=true;requireNetworkSolution(time,time,"INITIAL");
            reactivation.finishReactivation(machine,true);accepted=true;markHotReactivationDiscontinuity();
        } finally {
            eventBoundaryThisStep=previousEvent;
            if(!accepted) {
                restoreHotReactivationDiscontinuity(eventHistory);
                reactivation.finishReactivation(machine,false);
                machine.restoreRoundElectricalState(electrical);machine.restoreMechanicalIntegrationState(mechanical);
                machine.setPe(pe);machine.setPm(pm);machine.setEfd(efd);
                for(int i=0;i<voltages.length;i++){buses.get(i).setVoltage(voltages[i]);buses.get(i).setVoltage(magnitudes[i],angles[i]);}
                dstabAlgo.getNetwork().formYMatrix4DStab();dstabAlgo.getNetwork().setYMatrixDirty(true);
            }
        }
    }
    @Override public void nextStep(double time,double dt,com.interpss.dstab.algo.DynamicSimuMethod method)
            throws DStabSimuException {
        // Event-aligned steps can be shorter than the configured nominal step.
        // Prepare interval-dependent inputs only for an actual integration interval,
        // never during the zero-time pre/post switching solves in beforeStep.
        preparePhysicalIntervalInputs(time, dt);
        if(!reactivation.enabled() && physicalStepInputs.isEmpty()){super.nextStep(time,dt,method);return;}
        if(!Double.isFinite(dt)||dt<=0)throw new IllegalArgumentException("Integration interval must be positive");
        physicalStepWrapperActive=true;
        try {super.nextStep(time,dt,method);acceptPhysicalStepTrials();reactivation.commit();}
        catch(RuntimeException|DStabSimuException failure){discardPhysicalStepTrials(failure);try{reactivation.rollback();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}throw failure;}
        finally{physicalStepWrapperActive=false;}
    }
    @Override public void diffEqnIntegrationStep(double time,double dt,
            com.interpss.dstab.algo.DynamicSimuMethod method,int flag) throws DStabSimuException {
        if(flag==0){if(physicalStepWrapperActive)beginPhysicalStepTrials(time,dt);reactivation.begin(dt);}
        super.diffEqnIntegrationStep(time,dt,method,flag);
        if(flag==0&&physicalStepWrapperActive)publishPhysicalStepTrials();
    }
    public enum BusFrequencyMode { REQUIRED, ALL }
    public record BusFrequencyReport(int activeBuses,int measuredBuses,String reason) { }
    /** Frequency is optional at unused buses by default; ALL is an explicit override. */
    private BusFrequencyMode busFrequencyMode=BusFrequencyMode.REQUIRED;
    private BusFrequencyReport busFrequencyReport;
    private java.util.List<PsseGovernorBsgProcessor.Entry> governorBlockingPolicy=java.util.List.of();
    private java.util.List<PsseGovernorBsgProcessor.Entry> governorBlockingReport=java.util.List.of();
    /** Registration is inert; numerical replay occurs only after final startup preparation. */
    public void setGovernorBlockingPolicy(java.util.List<PsseGovernorBsgProcessor.Entry> policy) {
        if(dstabAlgo.getNetwork().isDStabNetInitialized())throw new IllegalStateException("Register governor policy before initialization");
        governorBlockingPolicy=java.util.List.copyOf(policy);governorBlockingReport=governorBlockingPolicy;
    }
    public java.util.List<PsseGovernorBsgProcessor.Entry> getGovernorBlockingReport(){return governorBlockingReport;}
    private boolean stepSensitiveModelSubstepsEnabled;
    public record LocalSubstepPreparation(String modelId,String modelClass,String coordinate,
            double enteredTimeConstantSec,double outerStepSec,int localSteps,double localStepSec) { }
    private java.util.List<LocalSubstepPreparation> localSubstepReport=java.util.List.of();
    /** Explicit numerical option; imported physical time constants and relay clocks are retained. */
    public void setStepSensitiveModelSubstepsEnabled(boolean enabled) {
        if(dstabAlgo.getNetwork().isDStabNetInitialized())
            throw new IllegalStateException("Configure model substeps before initialization");
        stepSensitiveModelSubstepsEnabled=enabled;
    }
    public boolean isStepSensitiveModelSubstepsEnabled(){return stepSensitiveModelSubstepsEnabled;}
    public java.util.List<LocalSubstepPreparation> getLocalSubstepPreparationReport(){return localSubstepReport;}

    private boolean controllerRuntimePreparationEnabled=true;
    public record ControllerPreparationReport(int compiledControllers,int st1Controllers,
            int st4Controllers,int pss2aControllers,int ieeestControllers,int compatibleControllers) { }
    private final ModifiedEulerControllerPreparation controllerPreparation=new ModifiedEulerControllerPreparation();
    private ControllerPreparationReport controllerPreparationReport;
    private final java.util.List<org.interpss.dstab.svc.Svsmo3t2Model> statcomEventModels = new java.util.ArrayList<>();
    private record EventInput(DynamicDevice device, BaseDStabBus<?, ?> bus,
            org.interpss.dstab.control.util.NetworkEventInput input) { }
    private final java.util.List<EventInput> networkEventInputs = new java.util.ArrayList<>();
    private record StepInput(DynamicDevice device, BaseDStabBus<?, ?> bus,
            org.interpss.dstab.control.util.IntegrationStepContext input) { }
    private final java.util.List<StepInput> integrationStepInputs = new java.util.ArrayList<>();
    private static final class PhysicalStepInput {
        final DynamicDevice device; final BaseDStabBus<?,?> bus;
        final org.interpss.dstab.control.util.PhysicalStepTrial input;
        boolean pending;
        PhysicalStepInput(DynamicDevice device,BaseDStabBus<?,?> bus,
                org.interpss.dstab.control.util.PhysicalStepTrial input){this.device=device;this.bus=bus;this.input=input;}
    }
    private boolean physicalStepWrapperActive;
    private final java.util.List<PhysicalStepInput> physicalStepInputs=new java.util.ArrayList<>();
    protected final void beginPhysicalStepTrials(double time,double dt) {
        for(var p:physicalStepInputs)if(p.device.isActive()&&p.bus.isActive()) {
            if(p.pending)throw new IllegalStateException("Unfinished physical-output trial");
            p.pending=true;p.input.beginPhysicalStepTrial(time,dt);
        }
    }
    protected final void publishPhysicalStepTrials(){for(var p:physicalStepInputs)if(p.pending)p.input.publishPhysicalStepTrial();}
    protected final void acceptPhysicalStepTrials(){
        for(var p:physicalStepInputs)if(p.pending)p.input.validatePhysicalStepAcceptance();
        for(var p:physicalStepInputs)if(p.pending){p.input.acceptPhysicalStepTrial();p.pending=false;}
    }
    protected final void discardPhysicalStepTrials(Throwable primary){
        RuntimeException cleanupFailure=null;
        for(int i=physicalStepInputs.size()-1;i>=0;i--){var p=physicalStepInputs.get(i);if(p.pending){
            try{p.input.discardPhysicalStepTrial();}catch(RuntimeException cleanup){
                if(primary!=null)primary.addSuppressed(cleanup);
                else if(cleanupFailure==null)cleanupFailure=cleanup;else cleanupFailure.addSuppressed(cleanup);
            }finally{p.pending=false;}
        }}
        if(cleanupFailure!=null)throw cleanupFailure;
    }
    /** Configure before initialization; false retains compatible controller dispatch. */
    public void setControllerRuntimePreparationEnabled(boolean enabled){controllerRuntimePreparationEnabled=enabled;}
    public boolean isControllerRuntimePreparationEnabled(){return controllerRuntimePreparationEnabled;}
    /** Actual initialized coverage, including custom or unsupported compatible controllers. */
    public ControllerPreparationReport getControllerPreparationReport(){return controllerPreparationReport;}
    protected boolean usesModifiedEulerControllerPreparation(){return true;}
    private com.interpss.dstab.common.IDStabSimuOutputHandler frequencyOutputHandler;
    private java.util.Set<String> frequencyOutputIds;
    private final java.util.Map<BaseDStabBus<?,?>,com.interpss.dstab.controller.cml.field.ICMLControlBlock>
            suppressedFrequencyBlocks=new java.util.IdentityHashMap<>();

    /** Applies on the next initialization. Unknown dependencies always retain all buses. */
    public void setBusFrequencyMode(BusFrequencyMode mode) {busFrequencyMode=java.util.Objects.requireNonNull(mode);}
    public BusFrequencyMode getBusFrequencyMode(){return busFrequencyMode;}
    public BusFrequencyReport getBusFrequencyReport(){return busFrequencyReport;}

    private void restoreFrequencyMeasurements() {
        suppressedFrequencyBlocks.forEach((bus,block)->{if(bus.getBusFreqMeasureBlock()==null)bus.setBusFreqMeasureBlock(block);});
        suppressedFrequencyBlocks.clear();busFrequencyReport=null;
        // A restored snapshot can legitimately omit an unused measurement.
        // Re-establish it before model initialization discovers the next run's needs.
        for(var bus:dstabAlgo.getNetwork().getBusList())if(bus.getBusFreqMeasureBlock()==null) {
            bus.setBusFreqMeasureBlock(com.interpss.dstab.DStabObjectFactory.createBusFreqMeasurement());
            bus.initStates();
        }
    }
    private void selectFrequencyMeasurements() {
        var selection=busFrequencyMode==BusFrequencyMode.ALL?null:BusFrequencySelection.select(dstabAlgo);
        var required=selection==null?null:selection.required();int active=0,measured=0;
        for(var bus:dstabAlgo.getNetwork().getBusList()) {
            if(bus.isActive())active++;
            if(required!=null&&!required.contains(bus.getId())&&bus.getBusFreqMeasureBlock()!=null) {
                suppressedFrequencyBlocks.put(bus,bus.getBusFreqMeasureBlock());bus.setBusFreqMeasureBlock(null);
            } else if(bus.isActive()&&bus.getBusFreqMeasureBlock()!=null)measured++;
        }
        busFrequencyReport=new BusFrequencyReport(active,measured,selection==null?"explicit all-bus measurement":selection.reason());
        frequencyOutputHandler=dstabAlgo.getSimuOutputHandler();
        frequencyOutputIds=frequencyOutputHandler==null?null:frequencyOutputHandler.requiredBusFrequencyIds();
    }

    protected final void preparePhysicalIntervalInputs(double time, double dt) {
        for (var entry : integrationStepInputs)
            if (entry.bus().isActive() && entry.device().isActive())
                entry.input().prepareIntegrationStep(time, dt);
    }

    @Override public void beforeStep(double time) throws DStabSimuException {
        // Relay actions occur after an accepted step, outside the scheduled-event list.
        // Recognize their rebuilt/numerically changed operator before STATCOM and
        // other event-sensitive setup; a consumed dirty flag is not sufficient.
        recognizeNetworkTopologyDiscontinuity();
        if(!suppressedFrequencyBlocks.isEmpty()&&(dstabAlgo.getScriptOutputHandler()!=null
                ||dstabAlgo.getSimuOutputHandler()!=frequencyOutputHandler
                ||!java.util.Objects.equals(frequencyOutputIds,frequencyOutputHandler==null?null:
                        frequencyOutputHandler.requiredBusFrequencyIds())))
            throw new DStabSimuException("Frequency output dependencies changed; reinitialize before continuing");
        if (eventBoundaryThisStep && !dstabAlgo.isDisableDynamicEvent())
            for (var model : statcomEventModels)
                if (model.isActive() && model.getDStabBus() != null && model.getDStabBus().isActive()) model.acceptNetworkEvent();
        reactivation.sampleBeforeEvents(time);
        super.beforeStep(time);
        reactivation.acceptStatusChanges();
        if(hotReactivationEvent){eventBoundaryThisStep=true;hotReactivationEvent=false;}
    }
    public static final int DEFAULT_MAXIMUM_NETWORK_ITERATIONS = 50;
    private double parameterTimeStepMultiple = 2.0;
    private DynamicParameterValidator.Report parameterValidationReport;

    /** Advisory pole-resolution margin; this does not alter any model parameter. */
    public void setParameterTimeStepMultiple(double multiple) {
        if(!Double.isFinite(multiple)||multiple<=0)
            throw new IllegalArgumentException("Parameter time-step multiple must be finite and positive");
        parameterTimeStepMultiple=multiple;
    }

    public double getParameterTimeStepMultiple() { return parameterTimeStepMultiple; }

    /** Last explicit or initialization validation; null before the first check. */
    public DynamicParameterValidator.Report getParameterValidationReport() { return parameterValidationReport; }

    /** Inspect imported data without solving, initializing states or modifying parameters. */
    public DynamicParameterValidator.Report validateModelParameters() {
        parameterValidationReport=DynamicParameterValidator.validate(dstabAlgo.getNetwork(),
                dstabAlgo.getSimuStepSec(),parameterTimeStepMultiple,this::parameterValidationSubsteps);
        return parameterValidationReport;
    }

    /** Local steps are model-specific; the ordinary solver uses the network step. */
    protected int parameterValidationSubsteps(Machine machine) { return 1; }

    private static final String COMPENSATION_KEY =
            PsseDStabSolver.class.getName() + ".compensation";
    private Hashtable<String, Complex> initializedCompensationOwner;
    private java.util.Map<String, Complex> initializedCompensationBaseline = java.util.Map.of();

    /** Only unchanged entries in this solver's accepted initialization residual
     * table are numerical bookkeeping. A caller may replace the table or edit an
     * entry in place; either change restores independent-source protection for
     * that entry. The immutable value snapshot prevents a mutable public table
     * from silently acquiring ownership of later caller-supplied injections. */
    @Override protected boolean isInitializationCompensationCurrentSource(
            Hashtable<String, Complex> table, String busId) {
        return table != null && table == initializedCompensationOwner
                && table == dstabAlgo.getNetwork().getCustomBusCurrInjHashtable()
                && table == dstabAlgo.getNetwork().getExtraInfo().get(COMPENSATION_KEY)
                && initializedCompensationBaseline.containsKey(busId)
                && java.util.Objects.equals(initializedCompensationBaseline.get(busId), table.get(busId));
    }

    public PsseDStabSolver(DynamicSimuAlgorithm algorithm) {
        super(algorithm);
        // Bound network work while retaining the configured convergence test.
        // Exhaustion is a failed solve, never acceptance of the last iterate.
        setMaxIterationTimes(DEFAULT_MAXIMUM_NETWORK_ITERATIONS);
        // Mixing requires an explicit caller choice; keep ordinary/packed iteration by default.
        setNetworkAccelerationEnabled(false);
    }

    /** Only known memory-free current sources qualify for bounded fixed-point mixing. */
    @Override protected boolean canAccelerateNetworkIterations(java.util.List<DynamicDevice> participants) {
        for (DynamicDevice device : participants)
            if (!stationaryCurrentSource(device)) return false;
        var network = dstabAlgo.getNetwork();
        var currents = network.getCustomBusCurrInjHashtable();
        if (currents != null && currents.getClass() != Hashtable.class) return false;
        var sequenceCurrents = network.getCustom3SeqBusCurrInjHashtable();
        if (sequenceCurrents != null && !sequenceCurrents.isEmpty()) return false;
        for (BaseDStabBus<?, ?> bus : network.getBusList()) {
            if (!bus.isActive()) continue;
            if (bus.getScriptDynamicBusDevice() != null && bus.getScriptDynamicBusDevice().isActive()) return false;
            for (DynamicDevice device : bus.getDynamicBusDeviceList())
                if (device.isActive() && !stationaryCurrentSource(device)) return false;
            if (bus.getDynLoadModelList() != null)
                for (DynamicDevice device : bus.getDynLoadModelList())
                    if (device.isActive()) return false;
            for (DStabGen generator : bus.getContributeGenList()) {
                if (!generator.isActive()) continue;
                var device = generator.getDynamicGenDevice();
                if (device != null && device.isActive() && !(device instanceof Machine)
                        && !stationaryCurrentSource(device)) return false;
            }
        }
        for (var branch : network.getBranchList())
            if (branch.isActive()) for (DynamicDevice device : branch.getDynamicBranchDeviceList())
                if (device.isActive()) return false;
        return true;
    }

    private static boolean stationaryCurrentSource(DynamicDevice device) {
        return device.supportsNetworkIterationAcceleration();
    }

    /** Keep the default finite budget at 50, while honoring an explicit caller
     * budget. Under-relaxed low-voltage equations can need additional numerical
     * passes without any change to tolerance, physical model or integration step.
     * The requested positive limit still bounds each solve, and exhaustion still
     * fails before accepting the step; this never retries partially advanced state. */
    @Override public void setMaxIterationTimes(int maximum) {
        if (maximum < 1)
            throw new IllegalArgumentException("Maximum network iterations must be positive");
        super.setMaxIterationTimes(maximum);
    }

    @Override public boolean networkSolutionStep() throws DStabSimuException {
        org.interpss.dstab.relay.AbstractStagedLoadSheddingRelayModel.reapplyAfterNetworkRebuild(dstabAlgo.getNetwork());
        boolean solved=super.networkSolutionStep();
        // The event handler rebases after one linear Norton solve. Nonlinear
        // current sources can still move the angle during INITIAL convergence.
        // Rebase at that final zero-time endpoint, before frequency integration,
        // without advancing or resetting its filter state or output.
        if(solved&&eventBoundaryThisStep&&!dstabAlgo.isDisableDynamicEvent()
                &&"INITIAL".equals(networkSolutionStage)){
            for(BaseDStabBus<?,?> bus:dstabAlgo.getNetwork().getBusList())
                if(bus.isActive()&&bus.getBusFreqMeasureBlock() instanceof BusFreqMeasurementImpl frequency)
                    frequency.synchronizeNetworkAngle(bus.getVoltageAng());
            for (var entry : networkEventInputs)
                if (entry.bus().isActive() && entry.device().isActive())
                    entry.input().acceptNetworkEventInput(dstabAlgo.getSimuTime());
        }
        if (solved && ("CORRECTOR".equals(networkSolutionStage)
                || ("INITIAL".equals(networkSolutionStage) && eventBoundaryThisStep
                        && !dstabAlgo.isDisableDynamicEvent()))) {
            for (BaseDStabBus<?, ?> bus : dstabAlgo.getNetwork().getBusList()) {
                if (!bus.isActive()) continue;
                for (DStabGen generator : bus.getContributeGenList()) {
                    if (generator.isActive() && generator.getDynamicGenDevice() instanceof Machine machine
                            && machine.isActive() && machine.getStabilizer() instanceof
                            org.interpss.dstab.control.util.AcceptedVoltageMeasurement stabilizer
                            && stabilizer.isActive()) {
                        if ("CORRECTOR".equals(networkSolutionStage))
                            stabilizer.acceptVoltageDerivativeEndpoint(networkEvaluationTime, timestep);
                        else if (eventBoundaryThisStep && !dstabAlgo.isDisableDynamicEvent())
                            stabilizer.synchronizeVoltageDerivativeMemory();
                    }
                }
            }
        }
        return solved;
    }

    @Override protected void rebaseDynamicModelInputs(com.interpss.dstab.algo.DynamicSimuMethod method)
            throws DStabSimuException {
        super.rebaseDynamicModelInputs(method);
        // Project bus-device algebraic limits at the settled event endpoint.
        // This is outside network iteration and does not commit any timer.
        for (var bus : dstabAlgo.getNetwork().getBusList()) if (bus.isActive())
            for (var device : bus.getDynamicBusDeviceList())
                if (device.isActive() && device instanceof org.interpss.dstab.svc.Svsmo3t2Model model
                        && !model.nextStep(0, method, 0))
                    throw new DStabSimuException("Could not refresh bus-device inputs for " + model.getId());
    }

    @Override
    public boolean initialization() {
        initializedCompensationOwner=null;initializedCompensationBaseline=java.util.Map.of();
        discardPhysicalStepTrials(null);
        governorBlockingReport=governorBlockingPolicy;
        controllerPreparation.clear();controllerPreparationReport=null;statcomEventModels.clear();networkEventInputs.clear();integrationStepInputs.clear();physicalStepInputs.clear();
        reactivation.prepare(dstabAlgo,hotReactivationIds);hotReactivationEvent=false;
        restoreFrequencyMeasurements();
        BaseDStabNetwork<?, ?> network = dstabAlgo.getNetwork();
        prepareImportedInitializationSettings(network);
        org.interpss.dstab.hvdc.Cdc6tDyrModel.prepareNetwork(network);
        org.interpss.dstab.hvdc.Chvdc2DyrModel.prepareNetwork(network, dstabAlgo.getSimuStepSec());
        var validation=validateModelParameters();
        validation.requireNoErrors();
        if(validation.count(DynamicParameterValidator.Severity.WARNING)>0||!validation.uncoveredModels().isEmpty())
            org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                    "Dynamic parameter validation: {} warnings; {} model families need additional rules. Inspect getParameterValidationReport().",
                    validation.count(DynamicParameterValidator.Severity.WARNING),validation.uncoveredModels().size());
        // Native constant-current loads retain I down to 0.5 pu, then use an
        // elliptic reduction. The generic core's sine curve remains available
        // to other solvers. Apply this before load-current initialization.
        network.setConstantCurrentLoadVoltageCurve(ConstantCurrentLoadVoltageCurve.ELLIPTIC);
        configureIntegrationStepAwareModels(dstabAlgo.getNetwork(), dstabAlgo.getSimuStepSec());
        if (!super.initialization()) return false;
        for (var bus : network.getBusList())
            for (var device : bus.getDynamicBusDeviceList()) {
                if (device instanceof org.interpss.dstab.svc.Svsmo3t2Model model) statcomEventModels.add(model);
                if (device instanceof org.interpss.dstab.control.util.NetworkEventInput input)
                    networkEventInputs.add(new EventInput(device, bus, input));
                if (device instanceof org.interpss.dstab.control.util.IntegrationStepContext input)
                    integrationStepInputs.add(new StepInput(device, bus, input));
                if (device instanceof org.interpss.dstab.control.util.PhysicalStepTrial input && input.requiresPhysicalStepTrial())
                    physicalStepInputs.add(new PhysicalStepInput(device,bus,input));
            }
        selectFrequencyMeasurements();
        // Do not overwrite a hybrid-simulation current source supplied by a caller.
        Hashtable<String, Complex> existing = network.getCustomBusCurrInjHashtable();
        Object previous = network.getExtraInfo().get(COMPENSATION_KEY);
        if (existing != null && existing != previous) return finishInitialPreparation(network);

        Hashtable<String, Complex> compensation = new Hashtable<>();
        // Ideal-current rows extend the operator beyond the physical bus rows.
        // Zero auxiliary coordinates evaluate only the ordinary network current.
        Complex[] voltage = new Complex[network.getYMatrix().getDimension()];
        java.util.Arrays.fill(voltage, Complex.ZERO);
        for (BaseDStabBus<?, ?> bus : network.getBusList()) {
            voltage[bus.getSortNumber()] = bus.isActive() ? bus.getVoltage() : Complex.ZERO;
        }
        Complex[] networkCurrent = network.getYMatrix().multiply(voltage);
        for (BaseDStabBus<?, ?> bus : network.getBusList()) {
            if (!bus.isActive()) continue;
            try {
                Complex deviceCurrent = bus.injCurDynamic(network.getStaticLoadModel(),
                        network.getStaticLoadSwitchVolt(),
                        network.getStaticLoadSwitchDeadZone());
                Complex residual = networkCurrent[bus.getSortNumber()].subtract(deviceCurrent);
                retainNonzeroCompensation(compensation, bus.getId(), residual);
            } catch (Exception e) {
                return false;
            }
        }
        // This is a fresh solver-owned residual. Preserve a component aggregate,
        // while ideal currents supply its internal zero-sum balance. The earlier
        // caller-table guard is essential: physical injected sources are never projected.
        if (network.getYMatrix() instanceof com.interpss.dstab.algo.defaultImpl.DynamicIdealNodeMatrix ideal)
            ideal.reconcileNewInitializationResidual(compensation);
        network.setCustomBusCurrInjHashtable(compensation);
        network.getExtraInfo().put(COMPENSATION_KEY, compensation);
        return finishInitialPreparation(network);
    }

    private boolean finishInitialPreparation(BaseDStabNetwork<?, ?> network) {
        if (!acceptInitialPlantEndpoints(network)) return false;
        // Startup source reconciliation may initialize a governor again. Apply the
        // explicit travel policy to its FINAL storage before compiling CML plans.
        // Never reinitialize or integrate a governor to change a numerical limit.
        if(!governorBlockingPolicy.isEmpty()) {
            try {governorBlockingReport=PsseGovernorBsgProcessor.apply(network,governorBlockingPolicy);}
            catch(com.interpss.common.exp.InterpssException failure) {
                org.slf4j.LoggerFactory.getLogger(getClass()).error("Governor policy replay failed",failure);return false;
            }
            var counts=governorBlockingReport.stream().collect(java.util.stream.Collectors.groupingBy(
                    PsseGovernorBsgProcessor.Entry::status,java.util.stream.Collectors.counting()));
            org.slf4j.LoggerFactory.getLogger(getClass()).info("Initial governor policy outcomes: {}; inspect getGovernorBlockingReport() for source/old/new limits",counts);
            if(counts.containsKey(PsseGovernorBsgProcessor.Status.ERROR))return false;
        }
        prepareStepSensitiveModelSubsteps(network);
        // Startup control transfers can reinitialize controller blocks and
        // references. Bind optimized execution only to their final storage.
        if(controllerRuntimePreparationEnabled&&usesModifiedEulerControllerPreparation()
                &&dstabAlgo.getSimuMethod()==com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER)
            controllerPreparationReport=controllerPreparation.prepare(dstabAlgo);
        var currents=network.getCustomBusCurrInjHashtable();
        if(currents!=null&&currents==network.getExtraInfo().get(COMPENSATION_KEY)) {
            initializedCompensationOwner=currents;
            initializedCompensationBaseline=java.util.Map.copyOf(currents);
        }
        return true;
    }

    /**
     * Enable only the two families demonstrated to be under-resolved in the
     * no-event step comparison. Motor local voltage feedback is refreshed inside
     * every substep; ESST1A integrates its complete regulator/feedback graph,
     * including a short transducer, with unchanged entered time constants.
     * Configure FINAL initialized blocks before compiling any controller plan.
     * This is not the independent native short-lag bypass convention.
     */
    private void prepareStepSensitiveModelSubsteps(BaseDStabNetwork<?,?> network) {
        localSubstepReport=java.util.List.of();
        if(!stepSensitiveModelSubstepsEnabled)return;
        if(dstabAlgo.getSimuMethod()!=com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER)
            throw new IllegalArgumentException("Model local substeps currently require modified Euler");
        double outer=dstabAlgo.getSimuStepSec();
        var report=new java.util.ArrayList<LocalSubstepPreparation>();
        var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object,Boolean>());
        for(var bus:network.getBusList()) {
            // Inactive metadata attachments may deliberately remain uninitialized.
            // They have no running differential equations to subdivide.
            if(!bus.isActive())continue;
            for(var device:bus.getDynamicBusDeviceList())if(device.isActive()&&seen.add(device)
                    &&device.getClass()==org.interpss.dstab.dynLoad.impl.InductionMotorImpl.class) {
                var motor=(org.interpss.dstab.dynLoad.impl.InductionMotorImpl)device;
                if(!motor.isTwoAxisModel())continue;
                double maximum=Math.min(.001,Math.min(.5*motor.getTp0(),.5*motor.getTpp0()));
                if(!Double.isFinite(maximum)||maximum<=0)throw new IllegalArgumentException("Invalid motor electrical time constant");
                int count=(int)Math.ceil(outer/maximum);
                if(count>128)throw new IllegalArgumentException("Motor requires more than 128 local intervals");
                motor.setLocalIntegrationMaximumStepSec(maximum);
                if(count>1)report.add(new LocalSubstepPreparation(motor.getId()+"@"+bus.getId(),motor.getClass().getName(),
                        "coupled electrical states and slip",motor.getTpp0(),outer,count,outer/count));
            }
            for(var generator:bus.getContributeGenList())if(generator.isActive()&&generator.getMach()!=null&&generator.getMach().isActive()
                    &&generator.getMach().getExciter() instanceof org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter exciter
                    &&exciter.isActive()&&seen.add(exciter)&&"ESST1A".equals(exciter.getName())&&exciter.hasInitializedBlocks()) {
                var block=(com.interpss.dstab.controller.cml.field.block.DelayControlBlock)exciter.getNamedStateBlocks().get("trDelayBlock");
                if(block.getType()!=com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType.NoLimit)
                    throw new IllegalStateException("Unexpected ESST1A transducer limiter");
                // A short TR is not the only stiff coordinate. The regulator /
                // washout feedback loop can have a much faster pole than either
                // entered Ta or Tf (even when both exceed the outer interval).
                // This conservative resolution indicator is not an eigenvalue
                // certificate. Retain at least the already resolved 1 ms local
                // interval for selected controllers, and qualify the coupled run.
                double rate=0;
                for(double constant:new double[]{exciter.tr,exciter.ta})if(constant>0)rate=Math.max(rate,1/constant);
                if(exciter.tb>0&&exciter.tc!=exciter.tb)rate=Math.max(rate,1/exciter.tb);
                if(exciter.tb1>0&&exciter.tc1!=exciter.tb1)rate=Math.max(rate,1/exciter.tb1);
                if(exciter.kf!=0&&exciter.tf>0) {
                    rate=Math.max(rate,1/exciter.tf);
                    if(exciter.ta>0) {
                        double directGain=(exciter.tb>0?Math.max(1,Math.abs(exciter.tc/exciter.tb)):1)
                                *(exciter.tb1>0?Math.max(1,Math.abs(exciter.tc1/exciter.tb1)):1);
                        rate=Math.max(rate,(1+Math.abs(exciter.ka*exciter.kf/exciter.tf)*directGain)/exciter.ta+1/exciter.tf);
                    }
                }
                int count=(int)Math.ceil(outer*rate);
                if(count>1)count=Math.max(count,(int)Math.ceil(outer/.001));
                if(count>128)throw new IllegalArgumentException("ESST1A requires more than 128 local intervals");
                // The entire feedback graph owns these intervals. Nested block
                // substeps would repeat work and confuse its accepted history.
                block.setLocalIntegrationSteps(1);
                exciter.setModifiedEulerLocalIntegrationSteps(Math.max(1,count));
                if(count>1)report.add(new LocalSubstepPreparation(generator.getMach().getId(),exciter.getClass().getName(),
                        "coupled controller states including transducer and feedback",exciter.getData().getTr(),outer,count,outer/count));
            }
        }
        localSubstepReport=java.util.List.copyOf(report);
        if(!report.isEmpty())org.slf4j.LoggerFactory.getLogger(getClass()).info(
                "Enabled local model substeps for {} entries at outer interval {} s; inspect getLocalSubstepPreparationReport()",report.size(),outer);
    }

    /** The solved initial network is an accepted endpoint. Start discrete plant
     * timers here, after Y/compensation setup, never inside a trial integrator
     * stage. Zero-delay actions change the physical Y for the next solve; they
     * must not be absorbed into the operating-point compensation current. */
    private boolean acceptInitialPlantEndpoints(BaseDStabNetwork<?, ?> network) {
        if (!prepareInitialOperatingPoint(network)) return false;
        boolean initialPhysicalAction = false;
        for (BaseDStabBus<?, ?> bus : network.getBusList()) {
            if (!bus.isActive()) continue;
            for (var device : bus.getDynamicBusDeviceList())
                if (device.isActive() && device instanceof org.interpss.dstab.svc.Svsmo3t2Model statcom
                        && !statcom.acceptSwitchingEndpoint()) return false;
            for (var device : bus.getDynamicBusDeviceList())
                if (device.isActive() && device instanceof org.interpss.dstab.control.util.InitialEndpointAction action
                        && !action.isInitialOperatingPointPreparation())
                    initialPhysicalAction |= action.acceptInitialEndpoint();
            for (DStabGen generator : bus.getContributeGenList()) {
                if (!generator.isActive()) continue;
                if (generator.getDynamicGenDevice() instanceof Machine machine && machine.isActive()
                        && machine.getStabilizer() instanceof org.interpss.dstab.control.util.AcceptedVoltageMeasurement stabilizer
                        && stabilizer.isActive()) stabilizer.synchronizeVoltageDerivativeMemory();
                if (generator.getDynamicGenDevice() instanceof org.interpss.dstab.renewable.Regca1Model converter
                        && converter.isActive()) {
                    var electrical=converter.getActiveElectricalController();
                    if (electrical!=null && electrical.getRepccController()!=null)
                        electrical.getRepccController().acceptedEndpoint();
                    if (electrical!=null && electrical.getHybridController()!=null)
                        electrical.getHybridController().owner().acceptedEndpoint();
                }
            }
        }
        if (initialPhysicalAction) {
            // Resolve a physical zero-time action without incorporating its
            // injection change into the fixed operating-point compensation.
            String previousStage = networkSolutionStage;
            networkSolutionStage = "INITIAL";
            try {
                org.interpss.dstab.relay.AbstractStagedLoadSheddingRelayModel.reapplyAfterNetworkRebuild(network);
                if (!super.networkSolutionStep()) return false;
                for (BaseDStabBus<?, ?> bus : network.getBusList()) if (bus.isActive())
                    for (var device : bus.getDynamicBusDeviceList())
                        if (device.isActive() && device instanceof org.interpss.dstab.control.util.InitialEndpointAction action)
                            action.acceptInitialNetworkEndpoint();
                return true;
            }
            catch (DStabSimuException e) { return false; }
            finally { networkSolutionStage = previousStage; }
        }
        return true;
    }

    /** Establish model-requested startup control transfers before physical
     * initial events. Keeping these phases separate preserves real load/trip
     * disturbances instead of absorbing them into generator shaft power. */
    private boolean prepareInitialOperatingPoint(BaseDStabNetwork<?, ?> network) {
        var preparations = new java.util.ArrayList<org.interpss.dstab.control.util.InitialEndpointAction>();
        for (BaseDStabBus<?, ?> bus : network.getBusList()) if (bus.isActive())
            for (var device : bus.getDynamicBusDeviceList())
                if (device.isActive() && device instanceof org.interpss.dstab.control.util.InitialEndpointAction action
                        && action.isInitialOperatingPointPreparation()) preparations.add(action);
        if (preparations.isEmpty()) return true;
        var sources = new java.util.ArrayList<org.interpss.dstab.mach.IeeeVcEConstMachine>();
        for (BaseDStabBus<?, ?> bus : network.getBusList()) if (bus.isActive())
            for (DStabGen generator : bus.getContributeGenList()) if (generator.isActive()) {
                var device = generator.getDynamicGenDevice();
                if (device == null || !device.isActive()) continue;
                if (!(device instanceof org.interpss.dstab.mach.IeeeVcEConstMachine machine)
                        || !machine.supportsInitialOperatingPointBalance())
                    throw new IllegalStateException("Initial control-transfer source balance is not qualified for " + generator.getId());
                sources.add(machine);
            }
        boolean changed = false;
        for (var action : preparations) changed |= action.acceptInitialEndpoint();
        if (!changed) return true;
        String previousStage = networkSolutionStage;
        networkSolutionStage = "INITIAL";
        try {
            if (!super.networkSolutionStep()) return false;
            for (var action : preparations) action.acceptInitialNetworkEndpoint();
            for (var source : sources) source.reconcileInitialOperatingPointBalance();
            return true;
        } catch (DStabSimuException failure) { return false; }
        finally { networkSolutionStage = previousStage; }
    }

    static void retainNonzeroCompensation(Hashtable<String, Complex> compensation,
            String busId, Complex residual) {
        // Every representable residual belongs to the solved operating point.
        // Dropping a small value here can excite a weak but unstable mode even
        // though the load-flow and every dynamic state were initialized exactly.
        if (residual != null && !residual.isNaN() && residual.abs() > 0.0) {
            compensation.put(busId, residual);
        }
    }

    static void configureIntegrationStepAwareModels(BaseDStabNetwork<?, ?> network,
            double timeStep) {
        var prepared = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
        for (BaseDStabBus<?, ?> bus : network.getBusList()) {
            // Composite owners must forward preparation before expanding their leaves.
            configure(bus.getInfoOnlyDynModel(), timeStep);
            for (DynamicDevice device : bus.getDynamicBusDeviceList())
                if (prepared.add(device)) configure(device, timeStep);
            if (bus.getDynLoadModelList() != null)
                for (DynamicDevice device : bus.getDynLoadModelList())
                    if (prepared.add(device)) configure(device, timeStep);
            if (prepared.add(bus.getScriptDynamicBusDevice())) configure(bus.getScriptDynamicBusDevice(), timeStep);
            for (DStabGen generator : bus.getContributeGenList()) {
                DynamicDevice device = generator.getDynamicGenDevice();
                configure(device, timeStep);
                if (device instanceof Machine machine) {
                    configure(machine.getExciter(), timeStep);
                    configure(machine.getGovernor(), timeStep);
                    configure(machine.getStabilizer(), timeStep);
                }
            }
        }
    }

    private static void configure(Object model, double timeStep) {
        if (model instanceof IntegrationStepAware aware) {
            aware.configureIntegrationStep(timeStep);
        }
    }

}
