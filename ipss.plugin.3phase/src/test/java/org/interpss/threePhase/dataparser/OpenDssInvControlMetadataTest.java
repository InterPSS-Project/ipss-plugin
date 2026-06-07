package org.interpss.threePhase.dataparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.powerflow.control.InverterControlData;
import org.interpss.threePhase.powerflow.control.InverterControlData.ControlMode;
import org.junit.jupiter.api.Test;

public class OpenDssInvControlMetadataTest {
	@Test
	void parsesInvControlIntoGenericInverterControlData() {
		OpenDSSDataParser parser = new OpenDSSDataParser();

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
		OpenDSSDataParser parser = new OpenDSSDataParser();

		assertTrue(parser.getInvControlParser().parseInvControlData(
				"New InvControl.inv2 DERList=[Storage.batt1] mode=VOLTWATT voltwatt_curve=vw1"));

		InverterControlData control = parser.getInverterControls().get(0);
		assertEquals("batt1", control.getGeneratorId());
		assertEquals(ControlMode.VOLTWATT, control.getControlMode());
		assertEquals("vw1", control.getCurveId());
	}
}
