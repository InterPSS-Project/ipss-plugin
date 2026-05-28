package org.interpss.threePhase.opf.dist.solver;

import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.DistOpfStatus;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;

public class OjAlgoDistOpfSolver implements DistOpfSolver {

	@Override
	public DistOpfSolverResult solve(DistOpfModel model, DistOpfOptions options) {
		if (model.getNumberOfVariables() == 0) {
			return new DistOpfSolverResult(DistOpfStatus.NOT_SOLVED, 0.0, 0.0,
					new double[0], "Empty DistOPF model");
		}
		return new DistOpfSolverResult(DistOpfStatus.NOT_SOLVED, 0.0, Double.NaN,
				new double[model.getNumberOfVariables()], "ojAlgo solve integration is not implemented yet");
	}
}
