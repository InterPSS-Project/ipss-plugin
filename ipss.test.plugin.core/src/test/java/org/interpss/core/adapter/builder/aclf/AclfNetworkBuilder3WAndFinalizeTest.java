package org.interpss.core.adapter.builder.aclf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.numeric.util.NumericUtil;
import org.junit.jupiter.api.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adj.AclfAdjustControlType;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.facts.StaticVarCompensator;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.AcscGen;
import com.interpss.core.acsc.AcscLoad;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.OriginalDataFormat;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabLoad;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;

/**
 * Unit tests for AclfNetworkBuilder 3W transformers, finalizeNetwork post-processing,
 * Acsc/DStab polymorphism, and a minimal loadflow smoke test.
 */
public class AclfNetworkBuilder3WAndFinalizeTest extends CorePluginTestSetup {

	private static final double TOL = 1.0E-6;

	@Test
	public void addXformer3W_standard() throws Exception {
		AclfNetworkBuilder builder = threeBusNet();
		Aclf3WBranch xfr3w = builder.addXformer3W(
				"Bus1", "Bus2", "Bus3", "1",
				new Complex(0.0, 0.1), new Complex(0.0, 0.2), new Complex(0.0, 0.15),
				1.05, 1.0, 0.98,
				new Complex(0.0, 0.01),
				1.0, 0.0,
				false, false, false,
				false, 0.0, 0.0, 0.0,
				true);

		assertNotNull(xfr3w);
		assertEquals(AclfBranchCode.W3_XFORMER, xfr3w.getBranchCode());
		assertTrue(xfr3w.isStatus());
		assertNotNull(xfr3w.getFromAclfBranch());
		assertNotNull(xfr3w.getToAclfBranch());
		assertNotNull(xfr3w.getTertAclfBranch());
		assertTrue(xfr3w.getFromAclfBranch().isStatus());
		assertTrue(xfr3w.getToAclfBranch().isStatus());
		assertTrue(xfr3w.getTertAclfBranch().isStatus());
		assertEquals(1.05, xfr3w.to3WXfr().getFromTurnRatio(), TOL);
		assertEquals(1.0, xfr3w.to3WXfr().getToTurnRatio(), TOL);
		assertEquals(0.98, xfr3w.to3WXfr().getTertTurnRatio(), TOL);
		assertTrue(NumericUtil.equals(
				xfr3w.getFromAclfBranch().getFromShuntY(), new Complex(0.0, 0.01), TOL));
		assertNotNull(xfr3w.getStarBus());
		assertEquals("3WXfr StarBus", xfr3w.getStarBus().getName());
	}

	@Test
	public void addXformer3W_phaseShiftingWithOfflineWinding() throws Exception {
		AclfNetworkBuilder builder = threeBusNet();
		Aclf3WBranch xfr3w = builder.addXformer3W(
				"Bus1", "Bus2", "Bus3", "1",
				new Complex(0.0, 0.1), new Complex(0.0, 0.2), new Complex(0.0, 0.15),
				1.0, 1.0, 1.0,
				null,
				1.02, 5.0,
				false, true, false,
				true, 10.0, -5.0, 2.0,
				true);

		assertEquals(AclfBranchCode.W3_PS_XFORMER, xfr3w.getBranchCode());
		assertTrue(xfr3w.getFromAclfBranch().isStatus());
		assertFalse(xfr3w.getToAclfBranch().isStatus());
		assertTrue(xfr3w.getTertAclfBranch().isStatus());
		BaseAclfBus starBus = (BaseAclfBus) xfr3w.getStarBus();
		assertEquals(1.02, starBus.getVoltageMag(), TOL);
		assertEquals(Math.toRadians(5.0), starBus.getVoltageAng(), 1.0E-5);
	}

