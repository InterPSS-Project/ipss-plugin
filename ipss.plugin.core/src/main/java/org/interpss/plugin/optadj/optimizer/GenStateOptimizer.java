package org.interpss.plugin.optadj.optimizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.optim.linear.Relationship;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;
import org.ojalgo.type.context.NumberContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.interpss.plugin.optadj.cluster.GeneratorClustering;
import org.interpss.plugin.optadj.optimizer.bean.BaseConstrainData;
import org.interpss.plugin.optadj.optimizer.bean.GenConstrainData;
import org.interpss.plugin.optadj.optimizer.bean.GeneratorParameter;
import org.interpss.plugin.optadj.optimizer.bean.SectionConstrainData;

/**
 * 
 * @author Donghao.F
 * 
 * @date 2024 May 27 17:19:38
 * 
 * 
 * 
 */
public class GenStateOptimizer {
    private static Logger log = LoggerFactory.getLogger(GenStateOptimizer.class);

    List<GenConstrainData> genConstrainDataList = new ArrayList<GenConstrainData>();
    List<SectionConstrainData> secConstrainDataList = new ArrayList<SectionConstrainData>();
    
    
    public static double senSecLimit = 0.02;
    public static double senGenLimit = 0.02;
    int genSize;
    
    Optimisation.Result result;
    
    ExpressionsBasedModel model;
    
    private double interfaceFactor = 1e4;
    private double sectionFactor = 1e2;
    
    // Cached results
    private double[] cachedDGenP;      // Cached generator adjustments dGenP
    private double[] cachedDSecP;      // Cached section slack variables dSecP, in original order
    
    // Mapping: original section index -> effective section index
    private Map<Integer, Integer> secIndexToEffectiveIndex;
    private int effectiveSecCount;
    
 // Generator clustering related
    private GeneratorClustering.ClusteringResult generatorClusters;  // Clustering result
    private double generatorClusteringThreshold = 0.001;
    
    public GenStateOptimizer() {
        model = new ExpressionsBasedModel();
    }

    public void adConstraint(BaseConstrainData data) {
        if (data instanceof GenConstrainData) {
            genConstrainDataList.add((GenConstrainData) data);
        } else {
            secConstrainDataList.add((SectionConstrainData) data);
        }
    }
    
 // Custom objects
    private Map<Integer, GeneratorParameter> genParams;
    private MergeSectionResult mergeResult;

    public boolean optimize() {
        // Preprocessing phase
        preprocess();
        
        // Build unified optimization model
        buildOptimizationModel();
        
        // Solve phase
        result = model.minimise();
        if (result.getState().isSuccess()) {
            cacheResults(mergeResult);
        }
        
        return result.getState().isSuccess();
    }

    /**
     * Preprocessing: collect data, merge constraints, and build mappings
     */
    private void preprocess() {
        // 1. Collect generator parameters with GeneratorParameter
        genParams = new HashMap<>();
        for (GenConstrainData data : genConstrainDataList) {
            int idx = data.getIndex();
            GeneratorParameter gp = genParams.computeIfAbsent(idx, GeneratorParameter::new);
            gp.setWeight(data.getWeight());
            
            if (data.getRelationship() == Relationship.LEQ) {
                gp.setUpperBound(data.getLimit());
            } else if (data.getRelationship() == Relationship.GEQ) {
                gp.setLowerBound(data.getLimit());
            }
        }
        genSize = genParams.size();
        
        // 2. Preprocess section constraints (merge similar ones)
        mergeResult = mergeSectionsBySensitivity();
        effectiveSecCount = mergeResult.mergedSections.size();
        
        // 3. Build index mappings
        secIndexToEffectiveIndex = new HashMap<>();
        for (int i = 0; i < secConstrainDataList.size(); i++) {
            secIndexToEffectiveIndex.put(i, 
                mergeResult.originalToMergedIndex.getOrDefault(i, -1));
        }
        
        // 4. Build generator clusters
        buildGeneratorClusters();
        
        // 5. Update section sensitivities based on generator clusters
        updateSectionSensitivitiesByClusters();
        
        // 6. Print statistics
        printOptimizationStats(mergeResult);
    }
    
