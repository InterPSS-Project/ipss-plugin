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
import org.interpss.threePhase.powerflow.control.RegulatorControlData;
import org.interpss.threePhase.qsts.QstsControlMode;
import org.interpss.threePhase.qsts.QstsCsvExporter;
import org.interpss.threePhase.qsts.QstsDevicePowerSample;
import org.interpss.threePhase.qsts.QstsMode;
import org.interpss.threePhase.qsts.QstsRegulatorTapSample;
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
		QstsMode qstsMode = QstsMode.from(System.getProperty("qsts.compare.mode", "DAILY"));
		double stepSizeHours = Double.parseDouble(System.getProperty("qsts.compare.stepSizeHours", "1.0"));
		int startIndex = Integer.getInteger("qsts.compare.startIndex", 0);
		int maxControlIterations = Integer.getInteger("qsts.compare.maxControlIterations", 100);
		double tolerance = Double.parseDouble(System.getProperty("qsts.compare.tolerance", "1.0e-4"));
		boolean regControlsEnabled = Boolean.parseBoolean(
				System.getProperty("qsts.compare.regControlsEnabled", "true"));
		boolean capControlsEnabled = Boolean.parseBoolean(
				System.getProperty("qsts.compare.capControlsEnabled", "true"));
		boolean allowDisabledControls = Boolean.parseBoolean(
				System.getProperty("qsts.compare.allowDisabledControls", "false"));
		requireEnabledControls(controlMode, maxControlIterations, regControlsEnabled,
				capControlsEnabled, allowDisabledControls);
		Path outputDir = Path.of(System.getProperty("qsts.compare.outputDir",
				"target/qsts-comparison"));
		Files.createDirectories(outputDir);

		for(FeederCase feeder : selectedFeeders(caseSelector)) {
			feeder = withMasterOverride(feeder, System.getProperty("qsts.compare.masterFile"));
			QstsExportResult export = runQsts(feeder, steps, qstsMode, stepSizeHours,
					startIndex, controlMode, maxControlIterations, tolerance,
					regControlsEnabled, capControlsEnabled);
			QstsResult result = export.result();
			Path output = outputDir.resolve(feeder.key()
					+ "_qsts_" + controlTag(controlMode, regControlsEnabled, capControlsEnabled)
					+ "_interpss_voltage_by_step.csv");
			Files.writeString(output, new QstsCsvExporter().exportBusVoltages(result),
					StandardCharsets.UTF_8);
			Path branchOutput = outputDir.resolve(feeder.key()
					+ "_qsts_" + controlTag(controlMode, regControlsEnabled, capControlsEnabled)
					+ "_interpss_branch_power_by_step.csv");
			Files.writeString(branchOutput, new QstsCsvExporter().exportBranchPowers(result),
					StandardCharsets.UTF_8);
			Path loadOutput = outputDir.resolve(feeder.key()
					+ "_qsts_" + controlTag(controlMode, regControlsEnabled, capControlsEnabled)
					+ "_interpss_load_power_by_step.csv");
			Files.writeString(loadOutput, exportLoadPowersKw(result, export.baseKva()),
					StandardCharsets.UTF_8);
			Path generatorOutput = outputDir.resolve(feeder.key()
					+ "_qsts_" + controlTag(controlMode, regControlsEnabled, capControlsEnabled)
					+ "_interpss_generator_power_by_step.csv");
			Files.writeString(generatorOutput, exportGeneratorPowersKw(result, export.baseKva()),
					StandardCharsets.UTF_8);
			Path regulatorOutput = outputDir.resolve(feeder.key()
					+ "_qsts_" + controlTag(controlMode, regControlsEnabled, capControlsEnabled)
					+ "_interpss_regulator_taps_by_step.csv");
			Files.writeString(regulatorOutput, exportRegulatorTaps(feeder, result),
					StandardCharsets.UTF_8);
			Path capacitorOutput = outputDir.resolve(feeder.key()
					+ "_qsts_" + controlTag(controlMode, regControlsEnabled, capControlsEnabled)
					+ "_interpss_capacitor_states_by_step.csv");
			Files.writeString(capacitorOutput, new QstsCsvExporter().exportCapacitorStates(result),
					StandardCharsets.UTF_8);
			System.out.println(String.format(Locale.US,
					"INTERPSS_QSTS_REFERENCE feeder=%s steps=%d controlMode=%s maxControlIterations=%d "
							+ "mode=%s stepSizeHours=%.12g startIndex=%d "
							+ "regControlsEnabled=%s capControlsEnabled=%s "
							+ "masterFile=%s converged=%s voltageRows=%d "
							+ "branchPowerRows=%d loadPowerRows=%d generatorPowerRows=%d "
							+ "regulatorTapRows=%d capacitorStateRows=%d "
							+ "output=%s branchOutput=%s loadOutput=%s generatorOutput=%s "
							+ "regulatorOutput=%s capacitorOutput=%s",
					feeder.name(), steps, controlMode, maxControlIterations,
					qstsMode, stepSizeHours, startIndex, regControlsEnabled, capControlsEnabled,
					feeder.masterFile(), result.isConverged(),
					result.getBusVoltages().size(), result.getBranchPowers().size(),
					result.getLoadPowers().size(),
					result.getGeneratorPowers().size(),
					result.getStepResults().stream().mapToInt(step -> step.getRegulatorTaps().size()).sum(),
					result.getCapacitorStates().size(),
					output, branchOutput, loadOutput, generatorOutput, regulatorOutput, capacitorOutput));
			assertTrue(result.isConverged(), "QSTS export did not converge for " + feeder.name());
		}
	}

	private static QstsExportResult runQsts(FeederCase feeder, int steps, QstsMode qstsMode,
			double stepSizeHours, int startIndex, QstsControlMode controlMode,
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
		List<RegulatorControlData> regulatorControls = parser.getRegulatorControls();
		var study = OpenDSSQstsStudyFactory.from(parser)
				.setPowerFlowAlgorithm(algorithm)
				.setRegulatorControls(regulatorControls)
				.setMode(qstsMode)
				.setNumberOfSteps(steps)
				.setStartIndex(startIndex)
				.setStepSizeHours(stepSizeHours)
				.setControlMode(controlMode)
				.setMaxControlIterations(maxControlIterations)
				.setPostSolveOutputMode(DistributionPostSolveOutputMode.VOLTAGE_ONLY)
				.setResultSamplingMode(QstsResultSamplingMode.FULL)
				.setMaxPowerFlowIterations(1000)
				.setTolerance(tolerance);
		if(!capControlsEnabled) {
			study.setCapacitorControls(Collections.emptyList());
		}
		return new QstsExportResult(study.run(), regulatorControls, parser.getStaticNetwork().getBaseKva());
	}

	private static String exportLoadPowersKw(QstsResult result, double baseKva) {
		StringBuilder csv = new StringBuilder();
		csv.append("step,hour,device_class,device,phase,p_kw,q_kvar\n");
		double phaseBaseKva = baseKva / 3.0;
		for(QstsDevicePowerSample sample : result.getLoadPowers()) {
			csv.append(sample.getStepIndex()).append(',')
					.append(String.format(Locale.US, "%.12g", sample.getHour())).append(',')
					.append(sample.getDeviceClass()).append(',')
					.append(sample.getDeviceId()).append(',')
					.append(sample.getPhase()).append(',')
					.append(String.format(Locale.US, "%.12g", sample.getP() * phaseBaseKva)).append(',')
					.append(String.format(Locale.US, "%.12g", sample.getQ() * phaseBaseKva))
					.append('\n');
		}
		return csv.toString();
	}

	private static String exportGeneratorPowersKw(QstsResult result, double baseKva) {
		StringBuilder csv = new StringBuilder();
		csv.append("step,hour,device_class,device,phase,p_kw,q_kvar\n");
		for(QstsDevicePowerSample sample : result.getGeneratorPowers()) {
			csv.append(sample.getStepIndex()).append(',')
					.append(String.format(Locale.US, "%.12g", sample.getHour())).append(',')
					.append(sample.getDeviceClass()).append(',')
					.append(sample.getDeviceId()).append(',')
					.append(sample.getPhase()).append(',')
					.append(String.format(Locale.US, "%.12g", sample.getP() * baseKva)).append(',')
					.append(String.format(Locale.US, "%.12g", sample.getQ() * baseKva))
					.append('\n');
		}
		return csv.toString();
	}

	private static String exportRegulatorTaps(FeederCase feeder, QstsResult result) {
		StringBuilder csv = new StringBuilder();
		csv.append("case,step,hour,control,branch,winding,tap_winding,phase,tap_number,tap_ratio,")
				.append("target_voltage,bandwidth,pt_ratio,delay_seconds,regulated_bus,line_drop_r,line_drop_x,voltage_limit\n");
		for(var step : result.getStepResults()) {
			for(QstsRegulatorTapSample sample : step.getRegulatorTaps()) {
				csv.append(feeder.name()).append(',')
						.append(sample.getStepIndex()).append(',')
						.append(String.format(Locale.US, "%.12g", sample.getHour())).append(',')
						.append(sample.getControlId()).append(',')
						.append(sample.getBranchName()).append(',')
						.append(sample.getWinding()).append(',')
						.append(sample.getTapWinding()).append(',')
						.append(sample.getPhase()).append(',')
						.append(sample.getTapPosition()).append(',')
						.append(String.format(Locale.US, "%.12g", sample.getTapRatio())).append(',')
						.append(String.format(Locale.US, "%.12g", sample.getTargetVoltage())).append(',')
						.append(String.format(Locale.US, "%.12g", sample.getBandwidth())).append(',')
						.append(String.format(Locale.US, "%.12g", sample.getPtRatio())).append(',')
						.append(String.format(Locale.US, "%.12g", sample.getDelaySeconds())).append(',')
						.append(sample.getRegulatedBusId() == null ? "" : sample.getRegulatedBusId()).append(',')
						.append(String.format(Locale.US, "%.12g", sample.getLineDropR())).append(',')
						.append(String.format(Locale.US, "%.12g", sample.getLineDropX())).append(',')
						.append(String.format(Locale.US, "%.12g", sample.getVoltageLimit()))
						.append('\n');
			}
		}
		return csv.toString();
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
		return new FeederCase(feeder.key(), feeder.name(), feeder.folder(), masterFile.trim());
	}

	private record FeederCase(String key, String name, String folder, String masterFile) {
	}

	private record QstsExportResult(QstsResult result, List<RegulatorControlData> regulatorControls,
			double baseKva) {
	}

	private static void requireEnabledControls(QstsControlMode controlMode, int maxControlIterations,
			boolean regControlsEnabled, boolean capControlsEnabled, boolean allowDisabledControls) {
		if(allowDisabledControls) {
			return;
		}
		assertTrue(controlMode != QstsControlMode.OFF,
				"Large-feeder QSTS comparisons must run with controls enabled; set "
						+ "-Dqsts.compare.allowDisabledControls=true only for frozen-state diagnostics");
		assertTrue(maxControlIterations >= 100,
				"Large-feeder QSTS comparisons must allow at least 100 control iterations; set "
						+ "-Dqsts.compare.allowDisabledControls=true only for frozen-state diagnostics");
		assertTrue(regControlsEnabled,
				"Large-feeder QSTS comparisons must keep regulator controls enabled; set "
						+ "-Dqsts.compare.allowDisabledControls=true only for frozen-state diagnostics");
		assertTrue(capControlsEnabled,
				"Large-feeder QSTS comparisons must keep capacitor controls enabled; set "
						+ "-Dqsts.compare.allowDisabledControls=true only for frozen-state diagnostics");
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
