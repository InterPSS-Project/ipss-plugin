package org.interpss.core.dstab.reference;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.LimitType;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.facts.StaticVarCompensator;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.algo.LoadflowAlgorithm;

/**
 * Explicit benchmark policies for a local generator plant with one continuous
 * shunt. The plant owns the voltage equation. Reactive allocation either retains
 * the initial shunt setting within physical limits or shares the requirement
 * using the declared control participation factors.
 *
 * <p>An aggregate capability solve determines the required total Q, including
 * the shunt's voltage-dependent limits. Original device targets and generator
 * limits are retained. Remote,
 * discrete, multiple-shunt and mixed generator-target groups are unsupported.
 * Fixed-Q generators without a primary voltage controller leave the ordinary
 * shunt controller in charge of its own voltage target.
 * Explicit fixed-Q machine limits also apply to multi-generator buses without
 * a secondary shunt; allocation preserves their solved aggregate power.
 * As with ordinary load flow, a failed solve can change network voltages;
 * temporary device limits and controls are restored and convergence is cleared.
 */
public final class GeneratorFirstShuntLoadflow {
    private GeneratorFirstShuntLoadflow() { }

    public enum ReactiveAllocation {
        RETAIN_INITIAL_SHUNT,
        PARTICIPATION_FACTORS
    }

    public record GroupResult(String bus, int generators, double voltage,
            double generatorQ, double initialB, double finalB) { }

    public record Result(boolean converged, int solves, List<GroupResult> groups) {
        public Result { groups = List.copyOf(groups); }
    }

    /** Solve using generator priority for supported colocated groups. */
    public static Result solve(LoadflowAlgorithm lf) throws InterpssException {
        return solve(lf, ReactiveAllocation.RETAIN_INITIAL_SHUNT);
    }

    /** Solve with an explicit reactive allocation policy and generator voltage target. */
    public static Result solve(LoadflowAlgorithm lf, ReactiveAllocation allocation) throws InterpssException {
        java.util.Objects.requireNonNull(allocation, "Reactive allocation policy");
        var groups = new ArrayList<Group>();
        // Validate every group before changing any devices.
        for (var bus : lf.getNetwork().getBusList()) {
            if (!bus.isActive() || bus.isSwing()) continue;
            var generators = bus.getContributeGenList().stream().filter(AclfGen::isActive).toList();
            var shunts = bus.getSwitchedShuntList().stream()
                    .filter(s -> s.isActive() && s.isControlStatus()
                            && s.getControlMode() != AclfAdjustControlMode.FIXED).toList();
            if (generators.isEmpty() || shunts.stream().noneMatch(
                    s -> s.getControlMode() == AclfAdjustControlMode.CONTINUOUS)) continue;
            if (!bus.isPVBusLimit() && generators.stream().allMatch(g -> {
                var limit = g.getQGenLimit();
                return limit != null && Double.isFinite(limit.getMin())
                        && limit.getMin() == limit.getMax();
            })) continue;
            if (bus.isRemoteQBus() || !bus.isPVBusLimit() || shunts.size() != 1)
                throw new IllegalArgumentException("Unsupported control group at " + bus.getId());
            groups.add(new Group(bus, generators, shunts.get(0), allocation));
        }
        if (groups.isEmpty()) {
            boolean converged = lf.loadflow();
            if (converged) {
                try {
                    allocateFixedReactiveMachines(lf);
                } catch (RuntimeException failure) {
                    lf.getNetwork().setLfConverged(false);
                    throw failure;
                }
            }
            return new Result(converged, 1, List.of());
        }
        boolean success = false;
        try {
            for (var group : groups) group.prepare();
            for (int iteration = 1; iteration <= 50; iteration++) {
                for (var group : groups) group.updateAggregateLimits();
                if (!lf.loadflow() || !lf.getTerminationReport().controlsSettled())
                    return new Result(false, iteration, List.of());
                boolean settled = true;
                for (var group : groups) settled &= group.limitsSettled();
                if (!settled) continue;
                var results = new ArrayList<GroupResult>();
                for (var group : groups) results.add(group.finish());
                allocateFixedReactiveMachines(lf);
                success = true;
                return new Result(true, iteration, results);
            }
            throw new IllegalStateException("Voltage-dependent group limits did not settle");
        } finally {
            for (var group : groups) group.restore(success);
            if (!success) lf.getNetwork().setLfConverged(false);
        }
    }

