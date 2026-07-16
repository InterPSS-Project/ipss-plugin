package org.interpss.core.adapter.builder.aclf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.junit.jupiter.api.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.adpter.AclfPQGenBusAdapter;
import com.interpss.core.aclf.adpter.AclfPVGenBusAdapter;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.net.Area;
import com.interpss.core.net.OriginalDataFormat;
import com.interpss.core.net.Owner;
import com.interpss.core.net.Zone;

/**
 * Unit tests for AclfNetworkBuilder core APIs: construction, metadata,
 * area/zone/owner, buses, gen codes, contribute gen/load, and fixed shunts.
 */
public class AclfNetworkBuilderCoreTest extends CorePluginTestSetup {

	private static final double TOL = 1.0E-6;

	@Test
	public void constructorsAndAccessors() throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		AclfNetwork net = builder.getNetwork();
		assertNotNull(net);
		assertSame(net, builder.getBaseNetwork());
		assertEquals(0, net.getNoBus());
		assertEquals(0, net.getNoBranch());
		assertNull(builder.getBus("missing"));
		assertNull(builder.getBranch("missing"));

		AclfNetwork existing = CoreObjectFactory.createAclfNetwork();
		AclfNetworkBuilder wrapped = new AclfNetworkBuilder(existing);
		assertSame(existing, wrapped.getBaseNetwork());
		assertSame(existing, wrapped.getNetwork());
	}

	@Test
	public void setNetworkInfo_ieeeCdfEnablesContributeModel() {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.setNetworkInfo("ieee14", "IEEE 14 Bus", 100000.0, OriginalDataFormat.IEEECDF);

		AclfNetwork net = builder.getNetwork();
		assertEquals("ieee14", net.getId());
		assertEquals("IEEE 14 Bus", net.getName());
		assertEquals(100000.0, net.getBaseKva(), TOL);
		assertEquals(OriginalDataFormat.IEEECDF, net.getOriginalDataFormat());
		assertTrue(net.isContributeGenLoadModel());
	}

	@Test
	public void setNetworkInfo_psseDoesNotForceContributeModel() {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.setNetworkInfo("psse", "PSSE Case", 100000.0, OriginalDataFormat.PSSE);
		assertFalse(builder.getNetwork().isContributeGenLoadModel());
		assertEquals(OriginalDataFormat.PSSE, builder.getNetwork().getOriginalDataFormat());
	}

	@Test
	public void setDefaultVoltageLimitAndEnsureCapacity() {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.setDefaultVoltageLimit(1.1, 0.9);
		builder.ensureCapacity(50, 80);

		assertEquals(1.1, builder.getNetwork().getDefaultVoltageLimit().getMax(), TOL);
		assertEquals(0.9, builder.getNetwork().getDefaultVoltageLimit().getMin(), TOL);
	}

	@Test
	public void addAreaZoneOwner_createAndUpdate() {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();

		Area area = builder.addArea("1", "Area1", "Area One");
		assertEquals("1", area.getId());
		assertEquals("Area1", area.getName());
		assertEquals("Area One", area.getDesc());

		Area sameArea = builder.addArea("1", "Area1-Updated", "Updated Desc");
		assertSame(area, sameArea);
		assertEquals("Area1-Updated", sameArea.getName());
		assertEquals("Updated Desc", sameArea.getDesc());

		Zone zone = builder.addZone("2", "Zone2", "Zone Two");
		assertEquals("2", zone.getId());
		assertEquals("Zone2", zone.getName());

		Owner owner = builder.addOwner("3", "Owner3");
		assertEquals("3", owner.getId());
		assertEquals("Owner3", owner.getName());

		// null name/desc use defaults
		Area area2 = builder.addArea("10", null, null);
		assertEquals("Area", area2.getName());
		assertEquals("Area Desc", area2.getDesc());
	}

	@Test
	public void addBus_setsVoltageBaseAndAreaZoneOwner() throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.addArea("1", "A1", null);
		builder.addZone("2", "Z2", null);
		builder.addOwner("3", "O3");

		BaseAclfBus bus = builder.addBus("Bus1", "Swing Bus", 1L, 138000.0,
				1.05, Math.toRadians(10.0), "1", "2", "3");

		assertEquals("Bus1", bus.getId());
		assertEquals("Swing Bus", bus.getName());
		assertEquals(1L, bus.getNumber());
		assertEquals(138000.0, bus.getBaseVoltage(), TOL);
		assertEquals(1.05, bus.getVoltageMag(), TOL);
		assertEquals(Math.toRadians(10.0), bus.getVoltageAng(), 1.0E-5);
		assertEquals("1", bus.getArea().getId());
		assertEquals("2", bus.getZone().getId());
		assertEquals("3", bus.getOwner().getId());
		assertSame(bus, builder.getBus("Bus1"));
	}

	@Test
	public void addBus_nullAreaZoneOwnerSkipped() throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		BaseAclfBus bus = builder.addBus("Bus1", "B1", 1L, 230000.0, 1.0, 0.0, null, null, null);
		assertNull(bus.getArea());
		assertNull(bus.getZone());
		assertNull(bus.getOwner());
	}

	@Test
	public void setBusVoltageLimit() throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.addBus("Bus1", "B1", 1L, 138000.0, 1.0, 0.0, null, null, null);
		builder.setBusVoltageLimit("Bus1", 1.1, 0.95);

		BaseAclfBus bus = builder.getBus("Bus1");
		assertEquals(1.1, bus.getVLimit().getMax(), TOL);
		assertEquals(0.95, bus.getVLimit().getMin(), TOL);
	}

	@Test
	public void setSwingPvPqAndNonGenBus() throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.addBus("Bus1", "Swing", 1L, 138000.0, 1.0, 0.0, null, null, null);
		builder.addBus("Bus2", "PV", 2L, 138000.0, 1.0, 0.0, null, null, null);
		builder.addBus("Bus3", "PQ", 3L, 138000.0, 1.0, 0.0, null, null, null);
		builder.addBus("Bus4", "NonGen", 4L, 138000.0, 1.0, 0.0, null, null, null);

		builder.setSwingBus("Bus1", 1.06, Math.toRadians(5.0));
		BaseAclfBus swingBus = builder.getBus("Bus1");
		assertEquals(AclfGenCode.SWING, swingBus.getGenCode());
		AclfSwingBusAdapter swing = swingBus.toSwingBus();
		assertEquals(1.06, swing.getDesiredVoltMag(UnitType.PU), TOL);
		assertEquals(Math.toRadians(5.0), swing.getDesiredVoltAng(UnitType.Rad), 1.0E-5);

		builder.setPVBus("Bus2", 0.5, 1.02, 0.3, -0.2, true);
		BaseAclfBus pvBus = builder.getBus("Bus2");
		assertEquals(AclfGenCode.GEN_PV, pvBus.getGenCode());
		AclfPVGenBusAdapter pv = pvBus.toPVBus();
		assertEquals(0.5, pv.getGenP(UnitType.PU), TOL);
		assertEquals(1.02, pv.getDesiredVoltMag(UnitType.PU), TOL);
		assertNotNull(pvBus.getPVBusLimit());
		assertTrue(pvBus.getPVBusLimit().isStatus());
		assertEquals(0.3, pvBus.getPVBusLimit().getQLimit().getMax(), TOL);
		assertEquals(-0.2, pvBus.getPVBusLimit().getQLimit().getMin(), TOL);

		builder.setPQBus("Bus3", 0.1, 0.05, 1.05, 0.95);
		BaseAclfBus pqBus = builder.getBus("Bus3");
		assertEquals(AclfGenCode.GEN_PQ, pqBus.getGenCode());
		AclfPQGenBusAdapter pq = pqBus.toPQBus();
		assertTrue(NumericUtil.equals(pq.getGen(UnitType.PU), new Complex(0.1, 0.05), TOL));
		assertNotNull(pqBus.getPQBusLimit());
		assertEquals(1.05, pqBus.getPQBusLimit().getVLimit(UnitType.PU).getMax(), TOL);
		assertEquals(0.95, pqBus.getPQBusLimit().getVLimit(UnitType.PU).getMin(), TOL);

		builder.setNonGenBus("Bus4");
		assertEquals(AclfGenCode.NON_GEN, builder.getBus("Bus4").getGenCode());
	}

	@Test
	public void setPVBus_qLimitInactive() throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.addBus("Bus2", "PV", 2L, 138000.0, 1.0, 0.0, null, null, null);
		builder.setPVBus("Bus2", 0.4, 1.01, 0.2, -0.1, false);
		assertFalse(builder.getBus("Bus2").getPVBusLimit().isStatus());
	}

	@Test
	public void addContributeGen() throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.addBus("Bus1", "GenBus", 1L, 138000.0, 1.0, 0.0, null, null, null);

		AclfGen gen = builder.addContributeGen("Bus1", "1", true,
				0.8, 0.3, 100.0, 1.05,
				0.5, -0.2, 1.0, 0.1,
				new Complex(0.0, 0.2), new Complex(0.0, 0.1), 1.0,
				"Bus2", 0.6, 0.4);

		assertNotNull(gen);
		assertTrue(gen.isStatus());
		assertEquals(100.0, gen.getMvaBase(), TOL);
		assertTrue(NumericUtil.equals(gen.getGen(), new Complex(0.8, 0.3), TOL));
		assertEquals(1.05, gen.getDesiredVoltMag(), TOL);
		assertEquals(0.5, gen.getQGenLimit().getMax(), TOL);
		assertEquals(-0.2, gen.getQGenLimit().getMin(), TOL);
		assertEquals(1.0, gen.getPGenLimit().getMax(), TOL);
		assertEquals(0.1, gen.getPGenLimit().getMin(), TOL);
		assertTrue(NumericUtil.equals(gen.getSourceZ(), new Complex(0.0, 0.2), TOL));
		assertTrue(NumericUtil.equals(gen.getXfrZ(), new Complex(0.0, 0.1), TOL));
		assertEquals(1.0, gen.getXfrTap(), TOL);
		assertEquals("Bus2", gen.getRemoteVControlBusId());
		assertEquals(0.6, gen.getMvarControlPFactor(), TOL);
		assertEquals(0.4, gen.getMwControlPFactor(), TOL);
		assertEquals(1, builder.getBus("Bus1").getContributeGenList().size());
	}

	@Test
	public void addContributeLoad_zipAndDistGen() throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.addBus("Bus5", "LoadBus", 5L, 138000.0, 1.0, 0.0, null, null, null);

		AclfLoad load = builder.addContributeLoad("Bus5", "1", true,
				new Complex(0.5, 0.2),
				new Complex(0.1, 0.05),
				new Complex(0.05, 0.02),
				new Complex(0.02, 0.01),
				true);

		assertNotNull(load);
		assertTrue(load.isStatus());
		assertEquals(AclfLoadCode.ZIP, load.getCode());
		assertTrue(NumericUtil.equals(load.getLoadCP(), new Complex(0.5, 0.2), TOL));
		assertTrue(NumericUtil.equals(load.getLoadCI(), new Complex(0.1, 0.05), TOL));
		assertTrue(NumericUtil.equals(load.getLoadCZ(), new Complex(0.05, 0.02), TOL));
		assertTrue(NumericUtil.equals(load.getDistGenPower(), new Complex(0.02, 0.01), TOL));
		assertTrue(load.isDistGenStatus());
		assertEquals(AclfLoadCode.CONST_P, builder.getBus("Bus5").getLoadCode());
	}

	@Test
	public void addContributeLoad_constPOnly() throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.addBus("Bus5", "LoadBus", 5L, 138000.0, 1.0, 0.0, null, null, null);

		AclfLoad load = builder.addContributeLoad("Bus5", "1", true,
				new Complex(0.3, 0.1), null, null, null, false);

		assertEquals(AclfLoadCode.CONST_P, load.getCode());
		assertTrue(NumericUtil.equals(load.getLoadCP(), new Complex(0.3, 0.1), TOL));
	}

	@Test
	public void setNonLoadBus() throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.addBus("Bus5", "LoadBus", 5L, 138000.0, 1.0, 0.0, null, null, null);
		builder.addContributeLoad("Bus5", "1", true, new Complex(0.1, 0.0), null, null, null, false);
		builder.setNonLoadBus("Bus5");
		assertEquals(AclfLoadCode.NON_LOAD, builder.getBus("Bus5").getLoadCode());
	}

	@Test
	public void setAndAddToBusShuntY() throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.addBus("Bus1", "B1", 1L, 138000.0, 1.0, 0.0, null, null, null);

		builder.setBusShuntY("Bus1", new Complex(0.01, 0.05));
		assertTrue(NumericUtil.equals(builder.getBus("Bus1").getShuntY(), new Complex(0.01, 0.05), TOL));

		builder.addToBusShuntY("Bus1", new Complex(0.02, 0.03));
		assertTrue(NumericUtil.equals(builder.getBus("Bus1").getShuntY(), new Complex(0.03, 0.08), TOL));
	}

	@Test
	public void missingBusReturnsNullForContributeGenLoad() {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		assertNull(builder.addContributeGen("missing", "1", true,
				0.0, 0.0, 100.0, 1.0, 0.0, 0.0, 0.0, 0.0,
				null, null, 0.0, null, 0.0, 0.0));
		assertNull(builder.addContributeLoad("missing", "1", true,
				new Complex(0.1, 0.0), null, null, null, false));
	}
}
