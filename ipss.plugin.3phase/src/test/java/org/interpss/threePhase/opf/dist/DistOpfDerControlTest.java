package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.opf.dist.model.DistOpfBatteryData;
import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
import org.interpss.threePhase.opf.dist.model.DistOpfBusData;
import org.interpss.threePhase.opf.dist.model.DistOpfCapacitorData;
import org.interpss.threePhase.opf.dist.model.DistOpfDerData;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelDataExtractor;
import org.interpss.threePhase.opf.dist.model.DistOpfRegulatorData;
import org.interpss.threePhase.opf.dist.model.LinDistFlowModelBuilder;
import org.interpss.threePhase.opf.dist.solver.DistOpfSolverResult;
import org.interpss.threePhase.opf.dist.solver.OjAlgoDistOpfSolver;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;

public class DistOpfDerControlTest {

	@Test
	public void fixedDerReducesUpstreamBranchFlow() throws InterpssException {
		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(createTwoBusFeederWithDer())
				.setControlMode(DistOpfControlMode.NONE)
				.solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(0.04, result.getDerActivePower("der-1", "A"), 1.0e-7);
		assertEquals(0.06, result.getBranchActivePower("source->load(0)", "A"), 1.0e-7);
		assertEquals(0.9972, result.getBusVoltageSquared("load", "A"), 1.0e-7);
	}

	@Test
	public void genMaxObjectiveUsesAvailableDerP() throws InterpssException {
		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(createTwoBusFeederWithDer())
				.setControlMode(DistOpfControlMode.P)
				.setObjective(DistOpfObjective.GEN_MAX)
				.solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(-0.12, result.getObjectiveValue(), 1.0e-7);
		assertTrue(result.getDerActivePower().values().stream().allMatch(v -> Math.abs(v - 0.04) < 1.0e-7));
		assertEquals(0.06, result.getBranchActivePower("source->load(0)", "A"), 1.0e-7);
	}

	@Test
	public void thermalLimitBindsWhenDerCanRelieveBranchFlow() throws InterpssException {
		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(createTwoBusFeederWithDer(0.06))
				.setControlMode(DistOpfControlMode.P)
				.setObjective(DistOpfObjective.GEN_MAX)
				.solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(0.06, result.getBranchActivePower("source->load(0)", "A"), 1.0e-7);
		assertEquals(0.06, result.getBranchActivePower("source->load(0)", "B"), 1.0e-7);
		assertEquals(0.06, result.getBranchActivePower("source->load(0)", "C"), 1.0e-7);
		assertTrue(result.getBindingConstraints().stream()
				.anyMatch(c -> c.contains("Thermal@source->load(0).A.P@upper")));
	}

	@Test
	public void thermalLimitReportsInfeasibleWhenNoControlCanRelieveBranchFlow() throws InterpssException {
		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(createTwoBusFeeder(0.09)).solve();

		assertEquals(DistOpfStatus.INFEASIBLE, result.getStatus());
		assertFalse(result.getDiagnostics().isEmpty());
		assertTrue(result.getDiagnostics().stream().anyMatch(d -> d.contains("INFEASIBLE")));
	}

	@Test
	public void targetSubstationPObjectiveControlsDerP() throws InterpssException {
		DistOpfOptions options = new DistOpfOptions().setTargetSubstationPPu(0.18);
		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(createTwoBusFeederWithDer())
				.setControlMode(DistOpfControlMode.P)
				.setObjective(DistOpfObjective.TARGET_SUBSTATION_P)
				.setOptions(options)
				.solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(0.0, result.getObjectiveValue(), 1.0e-7);
		assertEquals(0.04, result.getDerActivePower("der-1", "A"), 1.0e-7);
		assertEquals(0.06, result.getBranchActivePower("source->load(0)", "A"), 1.0e-7);
	}

	@Test
	public void qControlCorrectsLowVoltageWithReactiveSupport() throws InterpssException {
		DistOpfOptions options = new DistOpfOptions().setMinVoltagePu(Math.sqrt(0.998));

		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(createTwoBusFeederWithDer())
				.setControlMode(DistOpfControlMode.Q)
				.setOptions(options)
				.solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(0.01, result.getDerReactivePower("der-1", "A"), 1.0e-7);
		assertEquals(0.998, result.getBusVoltageSquared("load", "A"), 1.0e-7);
		assertEquals(0.01, result.getBranchReactivePower("source->load(0)", "A"), 1.0e-7);
	}

