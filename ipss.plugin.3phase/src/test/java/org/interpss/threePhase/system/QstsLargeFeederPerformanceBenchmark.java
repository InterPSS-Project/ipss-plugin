package org.interpss.threePhase.system;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.interpss.IpssCorePlugin;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.OpenDSSStaticDataParser;
import org.interpss.threePhase.powerflow.DistributionPostSolveOutputMode;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.qsts.QstsControlMode;
import org.interpss.threePhase.qsts.QstsMode;
import org.interpss.threePhase.qsts.QstsResult;
import org.interpss.threePhase.qsts.QstsResultSamplingMode;
import org.interpss.threePhase.qsts.QstsStepResult;
import org.interpss.threePhase.qsts.opendss.OpenDSSQstsStudyFactory;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

/**
 * Manual benchmark for larger QSTS feeders. The class intentionally does not end
 * with Test so it is not picked up by the default Surefire test scan.
 */
public class QstsLargeFeederPerformanceBenchmark {
	private static final FeederCase CKT24 = new FeederCase("Ckt24",
			"testData/feeder/Ckt24", "master_ckt24_interpss.dss");
	private static final FeederCase IEEE8500 = new FeederCase("IEEE8500",
			"testData/feeder/IEEE8500", "Master-InterPSS.dss");

	@Test
	void controlsOffRepeatedStateDailyWindow() {
		int warmupSteps = Integer.getInteger("qsts.perf.warmupSteps", 24);
		int measureSteps = Integer.getInteger("qsts.perf.steps", 240);
		int repeats = Integer.getInteger("qsts.perf.repeats", 3);
		String caseSelector = System.getProperty("qsts.perf.case", "all");
		QstsControlMode controlMode = QstsControlMode.from(
				System.getProperty("qsts.perf.controlMode", "STATIC"));
		int maxControlIterations = Integer.getInteger("qsts.perf.maxControlIterations", 20);
		boolean regControlsEnabled = Boolean.parseBoolean(
				System.getProperty("qsts.perf.regControlsEnabled", "true"));
		boolean capControlsEnabled = Boolean.parseBoolean(
				System.getProperty("qsts.perf.capControlsEnabled", "true"));
		System.out.println(IpssCorePlugin.configureSparseSolverFromSystemProperties().message());

		List<FeederCase> feeders = selectedFeeders(caseSelector);
		System.out.println("INTERPSS_QSTS_PERF_CONFIG cases=" + caseSelector
				+ ", warmupSteps=" + warmupSteps
				+ ", measureSteps=" + measureSteps
				+ ", repeats=" + repeats
				+ ", controlMode=" + controlMode
				+ ", maxControlIterations=" + maxControlIterations
				+ ", regControlsEnabled=" + regControlsEnabled
				+ ", capControlsEnabled=" + capControlsEnabled
				+ ", tolerance=1.0e-4");

		for(FeederCase feeder : feeders) {
			RunSummary warmup = runInterpssQsts(feeder, warmupSteps, "warmup", 0,
					controlMode, maxControlIterations, regControlsEnabled, capControlsEnabled);
			assertTrue(warmup.converged(), "Warm-up failed for " + feeder.name());

			List<RunSummary> measured = new ArrayList<>();
			for(int run = 1; run <= repeats; run++) {
				RunSummary summary = runInterpssQsts(feeder, measureSteps, "measured", run,
						controlMode, maxControlIterations, regControlsEnabled, capControlsEnabled);
				assertTrue(summary.converged(), "Measured run " + run + " failed for " + feeder.name());
				measured.add(summary);
			}
			printAggregate(feeder, measured);
		}
	}

