package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
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

	private static DistOpfBranchData branch() {
		return new DistOpfBranchData("source->load(0)", "source", "load",
				EnumSet.of(PhaseCode.A),
				Complex3x3.createUnitMatrix().multiply(new Complex(0.01, 0.04)));
	}

	private static DistOpfResult result(double fromV2, double toV2, double p, double q) {
		return new DistOpfResult(DistOpfStatus.OPTIMAL, 0.0, 0.0)
				.putBusVoltageSquared("source", "A", fromV2)
				.putBusVoltageSquared("load", "A", toV2)
				.putBranchActivePower("source->load(0)", "A", p)
				.putBranchReactivePower("source->load(0)", "A", q);
	}
}
