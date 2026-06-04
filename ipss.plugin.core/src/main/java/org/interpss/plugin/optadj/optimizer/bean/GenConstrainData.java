package org.interpss.plugin.optadj.optimizer.bean;

import org.apache.commons.math3.optim.linear.Relationship;

/** 

* @author  Donghao.F 

* @date 2024��5��27�� ����5:23:23 

* 

*/
public class GenConstrainData extends BaseConstrainData {

	int index;
	
	double weight = 1;

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public GenConstrainData(double value, Relationship relationship, double limit, int index) {
		super(0, relationship, limit - value);
		this.index = index;
	}

	public void setWeight(double weight) {
		this.weight = weight;
	}

	public double getWeight() {
		return weight;
	}


	

	
	
}
