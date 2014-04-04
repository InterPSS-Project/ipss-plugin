package org.interpss.dstab.control.cml.func;

import com.interpss.dstab.controller.block.adapt.CMLFunctionAdapter;

public class MultiplyFunction extends CMLFunctionAdapter {
	
	public MultiplyFunction() {
		
	}
	
	@Override public double eval(double[] dAry) {
		return  dAry[0]*dAry[1]; 
	}

}
