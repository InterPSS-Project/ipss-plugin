package org.interpss.dstab.algo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;

import org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter;
import org.interpss.dstab.control.gov.psse.gast.PsseGASTGasTurGovernor;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;
import org.interpss.dstab.control.gov.psse.ieesgo.PsseIEESGOSteamTurGovernor;
import org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor;
import org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1Governor;
import org.interpss.dstab.control.gov.psse.hygov.PsseHygovGovernor;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter;
import org.interpss.dstab.control.exc.ieee.y2005.st4b.Esst4bPIControlBlock;
import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer;
import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Pss2aLeadLagBlock;
import org.interpss.dstab.control.pss.psse.ieeest.IeeestStabilizer;
import org.interpss.dstab.control.util.FreezeNonWindupPIControlBlock;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.Regfma1Model;
import org.interpss.fadapter.psse.PsseDStabSolver;
import com.interpss.dstab.device.DynamicBusDeviceType;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.cml.field.ICMLControlBlock;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.IntegrationControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock;
import com.interpss.dstab.controller.cml.field.block.PIControlBlock;
import com.interpss.dstab.controller.cml.wrapper.ControllerFieldAnWrapper;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.impl.EConstMachineImpl;
import com.interpss.dstab.mach.impl.MachineImpl;
import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;
import com.interpss.dstab.mach.impl.SalientPoleMachineImpl;
import com.interpss.dstab.mach.impl.DynamicMachineImpl;

/**
 * Opt-in PSS/E-style staggered Euler integration for machine/renewable networks.
 *
 * <p>The STORE recurrence (STORE += h*f; STATE = STORE + h*f/2) is the
 * two-step rule x[n+1] = x[n] + h*(1.5*f[n] - 0.5*f[n-1]). At a network
 * discontinuity the first derivative is averaged with its retained history.
 * The latter behavior is verified by native PSS/E 36.7 fault and VREF steps.
 * Non-windup projection clears outward history at a bound.</p>
 *
 * <p>Supported machines: GENROU, GENSAL and uncontrolled GENCLS. Supported
 * controllers: IEEET1, EXST1/ESST1A, ESST4B, PSS2A, IEEEST, GAST, TGOV1, IEESGO,
 * IEEEG1, GGOV1 and HYGOV. REGCA1/REECA1 bundles use their two-step path.
 * The extended stack starts with zero derivative history; the original
 * IEEE39 controller subset retains its verified initial-derivative history.
 * Renewable event-history parity and full-fleet accuracy remain unverified.
 * ST1 controllers default to one configurable local STORE step per network step with physical
 * and stabilizer inputs held at the old endpoint. Their field outputs are
 * published before the final local algebraic refresh; machine and other
 * controller states retain the common network-step rule.
 * Unsupported
 * dynamic models are rejected at initialization rather than silently omitted.
 * This solver does not replace the default predictor/corrector solver. Select
 * it with {@code algorithm.setSolver(new PsseStaggeredEulerSolver(algorithm))}.
 * Keep the algorithm method MODIFIED_EULER for the underlying derivative API.
 * Use a constant step; reinitialize after changing model attachments. Call
 * {@link #notifyDiscontinuity()} after an external input/reference jump. Events
 * in the network's dynamic-event list are detected automatically.</p>
 *
 * <p>Source: OSTI 604312, section 6.1.3. Native verification and numerical
 * limitations are recorded in docs/benchmarks/ieee39-benchmark-2026-10-15.md.</p>
 */
public class PsseStaggeredEulerSolver extends PsseDStabSolver {
    /**
     * Boundary supplying synchronous-machine stator currents and air-gap power.
     * LAST_NETWORK_INJECTION retains the measurements used to assemble the final
     * network right-hand side. ACCEPTED_VOLTAGE recomputes them after its solve.
     * This is a numerical integration policy, not a performance switch.
     */
    public enum MachineMeasurementStage { LAST_NETWORK_INJECTION, ACCEPTED_VOLTAGE }
    private MachineMeasurementStage machineMeasurementStage = MachineMeasurementStage.LAST_NETWORK_INJECTION;

    private Boolean st1LocalStoreOverride;
    /** Configure the local storage implementation before state initialization. */
    public void setSt1LocalStoreEnabled(boolean enabled) {
        if (!states.isEmpty()) throw new IllegalStateException("Configure before initialization");
        st1LocalStoreOverride=enabled;
    }
    private boolean localStoreEnabled() {
        return st1LocalStoreOverride!=null?st1LocalStoreOverride:!Boolean.getBoolean("ipss.experiment.disableSt1LocalStore");
    }
    private int st1Substeps = IEEE1981ST1Exciter.DEFAULT_LOCAL_SUBSTEPS;
    private final java.util.Map<String, Integer> st1SubstepsByMachine = new java.util.HashMap<>();

    /**
     * Set the default ST1 local STORE steps per network step before initialization.
     * One selects a single local step. Other controller families and the network
     * step are unchanged. Counts from 1 through 128 are accepted without rounding.
     */
    public void setSt1Substeps(int count) {
        requireSubstepConfiguration(count);
        st1Substeps = count;
    }

