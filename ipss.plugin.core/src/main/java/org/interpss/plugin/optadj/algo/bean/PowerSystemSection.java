package org.interpss.plugin.optadj.algo.bean;
/** 

* @author  Donghao.F 

* @date 2026 Jan 6 10:16:50 

* 

*/
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ejml.data.DMatrixSparseCSC;
import org.interpss.plugin.optadj.algo.util.AclfNetSensSparseHelper;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.algo.dclf.DclfAlgorithm;


/**
 * Power system section model.
 * Stores section branches, branch coefficients, and generator sensitivity data.
 */
public class PowerSystemSection {
	
	private final String sectionName;
    
    // 1. Branch IDs in this section
    private final List<String> branchIds;
    
    // 2. Branch coefficient map (branch ID -> coefficient)
    private final Map<String, Double> branchCoefficients;
    
    // 3. Generator-to-section sensitivity map (generator ID -> sensitivity)
    private final Map<String, Double> generatorSensitivities;
    
    // Current section power (MW)
    private double currentPower;
    
    // Upper limit
    private double upper;
    
    // Upper limit
    private double lower;
    
    
    /**
     * Constructor
     * @param branchIds branch ID list
     * @param branchCoefficients branch coefficient map
     * @param generatorSensitivities generator sensitivity map
     */
	public PowerSystemSection(List<String> branchIds, Map<String, Double> branchCoefficients,
			Map<String, Double> generatorSensitivities, double upper, double lower, String sectionName) {
		this.sectionName = sectionName;
		this.branchIds = new ArrayList<>(branchIds);
		this.branchCoefficients = new HashMap<>(branchCoefficients);
		this.generatorSensitivities = new HashMap<>(generatorSensitivities);
		this.upper = upper;
		this.lower = lower;
		validateData();
	}
	
	public PowerSystemSection(List<String> branchIds, Map<String, Double> branchCoefficients,
			Map<String, Double> generatorSensitivities, double upper, String sectionName) {
		this(branchIds, branchCoefficients, generatorSensitivities, upper, Double.NEGATIVE_INFINITY, sectionName);
	}
    
    /**
     * Data validation
     * Ensures branch IDs and coefficient map are consistent
     */
    private void validateData() {
        // Check that every branch in branchIds has a coefficient
        for (String branchId : branchIds) {
            if (!branchCoefficients.containsKey(branchId)) {
                throw new IllegalArgumentException("Branch " + branchId + " is missing from the coefficient map");
            }
        }
        
        // Check that every coefficient entry appears in branchIds
        for (String branchId : branchCoefficients.keySet()) {
            if (!branchIds.contains(branchId)) {
                throw new IllegalArgumentException("Branch " + branchId + " in coefficient map is not in branch ID list");
            }
        }
    }
    
    // ==================== Getters ====================
    
    public List<String> getBranchIds() {
        return Collections.unmodifiableList(branchIds);
    }
    
    public Map<String, Double> getBranchCoefficients() {
        return Collections.unmodifiableMap(branchCoefficients);
    }
    
    public Map<String, Double> getGeneratorSensitivities() {
        return Collections.unmodifiableMap(generatorSensitivities);
    }
    
    /**
     * Get the coefficient for a branch
     * @param branchId branch ID
     * @return branch coefficient
     */
    public Double getBranchCoefficient(String branchId) {
        return branchCoefficients.get(branchId);
    }
    
    /**
     * Get the sensitivity for a generator
     * @param generatorId generator ID
     * @return sensitivity value
     */
    public Double getGeneratorSensitivity(String generatorId) {
        return generatorSensitivities.get(generatorId);
    }
    
    /**
     * Check whether a branch belongs to this section
     * @param branchId branch ID
     * @return whether the branch is included
     */
    public boolean containsBranch(String branchId) {
        return branchIds.contains(branchId);
    }
    
    /**
     * Get the number of branches in this section
     * @return branch count
     */
    public int getSectionSize() {
        return branchIds.size();
    }
    
    
    public String getSectionName() {
		return sectionName;
	}

