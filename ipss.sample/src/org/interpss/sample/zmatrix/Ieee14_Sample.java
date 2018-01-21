 /*
  * @(#)Ieee14_CASample.java   
  *
  * Copyright (C) 2006-2015 www.interpss.org
  *
  * This program is free software; you can redistribute it and/or
  * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
  * as published by the Free Software Foundation; either version 2.1
  * of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * @Author Mike Zhou
  * @Version 1.0
  * @Date 03/15/2015
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.sample.zmatrix;

import java.util.stream.IntStream;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.pssl.common.PSSLException;
import org.interpss.pssl.plugin.IpssAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.dclf.common.ReferenceBusException;

import edu.emory.mathcs.csparsej.tdcomplex.DZcs_common.DZcsa;

public class Ieee14_Sample {
	public static void main(String args[]) throws Exception {
		IpssCorePlugin.init();
		
		sample();
	}
	
	public static void sample() throws InterpssException, ReferenceBusException, IpssNumericException, PSSLException  {
		AclfNetwork net = getSampleNet();
		//System.out.println(net.net2String());
		
		ISparseEqnComplex eqn = net.formYMatrix();
		
		int j = net.getBus("Bus2").getSortNumber();
		int i = net.getBus("Bus14").getSortNumber();
		
		eqn.setBi(new Complex(1.0,0.0), i);
		eqn.setBi(new Complex(-1.0,0.0), j);
		eqn.solveEqn();

		Complex zij = eqn.getX(i).subtract(eqn.getX(j));
		System.out.println("Zij: " + ComplexFunc.toStr(zij));
		// Zij: 0.10097 + j0.33347
		
		int N = 10000;
	  	
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		for (int n = 0; n < N; n++) {
			eqn.setB2Zero();
			eqn.setBi(new Complex(1.0,0.0), i);
			eqn.setBi(new Complex(-1.0,0.0), j);
			eqn.solveEqn();

			zij = eqn.getX(i).subtract(eqn.getX(j));
			//System.out.println("Zij: " + ComplexFunc.toStr(zij));
			if (Math.abs(zij.getReal() - 0.10097) > 1.0e-5 || Math.abs(zij.getImaginary() - 0.33347) > 1.0e-5)
				System.out.println("Error: ");
			
		}
	  	timer.logStd("Time for solving zij in Seq");	
	  	
	  	timer.start();
	  	IntStream.range(0,N).parallel().forEach(n -> {
			try {
				int d = eqn.getDimension();
				DZcsa b = new DZcsa(d);
				
				b.set(i, 1.0, 0.0);
				b.set(j, -1.0, 0.0);
				
				DZcsa x = eqn.solveEqn(b);
				Complex z = new Complex(x.get(i)[0],x.get(i)[1]).subtract(new Complex(x.get(j)[0],x.get(j)[1]));
				//System.out.println("Zij: " + ComplexFunc.toStr(z));
				if (Math.abs(z.getReal() - 0.10097) > 1.0e-5 || Math.abs(z.getImaginary() - 0.33347) > 1.0e-5)
					System.out.println("Error: ");
			} catch (IpssNumericException e) {
				e.printStackTrace();
			}
	  	});
	  	timer.logStd("Time for solving zij in Parallel");	
	}

	
	public static AclfNetwork getSampleNet() throws InterpssException {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();		
		
		return net;
	}
}

