package org.interpss.dstab.algo;

import java.util.ArrayList;
import java.util.List;

import org.interpss.dstab.dynLoad.impl.Cmldblu1Model;
import org.interpss.dstab.dynLoad.impl.Cmldznu2Model;
import org.interpss.dstab.dynLoad.impl.IeelLoadModel;
import org.interpss.dstab.dynLoad.impl.InductionMotorImpl;
import org.interpss.fadapter.psse.PsseDStabSolver.LoadModelAssignment;
import org.interpss.plugin.dstab.config.LoadModelThresholds;

import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.algo.dynamic.DynamicSimulationSolverBase.LocalSubstepPreparation;
import com.interpss.dstab.algo.dynamic.DynamicSimulationSolverBase.SourceLoadIdentity;
import com.interpss.dstab.device.DynamicBusDevice;

/**
 * Imported dynamic-load behavior of the PSS/E solvers: composite-load
 * initialization thresholds, load-model ownership reports and induction-motor
 * local substeps. The Core solver runtime calls these through its hooks.
 */
public final class ImportedLoadModelSupport {
    private ImportedLoadModelSupport() { }

    /** Apply initialization thresholds to every composite source model on an active bus. */
    public static void applyThresholds(BaseDStabNetwork<?, ?> network, LoadModelThresholds thresholds) {
        for (var bus : network.getBusList())
            if (bus.isActive() && bus.getInfoOnlyDynModel() instanceof Cmldznu2Model first)
                for (var model : first.getSourceModels()) model.setInitializationThresholds(thresholds);
    }

    public static List<Cmldznu2Model.InitializationScreen> screeningReport(BaseDStabNetwork<?, ?> network) {
        var report = new ArrayList<Cmldznu2Model.InitializationScreen>();
        for (var bus : network.getBusList())
            if (bus.getInfoOnlyDynModel() instanceof Cmldznu2Model first)
                for (var model : first.getSourceModels())
                    if (model.getInitializationScreen() != null) report.add(model.getInitializationScreen());
        return List.copyOf(report);
    }

    /**
     * Final per-source ownership, including ordinary loads with no DYR assignment.
     * Identities are captured before composite expansion removes source records.
     * The query then resolves the actual initialization result, rather than
     * mistaking an attached-but-screened composite for a live feeder/motor injection.
     */
    public static List<LoadModelAssignment> assignmentReport(BaseDStabNetwork<?, ?> network,
            List<SourceLoadIdentity> sources) {
        var result = new ArrayList<LoadModelAssignment>();
        String fallback = network.isStaticLoadIncludedInYMatrix() ? "CONST_Z/CONST_Z" :
                network.getStaticLoadModel().name() + "/" + network.getReactiveStaticLoadModel().name();
        for (var source : sources) {
            var bus = network.getDStabBus(source.bus());
            String candidate = "NONE", applied = source.active() ? fallback : "NONE",
                    status = source.active() ? "STATIC_DEFAULT" : "INACTIVE";
            List<String> reasons = List.of();
            if (source.active()) {
                Cmldznu2Model owner = null;
                if (bus.getInfoOnlyDynModel() instanceof Cmldznu2Model first)
                    for (var model : first.getSourceModels())
                        if (model.getTargetLoadIds().contains(source.loadId())) owner = model;
                if (owner != null) {
                    candidate = owner instanceof Cmldblu1Model ? "CMLDBLU1" : "CMLDZNU2";
                    var screen = owner.getInitializationScreen();
                    if (screen != null && screen.bypassed()) { status = "SCREENED_STATIC"; reasons = screen.violations(); }
                    else { applied = candidate; status = screen == null ? "PENDING_INITIALIZATION" : "DYNAMIC"; }
                } else if (bus.getDynLoadModelList() != null) {
                    for (var model : bus.getDynLoadModelList())
                        if (model.isActive() && model.getId().equals(source.loadId())) {
                            candidate = model instanceof IeelLoadModel ieel
                                    ? ieel.getSourceModelName() : model.getClass().getSimpleName();
                            applied = candidate; status = "DYNAMIC"; break;
                        }
                }
            }
            result.add(new LoadModelAssignment(source.bus(), source.loadId(), candidate, applied, status, reasons));
        }
        return List.copyOf(result);
    }

    /** Two-axis induction motors integrate their electrical states on local substeps of at most 1 ms. */
    public static void prepareMotorSubsteps(DynamicBusDevice device, String busId, double outer,
            List<LocalSubstepPreparation> report) {
        if (device.getClass() != InductionMotorImpl.class) return;
        var motor = (InductionMotorImpl) device;
        if (!motor.isTwoAxisModel()) return;
        double maximum = Math.min(.001, Math.min(.5 * motor.getTp0(), .5 * motor.getTpp0()));
        if (!Double.isFinite(maximum) || maximum <= 0) throw new IllegalArgumentException("Invalid motor electrical time constant");
        int count = (int) Math.ceil(outer / maximum);
        if (count > 128) throw new IllegalArgumentException("Motor requires more than 128 local intervals");
        motor.setLocalIntegrationMaximumStepSec(maximum);
        if (count > 1) report.add(new LocalSubstepPreparation(motor.getId() + "@" + busId, motor.getClass().getName(),
                "coupled electrical states and slip", motor.getTpp0(), outer, count, outer / count));
    }
}
