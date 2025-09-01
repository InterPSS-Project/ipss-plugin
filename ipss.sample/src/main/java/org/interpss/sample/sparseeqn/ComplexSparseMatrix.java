package org.interpss.sample.sparseeqn;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.numeric.util.CSJSpraseEqnUtil;

import com.interpss.core.sparse.impl.csj.CSJSparseEqnComplexImpl;

import edu.emory.mathcs.csparsej.tdcomplex.DZcs_common.DZcsa;

public class ComplexSparseMatrix {
	public static void main(String[] args) throws Exception {
		System.out.println("Sample 1 output: ");
		sample1();
		
		System.out.println("\n\nSample 2 output: ");
		sample2();
		
		System.out.println("\n\nSample 3 output: ");
		sample3();
		
		System.out.println("\n\nSample 4 output: ");
		sample4();

		System.out.println("\n\nSample 4-1 output: ");
		sample4_1();
		
		System.out.println("\n\nSample 5 output: ");
		sample5();
	}	
	
	public static void sample1() {
		ISparseEqnComplex m = new CSJSparseEqnComplexImpl(2,3) ;
		
		m.setA(new Complex(1.0,1.0), 0, 0);
		m.setA(new Complex(2.0,2.0), 1, 1);

		m.setA(new Complex(0.0,11.0), 0, 2);
		m.setA(new Complex(0.0,12.0), 1, 2);
		
		//System.out.println("Sample 1 output: ");
		System.out.println(m);		
	}
	
	public static void sample2() {
		ISparseEqnComplex m = new CSJSparseEqnComplexImpl(2, 2);
		
		Complex  a11 = new Complex( 1.0, 1.1 ),
			     a12 = new Complex( 3.1, 4.1 ),
			     a21 = new Complex( 2.1, 1.9 ),
			     a22 = new Complex( 3.0, 4.0 );
		m.setA( a11, 0, 0 );
		m.setA( a12, 0, 1 );
		m.setA( a21, 1, 0 );
		m.setA( a22, 1, 1 );

		Complex b1 = new Complex( 2.03624, 0.3256 ),
			    b2 = new Complex(-0.16731, 0.03888 );

		Complex[] xAry = new Complex[2];
		xAry[0] = b1;
		xAry[1] = b2;
		
		Complex[] y = m.multiply(xAry);
		//System.out.println("\n\nSample 2 output: ");
		System.out.println(ComplexFunc.toStr(y[0]));
		System.out.println(ComplexFunc.toStr(y[1]));		
	}
	
	public static void sample3() {
		CSJSparseEqnComplexImpl m = new CSJSparseEqnComplexImpl(2, 2);
		
		Complex  a11 = new Complex( 1.0, 1.1 ),
			     a12 = new Complex( 3.1, 4.1 ),
			     a21 = new Complex( 2.1, 1.9 ),
			     a22 = new Complex( 3.0, 4.0 );
		m.setA( a11, 0, 0 );
		m.setA( a12, 0, 1 );
		m.setA( a21, 1, 0 );
		m.setA( a22, 1, 1 );

		Complex b1 = new Complex( 2.03624, 0.3256 ),
			    b2 = new Complex(-0.16731, 0.03888 );

		Complex[] xAry = new Complex[2];
		xAry[0] = b1;
		xAry[1] = b2;
		
		DZcsa dAry = CSJSpraseEqnUtil.ComplexAry2DZcsa(xAry);
		
		DZcsa y = m.multiply(dAry);
		//System.out.println("\n\nSample 3 output: ");
		System.out.println(y.get(0)[0]);
		System.out.println(y.get(0)[1]);
		System.out.println(y.get(1)[0]);		
		System.out.println(y.get(1)[1]);
	}	
	
	public static void sample4() throws IpssNumericException {
		ISparseEqnComplex eqn = new CSJSparseEqnComplexImpl(2);
		
		Complex  a11 = new Complex( 1.0, 1.1 ),
			     a12 = new Complex( 3.1, 4.1 ),
			     a21 = new Complex( 2.1, 1.9 ),
			     a22 = new Complex( 3.0, 4.0 );
		eqn.setA( a11, 0, 0 );
		eqn.setA( a12, 0, 1 );
		eqn.setA( a21, 1, 0 );
		eqn.setA( a22, 1, 1 );
		
		Complex b1 = new Complex( 1.0, 2.0 ),
			    b2 = new Complex( 3.0, 4.0 );

		eqn.setBi( b1, 0 );
		eqn.setBi( b2, 1 );
		
		eqn.solveEqn(1.0e-20);
		
		//System.out.println("\n\nSample 4 output: ");		
		System.out.println(eqn);
	}
	
	public static void sample4_1() throws IpssNumericException {
		ISparseEqnComplex eqn = new CSJSparseEqnComplexImpl(2);
		
		Complex  a11 = new Complex( 1.0, 1.1 ),
			     a12 = new Complex( 3.1, 4.1 ),
			     a21 = new Complex( 2.1, 1.9 ),
			     a22 = new Complex( 3.0, 4.0 );
		eqn.setA( a11, 0, 0 );
		eqn.setA( a12, 0, 1 );
		eqn.setA( a21, 1, 0 );
		eqn.setA( a22, 1, 1 );
		
		Complex[] bVector = { new Complex( 1.0, 2.0 ),
			                  new Complex( 3.0, 4.0 ) };

		eqn.setBVector(bVector);
		
		eqn.solveEqn(1.0e-20);
		
		//System.out.println("\n\nSample 4 output: ");		
		System.out.println(eqn);
	}	
	
	public static void sample5() throws IpssNumericException {
		ISparseEqnComplex eqn = new CSJSparseEqnComplexImpl(2, 3);
		eqn.setToZero();
	}	
}
