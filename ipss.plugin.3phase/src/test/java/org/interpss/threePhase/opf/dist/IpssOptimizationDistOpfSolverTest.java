package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.interpss.optimization.backend.highs.HighsBackendFactory;
import org.interpss.optimization.backend.highs.HighsBackendType;
import org.interpss.threePhase.opf.dist.constraint.DistOpfConstraintFactory;
import org.interpss.threePhase.opf.dist.model.DistOpfCsvModelDataImporter;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;
import org.interpss.threePhase.opf.dist.model.LinDistFlowModelBuilder;
import org.interpss.threePhase.opf.dist.solver.DistOpfSolverResult;
import org.interpss.threePhase.opf.dist.solver.IpssOptimizationDistOpfSolver;
import org.interpss.threePhase.opf.dist.solver.ORToolsDistOpfSolver;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.PhaseCode;

public class IpssOptimizationDistOpfSolverTest {

	private static final Path CASE_ROOT = Paths.get("src/test/resources/distopf");

	@Test
	public void solvesContinuousLinearProgramLikeOrToolsSolver() {
		DistOpfVariableIndex variableIndex = new DistOpfVariableIndex();
		int x = variableIndex.busV2("b1", PhaseCode.A);
		DistOpfModel model = new DistOpfModel(variableIndex);
		model.setLinearObjective(new double[] { 1.0 });
		model.addConstraint(DistOpfConstraintFactory.greaterThan(0, "x-min",
				2.0, new int[] { x }, new double[] { 1.0 }));

		DistOpfOptions options = new DistOpfOptions();
		DistOpfSolverResult orTools = new ORToolsDistOpfSolver().solve(model, options);
		DistOpfSolverResult optimization = optimizationOrToolsSolver().solve(model, options);

		assertEquals(DistOpfStatus.OPTIMAL, orTools.getStatus(), orTools.getDiagnostics().toString());
		assertEquals(orTools.getStatus(), optimization.getStatus(), optimization.getDiagnostics().toString());
		assertEquals(orTools.getObjectiveValue(), optimization.getObjectiveValue(), 1.0e-9);
		assertEquals(orTools.getMaxConstraintResidual(), optimization.getMaxConstraintResidual(), 1.0e-9);
		assertEquals(orTools.getPrimalVariables()[x], optimization.getPrimalVariables()[x], 1.0e-9);
		assertEquals(orTools.getBindingConstraints(), optimization.getBindingConstraints());
	}

	@Test
	public void benchmarksCsvOpfCaseAgainstOrToolsSolver() {
		DistOpfOptions options = new DistOpfOptions()
				.setMinVoltagePu(0.0)
				.setMaxVoltagePu(2.0);
		DistOpfModelData data = new DistOpfCsvModelDataImporter().importModel(
				CASE_ROOT.resolve("4Bus-YY-Bal_dss"), false);
		DistOpfModel model = new LinDistFlowModelBuilder().build(data, options,
				DistOpfControlMode.P, DistOpfObjective.CURTAILMENT_MIN);

		DistOpfSolverResult orTools = new ORToolsDistOpfSolver().solve(model, options);
		DistOpfSolverResult optimization = optimizationOrToolsSolver().solve(model, options);

		assertEquals(DistOpfStatus.OPTIMAL, orTools.getStatus(), orTools.getDiagnostics().toString());
		assertEquals(orTools.getStatus(), optimization.getStatus(), optimization.getDiagnostics().toString());
		assertEquals(orTools.getObjectiveValue(), optimization.getObjectiveValue(), 1.0e-7);
		assertTrue(optimization.getMaxConstraintResidual() < 1.0e-7,
				"max residual=" + optimization.getMaxConstraintResidual());
		assertEquals(orTools.getPrimalVariables()[model.getVariableIndex().branchP("line1", PhaseCode.A)],
				optimization.getPrimalVariables()[model.getVariableIndex().branchP("line1", PhaseCode.A)],
				1.0e-7);
		assertEquals(orTools.getPrimalVariables()[model.getVariableIndex().branchQ("line1", PhaseCode.A)],
				optimization.getPrimalVariables()[model.getVariableIndex().branchQ("line1", PhaseCode.A)],
				1.0e-7);
	}

	private static IpssOptimizationDistOpfSolver optimizationOrToolsSolver() {
		return new IpssOptimizationDistOpfSolver(HighsBackendFactory.create(HighsBackendType.ORTOOLS));
	}
}