    /** Override the ST1 count for a machine ID, for example {@code Bus1-mach1}. */
    public void setSt1Substeps(String machineId, int count) {
        requireSubstepConfiguration(count);
        if (machineId == null || machineId.isBlank())
            throw new IllegalArgumentException("A machine ID is required for an ST1 substep override");
        st1SubstepsByMachine.put(machineId, count);
    }

    public int getSt1Substeps() { return st1Substeps; }

    @Override protected int parameterValidationSubsteps(Machine machine) {
        return getSt1Substeps(machine.getId());
    }

    /** Configured override or default; target attachments are checked at initialization. */
    public int getSt1Substeps(String machineId) {
        return st1SubstepsByMachine.getOrDefault(java.util.Objects.requireNonNull(machineId), st1Substeps);
    }

    private void requireSubstepConfiguration(int count) {
        if (previous != null)
            throw new IllegalStateException("Configure ST1 substeps before initialization; create a new solver to change them");
        if (count < 1 || count > IEEE1981ST1Exciter.MAX_LOCAL_SUBSTEPS)
            throw new IllegalArgumentException("ST1 substeps must be between 1 and 128");
    }

    private void validateSubstepTargets() {
        for (String id : st1SubstepsByMachine.keySet()) {
            Machine machine = dstabAlgo.getNetwork().getMachine(id);
            if (machine == null || machine.getExciter() == null
                    || machine.getExciter().getClass() != IEEE1981ST1Exciter.class)
                throw new IllegalArgumentException("ST1 substep override requires an ST1 exciter at machine " + id);
        }
    }

    private final List<Machine> machines = new ArrayList<>();
    private final List<BooleanSupplier> machineActive = new ArrayList<>();
    private final List<BooleanSupplier> attachmentsUnchanged = new ArrayList<>();
    private final List<State> states = new ArrayList<>();
    private final List<LocalSt1Controller> localSt1Controllers = new ArrayList<>();
    private double[] previous;
    private boolean[] wasActive;
    private double fixedStep;
    private boolean inputDiscontinuity;
    private boolean reuseNetworkEndpoint;
    private boolean networkEndpointAvailable;
    private BooleanSupplier ownerActive;
    private boolean extendedStack;
    private boolean runtimeControllerOptimizationEnabled = true;
    private boolean st4EndpointReuseEnabled = true;
    private double[] start;
    private double[] derivative;
    private final List<Participant> participants = new ArrayList<>();
    private final java.util.IdentityHashMap<Object, Participant> participantByController = new java.util.IdentityHashMap<>();
    private final List<Renewable> renewables = new ArrayList<>();
    private final List<Participant> gridForming = new ArrayList<>();
    private boolean electricalEndpointPreparationEnabled;
    private final List<BooleanSupplier> electricalEndpointEligible = new ArrayList<>();
    private long electricalEndpointPreparationCount;
    public void setElectricalEndpointPreparationEnabled(boolean enabled) {
        electricalEndpointPreparationEnabled=enabled;
    }
    public long getElectricalEndpointPreparationCount() { return electricalEndpointPreparationCount; }

    private record State(DoubleSupplier value, DoubleConsumer set,
            DoubleSupplier derivative, DoubleUnaryOperator project, BooleanSupplier active,
            BooleanSupplier frozen) {}
    private record Renewable(Regca1Model model, BooleanSupplier active) {}
    /** ST1's local STORE recurrence uses the count resolved at initialization. */
    private static final class LocalSt1Controller {
        final int substeps;
        final IEEE1981ST1Exciter model;
        final BooleanSupplier active;
        final List<State> states;
        final double[] previous, start, rates;
        final boolean[] frozen;
        final boolean reuseFixedPssOutput;
        final BooleanSupplier useLocalStore;
        boolean wasActive;

        LocalSt1Controller(IEEE1981ST1Exciter model, BooleanSupplier active, List<State> states,
                boolean reuseFixedPssOutput, int substeps,BooleanSupplier useLocalStore) {
            this.substeps = substeps;
            this.useLocalStore=useLocalStore;
            this.reuseFixedPssOutput = reuseFixedPssOutput;
            this.model=model; this.active=active; this.states=List.copyOf(states);
            model.enableDirectEvaluationExperiment();
            previous=new double[states.size()]; start=new double[states.size()];
            rates=new double[states.size()]; frozen=new boolean[states.size()];
            wasActive=active.getAsBoolean();
        }

        private void evaluate() throws DStabSimuException {
            model.evaluateDirectExperiment();
        }

