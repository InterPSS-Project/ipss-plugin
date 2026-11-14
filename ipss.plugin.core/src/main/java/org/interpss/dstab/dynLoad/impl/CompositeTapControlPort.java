package org.interpss.dstab.dynLoad.impl;

import java.util.Map;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.controller.runtime.StaggeredEulerParticipant;
import com.interpss.dstab.controller.runtime.IntegrationStateAccess;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.device.NetworkIterationAware;

/** Zero-injection owner of composite tap history and its physical transformer stamp. */
final class CompositeTapControlPort extends Cmldznu2AlgebraicLoadModel
        implements StaggeredEulerParticipant, NetworkIterationAware, IntegrationStateAccess {
    private static final IntegrationStateMonitor.Snapshot NO_DIFFERENTIAL_RATES =
            new IntegrationStateMonitor.Snapshot(IntegrationStateMonitor.Stage.NOT_EVALUATED,Map.of(),Map.of());
    private final Cmldznu2Model owner;
    private final CompositeTapControl state, previous;
    private final double initialTap;
    private final double compensationR, compensationX;
    private double sampledVoltage;
    private double precedingAppliedTap;
    private double voltageMinimum, voltageMaximum;
    private boolean pending, solving, predictorPending;
    private double predictorStep;

    CompositeTapControlPort(Cmldznu2Model owner) {
        super(Kind.STATIC, owner.getDStabBus(), owner.getId()+"_Tap", 0, owner.getData(), 0);
        this.owner=owner;
        var d=owner.getData();
        state=new CompositeTapControl(d.value(9),d.value(10),d.value(11),d.value(14),d.value(15));
        previous=new CompositeTapControl(d.value(9),d.value(10),d.value(11),d.value(14),d.value(15));
        initialTap=owner.getDistributionTransformer().getToTurnRatio();
        double factor=owner.getDStabBus().getNetwork().getBaseMva()/owner.getDistEquivalent().getMva();
        if (!Double.isFinite(factor) || factor <= 0) throw new IllegalArgumentException("Initialized subsystem MVA base required");
        compensationR=d.value(16)*factor; compensationX=d.value(17)*factor;
        setVoltageBand(d.value(12),d.value(13));
    }
    void setVoltageBand(double minimum,double maximum) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum <= 0 || maximum < minimum)
            throw new IllegalArgumentException("A finite positive ordered voltage band is required");
        requireCompletedInterval();
        voltageMinimum=minimum; voltageMaximum=maximum;
    }
    @Override public boolean initStates() {
        if (!super.initStates()) return false;
        setTap(initialTap); state.initialize(initialTap); previous.copyFrom(state);
        pending=false; solving=false; predictorPending=false; sampledVoltage=controlledVoltage();
        return true;
    }
    private double controlledVoltage() {
        var branch=owner.getDistributionTransformer();
        var low=owner.getLowVoltBus().getVoltage(); var high=owner.getDStabBus().getVoltage();
        double tap=branch.getToTurnRatio(), zr=branch.getZ().getReal(), zi=branch.getZ().getImaginary();
        double dr=high.getReal()-low.getReal()/tap, di=high.getImaginary()-low.getImaginary()/tap;
        double denominator=(zr*zr+zi*zi)*tap;
        double ir=(dr*zr+di*zi)/denominator, ii=(di*zr-dr*zi)/denominator;
        // The timer observes the accepted command prepared with the existing
        // transformer boundary current, before that command changes the network.
        // Keep the physical voltage/current untouched; electrical application
        // still belongs to advance() and the following network solution.
        double command=state.tap();
        if (command == tap)
            return Math.hypot(low.getReal()-ir*compensationR+ii*compensationX,
                    low.getImaginary()-ir*compensationX-ii*compensationR);
        double voltageScale=command/tap, currentScale=tap/command;
        return Math.hypot(low.getReal()*voltageScale-(ir*compensationR-ii*compensationX)*currentScale,
                low.getImaginary()*voltageScale-(ir*compensationX+ii*compensationR)*currentScale);
    }
    private void advance(double dt, double voltage) {
        if (dt == 0) return;
        if (!owner.getDistributionTransformer().isActive() || !owner.getLowVoltBus().isActive()) return;
        if (pending) throw new IllegalStateException("Accept or discard the previous tap interval before advancing");
        previous.copyFrom(state);
        precedingAppliedTap=owner.getDistributionTransformer().getToTurnRatio();
        // The accepted command belongs to this network evaluation. The timer's
        // newly produced command is published only after this interval succeeds;
        // it must not alter the electrical endpoint that produced that command.
        state.advance(dt, voltage, voltageMinimum, voltageMaximum);
        pending=true;
        setTap(previous.tap());
    }
    private void setTap(double tap) {
        var branch=owner.getDistributionTransformer();
        if (branch.getToTurnRatio() == tap) return;
        var ff=branch.yff(); var ft=branch.yft(); var tf=branch.ytf(); var tt=branch.ytt();
        branch.setToTurnRatio(tap);
        var network=(DStabilityNetwork)owner.getDStabBus().getNetwork();
        if (network.getYMatrix() != null) {
            int from=owner.getDStabBus().getSortNumber(), to=owner.getLowVoltBus().getSortNumber();
            network.getYMatrix().addToA(branch.yff().subtract(ff),from,from);
            network.getYMatrix().addToA(branch.yft().subtract(ft),from,to);
            network.getYMatrix().addToA(branch.ytf().subtract(tf),to,from);
            network.getYMatrix().addToA(branch.ytt().subtract(tt),to,to);
            network.setYMatrixDirty(true);
        }
    }
    @Override public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) throw new IllegalArgumentException("Modified Euler required");
        validateInterval(dt);
        if (flag != 0 && flag != 1) throw new IllegalArgumentException("Unknown integration stage");
        if (dt == 0) return true;
        if (flag == 0) {
            requireCompletedInterval();
            sampledVoltage=controlledVoltage();
            predictorStep=dt; predictorPending=true;
        } else {
            if (!predictorPending || predictorStep != dt || pending || solving)
                throw new IllegalStateException("Matching tap predictor required before corrector");
            advance(dt,sampledVoltage);
            predictorPending=false;
        }
        return true;
    }
    private static void validateInterval(double dt) {
        if (!Double.isFinite(dt) || dt < 0) throw new IllegalArgumentException("Finite nonnegative tap interval required");
    }
    private void requireCompletedInterval() {
        if (pending || solving || predictorPending)
            throw new IllegalStateException("Complete or discard the current tap interval first");
    }
    @Override public int staggeredStateCount() { return 0; }
    @Override public void readStaggeredState(double[] values,int offset) { }
    @Override public void evaluateStaggered(double dt,double[] rates,int offset) { }
    @Override public void applyStaggeredState(double[] values,int offset,double dt) { }
    @Override public void completeStaggeredStep(double dt,double endpointTime) {
        validateInterval(dt);
        if (dt == 0) return;
        requireCompletedInterval();
        advance(dt,controlledVoltage());
    }
    @Override public void beginNetworkSolution(Context context) { solving=pending; }
    @Override public void endNetworkSolution(boolean converged) {
        if (solving && !converged) { setTap(precedingAppliedTap); state.copyFrom(previous); }
        if (solving) pending=false;
        if (!converged) predictorPending=false;
        solving=false;
    }
    @Override public Complex getNortonCurInj() { return Complex.ZERO; }
    @Override public Map<String,Double> getIntegrationStates() { return Map.of(); }
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot() { return NO_DIFFERENTIAL_RATES; }
    @Override public Map<String,Double> getIntegrationHistories() { return getNamedStates(); }
    @Override public Map<String,Double> getNamedStates() {
        var accepted=pending ? previous : state;
        return Map.of("LTC.Tap",accepted.tap(),"LTC.AppliedTap",pending ? precedingAppliedTap : owner.getDistributionTransformer().getToTurnRatio(),"LTC.Control",accepted.control(),
                "LTC.Time",accepted.time(),"LTC.PendingTime",Double.isFinite(accepted.due()) ? accepted.due() : -1.0);
    }
}
