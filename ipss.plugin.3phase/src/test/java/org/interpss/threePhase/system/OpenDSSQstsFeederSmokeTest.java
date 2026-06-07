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
import org.interpss.threePhase.qsts.control.QstsControlCompensationPolicy;
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
	void ckt24CapacitorCompensationMatchesBruteForceRebuildOnLargeFeeder() {
		SolvedQstsCase bruteForce = solveCkt24CapacitorControlCase(
				new QstsControlCompensationPolicy(false, true, true));
		SolvedQstsCase compensated = solveCkt24CapacitorControlCase(new QstsControlCompensationPolicy());

		assertTrue(bruteForce.result().isConverged(), "Ckt24 brute-force cap-control QSTS failed");
		assertTrue(compensated.result().isConverged(), "Ckt24 compensated cap-control QSTS failed");
		assertFalse(bruteForce.algorithm().isFixedPointYMatrixCacheEnabled(),
				"Brute-force policy should disable fixed-point matrix cache");
		assertTrue(compensated.algorithm().isFixedPointYMatrixCacheEnabled(),
				"Compensated policy should reuse the fixed-point matrix cache");

		Map<String, QstsBusVoltageSample> bruteForceVoltages = busVoltagesByKey(bruteForce.result());
		Map<String, QstsBusVoltageSample> compensatedVoltages = busVoltagesByKey(compensated.result());
		assertEquals(bruteForceVoltages.keySet(), compensatedVoltages.keySet());
		for(String key : bruteForceVoltages.keySet()) {
			QstsBusVoltageSample expected = bruteForceVoltages.get(key);
			QstsBusVoltageSample actual = compensatedVoltages.get(key);
			assertEquals(expected.getMagnitude(), actual.getMagnitude(), 1.0e-6,
					"Ckt24 capacitor-compensated voltage magnitude mismatch for " + key);
			assertEquals(expected.getAngleDegrees(), actual.getAngleDegrees(), 1.0e-4,
					"Ckt24 capacitor-compensated voltage angle mismatch for " + key);
		}
		assertEquals(capacitorStates(bruteForce.controls()), capacitorStates(compensated.controls()));
	}

	@Test
	void ieee123PerPhaseRegulatorSymbolicFactorizationMatchesBruteForceRebuild() {
		SolvedRegulatorQstsCase bruteForce = solveIeee123RegulatorControlCase(
				new QstsControlCompensationPolicy(true, true, false));
		SolvedRegulatorQstsCase symbolicReuse = solveIeee123RegulatorControlCase(
				new QstsControlCompensationPolicy());

		assertTrue(bruteForce.result().isConverged(), "IEEE123 brute-force regulator QSTS failed");
		assertTrue(symbolicReuse.result().isConverged(), "IEEE123 symbolic regulator QSTS failed");
		assertFalse(bruteForce.algorithm().isFixedPointYMatrixCacheEnabled(),
				"Brute-force policy should disable fixed-point matrix cache");
		assertFalse(symbolicReuse.algorithm().isFixedPointYMatrixCacheEnabled(),
				"Regulator symbolic reuse should rebuild numeric Y after tap changes");
		assertTrue(symbolicReuse.algorithm().getFixedPointYMatrixNumericFactorizationCount()
						> symbolicReuse.algorithm().getFixedPointYMatrixSymbolicFactorizationCount(),
				"Regulator symbolic reuse should perform more numeric than symbolic factorizations");
		assertTrue(symbolicReuse.algorithm().getFixedPointYMatrixValueUpdateCount() > 0,
				"Regulator symbolic reuse should update existing sparse-matrix values in place");

		Map<String, QstsBusVoltageSample> bruteForceVoltages = busVoltagesByKey(bruteForce.result());
		Map<String, QstsBusVoltageSample> symbolicVoltages = busVoltagesByKey(symbolicReuse.result());
		assertEquals(bruteForceVoltages.keySet(), symbolicVoltages.keySet());
		for(String key : bruteForceVoltages.keySet()) {
			QstsBusVoltageSample expected = bruteForceVoltages.get(key);
			QstsBusVoltageSample actual = symbolicVoltages.get(key);
			assertEquals(expected.getMagnitude(), actual.getMagnitude(), 1.0e-6,
					"IEEE123 regulator symbolic voltage magnitude mismatch for " + key);
			assertEquals(expected.getAngleDegrees(), actual.getAngleDegrees(), 1.0e-4,
					"IEEE123 regulator symbolic voltage angle mismatch for " + key);
		}
		assertEquals(tapsByControlId(bruteForce.controls()), tapsByControlId(symbolicReuse.controls()));
	}

	@Test
	void ieee123DampedRegulatorCompensationConvergesWithFallbackGuard() {
		SolvedRegulatorQstsCase dampedCompensation = solveIeee123RegulatorControlCase(
				new QstsControlCompensationPolicy(true, true, true));

		assertTrue(dampedCompensation.result().isConverged(),
				"IEEE123 damped regulator compensation QSTS should converge with fallback guard");
		assertTrue(dampedCompensation.algorithm().isFixedPointYMatrixCacheEnabled(),
				"Explicit regulator compensation policy should keep fixed-point matrix cache enabled");
		System.out.println("QSTS IEEE123 damped regulator compensation fallbackUsed="
				+ dampedCompensation.algorithm().isFixedPointFallbackUsed()
				+ ", fallbackCount="
				+ dampedCompensation.algorithm().getFixedPointFallbackCount()
				+ ", symbolicFactors="
				+ dampedCompensation.algorithm().getFixedPointYMatrixSymbolicFactorizationCount()
				+ ", numericFactors="
				+ dampedCompensation.algorithm().getFixedPointYMatrixNumericFactorizationCount());
	}

	@Test
	void ieee123RegulatorCompensationWithSeriesResistancePaddingConverges() {
		SolvedRegulatorQstsCase bruteForce = solveIeee123RegulatorControlCase(
				new QstsControlCompensationPolicy(true, true, false));
		String previous = System.getProperty("ipss.qsts.regulatorCompensationSeriesRPadPu");
		try {
			assertPaddedRegulatorCompensation(bruteForce, "0.01");
			assertPaddedRegulatorCompensation(bruteForce, "0.005");
			assertPaddedRegulatorCompensation(bruteForce, "0.001");
			assertPaddedRegulatorCompensation(bruteForce, "0.0005");
			assertPaddedRegulatorCompensation(bruteForce, "0.0001");
			assertPaddedRegulatorCompensation(bruteForce, "0.00001");
			assertPaddedRegulatorCompensation(bruteForce, "0.000001");
		}
		finally {
			if(previous == null) {
				System.clearProperty("ipss.qsts.regulatorCompensationSeriesRPadPu");
			}
			else {
				System.setProperty("ipss.qsts.regulatorCompensationSeriesRPadPu", previous);
			}
		}
	}

	@Test
	void ieee8500ControlsOffShortRepeatedStateDailyWindowConverges() {
		QstsSmokeSummary summary = runSmoke(new FeederSmokeCase("IEEE8500",
				"testData/feeder/IEEE8500", "Master-InterPSS.dss", QstsMode.DAILY,
				SHORT_LARGE_FEEDER_STEPS));
		assertSmokeSummary(summary);
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

	private static SolvedQstsCase solveCkt24CapacitorControlCase(QstsControlCompensationPolicy policy) {
		OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
		assertTrue(parser.parseFeederData("testData/feeder/Ckt24", "master_ckt24_interpss.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));
		List<CapacitorControlData> controls = ckt24CapacitorControls();
		DistributionPowerFlowAlgorithm algorithm = ThreePhaseObjectFactory
				.createDistPowerFlowAlgorithm(parser.getStaticNetwork());

		long startNanos = System.nanoTime();
		QstsResult result = OpenDSSQstsStudyFactory.from(parser)
				.setPowerFlowAlgorithm(algorithm)
				.setControlCompensationPolicy(policy)
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
		long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
		System.out.println("QSTS Ckt24 cap-control compensation policy cache="
				+ algorithm.isFixedPointYMatrixCacheEnabled()
				+ ", converged=" + result.isConverged()
				+ ", steps=" + result.getStepResults().size()
				+ ", elapsedMillis=" + elapsedMillis);
		return new SolvedQstsCase(result, controls, algorithm);
	}

	private static SolvedRegulatorQstsCase solveIeee123RegulatorControlCase(
			QstsControlCompensationPolicy policy) {
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

		long startNanos = System.nanoTime();
		QstsResult result = OpenDSSQstsStudyFactory.from(parser)
				.setPowerFlowAlgorithm(algorithm)
				.setControlCompensationPolicy(policy)
				.setMode(QstsMode.DAILY)
				.setNumberOfSteps(3)
				.setStepSizeHours(1.0)
				.setControlMode(QstsControlMode.STATIC)
				.setMaxControlIterations(20)
				.setMaxPowerFlowIterations(200)
				.setTolerance(1.0e-8)
				.run();
		long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
		System.out.println("QSTS IEEE123 regulator compensation policy cache="
				+ algorithm.isFixedPointYMatrixCacheEnabled()
				+ ", converged=" + result.isConverged()
				+ ", steps=" + result.getStepResults().size()
				+ ", elapsedMillis=" + elapsedMillis);
		return new SolvedRegulatorQstsCase(result, controls, algorithm);
	}

	private static void assertPaddedRegulatorCompensation(SolvedRegulatorQstsCase bruteForce,
			String rPadPu) {
		System.setProperty("ipss.qsts.regulatorCompensationSeriesRPadPu", rPadPu);
		SolvedRegulatorQstsCase paddedCompensation = solveIeee123RegulatorControlCase(
				new QstsControlCompensationPolicy(true, true, true));

		assertTrue(paddedCompensation.result().isConverged(),
				"IEEE123 padded regulator compensation QSTS should converge for rPadPu=" + rPadPu);
		assertTrue(paddedCompensation.algorithm().isFixedPointYMatrixCacheEnabled(),
				"Padded regulator compensation should keep fixed-point matrix cache enabled");
		double maxVoltageMagnitudeError = maxVoltageMagnitudeDifference(
				bruteForce.result(), paddedCompensation.result());
		double maxVoltageAngleError = maxVoltageAngleDifference(
				bruteForce.result(), paddedCompensation.result());
		System.out.println("QSTS IEEE123 padded regulator compensation rPadPu="
				+ rPadPu
				+ ", fallbackUsed="
				+ paddedCompensation.algorithm().isFixedPointFallbackUsed()
				+ ", fallbackCount="
				+ paddedCompensation.algorithm().getFixedPointFallbackCount()
				+ ", symbolicFactors="
				+ paddedCompensation.algorithm().getFixedPointYMatrixSymbolicFactorizationCount()
				+ ", numericFactors="
				+ paddedCompensation.algorithm().getFixedPointYMatrixNumericFactorizationCount()
				+ ", maxVoltageMagnitudeError="
				+ maxVoltageMagnitudeError
				+ ", maxVoltageAngleErrorDeg="
				+ maxVoltageAngleError
				+ ", tapsMatch="
				+ tapsByControlId(bruteForce.controls()).equals(tapsByControlId(paddedCompensation.controls())));
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

	private static Map<String, QstsBusVoltageSample> busVoltagesByKey(QstsResult result) {
		Map<String, QstsBusVoltageSample> samples = new LinkedHashMap<>();
		for(QstsBusVoltageSample sample : result.getBusVoltages()) {
			samples.put(sample.getStepIndex() + "|" + sample.getBusId() + "|" + sample.getPhase(), sample);
		}
		return samples;
	}

	private static double maxVoltageMagnitudeDifference(QstsResult expected, QstsResult actual) {
		Map<String, QstsBusVoltageSample> expectedVoltages = busVoltagesByKey(expected);
		Map<String, QstsBusVoltageSample> actualVoltages = busVoltagesByKey(actual);
		double max = 0.0;
		for(String key : expectedVoltages.keySet()) {
			QstsBusVoltageSample actualSample = actualVoltages.get(key);
			if(actualSample != null) {
				max = Math.max(max, Math.abs(expectedVoltages.get(key).getMagnitude()
						- actualSample.getMagnitude()));
			}
		}
		return max;
	}

	private static double maxVoltageAngleDifference(QstsResult expected, QstsResult actual) {
		Map<String, QstsBusVoltageSample> expectedVoltages = busVoltagesByKey(expected);
		Map<String, QstsBusVoltageSample> actualVoltages = busVoltagesByKey(actual);
		double max = 0.0;
		for(String key : expectedVoltages.keySet()) {
			QstsBusVoltageSample actualSample = actualVoltages.get(key);
			if(actualSample != null) {
				max = Math.max(max, Math.abs(expectedVoltages.get(key).getAngleDegrees()
						- actualSample.getAngleDegrees()));
			}
		}
		return max;
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
