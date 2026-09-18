package org.interpss.threePhase.opf.dist.solver;

import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.DistOpfSolverType;

public final class DistOpfSolverFactory {

	private DistOpfSolverFactory() {
	}

	public static DistOpfSolver create(DistOpfOptions options) {
		if (options.getSolverType() == DistOpfSolverType.APACHE_LP) {
			return new ApacheLpDistOpfSolver();
		}
		if (options.getSolverType() == DistOpfSolverType.ORTOOLS) {
			return new ORToolsDistOpfSolver();
		}
		return new OjAlgoDistOpfSolver();
	}
}
