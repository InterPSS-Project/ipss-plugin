package org.interpss.core.dstab.reference;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.interpss.fadapter.psse.PSSEMultiFileLoader;

import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.config.ControlInitializationMode;

/** Default operating-point preparation shared by reference and timing runs. */
public final class Texas2kPowerflowPreparation {
    private Texas2kPowerflowPreparation() { }

    private record NettedUnit(AclfGen generator, AclfLoad load) { }
    private record NettedBus(BaseAclfBus<?, ?> bus, AclfGenCode originalCode,
            List<NettedUnit> units) { }

    /**
     * Solve with primary generator regulation and secondary shunt support, then
     * net explicitly removed generators from their solved powers. The ordinary
     * dynamic initialization subsequently converts the resulting loads using the
     * configured active/reactive load profiles. No measured trajectory is used.
     */
    public static void solve(LoadflowAlgorithm lf, Path raw, Map<String, Object> status)
            throws Exception {
        solve(lf, raw, status, true);
    }

    /** The explicit cold post-netting option is retained for diagnostic comparisons. */
    public static void solve(LoadflowAlgorithm lf, Path raw, Map<String, Object> status,
            boolean reusePostNettingControls) throws Exception {
        var limits = lf.getLfAdjAlgo().getLimitCtrlConfig();
        var callerMode = limits.getControlInitializationMode();
        double callerToleranceFactor = limits.getToleranceFactor();
        try {
            // A looser Q-limit deadband can leave a PV solution outside the
            // machine capability. Final allocation then clips Q without solving
            // the changed balance, creating a dynamic initialization residual.
            // Honor a caller's stricter setting, but do not exceed the requested
            // electrical mismatch tolerance during operating-point preparation.
            limits.setToleranceFactor(Math.min(callerToleranceFactor, 1.));
            status.put("reactive_limit_tolerance_pu", limits.getToleranceFactor() * lf.getTolerance());
            solvePrepared(lf, raw, status, reusePostNettingControls);
        } finally {
            limits.setControlInitializationMode(callerMode);
            limits.setToleranceFactor(callerToleranceFactor);
        }
    }

    private static void solvePrepared(LoadflowAlgorithm lf, Path raw, Map<String, Object> status,
            boolean reusePostNettingControls) throws Exception {
        status.put("reuse_post_netting_controls", reusePostNettingControls);
        var deferred = findImportedNetting(lf, raw);
        // Validate all imported netting before changing any device status.
        for (var entry : deferred) {
            for (var unit : entry.units()) {
                unit.generator().setStatus(true);
                unit.load().setStatus(false);
            }
            entry.bus().setGenCode(entry.originalCode());
            entry.bus().initContributeGen(false);
            entry.bus().initContributeLoad(false);
        }
        lf.getLfAdjAlgo().setApplyAdjustAlgo(true);
        lf.setContinuousSwitchedShuntNrControlEnabled(true);
        // Settle the control active set before requesting final electrical accuracy.
        double transitionTolerance = Math.max(10. * lf.getTolerance(), 1.e-4);
        lf.setNrControlTransitionTolerance(transitionTolerance);
        lf.getLfAdjAlgo().getLimitCtrlConfig().setStartPoint((int) Math.min(
                Integer.MAX_VALUE, Math.ceil(transitionTolerance / lf.getTolerance())));
        lf.getLfAdjAlgo().setMaxPvLimitAdjustmentsPerIteration(Integer.MAX_VALUE);
        lf.getLfAdjAlgo().setMinPvLimitStableIterations(1);
        lf.getLfAdjAlgo().setRequirePriorPvLimitViolation(false);
        status.put("nr_control_transition_tolerance_pu", transitionTolerance);
        status.put("continuous_shunt_control", "inner-NR");
        // Continuous controls are owned by NR. Serialize physical discrete moves.
        lf.getLfAdjAlgo().setMaxSwitchedShuntAdjustmentsPerIteration(1);
        lf.getLfAdjAlgo().getVoltAdjConfig().setSwitchedShuntAdjust(true);
        lf.getLfAdjAlgo().getVoltAdjConfig().setAdjTolerance(0.00000001);
        status.put("imported_reactive_limit_guesses", seedImportedReactiveLimits(lf));
        // Imported Q and voltage provide an initial guess, not a locked active
        // set. Normal limit enforcement, backoff and final convergence remain on.
        lf.getLfAdjAlgo().getLimitCtrlConfig()
                .setControlInitializationMode(ControlInitializationMode.PREVIOUS_SOLUTION);
        status.put("powerflow_preparation", "generator-priority-solved-netting");
        status.put("reactive_allocation", "PARTICIPATION_FACTORS");
        status.put("switched_shunt_adjustment", true);
        requireSolved(lf, "before_netting", status);
        int count = 0;
        for (var entry : deferred) {
            for (var unit : entry.units()) {
                var power = unit.generator().getGen();
                if (power == null || !Double.isFinite(power.getReal())
                        || !Double.isFinite(power.getImaginary()))
                    throw new IllegalStateException("Invalid solved generator power at " + entry.bus().getId());
                status.put("netted_power_" + entry.bus().getId() + "_" + unit.generator().getId(),
                        power.toString());
                unit.load().setLoadCP(power.negate());
                unit.load().setCode(AclfLoadCode.CONST_P);
                unit.load().setStatus(true);
                unit.generator().setStatus(false);
                count++;
            }
            if (entry.bus().getContributeGenList().stream().noneMatch(AclfGen::isActive))
                entry.bus().setGenCode(AclfGenCode.NON_GEN);
            entry.bus().initContributeGen(false);
            entry.bus().initContributeLoad(false);
        }
        status.put("solved_netted_generators", count);
        if (!deferred.isEmpty() && reusePostNettingControls) {
            // Netting replaces each solved injection by the same negative load.
            // Keep the already-solved control state as the next solve's starting
            // point; still solve the changed equations and check all controls.
            var limits = lf.getLfAdjAlgo().getLimitCtrlConfig();
            var previousMode = limits.getControlInitializationMode();
            try {
                limits.setControlInitializationMode(ControlInitializationMode.PREVIOUS_SOLUTION);
                status.put("post_netting_control_initialization", "PREVIOUS_SOLUTION");
                requireSolved(lf, "after_netting", status);
            } finally {
                limits.setControlInitializationMode(previousMode);
            }
        } else if (!deferred.isEmpty()) {
            lf.getLfAdjAlgo().getLimitCtrlConfig()
                    .setControlInitializationMode(ControlInitializationMode.COLD_START);
            requireSolved(lf, "after_netting", status);
        }
    }