        void advance(double dt, boolean discontinuity) throws DStabSimuException {
            if (!active.getAsBoolean()) {
                java.util.Arrays.fill(previous,0.); wasActive=false; return;
            }
            double localStep=dt/substeps, field=model.getMachine().getEfd();
            Ieee1992PSS2AStabilizer held = reuseFixedPssOutput
                    && model.getMachine().getStabilizer() instanceof Ieee1992PSS2AStabilizer pss ? pss : null;
            if (held != null) held.beginFixedInputObservation();
            model.beginFixedMachineObservation();
            try {
                if(useLocalStore.getAsBoolean()) {
                    field=model.advanceDirectLocalStore(dt,substeps,discontinuity,wasActive,held!=null,previous,start,rates);
                    wasActive=true;
                } else for (int sub=0;sub<substeps;sub++) {
                    evaluate();
                    for (int i=0;i<states.size();i++) {
                        State state=states.get(i);
                        start[i]=state.value.getAsDouble(); rates[i]=state.derivative.getAsDouble();
                        frozen[i]=state.frozen.getAsBoolean();
                        if (!wasActive) previous[i]=rates[i];
                        if (sub==0&&discontinuity) rates[i]=.5*(rates[i]+previous[i]);
                    }
                    wasActive=true;
                    for (int i=0;i<states.size();i++) {
                        State state=states.get(i);
                        if (frozen[i]) { previous[i]=0.; continue; }
                        double trial=start[i]+localStep*(1.5*rates[i]-.5*previous[i]);
                        double end=state.project.applyAsDouble(trial);
                        state.set.accept(end); previous[i]=end!=trial?0.:rates[i];
                    }
                    // Publish before the zero-time regulator's input is refreshed.
                    // Physical machine, network and PSS inputs remain at the old
                    // network endpoint throughout these local steps.
                    field=model.getStaggeredFieldVoltage();
                    evaluate();
                }
            } finally {
                model.endFixedMachineObservation();
                if (held != null) held.endFixedInputObservation();
            }
            if (!Double.isFinite(field))
                throw new DStabSimuException("Non-finite ST1 field voltage: "+model.getId());
            model.getMachine().setEfd(field);
        }
    }
    private static final class Participant {
        final StaggeredEulerParticipant model;
        final BooleanSupplier active;
        final double[] values, rates, history, trial;
        boolean wasActive;
        Participant(StaggeredEulerParticipant model, BooleanSupplier active) {
            this.model=model;this.active=active;
            int size=model.staggeredStateCount();
            values=new double[size];rates=new double[size];history=new double[size];trial=new double[size];
        }
    }

    public PsseStaggeredEulerSolver(DynamicSimuAlgorithm algo) {
        super(algo);
        setNetworkSolutionTolerance(1.e-6);
        setMaxIterationTimes(100);
    }

    /** Select before initialization; the interpreter remains available for numerical controls. */
    public void setRuntimeControllerOptimizationEnabled(boolean enabled) {
        if (previous != null) throw new IllegalStateException("Configure controller evaluation before initialization");
        runtimeControllerOptimizationEnabled = enabled;
    }

    public boolean isRuntimeControllerOptimizationEnabled() {
        return runtimeControllerOptimizationEnabled;
    }

    /** Configure settled ST4B endpoint reuse before initialization. Enabled by default. */
    public void setSt4EndpointReuseEnabled(boolean enabled) {
        if (previous != null) throw new IllegalStateException("Configure ST4B endpoint reuse before initialization");
        st4EndpointReuseEnabled = enabled;
    }

    public boolean isSt4EndpointReuseEnabled() { return st4EndpointReuseEnabled; }

    public double getNetworkSolutionTolerance() { return converge_tol; }
    /** Configure before initialization; changing the boundary requires new derivative history. */
    public void setMachineMeasurementStage(MachineMeasurementStage stage) {
        if (previous != null) throw new IllegalStateException("Configure machine measurement stage before initialization");
        machineMeasurementStage = java.util.Objects.requireNonNull(stage, "machine measurement stage");
    }

    public MachineMeasurementStage getMachineMeasurementStage() { return machineMeasurementStage; }



    /**
     * Opt in to using the preceding solved endpoint for ordinary-step derivatives.
     * Events and a dirty network matrix still force a fresh solve. Call
     * notifyDiscontinuity after any external voltage, input, or activation change.
     * Repeated network solves can advance algebraic device memory, so this selects
     * a numerical lifecycle rather than merely caching an interchangeable result.
     */
    public void setReuseNetworkEndpoint(boolean enabled) { reuseNetworkEndpoint = enabled; }
    public boolean isReuseNetworkEndpoint() { return reuseNetworkEndpoint; }
    public int getBoundMachineCount() { return machines.size(); }
    public int getBoundRenewableCount() { return renewables.size()+gridForming.size(); }
    /** Bound machine/controller/grid-forming states; excludes REGCA bundle-owned states. */
    public int getBoundDifferentialStateCount() {
        return states.size()+participants.stream().mapToInt(p->p.values.length).sum()
                +localSt1Controllers.stream().mapToInt(c->c.states.size()).sum();
    }

    public void setNetworkSolutionTolerance(double tolerance) {
        if (!Double.isFinite(tolerance) || tolerance <= 0.)
            throw new IllegalArgumentException("Network tolerance must be finite and positive");
        converge_tol = tolerance;
    }

    /** Notify an externally applied discontinuous input without advancing time. */
    public void notifyDiscontinuity() { inputDiscontinuity = true; }

