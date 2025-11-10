package org.interpss.plugin.optadj.algo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 
 * 2D Matrix class for storing sensitivity matrix values
 * 
 *
 */
public class Sen2DMatrix {
    private static final Logger log = LoggerFactory.getLogger(Sen2DMatrix.class);
    
	// the 2D data array to store the matrix values
	private double[][] data;
	
	// the row and column index arrays
	private int[] rowIndex, colIndex;
	
	/**
	 * Constructor
	 * 
	 * @param row the number of rows
	 * @param col the number of columns
	 */
	public Sen2DMatrix(int row, int col) {
		data = new double[row][col];
		rowIndex = new int[row];
		colIndex = new int[col];
		for (int i = 0; i < row; i++) {
			rowIndex[i] = i;
		}
		for (int j = 0; j < col; j++) {
			colIndex[j] = j;
		}
	}
	
	/**
	 * Get the value at specified row and column
	 * 
	 * @param rowIdx the row index
	 * @param colIdx the column index
	 * @return the value at specified row and column
	 */
	public double get(int rowIdx, int colIdx) {
		int r = rowIndex[rowIdx];
		int c = colIndex[colIdx];
		if (r >= 0 && c >= 0)
			return data[r][c];
		else {
			log.warn("Sen2DMatrix.get(): invalid rowIdx or colIdx, return 0.0");
			return 0.0;
		}
	}
	
	/**
	 * Set the value at specified row and column
	 * 
	 * @param rowIdx the row index
	 * @param colInx the column index
	 * @param value the value to set
	 */
	public void set(int rowIdx, int colInx, double value) {
		int r = rowIndex[rowIdx];
		int c = colIndex[colInx];
		data[r][c] = value;
	}
	
	/**
	 * Set the row index array
	 * 
	 * @param rowIndexAry the row index array
	 */
	public void setRowIndex(int[] rowIndexAry) {
		this.rowIndex = rowIndexAry;
	}
	
	/**
	 * Set the column index array
	 * 
	 * @param colIndexAry the column index array
	 */
	public void setColIndex(int[] colIndexAry) {
		this.colIndex = colIndexAry;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("row, col, rowIdx, colInx: " + data.length + ", " + data[0].length
					+ ", " + rowIndex.length + ", " + colIndex.length + "\n");
		for (int i = 0; i < rowIndex.length; i++) {
			for (int j = 0; j < colIndex.length; j++) {
				sb.append(String.format("%7.3f", get(i,j)));
				if (j < colIndex.length - 1)
					sb.append(", ");
			}
			sb.append("\n");
		}
		return sb.toString();
	}
}
