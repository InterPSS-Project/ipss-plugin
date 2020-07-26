package org.interpss.threePhase.basic.acsc;

import org.interpss.numeric.sparse.ISparseEqnComplexMatrix3x3;

import com.interpss.core.abc.INetwork3Phase;

public interface Acsc3PNetworkTemp extends INetwork3Phase {
	

	
	public ISparseEqnComplexMatrix3x3 formYMatrixABC() throws Exception;
	
	public ISparseEqnComplexMatrix3x3 getYMatrixABC();
	
	
	public boolean run3PhaseLoadflow();
	
	
}