	@Test
	public void switchedCapacitorClosesToSatisfyVoltageLimit() throws InterpssException {
		DistOpfOptions options = new DistOpfOptions().setMinVoltagePu(Math.sqrt(0.997));
		DistOpfModelData baseData = new DistOpfModelDataExtractor().extract(createTwoBusFeeder(0.0));
		ArrayList<DistOpfCapacitorData> capacitors = new ArrayList<DistOpfCapacitorData>();
		Complex capStep = new Complex(0.0, 0.01);
		capacitors.add(new DistOpfCapacitorData("cap-1", "load",
				EnumSet.of(PhaseCode.A, PhaseCode.B, PhaseCode.C),
				new Complex3x1(capStep, capStep, capStep)));
		DistOpfModelData modelData = new DistOpfModelData(baseData.getBaseMva(),
				baseData.getSwingBusId(), baseData.getBuses(), baseData.getBranches(),
				baseData.getDers(), capacitors, childrenByBusId(baseData), parentBranchByBusId(baseData));
		DistOpfModel model = new LinDistFlowModelBuilder().build(modelData, options);

		DistOpfSolverResult result = new OjAlgoDistOpfSolver().solve(model, options);

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(1.0, result.getPrimalVariables()[
				model.getVariableIndex().capacitorStatus("cap-1", PhaseCode.A)], 1.0e-7);
		assertEquals(1.0, result.getPrimalVariables()[
				model.getVariableIndex().capacitorStatus("cap-1", PhaseCode.B)], 1.0e-7);
		assertEquals(1.0, result.getPrimalVariables()[
				model.getVariableIndex().capacitorStatus("cap-1", PhaseCode.C)], 1.0e-7);
		assertEquals(0.01, result.getPrimalVariables()[
				model.getVariableIndex().branchQ("source->load(0)", PhaseCode.A)], 1.0e-7);
		assertEquals(0.9972, result.getPrimalVariables()[
				model.getVariableIndex().busV2("load", PhaseCode.A)], 1.0e-7);
	}

	@Test
	public void regulatorTapRaisesVoltageWithinDiscreteLimits() throws InterpssException {
		DistOpfOptions options = new DistOpfOptions();
		DistOpfModelData baseData = new DistOpfModelDataExtractor().extract(createTwoBusFeeder(0.0));
		ArrayList<DistOpfBusData> buses = new ArrayList<DistOpfBusData>();
		for (DistOpfBusData bus : baseData.getBuses()) {
			if ("load".equals(bus.getId())) {
				buses.add(new DistOpfBusData(bus.getId(), bus.isSwing(), bus.getBaseVoltage(),
						bus.getPhases(), bus.getLoad(), bus.getFixedCapacitorQ(),
						Math.sqrt(1.001), Math.sqrt(1.002)));
			}
			else {
				buses.add(bus);
			}
		}
		ArrayList<DistOpfRegulatorData> regulators = new ArrayList<DistOpfRegulatorData>();
		regulators.add(new DistOpfRegulatorData("reg-1", "source->load(0)",
				EnumSet.of(PhaseCode.A, PhaseCode.B, PhaseCode.C), 0, 2, 0.005));
		DistOpfModelData modelData = new DistOpfModelData(baseData.getBaseMva(),
				baseData.getSwingBusId(), buses, baseData.getBranches(),
				baseData.getDers(), baseData.getCapacitors(), regulators,
				childrenByBusId(baseData), parentBranchByBusId(baseData));
		DistOpfModel model = new LinDistFlowModelBuilder().build(modelData, options);

		DistOpfSolverResult result = new OjAlgoDistOpfSolver().solve(model, options);

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(1.0, result.getPrimalVariables()[
				model.getVariableIndex().regulatorTap("reg-1", PhaseCode.A)], 1.0e-7);
		assertEquals(1.0014, result.getPrimalVariables()[
				model.getVariableIndex().busV2("load", PhaseCode.A)], 1.0e-7);
	}

	@Test
	public void busVoltageLimitOverridesGlobalOptionLimit() throws InterpssException {
		DStabNetwork3Phase net = createTwoBusFeederWithDer();
		net.getBus("load").setVLimit(new LimitType(1.05, Math.sqrt(0.998)));

		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(net)
				.setControlMode(DistOpfControlMode.Q)
				.solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(0.01, result.getDerReactivePower("der-1", "A"), 1.0e-7);
		assertEquals(0.998, result.getBusVoltageSquared("load", "A"), 1.0e-7);
	}