    /**
     * Update section sensitivities based on generator clusters
     * Replace generator sensitivities within a cluster with the cluster average
     */
    private void updateSectionSensitivitiesByClusters() {
        if (generatorClusters == null || generatorClusters.clusters == null) {
            return;
        }
        
        for (MergedSectionData section : mergeResult.mergedSections) {
            double[] originalSen = section.senArray;
            double[] updatedSen = new double[originalSen.length];
            System.arraycopy(originalSen, 0, updatedSen, 0, originalSen.length);
            
            // For each cluster
            for (GeneratorClustering.GeneratorCluster cluster : generatorClusters.clusters) {
                if (cluster.originalIndices == null || cluster.originalIndices.isEmpty()) {
                    continue;
                }
                
                // Compute average generator sensitivity on this section within the cluster
                double sumSen = 0.0;
                int validCount = 0;
                for (int genIdx : cluster.originalIndices) {
                    if (genIdx < originalSen.length) {
                        sumSen += originalSen[genIdx];
                        validCount++;
                    }
                }
                
                if (validCount > 0) {
                    double avgSen = sumSen / validCount;
                    
                    // Set all generator sensitivities in the cluster to the average
                    for (int genIdx : cluster.originalIndices) {
                        if (genIdx < updatedSen.length) {
                            updatedSen[genIdx] = avgSen;
                        }
                    }
                }
            }
            
            // Update section sensitivity array
            section.senArray = updatedSen;
        }
    }
    
    /**
     * Build generator clusters
     */
    private void buildGeneratorClusters() {
        // Prepare sensitivity matrix
        int numSections = mergeResult.mergedSections.size();
        double[][] sensitivities = new double[genSize][numSections];
        
        for (int i = 0; i < genSize; i++) {
            for (int j = 0; j < numSections; j++) {
                MergedSectionData section = mergeResult.mergedSections.get(j);
                if (i < section.senArray.length) {
                    sensitivities[i][j] = section.senArray[i];
                } else {
                    sensitivities[i][j] = 0.0;
                }
            }
        }
        
        // Prepare generator capacity limits and weights
        double[] minCapacities = new double[genSize];
        double[] maxCapacities = new double[genSize];
        double[] weights = new double[genSize];
        
        for (int i = 0; i < genSize; i++) {
            GeneratorParameter gp = genParams.get(i);
            minCapacities[i] = gp.hasLowerBound() ? gp.getLowerBound() : 0.0;
            maxCapacities[i] = gp.hasUpperBound() ? gp.getUpperBound() : Double.MAX_VALUE;
            weights[i] = gp.getWeight();
        }
        
        // Run clustering
        this.generatorClusters = GeneratorClustering.cluster(
            sensitivities, minCapacities, maxCapacities, weights, generatorClusteringThreshold
        );
    }

    /**
     * Build unified optimization model based on clusters
     */
    private void buildOptimizationModel() {
        model = new ExpressionsBasedModel();
        model.options.feasibility = NumberContext.of(4);
        model.options.solution = NumberContext.of(4);
        
        int clusterCount = generatorClusters.clusteredGenCount;
        
        // 1. Create variables for each cluster
        Variable[] pVars = new Variable[clusterCount];
        Variable[] nVars = new Variable[clusterCount];
        for (int c = 0; c < clusterCount; c++) {
            pVars[c] = model.addVariable("cluster_p_" + c).lower(0);
        }
        for (int c = 0; c < clusterCount; c++) {
            nVars[c] = model.addVariable("cluster_n_" + c).lower(0);
        }
        
        // 2. Add cluster constraints
        for (int c = 0; c < clusterCount; c++) {
            GeneratorClustering.GeneratorCluster cluster = generatorClusters.clusters.get(c);
            Expression clusterExpr = model.addExpression("cluster_" + c)
                .set(pVars[c], 1).set(nVars[c], -1);
            
            // Apply lower bound when the cluster has a finite minimum
            if (cluster.totalMinCapacity > Double.NEGATIVE_INFINITY) {
                clusterExpr.lower(cluster.totalMinCapacity);
            }
            // Apply upper bound when the cluster has a finite maximum
            if (cluster.totalMaxCapacity < Double.POSITIVE_INFINITY) {
                clusterExpr.upper(cluster.totalMaxCapacity);
            }
        }
        
        // 3. Section constraints
        Variable[] dSecVars = new Variable[effectiveSecCount];
        for (int i = 0; i < effectiveSecCount; i++) {
            dSecVars[i] = model.addVariable("dSecP_" + i).lower(0);
            addSectionConstraint(mergeResult.mergedSections.get(i), pVars, nVars, dSecVars[i], i);
        }
        
        // 4. Power balance
        Variable sp = model.addVariable("sp").lower(0);
        Variable sn = model.addVariable("sn").lower(0);
        Expression sumBalance = model.addExpression("sum_balance");
        for (int c = 0; c < clusterCount; c++) {
            sumBalance.set(pVars[c], 1).set(nVars[c], -1);
        }
        sumBalance.set(sp, 1).set(sn, -1).level(0);
        
        // 5. Objective function
        Expression objective = model.addExpression("Objective").weight(1.0);
        for (int c = 0; c < clusterCount; c++) {
            GeneratorClustering.GeneratorCluster cluster = generatorClusters.clusters.get(c);
            double w = cluster.weight;
            if (Math.abs(w) > 1e-6) {
                objective.set(pVars[c], w).set(nVars[c], w);
            }
        }
        for (int i = 0; i < effectiveSecCount; i++) {
            objective.set(dSecVars[i], sectionFactor);
        }
        objective.set(sp, interfaceFactor).set(sn, interfaceFactor);
    }

