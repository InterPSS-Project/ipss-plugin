package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.threePhase.opf.dist.constraint.DistBranchThermalLimitConstraintCollector;
import org.interpss.threePhase.opf.dist.constraint.DistDerLimitConstraintCollector;
import org.interpss.threePhase.opf.dist.constraint.DistInverterCapabilityConstraintCollector;
import org.interpss.threePhase.opf.dist.constraint.DistPowerBalanceConstraintCollector;
import org.interpss.threePhase.opf.dist.constraint.DistReactivePowerBalanceConstraintCollector;
import org.interpss.threePhase.opf.dist.constraint.DistSubstationTargetConstraintCollector;
import org.interpss.threePhase.opf.dist.constraint.DistSwingVoltageConstraintCollector;
import org.interpss.threePhase.opf.dist.constraint.DistVoltageDropConstraintCollector;
import org.interpss.threePhase.opf.dist.constraint.DistVoltageLimitConstraintCollector;
import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
import org.interpss.threePhase.opf.dist.model.DistOpfBusData;
import org.interpss.threePhase.opf.dist.model.DistOpfDerData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfConstraintCollectorTest {

	@Test
	public void balanceCollectorsBuildExpectedSparseRows() {
		DistOpfModelData data = modelData();
		DistOpfVariableIndex index = variableIndex();
		List<OpfConstraint> constraints = new ArrayList<OpfConstraint>();

		new DistPowerBalanceConstraintCollector(data, index, constraints).collectConstraint();
		new DistReactivePowerBalanceConstraintCollector(data, index, constraints, new DistOpfOptions())
				.collectConstraint();

		OpfConstraint p = constraint(constraints, "PBalance@load.A");
		assertEquals(0.10, p.getUpperLimit(), 1.0e-12);
		assertCoefficient(p, index.branchP("source->load(0)", PhaseCode.A), 1.0);
		assertCoefficient(p, index.branchP("load->tail(0)", PhaseCode.A), -1.0);
		assertCoefficient(p, index.derP("der-1", PhaseCode.A), 1.0);

		OpfConstraint q = constraint(constraints, "QBalance@load.A");
		assertEquals(0.01, q.getUpperLimit(), 1.0e-12);
		assertCoefficient(q, index.branchQ("source->load(0)", PhaseCode.A), 1.0);
		assertCoefficient(q, index.branchQ("load->tail(0)", PhaseCode.A), -1.0);
		assertCoefficient(q, index.derQ("der-1", PhaseCode.A), 1.0);
	}

	@Test
	public void voltageCollectorsBuildExpectedSparseRows() {
		DistOpfModelData data = modelData();
		DistOpfVariableIndex index = variableIndex();
		List<OpfConstraint> constraints = new ArrayList<OpfConstraint>();

		new DistVoltageDropConstraintCollector(data, index, constraints).collectConstraint();
		new DistSwingVoltageConstraintCollector(data, index, constraints).collectConstraint();
		new DistVoltageLimitConstraintCollector(data, index, constraints,
				new DistOpfOptions().setMinVoltagePu(0.96).setMaxVoltagePu(1.04)).collectConstraint();

		OpfConstraint drop = constraint(constraints, "VDrop@source->load(0).A");
		assertCoefficient(drop, index.busV2("source", PhaseCode.A), 1.0404);
		assertCoefficient(drop, index.busV2("load", PhaseCode.A), -1.0);
		assertCoefficient(drop, index.branchP("source->load(0)", PhaseCode.A), -0.02);
		assertCoefficient(drop, index.branchQ("source->load(0)", PhaseCode.A), -0.08);

		OpfConstraint swing = constraint(constraints, "SwingV2@source.A");
		assertEquals(1.0, swing.getUpperLimit(), 1.0e-12);
		assertCoefficient(swing, index.busV2("source", PhaseCode.A), 1.0);

		OpfConstraint limit = constraint(constraints, "VLimit@load.A");
		assertEquals(0.96 * 0.96, limit.getLowerLimit(), 1.0e-12);
		assertEquals(1.04 * 1.04, limit.getUpperLimit(), 1.0e-12);
		assertCoefficient(limit, index.busV2("load", PhaseCode.A), 1.0);
	}

	@Test
	public void controlLimitAndTargetCollectorsBuildExpectedSparseRows() {
		DistOpfModelData data = modelData();
		DistOpfVariableIndex index = variableIndex();
		List<OpfConstraint> constraints = new ArrayList<OpfConstraint>();

		new DistDerLimitConstraintCollector(data, index, constraints, DistOpfControlMode.P).collectConstraint();
		new DistInverterCapabilityConstraintCollector(data, index, constraints).collectConstraint();
		new DistBranchThermalLimitConstraintCollector(data, index, constraints, new DistOpfOptions()).collectConstraint();
		new DistSubstationTargetConstraintCollector(data, index, constraints,
				new DistOpfOptions().setTargetSubstationPPu(0.08), DistOpfObjective.TARGET_SUBSTATION_P)
						.collectConstraint();

		OpfConstraint derP = constraint(constraints, "DERP@der-1.A");
		assertEquals(0.0, derP.getLowerLimit(), 1.0e-12);
		assertEquals(0.04, derP.getUpperLimit(), 1.0e-12);
		assertCoefficient(derP, index.derP("der-1", PhaseCode.A), 1.0);

		OpfConstraint curtailment = constraint(constraints, "CurtailmentDef@der-1.A");
		assertEquals(0.04, curtailment.getUpperLimit(), 1.0e-12);
		assertCoefficient(curtailment, index.derP("der-1", PhaseCode.A), 1.0);
		assertCoefficient(curtailment, index.curtailment("der-1", PhaseCode.A), 1.0);

		OpfConstraint inverter = constraint(constraints, "InverterCapability@der-1.A.PplusQ");
		assertEquals(Math.sqrt(2.0) * 0.05, inverter.getUpperLimit(), 1.0e-12);
		assertCoefficient(inverter, index.derP("der-1", PhaseCode.A), 1.0);
		assertCoefficient(inverter, index.derQ("der-1", PhaseCode.A), 1.0);

		OpfConstraint thermal = constraint(constraints, "Thermal@source->load(0).A.P");
		assertEquals(0.20, thermal.getUpperLimit(), 1.0e-12);
		assertCoefficient(thermal, index.branchP("source->load(0)", PhaseCode.A), 1.0);

		OpfConstraint target = constraint(constraints, "TargetSubstationP@source");
		assertEquals(0.08, target.getUpperLimit(), 1.0e-12);
		assertCoefficient(target, index.branchP("source->load(0)", PhaseCode.A), 1.0);
		assertCoefficient(target, index.targetPPositive("source"), -1.0);
		assertCoefficient(target, index.targetPNegative("source"), 1.0);
	}

	private static DistOpfModelData modelData() {
		DistOpfBusData source = new DistOpfBusData("source", true, 12470.0,
				EnumSet.of(PhaseCode.A), new Complex3x1());
		DistOpfBusData load = new DistOpfBusData("load", false, 12470.0,
				EnumSet.of(PhaseCode.A), new Complex3x1(new Complex(0.10, 0.02), Complex.ZERO, Complex.ZERO),
				new Complex3x1(new Complex(0.0, 0.01), Complex.ZERO, Complex.ZERO));
		DistOpfBusData tail = new DistOpfBusData("tail", false, 12470.0,
				EnumSet.of(PhaseCode.A), new Complex3x1());

		Complex3x3 zabc = new Complex3x3();
		zabc.aa = new Complex(0.01, 0.04);
		DistOpfBranchData parent = new DistOpfBranchData("source->load(0)", "source", "load",
				EnumSet.of(PhaseCode.A), zabc, Double.valueOf(0.20), 1.02);
		DistOpfBranchData child = new DistOpfBranchData("load->tail(0)", "load", "tail",
				EnumSet.of(PhaseCode.A), zabc);
		DistOpfDerData der = new DistOpfDerData("der-1", "load", EnumSet.of(PhaseCode.A),
				new Complex3x1(new Complex(0.04, 0.01), Complex.ZERO, Complex.ZERO), Double.valueOf(0.05));

		Map<String, List<DistOpfBranchData>> children = new java.util.LinkedHashMap<String, List<DistOpfBranchData>>();
		children.put("source", Collections.singletonList(parent));
		children.put("load", Collections.singletonList(child));
		Map<String, DistOpfBranchData> parents = new java.util.LinkedHashMap<String, DistOpfBranchData>();
		parents.put("load", parent);
		parents.put("tail", child);
		return new DistOpfModelData(1.0, "source", java.util.Arrays.asList(source, load, tail),
				java.util.Arrays.asList(parent, child), Collections.singletonList(der), children, parents);
	}

	private static DistOpfVariableIndex variableIndex() {
		DistOpfVariableIndex index = new DistOpfVariableIndex();
		for (String branchId : java.util.Arrays.asList("source->load(0)", "load->tail(0)")) {
			index.branchP(branchId, PhaseCode.A);
			index.branchQ(branchId, PhaseCode.A);
		}
		for (String busId : java.util.Arrays.asList("source", "load", "tail")) {
			index.busV2(busId, PhaseCode.A);
		}
		index.derP("der-1", PhaseCode.A);
		index.derQ("der-1", PhaseCode.A);
		index.curtailment("der-1", PhaseCode.A);
		index.targetPPositive("source");
		index.targetPNegative("source");
		return index;
	}

	private static OpfConstraint constraint(List<OpfConstraint> constraints, String description) {
		for (OpfConstraint constraint : constraints) {
			if (description.equals(constraint.getDesc())) {
				return constraint;
			}
		}
		throw new AssertionError("Missing constraint " + description);
	}

	private static void assertCoefficient(OpfConstraint constraint, int column, double expected) {
		for (int i = 0; i < constraint.getColNo().size(); i++) {
			if (constraint.getColNo().get(i) == column) {
				assertEquals(expected, constraint.getVal().get(i), 1.0e-12);
				return;
			}
		}
		assertTrue(false, "Missing column " + column + " in " + constraint.getDesc());
	}
}