    @Override public boolean initialization() {
        machines.clear();
        electricalEndpointEligible.clear(); electricalEndpointPreparationCount=0; machineActive.clear(); attachmentsUnchanged.clear(); states.clear();
        localSt1Controllers.clear();
        participants.clear(); participantByController.clear(); renewables.clear(); gridForming.clear(); extendedStack=false;
        previous = null; wasActive = null; inputDiscontinuity = false;
        networkEndpointAvailable = false;
        fixedStep = dstabAlgo.getSimuStepSec();
        requireStep(fixedStep);
        validateSubstepTargets();
        validateModels();
        if (!super.initialization()) return false;
        dstabAlgo.getNetwork().setPolarCoordinate(false);
        bind();
        try {
            networkSolutionStep();
            evaluate(true);
            for (int i=0;i<states.size();i++) {
                previous[i]=extendedStack ? 0.0 : states.get(i).derivative.getAsDouble();
                wasActive[i]=states.get(i).active.getAsBoolean();
            }
            for (var p:participants) p.wasActive=p.active.getAsBoolean();
        } catch (DStabSimuException e) {
            throw new IllegalStateException("Could not initialize staggered-Euler history", e);
        }
        return true;
    }

    private static void requireStep(double step) {
        if (!Double.isFinite(step) || step <= 0.)
            throw new IllegalArgumentException("Integration step must be finite and positive");
    }

    @Override public boolean networkSolutionStep() throws DStabSimuException {
        networkEndpointAvailable = false;
        if (!super.networkSolutionStep())
            throw new DStabSimuException("Network did not converge to " + converge_tol
                    + " at " + dstabAlgo.getSimuTime());
        // Injection evaluation precedes the linear solve. The selected boundary
        // must remain the same for ordinary solves, events and endpoint reuse.
        // Refreshing here changes controller inputs and electrical derivatives;
        // it is not interchangeable with retaining the last injection boundary.
        if (machineMeasurementStage == MachineMeasurementStage.ACCEPTED_VOLTAGE)
            for (int i = 0; i < machines.size(); i++)
                if (machineActive.get(i).getAsBoolean()) machines.get(i).getIxy();
        networkEndpointAvailable = true;
        return true;
    }

    private static void validateController(Object c) {
        if (c == null) return;
        Class<?> type = c.getClass();
        if (type != Ieee1968Type1Exciter.class && type != PsseGASTGasTurGovernor.class
                && type != PsseTGov1SteamTurGovernor.class && type != PsseIEESGOSteamTurGovernor.class
                && type != IeeeSteamTCDRGovernor.class && type != PsseGgov1Governor.class
                && type != PsseHygovGovernor.class && type != IEEE1981ST1Exciter.class
                && type != IEEE2005ST4BExciter.class && type != Ieee1992PSS2AStabilizer.class
                && type != IeeestStabilizer.class)
            throw new IllegalArgumentException("Unsupported staggered-Euler controller: " + type.getName());
    }

    private void validateModels() {
        for (int runtimeIndex0=0; runtimeIndex0<dstabAlgo.getNetwork().getBusList().size(); runtimeIndex0++) {
            var bus=dstabAlgo.getNetwork().getBusList().get(runtimeIndex0);
            for (var device : bus.getDynamicBusDeviceList())
                if (device.getDeviceType() != DynamicBusDeviceType.DYNAMIC_GENERATOR)
                    throw new IllegalArgumentException("Unsupported dynamic bus device: " + device.getId());
            for (var gen : bus.getContributeGenList()) {
                if (!bus.isActive() || !gen.isActive()) continue;
                // The engine explicitly represents allowed generators without a
                // dynamic model as static negative loads during initialization.
                if (gen.getDynamicGenDevice()==null&&dstabAlgo.getNetwork().isAllowGenWithoutMach()) continue;
                if (gen.getDynamicGenDevice() instanceof Regfma1Model r) {
                    if (r.getPlantController()!=null)
                        throw new IllegalArgumentException("Staggered REGFMA1 plant-controller coupling is not supported: "+gen.getId());
                    continue;
                }
                if (gen.getDynamicGenDevice() instanceof Regca1Model r) {
                    if (r.getReeca1Controller()==null)
                        throw new IllegalArgumentException("Staggered REGCA1 requires REECA1: "+gen.getId());
                    continue;
                }
                if (!(gen.getDynamicGenDevice() instanceof Machine m)
                        || (!(m instanceof EConstMachineImpl) && !(m instanceof SalientPoleMachineImpl)))
                    throw new IllegalArgumentException("Unsupported staggered-Euler generator: " + gen.getId());
                validateController(m.getExciter());
                validateController(m.getGovernor());
                validateController(m.getStabilizer());
                if (m instanceof EConstMachineImpl && (m.hasExciter() || m.hasGovernor() || m.hasStabilizer()))
                    throw new IllegalArgumentException("GENCLS controllers are not supported by this solver");
            }
        }
        for (var branch : dstabAlgo.getNetwork().getBranchList())
            if (!branch.getDynamicBranchDeviceList().isEmpty())
                throw new IllegalArgumentException("Unsupported dynamic branch device: " + branch.getId());
    }

    private void state(DoubleSupplier value, DoubleConsumer set, DoubleSupplier derivative) {
        states.add(new State(value,set,derivative,x->x,ownerActive,()->false));
    }