	private static RunSummary runInterpssQsts(FeederCase feeder, int steps, String phase, int run,
			QstsControlMode controlMode, int maxControlIterations, boolean regControlsEnabled,
			boolean capControlsEnabled) {
		OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.setRegControlEnabled(regControlsEnabled);
		assertTrue(parser.parseFeederData(feeder.folder(), feeder.masterFile()),
				"Failed to parse " + feeder.name());
		assertTrue(parser.calcVoltageBases(), "Failed to calculate voltage bases for " + feeder.name());
		assertTrue(parser.convertActualValuesToPU(1.0),
				"Failed to convert actual values to PU for " + feeder.name());

		DistributionPowerFlowAlgorithm algorithm = ThreePhaseObjectFactory
				.createDistPowerFlowAlgorithm(parser.getStaticNetwork());
		long startNanos = System.nanoTime();
		var study = OpenDSSQstsStudyFactory.from(parser)
				.setPowerFlowAlgorithm(algorithm)
				.setMode(QstsMode.DAILY)
				.setNumberOfSteps(steps)
				.setStepSizeHours(1.0)
				.setControlMode(controlMode)
				.setMaxControlIterations(maxControlIterations)
				.setPostSolveOutputMode(DistributionPostSolveOutputMode.VOLTAGE_ONLY)
				.setResultSamplingMode(QstsResultSamplingMode.NONE)
				.setMaxPowerFlowIterations(1000)
				.setTolerance(1.0e-4);
		if(!capControlsEnabled) {
			study.setCapacitorControls(Collections.emptyList());
		}
		QstsResult result = study.run();
		long elapsedNanos = System.nanoTime() - startNanos;

		RunSummary summary = RunSummary.from(feeder, phase, run, steps, elapsedNanos,
				result, algorithm);
		System.out.println(summary.toMetricLine());
		return summary;
	}

	private static void printAggregate(FeederCase feeder, List<RunSummary> measured) {
		double avgMsPerStep = measured.stream()
				.mapToDouble(RunSummary::millisPerStep)
				.average()
				.orElse(Double.NaN);
		double minMsPerStep = measured.stream()
				.mapToDouble(RunSummary::millisPerStep)
				.min()
				.orElse(Double.NaN);
		double medianMsPerStep = median(measured.stream()
				.map(RunSummary::millisPerStep)
				.sorted()
				.toList());
		System.out.println(String.format(Locale.US,
				"INTERPSS_QSTS_PERF_AGG feeder=%s runs=%d avgMsPerStep=%.6f medianMsPerStep=%.6f minMsPerStep=%.6f",
				feeder.name(), measured.size(), avgMsPerStep, medianMsPerStep, minMsPerStep));
	}

	private static double median(List<Double> values) {
		if(values.isEmpty()) {
			return Double.NaN;
		}
		int mid = values.size() / 2;
		if(values.size() % 2 == 1) {
			return values.get(mid);
		}
		return (values.get(mid - 1) + values.get(mid)) / 2.0;
	}

	private static List<FeederCase> selectedFeeders(String caseSelector) {
		String selector = caseSelector == null ? "all" : caseSelector.trim().toLowerCase(Locale.ROOT);
		if(selector.equals("ckt24")) {
			return List.of(CKT24);
		}
		if(selector.equals("ieee8500") || selector.equals("8500")) {
			return List.of(IEEE8500);
		}
		return List.of(CKT24, IEEE8500);
	}

	private record FeederCase(String name, String folder, String masterFile) {
	}

	private record RunSummary(FeederCase feeder, String phase, int run, int requestedSteps,
			int actualSteps, boolean converged, long elapsedNanos, int maxIterationCount,
			long symbolicFactors, long numericFactors, long valueUpdates, long fallbackCount) {
		static RunSummary from(FeederCase feeder, String phase, int run, int requestedSteps,
				long elapsedNanos, QstsResult result, DistributionPowerFlowAlgorithm algorithm) {
			int maxIterations = result.getStepResults().stream()
					.map(QstsStepResult::getIterationCount)
					.max(Comparator.naturalOrder())
					.orElse(0);
			return new RunSummary(feeder, phase, run, requestedSteps, result.getStepResults().size(),
					result.isConverged(), elapsedNanos, maxIterations,
					algorithm.getFixedPointYMatrixSymbolicFactorizationCount(),
					algorithm.getFixedPointYMatrixNumericFactorizationCount(),
					algorithm.getFixedPointYMatrixValueUpdateCount(),
					algorithm.getFixedPointFallbackCount());
		}

		double elapsedMillis() {
			return elapsedNanos / 1_000_000.0;
		}

		double millisPerStep() {
			return elapsedMillis() / Math.max(1, actualSteps);
		}

		String toMetricLine() {
			return String.format(Locale.US,
					"INTERPSS_QSTS_PERF feeder=%s phase=%s run=%d requestedSteps=%d actualSteps=%d converged=%s elapsedMillis=%.3f msPerStep=%.6f maxIterations=%d symbolicFactors=%d numericFactors=%d valueUpdates=%d fallbackCount=%d",
					feeder.name(), phase, run, requestedSteps, actualSteps, converged,
					elapsedMillis(), millisPerStep(), maxIterationCount, symbolicFactors,
					numericFactors, valueUpdates, fallbackCount);
		}
	}
}
