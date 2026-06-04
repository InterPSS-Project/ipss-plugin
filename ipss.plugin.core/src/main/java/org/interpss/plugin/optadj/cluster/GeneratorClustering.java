package org.interpss.plugin.optadj.cluster;
/** 

* @author  Donghao.F 

* @date 2026��4��17�� ����11:02:04 

* 

*/

import java.util.*;

/**
 * ��������๤����
 * �������������ƫ����п��پ���
 * Ҫ��weight��ȫһ�²��ܾ���
 */
public class GeneratorClustering {
    
    private static final double DEFAULT_THRESHOLD = 0.001;
    
    /**
     * ��������
     */
    public static class ClusteringResult {
        public List<GeneratorCluster> clusters;           // �����б�
        public Map<Integer, Integer> genToClusterMap;     // ԭʼ��������� -> ����ID
        public int originalGenCount;                      // ԭʼ���������
        public int clusteredGenCount;                     // ���������
        public double reductionRate;                      // ������
        public double threshold;                          // ʹ�õ���ֵ
    }
    
    /**
     * ����������
     */
    public static class GeneratorCluster {
        public int clusterId;
        public List<Integer> originalIndices;              // ԭʼ����������б�
        public double[] representativeSensitivity;         // ����������������ȣ���һ����
        public double weight;                               // ����Ȩ�أ����з����weight����һ�£�
        public double totalMinCapacity;                    // ����С����
        public double totalMaxCapacity;                    // ��������
        
        public int size() {
            return originalIndices.size();
        }
    }
    
    /**
     * ʹ��Ĭ����ֵ���о���
     */
    public static ClusteringResult cluster(double[][] sensitivities,
                                            double[] minCapacities,
                                            double[] maxCapacities,
                                            double[] weights) {
        return cluster(sensitivities, minCapacities, maxCapacities, weights, DEFAULT_THRESHOLD);
    }
    
    /**
     * ʹ��ָ����ֵ���о���
     * 
     * @param sensitivities �����Ⱦ��� [���������][��������]
     * @param minCapacities �������С���� [���������]
     * @param maxCapacities ����������� [���������]
     * @param weights �����Ȩ�� [���������]��������ȫһ�²��ܾ��ࣩ
     * @param threshold ƫ����ֵ����һ������0.001��
     * @return ������
     */
    public static ClusteringResult cluster(double[][] sensitivities,
                                            double[] minCapacities,
                                            double[] maxCapacities,
                                            double[] weights,
                                            double threshold) {
        
        if (sensitivities == null || sensitivities.length == 0) {
            throw new IllegalArgumentException("�����Ⱦ�����Ϊ��");
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
                
                // ����1��weight������ȫһ��
                if (Math.abs(w[j] - cluster.weight) > 1e-9) {
                    continue;
                }
                
                // ����2�������ȱ�������
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
        
        // ��ӡ��Ҫ��Ϣ
        System.out.printf("���������: %d -> %d (����%.1f%%, ��ֵ=%.4f)%n",
            result.originalGenCount, result.clusteredGenCount, 
            result.reductionRate, result.threshold);
        
        return result;
    }
    
    /**
     * �ж����������������Ƿ����ƣ����ƫ���
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
     * ��ȡ�����еķ�����б�
     */
    public static List<Integer> getGeneratorsInCluster(ClusteringResult result, int clusterId) {
        if (clusterId < 0 || clusterId >= result.clusters.size()) {
            throw new IllegalArgumentException("��Ч�ľ���ID: " + clusterId);
        }
        return new ArrayList<>(result.clusters.get(clusterId).originalIndices);
    }
    
    /**
     * ��ȡĳ��������ľ���ID
     */
    public static int getClusterId(ClusteringResult result, int generatorIndex) {
        Integer clusterId = result.genToClusterMap.get(generatorIndex);
        if (clusterId == null) {
            throw new IllegalArgumentException("���������������: " + generatorIndex);
        }
        return clusterId;
    }
}