    private void controller(Object controller) {
        if (controller == null) return;
        if (controller instanceof StaggeredEulerParticipant model) {
            extendedStack=true;
            if (controller instanceof AbstractAnnotateController annotated)
                annotated.setRuntimeExpressionBindingsEnabled(runtimeControllerOptimizationEnabled);
            BooleanSupplier owner=ownerActive;
            BooleanSupplier active=controller instanceof PsseGgov1Governor g
                    ? ()->owner.getAsBoolean()&&g.isActive()
                    : controller instanceof IeeestStabilizer pss
                    ? ()->owner.getAsBoolean()&&pss.isActive()
                    : ()->owner.getAsBoolean()&&((PsseHygovGovernor)controller).isActive();
            Participant participant=new Participant(model,active);
            participants.add(participant);participantByController.put(controller,participant);
            return;
        }
        if (!(controller instanceof AbstractAnnotateController c))
            throw new IllegalArgumentException("Unsupported controller " + controller.getClass());
        c.setRuntimeExpressionBindingsEnabled(runtimeControllerOptimizationEnabled);
        if(c instanceof IEEE2005ST4BExciter st4)st4.enableDirectEvaluationExperiment();
        if(c instanceof Ieee1992PSS2AStabilizer pss)pss.enableDirectEvaluationExperiment();
        if (controller instanceof IEEE2005ST4BExciter || controller instanceof IEEE1981ST1Exciter
                || controller instanceof Ieee1992PSS2AStabilizer) extendedStack=true;
        BooleanSupplier owner=ownerActive;
        ownerActive=()->owner.getAsBoolean()&&c.isActive();
        int firstState=states.size();
        for (var wrapper:c.getFieldWrapperList()) {
            if (wrapper instanceof ControllerFieldAnWrapper child) {
                controller(child.getField());
                continue;
            }
            if (!(wrapper.getField() instanceof ICMLControlBlock)) continue;
            if (wrapper.getField() instanceof FilterNthOrderBlock compound) {
                for (var block:compound.getIntegrationBlocks()) block(block,wrapper.getFieldName());
            } else if (wrapper.getField() instanceof Pss2aLeadLagBlock custom) {
                state(custom::getStateX,custom::setStateX,custom::getStateDerivative);
            } else if (wrapper.getField() instanceof CMLControlBlock1stOrderAdapter b) {
                block(b,wrapper.getFieldName());
            } else throw new IllegalArgumentException("Unsupported block " + wrapper.getFieldName());
        }
        if (controller instanceof IEEE1981ST1Exciter st1) {
            localSt1Controllers.add(new LocalSt1Controller(st1,ownerActive,
                    states.subList(firstState,states.size()), runtimeControllerOptimizationEnabled,
                    getSt1Substeps(st1.getMachine().getId()),this::localStoreEnabled));
            states.subList(firstState,states.size()).clear();
        }
        ownerActive=owner;
    }

    private void block(CMLControlBlock1stOrderAdapter b,String name) {
            DoubleUnaryOperator project=x->x;
            BooleanSupplier frozen=()->false;
            if (b.getType()==StaticBlockType.NonWindup) {
                if (b instanceof DelayControlBlock delay) project=x->delay.getLimit().limit(x);
                else if (b instanceof Esst4bPIControlBlock pi) {
                    project=pi::projectIntegralState;
                    frozen=pi::isIntegratorFrozen;
                }
                else if (b instanceof IntegrationControlBlock integral) project=x->integral.getLimit().limit(x);
                else if (b instanceof FreezeNonWindupPIControlBlock pi) {
                    frozen=()-> {
                        double output=pi.getStateX()+pi.getKp()*pi.getU();
                        double rate=pi.getKi()*pi.getU();
                        return rate>0.0&&output>=pi.getLimit().getMax()
                                ||rate<0.0&&output<=pi.getLimit().getMin();
                    };
                } else if (b instanceof PIControlBlock pi)
                    project=x->pi.getLimit().limit(x+pi.getKp()*pi.getU())-pi.getKp()*pi.getU();
                else throw new IllegalArgumentException("Unsupported non-windup block " + name);
            }
            states.add(new State(b::getStateX,b::setStateX,b::getDX_dt,project,ownerActive,frozen));
    }

