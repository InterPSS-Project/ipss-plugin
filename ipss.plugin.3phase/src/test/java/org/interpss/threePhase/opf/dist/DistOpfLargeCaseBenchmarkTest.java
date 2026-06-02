package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
import org.interpss.threePhase.opf.dist.model.DistOpfBusData;
import org.interpss.threePhase.opf.dist.model.DistOpfCsvModelDataImporter;
import org.interpss.threePhase.opf.dist.model.DistOpfDerData;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.LinDistFlowModelBuilder;
import org.interpss.threePhase.opf.dist.solver.DirectLinDistFlowPowerFlowSolver;
import org.interpss.threePhase.opf.dist.solver.DistOpfSolverResult;
import org.interpss.threePhase.opf.dist.solver.ORToolsDistOpfSolver;
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

	@Test
	public void solvesIeee9500PrimaryCsvOpfWithOrToolsAndValidatesWithPowerFlow() {
		DistOpfModelData data = new DistOpfCsvModelDataImporter().importModel(
				Paths.get("src/test/resources/distopf/9500-primary-network"), false);
		DistOpfOptions options = new DistOpfOptions()
				.setSolverType(DistOpfSolverType.ORTOOLS)
				.setMinVoltagePu(0.0)
				.setMaxVoltagePu(2.0);
		DistOpfModel opfModel = new LinDistFlowModelBuilder().build(data, options,
				DistOpfControlMode.P, DistOpfObjective.CURTAILMENT_MIN);

		DistOpfSolverResult opf = new ORToolsDistOpfSolver().solve(opfModel, options);

		assertEquals(DistOpfStatus.OPTIMAL, opf.getStatus());
		assertTrue(opf.getMaxConstraintResidual() < 1.0e-7);
		// Reference generated with Python distopf 0.2.0:
		// DistOPFCase(data_path=... / "9500-primary-network",
		//     control_variable="P", objective_function="curtail_min").run()
		// with v_min=0.0 and v_max=2.0 to match the relaxed Java benchmark.
		assertVoltage(opf, opfModel, "2", PhaseCode.A, 1.0495639796871705, 5.0e-6);
		assertVoltage(opf, opfModel, "2", PhaseCode.B, 1.0496384784633417, 5.0e-6);
		assertVoltage(opf, opfModel, "2", PhaseCode.C, 1.049592725337863, 5.0e-6);
		assertVoltage(opf, opfModel, "38", PhaseCode.A, 1.0364563594450191, 5.0e-6);
		assertVoltage(opf, opfModel, "72", PhaseCode.B, 1.0318995199389975, 5.0e-6);
		assertVoltage(opf, opfModel, "120", PhaseCode.C, 1.0251739741810915, 5.0e-6);
		assertVoltage(opf, opfModel, "352", PhaseCode.A, 1.0523549305355877, 5.0e-4);
		assertEquals(3.1533251064819865, opf.getPrimalVariables()[
				opfModel.getVariableIndex().branchP("hvmv_sub_hsb", PhaseCode.A)], 2.0e-4);
		assertEquals(0.13635411335380931, opf.getPrimalVariables()[
				opfModel.getVariableIndex().branchQ("hvmv_sub_hsb", PhaseCode.A)], 5.0e-4);
		assertEquals(0.01458791615083284, opf.getPrimalVariables()[
				opfModel.getVariableIndex().derP("pv_1025", PhaseCode.B)], 5.0e-6);

		DistOpfModelData fixedDispatchData = fixedDispatchData(data, opfModel, opf);
		DistOpfModel powerFlowModel = new LinDistFlowModelBuilder().build(fixedDispatchData, options);
		DistOpfSolverResult powerFlow = new DirectLinDistFlowPowerFlowSolver().solve(powerFlowModel, options);

		assertEquals(DistOpfStatus.OPTIMAL, powerFlow.getStatus());
		assertTrue(maxVoltageMagnitudeDiff(opfModel, opf, powerFlowModel, powerFlow) < 1.0e-7);
		assertEquals(opf.getPrimalVariables()[opfModel.getVariableIndex().branchP("hvmv_sub_hsb", PhaseCode.A)],
				powerFlow.getPrimalVariables()[powerFlowModel.getVariableIndex().branchP("hvmv_sub_hsb", PhaseCode.A)],
				1.0e-7);
		assertEquals(opf.getPrimalVariables()[opfModel.getVariableIndex().branchQ("hvmv_sub_hsb", PhaseCode.A)],
				powerFlow.getPrimalVariables()[powerFlowModel.getVariableIndex().branchQ("hvmv_sub_hsb", PhaseCode.A)],
				1.0e-7);
	}

	private static void assertVoltage(DistOpfSolverResult result, DistOpfModel model,
			String busId, PhaseCode phase, double expectedMagnitude, double tolerance) {
		double voltageSquared = result.getPrimalVariables()[model.getVariableIndex().busV2(busId, phase)];
		assertEquals(expectedMagnitude, Math.sqrt(voltageSquared), tolerance,
				busId + "." + phase.name());
	}

	private static DistOpfModelData fixedDispatchData(DistOpfModelData data,
			DistOpfModel opfModel, DistOpfSolverResult opf) {
		List<DistOpfDerData> fixedDers = new ArrayList<DistOpfDerData>();
		for (DistOpfDerData der : data.getDers()) {
			fixedDers.add(new DistOpfDerData(der.getId(), der.getBusId(), der.getPhases(),
					new Complex3x1(dispatch(opfModel, opf, der, PhaseCode.A),
							dispatch(opfModel, opf, der, PhaseCode.B),
							dispatch(opfModel, opf, der, PhaseCode.C)),
					der.getApparentPowerLimitPu()));
		}
		return new DistOpfModelData(data.getBaseMva(), data.getSwingBusId(),
				data.getBuses(), data.getBranches(), fixedDers,
				data.getCapacitors(), data.getRegulators(),
				children(data),
				parentBranches(data));
	}

	private static Complex dispatch(DistOpfModel model, DistOpfSolverResult result,
			DistOpfDerData der, PhaseCode phase) {
		if (!der.getPhases().contains(phase)) {
			return Complex.ZERO;
		}
		return new Complex(result.getPrimalVariables()[model.getVariableIndex().derP(der.getId(), phase)],
				result.getPrimalVariables()[model.getVariableIndex().derQ(der.getId(), phase)]);
	}

	private static Map<String, List<DistOpfBranchData>> children(DistOpfModelData data) {
		Map<String, List<DistOpfBranchData>> children =
				new LinkedHashMap<String, List<DistOpfBranchData>>();
		for (DistOpfBranchData branch : data.getBranches()) {
			List<DistOpfBranchData> busChildren = children.get(branch.getFromBusId());
			if (busChildren == null) {
				busChildren = new ArrayList<DistOpfBranchData>();
				children.put(branch.getFromBusId(), busChildren);
			}
			busChildren.add(branch);
		}
		return children;
	}

	private static Map<String, DistOpfBranchData> parentBranches(DistOpfModelData data) {
		Map<String, DistOpfBranchData> parents =
				new LinkedHashMap<String, DistOpfBranchData>();
		for (DistOpfBranchData branch : data.getBranches()) {
			parents.put(branch.getToBusId(), branch);
		}
		return parents;
	}

	private static double maxVoltageMagnitudeDiff(DistOpfModel opfModel, DistOpfSolverResult opf,
			DistOpfModel powerFlowModel, DistOpfSolverResult powerFlow) {
		double max = 0.0;
		for (DistOpfBusData bus : opfModel.getModelData().getBuses()) {
			for (PhaseCode phase : bus.getPhases()) {
				double opfVoltage = Math.sqrt(opf.getPrimalVariables()[
						opfModel.getVariableIndex().busV2(bus.getId(), phase)]);
				double powerFlowVoltage = Math.sqrt(powerFlow.getPrimalVariables()[
						powerFlowModel.getVariableIndex().busV2(bus.getId(), phase)]);
				max = Math.max(max, Math.abs(opfVoltage - powerFlowVoltage));
			}
		}
		return max;
	}
}
