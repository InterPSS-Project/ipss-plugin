package org.interpss.plugin.optadj.algo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.optim.linear.Relationship;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.optadj.algo.result.AclfNetSsaResultContainer;
import org.interpss.plugin.optadj.algo.util.AclfNetGFSsHelper;
import org.interpss.plugin.optadj.algo.util.Sen2DMatrix;
import org.interpss.plugin.optadj.optimizer.BaseStateOptimizer;
import org.interpss.plugin.optadj.optimizer.GenStateOptimizer;
import org.interpss.plugin.optadj.optimizer.bean.DeviceConstrainData;
import org.interpss.plugin.optadj.optimizer.bean.SectionConstrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;

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