    // Section constraint helper logic
	private void addSectionConstraint(MergedSectionData merged, Variable[] pVars, Variable[] nVars, Variable dSecVar,
			int idx) {
		boolean hasLower = merged.lowerBound > Double.NEGATIVE_INFINITY;
		boolean hasUpper = merged.upperBound < Double.POSITIVE_INFINITY;

		// Use a single Expression
		Expression expr = model.addExpression("section_" + idx);

		for (int c = 0; c < generatorClusters.clusteredGenCount; c++) {
			GeneratorClustering.GeneratorCluster cluster = generatorClusters.clusters.get(c);
			double sen = merged.senArray[cluster.originalIndices.get(0)];
			if (sen != 0)
				expr.set(pVars[c], sen).set(nVars[c], -sen);
		}

		// Apply lower and upper bounds
		if (hasLower)
			expr.lower(merged.lowerBound);
		if (hasUpper)
			expr.upper(merged.upperBound);

		// Add slack variable when needed
		if (merged.lowerBound > 0) {
			expr.set(dSecVar, 1);
		}
		if (merged.upperBound < 0) {
			expr.set(dSecVar, -1);
		}
	}

    private void printOptimizationStats(MergeSectionResult mergeResult) {
        int originalEffectiveCount = (int) mergeResult.originalToMergedIndex.values().stream()
            .filter(v -> v >= 0).count();
        
        StringBuffer stats = new StringBuffer("\n");
        stats.append("========== Section Merge Optimization Stats ==========\n");
        stats.append("Original section count: ").append(secConstrainDataList.size()).append('\n');
        stats.append("Effective sections (after filtering): ").append(originalEffectiveCount).append('\n');
        stats.append("Merged section count: ").append(effectiveSecCount).append('\n');
        if (originalEffectiveCount > 0) {
            stats.append(String.format("Section reduction rate: %.1f%%%n",
                (1 - (double) effectiveSecCount / originalEffectiveCount) * 100));
        }
        stats.append("Total variables: ").append(genSize * 2 + effectiveSecCount + 2).append('\n');
        stats.append("Total constraints: ").append(genSize + effectiveSecCount + 1).append('\n');
        stats.append("======================================\n");
        log.info(stats.toString());
    }

    private void cacheResults(MergeSectionResult mergeResult) {
        double[] raw = result.toRawCopy1D();
        int clusterCount = generatorClusters.clusteredGenCount;
        
        // 1. Read each cluster adjustment first
        double[] clusterAdjustments = new double[clusterCount];
        for (int c = 0; c < clusterCount; c++) {
            clusterAdjustments[c] = raw[c] - raw[c + clusterCount];
        }
        
        // 2. Distribute cluster adjustments back to individual generators (proportional allocation)
        cachedDGenP = new double[genSize];
        for (int c = 0; c < clusterCount; c++) {
            GeneratorClustering.GeneratorCluster cluster = generatorClusters.clusters.get(c);
            double totalAdjustment = clusterAdjustments[c];
            
            if (Math.abs(totalAdjustment) < 1e-3) continue;
            
            // Use total capacity stored in the cluster directly
            double totalCap = (totalAdjustment > 0) ? cluster.totalMaxCapacity : -cluster.totalMinCapacity;
            
            for (int genIdx : cluster.originalIndices) {
                GeneratorParameter gp = genParams.get(genIdx);
                double genMin = gp.hasLowerBound() ? gp.getLowerBound() : 0.0;
                double genMax = gp.hasUpperBound() ? gp.getUpperBound() : Double.MAX_VALUE;
                
                double cap;
                if (totalAdjustment > 0) {
                    cap = genMax;
                } else {
                    cap = -genMin;
                }
                
                cachedDGenP[genIdx] = totalAdjustment * (cap / totalCap);
            }
        }
        
        // 3. Cache section slack variables; allocation logic unchanged, distribution may need tuning
        cachedDSecP = new double[secConstrainDataList.size()];
        int dSecOffset = clusterCount * 2;  // Note: offset is clusterCount * 2
        for (int i = 0; i < secConstrainDataList.size(); i++) {
            Integer effIdx = mergeResult.originalToMergedIndex.get(i);
            cachedDSecP[i] = (effIdx != null && effIdx >= 0) ? raw[dSecOffset + effIdx] : 0.0;
        }
    }

