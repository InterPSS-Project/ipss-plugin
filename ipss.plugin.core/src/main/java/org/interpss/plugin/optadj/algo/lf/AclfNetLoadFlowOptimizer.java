package org.interpss.plugin.optadj.algo.lf;

import org.interpss.plugin.optadj.algo.util.AclfNetSensHelper;

import com.interpss.core.aclf.AclfNetwork;

/**
 * 
 * 
 */
public class AclfNetLoadFlowOptimizer extends BaseAclfNetLoadFlowOptimizer{

	private float[][] senMatrix = null;

	@Override
	protected void createSenMatrix(AclfNetwork net) {
		AclfNetSensHelper helper = new AclfNetSensHelper(net);
		senMatrix = helper.calSen();
	}
	
	@Override
	protected float getSen(int busNo, int branchNo) {
		return senMatrix[busNo][branchNo];
	}
}
