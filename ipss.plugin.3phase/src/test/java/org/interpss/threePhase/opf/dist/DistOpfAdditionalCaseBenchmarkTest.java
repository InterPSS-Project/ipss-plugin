package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.interpss.threePhase.opf.dist.model.DistOpfCsvModelDataImporter;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.LinDistFlowModelBuilder;
import org.interpss.threePhase.opf.dist.solver.DirectLinDistFlowPowerFlowSolver;
import org.interpss.threePhase.opf.dist.solver.DistOpfSolverResult;
import org.interpss.threePhase.opf.dist.solver.ORToolsDistOpfSolver;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfAdditionalCaseBenchmarkTest {

	private static final Path CASE_ROOT = Paths.get("src/test/resources/distopf");

	@Test
	public void solvesFourBusCsvPowerFlowAgainstPythonDistopf() {
		SolvedModel solved = solvePowerFlow("4Bus-YY-Bal_dss", 4, 3);

		assertVoltage(solved.result, solved.model, "1", PhaseCode.A, 1.0, 1.0e-9);
		assertVoltage(solved.result, solved.model, "1", PhaseCode.B, 1.0, 1.0e-9);
		assertVoltage(solved.result, solved.model, "1", PhaseCode.C, 1.0, 1.0e-9);
		assertVoltage(solved.result, solved.model, "2", PhaseCode.A, 0.9906137500063679, 1.0e-6);
		assertEquals(1.799941343138584, solved.result.getPrimalVariables()[
				solved.model.getVariableIndex().branchP("line1", PhaseCode.A)], 1.0e-6);
		assertEquals(0.8716050855056401, solved.result.getPrimalVariables()[
				solved.model.getVariableIndex().branchQ("line1", PhaseCode.A)], 1.0e-6);
	}

	@Test
	public void solvesIeee123CsvPowerFlowAgainstPythonDistopf() {
		SolvedModel solved = solvePowerFlow("ieee123", 130, 129);

		assertVoltage(solved.result, solved.model, "1", PhaseCode.A, 1.0, 1.0e-9);
		assertVoltage(solved.result, solved.model, "1", PhaseCode.B, 1.0, 1.0e-9);
		assertVoltage(solved.result, solved.model, "1", PhaseCode.C, 1.0, 1.0e-9);
		assertVoltage(solved.result, solved.model, "2", PhaseCode.A, 1.0375, 1.0e-9);
		assertEquals(1.4080711885725221, solved.result.getPrimalVariables()[
				solved.model.getVariableIndex().branchP("reg1a", PhaseCode.A)], 1.0e-6);
		assertEquals(0.4779178132545617, solved.result.getPrimalVariables()[
				solved.model.getVariableIndex().branchQ("reg1a", PhaseCode.A)], 1.0e-6);
	}

	@Test
	public void solvesIeee9500CsvOpfWithOrToolsAgainstPythonDistopf() {
		DistOpfOptions options = new DistOpfOptions()
				.setSolverType(DistOpfSolverType.ORTOOLS)
				.setMinVoltagePu(0.0)
				.setMaxVoltagePu(2.0);
		DistOpfModelData data = new DistOpfCsvModelDataImporter().importModel(CASE_ROOT.resolve("9500"), false);
		DistOpfModel model = new LinDistFlowModelBuilder().build(data, options,
				DistOpfControlMode.P, DistOpfObjective.CURTAILMENT_MIN);

		DistOpfSolverResult result = new ORToolsDistOpfSolver().solve(model, options);

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(2752, data.getBuses().size());
		assertEquals(2751, data.getBranches().size());
		assertTrue(result.getMaxConstraintResidual() < 1.0e-7);
		assertVoltage(result, model, "1", PhaseCode.A, 1.0499999999999419, 1.0e-7);
		assertVoltage(result, model, "1", PhaseCode.B, 1.0499999999999436, 1.0e-7);
		assertVoltage(result, model, "1", PhaseCode.C, 1.0499999999999479, 1.0e-7);
		assertVoltage(result, model, "2", PhaseCode.A, 1.0495639796871705, 5.0e-6);
		assertEquals(3.1533251064819865, result.getPrimalVariables()[
				model.getVariableIndex().branchP("hvmv_sub_hsb", PhaseCode.A)], 2.0e-4);
		assertEquals(0.13635411335380931, result.getPrimalVariables()[
				model.getVariableIndex().branchQ("hvmv_sub_hsb", PhaseCode.A)], 5.0e-4);
	}

	private static SolvedModel solvePowerFlow(String caseName, int expectedBuses, int expectedBranches) {
		DistOpfOptions options = new DistOpfOptions().setMinVoltagePu(0.0).setMaxVoltagePu(2.0);
		DistOpfModelData data = new DistOpfCsvModelDataImporter().importModel(CASE_ROOT.resolve(caseName), false);
		DistOpfModel model = new LinDistFlowModelBuilder().build(data, options);
		DistOpfSolverResult result = new DirectLinDistFlowPowerFlowSolver().solve(model, options);
		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(expectedBuses, data.getBuses().size());
		assertEquals(expectedBranches, data.getBranches().size());
		return new SolvedModel(model, result);
	}

	private static void assertVoltage(DistOpfSolverResult result, DistOpfModel model,
			String busId, PhaseCode phase, double expectedMagnitude, double tolerance) {
		double voltageSquared = result.getPrimalVariables()[model.getVariableIndex().busV2(busId, phase)];
		assertEquals(expectedMagnitude, Math.sqrt(voltageSquared), tolerance,
				busId + "." + phase.name());
	}

	private static class SolvedModel {
		private final DistOpfModel model;
		private final DistOpfSolverResult result;

		private SolvedModel(DistOpfModel model, DistOpfSolverResult result) {
			this.model = model;
			this.result = result;
		}
	}
}
