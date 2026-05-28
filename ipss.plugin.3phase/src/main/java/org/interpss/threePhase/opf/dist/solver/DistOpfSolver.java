package org.interpss.threePhase.opf.dist.solver;

import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;

public interface DistOpfSolver {

	DistOpfSolverResult solve(DistOpfModel model, DistOpfOptions options);
}