	@Test
	public void inverterCapabilityLimitsReactiveSupport() throws InterpssException {
		DistOpfOptions options = new DistOpfOptions().setTargetSubstationQPu(-0.02);

		DistOpfResult result = ThreePhaseObjectFactory
				.createDistOpfAlgorithm(createTwoBusFeederWithDerPower(new Complex(0.04, 0.0), 0.0, 0.135))
				.setControlMode(DistOpfControlMode.Q)
				.setObjective(DistOpfObjective.TARGET_SUBSTATION_Q)
				.setOptions(options)
				.solve();

		double qLimit = Math.sqrt(2.0) * 0.045 - 0.04;
		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(qLimit, result.getDerReactivePower("der-1", "A"), 1.0e-7);
		assertEquals(0.02 - qLimit, result.getBranchReactivePower("source->load(0)", "A"), 1.0e-7);
	}

	@Test
	public void pCurtailmentCorrectsHighVoltageFromExport() throws InterpssException {
		DistOpfOptions options = new DistOpfOptions().setMaxVoltagePu(Math.sqrt(1.0002));

		DistOpfResult result = ThreePhaseObjectFactory
				.createDistOpfAlgorithm(createTwoBusFeederWithDerPower(new Complex(0.2, 0.0)))
				.setControlMode(DistOpfControlMode.P)
				.setObjective(DistOpfObjective.CURTAILMENT_MIN)
				.setOptions(options)
				.solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(0.19, result.getDerActivePower("der-1", "A"), 1.0e-7);
		assertEquals(1.0002, result.getBusVoltageSquared("load", "A"), 1.0e-7);
		assertEquals(-0.09, result.getBranchActivePower("source->load(0)", "A"), 1.0e-7);
	}

	@Test
	public void solveDoesNotMutateNetworkUntilSetpointsAreApplied() throws InterpssException {
		DStabNetwork3Phase net = createTwoBusFeederWithDer();
		DStab3PGen der = net.getBus("load").getThreePhaseGenList().get(0);

		DistOpfResult solved = ThreePhaseObjectFactory.createDistOpfAlgorithm(net)
				.setControlMode(DistOpfControlMode.P)
				.setObjective(DistOpfObjective.GEN_MAX)
				.solve();

		assertEquals(DistOpfStatus.OPTIMAL, solved.getStatus());
		assertEquals(0.04, der.getPower3Phase(UnitType.PU).a_0.getReal(), 1.0e-7);

		DistOpfResult result = new DistOpfResult(DistOpfStatus.OPTIMAL, 0.0, 0.0)
				.putDerActivePower("der-1", "A", 0.02)
				.putDerActivePower("der-1", "B", 0.02)
				.putDerActivePower("der-1", "C", 0.02)
				.putDerReactivePower("der-1", "A", 0.0)
				.putDerReactivePower("der-1", "B", 0.0)
				.putDerReactivePower("der-1", "C", 0.0);
		result.applySetpointsToNetwork(net);

		assertEquals(0.02, der.getPower3Phase(UnitType.PU).a_0.getReal(), 1.0e-7);
		assertEquals(0.02, der.getPower3Phase(UnitType.PU).b_1.getReal(), 1.0e-7);
		assertEquals(0.02, der.getPower3Phase(UnitType.PU).c_2.getReal(), 1.0e-7);
	}

	@Test
	public void batteryCanDischargeToMeetSubstationPTarget() throws InterpssException {
		DistOpfOptions options = new DistOpfOptions().setTargetSubstationPPu(0.25);
		DistOpfModel model = batteryModel(options, DistOpfControlMode.P, DistOpfObjective.TARGET_SUBSTATION_P);

		DistOpfSolverResult result = new OjAlgoDistOpfSolver().solve(model, options);

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(0.05, result.getPrimalVariables()[
				model.getVariableIndex().derP("battery-1", PhaseCode.A)], 1.0e-7);
		assertEquals(0.05, result.getPrimalVariables()[
				model.getVariableIndex().branchP("source->load(0)", PhaseCode.A)], 1.0e-7);
	}

	@Test
	public void batteryCanChargeToMeetSubstationPTarget() throws InterpssException {
		DistOpfOptions options = new DistOpfOptions().setTargetSubstationPPu(0.33);
		DistOpfModel model = batteryModel(options, DistOpfControlMode.P, DistOpfObjective.TARGET_SUBSTATION_P);

		DistOpfSolverResult result = new OjAlgoDistOpfSolver().solve(model, options);

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(-0.03, result.getPrimalVariables()[
				model.getVariableIndex().derP("battery-1", PhaseCode.A)], 1.0e-7);
		assertEquals(0.13, result.getPrimalVariables()[
				model.getVariableIndex().branchP("source->load(0)", PhaseCode.A)], 1.0e-7);
	}

