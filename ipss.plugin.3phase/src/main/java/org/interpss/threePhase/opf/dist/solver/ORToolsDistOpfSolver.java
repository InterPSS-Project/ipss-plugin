package org.interpss.threePhase.opf.dist.solver;

import java.util.ArrayList;
import java.util.List;

import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.DistOpfStatus;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;

public class ORToolsDistOpfSolver implements DistOpfSolver {

	private static final String LOADER_CLASS = "com.google.ortools.Loader";

	@Override
	public DistOpfSolverResult solve(DistOpfModel model, DistOpfOptions options) {
		List<String> diagnostics = new ArrayList<String>();
		diagnostics.add("OR-Tools Java solver adapter is present but not wired to a native OR-Tools dependency.");
		diagnostics.add("Add the OR-Tools Java artifact/native runtime before using this adapter for DistOPF.");
		if (!isOrToolsAvailable()) {
			diagnostics.add("Missing class: " + LOADER_CLASS);
		}
		return new DistOpfSolverResult(DistOpfStatus.ERROR, Double.NaN, Double.NaN,
				new double[Math.max(model.getNumberOfVariables(), 0)],
				"OR-Tools DistOPF solver is unavailable", new ArrayList<String>(), diagnostics);
	}

	public boolean isOrToolsAvailable() {
		try {
			Class.forName(LOADER_CLASS);
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}
}
