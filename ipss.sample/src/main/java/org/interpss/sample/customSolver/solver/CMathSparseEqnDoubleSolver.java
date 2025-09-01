package org.interpss.sample.customSolver.solver;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
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
	
	/**
	 * factorization of the matrix.
	 * 
	 * @param tolerance the tolerance for matrix singular detection
	 * @return if succeed return true.
	 */
	@Override public boolean factorization( final double tolerance)  throws IpssNumericException {
		// do nothing for the apache common math lib implementation, since it is
		// a full matrix solver
		return true;
	}
		
	@Override public boolean factorization()  throws IpssNumericException {
		return factorization(1.0e-6);
	}
	
	/**
	 * Solve the [A]X = B problem, the result is stored in the eqn B vector.
	 * 
	 */
	@Override
	public void solveEqn() throws IpssNumericException {
		// build the {B} vector
		int rows = this.eqn.getDimension(IndexType.Row);
		double[] b = new double[rows];
		for ( int i = 0; i < rows; i++ ) {
			b[i] = this.eqn.getElem(i).bi;
		}

		// solve the equation
		double[] x = solveEqn(b);
		
		// set the solution back to the eqn B
		this.eqn.setBVector(x);
	}
	
	@Override
	public double[] solveLUedEqn(double[] b) throws IpssNumericException {
		// TODO Auto-generated method stub
		return super.solveLUedEqn(b);
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
		if (!this.factored)
			this.factorization(1.0e-10);
		
		// build the [A] matrix
		RealMatrix A = buildAMatrix();
		
		// do LU decomposition
		LUDecomposition lu = new LUDecomposition(A);
		
		// build the {B} vector
		ArrayRealVector B = new ArrayRealVector(b);
		
		// solve the equation
		return lu.getSolver().solve(B).toArray();
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