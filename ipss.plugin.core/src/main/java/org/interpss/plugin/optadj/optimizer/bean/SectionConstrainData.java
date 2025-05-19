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

	private double[] senArray;

	public double[] getSenArray() {
		return senArray;
	}
	
	private double[] makeUnique(double[] input) {
		Map<Double, Integer> valueCounts = new HashMap<>();

		double[] uniqueValues = new double[input.length];

		double delta = 1e-5;

		for (int i = 0; i < input.length; i++) {
			double originalValue = input[i];
			valueCounts.put(originalValue, valueCounts.getOrDefault(originalValue, 0) + 1);
			double factor = originalValue > 0 ? -1 : 1;
			double uniqueValue = originalValue + factor* (valueCounts.get(originalValue) - 1) * delta;

			uniqueValues[i] = uniqueValue;
		}

		return uniqueValues;
	}
	
	public void setSenArray(double[] senArray) {
		senArray = makeUnique(senArray);
		this.senArray = senArray;
	}

	public SectionConstrainData(double value, Relationship relationship, double limit, double[] senArray) {
		super(value, relationship, limit);
		setSenArray(senArray);
	}
	
	
	
}
