package org.ipss.multiNet.equivalent;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.FieldMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.matrix.ComplexMatrixEqn;
import org.interpss.numeric.matrix.MatrixUtil;

import com.interpss.core.net.NetCoordinate;
import com.interpss.core.net.NetEquivType;

public class NetworkEquivalent {
	private NetCoordinate equivCoordinate = NetCoordinate.POSITIVE_SEQUENCE;
		 
	private  NetEquivType type = NetEquivType.THEVENIN;
	
	private ComplexMatrixEqn complexEqn = null;
    
	// three-sequence or three-phase
	private Complex3x1[] source3x1 = null;
	private Complex3x3[][] matrix3x3 = null;
	
	// positive sequence or single-phase
	private Complex[] source = null;
	private Complex[][] matrix = null;
	
	public NetworkEquivalent(){
		
	}
	
	/**
	 *  Use default positive sequence as the coordinate and the Thevenin as equivalent type
	 * @param dim  the number of equivalent port/bus 
	 */
	public NetworkEquivalent(int dim){
		this.complexEqn = new ComplexMatrixEqn(dim);
	}
	
	public NetworkEquivalent(int dim, NetCoordinate equivCoordinate,NetEquivType type){
		this(dim);
		this.equivCoordinate = equivCoordinate;
		this.type = type;
	}

	public NetCoordinate getEquivCoordinate() {
		return this.equivCoordinate;
	}

	public void setEquivCoordinate(NetCoordinate equivCoordinate) {
		this.equivCoordinate = equivCoordinate;
	}

	public NetEquivType getType() {
		return type;
	}

	public void setType(NetEquivType type) {
		this.type = type;
	}
	
	public ComplexMatrixEqn getComplexEqn() {
		return this.complexEqn;
	}	

	
	public Complex3x1[] getSource3x1() {
		if(this.source3x1 ==null)
			this.source3x1 = MatrixUtil.createComplex3x1DArray(this.complexEqn.getDimension());
		return source3x1;
	}
	
	public Complex[] getSource() {
		if(this.source ==null)
			this.source = MatrixUtil.createComplex1DArray(this.complexEqn.getDimension());
		return source;
	}
	

	public void setSource3x1(Complex3x1[] source3x1) {
		this.source3x1 = source3x1;
	}
	
	
	public void setSource(Complex[] newSource) {
		this.source = newSource;
	}
	

	public Complex3x3[][] getMatrix3x3() {
		return matrix3x3;
	}
	
	public Complex[][] getMatrix() {
		if (matrix ==null)
		     matrix = MatrixUtil.createComplex2DArray(this.complexEqn.getDimension(), this.complexEqn.getDimension());
		
		return matrix;
	}
	
	public void setMatrix(Complex[][] newMatrix) {
		
		this.matrix = newMatrix;
		
	}

	public void setMatrix3x3(Complex3x3[][] matrix3x3) {
		this.matrix3x3 = matrix3x3;
	}

	public NetworkEquivalent transformType(NetEquivType fType, NetEquivType tType){
		return null;
	}
	
	
	public NetworkEquivalent transformCoordinate(NetCoordinate toType){
		
		NetworkEquivalent transEquiv = new NetworkEquivalent(this.complexEqn.getDimension());
		
		if(this.equivCoordinate==NetCoordinate.THREE_PHASE){
			if(toType == NetCoordinate.THREE_SEQUENCE){
				
				//step-1 set the target coordinate
				transEquiv.setEquivCoordinate(toType);
				
				
				//step-1.1 if the original matrix is stored in a basic matrix format, it is first transformed to a 3x3 block matrix format
				// to better perform the coordinate the 3-phase to 3-seq transformation
				if(this.complexEqn!=null && this.matrix3x3 == null)
					this.matrix3x3 = this.complexEqn.to3x3BlockMatrix();
				if(this.complexEqn!=null && this.source3x1 == null)
					this.source3x1 = this.complexEqn.to3x1BlockVector();
				
				//Step-2 perform 3-phase to three-sequence transformation for the matrix part
				int dim = this.matrix3x3[0].length;
				Complex3x3[][] m3x3 = new Complex3x3[dim][dim];
				
				for(int i =0;i<dim;i++){
					for(int j =0;j<dim;j++){
						m3x3[i][j] = Complex3x3.abc_to_120(this.matrix3x3[i][j]);
					}
				}
				
				transEquiv.matrix3x3 = m3x3;
				
				//step-3  perform 3-phase to three-sequence transformation for the source part
				
				if(this.source3x1!=null){
					Complex3x1[] v3x1 = new Complex3x1[dim];
					for(int i =0;i<dim;i++){
						v3x1[i] = Complex3x1.abc_to_z12(this.source3x1[i]);
					}
					
					transEquiv.setSource3x1(v3x1);
				}
				
				return transEquiv;
			}
			else{
				throw new UnsupportedOperationException("Only three-phase to three-sequence transformation is supported.");
				
			}
				
		}
		else
			throw new UnsupportedOperationException("Only three-phase to three-sequence transformation is supported.");
	}
	
	/**
	 * convert input current or voltage array in 120-coordinate to the corresponding ABC-coordinate
	 * 
	 * Iabc =s*I120
	 * 
	 * @return
	 */
    private Complex[] source120ToAbc(Complex[] v120){
    	FieldMatrix<Complex> fmT= MatrixUtils.createFieldMatrix(Complex3x3.T120_abc);
    	return fmT.operate(v120);
    }
    
    
	/**
	 * convert input current or voltage array in ABC-coordinate to the corresponding 120-coordinate
	 * 
	 *  V120=Tinv*Vabc
	 * 
	 * @return
	 */
    private Complex[] sourceABCTo120(Complex[] vABC){
    	FieldMatrix<Complex> fmTinv= MatrixUtils.createFieldMatrix(Complex3x3.Tabc_120);
    	return fmTinv.operate(vABC);
    }
    
    /**
     * transform impedance matrix in 120 coordinate to ABC coordinate
     * @param z120
     * @return
     */
	private Complex[][] matrix120ToAbc(Complex[][] z120){
		//Zabc=T*Z120*T^-1
		FieldMatrix<Complex> fmZ120= MatrixUtils.createFieldMatrix(z120);
		FieldMatrix<Complex> fmT= MatrixUtils.createFieldMatrix(Complex3x3.T120_abc);
		FieldMatrix<Complex> fmTinv= MatrixUtils.createFieldMatrix(Complex3x3.Tabc_120);
		Complex[][] Zabc = fmT.multiply(fmZ120).multiply(fmTinv).getData();
	    return Zabc;
	}
	
	 /**
     * transform impedance matrix in 120 coordinate to ABC coordinate
     * @param z120
     * @return
     */
	private Complex[][] matrixAbcTo120(Complex[][] zabc){
		//Z120=T^-1*ZABC*T
		FieldMatrix<Complex> fmZabc= MatrixUtils.createFieldMatrix(zabc);
		FieldMatrix<Complex> fmT= MatrixUtils.createFieldMatrix(Complex3x3.T120_abc);
		FieldMatrix<Complex> fmTinv= MatrixUtils.createFieldMatrix(Complex3x3.Tabc_120);
		Complex[][] Z120 = fmTinv.multiply(fmZabc).multiply(fmT).getData();
	    return Z120;
	}
	
	public void transMatrixTo3x3BlockMatrix(){
        this.matrix3x3 = this.complexEqn.to3x3BlockMatrix();
	}
	
	public void transSourceTo3x1BlockVector(){
        this.source3x1 = this.complexEqn.to3x1BlockVector();
	}
}
