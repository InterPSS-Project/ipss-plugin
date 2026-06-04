package org.interpss.plugin.optadj.optimizer.bean;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.optim.linear.Relationship;

/** 

* @author  Donghao.F 

* @date 2024��5��27�� ����5:23:23 

* 

*/
public class SectionConstrainData extends BaseConstrainData {

	private String name;
	
	double[] senArray;

	public double[] getSenArray() {
		return senArray;
	}
	
	public SectionConstrainData(double value, Relationship relationship, double limit, double[] senArray) {
		super(value, relationship, limit);
		setSenArray(senArray);
	}
	
	public SectionConstrainData(String name,double value, Relationship relationship, double limit,  double[] senArray) {
		this(value, relationship, limit, senArray);
		this.name = name;
		
	}



	private double[] makeUnique(double[] input) {
	    if (input == null || input.length == 0) {
	        return input;
	    }
	    
	    // Ԥ���� HashMap ��С����������
	    Map<Double, Integer> counts = new HashMap<>(input.length * 4 / 3 + 1);
	    double[] result = new double[input.length];
	    final double epsilon = 1e-10; // �� 1e-5��������Ҫ����
	    
	    for (int i = 0; i < input.length; i++) {
	        double value = input[i];
	        int count = counts.getOrDefault(value, 0);
	        
	        if (count == 0) {
	            result[i] = value;
	        } else {
	            // ����ֵ������������������
	            result[i] = value + (value >= 0 ? -epsilon : epsilon) * count;
	        }
	        
	        counts.put(value, count + 1);
	    }
	    
	    return result;
	}
	
	public void setSenArray(double[] senArray) {
//		senArray = makeUnique(senArray);
		this.senArray = senArray;
	}

	

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	
	
}
