package org.interpss.threePhase.dataparser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.powerflow.control.CapacitorControlData;
import org.interpss.threePhase.powerflow.control.CapacitorControlData.ControlType;
import org.junit.jupiter.api.Test;

public class OpenDssCapControlMetadataTest {
	@Test
	void parsesOpenDssVoltageCapControlMetadata() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();

		parser.getCapacitorParser().parseCapControlData(
				"New capcontrol.CP-NR-613 capacitor=CP-NR-613 element=Line.175007 "
						+ "type=voltage ptratio=60 OnSetting=118 OffSetting=121 delay=75");

		assertEquals(1, parser.getCapacitorParser().getCapControlCount());
		List<CapacitorControlData> controls = parser.getCapacitorControls();
		assertEquals(1, controls.size());
		CapacitorControlData control = controls.get(0);
		assertEquals("cp-nr-613", control.getCapacitorId());
		assertEquals("line.175007", control.getMonitoredElementName());
		assertEquals(ControlType.VOLTAGE, control.getControlType());
		assertEquals(118.0, control.getOnSetting(), 1.0e-12);
		assertEquals(121.0, control.getOffSetting(), 1.0e-12);
		assertEquals(60.0, control.getPtRatio(), 1.0e-12);
		assertEquals(75.0, control.getOnDelaySeconds(), 1.0e-12);
	}

	@Test
	void parsesOpenDssKvarCapControlMetadataWithVoltageOverride() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();

		parser.getCapacitorParser().parseCapControlData(
				"New CapControl.CAPBank2A_Ctrl Capacitor=CAPBank2A element=line.CAP_1A terminal=1 "
						+ "type=kvar ptratio=1 ctratio=1 ONsetting=150 OFFsetting=-225 "
						+ "VoltOverride=Y Vmin=7110 Vmax=7740 Delay=100 Delayoff=100");

		CapacitorControlData control = parser.getCapacitorControls().get(0);
		assertEquals("capbank2a", control.getCapacitorId());
		assertEquals(ControlType.KVAR, control.getControlType());
		assertEquals(1, control.getTerminal());
		assertEquals(150.0, control.getOnSetting(), 1.0e-12);
		assertEquals(-225.0, control.getOffSetting(), 1.0e-12);
		assertEquals(1.0, control.getPtRatio(), 1.0e-12);
		assertEquals(1.0, control.getCtRatio(), 1.0e-12);
		assertEquals(7110.0, control.getVMin(), 1.0e-12);
		assertEquals(7740.0, control.getVMax(), 1.0e-12);
		assertEquals(100.0, control.getOffDelaySeconds(), 1.0e-12);
	}
}