    private static void allocateFixedReactiveMachines(LoadflowAlgorithm lf) {
        for (var bus : lf.getNetwork().getBusList()) {
            if (!bus.isActive() || bus.isSwing()) continue;
            var generators = bus.getContributeGenList().stream().filter(AclfGen::isActive).toList();
            if (generators.size() < 2 || generators.stream().noneMatch(gen -> {
                var limit = gen.getQGenLimit();
                return limit != null && Double.isFinite(limit.getMin())
                        && limit.getMin() == limit.getMax()
                        && Math.abs(gen.getGen().getImaginary() - limit.getMin()) > 1.e-9;
            })) continue;
            var limits = generators.stream().map(AclfGen::getQGenLimit).toList();
            limits.forEach(GeneratorFirstShuntLoadflow::checkLimits);
            var powers = generators.stream().map(AclfGen::getGen).toList();
            double target = powers.stream().mapToDouble(Complex::getImaginary).sum();
            if (!allocatePhysicalReactivePower(generators, limits, powers, target))
                throw new IllegalStateException("Fixed-Q machine allocation failed at " + bus.getId());
        }
    }

    private static boolean allocatePhysicalReactivePower(List<? extends AclfGen> generators,
            List<LimitType> limits, List<Complex> powers, double target) {
        // Source-record zero intervals are fixed Q, not unspecified limits.
        // Compute first so an infeasible allocation cannot partially modify devices.
        double[] q = new double[generators.size()];
        for (int i = 0; i < q.length; i++) {
            double value = generators.get(i).getGen().getImaginary();
            if (!Double.isFinite(value)) return false;
            q[i] = Math.max(limits.get(i).getMin(), Math.min(limits.get(i).getMax(), value));
        }
        for (int pass = 0; pass <= q.length; pass++) {
            double residual = target;
            for (double value : q) residual -= value;
            if (Math.abs(residual) <= 1.e-9) {
                for (int i = 0; i < q.length; i++)
                    generators.get(i).setGen(new Complex(powers.get(i).getReal(), q[i]));
                return true;
            }
            double weight = 0.;
            int eligible = 0;
            for (int i = 0; i < q.length; i++) {
                if (!hasPhysicalHeadroom(q[i], limits.get(i), residual)) continue;
                weight += Math.abs(powers.get(i).getReal());
                eligible++;
            }
            if (eligible == 0) return false;
            for (int i = 0; i < q.length; i++) {
                if (!hasPhysicalHeadroom(q[i], limits.get(i), residual)) continue;
                double fraction = weight > 0. ? Math.abs(powers.get(i).getReal()) / weight : 1. / eligible;
                q[i] = Math.max(limits.get(i).getMin(),
                        Math.min(limits.get(i).getMax(), q[i] + residual * fraction));
            }
        }
        return false;
    }

    private static boolean hasPhysicalHeadroom(double q, LimitType limit, double residual) {
        return residual > 0. ? q < limit.getMax() : q > limit.getMin();
    }

    private static final class Group {
        final BaseAclfBus<?, ?> bus;
        final List<? extends AclfGen> generators;
        final SwitchedShunt shunt;
        final List<LimitType> limits;
        final List<Complex> powers;
        final LimitType busLimits;
        final AclfGenCode busCode;
        final boolean pvControl, pvAdjust, shuntAdjust;
        final double initialB, target, qMin, qMax, bMin, bMax, busQ, busTarget;
        final ReactiveAllocation allocation;
        final double plantShare;
        double totalQ;

