package org.interpss.core.sparse.solver.klusolvex;

record KlusolveXMatrixEntry(int row, int col, double re, double im) {
	boolean samePosition(KlusolveXMatrixEntry other) {
		return this.row == other.row && this.col == other.col;
	}
}
