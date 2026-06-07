package org.interpss.threePhase.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.OpenDSSStaticDataParser;
import org.interpss.threePhase.qsts.QstsBusVoltageSample;
import org.interpss.threePhase.qsts.QstsControlMode;
import org.interpss.threePhase.qsts.QstsMode;
import org.interpss.threePhase.qsts.QstsResult;
import org.interpss.threePhase.qsts.QstsStepResult;
import org.interpss.threePhase.qsts.opendss.OpenDSSQstsStudyFactory;
import org.junit.jupiter.api.Test;

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