    /**
     * Merge section constraints with identical sensitivity profiles
     * Criterion: all generator sensitivity deviations are below 0.001
     */
    private MergeSectionResult mergeSectionsBySensitivity() {
        final double TOLERANCE = 0.001;
        
        // Sensitivity signature -> section group
        Map<String, List<SectionConstrainData>> groups = new HashMap<>();
        Map<SectionConstrainData, Integer> originalIndex = new HashMap<>();
        
        // Build original index mapping
        for (int i = 0; i < secConstrainDataList.size(); i++) {
            originalIndex.put(secConstrainDataList.get(i), i);
        }
        
        // Group valid sections by sensitivity signature
        for (SectionConstrainData data : secConstrainDataList) {
            if (!isValidSection(data)) continue;
            
            String signature = getSensitivitySignature(data.getSenArray(), TOLERANCE);
            groups.computeIfAbsent(signature, k -> new ArrayList<>()).add(data);
        }
        
        // Build merged result
        List<MergedSectionData> mergedSections = new ArrayList<>();
        Map<Integer, Integer> indexMap = new HashMap<>();
        
        for (List<SectionConstrainData> group : groups.values()) {
            MergedSectionData merged = new MergedSectionData();
            merged.senArray = filterSensitivity(group.get(0).getSenArray());
            merged.lowerBound = Double.NEGATIVE_INFINITY;
            merged.upperBound = Double.POSITIVE_INFINITY;
            merged.originalIndices = new ArrayList<>();
            
            // Merge constraint bounds
            for (SectionConstrainData data : group) {
                double bound = data.getLimit() - data.getValue();
                if (data.getRelationship() == Relationship.LEQ) {
                    merged.upperBound = Math.min(merged.upperBound, bound);
                } else {
                    merged.lowerBound = Math.max(merged.lowerBound, bound);
                }
                merged.originalIndices.add(originalIndex.get(data));
            }
            
            int mergedIdx = mergedSections.size();
            mergedSections.add(merged);
            for (int origIdx : merged.originalIndices) {
                indexMap.put(origIdx, mergedIdx);
            }
        }
        
        return new MergeSectionResult(mergedSections, indexMap);
    }

    // Check whether a section is effective
    private boolean isValidSection(SectionConstrainData data) {
        for (double v : data.getSenArray()) {
            if (Math.abs(v) >= senSecLimit) return true;
        }
        return false;
    }

    // Build sensitivity signature (quantized concatenation)
    private String getSensitivitySignature(double[] senArray, double tolerance) {
        StringBuilder sb = new StringBuilder();
        int len = Math.min(senArray.length, genSize);
        for (int i = 0; i < len; i++) {
            double val = Math.abs(senArray[i]) < senGenLimit ? 0 : senArray[i];
            long quantized = Math.round(val / tolerance);
            sb.append(quantized).append(',');
        }
        return sb.toString();
    }

    // Filter sensitivity values
    private double[] filterSensitivity(double[] senArray) {
        int len = Math.min(senArray.length, genSize);
        double[] filtered = new double[len];
        for (int i = 0; i < len; i++) {
            filtered[i] = Math.abs(senArray[i]) < senGenLimit ? 0 : senArray[i];
        }
        return filtered;
    }

    /**
     * Section merge result
     */
    private static class MergeSectionResult {
        final List<MergedSectionData> mergedSections;
        final Map<Integer, Integer> originalToMergedIndex;
        
        MergeSectionResult(List<MergedSectionData> mergedSections, 
                           Map<Integer, Integer> originalToMergedIndex) {
            this.mergedSections = mergedSections;
            this.originalToMergedIndex = originalToMergedIndex;
        }
    }

    /**
     * Merged section data
     */
    private static class MergedSectionData {
        double[] senArray;
        double lowerBound;
        double upperBound;
        List<Integer> originalIndices;
    }
    
