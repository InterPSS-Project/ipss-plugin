package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

public class DistOpfPythonReferenceComparisonTest {

	@Test
	public void matchesPythonDistopfTwoBusReference() throws InterpssException {
		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(createTwoBusFeeder()).solve();

		// Reference generated with GRIDAPPSD/distopf 0.4.0 matrix wrapper on the same two-bus CSV case:
		// load voltage magnitude = 0.998198 pu, branch P = 0.1 pu, branch Q = 0.02 pu.
		assertEquals(0.9964, result.getBusVoltageSquared("load", "A"), 5.0e-6);
		assertEquals(0.998198, Math.sqrt(result.getBusVoltageSquared("load", "A")), 5.0e-6);
		assertEquals(0.1, result.getBranchActivePower().values().iterator().next(), 1.0e-7);
		assertEquals(0.02, result.getBranchReactivePower().values().iterator().next(), 1.0e-7);
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
