package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;

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
	}

	@Test
	public void thermalLimitReportsInfeasibleWhenNoControlCanRelieveBranchFlow() throws InterpssException {
		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(createTwoBusFeeder(0.09)).solve();

		assertEquals(DistOpfStatus.INFEASIBLE, result.getStatus());
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

	private static DStabNetwork3Phase createTwoBusFeederWithDer() throws InterpssException {
		return createTwoBusFeederWithDer(0.0);
	}

	private static DStabNetwork3Phase createTwoBusFeederWithDer(double ratingMva1) throws InterpssException {
		return createTwoBusFeederWithDerPower(new Complex(0.04, 0.0), ratingMva1);
	}

	private static DStabNetwork3Phase createTwoBusFeederWithDerPower(Complex derPower) throws InterpssException {
		return createTwoBusFeederWithDerPower(derPower, 0.0);
	}

	private static DStabNetwork3Phase createTwoBusFeederWithDerPower(Complex derPower, double ratingMva1)
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
