package org.interpss.threePhase.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.OpenDSSStaticDataParser;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.control.CapacitorControlData;
import org.interpss.threePhase.powerflow.control.CapacitorControlData.ControlType;
import org.interpss.threePhase.powerflow.control.CapacitorControlData.PhaseSelection;
import org.interpss.threePhase.powerflow.control.RegulatorControlData;
import org.interpss.threePhase.qsts.QstsBusVoltageSample;
import org.interpss.threePhase.qsts.QstsControlMode;
import org.interpss.threePhase.qsts.QstsMode;
import org.interpss.threePhase.qsts.QstsResult;
import org.interpss.threePhase.qsts.QstsStepResult;
import org.interpss.threePhase.qsts.opendss.OpenDSSQstsStudyFactory;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.PhaseCode;

public class OpenDSSQstsFeederSmokeTest {
	private static final int FULL_DAY_STEPS = 24;
	private static final int SHORT_LARGE_FEEDER_STEPS = 6;
	private static final double SMOKE_VOLTAGE_CEILING_PU = 2.0;

	@Test
	void ieee123ControlsOffRepeatedStateDailyWindowConverges() {
		QstsSmokeSummary summary = runSmoke(new FeederSmokeCase("IEEE123",
				"testData/feeder/IEEE123", "IEEE123Master.dss", QstsMode.DAILY, FULL_DAY_STEPS));
		assertSmokeSummary(summary);
	}

	@Test
	void ckt7ControlsOffYearlyWindowConverges() {
		QstsSmokeSummary summary = runSmoke(new FeederSmokeCase("Ckt7",
				"testData/feeder/Ckt7", "Master_ckt7.dss", QstsMode.YEARLY, FULL_DAY_STEPS));
		assertSmokeSummary(summary);
	}

	@Test
	void ckt24ControlsOffRepeatedStateDailyWindowConverges() {
		QstsSmokeSummary summary = runSmoke(new FeederSmokeCase("Ckt24",
				"testData/feeder/Ckt24", "master_ckt24_interpss.dss", QstsMode.DAILY, FULL_DAY_STEPS));
		assertSmokeSummary(summary);
	}

	@Test
	void ieee8500ControlsOffShortRepeatedStateDailyWindowConverges() {
		QstsSmokeSummary summary = runSmoke(new FeederSmokeCase("IEEE8500",
				"testData/feeder/IEEE8500", "Master-InterPSS.dss", QstsMode.DAILY,
				SHORT_LARGE_FEEDER_STEPS));
		assertSmokeSummary(summary);
	}

	@Test
	void ckt24CapacitorControlRebuildsYMatrixOnLargeFeeder() {
		SolvedQstsCase solved = solveCkt24CapacitorControlCase();

		assertTrue(solved.result().isConverged(), "Ckt24 cap-control QSTS failed");
		assertFalse(solved.algorithm().isFixedPointYMatrixCacheEnabled(),
				"Control-enabled QSTS should not use the fixed Y-matrix cache");
		assertTrue(solved.algorithm().getFixedPointYMatrixNumericFactorizationCount() > 0);
		assertEquals(capacitorStates(solved.controls()), capacitorStates(solved.controls()));
	}

	@Test
	void ieee123PerPhaseRegulatorSymbolicFactorizationMatchesFullRebuild() {
		SolvedRegulatorQstsCase fullRebuild = solveIeee123RegulatorControlCase(true, true);
		SolvedRegulatorQstsCase symbolicReuse = solveIeee123RegulatorControlCase(false, false);

		assertTrue(fullRebuild.result().isConverged(), "IEEE123 full-rebuild regulator QSTS failed");
		assertTrue(symbolicReuse.result().isConverged(), "IEEE123 symbolic regulator QSTS failed");
		assertFalse(symbolicReuse.algorithm().isFixedPointYMatrixCacheEnabled(),
				"Regulator controls should not use the fixed Y-matrix cache");
		assertTrue(symbolicReuse.algorithm().getFixedPointYMatrixNumericFactorizationCount()
						> symbolicReuse.algorithm().getFixedPointYMatrixSymbolicFactorizationCount(),
				"Regulator symbolic reuse should perform more numeric than symbolic factorizations");
		assertTrue(symbolicReuse.algorithm().getFixedPointYMatrixValueUpdateCount() > 0,
				"Regulator symbolic reuse should update existing sparse-matrix values in place");

		assertVoltageParity(fullRebuild.result(), symbolicReuse.result(), 1.0e-6, 1.0e-4,
				"IEEE123 regulator symbolic");
		assertEquals(tapsByControlId(fullRebuild.controls()), tapsByControlId(symbolicReuse.controls()));
	}

	private static QstsSmokeSummary runSmoke(FeederSmokeCase feeder) {
		OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData(feeder.folder(), feeder.masterFile()),
				"Failed to parse " + feeder.name());
		assertTrue(parser.calcVoltageBases(), "Failed to calculate voltage bases for " + feeder.name());
		assertTrue(parser.convertActualValuesToPU(1.0),
				"Failed to convert actual values to PU for " + feeder.name());