    private void bind() {
        for (int runtimeIndex1=0; runtimeIndex1<dstabAlgo.getNetwork().getBusList().size(); runtimeIndex1++) {
            var bus=dstabAlgo.getNetwork().getBusList().get(runtimeIndex1);

            for (var gen:bus.getContributeGenList()) {
                if (!bus.isActive() || !gen.isActive()) {
                    attachmentsUnchanged.add(()->!bus.isActive()||!gen.isActive());
                    continue;
                }
                if (gen.getDynamicGenDevice()==null&&dstabAlgo.getNetwork().isAllowGenWithoutMach()) {
                    attachmentsUnchanged.add(()->gen.getDynamicGenDevice()==null);
                    continue;
                }
                if (gen.getDynamicGenDevice() instanceof Regfma1Model r) {
                    extendedStack=true;
                    Participant participant=new Participant(r,()->bus.isActive()&&gen.isActive()&&r.isActive());
                    participants.add(participant);
                    gridForming.add(participant);
                    attachmentsUnchanged.add(()->gen.getDynamicGenDevice()==r&&r.getPlantController()==null);
                    continue;
                }
                if (gen.getDynamicGenDevice() instanceof Regca1Model r) {
                    extendedStack=true;
                    r.setIntegrationScheme(Regca1Model.IntegrationScheme.TWO_STEP);
                    renewables.add(new Renewable(r,()->bus.isActive()&&gen.isActive()&&r.isActive()));
                    var electrical=r.getReeca1Controller();
                    var plant=electrical.getPlantController();var wind=electrical.getWindControlStack();
                    attachmentsUnchanged.add(()->gen.getDynamicGenDevice()==r
                            &&r.getReeca1Controller()==electrical&&electrical.getPlantController()==plant
                            &&electrical.getWindControlStack()==wind);
                    continue;
                }
                if (!(gen.getDynamicGenDevice() instanceof MachineImpl m))
                    throw new IllegalArgumentException("Unsupported generator " + gen.getId());
                if (!(m instanceof EConstMachineImpl) && !(m instanceof SalientPoleMachineImpl))
                    throw new IllegalArgumentException("Unsupported machine " + m.getClass());
                machines.add(m);
                var exciter=m.getExciter(); var governor=m.getGovernor(); var stabilizer=m.getStabilizer();
                attachmentsUnchanged.add(() -> gen.getDynamicGenDevice()==m
                        && m.getExciter()==exciter && m.getGovernor()==governor && m.getStabilizer()==stabilizer);
                ownerActive = () -> bus.isActive() && gen.isActive() && m.isActive();
                machineActive.add(ownerActive);
                state(m::getSpeed,m::setSpeed,()->m.captureMechanicalIntegrationState().speedDerivative());
                state(m::getAngle,m::setAngle,()->m.captureMechanicalIntegrationState().angleDerivative());
                if (m instanceof RoundRotorMachineImpl r) {
                    state(r::getEq1,r::setEq1,()->r.captureRoundElectricalState().dx3Eq1());
                    state(r::getPsikd,r::setPsikd,()->r.captureRoundElectricalState().dx4Psid1());
                    state(r::getPsikq,r::setPsikq,()->r.captureRoundElectricalState().dx5Psiq1());
                    state(r::getEd1,r::setEd1,()->r.captureRoundElectricalState().dx6Ed1());
                } else if (m instanceof SalientPoleMachineImpl s) {
                    extendedStack=true;
                    state(s::getEq1,s::setEq1,()->s.captureSalientElectricalState().dx3Eq1());
                    state(s::getPsikd,s::setPsikd,()->s.captureSalientElectricalState().dx4Psid1());
                    state(s::getPsiq11,s::setPsiq11,()->s.captureSalientElectricalState().dx5Psiq11());
                }
                controller(m.getExciter()); controller(m.getGovernor()); controller(m.getStabilizer());
                electricalEndpointEligible.add(endpointEligibility(m,exciter,governor,stabilizer));
            }
        }
        previous=new double[states.size()];
        wasActive=new boolean[states.size()];
        start=new double[states.size()]; derivative=new double[states.size()];
    }

    @Override
    public void beforeStep(double time) throws DStabSimuException {
        // The enclosing solve has already identified this event. Capture its
        // settled left endpoint before the event processor changes the network.
        if (eventBoundaryThisStep && !dstabAlgo.isDisableDynamicEvent())
            for (var renewable : renewables)
                if (renewable.active.getAsBoolean())
                    renewable.model.captureNetworkEventLeftBoundary();
        super.beforeStep(time);
    }

    private void evaluate() throws DStabSimuException { evaluate(false); }

    private void evaluate(boolean initialEndpoint) throws DStabSimuException {
        evaluate(initialEndpoint,false);
    }

    private BooleanSupplier endpointEligibility(MachineImpl machine, Object exciter, Object governor, Object stabilizer) {
        Class<?> type=machine.getClass();
        if (type!=RoundRotorMachineImpl.class && type!=SalientPoleMachineImpl.class
                && type!=org.interpss.dstab.mach.IeeeVcRoundRotorMachine.class
                && type!=org.interpss.dstab.mach.IeeeVcSalientPoleMachine.class
                || governor!=null && governor.getClass()!=PsseGgov1Governor.class
                || stabilizer!=null && stabilizer.getClass()!=Ieee1992PSS2AStabilizer.class)
            return ()->false;
        BooleanSupplier exciterReady;
        if (exciter instanceof IEEE2005ST4BExciter st4)
            exciterReady=frozenControllerReady(st4, true);
        else if (exciter instanceof IEEE1981ST1Exciter st1) {
            var local=localSt1Controllers.stream().filter(c->c.model==st1).findFirst().orElse(null);
            if(local==null)return ()->false;
            BooleanSupplier ready=frozenControllerReady(st1,false);
            exciterReady=()->local.wasActive && localStoreEnabled()
                    && ready.getAsBoolean();
        } else return ()->false;
        return ()->{
            if (!exciterReady.getAsBoolean() || machine.eNotificationRequired()
                    || machine.getDStabBus().getClass()!=com.interpss.dstab.impl.DStabBusImpl.class
                    || ((com.interpss.dstab.impl.DStabBusImpl)machine.getDStabBus()).eNotificationRequired())return false;
            if (governor instanceof PsseGgov1Governor g && (g.eNotificationRequired()
                    || g.getData().getClass()!=org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1GovernorData.class))return false;
            if (stabilizer instanceof Ieee1992PSS2AStabilizer pss) {
                if (pss.eNotificationRequired()
                        || pss.getData().getClass()!=org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizerData.class)return false;
                int first=pss.getData().getIcs1(), second=pss.getData().getIcs2();
                if (first==4 || second==4 || first==6 || second==6)return false;
            }
            return true;
        };
    }

