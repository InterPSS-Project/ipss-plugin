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
import org.interpss.plugin.pssl.common.PSSLException;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.common.ReferenceBusException;
import com.interpss.core.sparse.impl.csj.CSJSparseEqnComplexImpl;
import com.interpss.simu.util.sample.SampleTestingCases;

import edu.emory.mathcs.csparsej.tdcomplex.DZcs_common.DZcsa;

public class EDistance_Sample {
	public static void main(String args[]) throws Exception {
		IpssCorePlugin.init();
		
		sample_5Bus();
		
		sample_ieee14();
	}

	public static void sample_5Bus() throws InterpssException, ReferenceBusException, IpssNumericException, PSSLException  {
		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		SampleTestingCases.load_LF_5BusSystem(net);
		//System.out.println(net.net2String());
		
		/*
		1   1   2    0.04    0.25    -0.25
		2   1   3    0.1     0.35     0.0
		3   2   3    0.08    0.3     -0.25
		4   4   2    0.0     0.015    1.05
		5   5   3    0.0     0.03     1.05
		
		z(2,3) = (0.08+j0.3) // [ (0.04+j0.25) + (0.1+j0.35) ]
		*/
		Complex z1 = new Complex(0.08,0.3);
		Complex z2 = new Complex(0.04+0.1, 0.25+0.35);
		
		System.out.println("5 Bus z(2-3) // [ z(1-2) + z(1-3) ]: " + ComplexFunc.toStr(z1.multiply(z2).divide(z1.add(z2))));
		
		ISparseEqnComplex eqn = net.formYMatrix();
		
		int i = net.getBus("2").getSortNumber();
		int j = net.getBus("3").getSortNumber();
		
		eqn.setBi(new Complex(1.0,0.0), i);
		eqn.setBi(new Complex(-1.0,0.0), j);
		eqn.solveEqn();

		Complex vi = eqn.getX(i);
		Complex vj = eqn.getX(j);
		Complex zij = vi.subtract(vj);
		System.out.println("5 Bus Z(2,3): " + ComplexFunc.toStr(zij));
	}
	
	public static void sample_ieee14() throws InterpssException, ReferenceBusException, IpssNumericException, PSSLException  {
		AclfNetwork net = getSampleNet();
		//System.out.println(net.net2String());
		
		CSJSparseEqnComplexImpl eqn = (CSJSparseEqnComplexImpl)net.formYMatrix();
		
		int j = net.getBus("Bus2").getSortNumber();
		int i = net.getBus("Bus14").getSortNumber();
		
		eqn.setBi(new Complex(1.0,0.0), i);
		eqn.setBi(new Complex(-1.0,0.0), j);
		eqn.solveEqn();

		Complex vi = eqn.getX(i);
		Complex vj = eqn.getX(j);
		Complex zij = vi.subtract(vj);
		System.out.println("14Bus Zij: " + ComplexFunc.toStr(zij));
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
				
				DZcsa x = eqn.solveLUedEqn(b);
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

