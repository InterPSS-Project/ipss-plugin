package org.interpss.plugin.contingency;

import java.util.EnumSet;

import org.interpss.plugin.aclf.config.AclfRunConfigRec;

import com.google.gson.JsonObject;

import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

/**
 * Solver options for a batch of AC contingencies.
 *
 * <p>With a shared load-flow profile, each attempt starts from Core and import
 * settings, applies explicit profile fields, and then applies the overrides
 * supplied through this configuration's setters. With no shared profile,
 * imported cases retain their settings unless overridden. Programmatically
 * constructed cases use the contingency defaults.
 *
 * @author InterPSS Team
 */
public class AclfContingencyConfig  extends BaseContingencyConfig {
    // Load flow options
 ;
    private enum Option { METHOD, NON_DIVERGENT, ITERATIONS, TOLERANCE, ISLANDS, LINE_CONVERSION }

    // Distinguish an explicit setter call from the bean's legacy default value.
    private final transient EnumSet<Option> explicitOptions = EnumSet.noneOf(Option.class);

    private transient AclfRunConfigRec loadflowProfile;

    private AclfMethodType lfMethod = AclfMethodType.NR;
    private boolean applyAdjustAlgo = true;
    private boolean nonDivergent = true;
    private int maxIterations = 50;
    private double tolerance = 0.005;
    private int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());
    

    
    // Unused DCLF screening options â€” not wired into analyzers; retained for possible future use.
    // private boolean dclfScreening = false;
    // private double dcLoadingCutoff = 100.0; // in percent
    // private double dcFlowChangeCutoff = 200.0; // in percent of base case flow
    private boolean dclfInclLoss = true;

    public AclfMethodType getLfMethod() { return lfMethod; }
    public void setLfMethod(AclfMethodType lfMethod) {
        this.lfMethod = lfMethod;
        explicitOptions.add(Option.METHOD);
    }
    
    /** Whether to allow the adjustment categories configured by the model. */
    public boolean isApplyAdjustAlgo() { return applyAdjustAlgo; }
    /**
     * Sets batch-level permission for controls to move. Disabling adjustments
     * holds controls even when the shared profile permits them. Enabling
     * adjustments leaves the individual control switches in effect.
     *
     * @param applyAdjustAlgo whether to allow the configured adjustments
     */
    public void setApplyAdjustAlgo(boolean applyAdjustAlgo) { this.applyAdjustAlgo = applyAdjustAlgo; }
    
    public boolean isNonDivergent() { return nonDivergent; }
    public void setNonDivergent(boolean nonDivergent) {
        this.nonDivergent = nonDivergent;
        explicitOptions.add(Option.NON_DIVERGENT);
    }
    
    public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        explicitOptions.add(Option.ITERATIONS);
    }
    
    public double getTolerance() { return tolerance; }
    /**
     * Overrides the electrical solve tolerance for each outage attempt.
     *
     * @param tolerance maximum equation mismatch in per unit on the case base
     */
    public void setTolerance(double tolerance) {
        this.tolerance = tolerance;
        explicitOptions.add(Option.TOLERANCE);
    }

    @Override
    public void setTurnOffIslandBus(boolean enabled) {
        super.setTurnOffIslandBus(enabled);
        explicitOptions.add(Option.ISLANDS);
    }

    @Override
    public void setAutoTurnLine2Xfr(boolean enabled) {
        super.setAutoTurnLine2Xfr(enabled);
        explicitOptions.add(Option.LINE_CONVERSION);
    }

    /**
     * Supplies the load-flow profile to apply to every isolated outage attempt,
     * including recovery attempts. The caller can apply the same profile to the
     * base solver before starting the study.
     *
     * @param profile profile to copy, or {@code null} to use imported or
     *        legacy contingency defaults directly
     */
    public void setLoadflowProfile(AclfRunConfigRec profile) {
        loadflowProfile = profile == null ? null : AclfRunConfigRec.copyOf(profile);
    }

    /** Apply explicit overrides to the factory's imported profile, or the defaults for a built model. */
    void applySolverOptions(LoadflowAlgorithm algorithm, boolean imported) {
        if (loadflowProfile != null) {
            applySharedLoadflowProfile(algorithm);
            return;
        }
        if (!isApplyAdjustAlgo()) {
            algorithm.getLfAdjAlgo().setApplyAdjustAlgo(false);
        }
        if (!imported || explicitOptions.contains(Option.ISLANDS)) {
            algorithm.getDataCheckConfig().setTurnOffIslandBus(isTurnOffIslandBus());
        }
        if (!imported || explicitOptions.contains(Option.LINE_CONVERSION)) {
            algorithm.getDataCheckConfig().setAutoTurnLine2Xfr(isAutoTurnLine2Xfr());
        }
        if (!imported || explicitOptions.contains(Option.METHOD)) algorithm.setLfMethod(getLfMethod());
        if (!imported || explicitOptions.contains(Option.NON_DIVERGENT)) {
            algorithm.getNrMethodConfig().setNonDivergent(isNonDivergent());
        }
        if (!imported || explicitOptions.contains(Option.ITERATIONS)) algorithm.setMaxIterations(getMaxIterations());
        if (!imported || explicitOptions.contains(Option.TOLERANCE)) algorithm.setTolerance(getTolerance());
    }

    private void applySharedLoadflowProfile(LoadflowAlgorithm algorithm) {
        // Resolve ownership and tolerance before the profile constructs a solver.
        // Applying these fields afterward could leave its equations out of sync.
        var profile = loadflowProfile;
        if (!explicitOptions.isEmpty() || !isApplyAdjustAlgo()) {
            var overrides = new JsonObject();
            if (!isApplyAdjustAlgo()) overrides.addProperty("includeAdjustments", false);
            if (explicitOptions.contains(Option.ISLANDS)) {
                overrides.addProperty("turnOffIslandBus", isTurnOffIslandBus());
            }
            if (explicitOptions.contains(Option.LINE_CONVERSION)) {
                overrides.addProperty("autoTurnLine2Xfr", isAutoTurnLine2Xfr());
            }
            if (explicitOptions.contains(Option.METHOD)) overrides.addProperty("lfMethod", getLfMethod().name());
            if (explicitOptions.contains(Option.NON_DIVERGENT)) {
                overrides.addProperty("nonDivergent", isNonDivergent());
            }
            if (explicitOptions.contains(Option.ITERATIONS)) overrides.addProperty("maxIterations", getMaxIterations());
            if (explicitOptions.contains(Option.TOLERANCE)) {
                overrides.addProperty("tolerance", getTolerance());
                overrides.addProperty("tolUnitType", "PU");
            }
            profile = AclfRunConfigRec.overlayJson(profile, overrides.toString());
        }
        profile.applyTo(algorithm);
    }

    public int getParallelism() { return parallelism; }
    public void setParallelism(int parallelism) { this.parallelism = Math.max(1, parallelism); }

    // public boolean isDclfScreening() { return dclfScreening; }
    // public void setDclfScreening(boolean dclfScreening) { this.dclfScreening = dclfScreening; }

    public boolean isDclfInclLoss() { return dclfInclLoss; }
    public void setDclfInclLoss(boolean dclfInclLoss) { this.dclfInclLoss = dclfInclLoss; }

    // public double getDcLoadingCutoff() { return dcLoadingCutoff; }
    // public void setDcLoadingCutoff(double dcLoadingCutoff) { this.dcLoadingCutoff = dcLoadingCutoff; }

    // public double getDcFlowChangeCutoff() { return dcFlowChangeCutoff; }
    // public void setDcFlowChangeCutoff(double dcFlowChangeCutoff) { this.dcFlowChangeCutoff = dcFlowChangeCutoff; }
    
    /**
     * Create a default configuration instance for Python convenience
     */
    public static AclfContingencyConfig createDefaultConfig() {
        return new AclfContingencyConfig();
    }
}
