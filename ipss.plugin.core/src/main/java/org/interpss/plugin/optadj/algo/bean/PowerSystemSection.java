package org.interpss.plugin.optadj.algo.bean;
/** 

* @author  Donghao.F 

* @date 2026��1��6�� ����10:16:50 

* 

*/
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ejml.data.DMatrixSparseCSC;
import org.interpss.plugin.optadj.algo.util.AclfNetSensHelper;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.algo.dclf.DclfAlgorithm;


/**
 * ����ϵͳ����������
 * �����������֧·��֧·ϵ���ͻ�����������Ϣ
 */
public class PowerSystemSection {
	
	private final String sectionName;
    
    // 1. �������֧·ID�б�
    private final List<String> branchIds;
    
    // 2. ֧·ϵ��ӳ�䣨֧·ID -> ϵ����
    private final Map<String, Double> branchCoefficients;
    
    // 3. ����Զ����������ӳ�䣨����ID -> �����ȣ�
    private final Map<String, Double> generatorSensitivities;
    
    // ���浱ǰ����ֵ��MW��
    private double currentPower;
    
    // ����
    private double upper;
    
    // ����
    private double lower;
    
    
    /**
     * ���캯��
     * @param branchIds ֧·ID�б�
     * @param branchCoefficients ֧·ϵ��ӳ��
     * @param generatorSensitivities ����������ӳ��
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
     * ������֤
     * ȷ��֧·ID�б���ϵ��ӳ���һ����
     */
    private void validateData() {
        // ���branchIds�е�ÿ��֧·�Ƿ��ж�Ӧ��ϵ��
        for (String branchId : branchIds) {
            if (!branchCoefficients.containsKey(branchId)) {
                throw new IllegalArgumentException("֧· " + branchId + " ��ϵ��ӳ���в�����");
            }
        }
        
        // ���ϵ��ӳ���е�֧·�Ƿ���ID�б��У���ѡ��ȡ��������
        for (String branchId : branchCoefficients.keySet()) {
            if (!branchIds.contains(branchId)) {
                throw new IllegalArgumentException("ϵ��ӳ���е�֧· " + branchId + " ����֧·ID�б���");
            }
        }
    }
    
    // ==================== Getter���� ====================
    
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
     * ��ȡָ��֧·��ϵ��
     * @param branchId ֧·ID
     * @return ֧·ϵ��
     */
    public Double getBranchCoefficient(String branchId) {
        return branchCoefficients.get(branchId);
    }
    
    /**
     * ��ȡָ�������������
     * @param generatorId ����ID
     * @return ������ֵ
     */
    public Double getGeneratorSensitivity(String generatorId) {
        return generatorSensitivities.get(generatorId);
    }
    
    /**
     * ���֧·�Ƿ��ڶ�����
     * @param branchId ֧·ID
     * @return �Ƿ����
     */
    public boolean containsBranch(String branchId) {
        return branchIds.contains(branchId);
    }
    
    /**
     * ��ȡ�����С��������֧·������
     * @return ֧·����
     */
    public int getSectionSize() {
        return branchIds.size();
    }
    
    
    public String getSectionName() {
		return sectionName;
	}

	/**
     * �����ⲿ�������֧·�������ȣ����㷢����Զ����������
     * ���㹫ʽ��������Զ���������� = ��(�������֧·�������� �� ֧·ϵ��)
     * 
     * @param generatorBranchSensitivities �������֧·��������ӳ��
     * @return ������Զ����������
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
     * �����������������ݣ��ۺϷ�����
     * ִ��˳��
     * 1. �������������Ⱦ���
     * 2. ���������ȼ��㷢����Զ����������
     * 3. ������浱ǰ����
     * 
     * �˷�����һ����ݵ��ۺϷ�����һ������ɶ�������м�������
     * 
     * @param net ACLF����ģ�ͣ����ڻ�ȡ�������˺ͳ�������
     * 
     * �������̣�
     * ����������������������������������������������������������������������������������������������
     * ��            calculate(net)                   ��
     * ��                                             ��
     * �� 1. ʹ��AclfNetSensHelper���������Ⱦ���       ��
     * ��    ����> ���ض�ά���� sen[�����ĸ��][֧·]    ��
     * ��                                             ��
     * �� 2. ����calculate(net, sen)                  ��
     * ��    ����> ���������Ⱦ�����㷢����Զ���������� ��
     * ��                                             ��
     * �� 3. ����calculateCurrentPower(net)           ��
     * ��    ����> ������浱ǰ��ʵ�ʹ���ֵ             ��
     * ����������������������������������������������������������������������������������������������
     * 
     * ע�⣺
     * - �����ȼ���������AclfNetSensHelper��
     * - ���湦�ʼ������������統ǰ�ĳ���״̬
     * - ����˳���������������Ϊ���������ȼ�����Ҫ���������Ⱦ���
     * - �˷�����������ڲ������������ݺ͵�ǰ����ֵ
     * 
     * @see #calculate(AclfNetwork, double[][])
     * @see #calculateCurrentPower(AclfNetwork)
     * @see AclfNetSensHelper
     */
    public void calculate(AclfNetwork net) {
        // ����1: �������������Ⱦ���
        // ʹ��AclfNetSensHelper��������㷢�����֧·��������
        // sen[i][j] ��ʾ��i�������ĸ�߶Ե�j��֧·��������
        DMatrixSparseCSC sen = new AclfNetSensHelper(net).calSenSortNumber();
        
        // ����2: ���������Ⱦ�����㷢����Զ����������
        // ʹ�ù�ʽ: ������Զ��������� = ��(�������֧·������ �� ֧·ϵ��)
        calculate(net, net.getAclfGenNameLookupTable(), sen);
        
        // ����3: ������浱ǰ��ʵ�ʹ���ֵ
        // ʹ�ù�ʽ: ���浱ǰ���� = ��(֧·���� �� ֧·ϵ��)
        calculateCurrentPower(net);
    }
	