	@Test
	public void batteryProvidesReactiveSupportWithinInverterCapability() throws InterpssException {
		DistOpfOptions options = new DistOpfOptions().setTargetSubstationQPu(0.04);
		DistOpfModel model = batteryModel(options, DistOpfControlMode.Q, DistOpfObjective.TARGET_SUBSTATION_Q);

		DistOpfSolverResult result = new OjAlgoDistOpfSolver().solve(model, options);

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(0.02, result.getPrimalVariables()[
				model.getVariableIndex().derQ("battery-1", PhaseCode.A)], 1.0e-7);
		assertEquals(0.0, result.getPrimalVariables()[
				model.getVariableIndex().branchQ("source->load(0)", PhaseCode.A)], 1.0e-7);
	}

	@Test
	public void batteryDischargeIsLimitedByStateOfCharge() throws InterpssException {
		DistOpfOptions options = new DistOpfOptions().setTargetSubstationPPu(0.25);
		DistOpfModel model = batteryModelWithSoc(options, 0.04, 0.50, 0.25, 0.75);

		DistOpfSolverResult result = new OjAlgoDistOpfSolver().solve(model, options);

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(0.01, result.getPrimalVariables()[
				model.getVariableIndex().derP("battery-1", PhaseCode.A)], 1.0e-7);
		assertEquals(0.09, result.getPrimalVariables()[
				model.getVariableIndex().branchP("source->load(0)", PhaseCode.A)], 1.0e-7);
	}

	@Test
	public void batteryChargeIsLimitedByStateOfChargeHeadroom() throws InterpssException {
		DistOpfOptions options = new DistOpfOptions().setTargetSubstationPPu(0.33);
		DistOpfModel model = batteryModelWithSoc(options, 0.04, 0.50, 0.25, 0.75);

		DistOpfSolverResult result = new OjAlgoDistOpfSolver().solve(model, options);

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(-0.01, result.getPrimalVariables()[
				model.getVariableIndex().derP("battery-1", PhaseCode.A)], 1.0e-7);
		assertEquals(0.11, result.getPrimalVariables()[
				model.getVariableIndex().branchP("source->load(0)", PhaseCode.A)], 1.0e-7);
	}

	private static DStabNetwork3Phase createTwoBusFeederWithDer() throws InterpssException {
		return createTwoBusFeederWithDer(0.0);
	}

	private static DistOpfModel batteryModel(DistOpfOptions options, DistOpfControlMode controlMode,
			DistOpfObjective objective) throws InterpssException {
		return batteryModel(options, controlMode, objective, null);
	}

	private static DistOpfModel batteryModelWithSoc(DistOpfOptions options, double energyCapacityPuHour,
			double initialSocPu, double minSocPu, double maxSocPu) throws InterpssException {
		return batteryModel(options, DistOpfControlMode.P, DistOpfObjective.TARGET_SUBSTATION_P,
				new double[] { energyCapacityPuHour, initialSocPu, minSocPu, maxSocPu });
	}

	private static DistOpfModel batteryModel(DistOpfOptions options, DistOpfControlMode controlMode,
			DistOpfObjective objective, double[] socData) throws InterpssException {
		DistOpfModelData baseData = new DistOpfModelDataExtractor().extract(createTwoBusFeeder(0.0));
		ArrayList<DistOpfDerData> ders = new ArrayList<DistOpfDerData>(baseData.getDers());
		if (socData == null) {
			ders.add(new DistOpfBatteryData("battery-1", "load",
					EnumSet.of(PhaseCode.A),
					0.05, 0.06, Double.valueOf(0.08)));
		}
		else {
			ders.add(new DistOpfBatteryData("battery-1", "load",
					EnumSet.of(PhaseCode.A), new Complex3x1(),
					0.05, 0.06, Double.valueOf(0.08),
					socData[0], socData[1], socData[2], socData[3]));
		}
		DistOpfModelData modelData = new DistOpfModelData(baseData.getBaseMva(),
				baseData.getSwingBusId(), baseData.getBuses(), baseData.getBranches(), ders,
				childrenByBusId(baseData), parentBranchByBusId(baseData));
		return new LinDistFlowModelBuilder().build(modelData, options, controlMode, objective);
	}

	private static Map<String, List<DistOpfBranchData>> childrenByBusId(DistOpfModelData baseData) {
		Map<String, List<DistOpfBranchData>> childrenByBusId =
				new LinkedHashMap<String, List<DistOpfBranchData>>();
		childrenByBusId.put("source", baseData.getBranches());
		return childrenByBusId;
	}

