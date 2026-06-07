package org.interpss.threePhase.qsts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class QstsCsvExporterTest {
	@Test
	void busVoltageCsvHasStableHeaderAndRows() {
		QstsStepContext context = new QstsStepContext(0, 0, 1.5, QstsMode.DAILY,
				0.25, 1.0, QstsControlMode.OFF);
		QstsStepResult step = new QstsStepResult(context, true, 2, Double.NaN, null, 0,
				List.of(new QstsBusVoltageSample(0, 1.5, "bus,1", "A", 0.98, -1.25)),
				null, null);

		String csv = new QstsCsvExporter().exportBusVoltages(new QstsResult(List.of(step)));

		String[] lines = csv.split("\\n");
		assertEquals("step,hour,bus,phase,vmag_pu,angle_deg", lines[0]);
		assertEquals(2, lines.length);
		assertTrue(lines[1].contains("\"bus,1\""));
		assertTrue(lines[1].startsWith("0,1.50000000000,"));
	}

	@Test
	void capacitorStateCsvHasStableHeaderAndRows() {
		QstsCapacitorStateSample state = new QstsCapacitorStateSample(2, 0.5,
				"cap,1", true, -0.018, -600.0, 1);

		String csv = new QstsCsvExporter().exportCapacitorStates(List.of(state));

		String[] lines = csv.split("\\n");
		assertEquals("step,hour,capacitor,closed,total_q_pu,total_q_kvar,operation_count", lines[0]);
		assertEquals(2, lines.length);
		assertEquals("2,0.500000000000,\"cap,1\",true,-0.0180000000000,-600.000000000,1", lines[1]);
	}
}
