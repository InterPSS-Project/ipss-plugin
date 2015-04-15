package org.ipss.multiNet.equivalent;

import org.apache.commons.math3.complex.Complex;

public class NetworkEquivalent {
	
	public enum Coordinate {Three_phase, Three_sequence,Positive_sequence};
	
	public enum EquivType {Norton,Thevenin};
	
	
	private Coordinate equivCoordinate = Coordinate.Positive_sequence;
	
		 
	private  EquivType type = EquivType.Thevenin;
	
	
	private Complex[] source = null;
	
	private Complex[][] matrix = null;
	
	private int dimension = 0;


	private Coordinate equivCoordiante;
	
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

	public void setEquivCoordinate(Coordinate equivCoordiante) {
		this.equivCoordiante = equivCoordiante;
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

	public int getDimension() {
		return dimension;
	}

	public void setDimension(int dimension) {
		this.dimension = dimension;
	}
	
	

}