        Group(BaseAclfBus<?, ?> bus, List<? extends AclfGen> generators, SwitchedShunt shunt,
                ReactiveAllocation allocation) {
            this.bus = bus;
            this.generators = generators;
            this.shunt = shunt;
            this.allocation = allocation;
            if (shunt instanceof StaticVarCompensator
                    || shunt.getControlMode() != AclfAdjustControlMode.CONTINUOUS
                    || shunt.getRemoteBus() != bus || shunt.getDesiredControlRange() == null
                    || Math.abs(shunt.getDesiredControlRange().getMax()
                            - shunt.getDesiredControlRange().getMin()) > 1.e-12)
                throw new IllegalArgumentException("Expected one local continuous point shunt at " + bus.getId());
            target = generators.get(0).getDesiredVoltMag();
            if (!Double.isFinite(target) || target <= 0.)
                throw new IllegalArgumentException("Invalid generator voltage target");
            limits = new ArrayList<>();
            powers = new ArrayList<>();
            double min = 0., max = 0.;
            for (var gen : generators) {
                String remote = gen.getRemoteVControlBusId();
                if ((remote != null && !remote.isEmpty() && !remote.equals(bus.getId()))
                        || Math.abs(gen.getDesiredVoltMag() - target) > 1.e-12)
                    throw new IllegalArgumentException("Mixed or remote generator control at " + bus.getId());
                var limit = gen.getQGenLimit();
                checkLimits(limit);
                min += limit.getMin(); max += limit.getMax();
                limits.add(limit); powers.add(gen.getGen());
            }
            qMin = min; qMax = max;
            if (allocation == ReactiveAllocation.PARTICIPATION_FACTORS) {
                // A plant is one voltage controller; do not count its machines
                // as separate participants alongside the shunt.
                double plantWeight = generators.get(0).getMvarControlPFactor();
                double shuntWeight = shunt.getRemoteControlPercentage() * .01;
                if (!Double.isFinite(plantWeight) || plantWeight <= 0.
                        || !Double.isFinite(shuntWeight) || shuntWeight <= 0.
                        || generators.stream().anyMatch(g ->
                            !Double.isFinite(g.getMvarControlPFactor())
                            || Math.abs(g.getMvarControlPFactor() - plantWeight) > 1.e-12))
                    throw new IllegalArgumentException("Invalid or inconsistent control participation at " + bus.getId());
                plantShare = plantWeight / (plantWeight + shuntWeight);
            } else {
                plantShare = Double.NaN;
            }
            var bLimits = shunt.getBLimit(true);
            checkLimits(bLimits);
            bMin = bLimits.getMin(); bMax = bLimits.getMax();
            initialB = shunt.getBActual();
            if (!Double.isFinite(initialB) || initialB < bMin - 1.e-12 || initialB > bMax + 1.e-12)
                throw new IllegalArgumentException("Initial shunt outside limits at " + bus.getId());
            busLimits = bus.getQGenLimit(); busCode = bus.getGenCode(); busQ = bus.getGenQ();
            busTarget = bus.getDesiredVoltMag();
            pvControl = bus.getPVBusLimit().isControlStatus();
            pvAdjust = bus.getPVBusLimit().isAdjustStatus();
            shuntAdjust = shunt.isAdjustStatus();
        }

        void prepare() {
            shunt.setControlStatus(false);
            shunt.setBActual(0.);
            bus.setGenCode(AclfGenCode.GEN_PV);
            bus.setDesiredVoltMag(target);
            bus.setVoltageMag(target);
            bus.getPVBusLimit().setControlStatus(true);
            bus.getPVBusLimit().setAdjustStatus(true);
        }

        void updateAggregateLimits() {
            double v2 = voltageSquared();
            // Only the temporary representation uses this expanded interval.
            // Every machine's real power and physical Q interval are restored.
            var first = limits.get(0);
            generators.get(0).setQGenLimit(new LimitType(
                    first.getMax() + bMax * v2, first.getMin() + bMin * v2));
            bus.setQGenLimit(new LimitType(qMax + bMax * v2, qMin + bMin * v2));
        }

