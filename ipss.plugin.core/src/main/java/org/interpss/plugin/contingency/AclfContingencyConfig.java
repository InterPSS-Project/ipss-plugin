package org.interpss.plugin.contingency;

import com.interpss.core.algo.AclfMethodType;

/**
 * Configuration class for contingency analysis parameters
 * 
 * @author InterPSS Team
 */
public class AclfContingencyConfig  extends BaseContingencyConfig {
    // Load flow options
 ;
    private AclfMethodType lfMethod = AclfMethodType.NR;
    private boolean applyAdjustAlgo = false;
    private boolean nonDivergent = true;
    private int maxIterations = 50;
    private double tolerance = 0.005;
    

    
    // Dclf screening options
    private boolean dclfScreening = false;
    private boolean dclfInclLoss = true;
    private double dcLoadingCutoff = 100.0; // in percent
    private double dcFlowChangeCutoff = 200.0; // in percent of base case flow
    

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
    

    public boolean isDclfScreening() { return dclfScreening; }
    public void setDclfScreening(boolean dclfScreening) { this.dclfScreening = dclfScreening; }
    
    public boolean isDclfInclLoss() { return dclfInclLoss; }
    public void setDclfInclLoss(boolean dclfInclLoss) { this.dclfInclLoss = dclfInclLoss; }
    
    public double getDcLoadingCutoff() { return dcLoadingCutoff; }
    public void setDcLoadingCutoff(double dcLoadingCutoff) { this.dcLoadingCutoff = dcLoadingCutoff; }
    
    public double getDcFlowChangeCutoff() { return dcFlowChangeCutoff; }
    public void setDcFlowChangeCutoff(double dcFlowChangeCutoff) { this.dcFlowChangeCutoff = dcFlowChangeCutoff; }
    
    /**
     * Create a default configuration instance for Python convenience
     */
    public static AclfContingencyConfig createDefaultConfig() {
        return new AclfContingencyConfig();
    }
}
