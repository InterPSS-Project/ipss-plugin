package org.interpss.plugin.contingency;

/**
 * Configuration class for contingency analysis parameters
 * 
 * @author InterPSS Team
 */
public class DclfContingencyConfig  extends BaseContingencyConfig {
   
    // Dclf screening options
    private boolean dclfScreening = false;
    private boolean dclfInclLoss = true;
    private double dcLoadingCutoff = 100.0; // in percent
    private double dcFlowChangeCutoff = 200.0; // in percent of base case flow

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
    public static DclfContingencyConfig createDefaultConfig() {
        return new DclfContingencyConfig();
    }
}