	/**
     * Compute generator-to-section sensitivity from branch sensitivities
     * Formula: generator-to-section sensitivity = sum(branch sensitivity * branch coefficient)
     * 
     * @param generatorBranchSensitivities generator-to-branch sensitivity map
     * @return generator-to-section sensitivity
     */
    private double calculateGeneratorToSectionSensitivity(Map<String, Double> generatorBranchSensitivities) {
        if (generatorBranchSensitivities == null || generatorBranchSensitivities.isEmpty()) {
            return 0.0;
        }
        
        double sectionSensitivity = 0.0;
        
        for (String branchId : branchIds) {
            Double branchSensitivity = generatorBranchSensitivities.get(branchId);
            Double branchCoefficient = branchCoefficients.get(branchId);
            
            if (branchSensitivity != null && branchCoefficient != null) {
                sectionSensitivity += branchSensitivity * branchCoefficient;
            }
        }
        
        return sectionSensitivity;
    }
    
    /**
     * Calculate all section data (aggregate entry point)
     * Execution order:
     * 1. Build the generator sensitivity matrix
     * 2. Compute generator-to-section sensitivities from the matrix
     * 3. Calculate current section power
     * 
     * Convenience method that runs all intermediate calculations in one call
     * 
     * @param net ACLF network model used to obtain topology and operating data
     * 
     * Flow:
     * +----------------------------------------------+
     * |            calculate(net)                    |
     * |                                              |
     * | 1. Build sensitivity matrix with             |
     * |    AclfNetSensHelper                         |
     * |    -> sparse matrix sen[generator bus][branch] |
     * |                                              |
     * | 2. call calculate(net, sen)                  |
     * |    -> compute generator-to-section sensitivities |
     * |                                              |
     * | 3. call calculateCurrentPower(net)           |
     * |    -> compute actual current section power   |
     * +----------------------------------------------+
     * 
     * Notes:
     * - Sensitivity calculation is delegated to AclfNetSensHelper
     * - Section power calculation depends on the current operating state
     * - Order matters because generator sensitivities require the sensitivity matrix
     * - This method updates internal sensitivity data and current power
     * 
     * @see #calculate(AclfNetwork, double[][])
     * @see #calculateCurrentPower(AclfNetwork)
     * @see AclfNetSensSparseHelper
     */
    public void calculate(AclfNetwork net) {
        // Step 1: build generator sensitivity matrix
        // Use AclfNetSensHelper to compute generator-to-branch sensitivities
        // sen[i][j] is the sensitivity of generator bus i to branch j
        DMatrixSparseCSC sen = new AclfNetSensSparseHelper(net).calSenSortNumber();
        
        // Step 2: compute generator-to-section sensitivities from the matrix
        // Formula: generator-to-section sensitivity = sum(branch sensitivity * branch coefficient)
        calculate(net, net.getAclfGenNameLookupTable(), sen);
        
        // Step 3: compute actual current section power
        // Formula: section power = sum(branch flow * branch coefficient)
        calculateCurrentPower(net);
    }
	
	/**
	 * Calculate current section power from the ACLF network
	 * Formula: section power = sum(branch flow * branch coefficient)
	 * 
	 * @param net ACLF network model
	 * @return current total section power
	 */
	public double calculateCurrentPower(AclfNetwork net) {
	    if (net == null) {
	        throw new IllegalArgumentException("ACLF network must not be null");
	    }
	    
	    double totalPower = 0.0;
	    
	    for (String branchId : branchIds) {
	        // Get branch object
	        AclfBranch branch = net.getBranch(branchId);
	        if (branch == null) {
	            // Skip missing branches instead of throwing
	            continue;
	        }
	        
	        // Get branch coefficient
	        Double coefficient = branchCoefficients.get(branchId);
	        if (coefficient == null) {
	            coefficient = 1.0; // default coefficient is 1.0
	        }
	        
	        // Get branch active power
	        double branchPower = branch.powerFrom2To().getReal();
	        
	       
	        // Accumulate branch flow * branch coefficient
	        totalPower += branchPower * coefficient;
//	        System.out.println(branch.getId()+branchPower * coefficient);
	    }
	    
	    // Update and return current power
	    this.currentPower = totalPower;
	    return totalPower;
	}
	
