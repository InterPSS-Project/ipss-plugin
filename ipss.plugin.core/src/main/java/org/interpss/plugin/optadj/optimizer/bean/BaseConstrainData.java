package org.interpss.plugin.optadj.optimizer.bean;

import org.apache.commons.math3.optim.linear.Relationship;

/** 

* @author  Donghao.F 

* @date 2024��5��27�� ����5:23:23 

* 

*/
public class BaseConstrainData {
	
	double value;

	Relationship relationship;
	
	double limit;
	

	public BaseConstrainData(double value, Relationship relationship, double limit) {
		super();
		this.value = value;
		this.relationship = relationship;
		if (Math.abs(value - limit) < 0.001) {
			limit = value;
		}
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
	
	@Override
	public String toString() {
		return this.value+relationship.toString()+this.limit ;
	}

	
}
