package org.interpss.plugin.contingency;

import com.interpss.core.algo.AclfMethodType;

/**
 * Configuration class for contingency analysis parameters
 * 
 * @author InterPSS Team
 */
public class ContingencyConfig  {
    private boolean turnOffIslandBus = true;
    private boolean autoTurnLine2Xfr = true;
    private AclfMethodType lfMethod = AclfMethodType.NR;
    private boolean applyAdjustAlgo = false;
    private boolean nonDivergent = true;
    private int maxIterations = 50;
    private double tolerance = 0.005;
    
    private double overloadThreshold = 100.0; // in percent
    
    // Getters and setters
    public boolean isTurnOffIslandBus() { return turnOffIslandBus; }
    public void setTurnOffIslandBus(boolean turnOffIslandBus) { this.turnOffIslandBus = turnOffIslandBus; }
    
    public boolean isAutoTurnLine2Xfr() { return autoTurnLine2Xfr; }
    public void setAutoTurnLine2Xfr(boolean autoTurnLine2Xfr) { this.autoTurnLine2Xfr = autoTurnLine2Xfr; }
    
    public AclfMethodType getLfMethod() { return lfMethod; }
    public void setLfMethod(AclfMethodType lfMethod) { this.lfMethod = lfMethod; }
    
    public boolean isApplyAdjustAlgo() { return applyAdjustAlgo; }
    public void setApplyAdjustAlgo(boolean applyAdjustAlgo) { this.applyAdjustAlgo = applyAdjustAlgo; }
    
    public boolean isNonDivergent() { return nonDivergent; }
    public void setNonDivergent(boolean nonDivergent) { this.nonDivergent = nonDivergent; }
    
    public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
    
    public double getTolerance() { return tolerance; }
    public void setTolerance(double tolerance) { this.tolerance = tolerance; }
    
    public double getOverloadThreshold() { return overloadThreshold; }
    public void setOverloadThreshold(double overloadThreshold) { this.overloadThreshold = overloadThreshold; }
    
    /**
     * Create a default configuration instance for Python convenience
     */
    public static ContingencyConfig createDefaultConfig() {
        return new ContingencyConfig();
    }
}