    /** Experimental guard for the pinned direct-controller implementations. */
    private BooleanSupplier frozenControllerReady(AbstractAnnotateController controller, boolean fixedLimits) {
        Class<?> expected=fixedLimits?IEEE2005ST4BExciter.class:IEEE1981ST1Exciter.class;
        if(controller.getClass()!=expected || controller.getFieldWrapperList()==null)return ()->false;
        var wrappers=java.util.List.copyOf(controller.getFieldWrapperList());
        Object[] fields=new Object[wrappers.size()];
        for(int i=0;i<fields.length;i++)fields[i]=wrappers.get(i).getField();
        return ()->{
            if(controller.eNotificationRequired() || controller.getFieldWrapperList().size()!=wrappers.size())return false;
            for(int i=0;i<fields.length;i++) {
                if(controller.getFieldWrapperList().get(i)!=wrappers.get(i) || wrappers.get(i).getField()!=fields[i])return false;
                if(fixedLimits && fields[i] instanceof com.interpss.dstab.controller.cml.field.ICMLLimitBlock block
                        && block.getLimit()!=null && (block.getLimit().getClass()!=com.interpss.dstab.datatype.LimitExpression.class
                        || block.getLimit().getMaxExp()!=null || block.getLimit().getMinExp()!=null))return false;
            }
            return true;
        };
    }

