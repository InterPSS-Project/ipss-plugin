package org.interpss.threePhase.system;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.OpenDSSStaticDataParser;
import org.interpss.threePhase.powerflow.DistributionPostSolveOutputMode;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.qsts.QstsControlMode;
import org.interpss.threePhase.qsts.QstsCsvExporter;
import org.interpss.threePhase.qsts.QstsMode;
import org.interpss.threePhase.qsts.QstsResult;
import org.interpss.threePhase.qsts.QstsResultSamplingMode;
import org.interpss.threePhase.qsts.opendss.OpenDSSQstsStudyFactory;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

/**
 * Manual export for large-feeder QSTS comparison data. The class intentionally
 * does not end with Test so it is not picked up by the default Surefire scan.
 */
public class QstsLargeFeederComparisonExport {
	private static final FeederCase CKT24 = new FeederCase("ckt24", "Ckt24",
			"testData/feeder/Ckt24", "master_ckt24_interpss.dss");
	private static final FeederCase IEEE8500 = new FeederCase("ieee8500", "IEEE8500",
			"testData/feeder/IEEE8500", "Master-InterPSS.dss");

	@Test
	void exportBusVoltages() throws IOException {
		int steps = Integer.getInteger("qsts.compare.steps", 24);
		String caseSelector = System.getProperty("qsts.compare.case", "all");
		QstsControlMode controlMode = QstsControlMode.from(
				System.getProperty("qsts.compare.controlMode", "STATIC"));
		int maxControlIterations = Integer.getInteger("qsts.compare.maxControlIterations", 20);
		boolean regControlsEnabled = Boolean.parseBoolean(
				System.getProperty("qsts.compare.regControlsEnabled", "true"));
		boolean capControlsEnabled = Boolean.parseBoolean(
				System.getProperty("qsts.compare.capControlsEnabled", "true"));
		Path outputDir = Path.of(System.getProperty("qsts.compare.outputDir",
				"target/qsts-comparison"));
		Files.createDirectories(outputDir);

		for(FeederCase feeder : selectedFeeders(caseSelector)) {
			QstsResult result = runQsts(feeder, steps, controlMode, maxControlIterations,
					regControlsEnabled, capControlsEnabled);
			Path output = outputDir.resolve(feeder.key()
					+ "_qsts_" + controlTag(controlMode, regControlsEnabled, capControlsEnabled)
					+ "_interpss_voltage_by_step.csv");
			Files.writeString(output, new QstsCsvExporter().exportBusVoltages(result),
					StandardCharsets.UTF_8);
			System.out.println(String.format(Locale.US,
					"INTERPSS_QSTS_REFERENCE feeder=%s steps=%d controlMode=%s maxControlIterations=%d "
							+ "regControlsEnabled=%s capControlsEnabled=%s converged=%s voltageRows=%d output=%s",
					feeder.name(), steps, controlMode, maxControlIterations,
					regControlsEnabled, capControlsEnabled, result.isConverged(),
					result.getBusVoltages().size(), output));
			assertTrue(result.isConverged(), "QSTS export did not converge for " + feeder.name());
		}
	}

	private static QstsResult runQsts(FeederCase feeder, int steps, QstsControlMode controlMode,
			int maxControlIterations, boolean regControlsEnabled, boolean capControlsEnabled) {
		OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.setRegControlEnabled(regControlsEnabled);
		assertTrue(parser.parseFeederData(feeder.folder(), feeder.masterFile()),
				"Failed to parse " + feeder.name());
		assertTrue(parser.calcVoltageBases(), "Failed to calculate voltage bases for " + feeder.name());
		assertTrue(parser.convertActualValuesToPU(1.0),
				"Failed to convert actual values to PU for " + feeder.name());

		DistributionPowerFlowAlgorithm algorithm = ThreePhaseObjectFactory
				.createDistPowerFlowAlgorithm(parser.getStaticNetwork());
		var study = OpenDSSQstsStudyFactory.from(parser)
				.setPowerFlowAlgorithm(algorithm)
				.setMode(QstsMode.DAILY)
				.setNumberOfSteps(steps)
				.setStepSizeHours(1.0)
				.setControlMode(controlMode)
				.setMaxControlIterations(maxControlIterations)
				.setPostSolveOutputMode(DistributionPostSolveOutputMode.VOLTAGE_ONLY)
				.setResultSamplingMode(QstsResultSamplingMode.FULL)
				.setMaxPowerFlowIterations(1000)
				.setTolerance(1.0e-4);
		if(!capControlsEnabled) {
			study.setCapacitorControls(Collections.emptyList());
		}
		return study.run();
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

	private record FeederCase(String key, String name, String folder, String masterFile) {
	}

	private static String controlTag(QstsControlMode controlMode, boolean regControlsEnabled,
			boolean capControlsEnabled) {
		String tag = "controls_" + controlMode.name().toLowerCase(Locale.ROOT);
		if(!regControlsEnabled) {
			tag += "_noreg";
		}
		if(!capControlsEnabled) {
			tag += "_nocap";
		}
		return tag;
	}
}