	@Test
	public void finalizeNetwork_setsLfDataLoadedAndResolvesRemoteBuses() throws Exception {
		AclfNetworkBuilder builder = twoBusNet(OriginalDataFormat.IEEECDF);
		builder.addSVC("Bus1", "SVC1", true, 0.5, -0.3, 1.0, "Bus2", 100.0);
		builder.addSwitchedShunt("Bus2", "1", true,
				AclfAdjustControlMode.CONTINUOUS, AclfAdjustControlType.RANGE_CONTROL,
				0.0, 1.05, 0.95, "Bus1", null);
		builder.addLine("Bus1", "Bus2", "1",
				new Complex(0.01, 0.1), null, null, null, 100.0, 0.0, 0.0, true);

		builder.finalizeNetwork();

		AclfNetwork net = builder.getNetwork();
		assertTrue(net.isLfDataLoaded());

		StaticVarCompensator svc =
				(StaticVarCompensator) builder.getBus("Bus1").getStaticVarCompensatorList().get(0);
		assertSame(builder.getBus("Bus2"), svc.getRemoteBus());

		SwitchedShunt sw = (SwitchedShunt) builder.getBus("Bus2").getSwitchedShuntList().get(0);
		assertSame(builder.getBus("Bus1"), sw.getRemoteBus());
	}

	@Test
	public void finalizeNetwork_psseRenumbers3WStarBuses() throws Exception {
		AclfNetworkBuilder builder = threeBusNet();
		builder.setNetworkInfo("psse", "psse", 100000.0, OriginalDataFormat.PSSE);
		builder.addXformer3W(
				"Bus1", "Bus2", "Bus3", "1",
				new Complex(0.0, 0.1), new Complex(0.0, 0.2), new Complex(0.0, 0.15),
				1.0, 1.0, 1.0, null, 1.0, 0.0,
				false, false, false, false, 0.0, 0.0, 0.0, true);

		BaseAclfBus starBefore = (BaseAclfBus) builder.getNetwork().getBusList().stream()
				.filter(b -> "3WXfr StarBus".equals(((BaseAclfBus) b).getName()))
				.findFirst().orElse(null);
		assertNotNull(starBefore);
		long starNumBefore = starBefore.getNumber();

		builder.finalizeNetwork();

		// max bus number among Bus1/2/3 is 3 → startingNum = 10
		assertEquals(10L, starBefore.getNumber());
		assertTrue(starBefore.getNumber() != starNumBefore || starNumBefore == 10L
				|| starBefore.getNumber() >= 10L);
		assertEquals(10L, starBefore.getNumber());
	}

	@Test
	public void finalizeNetwork_nonPsseDoesNotRenumberStarBuses() throws Exception {
		AclfNetworkBuilder builder = threeBusNet();
		builder.setNetworkInfo("ieee", "ieee", 100000.0, OriginalDataFormat.IEEECDF);
		builder.addXformer3W(
				"Bus1", "Bus2", "Bus3", "1",
				new Complex(0.0, 0.1), new Complex(0.0, 0.2), new Complex(0.0, 0.15),
				1.0, 1.0, 1.0, null, 1.0, 0.0,
				false, false, false, false, 0.0, 0.0, 0.0, true);

		BaseAclfBus star = (BaseAclfBus) builder.getNetwork().getBusList().stream()
				.filter(b -> "3WXfr StarBus".equals(((BaseAclfBus) b).getName()))
				.findFirst().orElse(null);
		assertNotNull(star);
		long numBefore = star.getNumber();

		builder.finalizeNetwork();
		assertEquals(numBefore, star.getNumber());
	}