    private void evaluate(boolean initialEndpoint, boolean prepareElectrical) throws DStabSimuException {
        for (var p:gridForming)
            if (p.active.getAsBoolean()) p.model.evaluateStaggered(fixedStep,p.rates,0);
        for (var r:renewables)
            if (r.active.getAsBoolean()&&!r.model.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,0))
                throw new DStabSimuException("Renewable boundary refresh failed: "+r.model.getId());
        for (int i=0;i<machines.size();i++) {
            Machine m=machines.get(i);
            if (!machineActive.get(i).getAsBoolean()) continue;
            if(prepareElectrical && extendedStack && electricalEndpointPreparationEnabled
                    && electricalEndpointEligible.get(i).getAsBoolean()) {
                if(!((DynamicMachineImpl)m).nextStepElectrical(0.,DynamicSimuMethod.MODIFIED_EULER,dstabAlgo.getNetwork(),0))
                    throw new DStabSimuException("Electrical endpoint preparation failed: "+m.getId());
                electricalEndpointPreparationCount++;
                continue;
            }

            if (!extendedStack) {
                if (!m.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,0))
                    throw new DStabSimuException("Derivative evaluation failed: " + m.getId());
                continue;
            }
            // Publish algebraic controller outputs at this same state/network
            // endpoint before evaluating the machine's electrical derivatives.
            if (m.hasStabilizer()&&m.getStabilizer().isActive()) {
                Participant p=participantByController.get(m.getStabilizer());
                if (p!=null) p.model.evaluateStaggered(fixedStep,p.rates,0);
                else if (!m.getStabilizer().nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,m,0))
                    throw new DStabSimuException("Stabilizer derivative failed: "+m.getId());
            }
            if (m.hasExciter()&&m.getExciter().isActive()) {
                // PSS state and physical inputs stay fixed while this AVR is
                // evaluated. Release before governor or machine updates below.
                Ieee1992PSS2AStabilizer held = runtimeControllerOptimizationEnabled
                        && m.getStabilizer() instanceof Ieee1992PSS2AStabilizer pss ? pss : null;
                if (held != null) held.beginFixedInputObservation();
                try {
                    if (runtimeControllerOptimizationEnabled && st4EndpointReuseEnabled
                            && m.getExciter() instanceof IEEE2005ST4BExciter st4) {
                        m.setEfd(st4.evaluateFixedInputEndpoint(m, held != null));
                    } else {
                        if (!m.getExciter().nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,m,0))
                            throw new DStabSimuException("Exciter derivative failed: "+m.getId());
                        if (initialEndpoint || !(m.getExciter() instanceof IEEE1981ST1Exciter))
                            m.setEfd(m.getExciter().getOutput(m));
                    }
                } finally {
                    if (held != null) held.endFixedInputObservation();
                }
            }
            if (m.hasGovernor()&&m.getGovernor().isActive()) {
                Participant p=participantByController.get(m.getGovernor());
                if (p!=null) p.model.evaluateStaggered(fixedStep,p.rates,0);
                else if (!m.getGovernor().nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,m,0))
                    throw new DStabSimuException("Governor derivative failed: "+m.getId());
                m.setPm(m.getGovernor().getOutput(m));
            }
            // Classical machines have mechanical states and constant internal
            // voltage; they do not inherit the electrical-state implementation.
            if (!m.nextStepMechanical(0.,DynamicSimuMethod.MODIFIED_EULER,dstabAlgo.getNetwork(),0)
                    ||m instanceof DynamicMachineImpl dynamic
                        &&!dynamic.nextStepElectrical(0.,DynamicSimuMethod.MODIFIED_EULER,
                                dstabAlgo.getNetwork(),0))
                throw new DStabSimuException("Machine derivative failed: "+m.getId());
        }
    }

    @Override public void nextStep(double time,double dt,DynamicSimuMethod method) throws DStabSimuException {
        requireStep(dt);
        if (previous == null) throw new IllegalStateException("Initialize the solver before advancing");
        if (Double.compare(dt, fixedStep) != 0)
            throw new IllegalArgumentException("Staggered Euler requires a fixed integration step");
        if (method != DynamicSimuMethod.MODIFIED_EULER)
            throw new IllegalArgumentException("Set the model derivative method to MODIFIED_EULER");
        for (var unchanged:attachmentsUnchanged)
            if (!unchanged.getAsBoolean())
                throw new IllegalStateException("Reinitialize after changing dynamic model attachments");
        timestep=dt;
        boolean discontinuity=eventBoundaryThisStep || inputDiscontinuity;
        boolean reuse = reuseNetworkEndpoint && networkEndpointAvailable && !discontinuity
                && !dstabAlgo.getNetwork().isYMatrixDirty();
        networkEndpointAvailable = false;
        if (discontinuity) for (var r:renewables)
            if (r.active.getAsBoolean()) r.model.rebaseNextTwoStep();
        if (!reuse) {
            // The event-angle rebase belongs to this zero-time initial solve,
            // before any bus-frequency or controller state advances.
            String previousStage = networkSolutionStage;
            networkSolutionStage = "INITIAL";
            try { networkSolutionStep(); }
            finally { networkSolutionStage = previousStage; }
        }
        networkEndpointAvailable = false;
        if (discontinuity) for (var p:participants)
            if (p.active.getAsBoolean() && p.model instanceof IeeestStabilizer stabilizer)
                stabilizer.synchronizeVoltageDerivativeMemory();
        if (discontinuity) rebaseDynamicModelInputs(method);
        evaluate();
        for (int i=0;i<states.size();i++) {
            start[i]=states.get(i).value.getAsDouble();
            derivative[i]=states.get(i).derivative.getAsDouble();
            boolean active = states.get(i).active.getAsBoolean();
            if (active && !wasActive[i]) previous[i]=derivative[i];
            wasActive[i]=active;
            if (discontinuity) derivative[i]=.5*(derivative[i]+previous[i]);
        }
        for (var p:participants) {
            p.model.readStaggeredState(p.values,0);
            boolean active=p.active.getAsBoolean();
            if (active&&!p.wasActive) System.arraycopy(p.rates,0,p.history,0,p.rates.length);
            p.wasActive=active;
            if (discontinuity && p.model.averageStaggeredRatesAtDiscontinuity())
                for (int i=0;i<p.rates.length;i++) p.rates[i]=.5*(p.rates[i]+p.history[i]);
        }
        // Advance ST1 while its physical and upstream controller inputs still
        // belong to the old endpoint. Global machine rates are already captured.
        for (var controller:localSt1Controllers) controller.advance(dt,discontinuity);
        for (var bus:dstabAlgo.getNetwork().getBusList())
            if (bus.isActive()) bus.nextStep(dt,method,0);
        // Renewable derivatives observe the old synchronous-machine and network
        // endpoint. Their bundle advances only after all other rates are captured.
        for (var r:renewables)
            if (r.active.getAsBoolean()&&!r.model.nextStep(dt,method,0))
                throw new DStabSimuException("Renewable integration failed: "+r.model.getId());
        for (int i=0;i<states.size();i++) {
            State s=states.get(i);
            if (!wasActive[i]) { previous[i]=0.; continue; }
            if (s.frozen.getAsBoolean()) { previous[i]=0.; continue; }
            double trial=start[i]+dt*(1.5*derivative[i]-.5*previous[i]);
            double end=s.project.applyAsDouble(trial);
            s.set.accept(end);
            previous[i]=end!=trial ? 0. : derivative[i];
        }
        for (var p:participants) {
            if (!p.wasActive) { java.util.Arrays.fill(p.history,0.);continue; }
            for (int i=0;i<p.trial.length;i++)
                p.trial[i]=p.values[i]+dt*(1.5*p.rates[i]-.5*p.history[i]);
            p.model.applyStaggeredState(p.trial,0,dt);
            p.model.readStaggeredState(p.values,0);
            for (int i=0;i<p.trial.length;i++) p.history[i]=p.values[i]!=p.trial[i]?0.:p.rates[i];
        }
        for (var p:participants)
            if (p.wasActive) p.model.completeStaggeredStep(dt);
        // Refresh model algebraic outputs/caches without advancing any state.
        evaluate(false,true); evaluate();
        networkSolutionStep();
        networkEndpointAvailable = false;
        // Zero-time algebraic blocks (for example IEEET1 with TR=0) must report
        // the solved endpoint voltage, not the previous network solution.
        evaluate();
        for (var p:participants)
            if (p.active.getAsBoolean() && p.model instanceof IeeestStabilizer stabilizer)
                stabilizer.acceptVoltageDerivativeEndpoint(time+dt,dt);
        for (var bus:dstabAlgo.getNetwork().getBusList())
            if (bus.isActive()) bus.nextStep(dt,method,1);
        inputDiscontinuity=false;
        networkEndpointAvailable = true;
    }
}
