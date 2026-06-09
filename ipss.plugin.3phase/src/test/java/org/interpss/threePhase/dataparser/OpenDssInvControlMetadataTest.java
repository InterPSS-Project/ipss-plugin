package org.interpss.threePhase.dataparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.powerflow.control.InverterControlData;
import org.interpss.threePhase.powerflow.control.InverterControlData.ControlMode;
import org.interpss.threePhase.powerflow.control.InverterControlModel.InverterControlResult;
import org.interpss.threePhase.qsts.InverterGenAdapter;
import org.interpss.threePhase.qsts.QstsControlMode;
import org.interpss.threePhase.qsts.QstsMode;
import org.interpss.threePhase.qsts.QstsStepContext;
import org.junit.jupiter.api.Test;

import com.interpss.core.threephase.AclfGen3Phase;

public class OpenDssInvControlMetadataTest {
	@Test
	void parsesInvControlIntoGenericInverterControlData() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();

		assertTrue(parser.getInvControlParser().parseInvControlData(
				"New InvControl.inv1 DERList=(PVSystem.pv1) mode=VOLTVAR vvc_curve1=vv1 "
						+ "kVA=600 kvarMax=300 kvarMin=-250 pfMin=0.9"));

		assertEquals(1, parser.getInverterControls().size());
		InverterControlData control = parser.getInverterControls().get(0);
		assertEquals("inv1", control.getId());
		assertEquals("pv1", control.getGeneratorId());
		assertEquals(ControlMode.VOLTVAR, control.getControlMode());
		assertEquals("vv1", control.getCurveId());
		assertEquals(600.0, control.getRatedKva(), 1.0e-12);
		assertEquals(-250.0, control.getMinReactivePowerKvar(), 1.0e-12);
		assertEquals(300.0, control.getMaxReactivePowerKvar(), 1.0e-12);
		assertEquals(0.9, control.getMinPowerFactor(), 1.0e-12);
	}

	@Test
	void parsesVoltWattCurveReference() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();

		assertTrue(parser.getInvControlParser().parseInvControlData(
				"New InvControl.inv2 DERList=[Storage.batt1] mode=VOLTWATT voltwatt_curve=vw1"));

		InverterControlData control = parser.getInverterControls().get(0);
		assertEquals("batt1", control.getGeneratorId());
		assertEquals(ControlMode.VOLTWATT, control.getControlMode());
		assertEquals("vw1", control.getCurveId());
	}

	@Test
	void parsesXYCurveAndAppliesItToExistingPvSystemAdapter() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.getPVSystemParser().parsePVSystemData(
				"New PVSystem.pv1 bus1=bus1.1.2.3 phases=3 kva=600 pmpp=500 irradiance=1 kvar=0 "
						+ "kvarMax=200 kvarMaxAbs=200",
				"Master.dss", 10);

		assertTrue(parser.getXYCurveParser().parseXYCurve(
				"New XYCurve.vv1 npts=3 xarray=(0.95 1.00 1.05) yarray=(0.5 0 -0.5)"));
		assertTrue(parser.getInvControlParser().parseInvControlData(
				"New InvControl.inv1 DERList=(PVSystem.pv1) mode=VOLTVAR vvc_curve1=vv1"));

		InverterControlData control = parser.getInverterControls().get(0);
		InverterGenAdapter adapter = parser.getTimeSeriesData().getInverterAdapterStore().get("pv1");
		adapter.setTerminalVoltagePu(0.975);
		InverterControlResult result = adapter.apply(control,
				new QstsStepContext(0, 0, 0.0, QstsMode.DAILY, 1.0, 1.0, QstsControlMode.STATIC),
				parser.getStaticNetwork().getBaseKva());

		assertTrue(result.isApplied());
		assertEquals(50.0, result.getReactivePowerKvar(), 1.0e-12);
		AclfGen3Phase generator = parser.getStaticNetwork().getBus("bus1").getPhaseGenList().get(0);
		assertEquals(50.0 / parser.getStaticNetwork().getBaseKva(),
				generator.getPower3Phase(UnitType.PU).a_0
						.add(generator.getPower3Phase(UnitType.PU).b_1)
						.add(generator.getPower3Phase(UnitType.PU).c_2).getImaginary(),
				1.0e-12);
	}

	@Test
	void curveParsedBeforePvSystemIsAppliedWhenAdapterRegistersLater() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();
		assertTrue(parser.getXYCurveParser().parseXYCurve(
				"New XYCurve.vw1 npts=2 xarray=(0.98 1.08) yarray=(1.0 0.4)"));
		parser.getPVSystemParser().parsePVSystemData(
				"New PVSystem.pv1 bus1=bus1.1.2.3 phases=3 kva=600 pmpp=500 irradiance=1 kvar=0",
				"Master.dss", 11);
		assertTrue(parser.getInvControlParser().parseInvControlData(
				"New InvControl.inv1 DERList=(PVSystem.pv1) mode=VOLTWATT voltwatt_curve=vw1"));

		InverterGenAdapter adapter = parser.getTimeSeriesData().getInverterAdapterStore().get("pv1");
		adapter.setTerminalVoltagePu(1.08);
		InverterControlResult result = adapter.apply(parser.getInverterControls().get(0),
				new QstsStepContext(0, 0, 0.0, QstsMode.DAILY, 1.0, 1.0, QstsControlMode.STATIC),
				parser.getStaticNetwork().getBaseKva());

		assertTrue(result.isApplied());
		assertEquals(200.0, result.getActivePowerKw(), 1.0e-12);
	}
}
