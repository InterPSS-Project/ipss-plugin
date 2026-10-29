package org.interpss.threePhase.system;

import org.interpss.test.TestConfig;

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
    private static final TestConfig CONFIG = TestConfig.load(
            "/test-config/ipss.plugin.3phase.json", "QstsLargeFeederPerformanceBenchmark");

	private static final FeederCase CKT24 = new FeederCase("Ckt24",
			"testData/feeder/Ckt24", "master_ckt24_interpss.dss");
	private static final FeederCase IEEE8500 = new FeederCase("IEEE8500",
			"testData/feeder/IEEE8500", "Master-InterPSS.dss");

	@Test
	void controlledRepeatedStateDailyWindow() throws Exception {
		String recordingPath = CONFIG.string("qsts.perf.flightRecording");
		try(jdk.jfr.Recording recording = recordingPath == null ? null
				: new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"))) {
			if(recording != null) recording.start();
			runConfiguredBenchmark();
			if(recording != null) {
				recording.stop();
				recording.dump(java.nio.file.Path.of(recordingPath));
			}
		}
	}

	private void runConfiguredBenchmark() {
		int warmupSteps = CONFIG.integer("qsts.perf.warmupSteps", 24);
		int measureSteps = CONFIG.integer("qsts.perf.steps", 240);
		int repeats = CONFIG.integer("qsts.perf.repeats", 3);
		String caseSelector = CONFIG.string("qsts.perf.case", "all");
		QstsControlMode controlMode = QstsControlMode.from(
				CONFIG.string("qsts.perf.controlMode", "STATIC"));
		QstsMode qstsMode = QstsMode.from(CONFIG.string("qsts.perf.mode", "DAILY"));
		double stepSizeHours = Double.parseDouble(CONFIG.string("qsts.perf.stepSizeHours", "1.0"));
		double tolerance = Double.parseDouble(CONFIG.string("qsts.perf.tolerance", "1.0e-4"));
		int maxControlIterations = CONFIG.integer("qsts.perf.maxControlIterations", 100);
		boolean regControlsEnabled = Boolean.parseBoolean(
				CONFIG.string("qsts.perf.regControlsEnabled", "true"));
		boolean capControlsEnabled = Boolean.parseBoolean(
				CONFIG.string("qsts.perf.capControlsEnabled", "true"));
		boolean allowDisabledControls = Boolean.parseBoolean(
				CONFIG.string("qsts.perf.allowDisabledControls", "false"));
		requireEnabledControls(controlMode, maxControlIterations, regControlsEnabled,
				capControlsEnabled, allowDisabledControls);
		org.interpss.core.sparse.solver.klusolvex.KlusolveXLibrary.configureLibrary(
				CONFIG.string("nativeLibraryPath"));
		System.out.println(IpssCorePlugin.configureSparseSolver(CONFIG.string("sparseSolver", "java-klu")).message());

		List<FeederCase> feeders = selectedFeeders(caseSelector);
		System.out.println("INTERPSS_QSTS_PERF_CONFIG cases=" + caseSelector
				+ ", warmupSteps=" + warmupSteps
				+ ", measureSteps=" + measureSteps
				+ ", repeats=" + repeats
				+ ", mode=" + qstsMode
				+ ", stepSizeHours=" + stepSizeHours
				+ ", controlMode=" + controlMode
				+ ", maxControlIterations=" + maxControlIterations
				+ ", regControlsEnabled=" + regControlsEnabled
				+ ", capControlsEnabled=" + capControlsEnabled
				+ ", tolerance=" + tolerance);

		for(FeederCase feeder : feeders) {
			feeder = withMasterOverride(feeder, CONFIG.string("qsts.perf.masterFile"));
			RunSummary warmup = runInterpssQsts(feeder, warmupSteps, "warmup", 0,
					qstsMode, stepSizeHours, controlMode, maxControlIterations, tolerance,
					regControlsEnabled, capControlsEnabled);
			assertTrue(warmup.converged(), "Warm-up failed for " + feeder.name());

			List<RunSummary> measured = new ArrayList<>();
			for(int run = 1; run <= repeats; run++) {
				RunSummary summary = runInterpssQsts(feeder, measureSteps, "measured", run,
						qstsMode, stepSizeHours, controlMode, maxControlIterations, tolerance,
						regControlsEnabled, capControlsEnabled);
				assertTrue(summary.converged(), "Measured run " + run + " failed for " + feeder.name());
				measured.add(summary);
			}
			printAggregate(feeder, measured);
		}
	}

	private static RunSummary runInterpssQsts(FeederCase feeder, int steps, String phase, int run,
			QstsMode qstsMode, double stepSizeHours, QstsControlMode controlMode,
			int maxControlIterations, double tolerance, boolean regControlsEnabled,
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
		algorithm.setFixedPointRuntimeProfileEnabled(CONFIG.bool("qsts.perf.fixedPointProfile", false));
		algorithm.setMinIteration(CONFIG.integer("qsts.perf.minIterations", 2));
		algorithm.setPrimitiveVoltageStateEnabled(CONFIG.bool("qsts.perf.primitiveVoltageState", false));
		long startNanos = System.nanoTime();
		var study = OpenDSSQstsStudyFactory.from(parser)
				.setRuntimeProfileEnabled(CONFIG.bool("qsts.perf.profile"))
				.setStaticStateReuseEnabled(CONFIG.bool("qsts.perf.staticStateReuseEnabled", true))
				.setPowerFlowAlgorithm(algorithm)
				.setMode(qstsMode)
				.setNumberOfSteps(steps)
				.setStepSizeHours(stepSizeHours)
				.setControlMode(controlMode)
				.setMaxControlIterations(maxControlIterations)
				.setPostSolveOutputMode(DistributionPostSolveOutputMode.VOLTAGE_ONLY)
				.setResultSamplingMode(QstsResultSamplingMode.NONE)
				.setMaxPowerFlowIterations(1000)
				.setTolerance(tolerance);
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

	private static FeederCase withMasterOverride(FeederCase feeder, String masterFile) {
		if(masterFile == null || masterFile.trim().isEmpty()) {
			return feeder;
		}
		return new FeederCase(feeder.name(), feeder.folder(), masterFile.trim());
	}

	private record FeederCase(String name, String folder, String masterFile) {
	}

	private static void requireEnabledControls(QstsControlMode controlMode, int maxControlIterations,
			boolean regControlsEnabled, boolean capControlsEnabled, boolean allowDisabledControls) {
		if(allowDisabledControls) {
			return;
		}
		assertTrue(controlMode != QstsControlMode.OFF,
				"Large-feeder QSTS performance comparisons must run with controls enabled; set "
						+ "JSON setting qsts.perf.allowDisabledControls=true only for frozen-state diagnostics");
		assertTrue(maxControlIterations >= 100,
				"Large-feeder QSTS performance comparisons must allow at least 100 control iterations; set "
						+ "JSON setting qsts.perf.allowDisabledControls=true only for frozen-state diagnostics");
		assertTrue(regControlsEnabled,
				"Large-feeder QSTS performance comparisons must keep regulator controls enabled; set "
						+ "JSON setting qsts.perf.allowDisabledControls=true only for frozen-state diagnostics");
		assertTrue(capControlsEnabled,
				"Large-feeder QSTS performance comparisons must keep capacitor controls enabled; set "
						+ "JSON setting qsts.perf.allowDisabledControls=true only for frozen-state diagnostics");
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