	private static Map<String, DistOpfBranchData> parentBranchByBusId(DistOpfModelData baseData) {
		Map<String, DistOpfBranchData> parentBranchByBusId =
				new LinkedHashMap<String, DistOpfBranchData>();
		parentBranchByBusId.put("load", baseData.getBranches().get(0));
		return parentBranchByBusId;
	}

	private static DStabNetwork3Phase createTwoBusFeederWithDer(double ratingMva1) throws InterpssException {
		return createTwoBusFeederWithDerPower(new Complex(0.04, 0.0), ratingMva1);
	}

	private static DStabNetwork3Phase createTwoBusFeederWithDerPower(Complex derPower) throws InterpssException {
		return createTwoBusFeederWithDerPower(derPower, 0.0);
	}

	private static DStabNetwork3Phase createTwoBusFeederWithDerPower(Complex derPower, double ratingMva1)
			throws InterpssException {
		return createTwoBusFeederWithDerPower(derPower, ratingMva1, 0.0);
	}

	private static DStabNetwork3Phase createTwoBusFeederWithDerPower(Complex derPower, double ratingMva1,
			double derMvaBase)
			throws InterpssException {
		DStabNetwork3Phase net = ThreePhaseObjectFactory.create3PhaseDStabNetwork();
		net.setBaseKva(1000.0);

		DStab3PBus source = ThreePhaseObjectFactory.create3PDStabBus("source", net);
		source.setBaseVoltage(12470.0);
		source.setGenCode(AclfGenCode.SWING);
		source.setLoadCode(AclfLoadCode.NON_LOAD);
		source.setVoltage(new Complex(1.0, 0.0));

		DStab3PBus loadBus = ThreePhaseObjectFactory.create3PDStabBus("load", net);
		loadBus.setBaseVoltage(12470.0);
		loadBus.setGenCode(AclfGenCode.NON_GEN);
		loadBus.setLoadCode(AclfLoadCode.CONST_P);
		DStab3PLoad load = ThreePhaseObjectFactory.create3PLoad("load-1");
		load.set3PhaseLoad(new Complex3x1(new Complex(0.1, 0.02),
				new Complex(0.1, 0.02), new Complex(0.1, 0.02)));
		loadBus.getThreePhaseLoadList().add(load);

		DStab3PGen der = ThreePhaseObjectFactory.create3PGenerator("der-1");
		if (derMvaBase > 0.0) {
			der.setMvaBase(derMvaBase);
		}
		der.setPower3Phase(new Complex3x1(derPower, derPower, derPower), UnitType.PU);
		loadBus.getThreePhaseGenList().add(der);

		DStab3PBranch line = ThreePhaseObjectFactory.create3PBranch("source", "load", "0", net);
		line.setBranchCode(AclfBranchCode.LINE);
		line.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex(0.01, 0.04)));
		if (ratingMva1 > 0.0) {
			line.setRatingMva1(ratingMva1);
		}
		return net;
	}

	private static DStabNetwork3Phase createTwoBusFeeder(double ratingMva1) throws InterpssException {
		DStabNetwork3Phase net = ThreePhaseObjectFactory.create3PhaseDStabNetwork();
		net.setBaseKva(1000.0);

		DStab3PBus source = ThreePhaseObjectFactory.create3PDStabBus("source", net);
		source.setBaseVoltage(12470.0);
		source.setGenCode(AclfGenCode.SWING);
		source.setLoadCode(AclfLoadCode.NON_LOAD);
		source.setVoltage(new Complex(1.0, 0.0));

		DStab3PBus loadBus = ThreePhaseObjectFactory.create3PDStabBus("load", net);
		loadBus.setBaseVoltage(12470.0);
		loadBus.setGenCode(AclfGenCode.NON_GEN);
		loadBus.setLoadCode(AclfLoadCode.CONST_P);
		DStab3PLoad load = ThreePhaseObjectFactory.create3PLoad("load-1");
		load.set3PhaseLoad(new Complex3x1(new Complex(0.1, 0.02),
				new Complex(0.1, 0.02), new Complex(0.1, 0.02)));
		loadBus.getThreePhaseLoadList().add(load);

		DStab3PBranch line = ThreePhaseObjectFactory.create3PBranch("source", "load", "0", net);
		line.setBranchCode(AclfBranchCode.LINE);
		line.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex(0.01, 0.04)));
		if (ratingMva1 > 0.0) {
			line.setRatingMva1(ratingMva1);
		}
		return net;
	}
}
