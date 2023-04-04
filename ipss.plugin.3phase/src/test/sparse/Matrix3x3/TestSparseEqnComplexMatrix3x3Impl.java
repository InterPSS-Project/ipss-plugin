package test.sparse.Matrix3x3;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.exp.IpssNumericException;
import org.junit.Test;

import com.interpss.core.sparse.impl.csj.CSJSparseEqnComplexMatrix3x3Impl;

public class TestSparseEqnComplexMatrix3x3Impl {
	
	
	@Test
	public void testReal(){

	CSJSparseEqnComplexMatrix3x3Impl matrix3x3 = new CSJSparseEqnComplexMatrix3x3Impl(1);
	
	Complex3x3 y = new Complex3x3();
	y.aa = new Complex(1,0);
	y.ab = new Complex(3,0);
	y.ac = new Complex(5,0);
	
	y.ba = new Complex(1,0);
	y.bb = new Complex(1,0);
	y.bc = new Complex(1,0);
	
	y.ca = new Complex(4,0);
	y.cb = new Complex(0,0);
	y.cc = new Complex(9,0);
	
	
	Complex3x1 b = new Complex3x1(new Complex(1,0),new Complex(0,0),new Complex(0,0));
	matrix3x3.setA(y, 0, 0);
	matrix3x3.setBi(b, 0);
	
	try {
		
		matrix3x3.solveEqn(1.0e-5);
	} catch (IpssNumericException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	
	Complex3x1 x = matrix3x3.getX(0);
	/* Matlab result
	-0.3462
    0.1923
    0.1538
    */
	assertTrue(Math.abs(x.a_0.getReal()+0.3462)<5.0E-4);
	assertTrue(Math.abs(x.b_1.getReal()-0.1923)<5.0E-4);
	assertTrue(Math.abs(x.c_2.getReal()-0.1538)<5.0E-4);
	System.out.println(x.toString());
	}
	
	@Test
	public void testComplex(){
	CSJSparseEqnComplexMatrix3x3Impl matrix3x3 = new CSJSparseEqnComplexMatrix3x3Impl(1);
	
	Complex3x3 y = new Complex3x3();
	y.aa = new Complex(1,0.5);
	y.ab = new Complex(3,0);
	y.ac = new Complex(5,2);
	
	y.ba = new Complex(1,0);
	y.bb = new Complex(1,0);
	y.bc = new Complex(1,0);
	
	y.ca = new Complex(4,0);
	y.cb = new Complex(0,0);
	y.cc = new Complex(9,0);
	
	
	Complex3x1 b = new Complex3x1(new Complex(1,0),new Complex(0,0),new Complex(0,0));
	matrix3x3.setA(y, 0, 0);
	matrix3x3.setBi(b, 0);
	
	try {
		
		matrix3x3.solveEqn(1.0e-5);
	} catch (IpssNumericException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	
	Complex3x1 x = matrix3x3.getX(0);
	/* Matlab result
	-0.3462
    0.1923
    0.1538
    */
	assertTrue(x.a_0.subtract(new Complex(-0.3400 ,0.0458)).abs()<5.0E-4);
	assertTrue(x.b_1.subtract(new Complex(0.1889, -0.0254)).abs()<5.0E-4);
	assertTrue(x.c_2.subtract(new Complex(0.1511,-0.0203)).abs()<5.0E-4);
	System.out.println(x.toString());
	}
}
