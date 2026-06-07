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
import java.util.List;

import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.OpenDSSStaticDataParser;
import org.interpss.threePhase.qsts.QstsDevicePowerSample;
import org.interpss.threePhase.qsts.QstsMode;
import org.interpss.threePhase.qsts.QstsResult;
import org.interpss.threePhase.qsts.QstsStudy;
import org.interpss.threePhase.qsts.opendss.OpenDSSQstsStudyFactory;
import org.junit.jupiter.api.Test;

public class OpenDssStorageMiniComparisonTest {
	private static final String FEEDER_FOLDER = "testData/feeder/OpenDSSStorageMini";
	private static final String REFERENCE_RESOURCE =
			"opendss-reference/storage-mini-dss-python-storage-reference.csv";
	private static final double TERMINAL_POWER_TOLERANCE_KW = 0.05;

	@Test
	void scheduledStorageTerminalPowersMatchDssPythonMiniCase() throws IOException {
		OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.getStaticNetwork().setBaseKva(1000.0);
		assertTrue(parser.parseFeederData(FEEDER_FOLDER, "Master.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));
		QstsStudy study = OpenDSSQstsStudyFactory.from(parser)
				.setMode(QstsMode.DAILY)
				.setNumberOfSteps(2)
				.setStepSizeHours(1.0)
				.setTolerance(1.0e-8);

		QstsResult result = study.run();

		assertTrue(result.isConverged());
		for(StorageReference reference : readReferences()) {
			double pKw = totalPower(result, reference.stepIndex, reference.storageId, true)
					* parser.getNetworkBaseKva();
			double qKvar = totalPower(result, reference.stepIndex, reference.storageId, false)
					* parser.getNetworkBaseKva();
			assertEquals(reference.expectedPKw, pKw, TERMINAL_POWER_TOLERANCE_KW,
					"Storage P mismatch for " + reference.caseId + " step " + reference.stepIndex);
			assertEquals(reference.expectedQKvar, qKvar, TERMINAL_POWER_TOLERANCE_KW,
					"Storage Q mismatch for " + reference.caseId + " step " + reference.stepIndex);
		}
	}

	private static double totalPower(QstsResult result, int stepIndex, String storageId, boolean activePower) {
		double total = 0.0;
		boolean found = false;
		for(QstsDevicePowerSample sample : result.getGeneratorPowers()) {
			if(sample.getStepIndex() == stepIndex
					&& sample.getDeviceId().equalsIgnoreCase(storageId)) {
				total += activePower ? sample.getP() : sample.getQ();
				found = true;
			}
		}
		assertTrue(found, "Missing storage generator sample " + storageId + " at step " + stepIndex);
		return total;
	}

	private static List<StorageReference> readReferences() throws IOException {
		InputStream stream = OpenDssStorageMiniComparisonTest.class.getClassLoader()
				.getResourceAsStream(REFERENCE_RESOURCE);
		assertNotNull(stream, "Missing resource " + REFERENCE_RESOURCE);
		List<StorageReference> references = new ArrayList<StorageReference>();
		try(BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line = reader.readLine();
			while((line = reader.readLine()) != null) {
				if(line.trim().isEmpty()) {
					continue;
				}
				String[] columns = line.split(",");
				references.add(new StorageReference(columns[0],
						Integer.valueOf(columns[1]).intValue(), columns[2],
						Double.valueOf(columns[3]).doubleValue(),
						Double.valueOf(columns[4]).doubleValue()));
			}
		}
		return references;
	}

	private static class StorageReference {
		private final String caseId;
		private final int stepIndex;
		private final String storageId;
		private final double expectedPKw;
		private final double expectedQKvar;

		private StorageReference(String caseId, int stepIndex, String storageId,
				double expectedPKw, double expectedQKvar) {
			this.caseId = caseId;
			this.stepIndex = stepIndex;
			this.storageId = storageId;
			this.expectedPKw = expectedPKw;
			this.expectedQKvar = expectedQKvar;
		}
	}
}
