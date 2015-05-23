package org.ipss.multiNet.equivalent;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.FieldMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.matrix.MatrixUtil;

import com.interpss.common.util.IpssLogger;

public class NetworkEquivalent {
	
	public enum Coordinate {Three_phase, Three_sequence,Positive_sequence};
	
	public enum EquivType {Norton,Thevenin};
	
	
	private Coordinate equivCoordinate = Coordinate.Positive_sequence;
	
		 
	private  EquivType type = EquivType.Thevenin;
	
	
	private Complex[] source = null;

	
	private Complex[][] matrix = null;
	
	private Complex3x1[] source3x1 = null;
	private Complex3x3[][] matrix3x3 = null;
	
	private int dimension = 0;
	
	private static final Complex a = new Complex(-0.5, Math.sqrt(3)/2);
	public static final Complex[][] T = new Complex[][]{
			{new Complex(1,0),new Complex(1,0),new Complex(1,0)},
			{a.multiply(a),      a            ,new Complex(1,0)},
			{a,               a.multiply(a)   ,new Complex(1,0)}};
	
	 public static final Complex[][] Tinv = new Complex[][]{
			{new Complex(1.0/3,0), a.divide(3)              ,a.multiply(a).divide(3)},
			{new Complex(1.0/3,0), a.multiply(a).divide(3)  ,a.divide(3)},
			{new Complex(1.0/3,0), new Complex(1.0/3,0)       ,new Complex(1.0/3,0)}};
	

	
	public NetworkEquivalent(){
		
	}
	
	/**
	 *  Use default positive sequence as the coordinate and the Thevenin as equivalent type
	 * @param dim  the number of equivalent port/bus 
	 */
	public NetworkEquivalent(int dim){
		dimension = dim;
		source = new Complex[dim];
		matrix = new Complex[dim][dim];
	}
	
	public NetworkEquivalent(int dim, Coordinate equivCoordinate,EquivType type){
		dimension = dim;
		source = new Complex[dim];
		matrix = new Complex[dim][dim];
		this.equivCoordinate = equivCoordinate;
		this.type = type;
	}

	public Coordinate getEquivCoordinate() {
		return this.equivCoordinate;
	}

	public void setEquivCoordinate(Coordinate equivCoordinate) {
		this.equivCoordinate = equivCoordinate;
	}

	public EquivType getType() {
		return type;
	}

	public void setType(EquivType type) {
		this.type = type;
	}

	public Complex[] getSource() {
		return source;
	}

	public void setSource(Complex[] source) {
		this.source = source;
	}

	public Complex[][] getMatrix() {
		return matrix;
	}

	public void setMatrix(Complex[][] matrix) {
		this.matrix = matrix;
	}

	public Complex3x1[] getSource3x1() {
		if(this.source3x1 ==null)
			this.source3x1 = MatrixUtil.createComplex3x1DArray(dimension);
		return source3x1;
	}

	public void setSource3x1(Complex3x1[] source3x1) {
		this.source3x1 = source3x1;
	}

	public Complex3x3[][] getMatrix3x3() {
		return matrix3x3;
	}

	public void setMatrix3x3(Complex3x3[][] matrix3x3) {
		this.matrix3x3 = matrix3x3;
	}

	public int getDimension() {
		return dimension;
	}

