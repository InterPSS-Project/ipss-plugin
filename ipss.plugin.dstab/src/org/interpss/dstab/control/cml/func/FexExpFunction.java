package org.interpss.dstab.control.cml.func;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.controller.block.adapt.CMLFunctionExpressionAdapter;
import com.interpss.dstab.datatype.ExpCalculator;

public class FexExpFunction extends CMLFunctionExpressionAdapter {
	private double kc = 1.0;
	/**
	 * constructor
	 * 
	 * @param k
	 */
	public FexExpFunction(double kc) {
		this.kc = kc;
	}


	/**
	 * evaluate function value based on the input 2D double array. The array matches the input expression list
	 *
	 * @param dAry2D contains two arrays, the first is the VE, the second is the Ifd
	 * @return the function value
	 */
	@Override public double eval(double[][] dAry2D) throws InterpssException {
		ExpCalculator veExp = getInputExpList().get(0);
		ExpCalculator ifdExp = getInputExpList().get(1);
		
		double ve = ifdExp.eval(dAry2D[0]);
		double ifd = ifdExp.eval(dAry2D[1]);
		
		double In = this.kc *ifd/ve;
		if (In <= 0.0)
			return 1.0;
		else if (In > 0.0 && In <= 0.433)
			return 1.0 - 0.5777 * In;
		else if (In > 0.433 && In < 0.75)
			return Math.sqrt(0.75 - In * In);
		else if (In >= 0.75 && In <= 1.0)
			return 1.732 * (1.0 - In);
		else
			return 0.0; // In > 1.0
	}

}
