package org.interpss.threePhase.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.OpenDSSStaticDataParser;
import org.interpss.threePhase.qsts.QstsDevicePowerSample;
import org.interpss.threePhase.qsts.QstsMode;
import org.interpss.threePhase.qsts.QstsResult;
import org.interpss.threePhase.qsts.opendss.OpenDSSQstsStudyFactory;
import org.junit.jupiter.api.Test;

public class OpenDssQstsMultiStepReferenceTest {
	private static final String REFERENCE_RESOURCE =
			"opendss-reference/qsts-multistep-dss-python-device-reference.csv";
	private static final double POWER_TOLERANCE_KW = 0.10;

	@Test
	void multiStepLoadPvAndStorageTerminalPowersMatchDssPythonReferences() throws IOException {
		Map<String, QstsCaseResult> results = new LinkedHashMap<>();
		results.put("load_daily_shape", runCase("testData/feeder/OpenDSSQstsLoadMini", "Master.dss"));
		results.put("official_pvsystem_example", runCase("testData/feeder/OpenDSSPVSystemMini", "Master.dss"));
		results.put("scheduled_charge_discharge", runCase("testData/feeder/OpenDSSStorageMini", "Master.dss"));

		for(DevicePowerReference reference : readReferences()) {
			QstsCaseResult caseResult = results.get(reference.caseId);
			assertNotNull(caseResult, "Missing QSTS result for " + reference.caseId);
			double powerBaseKva = reference.deviceClass.equals("load")
					? caseResult.baseKva / 3.0
					: caseResult.baseKva;
			double pKw = totalPower(caseResult.result, reference, true) * powerBaseKva;
			double qKvar = totalPower(caseResult.result, reference, false) * powerBaseKva;
			assertEquals(reference.expectedPKw, pKw, POWER_TOLERANCE_KW,
					"P mismatch for " + reference.description());
			assertEquals(reference.expectedQKvar, qKvar, POWER_TOLERANCE_KW,
					"Q mismatch for " + reference.description());
		}
	}

	private static QstsCaseResult runCase(String feederFolder, String masterFile) {
		OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.getStaticNetwork().setBaseKva(1000.0);
		assertTrue(parser.parseFeederData(feederFolder, masterFile));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		QstsResult result = OpenDSSQstsStudyFactory.from(parser)
				.setMode(QstsMode.DAILY)
				.setStartIndex(0)
				.setNumberOfSteps(2)
				.setStepSizeHours(1.0)
				.setTolerance(1.0e-8)
				.run();

		assertTrue(result.isConverged(), "QSTS did not converge for " + feederFolder);
		assertEquals(2, result.getStepResults().size());
		assertFalseDynamic(parser);
		return new QstsCaseResult(result, parser.getNetworkBaseKva());
	}

	private static void assertFalseDynamic(OpenDSSStaticDataParser parser) {
		assertTrue(parser.isStaticNetworkMode());
		assertTrue(parser.getStaticNetwork().getBusList().stream()
				.noneMatch(bus -> bus.getClass().getName().contains(".dstab.")));
		assertTrue(parser.getStaticNetwork().getBranchList().stream()
				.noneMatch(branch -> branch.getClass().getName().contains(".dstab.")));
	}

	private static double totalPower(QstsResult result, DevicePowerReference reference, boolean activePower) {
		List<QstsDevicePowerSample> samples = reference.deviceClass.equals("load")
				? result.getLoadPowers()
				: result.getGeneratorPowers();
		double total = 0.0;
		boolean found = false;
		for(QstsDevicePowerSample sample : samples) {
			if(sample.getStepIndex() == reference.step
					&& sample.getDeviceId().equalsIgnoreCase(reference.deviceId)) {
				total += activePower ? sample.getP() : sample.getQ();
				found = true;
			}
		}
		assertTrue(found, "Missing sample for " + reference.description());
		return total;
	}

	private static List<DevicePowerReference> readReferences() throws IOException {
		InputStream stream = OpenDssQstsMultiStepReferenceTest.class.getClassLoader()
				.getResourceAsStream(REFERENCE_RESOURCE);
		assertNotNull(stream, "Missing resource " + REFERENCE_RESOURCE);
		List<DevicePowerReference> references = new ArrayList<>();
		try(BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line = reader.readLine();
			while((line = reader.readLine()) != null) {
				if(line.trim().isEmpty()) {
					continue;
				}
				String[] columns = line.split(",");
				references.add(new DevicePowerReference(columns[0], columns[1], columns[2],
						Integer.valueOf(columns[3]).intValue(),
						Double.valueOf(columns[4]).doubleValue(),
						Double.valueOf(columns[5]).doubleValue()));
			}
		}
		return references;
	}

	private static class QstsCaseResult {
		private final QstsResult result;
		private final double baseKva;

		private QstsCaseResult(QstsResult result, double baseKva) {
			this.result = result;
			this.baseKva = baseKva;
		}
	}

	private static class DevicePowerReference {
		private final String caseId;
		private final String deviceClass;
		private final String deviceId;
		private final int step;
		private final double expectedPKw;
		private final double expectedQKvar;

		private DevicePowerReference(String caseId, String deviceClass, String deviceId, int step,
				double expectedPKw, double expectedQKvar) {
			this.caseId = caseId;
			this.deviceClass = deviceClass;
			this.deviceId = deviceId;
			this.step = step;
			this.expectedPKw = expectedPKw;
			this.expectedQKvar = expectedQKvar;
		}

		private String description() {
			return caseId + " " + deviceClass + "." + deviceId + " step " + step;
		}
	}
}
