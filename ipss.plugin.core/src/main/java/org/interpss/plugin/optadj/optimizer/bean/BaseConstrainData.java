package org.interpss.plugin.optadj.optimizer.bean;

import org.apache.commons.math3.optim.linear.Relationship;

/** 

* @author  Donghao.F 

* @date 2024��5��27�� ����5:23:23 

* 

*/
public class BaseConstrainData {
	
	private double value;

	private Relationship relationship;
	
	private double limit;
	

	public BaseConstrainData(double value, Relationship relationship, double limit) {
		super();
		this.value = value;
		this.relationship = relationship;
		this.limit = limit;
	}

	public double getLimit() {
		return limit;
	}

	public void setLimit(double limit) {
		this.limit = limit;
	}

	public double getValue() {
		return value;
	}

	public void setValue(double value) {
		this.value = value;
	}

	public Relationship getRelationship() {
		return relationship;
	}

	public void setRelationship(Relationship relationship) {
		this.relationship = relationship;
	}

	
}
