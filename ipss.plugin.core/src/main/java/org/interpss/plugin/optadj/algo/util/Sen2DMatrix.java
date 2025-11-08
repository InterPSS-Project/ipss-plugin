package org.interpss.plugin.optadj.algo.util;

public class Sen2DMatrix {
	private double[][] data;
	
	private int[] rowIndex, colIndex;
	
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
	
	public double get(int row, int col) {
		int r = rowIndex[row];
		int c = colIndex[col];
		if (r >= 0 && c >= 0)
			return data[r][c];
		else 
			return 0.0;
	}
	
	public void set(int row, int col, double value) {
		int r = rowIndex[row];
		int c = colIndex[col];
		data[r][c] = value;
	}
	
	public void setRowIndex(int[] rowIndex) {
		this.rowIndex = rowIndex;
	}
	
	public void setColIndex(int[] colIndex) {
		this.colIndex = colIndex;
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