    /**
     * Get generator adjustment dGenP
     * @param genIndex original generator index
     * @return adjustment value
     */
    public double getDGenP(int genIndex) {
        if (cachedDGenP == null || genIndex < 0 || genIndex >= genSize) {
            return 0.0;
        }
        return cachedDGenP[genIndex];
    }
    
    /**
     * Get section slack variable dSecP
     * @param secIndex original section index
     * @return slack variable value
     */
    public double getDSecP(int secIndex) {
        if (cachedDSecP == null || secIndex < 0 || secIndex >= cachedDSecP.length) {
            return 0.0;
        }
        return cachedDSecP[secIndex];
    }
    
    public double[] getPoint() {
        
        if (result == null || !result.getState().isSuccess()) {
            return null;
        }
        
        double[] raw = result.toRawCopy1D();
        int clusterCount = generatorClusters.clusteredGenCount;
        
        // Legacy layout: dGenP(genSize) + dSecP(secSize)
        int legacySize = genSize + secConstrainDataList.size();
        double[] legacy = new double[legacySize];
        
        // 1. Read each cluster adjustment first
        double[] clusterAdjustments = new double[clusterCount];
        for (int c = 0; c < clusterCount; c++) {
            clusterAdjustments[c] = raw[c] - raw[c + clusterCount];
        }
        
        // 2. Distribute cluster adjustments back to generators (same as cacheResults)
        for (int c = 0; c < clusterCount; c++) {
            GeneratorClustering.GeneratorCluster cluster = generatorClusters.clusters.get(c);
            double totalAdjustment = clusterAdjustments[c];
            double totalCapacityRange = cluster.totalMaxCapacity - cluster.totalMinCapacity;
            
            for (int genIdx : cluster.originalIndices) {
                GeneratorParameter gp = genParams.get(genIdx);
                double genMin = gp.hasLowerBound() ? gp.getLowerBound() : 0.0;
                double genMax = gp.hasUpperBound() ? gp.getUpperBound() : Double.MAX_VALUE;
                double genRange = genMax - genMin;
                
                if (totalCapacityRange > 1e-6) {
                    legacy[genIdx] = totalAdjustment * (genRange / totalCapacityRange);
                } else {
                    legacy[genIdx] = totalAdjustment / cluster.originalIndices.size();
                }
            }
        }
        
        // 3. dSecP_i in original order; ineffective sections are 0
        int dSecOffset = clusterCount * 2;  // offset is clusterCount * 2
        for (int i = 0; i < secConstrainDataList.size(); i++) {
            Integer effIdx = secIndexToEffectiveIndex.get(i);
            if (effIdx != null && effIdx >= 0) {
                legacy[genSize + i] = raw[dSecOffset + effIdx];
            } else {
                legacy[genSize + i] = 0.0;
            }
        }
       
        return legacy;
    }

    public void setInterfaceFactor(double interfaceFactor) {
        this.interfaceFactor = interfaceFactor;
    }

    public void setSectionFactor(double sectionFactor) {
        this.sectionFactor = sectionFactor;
    }
    
    public boolean isAllControl() {
        if (cachedDSecP == null) {
            return false;
        }
        for (int i = 0; i < cachedDSecP.length; i++) {
            if (Math.abs(cachedDSecP[i]) > 1e-4) {
                SectionConstrainData section = secConstrainDataList.get(i);
                System.out.println("out of control:" + i + "," + section.getName() 
                        + "," + Math.abs(cachedDSecP[i]) + "," + section);
                return false;
            }
        }
        return true;
    }
    
    /**
     * Print all generator adjustments
     */
    public void printAllDGenP() {
        if (cachedDGenP == null) {
            System.out.println("No valid solution available");
            return;
        }
        System.out.println("=== Generator Adjustments dGenP ===");
        for (int i = 0; i < cachedDGenP.length; i++) {
            System.out.printf("Generator %d: %.6f\n", i, cachedDGenP[i]);
        }
    }

    /**
     * Print all section slack variables
     */
    public void printAllDSecP() {
        if (cachedDSecP == null) {
            System.out.println("No valid solution available");
            return;
        }
        System.out.println("=== Section Slack Variables dSecP ===");
        for (int i = 0; i < cachedDSecP.length; i++) {
            String sectionName = secConstrainDataList.get(i).getName();
            System.out.printf("Section %d (%s): %.6f\n", i, sectionName, cachedDSecP[i]);
        }
    }

	public double[] getCachedDGenP() {
		return cachedDGenP;
	}
	public double[] getCachedDSecP() {
		return cachedDSecP;
	}
	
	public double getSen(int row, int col) {
		return this.secConstrainDataList.get(col).getSenArray()[row];
	}
    
}

