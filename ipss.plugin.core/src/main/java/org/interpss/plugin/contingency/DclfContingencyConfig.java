package org.interpss.plugin.contingency;

import com.interpss.core.algo.dclf.DclfContingencySolutionMethod;

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
    private DclfContingencySolutionMethod solutionMethod = DclfContingencySolutionMethod.SparseEqnSolve;
    private int kluEndpointRhsBatchSize = 0;

    public boolean isDclfScreening() { return dclfScreening; }
    public void setDclfScreening(boolean dclfScreening) { this.dclfScreening = dclfScreening; }
    
    public boolean isDclfInclLoss() { return dclfInclLoss; }
    public void setDclfInclLoss(boolean dclfInclLoss) { this.dclfInclLoss = dclfInclLoss; }
    
    public double getDcLoadingCutoff() { return dcLoadingCutoff; }
    public void setDcLoadingCutoff(double dcLoadingCutoff) { this.dcLoadingCutoff = dcLoadingCutoff; }
    
    public double getDcFlowChangeCutoff() { return dcFlowChangeCutoff; }
    public void setDcFlowChangeCutoff(double dcFlowChangeCutoff) { this.dcFlowChangeCutoff = dcFlowChangeCutoff; }

    /**
     * Returns the core DCLF contingency solution method for analyzers that
     * delegate to {@code ContingencyAnalysisAlgorithm.ca()}.
     */
    public DclfContingencySolutionMethod getSolutionMethod() { return solutionMethod; }

    public void setSolutionMethod(DclfContingencySolutionMethod solutionMethod) {
        if (solutionMethod == null) {
            throw new IllegalArgumentException("solutionMethod cannot be null");
        }
        this.solutionMethod = solutionMethod;
    }

    /**
     * KLU unit-RHS batch size for the accelerated endpoint solve path. A value
     * of {@code 0} keeps the analyzer default/system-property behavior.
     */
    public int getKluEndpointRhsBatchSize() { return kluEndpointRhsBatchSize; }

    public void setKluEndpointRhsBatchSize(int kluEndpointRhsBatchSize) {
        this.kluEndpointRhsBatchSize = Math.max(0, kluEndpointRhsBatchSize);
    }
    
    /**
     * Create a default configuration instance for Python convenience
     */
    public static DclfContingencyConfig createDefaultConfig() {
        return new DclfContingencyConfig();
    }
}
