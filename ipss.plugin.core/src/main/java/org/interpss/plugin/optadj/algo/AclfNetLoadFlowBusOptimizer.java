package org.interpss.plugin.optadj.algo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.optim.linear.Relationship;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.optadj.algo.util.AclfNetGFSsHelper;
import org.interpss.plugin.optadj.algo.util.Sen2DMatrix;
import org.interpss.plugin.optadj.optimizer.BaseStateOptimizer;
import org.interpss.plugin.optadj.optimizer.GenStateOptimizer;
import org.interpss.plugin.optadj.optimizer.bean.DeviceConstrainData;
import org.interpss.plugin.optadj.optimizer.bean.SectionConstrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBus;
import com.interpss.core.algo.dclf.adapter.DclfAlgoGen;

/**
 * AC Load Flow Bus Optimizer for eliminating line overloads by optimizing generator states.
 * Uses GFS (Generator to Flow Sensitivity) analysis to identify control buses.
 * 
 * @author Donghao.F
 */
public class AclfNetLoadFlowBusOptimizer {
    private static final Logger log = LoggerFactory.getLogger(AclfNetLoadFlowBusOptimizer.class);
    
    // Configuration constants
    private static final double GEN_SEN_THRESHOLD = 0.20;
    private static final double SECTION_SEN_THRESHOLD = 0.05;
    private static final double OPT_ADJUSTMENT_THRESHOLD_MW = 0.1;
    
    protected final ContingencyAnalysisAlgorithm dclfAlgo;
    protected final AclfNetGFSsHelper helper;
    protected final AclfNetwork network;
    
    // Performance optimization: cache frequently accessed values
    private Double baseMvaCache;
    private Map<String, DclfAlgoBranch> dclfBranchCache;
    
    protected Map<Integer, AclfBus> controlBusMap;
    protected BaseStateOptimizer optimizer;
    protected Set<String> overLimitBranchList;
    
    /**
     * Constructor for AclfNetLoadFlowBusOptimizer.
     * 
     * @param dclfAlgo DCLF algorithm object used for optimization
     * @throws IllegalArgumentException if dclfAlgo or its network is null
     */
    public AclfNetLoadFlowBusOptimizer(ContingencyAnalysisAlgorithm dclfAlgo) {
        if (dclfAlgo == null) {
            throw new IllegalArgumentException("DCLF algorithm cannot be null");
        }
        if (dclfAlgo.getNetwork() == null) {
            throw new IllegalArgumentException("Network in DCLF algorithm cannot be null");
        }
        
        this.dclfAlgo = dclfAlgo;
        this.network = (AclfNetwork) dclfAlgo.getNetwork();
        this.helper = new AclfNetGFSsHelper(network);
        this.dclfBranchCache = new HashMap<>();
    }

    /**
     * Optimize generator states to eliminate line overloads.
     * 
     * @param threshold Overload threshold in percentage (e.g., 120 for 120%)
     */
    public void optimize(double threshold) {
        // Initialize optimizer if needed
        if (this.optimizer == null) {
            this.optimizer = new GenStateOptimizer();
        }
        
        // 1. Identify overloaded branches
        identifyOverloadedBranches(threshold);
        
        // Early return if no overloads
        if (overLimitBranchList.isEmpty()) {
            log.debug("No overloaded branches found");
            return;
        }
        
        // 2. Build control bus set
        Set<AclfBus> controlBusSet = buildControlBusSet();
        
        // Early return if no control buses
        if (controlBusSet.isEmpty()) {
            log.debug("No suitable control buses found");
            return;
        }
        
        // 3. Create control bus mapping
        controlBusMap = AclfNetGFSsHelper.arrangeIndex(controlBusSet);
        
        // 4. Calculate GFS matrix once for all constraints
        Sen2DMatrix gfsMatrix = calculateGFSMatrix(controlBusSet);
        
        // 5. Build constraints
        buildSectionConstraints(gfsMatrix, threshold);
        buildGenConstraints();
        
        // 6. Execute optimization
        optimizer.optimize();
        
        // 7. Update DCLF algorithm
        updateDclfAlgorithm();
    }

