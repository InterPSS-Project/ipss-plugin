 /*
  * @(#)SparseRqnPerformance.java   
  *
  * Copyright (C) 2006 www.interpss.org
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
  * @Date 09/15/2006
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.sample.sparseeqn;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.interpss.numeric.NumericObjectFactory;
import org.interpss.numeric.sparse.ISparseEqnDouble;
import org.interpss.numeric.util.PerformanceTimer;

import com.interpss.common.util.IpssLogger;
import com.interpss.core.sparse.SparseEqnDataType;
import com.interpss.core.sparse.impl.SparseEqnDoubleImpl;


public class SparseEqnPerformance {
	public static void main(String args[]) throws Exception {
		test1();
		
		for (int i = 0; i < 100; i++)
			test2();
	}	
	
	static void test1() throws Exception {
		ISparseEqnDouble eqn = buildEqn("testData/JMatrix_9Bus.txt", 18);
		
		eqn.setB2Unity(10);
		eqn.solveEqn();
		
		//System.out.println(eqn.toString());	
/*
b(0): 0.054228211361556304
b(1): 3.5199568569548116E-4
b(2): 0.054257249432160516
b(3): 0.0
b(4): 0.054193944080630885
b(5): 0.0
b(6): 0.05427371420982078
b(7): 3.545237795881397E-4
b(8): 0.05427246116531197
b(9): 3.227992658169093E-4
b(10): 0.05409722545764893
b(11): 0.001628306547165275
b(12): 0.0
b(13): 0.0
b(14): 0.05424021516237524
b(15): 0.0012411184010960676
b(16): 0.054249336332908196
b(17): 0.001294411022165286
 */
	}
	
	static void test2() throws Exception {
		int n = 82437+1;
		ISparseEqnDouble eqnOld = buildEqn("testData/JMatrix_40kBus.txt",n);
		
		int cnt = 0;
		int[] index = new int[n];
		for (int i = 0; i < n; i++) {
			if (((SparseEqnDoubleImpl)eqnOld).getElem(i).aijList.size() > 0)
				index[i] = cnt++;
			else
				index[i] = -1;
		}
		System.out.println("Cnt: " + cnt);

		ISparseEqnDouble eqnNew = NumericObjectFactory.createSparseEqnDouble(cnt);
		for (int i = 0; i < n; i++) {
			if (index[i] >= 0) {
				SparseEqnDataType.DblAii aii = ((SparseEqnDoubleImpl)eqnOld).getElem(i);
				int i_new = index[i];
				eqnNew.setAij(aii.aii, i_new, i_new);
				aii.aijList.forEach(aij -> {
					eqnNew.setAij(aij.aij, i_new, index[aij.j]);
				});
			}
		}

		eqnNew.setB2Unity(10);
	  	PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
	  	
		eqnNew.luMatrix(1.0e-10);
	  	timer.logStd("Time for LU the eqnNew");
	  	
	  	timer.start();
		eqnNew.solveEqn();
	  	timer.logStd("Time for solve the eqnNew");
	  	
		//System.out.println(eqn1.toString());
		
		/*
		for (int i = 0; i < n; i++) 
			System.out.println(i + ", " + index[i]);
		*/	
	}
	
	static ISparseEqnDouble buildEqn(String fileName, int n)  throws Exception {
		ISparseEqnDouble eqn = NumericObjectFactory.createSparseEqnDouble(n);
		
		Stream<String> stream = Files.lines(Paths.get(fileName));
		stream.forEach(line -> {
			if (!line.trim().equals("")) {
				//System.out.println(line);	
				int i = new Integer(line.substring(line.indexOf('(')+1,line.indexOf(','))).intValue();
				int j = new Integer(line.substring(line.indexOf(',')+1,line.indexOf(')'))).intValue();
				double a = new Double(line.substring(line.indexOf(':')+2)).doubleValue();
				//System.out.println(i + ", " + j + ", " + a);
				
				eqn.setAij(a, i, j);
			}
		});
		stream.close();
		
		return eqn;
	}
	
}
