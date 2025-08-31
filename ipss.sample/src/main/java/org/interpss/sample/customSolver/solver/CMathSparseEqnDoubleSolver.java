package org.interpss.sample.customSolver.solver;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.sparse.DoubleSEqnElem;
import com.interpss.core.sparse.DoubleSEqnRow;
import com.interpss.core.sparse.impl.AbstractSparseEqnDoubleImpl;
import com.interpss.core.sparse.solver.ISparseEqnSolver;


/**
 * Sparse Equation of data type double for solving the [A]X=B problem. 
   using the apache common math lib. It is for illustrating extension implementation.
 */
public class CMathSparseEqnDoubleSolver extends AbstractSparseEqnDoubleImpl implements ISparseEqnSolver {
	private static final Logger log = LoggerFactory.getLogger(CoreObjectFactory.class);
	
	// the sparse equation object to be solved
	private AbstractSparseEqnDoubleImpl eqn;

	/**
	 * Constructor
	 * 
	 * @param n the dimension of the equation
	 */
	public CMathSparseEqnDoubleSolver(int n) {
		super(n);
		log.info("dimension = " + n);
	}
	
	/**
	 * Constructor
	 * 
	 * @param eqn the sparse equation object to be solved
	 */
	public CMathSparseEqnDoubleSolver(ISparseEqnDouble eqn) {
		super(eqn.getDimension());
		this.eqn = (AbstractSparseEqnDoubleImpl)eqn;
		//System.out.println("eqn: " + eqn);
	}
		
	@Override
	public void solveEqn() {
		// build the [A] matrix
		RealMatrix A = buildAMatrix();

		// do LU decomposition
		LUDecomposition lu = new LUDecomposition(A);
		
		// build the {B} vector
		int rows = this.eqn.getDimension(IndexType.Row);
		RealVector B = new ArrayRealVector(rows);
		for ( int i = 0; i < rows; i++ ) {
			B.setEntry(i, this.eqn.getElem(i).bi);
		}
		
		// solve the equation
		RealVector X = lu.getSolver().solve(B);
		
		// set the solution back to the eqn B
		this.eqn.setBVector(X.toArray());
	}
	
	/**
	 * Solve the equation [A]{x}={b}, return the solution {x}
	 * 
	 * @param b the right-hand side vector {b}
	 * @return the solution vector {x}
	 * @throws IpssNumericException
	 */
	@Override
	public double[] solveEqn(double[] b) throws IpssNumericException {
		// build the [A] matrix
		RealMatrix A = buildAMatrix();
		
		// do LU decomposition
		LUDecomposition lu = new LUDecomposition(A);
		
		// build the {B} vector
		ArrayRealVector B = new ArrayRealVector(b);
		
		// solve the equation
		return lu.getSolver().solve(B).toArray();
	}
	
	@Override
	public boolean factorization( final double tolerance) throws IpssNumericException {
		// no need to do factorization here
		 return true;
	}
	
	/**
	 * Build the [A] matrix from the lfEqen.
	 * 
	 */
	protected RealMatrix buildAMatrix() {
		//System.out.println("\n\neqn: " + eqn);
		
		int rows = this.eqn.getDimension(IndexType.Row);
		
		RealMatrix A = new Array2DRowRealMatrix( rows, rows );
		// copy data from the Sparse eqn to the [A] matrix
		for ( int i = 0; i < rows; i++ ) {
			final DoubleSEqnRow ai = this.eqn.getElem(i);
			A.setEntry(i, i, ai.aii);
			for ( DoubleSEqnElem aij : ai.aijList )  {
				A.setEntry(i, aij.j, aij.aij);
			}
		}
		
		//System.out.println("A = " + this.A.toString());
		return A;
	}
	
	@Override
	public void setMatrixDirty() {
		// do nothing
	}
}