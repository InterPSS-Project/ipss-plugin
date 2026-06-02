package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
import org.interpss.threePhase.opf.dist.model.DistOpfBusData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.validation.DistBranchFlowEquationValidation;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.PhaseCode;

public class DistBranchFlowEquationValidationTest {

	@Test
	public void exactBranchFlowVoltageDropHasZeroResidual() {
		DistOpfBranchData branch = branch();
		double p = 0.1;
		double q = 0.02;
		double fromV2 = 1.0;
		double r = 0.01;
		double x = 0.04;
		double toV2 = fromV2 - 2.0 * (r * p + x * q)
				+ (r * r + x * x) * (p * p + q * q) / fromV2;
		DistOpfResult result = result(fromV2, toV2, p, q);

		double residual = new DistBranchFlowEquationValidation()
				.voltageDropResidual(branch, PhaseCode.A, result);

		assertEquals(0.0, residual, 1.0e-12);
	}

	@Test
	public void lindistflowVoltageDropLeavesQuadraticLossResidual() {
		DistOpfBranchData branch = branch();
		double p = 0.1;
		double q = 0.02;
		double fromV2 = 1.0;
		double r = 0.01;
		double x = 0.04;
		double toV2 = fromV2 - 2.0 * (r * p + x * q);
		DistOpfResult result = result(fromV2, toV2, p, q);

		double residual = new DistBranchFlowEquationValidation()
				.voltageDropResidual(branch, PhaseCode.A, result);

		assertEquals((r * r + x * x) * (p * p + q * q), residual, 1.0e-12);
	}

	@Test
	public void exactBranchFlowPowerBalanceHasZeroResidual() {
		DistOpfBranchData branch = branch();
		double parentP = 0.1;
		double parentQ = 0.02;
		double r = 0.01;
		double x = 0.04;
		double l = parentP * parentP + parentQ * parentQ;
		DistOpfModelData modelData = modelData(branch, parentP - r * l, parentQ - x * l);
		DistOpfResult result = result(1.0, 1.0, parentP, parentQ);
		DistBranchFlowEquationValidation validation = new DistBranchFlowEquationValidation();

		assertEquals(0.0, validation.maxActivePowerBalanceResidual(modelData, result), 1.0e-12);
		assertEquals(0.0, validation.maxReactivePowerBalanceResidual(modelData, result), 1.0e-12);
	}

	@Test
	public void lindistflowPowerBalanceLeavesLossResidual() {
		DistOpfBranchData branch = branch();
		double loadP = 0.1;
		double loadQ = 0.02;
		DistOpfModelData modelData = modelData(branch, loadP, loadQ);
		double r = 0.01;
		double x = 0.04;
		double l = loadP * loadP + loadQ * loadQ;
		DistOpfResult result = result(1.0, 1.0, loadP, loadQ);
		DistBranchFlowEquationValidation validation = new DistBranchFlowEquationValidation();

		assertEquals(r * l, validation.maxActivePowerBalanceResidual(modelData, result), 1.0e-12);
		assertEquals(x * l, validation.maxReactivePowerBalanceResidual(modelData, result), 1.0e-12);
	}

	private static DistOpfBranchData branch() {
		return new DistOpfBranchData("source->load(0)", "source", "load",
				EnumSet.of(PhaseCode.A),
				Complex3x3.createUnitMatrix().multiply(new Complex(0.01, 0.04)));
	}

	private static DistOpfModelData modelData(DistOpfBranchData branch, double loadP, double loadQ) {
		DistOpfBusData source = new DistOpfBusData("source", true, 12470.0,
				EnumSet.of(PhaseCode.A), new Complex3x1());
		DistOpfBusData load = new DistOpfBusData("load", false, 12470.0,
				EnumSet.of(PhaseCode.A), new Complex3x1(new Complex(loadP, loadQ),
						Complex.ZERO, Complex.ZERO));
		Map<String, List<DistOpfBranchData>> children = new LinkedHashMap<String, List<DistOpfBranchData>>();
		children.put("source", Collections.singletonList(branch));
		children.put("load", Collections.<DistOpfBranchData>emptyList());
		Map<String, DistOpfBranchData> parents = new LinkedHashMap<String, DistOpfBranchData>();
		parents.put("load", branch);
		return new DistOpfModelData(1.0, "source",
				Arrays.asList(source, load), Collections.singletonList(branch), children, parents);
	}

	private static DistOpfResult result(double fromV2, double toV2, double p, double q) {
		return new DistOpfResult(DistOpfStatus.OPTIMAL, 0.0, 0.0)
				.putBusVoltageSquared("source", "A", fromV2)
				.putBusVoltageSquared("load", "A", toV2)
				.putBranchActivePower("source->load(0)", "A", p)
				.putBranchReactivePower("source->load(0)", "A", q);
	}
}
