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
    public enum BusFrequencyMode { REQUIRED, ALL }
    public record BusFrequencyReport(int activeBuses,int measuredBuses,String reason) { }
    /** Frequency is optional at unused buses by default; ALL is an explicit override. */
    private BusFrequencyMode busFrequencyMode=BusFrequencyMode.REQUIRED;
    private BusFrequencyReport busFrequencyReport;
    private boolean controllerRuntimePreparationEnabled=true;
    public record ControllerPreparationReport(int compiledControllers,int st1Controllers,
            int st4Controllers,int pss2aControllers,int ieeestControllers,int compatibleControllers) { }
    private final ModifiedEulerControllerPreparation controllerPreparation=new ModifiedEulerControllerPreparation();
    private ControllerPreparationReport controllerPreparationReport;
    private final java.util.List<org.interpss.dstab.svc.Svsmo3t2Model> statcomEventModels = new java.util.ArrayList<>();
    private record EventInput(DynamicDevice device, BaseDStabBus<?, ?> bus,
            org.interpss.dstab.control.util.NetworkEventInput input) { }
    private final java.util.List<EventInput> networkEventInputs = new java.util.ArrayList<>();
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

    @Override public void beforeStep(double time) throws DStabSimuException {
        if(!suppressedFrequencyBlocks.isEmpty()&&(dstabAlgo.getScriptOutputHandler()!=null
                ||dstabAlgo.getSimuOutputHandler()!=frequencyOutputHandler
                ||!java.util.Objects.equals(frequencyOutputIds,frequencyOutputHandler==null?null:
                        frequencyOutputHandler.requiredBusFrequencyIds())))
            throw new DStabSimuException("Frequency output dependencies changed; reinitialize before continuing");
        if (eventBoundaryThisStep && !dstabAlgo.isDisableDynamicEvent())
            for (var model : statcomEventModels)
                if (model.isActive() && model.getDStabBus() != null && model.getDStabBus().isActive()) model.acceptNetworkEvent();
        super.beforeStep(time);
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

    /** Imported-case network solves have a bounded iteration budget. */
    @Override public void setMaxIterationTimes(int maximum) {
        if (maximum < 1 || maximum > DEFAULT_MAXIMUM_NETWORK_ITERATIONS)
            throw new IllegalArgumentException("Maximum network iterations must be between 1 and 50");
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
        controllerPreparation.clear();controllerPreparationReport=null;statcomEventModels.clear();networkEventInputs.clear();
        restoreFrequencyMeasurements();
        BaseDStabNetwork<?, ?> network = dstabAlgo.getNetwork();
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
            }
        selectFrequencyMeasurements();
        if(controllerRuntimePreparationEnabled&&usesModifiedEulerControllerPreparation()
                &&dstabAlgo.getSimuMethod()==com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER)
            controllerPreparationReport=controllerPreparation.prepare(dstabAlgo);

        // Do not overwrite a hybrid-simulation current source supplied by a caller.
        Hashtable<String, Complex> existing = network.getCustomBusCurrInjHashtable();
        Object previous = network.getExtraInfo().get(COMPENSATION_KEY);
        if (existing != null && existing != previous) return acceptInitialPlantEndpoints(network);

        Hashtable<String, Complex> compensation = new Hashtable<>();
        Complex[] voltage = new Complex[network.getNoBus()];
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
        network.setCustomBusCurrInjHashtable(compensation);
        network.getExtraInfo().put(COMPENSATION_KEY, compensation);
        return acceptInitialPlantEndpoints(network);
    }

    /** The solved initial network is an accepted endpoint. Start discrete plant
     * timers here, after Y/compensation setup, never inside a trial integrator
     * stage. Zero-delay actions change the physical Y for the next solve; they
     * must not be absorbed into the operating-point compensation current. */
    private boolean acceptInitialPlantEndpoints(BaseDStabNetwork<?, ?> network) {
        boolean initialPhysicalAction = false;
        for (BaseDStabBus<?, ?> bus : network.getBusList()) {
            if (!bus.isActive()) continue;
            for (var device : bus.getDynamicBusDeviceList())
                if (device.isActive() && device instanceof org.interpss.dstab.svc.Svsmo3t2Model statcom
                        && !statcom.acceptSwitchingEndpoint()) return false;
            for (var device : bus.getDynamicBusDeviceList())
                if (device.isActive() && device instanceof org.interpss.dstab.control.util.InitialEndpointAction action)
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
        for (BaseDStabBus<?, ?> bus : network.getBusList()) {
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
