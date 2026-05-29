package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;
import org.interpss.threePhase.opf.dist.solver.DistOpfSolverResult;
import org.interpss.threePhase.opf.dist.solver.ORToolsDistOpfSolver;
import org.junit.jupiter.api.Test;

public class ORToolsDistOpfSolverTest {

	@Test
	public void reportsUnavailableUntilOrToolsDependencyIsAdded() {
		ORToolsDistOpfSolver solver = new ORToolsDistOpfSolver();
		DistOpfModel model = new DistOpfModel(new DistOpfVariableIndex());

		DistOpfSolverResult result = solver.solve(model, new DistOpfOptions());

		assertFalse(solver.isOrToolsAvailable());
		assertEquals(DistOpfStatus.ERROR, result.getStatus());
		assertTrue(result.getDiagnostics().stream()
				.anyMatch(message -> message.contains("OR-Tools Java solver adapter")));
	}
}
