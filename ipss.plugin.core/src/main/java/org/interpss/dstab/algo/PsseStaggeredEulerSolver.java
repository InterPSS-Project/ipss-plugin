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
import com.interpss.dstab.device.DynamicBusDeviceType;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.defaultImpl.DStabSolverImpl;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.cml.field.ICMLControlBlock;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.IntegrationControlBlock;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.impl.EConstMachineImpl;
import com.interpss.dstab.mach.impl.MachineImpl;
import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;

/**
 * Opt-in PSS/E-style staggered Euler integration for GENROU/GENCLS networks.
 *
 * <p>The STORE recurrence (STORE += h*f; STATE = STORE + h*f/2) is the
 * two-step rule x[n+1] = x[n] + h*(1.5*f[n] - 0.5*f[n-1]). At a network
 * discontinuity the first derivative is averaged with its retained history.
 * The latter behavior is verified by native PSS/E 36.7 fault and VREF steps.
 * Non-windup projection clears outward history at a bound.</p>
 *
 * <p>Supported controllers: IEEET1, GAST, TGOV1, IEESGO and IEEEG1. Unsupported
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
public class PsseStaggeredEulerSolver extends DStabSolverImpl {
    private final List<Machine> machines = new ArrayList<>();
    private final List<BooleanSupplier> machineActive = new ArrayList<>();
    private final List<BooleanSupplier> attachmentsUnchanged = new ArrayList<>();
    private final List<State> states = new ArrayList<>();
    private double[] previous;
    private boolean[] wasActive;
    private double fixedStep;
    private boolean inputDiscontinuity;
    private BooleanSupplier ownerActive;
    private record State(DoubleSupplier value, DoubleConsumer set,
            DoubleSupplier derivative, DoubleUnaryOperator project, BooleanSupplier active) {}

    public PsseStaggeredEulerSolver(DynamicSimuAlgorithm algo) {
        super(algo);
        setNetworkSolutionTolerance(1.e-6);
        setMaxIterationTimes(100);
    }

    public double getNetworkSolutionTolerance() { return converge_tol; }

    public void setNetworkSolutionTolerance(double tolerance) {
        if (!Double.isFinite(tolerance) || tolerance <= 0.)
            throw new IllegalArgumentException("Network tolerance must be finite and positive");
        converge_tol = tolerance;
    }

    /** Notify an externally applied discontinuous input without advancing time. */
    public void notifyDiscontinuity() { inputDiscontinuity = true; }

    @Override public boolean initialization() {
        machines.clear(); machineActive.clear(); attachmentsUnchanged.clear(); states.clear();
        previous = null; wasActive = null; inputDiscontinuity = false;
        fixedStep = dstabAlgo.getSimuStepSec();
        requireStep(fixedStep);
        validateModels();
        if (!super.initialization()) return false;
        bind();
        try {
            networkSolutionStep();
            evaluate();
            for (int i=0;i<states.size();i++) {
                previous[i]=states.get(i).derivative.getAsDouble();
                wasActive[i]=states.get(i).active.getAsBoolean();
            }
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
                && type != IeeeSteamTCDRGovernor.class)
            throw new IllegalArgumentException("Unsupported staggered-Euler controller: " + type.getName());
    }

    private void validateModels() {
        for (var bus : dstabAlgo.getNetwork().getBusList()) {
            for (var device : bus.getDynamicBusDeviceList())
                if (device.getDeviceType() != DynamicBusDeviceType.DYNAMIC_GENERATOR)
                    throw new IllegalArgumentException("Unsupported dynamic bus device: " + device.getId());
            for (var gen : bus.getContributeGenList()) {
                if (gen.getDynamicGenDevice() == null && !gen.isActive()) continue;
                if (!(gen.getDynamicGenDevice() instanceof Machine m)
                        || (!(m instanceof EConstMachineImpl) && !(m instanceof RoundRotorMachineImpl)))
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
        states.add(new State(value,set,derivative,x->x,ownerActive));
    }

    private void controller(Object controller) {
        if (controller == null) return;
        if (!(controller instanceof AbstractAnnotateController c))
            throw new IllegalArgumentException("Unsupported controller " + controller.getClass());
        for (var wrapper:c.getFieldWrapperList()) {
            if (!(wrapper.getField() instanceof ICMLControlBlock)) continue;
            if (!(wrapper.getField() instanceof CMLControlBlock1stOrderAdapter b))
                throw new IllegalArgumentException("Unsupported block " + wrapper.getFieldName());
            DoubleUnaryOperator project=x->x;
            if (b.getType()==StaticBlockType.NonWindup) {
                if (b instanceof DelayControlBlock delay) project=x->delay.getLimit().limit(x);
                else if (b instanceof IntegrationControlBlock integral) project=x->integral.getLimit().limit(x);
                else throw new IllegalArgumentException("Unsupported non-windup block " + wrapper.getFieldName());
            }
            BooleanSupplier machineOwner = ownerActive;
            states.add(new State(b::getStateX,b::setStateX,b::getDX_dt,project,
                    () -> machineOwner.getAsBoolean() && c.isActive()));
        }
    }

    private void bind() {
        for (var bus:dstabAlgo.getNetwork().getBusList()) {

            for (var gen:bus.getContributeGenList()) {
                if (gen.getDynamicGenDevice() == null && !gen.isActive()) continue;
                if (!(gen.getDynamicGenDevice() instanceof MachineImpl m))
                    throw new IllegalArgumentException("Unsupported generator " + gen.getId());
                if (!(m instanceof EConstMachineImpl) && !(m instanceof RoundRotorMachineImpl))
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
                }
                controller(m.getExciter()); controller(m.getGovernor()); controller(m.getStabilizer());
            }
        }
        previous=new double[states.size()];
        wasActive=new boolean[states.size()];
    }

    private void evaluate() throws DStabSimuException {
        for (int i=0;i<machines.size();i++) {
            Machine m=machines.get(i);
            if (machineActive.get(i).getAsBoolean()
                    && !m.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,0))
                throw new DStabSimuException("Derivative evaluation failed: " + m.getId());
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
        double[] start=new double[states.size()], derivative=new double[states.size()];
        for (int i=0;i<states.size();i++) {
            start[i]=states.get(i).value.getAsDouble();
            derivative[i]=states.get(i).derivative.getAsDouble();
            boolean active = states.get(i).active.getAsBoolean();
            if (active && !wasActive[i]) previous[i]=derivative[i];
            wasActive[i]=active;
            if (discontinuity) derivative[i]=.5*(derivative[i]+previous[i]);
        }
        for (var bus:dstabAlgo.getNetwork().getBusList())
            if (bus.isActive()) bus.nextStep(dt,method,0);
        for (int i=0;i<states.size();i++) {
            State s=states.get(i);
            if (!wasActive[i]) { previous[i]=0.; continue; }
            double trial=start[i]+dt*(1.5*derivative[i]-.5*previous[i]);
            double end=s.project.applyAsDouble(trial);
            s.set.accept(end);
            previous[i]=end!=trial ? 0. : derivative[i];
        }
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
