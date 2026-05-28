package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelDataExtractor;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;

public class DistOpfModelDataExtractorTest {

	@Test
	public void extractsRadialThreePhaseFeeder() throws InterpssException {
		DStabNetwork3Phase net = createTwoBusFeeder();

		DistOpfModelData data = new DistOpfModelDataExtractor().extract(net);

		assertEquals(1.0, data.getBaseMva(), 1.0e-12);
		assertEquals("source", data.getSwingBusId());
		assertEquals(2, data.getBuses().size());
		assertEquals(1, data.getBranches().size());
		assertEquals(1, data.getChildren("source").size());
		assertTrue(data.getBranches().get(0).getPhases().contains(PhaseCode.A));
		assertTrue(data.getBranches().get(0).getPhases().contains(PhaseCode.B));
		assertTrue(data.getBranches().get(0).getPhases().contains(PhaseCode.C));
	}

	@Test
	public void rejectsMeshedFeeder() throws InterpssException {
		DStabNetwork3Phase net = createTwoBusFeeder();
		DStab3PBus extra = ThreePhaseObjectFactory.create3PDStabBus("extra", net);
		extra.setBaseVoltage(12470.0);
		extra.setGenCode(AclfGenCode.NON_GEN);
		extra.setLoadCode(AclfLoadCode.NON_LOAD);
		addLine(net, "load", "extra");
		addLine(net, "extra", "source");

		assertThrows(IllegalArgumentException.class, () -> new DistOpfModelDataExtractor().extract(net));
	}

	@Test
	public void extractsBranchThermalRating() throws InterpssException {
		DStabNetwork3Phase net = createTwoBusFeeder();
		net.getBranch("source->load(0)").setRatingMva1(0.25);

		DistOpfModelData data = new DistOpfModelDataExtractor().extract(net);

		assertEquals(0.25, data.getBranches().get(0).getThermalLimitPu(), 1.0e-12);
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

		addLine(net, "source", "load");
		return net;
	}

	private static void addLine(DStabNetwork3Phase net, String fromBusId, String toBusId)
			throws InterpssException {
		DStab3PBranch line = ThreePhaseObjectFactory.create3PBranch(fromBusId, toBusId, "0", net);
		line.setBranchCode(AclfBranchCode.LINE);
		line.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex(0.01, 0.04)));
	}
}