	public void setDimension(int dimension) {
		this.dimension = dimension;
	}
	
	
	public NetworkEquivalent transformType(EquivType fType, EquivType tType){
		
		
		return null;
	}
	
	
	public NetworkEquivalent transformCoordinate(Coordinate toType){
		
		NetworkEquivalent transEquiv = new NetworkEquivalent(this.getDimension());
		
		if(this.equivCoordinate==Coordinate.Three_phase){
			if(toType == Coordinate.Three_sequence){
				
				//step-1 set the target coordinate
				transEquiv.setEquivCoordinate(toType);
				
				
				//step-1.1 if the original matrix is stored in a basic matrix format, it is first transformed to a 3x3 block matrix format
				// to better perform the coordinate the 3-phase to 3-seq transformation
				if(this.matrix!=null && this.matrix3x3 == null)
					      transMatrixTo3x3BlockMatrix();
				if(this.source!=null && this.source3x1 == null)
					      transSourceTo3x1BlockVector();
				
				//Step-2 perform 3-phase to three-sequence transformation for the matrix part
				int dim = this.matrix3x3[0].length;
				Complex3x3[][] m3x3 = new Complex3x3[dim][dim];
				
				for(int i =0;i<dim;i++){
					for(int j =0;j<dim;j++){
						m3x3[i][j] = Complex3x3.abc_to_120(this.matrix3x3[i][j]);
					}
				}
				
				transEquiv.setMatrix3x3(m3x3);
				
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
    	FieldMatrix<Complex> fmT= MatrixUtils.createFieldMatrix(T);
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
    	FieldMatrix<Complex> fmTinv= MatrixUtils.createFieldMatrix(Tinv);
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
		FieldMatrix<Complex> fmT= MatrixUtils.createFieldMatrix(T);
		FieldMatrix<Complex> fmTinv= MatrixUtils.createFieldMatrix(Tinv);
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
		FieldMatrix<Complex> fmT= MatrixUtils.createFieldMatrix(T);
		FieldMatrix<Complex> fmTinv= MatrixUtils.createFieldMatrix(Tinv);
		Complex[][] Z120 = fmTinv.multiply(fmZabc).multiply(fmT).getData();
	    return Z120;
	}
	
	
	public Complex3x3[][] transMatrixTo3x3BlockMatrix(){
                //check the dimension if it is valid
		        if((getDimension() % 3)!=0){
		        	IpssLogger.getLogger().severe("The dimension of the matrix is not multiple times of 3!");
		        	return null;
		        }
		        else{
		        	int k = dimension/3;
		        	// this is the new dimension
		        	this.setDimension(k);
		        	
		        	Complex3x3[][] blockMatrix = new Complex3x3[k][k] ;
		        	
		        	
					// perform matrix transformation in a 3x3 block-wise manner
					for(int i=0; i<k; i++){
						for(int j=0; j<k; j++){
							
							Complex3x3 m3x3 =  new Complex3x3();
							
							m3x3.aa=matrix[3*i][3*j];
							m3x3.ab=matrix[3*i][3*j+1];
							m3x3.ac=matrix[3*i][3*j+2];
							
							m3x3.ba=matrix[3*i+1][3*j];
							m3x3.bb=matrix[3*i+1][3*j+1];
							m3x3.bc=matrix[3*i+1][3*j+2];
							
							m3x3.ca=matrix[3*i+2][3*j];
							m3x3.cb=matrix[3*i+2][3*j+1];
							m3x3.cc=matrix[3*i+2][3*j+2];
							
							blockMatrix[i][j] = m3x3;
							
						}
						
					}
					return this.matrix3x3 = blockMatrix;
		        }
				
	}
	
	public Complex3x1[] transSourceTo3x1BlockVector(){
        //check the dimension if it is valid
        if((getDimension() % 3)!=0){
        	IpssLogger.getLogger().severe("The dimension of the matrix is not multiple times of 3!");
        	return null;
        }
        else{
        	int k = dimension/3;
        	Complex3x1[] blockSource = new Complex3x1[k] ;
        	
			// perform matrix transformation in a 3x3 block-wise manner
			for(int i=0; i<dimension/3; i++){
					Complex3x1 v3X1 =  new Complex3x1();
					
					v3X1.a_0 = this.source[3*i];
					v3X1.b_1 = this.source[3*i+1];
					v3X1.c_2 = this.source[3*i+2];
					
					blockSource[i] = v3X1;
				
			}
			return this.source3x1 = blockSource;
        }
		
}
	
	

}
