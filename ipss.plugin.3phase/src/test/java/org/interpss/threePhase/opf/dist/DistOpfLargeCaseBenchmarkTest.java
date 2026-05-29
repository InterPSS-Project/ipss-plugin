package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Paths;

import org.interpss.threePhase.opf.dist.model.DistOpfCsvModelDataImporter;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.LinDistFlowModelBuilder;
import org.interpss.threePhase.opf.dist.solver.DirectLinDistFlowPowerFlowSolver;
import org.interpss.threePhase.opf.dist.solver.DistOpfSolverResult;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfLargeCaseBenchmarkTest {

	@Test
	public void solvesIeee9500PrimaryCsvCase() {
		DistOpfModelData data = new DistOpfCsvModelDataImporter().importModel(
				Paths.get("src/test/resources/distopf/9500-primary-network"), false);
		DistOpfOptions options = new DistOpfOptions().setMinVoltagePu(0.0).setMaxVoltagePu(2.0);
		DistOpfModel model = new LinDistFlowModelBuilder().build(data, options);

		DistOpfSolverResult result = new DirectLinDistFlowPowerFlowSolver().solve(model, options);

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(2752, data.getBuses().size());
		assertEquals(2751, data.getBranches().size());
		// Reference generated with Python distopf 0.2.0:
		// DistOPFCase(data_path=... / "9500-primary-network").run_pf()
		assertVoltage(result, model, "2", PhaseCode.A, 1.049564, 5.0e-6);
		assertVoltage(result, model, "2", PhaseCode.B, 1.049638, 5.0e-6);
		assertVoltage(result, model, "2", PhaseCode.C, 1.049593, 5.0e-6);
		assertVoltage(result, model, "38", PhaseCode.A, 1.036456, 5.0e-6);
		assertVoltage(result, model, "72", PhaseCode.B, 1.031900, 5.0e-6);
		assertVoltage(result, model, "120", PhaseCode.C, 1.025174, 5.0e-6);
		assertVoltage(result, model, "352", PhaseCode.A, 1.052356, 5.0e-4);
		assertEquals(3.153184653012561, result.getPrimalVariables()[
				model.getVariableIndex().branchP("hvmv_sub_hsb", PhaseCode.A)], 1.0e-9);
		assertEquals(0.13635286157010112, result.getPrimalVariables()[
				model.getVariableIndex().branchQ("hvmv_sub_hsb", PhaseCode.A)], 5.0e-4);
	}

	private static void assertVoltage(DistOpfSolverResult result, DistOpfModel model,
			String busId, PhaseCode phase, double expectedMagnitude, double tolerance) {
		double voltageSquared = result.getPrimalVariables()[model.getVariableIndex().busV2(busId, phase)];
		assertEquals(expectedMagnitude, Math.sqrt(voltageSquared), tolerance,
				busId + "." + phase.name());
	}
}
