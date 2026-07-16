package org.interpss.core.adapter.builder.aclf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.hvdc.ConverterType;
import com.interpss.core.aclf.hvdc.HvdcControlMode;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcLine2TVSC;
import com.interpss.core.aclf.hvdc.HvdcOperationMode;
import com.interpss.core.aclf.hvdc.ThyConverter;
import com.interpss.core.aclf.hvdc.VSCAcControlMode;
import com.interpss.core.aclf.hvdc.VSCConverter;
import com.interpss.core.net.OriginalDataFormat;

/**
 * Unit tests for AclfNetworkBuilder LCC and VSC HVDC APIs.
 */
public class AclfNetworkBuilderHvdcTest extends CorePluginTestSetup {

	private static final double TOL = 1.0E-6;

	private AclfNetworkBuilder twoBusNet() throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.setNetworkInfo("hvdc", "hvdc", 100000.0, OriginalDataFormat.PSSE);
		builder.addBus("Bus1", "Rec", 1L, 230000.0, 1.0, 0.0, null, null, null);
		builder.addBus("Bus2", "Inv", 2L, 230000.0, 1.0, 0.0, null, null, null);
		return builder;
	}

	@Test
	public void addHvdcLine2TLCC_andConverters() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();

		HvdcLine2TLCC<AclfBus> lcc = builder.addHvdcLine2TLCC(
				"HVDC1", "LCC Line", "Bus1", "Bus2",
				true, false,
				HvdcControlMode.DC_POWER, HvdcOperationMode.REC1_INV1,
				10.0, 200.0, 0.0,
				true, 500.0, 0.0, 0.1,
				ConverterType.RECTIFIER);

		assertNotNull(lcc);
		assertEquals("Bus1->Bus2(HVDC1)", lcc.getId());
		assertEquals("LCC Line", lcc.getName());
		assertTrue(lcc.isStatus());
		assertEquals(HvdcControlMode.DC_POWER, lcc.getDcLineControlMode());
		assertEquals(HvdcOperationMode.REC1_INV1, lcc.getOperationMode());
		assertEquals(10.0, lcc.getRdc(UnitType.Ohm), TOL);
		assertEquals(200.0, lcc.getPowerDemand(UnitType.mW), TOL);
		assertEquals(500.0, lcc.getScheduledDCVoltage(UnitType.kV), TOL);
		assertEquals(ConverterType.RECTIFIER, lcc.getMeterEnd());
		assertEquals(0.1, lcc.getPowerCurrentMargin(), TOL);

		ThyConverter<AclfBus> rec = builder.setLCCRectifier(lcc,
				2, 5.0, 30.0,
				0.5, 5.0,
				230.0, 1.0, 1.0,
				1.1, 0.9, 0.0125,
				0.0, 15.0);
		assertNotNull(rec);
		assertEquals(ConverterType.RECTIFIER, rec.getConverterType());
		assertEquals(2, rec.getNBridges());
		assertEquals(30.0, rec.getFiringAngLimit(UnitType.Deg).getMax(), TOL);
		assertEquals(5.0, rec.getFiringAngLimit(UnitType.Deg).getMin(), TOL);
		assertTrue(NumericUtil.equals(rec.getCommutingZ(), new Complex(0.5, 5.0), TOL));
		assertEquals(230.0, rec.getAcRatedVoltage(), TOL);
		assertEquals(1.0, rec.getXformerRatio(), TOL);
		assertEquals(1.0, rec.getXformerTapSetting(), TOL);
		assertEquals(1.1, rec.getXformerTapLimit().getMax(), TOL);
		assertEquals(0.9, rec.getXformerTapLimit().getMin(), TOL);
		assertEquals(0.0125, rec.getXformerTapStepSize(), TOL);
		assertEquals(15.0, rec.getFiringAng(), TOL);
		assertSame(rec, lcc.getRectifier());

		ThyConverter<AclfBus> inv = builder.setLCCInverter(lcc,
				2, 15.0, 25.0,
				0.5, 5.0,
				230.0, 1.0, 1.0,
				1.1, 0.9, 0.0125,
				0.0, 20.0);
		assertEquals(ConverterType.INVERTER, inv.getConverterType());
		assertEquals(2, inv.getNBridges());
		assertEquals(20.0, inv.getFiringAng(), TOL);
		assertSame(inv, lcc.getInverter());
	}

	@Test
	public void addHvdcLine2TLCC_currentControlMode() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		HvdcLine2TLCC<AclfBus> lcc = builder.addHvdcLine2TLCC(
				"HVDC2", null, "Bus1", "Bus2",
				true, true,
				HvdcControlMode.DC_CURRENT, HvdcOperationMode.REC1_INV1,
				8.0, 0.0, 800.0,
				true, 400.0, 1.0, 0.05,
				ConverterType.INVERTER);

		assertEquals(HvdcControlMode.DC_CURRENT, lcc.getDcLineControlMode());
		assertEquals(800.0, lcc.getCurrentDemand(), TOL);
		assertEquals("", lcc.getName());
		assertTrue(lcc.isPuBasedPowerFlowAlgo());
	}

	@Test
	public void addHvdcLine2TVSC_andConverters() throws Exception {
		AclfNetworkBuilder builder = twoBusNet();
		builder.addBus("Bus3", "Remote", 3L, 230000.0, 1.0, 0.0, null, null, null);

		HvdcLine2TVSC<AclfBus> vsc = builder.addHvdcLine2TVSC(
				"VSC1", "VSC Line", "Bus1", "Bus2",
				true, 5.0, 300.0);

		assertNotNull(vsc);
		assertEquals("Bus1->Bus2(VSC1)", vsc.getId());
		// addHvdcLine2T overwrites the name with a generated label
		assertTrue(vsc.getName().contains("Bus1->Bus2(VSC1)"));
		assertTrue(vsc.isStatus());
		assertEquals(5.0, vsc.getRdc(UnitType.Ohm), TOL);
		assertEquals(300.0, vsc.getMvaRating(), TOL);

		// Builder setName is applied before addHvdcLine2T; re-apply to verify setter path
		vsc.setName("VSC Line");
		assertEquals("VSC Line", vsc.getName());

		VSCConverter rec = (VSCConverter) vsc.getRecConverter();
		builder.setVSCConverter(rec, "Bus1",
				HvdcControlMode.DC_VOLTAGE, 1.0,
				VSCAcControlMode.AC_VOLTAGE, 1.02,
				300.0, 100.0, -100.0,
				"Bus3", 75.0);

		assertSame(builder.getBus("Bus1"), rec.getBus());
		assertEquals(HvdcControlMode.DC_VOLTAGE, rec.getDcControlMode());
		assertEquals(1.0, rec.getDcSetPoint(), TOL);
		assertEquals(VSCAcControlMode.AC_VOLTAGE, rec.getAcControlMode());
		assertEquals(1.02, rec.getAcSetPoint(), TOL);
		assertEquals(300.0, rec.getMvaRating(), TOL);
		assertEquals(100.0, rec.getQMvarLimit().getMax(), TOL);
		assertEquals(-100.0, rec.getQMvarLimit().getMin(), TOL);
		assertEquals("Bus3", rec.getRemoteControlBusId());
		assertEquals(75.0, rec.getRemoteControlPercent(), TOL);

		VSCConverter inv = (VSCConverter) vsc.getInvConverter();
		builder.setVSCConverter(inv, "Bus2",
				HvdcControlMode.DC_POWER, 0.5,
				VSCAcControlMode.AC_REACTIVE_POWER, -0.1,
				300.0, 80.0, -80.0,
				null, 0.0);

		assertEquals(HvdcControlMode.DC_POWER, inv.getDcControlMode());
		assertEquals(0.5, inv.getDcSetPoint(), TOL);
		assertEquals(VSCAcControlMode.AC_REACTIVE_POWER, inv.getAcControlMode());
		assertEquals(-0.1, inv.getAcSetPoint(), TOL);
	}
}
