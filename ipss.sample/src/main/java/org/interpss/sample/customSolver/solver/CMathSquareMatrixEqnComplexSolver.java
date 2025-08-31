package org.interpss.sample.customSolver.solver;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexField;
import org.apache.commons.math3.linear.Array2DRowFieldMatrix;
import org.apache.commons.math3.linear.ArrayFieldVector;
import org.apache.commons.math3.linear.FieldDecompositionSolver;
import org.apache.commons.math3.linear.FieldLUDecomposition;
import org.apache.commons.math3.linear.FieldMatrix;
import org.apache.commons.math3.linear.FieldVector;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;

import com.interpss.core.sparse.ComplexSEqnElem;
import com.interpss.core.sparse.ComplexSEqnRow;
import com.interpss.core.sparse.impl.AbstractSparseEqnComplexImpl;
import com.interpss.core.sparse.solver.ISparseEqnSolver;

/**
 * Sparse Equation solver implementation of data type complex for solving the [A]X=B problem,
 * using the apache common math lib.
 */
public class CMathSquareMatrixEqnComplexSolver extends AbstractSparseEqnComplexImpl<ISparseEqnSolver> implements ISparseEqnSolver {
	// the sparse equation object to be solved
	private AbstractSparseEqnComplexImpl<ISparseEqnSolver> eqn = null;
	
	/**
	 * Constructor
	 * 
	 * @param eqn the sparse equation object to be solved
	 */
	@SuppressWarnings("unchecked")
	public CMathSquareMatrixEqnComplexSolver(ISparseEqnComplex eqn) {
		super(eqn.getDimension());
		this.eqn = (AbstractSparseEqnComplexImpl<ISparseEqnSolver>) eqn;
	}
	
  /**
   * LU decomposition of the matrix.
	* 
	* @param tolerance the tolerance for matrix singular detection
	* @return if succeed return true.
   */
	@Override public boolean factorization( final double tolerance)  throws IpssNumericException {
		// do nothing for the apache common math lib implementation
        return true;
	}
	
	@Override public boolean factorization()  throws IpssNumericException {
		return factorization(1.0e-6);
	}

	/**
	 * Solve the [A]X = B problem
	 * 
	 */
	@Override public void solveEqn() throws IpssNumericException {
		if (!this.factored)
			this.factorization(1.0e-10);
		
		// build the [A] matrix
		FieldMatrix<Complex> A = buildAMatrix();
		
		// LU decomposition solver
    	FieldDecompositionSolver<Complex> solver = new FieldLUDecomposition<Complex>(A).getSolver();
    	
		// build the {B} vector
		int rows = this.eqn.getDimension(IndexType.Row);
		Complex[] b = new Complex[rows];
		for ( int i = 0; i < rows; i++ ) {
			b[i] = this.eqn.getElem(i).bi;
		}
    	FieldVector<Complex> B = new ArrayFieldVector<>(b, false);
    	
    	// solve the equation
    	FieldVector<Complex> X = solver.solve(B);

		// set the solution back to the eqn B
		this.eqn.setBVector(X.toArray());
	}
	
	@Override
	public Complex[] solveLUedEqn(Complex[] b) throws IpssNumericException {
		// build the [A] matrix
		FieldMatrix<Complex> A = buildAMatrix();
		
		// LU decomposition solver
    	FieldDecompositionSolver<Complex> solver = new FieldLUDecomposition<Complex>(A).getSolver();
    	
    	// defined the {B} vector
    	FieldVector<Complex> B = new ArrayFieldVector<>(b, false);
    	
    	// solve the equation
    	FieldVector<Complex> solution = solver.solve(B);
    	
    	return solution.toArray();
	}

	@Override
	public double[] solveEqn(double[] b) throws IpssNumericException {
		throw new IpssNumericException("Function not implemented");
	}

	/**
	 * Build the [A] matrix from the lfEqen.
	 * 
	 */
	protected FieldMatrix<Complex> buildAMatrix() {
		//System.out.println("\n\neqn: " + eqn);
		
		int rows = this.eqn.getDimension(IndexType.Row);
		
		// copy data from the Sparse eqn to the [A] matrix
		FieldMatrix<Complex> A = new Array2DRowFieldMatrix<Complex>(ComplexField.getInstance(), rows, rows);
		for ( int i = 0; i < rows; i++ ) {
			final ComplexSEqnRow ai = this.eqn.getElem(i);
			A.setEntry(i, i, ai.aii);
			for ( ComplexSEqnElem aij : ai.aijList )  {
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