	public double calculateCurrentPower(DclfAlgorithm dcAlgo) {
	    BaseAclfNetwork<?, ?> net = dcAlgo.getNetwork();
	    
	    double totalPower = 0.0;
	    
	    for (String branchId : branchIds) {
	        // Get branch object
	        AclfBranch branch = net.getBranch(branchId);
	        if (branch == null) {
	            // Skip missing branches instead of throwing
	            continue;
	        }
	        
	        // Get branch coefficient
	        Double coefficient = branchCoefficients.get(branchId);
	        if (coefficient == null) {
	            coefficient = 1.0; // default coefficient is 1.0
	        }
	        
	        // Get branch active power
	        double branchPower = dcAlgo.getBranchFlow(branchId);
	        
	       
	        // Accumulate branch flow * branch coefficient
	        totalPower += branchPower * coefficient;
//	        System.out.println(branch.getId()+branchPower * coefficient);
	    }
	    
	    // Update and return current power
	    this.currentPower = totalPower;
	    return totalPower;
	}
	
	
	/**
	 * Compute generator-to-section sensitivities from the network model and sensitivity matrix
	 * @param net network model
	 * @param generatorMap 
	 * @param sen sensitivity matrix [generator bus][branch]
	 */
	public void calculate(AclfNetwork net, Map<String, AclfGen> generatorMap, DMatrixSparseCSC sen) {
	    if (net == null || sen == null) {
	        throw new IllegalArgumentException("Network model and sensitivity matrix must not be null");
	    }
	    
	    // Iterate over all generators
	    for (Map.Entry<String, AclfGen> entry : generatorMap.entrySet()) {
	        String generatorId = entry.getKey();
	        AclfGen generator = entry.getValue();
	        BaseAclfBus<?, ?> parentBus = generator.getParentBus();
	        
	        if (parentBus == null) {
	            continue; // skip generators without a parent bus
	        }
	        
	        // Collect generator-to-branch sensitivities for section branches
	        Map<String, Double> branchSensitivities = new HashMap<>();
	        
	        for (String branchId : this.branchIds) {
	            AclfBranch branch = net.getBranch(branchId);
	            if (branch == null) {
	                continue; // skip branches not in the network
	            }
	            
	            try {
	                // Read sensitivity from the sparse matrix
	                int busIndex = parentBus.getSortNumber();
	                int branchIndex = branch.getSortNumber();

					// Check matrix bounds

					double sensitivity = sen.get(busIndex, branchIndex);
					branchSensitivities.put(branchId, sensitivity);

	            } catch (Exception e) {
	                // Handle possible conversion errors
	                branchSensitivities.put(branchId, 0.0);
	            }
	        }
	        
	        // Compute generator-to-section sensitivity
			double sectionSensitivity = calculateGeneratorToSectionSensitivity(branchSensitivities);
			if (Math.abs(sectionSensitivity) > 0.001) {
				this.generatorSensitivities.put(generatorId, sectionSensitivity);
			}
	    }
	}
    
    // ==================== Builder (optional) ====================
    
    /**
     * Builder for constructing PowerSystemSection instances
     */
    public static class Builder {
    	private String sectionName;
        private List<String> branchIds = new ArrayList<>();
        private Map<String, Double> branchCoefficients = new HashMap<>();
        private Map<String, Double> generatorSensitivities = new HashMap<>();
        // Upper limit
        private double upper = Double.POSITIVE_INFINITY;
        
        // Upper limit
        private double lower = Double.NEGATIVE_INFINITY;
        public Builder addBranch(String branchId, double coefficient) {
            branchIds.add(branchId);
            branchCoefficients.put(branchId, coefficient);
            return this;
        }
        
        public Builder addGeneratorSensitivity(String generatorId, double sensitivity) {
            generatorSensitivities.put(generatorId, sensitivity);
            return this;
        }
        
        public Builder setGeneratorSensitivities(Map<String, Double> sensitivities) {
            this.generatorSensitivities = new HashMap<>(sensitivities);
            return this;
        }
        
        public Builder upper(double upper) {
            this.upper = upper;
            return this;
        }
        
        public Builder lower(double lower) {
            this.lower = lower;
            return this;
        }
        
        
        public PowerSystemSection build() {
            return new PowerSystemSection(branchIds, branchCoefficients, generatorSensitivities,upper, lower,sectionName);
        }

		public Builder setSectionName(String sectionName) {
			this.sectionName = sectionName;
			return this;
		}
        
    }
    
    // ==================== Object overrides ====================
    
    @Override
    public String toString() {
        return String.format("PowerSystemSection{branches=%d, generators=%d}", 
                branchIds.size(), generatorSensitivities.size());
    }

	public double getCurrentPower() {
		return currentPower;
	}

	public void setCurrentPower(double currentPower) {
		this.currentPower = currentPower;
	}

	public double getUpper() {
		return upper;
	}

	public void setUpper(double upper) {
		this.upper = upper;
	}

	public double getLower() {
		return lower;
	}

	public void setLower(double lower) {
		this.lower = lower;
	}

    
    
}
