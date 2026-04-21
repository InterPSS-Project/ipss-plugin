package org.interpss.plugin.optadj.algo;

import org.interpss.plugin.optadj.optimizer.BaseStateOptimizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;

/**
 * 
 * @author Donghao.F
 * 
 * @date 2025��4��17�� ����11:20:49
 * 
 * 
 * 
 */
public abstract class BaseAclfNetOptimizer {
    private static final Logger log = LoggerFactory.getLogger(BaseAclfNetOptimizer.class);
    
    // adjust gen size for the optimization, -1 means no limit
    protected int genOptSizeLimit = -1;
    
    // adjust section size for the optimization, -1 means no limit
    protected int secOptSizeLimit = -1;
    
	// a contingency analysis algorithm object based on which the optimization is performed
	protected ContingencyAnalysisAlgorithm dclfAlgo;
	
	// the GenState optimizer object created during the optimization
	private BaseStateOptimizer optimizer;
	
	/**
	 * Constructor
	 * 
	 * @param dclfAlgo
	 */
	public BaseAclfNetOptimizer(ContingencyAnalysisAlgorithm dclfAlgo) {
        if (dclfAlgo == null) {
            throw new IllegalArgumentException("DCLF algorithm cannot be null");
        }
        if (dclfAlgo.getNetwork() == null) {
            throw new IllegalArgumentException("Network in DCLF algorithm cannot be null");
        }
        
		this.dclfAlgo = dclfAlgo;
	}
	
	/**
	 * Constructor
	 * 
	 * @param dclfAlgo
	 */
	public BaseAclfNetOptimizer(ContingencyAnalysisAlgorithm dclfAlgo, int genOptSizeLimit, int secOptSizeLimit) {
        this(dclfAlgo);
        this.genOptSizeLimit = genOptSizeLimit;
        this.secOptSizeLimit = secOptSizeLimit;
	}
	 
    // Getter methods
    public BaseStateOptimizer getOptimizer() {
        return optimizer;
    }
    
    public void setOptimizer(BaseStateOptimizer genOptimizer) {
        this.optimizer = genOptimizer;
    }
}