		long startNanos = System.nanoTime();
		QstsResult result = OpenDSSQstsStudyFactory.from(parser)
				.setMode(feeder.mode())
				.setNumberOfSteps(feeder.steps())
				.setStepSizeHours(1.0)
				.setControlMode(QstsControlMode.OFF)
				.setMaxControlIterations(0)
				.setMaxPowerFlowIterations(1000)
				.setTolerance(1.0e-4)
				.run();
		long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

		QstsSmokeSummary summary = QstsSmokeSummary.from(feeder, result, elapsedMillis);
		System.out.println(summary);
		return summary;
	}

	private static SolvedQstsCase solveCkt24CapacitorControlCase() {
		OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
		assertTrue(parser.parseFeederData("testData/feeder/Ckt24", "master_ckt24_interpss.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));
		List<CapacitorControlData> controls = ckt24CapacitorControls();
		DistributionPowerFlowAlgorithm algorithm = ThreePhaseObjectFactory
				.createDistPowerFlowAlgorithm(parser.getStaticNetwork());

		QstsResult result = OpenDSSQstsStudyFactory.from(parser)
				.setPowerFlowAlgorithm(algorithm)
				.setRegulatorControls(List.of())
				.setCapacitorControls(controls)
				.setMode(QstsMode.DAILY)
				.setNumberOfSteps(3)
				.setStepSizeHours(1.0)
				.setControlMode(QstsControlMode.STATIC)
				.setMaxControlIterations(12)
				.setMaxPowerFlowIterations(1000)
				.setTolerance(1.0e-6)
				.run();
		return new SolvedQstsCase(result, controls, algorithm);
	}

	private static SolvedRegulatorQstsCase solveIeee123RegulatorControlCase(
			boolean disableSymbolReuse, boolean disableValueUpdate) {
		String oldDisableSymbolReuse = System.getProperty("ipss.qsts.disableFixedPointSymbolReuse");
		String oldDisableValueUpdate = System.getProperty("ipss.qsts.disableFixedPointValueUpdate");
		try {
			setBooleanProperty("ipss.qsts.disableFixedPointSymbolReuse", disableSymbolReuse);
			setBooleanProperty("ipss.qsts.disableFixedPointValueUpdate", disableValueUpdate);

			OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
			assertTrue(parser.parseFeederData("testData/feeder/IEEE123", "IEEE123Master.dss"));
			assertTrue(parser.getRegulatorParser().getRegControlCount() >= 7,
					"IEEE123 should parse its per-phase regulator controls");
			assertTrue(parser.calcVoltageBases());
			assertTrue(parser.convertActualValuesToPU(1.0));
			List<RegulatorControlData> controls = parser.getRegulatorControls();
			assertTrue(controls.size() >= 7, "IEEE123 regulator controls should be available to QSTS");
			DistributionPowerFlowAlgorithm algorithm = ThreePhaseObjectFactory
					.createDistPowerFlowAlgorithm(parser.getStaticNetwork());

			QstsResult result = OpenDSSQstsStudyFactory.from(parser)
					.setPowerFlowAlgorithm(algorithm)
					.setMode(QstsMode.DAILY)
					.setNumberOfSteps(3)
					.setStepSizeHours(1.0)
					.setControlMode(QstsControlMode.STATIC)
					.setMaxControlIterations(20)
					.setMaxPowerFlowIterations(200)
					.setTolerance(1.0e-8)
					.run();
			return new SolvedRegulatorQstsCase(result, controls, algorithm);
		}
		finally {
			restoreProperty("ipss.qsts.disableFixedPointSymbolReuse", oldDisableSymbolReuse);
			restoreProperty("ipss.qsts.disableFixedPointValueUpdate", oldDisableValueUpdate);
		}
	}

	private static List<CapacitorControlData> ckt24CapacitorControls() {
		return List.of(
				ckt24VoltageCapControl("capctrl_g2100pl6500", "cap_g2100pl6500"),
				ckt24VoltageCapControl("capctrl_g2100fk7800", "cap_g2100fk7800"),
				ckt24VoltageCapControl("capctrl_g2101ae7400", "cap_g2101ae7400"));
	}

	private static CapacitorControlData ckt24VoltageCapControl(String controlId, String capacitorId) {
		return new CapacitorControlData(controlId, capacitorId, "", 1, ControlType.VOLTAGE,
				118.0, 121.0, 120.0, 1.0, false, 0.0, 0.0, 0.0, 0.0,
				PhaseCode.ABC, PhaseSelection.AVG);
	}

	private static void assertVoltageParity(QstsResult expected, QstsResult actual,
			double magnitudeTolerance, double angleTolerance, String prefix) {
		Map<String, QstsBusVoltageSample> expectedVoltages = busVoltagesByKey(expected);
		Map<String, QstsBusVoltageSample> actualVoltages = busVoltagesByKey(actual);
		assertEquals(expectedVoltages.keySet(), actualVoltages.keySet());
		for(String key : expectedVoltages.keySet()) {
			QstsBusVoltageSample expectedSample = expectedVoltages.get(key);
			QstsBusVoltageSample actualSample = actualVoltages.get(key);
			assertEquals(expectedSample.getMagnitude(), actualSample.getMagnitude(),
					magnitudeTolerance, prefix + " voltage magnitude mismatch for " + key);
			assertEquals(expectedSample.getAngleDegrees(), actualSample.getAngleDegrees(),
					angleTolerance, prefix + " voltage angle mismatch for " + key);
		}
	}

	private static Map<String, QstsBusVoltageSample> busVoltagesByKey(QstsResult result) {
		Map<String, QstsBusVoltageSample> samples = new LinkedHashMap<>();
		for(QstsBusVoltageSample sample : result.getBusVoltages()) {
			samples.put(sample.getStepIndex() + "|" + sample.getBusId() + "|" + sample.getPhase(), sample);
		}
		return samples;
	}

	private static Map<String, Boolean> capacitorStates(List<CapacitorControlData> controls) {
		Map<String, Boolean> states = new LinkedHashMap<>();
		for(CapacitorControlData control : controls) {
			states.put(control.getCapacitorId(), control.isClosed());
		}
		return states;
	}

	private static Map<String, Integer> tapsByControlId(List<RegulatorControlData> controls) {
		Map<String, Integer> taps = new LinkedHashMap<>();
		for(RegulatorControlData control : controls) {
			taps.put(control.getId(), control.getTapPosition());
		}
		return taps;
	}

	private static void assertSmokeSummary(QstsSmokeSummary summary) {
		assertTrue(summary.converged(), summary.name() + " QSTS did not converge");
		assertEquals(summary.expectedSteps(), summary.actualSteps(),
				summary.name() + " should complete every requested step");
		assertFalse(summary.hasNonFiniteVoltage(),
				summary.name() + " should not record NaN or infinite voltage samples");
		assertTrue(summary.voltageSampleCount() > 0,
				summary.name() + " should record voltage samples");
		assertTrue(summary.maxVoltagePu() < SMOKE_VOLTAGE_CEILING_PU,
				summary.name() + " max voltage is outside smoke range: " + summary.maxVoltagePu());
		assertTrue(summary.maxIterationCount() > 0,
				summary.name() + " should report at least one PF iteration");
	}

	private static void setBooleanProperty(String key, boolean value) {
		if(value) {
			System.setProperty(key, "true");
		}
		else {
			System.clearProperty(key);
		}
	}

	private static void restoreProperty(String key, String value) {
		if(value == null) {
			System.clearProperty(key);
		}
		else {
			System.setProperty(key, value);
		}
	}

	private record FeederSmokeCase(String name, String folder, String masterFile,
			QstsMode mode, int steps) {
	}

	private record SolvedQstsCase(QstsResult result, List<CapacitorControlData> controls,
			DistributionPowerFlowAlgorithm algorithm) {
	}

	private record SolvedRegulatorQstsCase(QstsResult result, List<RegulatorControlData> controls,
			DistributionPowerFlowAlgorithm algorithm) {
	}

	private record QstsSmokeSummary(String name, QstsMode mode, int expectedSteps,
			int actualSteps, boolean converged, int voltageSampleCount,
			boolean hasNonFiniteVoltage, double maxVoltagePu, int maxIterationCount,
			long elapsedMillis) {
		static QstsSmokeSummary from(FeederSmokeCase feeder, QstsResult result,
				long elapsedMillis) {
			double maxVoltagePu = 0.0;
			boolean hasNonFiniteVoltage = false;
			int voltageSampleCount = 0;
			int maxIterationCount = 0;
			for(QstsStepResult step : result.getStepResults()) {
				maxIterationCount = Math.max(maxIterationCount, step.getIterationCount());
				for(QstsBusVoltageSample sample : step.getBusVoltages()) {
					voltageSampleCount++;
					double magnitude = sample.getMagnitude();
					if(!Double.isFinite(magnitude)) {
						hasNonFiniteVoltage = true;
					}
					else {
						maxVoltagePu = Math.max(maxVoltagePu, magnitude);
					}
				}
			}
			return new QstsSmokeSummary(feeder.name(), feeder.mode(), feeder.steps(),
					result.getStepResults().size(), result.isConverged(), voltageSampleCount,
					hasNonFiniteVoltage, maxVoltagePu, maxIterationCount, elapsedMillis);
		}

		@Override
		public String toString() {
			return "QSTS smoke " + name + " mode=" + mode
					+ ", steps=" + actualSteps + "/" + expectedSteps
					+ ", converged=" + converged
					+ ", voltageSamples=" + voltageSampleCount
					+ ", maxVoltagePu=" + maxVoltagePu
					+ ", maxIterations=" + maxIterationCount
					+ ", elapsedMillis=" + elapsedMillis;
		}
	}
}