	/**
	 * ����ACLF���������浱ǰ����
	 * ���㹫ʽ�����湦�� = ��(֧·���� �� ֧·ϵ��)
	 * 
	 * @param net ACLF����ģ��
	 * @return ���浱ǰ�ܹ���
	 */
	public double calculateCurrentPower(AclfNetwork net) {
	    if (net == null) {
	        throw new IllegalArgumentException("ACLF���粻��Ϊ��");
	    }
	    
	    double totalPower = 0.0;
	    
	    for (String branchId : branchIds) {
	        // ��ȡ֧·����
	        AclfBranch branch = net.getBranch(branchId);
	        if (branch == null) {
	            // ��¼������׳��쳣��ȡ����ҵ������
	            continue;
	        }
	        
	        // ��ȡ֧·ϵ��
	        Double coefficient = branchCoefficients.get(branchId);
	        if (coefficient == null) {
	            coefficient = 1.0; // Ĭ��ϵ��Ϊ1.0
	        }
	        
	        // ��ȡ֧·�й����ʣ�������getActivePower������
	        double branchPower = branch.powerFrom2To().getReal();
	        
	       
	        // �ۼӣ�֧·���� �� ֧·ϵ��
	        totalPower += branchPower * coefficient;
//	        System.out.println(branch.getId()+branchPower * coefficient);
	    }
	    
	    // ���²����ص�ǰ����
	    this.currentPower = totalPower;
	    return totalPower;
	}
	
	public double calculateCurrentPower(DclfAlgorithm dcAlgo) {
	    BaseAclfNetwork<?, ?> net = dcAlgo.getNetwork();
	    
	    double totalPower = 0.0;
	    
	    for (String branchId : branchIds) {
	        // ��ȡ֧·����
	        AclfBranch branch = net.getBranch(branchId);
	        if (branch == null) {
	            // ��¼������׳��쳣��ȡ����ҵ������
	            continue;
	        }
	        
	        // ��ȡ֧·ϵ��
	        Double coefficient = branchCoefficients.get(branchId);
	        if (coefficient == null) {
	            coefficient = 1.0; // Ĭ��ϵ��Ϊ1.0
	        }
	        
	        // ��ȡ֧·�й����ʣ�������getActivePower������
	        double branchPower = dcAlgo.getBranchFlow(branchId);
	        
	       
	        // �ۼӣ�֧·���� �� ֧·ϵ��
	        totalPower += branchPower * coefficient;
//	        System.out.println(branch.getId()+branchPower * coefficient);
	    }
	    
	    // ���²����ص�ǰ����
	    this.currentPower = totalPower;
	    return totalPower;
	}
	
	
	/**
	 * ���ڵ���ģ�ͺ������Ⱦ���������з�����Զ����������
	 * @param net ��������ģ��
	 * @param generatorMap 
	 * @param sen �����ȶ�ά���� [�����ĸ��][֧·]
	 */
	public void calculate(AclfNetwork net, Map<String, AclfGen> generatorMap, DMatrixSparseCSC sen) {
	    if (net == null || sen == null) {
	        throw new IllegalArgumentException("����ģ�ͺ����������鲻��Ϊ��");
	    }
	    
	    // �������з����
	    for (Map.Entry<String, AclfGen> entry : generatorMap.entrySet()) {
	        String generatorId = entry.getKey();
	        AclfGen generator = entry.getValue();
	        BaseAclfBus<?, ?> parentBus = generator.getParentBus();
	        
	        if (parentBus == null) {
	            continue; // ����û��ĸ�ߵķ����
	        }
	        
	        // �ռ������������֧·��������
	        Map<String, Double> branchSensitivities = new HashMap<>();
	        
	        for (String branchId : this.branchIds) {
	            AclfBranch branch = net.getBranch(branchId);
	            if (branch == null) {
	                continue; // ���������ڵ�֧·
	            }
	            
	            try {
	                // �������������л�ȡ����
	                int busIndex = parentBus.getSortNumber();
	                int branchIndex = branch.getSortNumber();

					// ���������Χ

					double sensitivity = sen.get(busIndex, branchIndex);
					branchSensitivities.put(branchId, sensitivity);

	            } catch (Exception e) {
	                // �������ܵ�ת���쳣
	                branchSensitivities.put(branchId, 0.0);
	            }
	        }
	        
	        // ���㷢����Զ����������
			double sectionSensitivity = calculateGeneratorToSectionSensitivity(branchSensitivities);
			if (Math.abs(sectionSensitivity) > 0.001) {
				this.generatorSensitivities.put(generatorId, sectionSensitivity);
			}
	    }
	}
    
    // ==================== Builderģʽ����ѡ�� ====================
    
    /**
     * Builder�࣬���ڹ���PowerSystemSection����
     */
    public static class Builder {
    	private String sectionName;
        private List<String> branchIds = new ArrayList<>();
        private Map<String, Double> branchCoefficients = new HashMap<>();
        private Map<String, Double> generatorSensitivities = new HashMap<>();
        // ����
        private double upper = Double.POSITIVE_INFINITY;
        
        // ����
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
    
    // ==================== ��дObject���� ====================
    
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
