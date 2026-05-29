package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.opf.dist.constraint.DistOpfConstraintFactory;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.net.OriginalDataFormat;
import com.interpss.opf.datatype.OpfConstraintType;

public class DistOpfApiTest {

	@Test
	public void factoryCreatesDistOpfAlgorithm() {
		DStabNetwork3Phase net = ThreePhaseObjectFactory.create3PhaseDStabNetwork();
		DistOpfAlgorithm algorithm = ThreePhaseObjectFactory.createDistOpfAlgorithm(net);

		assertNotNull(algorithm);
		DistOpfResult result = algorithm.solve();
		assertEquals(DistOpfStatus.ERROR, result.getStatus());
		assertFalse(result.isSolved());
	}

	@Test
	public void algorithmSolvesAndMapsSmallFeeder() throws InterpssException {
		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(createTwoBusFeeder()).solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertTrue(result.isSolved());
		assertEquals(0.9964, result.getBusVoltageSquared("load", "A"), 1.0e-7);
		assertTrue(result.getBranchActivePower().values().stream().anyMatch(v -> Math.abs(v - 0.1) < 1.0e-7));
		assertTrue(result.getBranchReactivePower().values().stream().anyMatch(v -> Math.abs(v - 0.02) < 1.0e-7));
	}

	@Test
	public void algorithmIncludesFixedCapacitorInjectionInReactiveBalance() throws InterpssException {
		DStabNetwork3Phase net = createTwoBusFeeder();
		DStab3PLoad capacitor = ThreePhaseObjectFactory.create3PLoad("cap-1");
		capacitor.setCode(AclfLoadCode.CONST_Z);
		capacitor.set3PhaseLoad(new Complex3x1(new Complex(0.0, -0.01),
				new Complex(0.0, -0.01), new Complex(0.0, -0.01)));
		net.getBus("load").getThreePhaseLoadList().add(capacitor);

		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(net).solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(0.01002802241793, result.getBranchReactivePower("source->load(0)", "A"), 1.0e-12);
		assertEquals(0.99719775820657, result.getBusVoltageSquared("load", "A"), 1.0e-12);
	}

	@Test
	public void algorithmCanRunLossLinearizedBranchFlowIteration() throws InterpssException {
		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(createTwoBusFeeder())
				.setOptions(new DistOpfOptions().setBranchFlowLossIterations(1))
				.solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(0.100104, result.getBranchActivePower("source->load(0)", "A"), 1.0e-9);
		assertEquals(0.020416, result.getBranchReactivePower("source->load(0)", "A"), 1.0e-9);
		assertEquals(0.99638232, result.getBusVoltageSquared("load", "A"), 1.0e-9);
		assertTrue(result.getDiagnostics().stream()
				.anyMatch(message -> message.contains("Branch-flow loss iteration 1")));
	}

	@Test
	public void zeroBranchFlowLossIterationsKeepsDefaultLinDistFlow() {
		assertEquals(0, new DistOpfOptions().getBranchFlowLossIterations());
	}

	@Test
	public void algorithmSolvesNetworkImportedFromCimOrOdmMapper() throws InterpssException {
		DStabNetwork3Phase net = createTwoBusFeeder();
		net.setOriginalDataFormat(OriginalDataFormat.CIM);

		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(net).solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(0.9964, result.getBusVoltageSquared("load", "A"), 1.0e-7);
	}

	@Test
	public void ojAlgoIsDefaultSolverAndOrToolsCanBeSelectedExplicitly() throws InterpssException {
		DistOpfOptions defaultOptions = new DistOpfOptions();
		DistOpfResult defaultResult = ThreePhaseObjectFactory.createDistOpfAlgorithm(createTwoBusFeeder())
				.setOptions(defaultOptions)
				.solve();

		assertEquals(DistOpfSolverType.OJALGO, defaultOptions.getSolverType());
		assertEquals(DistOpfVoltageModel.ANGLE_COUPLED_LINDISTFLOW, defaultOptions.getVoltageModel());
		assertEquals(DistOpfStatus.OPTIMAL, defaultResult.getStatus());

		DistOpfResult orToolsResult = ThreePhaseObjectFactory.createDistOpfAlgorithm(createTwoBusFeeder())
				.setOptions(new DistOpfOptions().setSolverType(DistOpfSolverType.ORTOOLS))
				.solve();

		assertEquals(DistOpfStatus.OPTIMAL, orToolsResult.getStatus());
		assertEquals(defaultResult.getBusVoltageSquared("load", "A"),
				orToolsResult.getBusVoltageSquared("load", "A"), 1.0e-9);
	}

	@Test
	public void variableIndexIsDeterministic() {
		DistOpfVariableIndex index = new DistOpfVariableIndex();

		assertEquals(0, index.branchP("l1", PhaseCode.A));
		assertEquals(1, index.branchQ("l1", PhaseCode.A));
		assertEquals(0, index.branchP("l1", PhaseCode.A));
		assertEquals(2, index.busV2("b2", PhaseCode.B));
		assertEquals(3, index.size());
	}

	@Test
	public void sparseConstraintFactoryBuildsEqualityRows() {
		assertEquals(OpfConstraintType.EQUALITY,
				DistOpfConstraintFactory.equality(0, "balance", 1.5,
						new int[] {0, 2}, new double[] {1.0, -1.0}).getCstType());
		assertEquals(2, DistOpfConstraintFactory.equality(0, "balance", 1.5,
				new int[] {0, 2}, new double[] {1.0, -1.0}).getColNo().size());
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
		return net;
	}
}