	@Test
	public void polymorphism_acscNetworkCreatesAcscObjects() throws Exception {
		AcscNetwork acsc = CoreObjectFactory.createAcscNetwork();
		AclfNetworkBuilder builder = new AclfNetworkBuilder(acsc);
		builder.addBus("Bus1", "B1", 1L, 138000.0, 1.0, 0.0, null, null, null);
		builder.addBus("Bus2", "B2", 2L, 138000.0, 1.0, 0.0, null, null, null);

		assertInstanceOf(AcscBus.class, builder.getBus("Bus1"));

		AclfBranch bra = builder.addLine("Bus1", "Bus2", "1",
				new Complex(0.01, 0.1), null, null, null, 100.0, 0.0, 0.0, true);
		assertInstanceOf(AcscBranch.class, bra);

		AclfGen gen = builder.addContributeGen("Bus1", "1", true,
				0.5, 0.1, 100.0, 1.0, 0.3, -0.2, 1.0, 0.0,
				new Complex(0.0, 0.2), null, 0.0, null, 0.0, 0.0);
		assertInstanceOf(AcscGen.class, gen);

		AclfLoad load = builder.addContributeLoad("Bus2", "1", true,
				new Complex(0.3, 0.1), null, null, null, false);
		assertInstanceOf(AcscLoad.class, load);
	}

	@Test
	public void polymorphism_dstabNetworkCreatesDStabObjects() throws Exception {
		DStabilityNetwork dstab = DStabObjectFactory.createDStabilityNetwork();
		AclfNetworkBuilder builder = new AclfNetworkBuilder(dstab);
		builder.addBus("Bus1", "B1", 1L, 138000.0, 1.0, 0.0, null, null, null);
		builder.addBus("Bus2", "B2", 2L, 138000.0, 1.0, 0.0, null, null, null);

		assertInstanceOf(DStabBus.class, builder.getBus("Bus1"));

		AclfBranch bra = builder.addLine("Bus1", "Bus2", "1",
				new Complex(0.01, 0.1), null, null, null, 100.0, 0.0, 0.0, true);
		assertInstanceOf(DStabBranch.class, bra);

		AclfGen gen = builder.addContributeGen("Bus1", "1", true,
				0.5, 0.1, 100.0, 1.0, 0.3, -0.2, 1.0, 0.0,
				new Complex(0.0, 0.2), null, 0.0, null, 0.0, 0.0);
		assertInstanceOf(DStabGen.class, gen);

		AclfLoad load = builder.addContributeLoad("Bus2", "1", true,
				new Complex(0.3, 0.1), null, null, null, false);
		assertInstanceOf(DStabLoad.class, load);
	}

	@Test
	public void loadflowSmoke_twoBusNetworkConverges() throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.setNetworkInfo("ut", "ut", 100000.0, OriginalDataFormat.IEEECDF);
		builder.addBus("Bus1", "Swing", 1L, 138000.0, 1.0, 0.0, null, null, null);
		builder.addBus("Bus2", "Load", 2L, 138000.0, 1.0, 0.0, null, null, null);
		builder.setSwingBus("Bus1", 1.0, 0.0);
		builder.addContributeLoad("Bus2", "1", true,
				new Complex(0.5, 0.2), null, null, null, false);
		builder.addLine("Bus1", "Bus2", "1",
				new Complex(0.01, 0.1),
				new Complex(0.0, 0.01), null, null, 100.0, 0.0, 0.0, true);
		builder.finalizeNetwork();

		AclfNetwork net = builder.getNetwork();
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setLfMethod(AclfMethodType.NR);
		algo.loadflow();

		assertTrue(net.isLfConverged());
		assertEquals(2, net.getNoBus());
		assertEquals(1, net.getNoBranch());
	}

	private AclfNetworkBuilder twoBusNet(OriginalDataFormat format) throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.setNetworkInfo("ut", "ut", 100000.0, format);
		builder.addBus("Bus1", "B1", 1L, 138000.0, 1.0, 0.0, null, null, null);
		builder.addBus("Bus2", "B2", 2L, 138000.0, 1.0, 0.0, null, null, null);
		return builder;
	}

	private AclfNetworkBuilder threeBusNet() throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.setNetworkInfo("ut", "ut", 100000.0, OriginalDataFormat.PSSE);
		builder.addBus("Bus1", "B1", 1L, 138000.0, 1.0, 0.0, null, null, null);
		builder.addBus("Bus2", "B2", 2L, 69000.0, 1.0, 0.0, null, null, null);
		builder.addBus("Bus3", "B3", 3L, 13800.0, 1.0, 0.0, null, null, null);
		return builder;
	}
}
