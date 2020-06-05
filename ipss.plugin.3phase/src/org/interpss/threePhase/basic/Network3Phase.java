package org.interpss.threePhase.basic;

import org.interpss.numeric.sparse.ISparseEqnComplexMatrix3x3;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.BaseAcscNetwork;

public interface Network3Phase {
	

	
	public ISparseEqnComplexMatrix3x3 formYMatrixABC() throws Exception;
	
	public ISparseEqnComplexMatrix3x3 getYMatrixABC();
	
	
	public boolean run3PhaseLoadflow();
	
	
}
