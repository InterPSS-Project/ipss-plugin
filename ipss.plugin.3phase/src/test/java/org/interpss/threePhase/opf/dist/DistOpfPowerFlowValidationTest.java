package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;

public class DistOpfPowerFlowValidationTest {

	@Test
	public void validatesSmallFeederWithFixedPointPowerFlow() throws InterpssException {
		DStabNetwork3Phase net = createTwoBusFeeder();
		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(net).solve();

		assertEquals(Boolean.TRUE, result.getPowerFlowConverged());
		assertTrue(result.getPowerFlowIterationCount() > 0);
		assertTrue(result.getMaxPowerFlowVoltageDiff() < 0.01);
		assertEquals(1.0, result.getPowerFlowBusVoltageMagnitude("source", "A"), 1.0e-12);
		assertEquals(0.100104, result.getPowerFlowBranchActivePower("source->load(0)", "A"), 1.0e-6);
		assertEquals(0.02041750301083667,
				result.getPowerFlowBranchReactivePower("source->load(0)", "A"), 1.0e-12);
		assertEquals(1.04e-4, result.getMaxPowerFlowBranchActivePowerDiff(), 1.0e-6);
		assertEquals(4.1750301083667e-4, result.getMaxPowerFlowBranchReactivePowerDiff(), 1.0e-12);
		assertEquals(0.0, result.getMaxPowerFlowVoltageViolation(), 1.0e-7);
		assertEquals(0.0, result.getMaxPowerFlowBranchLimitViolation(), 1.0e-7);
		assertEquals(1.768e-5, result.getMaxBranchFlowVoltageDropResidual(), 1.0e-9);
		assertEquals(1.04e-4, result.getMaxBranchFlowActivePowerBalanceResidual(), 1.0e-9);
		assertEquals(4.16e-4, result.getMaxBranchFlowReactivePowerBalanceResidual(), 1.0e-9);
		assertTrue(result.getDiagnostics().stream()
				.anyMatch(message -> message.startsWith("AC power-flow validation: converged=true")));
	}

	private static DStabNetwork3Phase createTwoBusFeeder() throws InterpssException {
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
		line.setRatingMva1(1.0);
		return net;
	}
}