    private static int seedImportedReactiveLimits(LoadflowAlgorithm lf) {
        int count = 0;
        for (var bus : lf.getNetwork().getBusList()) {
            if (!bus.isActive() || !bus.isGenPV() || !bus.isPVBusLimit() || bus.isRemoteQBus()
                    || !bus.getPVBusLimit().isControlStatus()
                    || bus.getContributeGenList().stream().noneMatch(AclfGen::isActive)) continue;
            var limits = bus.getQGenLimit();
            if (limits == null) continue;
            double q = bus.getGenQ(), voltage = bus.getVoltageMag(), target = bus.getDesiredVoltMag();
            if (Math.abs(q - limits.getMin()) < 1.e-6 && voltage > target + 1.e-6) {
                bus.getPVBusLimit().changeToGenPQBus(limits.getMin());
                count++;
            } else if (Math.abs(q - limits.getMax()) < 1.e-6 && voltage < target - 1.e-6) {
                bus.getPVBusLimit().changeToGenPQBus(limits.getMax());
                count++;
            } else {
                bus.setVoltageMag(target);
            }
        }
        return count;
    }

    private static void requireSolved(LoadflowAlgorithm lf, String stage,
            Map<String, Object> status) throws Exception {
        // The generator owns the voltage target; colocated controllers share
        // total Q by their declared participation factors and physical limits.
        // Retaining the imported shunt can reproduce V while initializing the
        // generator at a substantially different reactive operating point.
        var result = GeneratorFirstShuntLoadflow.solve(lf,
                GeneratorFirstShuntLoadflow.ReactiveAllocation.PARTICIPATION_FACTORS);
        var termination = lf.getTerminationReport();
        status.put("loadflow_" + stage + "_solves", result.solves());
        status.put("loadflow_" + stage + "_iterations", termination.iteration());
        status.put("loadflow_" + stage + "_max_mismatch_pu", termination.maxEquationMismatchPu());
        status.put("loadflow_" + stage + "_max_auxiliary_mismatch_pu", termination.maxAuxiliaryMismatchPu());
        status.put("loadflow_termination", termination.status().toString());
        status.put("loadflow_unsettled_controls", termination.unsettledControlCount());
        if (!result.converged() || !termination.controlsSettled())
            throw new IllegalStateException("Coordinated load flow failed " + stage + ": " + termination);
    }

    private static List<NettedBus> findImportedNetting(LoadflowAlgorithm lf, Path raw)
            throws Exception {
        var result = new ArrayList<NettedBus>();
        boolean hasNetting = lf.getNetwork().getBusList().stream().anyMatch(bus ->
                bus.getContributeLoadList().stream().anyMatch(load ->
                        load.isActive() && load.getId().startsWith("GNET-")));
        if (!hasNetting) return result;
        var original = new PSSEMultiFileLoader().loadDStab(raw.toString()).getDStabilityNet();
        for (var bus : lf.getNetwork().getBusList()) {
            var source = original.getBus(bus.getId());
            if (source == null) throw new IllegalStateException("Bus absent from input: " + bus.getId());
            var generated = bus.getContributeLoadList().stream().filter(load -> load.isActive()
                    && load.getId().startsWith("GNET-")
                    && source.getContributeLoad(load.getId()) == null).toList();
            if (generated.isEmpty()) continue;
            var reserved = new HashSet<String>();
            source.getContributeLoadList().forEach(load -> reserved.add(load.getId()));
            var units = new ArrayList<NettedUnit>();
            // Reproduce the importer's collision-safe IDs, including generator
            // IDs containing hyphens. Never reinterpret an original input load.
            for (var generator : source.getContributeGenList()) {
                if (!generator.isActive()) continue;
                String base = "GNET-" + generator.getId(), id = base;
                int suffix = 2;
                while (!reserved.add(id)) id = base + "-" + suffix++;
                var load = bus.getContributeLoad(id);
                var current = bus.getContributeGen(generator.getId());
                if (load == null || !generated.contains(load) || current == null || current.isActive())
                    throw new IllegalStateException("Unexpected imported netting at " + bus.getId() + "/" + id);
                units.add(new NettedUnit(current, load));
            }
            if (units.size() != generated.size())
                throw new IllegalStateException("Unmatched imported netting at " + bus.getId());
            result.add(new NettedBus(bus, source.getGenCode(), List.copyOf(units)));
        }
        return result;
    }
}
