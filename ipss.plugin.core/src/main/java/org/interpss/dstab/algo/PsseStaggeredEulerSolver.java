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
import org.interpss.dstab.control.util.FreezeNonWindupPIControlBlock;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import org.interpss.dstab.renewable.Regca1Model;
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
 * controllers: IEEET1, EXST1/ESST1A, ESST4B, PSS2A, GAST, TGOV1, IEESGO,
 * IEEEG1, GGOV1 and HYGOV. REGCA1/REECA1 bundles use their two-step path.
 * The extended stack starts with zero derivative history; the original
 * IEEE39 controller subset retains its verified initial-derivative history.
 * Renewable event-history parity and full-fleet accuracy remain unverified.
 * ST1 controllers use ten local STORE steps per network step with physical
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
    private final List<Machine> machines = new ArrayList<>();
    private final List<BooleanSupplier> machineActive = new ArrayList<>();
    private final List<BooleanSupplier> attachmentsUnchanged = new ArrayList<>();
    private final List<State> states = new ArrayList<>();
    private final List<LocalSt1Controller> localSt1Controllers = new ArrayList<>();
    private double[] previous;
    private boolean[] wasActive;
    private double fixedStep;
    private boolean inputDiscontinuity;
    private BooleanSupplier ownerActive;
    private boolean extendedStack;
    private double[] start;
    private double[] derivative;
    private final List<Participant> participants = new ArrayList<>();
    private final java.util.IdentityHashMap<Object, Participant> participantByController = new java.util.IdentityHashMap<>();
    private final List<Renewable> renewables = new ArrayList<>();
    private record State(DoubleSupplier value, DoubleConsumer set,
            DoubleSupplier derivative, DoubleUnaryOperator project, BooleanSupplier active,
            BooleanSupplier frozen) {}
    private record Renewable(Regca1Model model, BooleanSupplier active) {}
    /** ST1's local STORE recurrence uses ten steps per network step. */
    private static final class LocalSt1Controller {
        private static final int SUBSTEPS = 10;
        final IEEE1981ST1Exciter model;
        final BooleanSupplier active;
        final List<State> states;
        final double[] previous, start, rates;
        final boolean[] frozen;
        boolean wasActive;

        LocalSt1Controller(IEEE1981ST1Exciter model, BooleanSupplier active, List<State> states) {
            this.model=model; this.active=active; this.states=List.copyOf(states);
            previous=new double[states.size()]; start=new double[states.size()];
            rates=new double[states.size()]; frozen=new boolean[states.size()];
            wasActive=active.getAsBoolean();
        }

        private void evaluate() throws DStabSimuException {
            if (!model.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,model.getMachine(),0))
                throw new DStabSimuException("ST1 local evaluation failed: "+model.getId());
        }

        void advance(double dt, boolean discontinuity) throws DStabSimuException {
            if (!active.getAsBoolean()) {
                java.util.Arrays.fill(previous,0.); wasActive=false; return;
            }
            double localStep=dt/SUBSTEPS, field=model.getMachine().getEfd();
            for (int sub=0;sub<SUBSTEPS;sub++) {
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

    public double getNetworkSolutionTolerance() { return converge_tol; }
    public int getBoundMachineCount() { return machines.size(); }
    public int getBoundRenewableCount() { return renewables.size(); }
    /** Bound machine/controller states; excludes renewable bundle-owned states. */
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
        machines.clear(); machineActive.clear(); attachmentsUnchanged.clear(); states.clear();
        localSt1Controllers.clear();
        participants.clear(); participantByController.clear(); renewables.clear(); extendedStack=false;
        previous = null; wasActive = null; inputDiscontinuity = false;
        fixedStep = dstabAlgo.getSimuStepSec();
        requireStep(fixedStep);
        validateModels();
        if (!super.initialization()) return false;
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
        if (!super.networkSolutionStep())
            throw new DStabSimuException("Network did not converge to " + converge_tol
                    + " at " + dstabAlgo.getSimuTime());
        return true;
    }

    private static void validateController(Object c) {
        if (c == null) return;
        Class<?> type = c.getClass();
        if (type != Ieee1968Type1Exciter.class && type != PsseGASTGasTurGovernor.class
                && type != PsseTGov1SteamTurGovernor.class && type != PsseIEESGOSteamTurGovernor.class
                && type != IeeeSteamTCDRGovernor.class && type != PsseGgov1Governor.class
                && type != PsseHygovGovernor.class && type != IEEE1981ST1Exciter.class
                && type != IEEE2005ST4BExciter.class && type != Ieee1992PSS2AStabilizer.class)
            throw new IllegalArgumentException("Unsupported staggered-Euler controller: " + type.getName());
    }

    private void validateModels() {
        for (var bus : dstabAlgo.getNetwork().getBusList()) {
            for (var device : bus.getDynamicBusDeviceList())
                if (device.getDeviceType() != DynamicBusDeviceType.DYNAMIC_GENERATOR)
                    throw new IllegalArgumentException("Unsupported dynamic bus device: " + device.getId());
            for (var gen : bus.getContributeGenList()) {
                if (!bus.isActive() || !gen.isActive()) continue;
                // The engine explicitly represents allowed generators without a
                // dynamic model as static negative loads during initialization.
                if (gen.getDynamicGenDevice()==null&&dstabAlgo.getNetwork().isAllowGenWithoutMach()) continue;
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
            BooleanSupplier owner=ownerActive;
            BooleanSupplier active=controller instanceof PsseGgov1Governor g
                    ? ()->owner.getAsBoolean()&&g.isActive()
                    : ()->owner.getAsBoolean()&&((PsseHygovGovernor)controller).isActive();
            Participant participant=new Participant(model,active);
            participants.add(participant);participantByController.put(controller,participant);
            return;
        }
        if (!(controller instanceof AbstractAnnotateController c))
            throw new IllegalArgumentException("Unsupported controller " + controller.getClass());
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
                    states.subList(firstState,states.size())));
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
        for (var bus:dstabAlgo.getNetwork().getBusList()) {

            for (var gen:bus.getContributeGenList()) {
                if (!bus.isActive() || !gen.isActive()) {
                    attachmentsUnchanged.add(()->!bus.isActive()||!gen.isActive());
                    continue;
                }
                if (gen.getDynamicGenDevice()==null&&dstabAlgo.getNetwork().isAllowGenWithoutMach()) {
                    attachmentsUnchanged.add(()->gen.getDynamicGenDevice()==null);
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
            }
        }
        previous=new double[states.size()];
        wasActive=new boolean[states.size()];
        start=new double[states.size()]; derivative=new double[states.size()];
    }

    private void evaluate() throws DStabSimuException { evaluate(false); }

    private void evaluate(boolean initialEndpoint) throws DStabSimuException {
        for (var r:renewables)
            if (r.active.getAsBoolean()&&!r.model.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,0))
                throw new DStabSimuException("Renewable boundary refresh failed: "+r.model.getId());
        for (int i=0;i<machines.size();i++) {
            Machine m=machines.get(i);
            if (!machineActive.get(i).getAsBoolean()) continue;
            if (!extendedStack) {
                if (!m.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,0))
                    throw new DStabSimuException("Derivative evaluation failed: " + m.getId());
                continue;
            }
            // Publish algebraic controller outputs at this same state/network
            // endpoint before evaluating the machine's electrical derivatives.
            if (m.hasStabilizer()&&m.getStabilizer().isActive()
                    &&!m.getStabilizer().nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,m,0))
                throw new DStabSimuException("Stabilizer derivative failed: "+m.getId());
            if (m.hasExciter()&&m.getExciter().isActive()) {
                if (!m.getExciter().nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,m,0))
                    throw new DStabSimuException("Exciter derivative failed: "+m.getId());
                if (initialEndpoint || !(m.getExciter() instanceof IEEE1981ST1Exciter))
                    m.setEfd(m.getExciter().getOutput(m));
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
        networkSolutionStep();
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
            if (discontinuity)
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
        evaluate(); evaluate();
        networkSolutionStep();
        // Zero-time algebraic blocks (for example IEEET1 with TR=0) must report
        // the solved endpoint voltage, not the previous network solution.
        evaluate();
        for (var bus:dstabAlgo.getNetwork().getBusList())
            if (bus.isActive()) bus.nextStep(dt,method,1);
        inputDiscontinuity=false;
    }
}
