package org.interpss.plugin.optadj.optimizer.bean;

import org.apache.commons.math3.optim.linear.Relationship;

/** 

* @author  Donghao.F 

* @date 2024��5��27�� ����5:23:23 

* 

*/
public class DeviceConstrainData extends BaseConstrainData {

	private int index;
	
	private boolean load;

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public DeviceConstrainData(double value, Relationship relationship, double limit, int index) {
		super(0, relationship, limit - value);
		this.index = index;
	}

	public boolean isLoad() {
		return load;
	}

	public void setLoad(boolean isLoad) {
		this.load = isLoad;
	}



	
	
}