        boolean limitsSettled() {
            double v2 = voltageSquared();
            // A PV bus's specified genQ can be stale; use solved network power.
            totalQ = bus.calNetGenResults().getImaginary();
            var current = bus.getQGenLimit();
            double min = qMin + bMin * v2, max = qMax + bMax * v2;
            return Double.isFinite(totalQ) && totalQ >= min - 1.e-10 && totalQ <= max + 1.e-10
                    && (Math.abs(totalQ - current.getMin()) >= 1.e-8
                            || Math.abs(min - current.getMin()) < 1.e-10)
                    && (Math.abs(totalQ - current.getMax()) >= 1.e-8
                            || Math.abs(max - current.getMax()) < 1.e-10);
        }

        GroupResult finish() {
            double v2 = voltageSquared();
            // Retain B exactly if the primary plant can supply the residual Q.
            double required = totalQ - initialB * v2;
            double plantQ = Math.max(qMin, Math.min(qMax, required));
            double b = required >= qMin && required <= qMax
                    ? initialB : (totalQ - plantQ) / v2;
            if (allocation == ReactiveAllocation.PARTICIPATION_FACTORS) {
                // The interval accounts for both the plant's Mvar limits and
                // the shunt's voltage-dependent admittance limits.
                double lower = Math.max(qMin, totalQ - bMax * v2);
                double upper = Math.min(qMax, totalQ - bMin * v2);
                if (lower > upper + 1.e-9)
                    throw new IllegalStateException("Infeasible reactive sharing at " + bus.getId());
                plantQ = Math.max(lower, Math.min(upper, totalQ * plantShare));
                b = (totalQ - plantQ) / v2;
            }
            b = Math.max(bMin, Math.min(bMax, b));
            shunt.setBActual(b);
            restoreLimits();
            bus.setGenQ(plantQ);
            bus.allocateContributeGenQ(1.e-9);
            if (!allocatePhysicalReactivePower(generators, limits, powers, plantQ))
                throw new IllegalStateException("Physical machine allocation failed at " + bus.getId());
            double sum = 0.;
            for (int i = 0; i < generators.size(); i++) {
                var gen = generators.get(i);
                double q = gen.getGen().getImaginary();
                if (q < limits.get(i).getMin() - 1.e-9 || q > limits.get(i).getMax() + 1.e-9)
                    throw new IllegalStateException("Machine Q outside physical limits at " + bus.getId());
                gen.setGen(new Complex(powers.get(i).getReal(), q));
                sum += q;
            }
            if (Math.abs(totalQ - sum - b * v2) > 1.e-8)
                throw new IllegalStateException("Group reactive balance failed at " + bus.getId());
            boolean limited = plantQ <= qMin + 1.e-10 || plantQ >= qMax - 1.e-10;
            bus.setGenCode(limited ? AclfGenCode.GEN_PQ : AclfGenCode.GEN_PV);
            return new GroupResult(bus.getId(), generators.size(), bus.getVoltageMag(), sum, initialB, b);
        }

        double voltageSquared() {
            double v = bus.getVoltageMag();
            if (!Double.isFinite(v) || v <= 0.) throw new IllegalStateException("Invalid voltage at " + bus.getId());
            return v * v;
        }

        void restoreLimits() {
            for (int i = 0; i < generators.size(); i++) generators.get(i).setQGenLimit(limits.get(i));
            bus.setQGenLimit(new LimitType(qMax, qMin));
        }

        void restore(boolean success) {
            restoreLimits();
            shunt.setControlStatus(true);
            shunt.setAdjustStatus(shuntAdjust);
            bus.getPVBusLimit().setControlStatus(pvControl);
            bus.getPVBusLimit().setAdjustStatus(pvAdjust);
            bus.setDesiredVoltMag(busTarget);
            if (!success) {
                for (int i = 0; i < generators.size(); i++) generators.get(i).setGen(powers.get(i));
                shunt.setBActual(initialB);
                bus.setQGenLimit(busLimits); bus.setGenCode(busCode); bus.setGenQ(busQ);
            }
        }
    }

    private static void checkLimits(LimitType limits) {
        if (limits == null || !Double.isFinite(limits.getMin()) || !Double.isFinite(limits.getMax())
                || limits.getMin() > limits.getMax())
            throw new IllegalArgumentException("Expected finite physical limits");
    }
}
