package org.interpss.plugin.optadj.cluster;
/** 

* @author  Donghao.F 

* @date 2026 Apr 17 11:02:04 

* 

*/

import java.util.*;

/**
 * Generator clustering utility.
 * Clusters generators with similar sensitivity profiles for faster optimization.
 * Generators can be clustered only when their weights are identical.
 */
public class GeneratorClustering {
    
    private static final double DEFAULT_THRESHOLD = 0.001;
    
    /**
     * Clustering result.
     */
    public static class ClusteringResult {
        public List<GeneratorCluster> clusters;           // cluster list
        public Map<Integer, Integer> genToClusterMap;     // original generator index -> cluster ID
        public int originalGenCount;                      // original generator count
        public int clusteredGenCount;                     // clustered generator count
        public double reductionRate;                      // reduction rate
        public double threshold;                          // threshold used
    }
    
    /**
     * Generator cluster.
     */
    public static class GeneratorCluster {
        public int clusterId;
        public List<Integer> originalIndices;              // original generator index list
        public double[] representativeSensitivity;         // representative sensitivity (first member)
        public double weight;                               // cluster weight (all members must match)
        public double totalMinCapacity;                    // total minimum capacity
        public double totalMaxCapacity;                    // total maximum capacity
        
        public int size() {
            return originalIndices.size();
        }
    }
    
    /**
     * Cluster using the default threshold.
     */
    public static ClusteringResult cluster(double[][] sensitivities,
                                            double[] minCapacities,
                                            double[] maxCapacities,
                                            double[] weights) {
        return cluster(sensitivities, minCapacities, maxCapacities, weights, DEFAULT_THRESHOLD);
    }
    
    /**
     * Cluster using the specified threshold.
     * 
     * @param sensitivities sensitivity matrix [generator count][section count]
     * @param minCapacities minimum capacity per generator [generator count]
     * @param maxCapacities maximum capacity per generator [generator count]
     * @param weights generator weights [generator count]; clustering requires identical weights
     * @param threshold deviation threshold, typically around 0.001
     * @return clustering result
     */
    public static ClusteringResult cluster(double[][] sensitivities,
                                            double[] minCapacities,
                                            double[] maxCapacities,
                                            double[] weights,
                                            double threshold) {
        
        if (sensitivities == null || sensitivities.length == 0) {
            throw new IllegalArgumentException("Sensitivity matrix must not be empty");
        }
        
        int numGens = sensitivities.length;
        
        double[] mins = (minCapacities != null) ? minCapacities : new double[numGens];
        double[] maxs = (maxCapacities != null) ? maxCapacities : new double[numGens];
        double[] w = (weights != null) ? weights : new double[numGens];
        
        boolean[] visited = new boolean[numGens];
        List<GeneratorCluster> clusters = new ArrayList<>();
        Map<Integer, Integer> genToClusterMap = new HashMap<>();
        
        for (int i = 0; i < numGens; i++) {
            if (visited[i]) continue;
            
            GeneratorCluster cluster = new GeneratorCluster();
            cluster.clusterId = clusters.size();
            cluster.originalIndices = new ArrayList<>();
            cluster.originalIndices.add(i);
            cluster.representativeSensitivity = sensitivities[i];
            cluster.weight = w[i];
            cluster.totalMinCapacity = mins[i];
            cluster.totalMaxCapacity = maxs[i];
            visited[i] = true;
            genToClusterMap.put(i, cluster.clusterId);
            
            for (int j = i + 1; j < numGens; j++) {
                if (visited[j]) continue;
                
                // condition 1: weights must be identical
                if (Math.abs(w[j] - cluster.weight) > 1e-9) {
                    continue;
                }
                
                // condition 2: sensitivity profiles must be similar
                if (isSimilar(sensitivities[i], sensitivities[j], threshold)) {
                    cluster.originalIndices.add(j);
                    cluster.totalMinCapacity += mins[j];
                    cluster.totalMaxCapacity += maxs[j];
                    visited[j] = true;
                    genToClusterMap.put(j, cluster.clusterId);
                }
            }
            
            clusters.add(cluster);
        }
        
        ClusteringResult result = new ClusteringResult();
        result.clusters = clusters;
        result.genToClusterMap = genToClusterMap;
        result.originalGenCount = numGens;
        result.clusteredGenCount = clusters.size();
        result.reductionRate = (1 - (double)clusters.size() / numGens) * 100;
        result.threshold = threshold;
        
        // print summary
        System.out.printf("Generator clustering: %d -> %d (reduced %.1f%%, threshold=%.4f)%n",
            result.originalGenCount, result.clusteredGenCount, 
            result.reductionRate, result.threshold);
        
        return result;
    }
    
    /**
     * Check whether two sensitivity profiles are similar (max absolute difference).
     */
    private static boolean isSimilar(double[] sens1, double[] sens2, double threshold) {
        for (int i = 0; i < sens1.length; i++) {
            if (Math.abs(sens1[i] - sens2[i]) > threshold) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Get the generator list in a cluster.
     */
    public static List<Integer> getGeneratorsInCluster(ClusteringResult result, int clusterId) {
        if (clusterId < 0 || clusterId >= result.clusters.size()) {
            throw new IllegalArgumentException("Invalid cluster ID: " + clusterId);
        }
        return new ArrayList<>(result.clusters.get(clusterId).originalIndices);
    }
    
    /**
     * Get the cluster ID for a generator index.
     */
    public static int getClusterId(ClusteringResult result, int generatorIndex) {
        Integer clusterId = result.genToClusterMap.get(generatorIndex);
        if (clusterId == null) {
            throw new IllegalArgumentException("Generator index out of range: " + generatorIndex);
        }
        return clusterId;
    }
}
