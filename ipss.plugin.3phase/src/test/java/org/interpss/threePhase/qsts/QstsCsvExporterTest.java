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

	@Test
	void capacitorStateCsvCanExportQstsResultSamples() {
		QstsStepContext context = new QstsStepContext(0, 0,
				0.0, QstsMode.DAILY, 0.25, 1.0, QstsControlMode.TIME);
		QstsResult result = new QstsResult(List.of(new QstsStepResult(context,
				true, 1, 0.0, null, 1, List.of(), List.of(), List.of(),
				List.of(new QstsCapacitorStateSample(0, 0.0,
						"cap1", false, 0.0, 0.0, 1)))));

		String csv = new QstsCsvExporter().exportCapacitorStates(result);

		String[] lines = csv.trim().split("\\R");
		assertEquals("step,hour,capacitor,closed,total_q_pu,total_q_kvar,operation_count", lines[0]);
		assertEquals("0,0.00000000000,cap1,false,0.00000000000,0.00000000000,1", lines[1]);
	}

	@Test
	void inverterControlCsvCanExportQstsResultSamples() {
		QstsStepContext context = new QstsStepContext(0, 0,
				0.0, QstsMode.DAILY, 0.25, 1.0, QstsControlMode.STATIC);
		QstsResult result = new QstsResult(List.of(new QstsStepResult(context,
				true, 1, 0.0, null, 0, List.of(), List.of(), List.of(),
				List.of(), List.of(new QstsInverterControlSample(0, 0.0, "inv1",
						"pv1", "VOLTVAR", true, 0.0, 500.0, false, "")))));

		String csv = new QstsCsvExporter().exportInverterControls(result);

		String[] lines = csv.trim().split("\\R");
		assertEquals("step,hour,control,generator,mode,applied,p_kw,q_kvar,limited,reason", lines[0]);
		assertEquals("0,0.00000000000,inv1,pv1,VOLTVAR,true,0.00000000000,500.000000000,false,", lines[1]);
	}

	@Test
	void generatorPowerCsvUsesSampledStaticPhaseGenChannel() {
		QstsStepContext context = new QstsStepContext(1, 1, 0.25, QstsMode.DAILY,
				0.25, 1.0, QstsControlMode.OFF);
		QstsStepResult step = new QstsStepResult(context, true, 2, Double.NaN, null, 0,
				null, null, List.of(new QstsDevicePowerSample(1, 0.25,
						"generator", "pv,1", "A", 0.12, -0.03)));

		String csv = new QstsCsvExporter().exportGeneratorPowers(new QstsResult(List.of(step)));

		String[] lines = csv.split("\\n");
		assertEquals("step,hour,device_class,device,phase,p_pu,q_pu", lines[0]);
		assertEquals(2, lines.length);
		assertEquals("1,0.250000000000,generator,\"pv,1\",A,0.120000000000,-0.0300000000000",
				lines[1]);
	}

	@Test
	void branchPowerCsvUsesStaticBranchPowerSamples() {
		QstsStepContext context = new QstsStepContext(1, 1, 0.25, QstsMode.DAILY,
				0.25, 1.0, QstsControlMode.STATIC);
		QstsStepResult step = new QstsStepResult(context, true, 2, Double.NaN, null, 0,
				null, null, null,
				List.of(new QstsBranchPowerSample(1, 0.25, "line",
						"line,1", 2, "bus.1.2.3", "C", -12.5, 3.75)),
				null, null);

		String csv = new QstsCsvExporter().exportBranchPowers(new QstsResult(List.of(step)));

		String[] lines = csv.split("\\n");
		assertEquals("step,hour,class,element,terminal,bus,phase,p_kw,q_kvar", lines[0]);
		assertEquals(2, lines.length);
		assertEquals("1,0.250000000000,line,\"line,1\",2,bus.1.2.3,C,-12.5000000000,3.75000000000",
				lines[1]);
	}

	@Test
	void loadPowerCsvUsesSampledStaticPhaseLoadChannel() {
		QstsStepContext context = new QstsStepContext(2, 2, 0.5, QstsMode.DAILY,
				0.25, 1.0, QstsControlMode.OFF);
		QstsStepResult step = new QstsStepResult(context, true, 2, Double.NaN, null, 0,
				null, List.of(new QstsDevicePowerSample(2, 0.5,
						"load", "load1", "B", 0.05, 0.02)), null);

		String csv = new QstsCsvExporter().exportLoadPowers(new QstsResult(List.of(step)));

		String[] lines = csv.split("\\n");
		assertEquals("step,hour,device_class,device,phase,p_pu,q_pu", lines[0]);
		assertEquals(2, lines.length);
		assertEquals("2,0.500000000000,load,load1,B,0.0500000000000,0.0200000000000",
				lines[1]);
	}
}
