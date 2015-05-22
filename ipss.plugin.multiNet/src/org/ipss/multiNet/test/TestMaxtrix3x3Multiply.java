package org.ipss.multiNet.test;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.matrix.MatrixUtil;
import org.junit.Test;

public class TestMaxtrix3x3Multiply {
	
	@Test
	public void testPreMulitply(){
		
		Complex3x3[][] m = MatrixUtil.createComplex3x32DArray(2, 2);
		
		Complex3x3 block = new Complex3x3(new Complex(1,0),new Complex(2,0),new Complex(3,0));
		
		m[0][0] = block;
		
		m[1][1] = block.multiply(2.0);
		
		int[][] t = new int[][]{ {1, 0},{1,1},{0,1}};
		
		Complex3x3[][] preMulResult = MatrixUtil.preMultiply( t,m) ;
		
		System.out.println(MatrixUtil.complex3x32DAry2String(preMulResult));
		
		assertTrue(preMulResult.length==3);
		assertTrue(preMulResult[0].length==2);
		
		
		// The following results should be all zeros
		System.out.println(preMulResult[0][0].subtract(block).toString());
		
		System.out.println(preMulResult[0][1].toString());
		
		System.out.println(preMulResult[1][0].subtract(block).toString());
		
		System.out.println(preMulResult[2][1].subtract( block.multiply(2.0)).toString());
		
		//
		int[][] tTranspose =  MatrixUtil.transpose(t);// new int[][]{ {1, 0},{1,1},{0,1}};
		
		Complex3x3[][] postMulResult = MatrixUtil.multiply( preMulResult ,tTranspose) ;
		
		assertTrue(postMulResult.length==3);
		assertTrue(postMulResult[0].length==3);
		
		/**
		 * result = [block; block;         0]
		 *          [block; block*3; block*2]
		 *          [0 ;    block*2; block*2]
		 */
		// The following result should be all zeros
		System.out.println("post (0,0) - block=\n"+postMulResult[0][0].subtract(block).toString());
		
		System.out.println("post (0,1) - block =\n"+postMulResult[0][1].subtract(block).toString());
		
		System.out.println("post (0,2)  =\n"+postMulResult[0][2].toString());
		
		System.out.println("post (1,1) - block*3 =\n"+postMulResult[1][1].subtract(block.multiply(3)).toString());
		
		System.out.println("post (2,2) - block*2 =\n"+postMulResult[2][2].subtract(block.multiply(2)).toString());
		
		
		// Test vector premultiply
		
		Complex3x1[] v = new Complex3x1[2];
		v[0] = new Complex3x1(new Complex(1,0),new Complex(2,0),new Complex(3,0));
		v[1] = v[0].multiply(2.0);
		
		
		// result = [block; block*3;block*2]
		Complex3x1[] result = MatrixUtil.preMultiply(t, v);  
		assertTrue(result.length==3);
		System.out.println("post (0) - block =\n"+result[0].subtract(v[0]).toString());
		System.out.println("post (1) - block*3 =\n"+result[1].subtract(v[0].multiply(3)).toString());
		System.out.println("post (2) - block*2 =\n"+result[2].subtract(v[0].multiply(2)).toString());
	}
	
	

}
