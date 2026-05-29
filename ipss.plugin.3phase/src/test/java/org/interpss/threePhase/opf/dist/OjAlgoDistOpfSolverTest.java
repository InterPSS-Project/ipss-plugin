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
import org.interpss.threePhase.opf.dist.constraint.DistOpfConstraintFactory;
import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelDataExtractor;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;
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

public class OjAlgoDistOpfSolverTest {

	@Test
	public void solvesNoControlTwoBusLinDistFlow() throws InterpssException {
		DistOpfModelData data = new DistOpfModelDataExtractor().extract(createTwoBusFeeder());
		DistOpfModel model = new LinDistFlowModelBuilder().build(data, new DistOpfOptions());

		DistOpfSolverResult result = new OjAlgoDistOpfSolver().solve(model, new DistOpfOptions());

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertTrue(result.getMaxConstraintResidual() < 1.0e-7);

		DistOpfBranchData branch = data.getBranches().get(0);
		int pA = model.getVariableIndex().branchP(branch.getId(), PhaseCode.A);
		int qA = model.getVariableIndex().branchQ(branch.getId(), PhaseCode.A);
		int vLoadA = model.getVariableIndex().busV2("load", PhaseCode.A);
		assertEquals(0.1, result.getPrimalVariables()[pA], 1.0e-7);
		assertEquals(0.02, result.getPrimalVariables()[qA], 1.0e-7);
		assertEquals(0.9964, result.getPrimalVariables()[vLoadA], 1.0e-7);
	}

	@Test
	public void honorsBinaryVariableBounds() {
		DistOpfVariableIndex variableIndex = new DistOpfVariableIndex();
		int binary = variableIndex.targetPPositive("source");
		DistOpfModel model = new DistOpfModel(variableIndex);
		model.setLinearObjective(new double[] { 1.0 });
		model.setBinaryVariable(binary);
		model.addConstraint(DistOpfConstraintFactory.greaterThan(0, "BinaryLower", 0.2,
				new int[] { binary }, new double[] { 1.0 }));

		DistOpfSolverResult result = new OjAlgoDistOpfSolver().solve(model, new DistOpfOptions());

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(1.0, result.getPrimalVariables()[binary], 1.0e-7);
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