    /**
     * Build control bus set with sensitivity analysis.
     */
    private Set<AclfBus> buildControlBusSet() {
        // Get all generator buses
        Set<AclfBus> genBuses = network.getBusList().stream()
            .filter(bus -> bus.isActive() && bus.isGen())
            .map(bus -> (AclfBus) bus)
            .collect(Collectors.toCollection(HashSet::new));
        
        if (genBuses.isEmpty()) {
            return new HashSet<>();
        }
        
        // Get generator bus IDs
        Set<String> genBusIds = genBuses.stream()
            .map(AclfBus::getId)
            .collect(Collectors.toCollection(HashSet::new));
        
        // Calculate GFS matrix for generator buses to overloaded branches
        Sen2DMatrix gfsMatrix = helper.calGFS(genBusIds, overLimitBranchList);
        
        // Filter buses with sufficient sensitivity
        return genBuses.stream()
            .filter(bus -> hasSufficientSensitivity(gfsMatrix, bus))
            .collect(Collectors.toCollection(HashSet::new));
    }
    
    /**
     * Check if a bus has sufficient sensitivity to any overloaded branch.
     */
    private boolean hasSufficientSensitivity(Sen2DMatrix gfsMatrix, AclfBus bus) {
        int busNo = bus.getSortNumber();
        
        for (String branchId : overLimitBranchList) {
            AclfBranch branch = network.getBranch(branchId);
            if (branch == null) continue;
            
            double sensitivity = gfsMatrix.get(busNo, branch.getSortNumber());
            if (Math.abs(sensitivity) > GEN_SEN_THRESHOLD) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Identify overloaded branches in the network.
     */
    private void identifyOverloadedBranches(double threshold) {
        overLimitBranchList = new HashSet<>();
        
        for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
            if (!isNonSwingBranch(dclfBranch)) continue;
            
            AclfBranch branch = dclfBranch.getBranch();
            double powerFlowMW = dclfAlgo.getBranchFlow(branch, UnitType.mW);
            double ratingMVA = branch.getRatingMva1();
            
            if (ratingMVA <= 0) continue;
            
            double loadingPercent = Math.abs(powerFlowMW) / ratingMVA * 100.0;
            if (loadingPercent > threshold) {
                overLimitBranchList.add(branch.getId());
            }
        }
        
        log.info("Found {} overloaded branches", overLimitBranchList.size());
    }
    
    /**
     * Check if branch is between non-swing buses.
     */
    private boolean isNonSwingBranch(DclfAlgoBranch branch) {
        AclfBranch aclfBranch = branch.getBranch();
        return !aclfBranch.getFromAclfBus().isSwing() 
            && !aclfBranch.getToAclfBus().isSwing();
    }
    
    /**
     * Calculate GFS matrix for control buses.
     */
    private Sen2DMatrix calculateGFSMatrix(Set<AclfBus> controlBusSet) {
        Set<String> controlBusIds = controlBusSet.stream()
            .map(AclfBus::getId)
            .collect(Collectors.toCollection(HashSet::new));
        
        return helper.calGFS(controlBusIds);
    }
    
    /**
     * Build section constraints.
     */
    private void buildSectionConstraints(Sen2DMatrix gfsMatrix, double threshold) {
        double baseMva = getBaseMva();
        
        for (AclfBranch branch : network.getBranchList()) {
            if (!branch.isActive()) continue;
            
            DclfAlgoBranch dclfBranch = getCachedDclfBranch(branch.getId());
            if (dclfBranch == null) continue;
            
            double[] genSenArray = new double[controlBusMap.size()];
            boolean hasSignificantSensitivity = false;
            
            for (Map.Entry<Integer, AclfBus> entry : controlBusMap.entrySet()) {
                int index = entry.getKey();
                AclfBus bus = entry.getValue();
                double sen = gfsMatrix.get(bus.getSortNumber(), branch.getSortNumber());
                
                // Adjust sign based on flow direction
                genSenArray[index] = dclfBranch.getDclfFlow() > 0 ? sen : -sen;
                
                if (Math.abs(genSenArray[index]) > SECTION_SEN_THRESHOLD) {
                    hasSignificantSensitivity = true;
                }
            }
            
            if (hasSignificantSensitivity) {
                double limit = dclfBranch.getBranch().getRatingMva1() * threshold / 100.0;
                double flowMw = Math.abs(dclfBranch.getDclfFlow() * baseMva);
                
                optimizer.addConstraint(new SectionConstrainData(
                    flowMw, Relationship.LEQ, limit, genSenArray));
            }
        }
    }
    
    /**
     * Get DCLF branch with caching for performance.
     */
    private DclfAlgoBranch getCachedDclfBranch(String branchId) {
        DclfAlgoBranch branch = dclfBranchCache.get(branchId);
        if (branch == null) {
            branch = dclfAlgo.getDclfAlgoBranch(branchId);
            if (branch != null) {
                dclfBranchCache.put(branchId, branch);
            }
        }
        return branch;
    }
    
    /**
     * Build generator constraints.
     */
    private void buildGenConstraints() {
        double baseMva = getBaseMva();
        
        for (Map.Entry<Integer, AclfBus> entry : controlBusMap.entrySet()) {
            int index = entry.getKey();
            AclfBus bus = entry.getValue();
            LimitType genLimit = bus.getPGenLimit();
            double currentGenP = bus.getGenP() * baseMva;
            
            // Upper limit constraint
            optimizer.addConstraint(new DeviceConstrainData(
                currentGenP, Relationship.LEQ, genLimit.getMax() * baseMva, index));
            
            // Lower limit constraint
            optimizer.addConstraint(new DeviceConstrainData(
                currentGenP, Relationship.GEQ, genLimit.getMin() * baseMva, index));
        }
    }
    
    /**
     * Update DCLF algorithm with optimization results.
     */
    private void updateDclfAlgorithm() {
        double baseMva = getBaseMva();
        
        for (int i = 0; i < controlBusMap.size(); i++) {
            double adjustmentMW = optimizer.getPoint()[i];
            
            if (Math.abs(adjustmentMW) <= OPT_ADJUSTMENT_THRESHOLD_MW) continue;
            
            AclfBus bus = controlBusMap.get(i);
            DclfAlgoBus dcBus = dclfAlgo.getDclfAlgoBus(bus.getId());
            if (dcBus == null) continue;
            
            log.debug("Bus {} adjustment: {} MW", bus.getName(), adjustmentMW);
            distributeAdjustmentToGenerators(bus, dcBus, adjustmentMW, baseMva);
        }
    }
    
    /**
     * Distribute adjustment to individual generators.
     */
	private void distributeAdjustmentToGenerators(AclfBus bus, DclfAlgoBus dcBus, double adjustmentMW, double baseMva) {
		double totalBusGenP = bus.getGenP() + dcBus.getGenAdjust();
		if (Math.abs(totalBusGenP) < 1e-6)
			return; // Avoid division by zero

		double adjustmentPU = adjustmentMW / baseMva;

		for (DclfAlgoGen gen : dcBus.getGenList()) {
			double genRatio = gen.getGenP() / totalBusGenP;
			double individualAdjustmentPU = adjustmentPU * genRatio;

			gen.setAdjust(individualAdjustmentPU);
			log.debug("Gen {} adjustment: {} MW", gen.getId(), individualAdjustmentPU * baseMva);
		}
	}
    
    /**
     * Get base MVA with caching.
     */
    private double getBaseMva() {
        if (baseMvaCache == null) {
            baseMvaCache = network.getBaseMva();
        }
        return baseMvaCache;
    }
    
    /**
     * Get optimization results as a map.
     */
    public Map<String, Double> getResultMap() {
        Map<String, Double> resultMap = new HashMap<>();
        double baseMva = getBaseMva();
        
        for (int i = 0; i < controlBusMap.size(); i++) {
            double adjustmentMW = optimizer.getPoint()[i];
            
            if (Math.abs(adjustmentMW) > OPT_ADJUSTMENT_THRESHOLD_MW) {
                AclfBus bus = controlBusMap.get(i);
                resultMap.put(bus.getId(), adjustmentMW / baseMva);
            }
        }
        return resultMap;
    }
    
    // Getter methods
    public BaseStateOptimizer getGenOptimizer() {
        return optimizer;
    }
    
    public Map<Integer, AclfBus> getControlGenMap() {
        return controlBusMap != null ? new HashMap<>(controlBusMap) : new HashMap<>();
    }
    
    public void setOptimizer(BaseStateOptimizer genOptimizer) {
        this.optimizer = genOptimizer;
    }
}