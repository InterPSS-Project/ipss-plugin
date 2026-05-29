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
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelDataExtractor;
import org.interpss.threePhase.opf.dist.model.DistBranchFlowLossProfile;
import org.interpss.threePhase.opf.dist.model.LinDistFlowModelBuilder;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;

public class LinDistFlowModelBuilderTest {

	@Test
	public void buildsNoControlTwoBusModel() throws InterpssException {
		DistOpfModelData data = new DistOpfModelDataExtractor().extract(createTwoBusFeeder());

		DistOpfModel model = new LinDistFlowModelBuilder().build(data, new DistOpfOptions());

		assertEquals(12, model.getNumberOfVariables());
		assertEquals(18, model.getConstraints().size());
		assertTrue(model.getConstraints().stream().anyMatch(c -> c.getDesc().startsWith("PBalance@load.A")));
		assertTrue(model.getConstraints().stream().anyMatch(c -> c.getDesc().startsWith("VDrop@")));
		assertTrue(model.getConstraints().stream().anyMatch(c -> c.getDesc().startsWith("SwingV2@source.A")));
	}

	@Test
	public void voltageDropIncludesMutualPhaseCoupling() throws InterpssException {
		DistOpfModelData data = new DistOpfModelDataExtractor().extract(createTwoBusFeeder());
		DistOpfModel model = new LinDistFlowModelBuilder().build(data, new DistOpfOptions());
		String branchId = data.getBranches().get(0).getId();
		int branchPb = model.getVariableIndex().branchP(branchId, PhaseCode.B);

		assertTrue(model.getConstraints().stream()
				.filter(c -> c.getDesc().startsWith("VDrop@") && c.getDesc().endsWith(".A"))
				.anyMatch(c -> containsCoefficient(c, branchPb, -0.004)));
	}

	@Test
	public void voltageDropIncludesFixedVoltageRatio() throws InterpssException {
		DStabNetwork3Phase net = createTwoBusFeeder();
		net.getBranch("source->load(0)").setToTurnRatio(1.02);
		DistOpfModelData data = new DistOpfModelDataExtractor().extract(net);
		DistOpfModel model = new LinDistFlowModelBuilder().build(data, new DistOpfOptions());
		int sourceVa = model.getVariableIndex().busV2("source", PhaseCode.A);

		assertTrue(model.getConstraints().stream()
				.filter(c -> c.getDesc().startsWith("VDrop@") && c.getDesc().endsWith(".A"))
				.anyMatch(c -> containsCoefficient(c, sourceVa, 1.0404)));
	}

	@Test
	public void lossProfileAddsFixedBranchFlowLossOffsets() throws InterpssException {
		DistOpfModelData data = new DistOpfModelDataExtractor().extract(createTwoBusFeeder());
		String branchId = data.getBranches().get(0).getId();
		DistBranchFlowLossProfile lossProfile = DistBranchFlowLossProfile.none()
				.putCurrentSquared(branchId, PhaseCode.A, 0.0104);

		DistOpfModel model = new LinDistFlowModelBuilder().build(data, new DistOpfOptions(),
				DistOpfControlMode.NONE, DistOpfObjective.CURTAILMENT_MIN, lossProfile);

		assertEquals(0.100104, equalityRhs(model, "PBalance@load.A"), 1.0e-12);
		assertEquals(0.020416, equalityRhs(model, "QBalance@load.A"), 1.0e-12);
		assertEquals(-1.768e-5, equalityRhs(model, "VDrop@" + branchId + ".A"), 1.0e-12);
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
		line.setZabc(new Complex3x3(new Complex[][] {
				{ new Complex(0.01, 0.04), new Complex(0.002, 0.003), new Complex(0.001, 0.002) },
				{ new Complex(0.002, 0.003), new Complex(0.01, 0.04), new Complex(0.002, 0.003) },
				{ new Complex(0.001, 0.002), new Complex(0.002, 0.003), new Complex(0.01, 0.04) } }));
		return net;
	}

	private static boolean containsCoefficient(org.interpss.plugin.opf.constraint.OpfConstraint constraint,
			int column, double value) {
		for (int i = 0; i < constraint.getColNo().size(); i++) {
			if (constraint.getColNo().get(i) == column
					&& Math.abs(constraint.getVal().get(i) - value) < 1.0e-12) {
				return true;
			}
		}
		return false;
	}

	private static double equalityRhs(DistOpfModel model, String description) {
		return model.getConstraints().stream()
				.filter(c -> c.getDesc().equals(description))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing constraint " + description))
				.getUpperLimit();
	}
}
