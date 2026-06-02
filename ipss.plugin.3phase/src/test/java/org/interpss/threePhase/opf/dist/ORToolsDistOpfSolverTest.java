package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.threePhase.opf.dist.constraint.DistOpfConstraintFactory;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;
import org.interpss.threePhase.opf.dist.solver.DistOpfSolverResult;
import org.interpss.threePhase.opf.dist.solver.ORToolsDistOpfSolver;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.PhaseCode;

public class ORToolsDistOpfSolverTest {

	@Test
	public void solvesContinuousLinearProgram() {
		ORToolsDistOpfSolver solver = new ORToolsDistOpfSolver();
		DistOpfVariableIndex variableIndex = new DistOpfVariableIndex();
		int x = variableIndex.busV2("b1", PhaseCode.A);
		DistOpfModel model = new DistOpfModel(variableIndex);
		model.setLinearObjective(new double[] { 1.0 });
		model.addConstraint(DistOpfConstraintFactory.greaterThan(0, "x-min",
				2.0, new int[] { x }, new double[] { 1.0 }));

		DistOpfSolverResult result = solver.solve(model, new DistOpfOptions());

		assertTrue(solver.isOrToolsAvailable());
		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(2.0, result.getPrimalVariables()[x], 1.0e-9);
		assertEquals(2.0, result.getObjectiveValue(), 1.0e-9);
	}
}
