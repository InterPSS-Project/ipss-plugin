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
import org.interpss.threePhase.qsts.QstsControlMode;
import org.interpss.threePhase.qsts.QstsDevicePowerSample;
import org.interpss.threePhase.qsts.QstsMode;
import org.interpss.threePhase.qsts.QstsResult;
import org.interpss.threePhase.qsts.QstsStudy;
import org.interpss.threePhase.qsts.opendss.OpenDSSQstsStudyFactory;
import org.junit.jupiter.api.Test;

public class OpenDssInvControlMiniComparisonTest {
	private static final String FEEDER_FOLDER = "testData/feeder/OpenDSSInvControlMini";
	private static final String REFERENCE_RESOURCE =
			"opendss-reference/invcontrol-mini-dss-python-generator-reference.csv";
	private static final String DUTY_REFERENCE_RESOURCE =
			"opendss-reference/invcontrol-duty-qsts-dss-python-generator-reference.csv";

	@Test
	void inverterControlTerminalPowersTrackDssPythonMiniCases() throws IOException {
		for(InvControlReference reference : readReferences()) {
			OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
			parser.getStaticNetwork().setBaseKva(1000.0);
			assertTrue(parser.parseFeederData(FEEDER_FOLDER, reference.caseId + ".dss"));
			assertTrue(parser.calcVoltageBases());
			assertTrue(parser.convertActualValuesToPU(1.0));
			QstsStudy study = OpenDSSQstsStudyFactory.from(parser)
					.setMode(QstsMode.SNAPSHOT)
					.setNumberOfSteps(1)
					.setControlMode(QstsControlMode.STATIC)
					.setMaxControlIterations(20)
					.setTolerance(1.0e-8);

			QstsResult result = study.run();

				assertTrue(result.isConverged(), "QSTS did not converge for " + reference.caseId);
				double pKw = totalPower(result, 0, reference.generatorId, true) * parser.getNetworkBaseKva();
				double qKvar = totalPower(result, 0, reference.generatorId, false) * parser.getNetworkBaseKva();
				assertEquals(reference.expectedPKw, pKw, activeTolerance(reference.caseId),
						"InvControl P mismatch for " + reference.caseId);
				assertEquals(reference.expectedQKvar, qKvar, reactiveTolerance(reference.caseId),
					"InvControl Q mismatch for " + reference.caseId);
		}
	}

	@Test
	void dutyCurveQstsWithEnabledInverterControlTracksDssPythonReferences() throws IOException {
		for(InvControlDutyReference reference : readDutyReferences()) {
			OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
			parser.getStaticNetwork().setBaseKva(1000.0);
			assertTrue(parser.parseFeederData(FEEDER_FOLDER, reference.masterFile));
			assertTrue(parser.calcVoltageBases());
			assertTrue(parser.convertActualValuesToPU(1.0));

			QstsResult result = OpenDSSQstsStudyFactory.from(parser)
					.setMode(QstsMode.DUTY)
					.setStartIndex(0)
					.setNumberOfSteps(3)
					.setStepSizeHours(1.0)
					.setControlMode(QstsControlMode.STATIC)
					.setMaxControlIterations(20)
					.setTolerance(1.0e-8)
					.run();

			assertTrue(result.isConverged(), "QSTS did not converge for " + reference.caseId);
			double pKw = totalPower(result, reference.stepIndex, reference.generatorId, true)
					* parser.getNetworkBaseKva();
			double qKvar = totalPower(result, reference.stepIndex, reference.generatorId, false)
					* parser.getNetworkBaseKva();
			assertEquals(reference.expectedPKw, pKw, 0.15,
					"InvControl duty P mismatch for " + reference.caseId + " step " + reference.stepIndex);
			assertEquals(reference.expectedQKvar, qKvar, 0.15,
					"InvControl duty Q mismatch for " + reference.caseId + " step " + reference.stepIndex);
		}
	}

	private static double totalPower(QstsResult result, int stepIndex, String generatorId, boolean activePower) {
		double total = 0.0;
		boolean found = false;
		for(QstsDevicePowerSample sample : result.getGeneratorPowers()) {
			if(sample.getStepIndex() == stepIndex && sample.getDeviceId().equalsIgnoreCase(generatorId)) {
				total += activePower ? sample.getP() : sample.getQ();
				found = true;
			}
		}
		assertTrue(found, "Missing inverter generator sample " + generatorId);
		return total;
	}

	private static double activeTolerance(String caseId) {
		return "VoltWatt".equals(caseId) ? 3.0 : 0.15;
	}

	private static double reactiveTolerance(String caseId) {
		return "VoltVar".equals(caseId) ? 5.0 : 0.15;
	}

	private static List<InvControlReference> readReferences() throws IOException {
		InputStream stream = OpenDssInvControlMiniComparisonTest.class.getClassLoader()
				.getResourceAsStream(REFERENCE_RESOURCE);
		assertNotNull(stream, "Missing resource " + REFERENCE_RESOURCE);
		List<InvControlReference> references = new ArrayList<InvControlReference>();
		try(BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line = reader.readLine();
			while((line = reader.readLine()) != null) {
				if(line.trim().isEmpty()) {
					continue;
				}
				String[] columns = line.split(",");
				references.add(new InvControlReference(columns[0], columns[1],
						Double.valueOf(columns[2]).doubleValue(),
						Double.valueOf(columns[3]).doubleValue()));
			}
		}
		return references;
	}

	private static List<InvControlDutyReference> readDutyReferences() throws IOException {
		InputStream stream = OpenDssInvControlMiniComparisonTest.class.getClassLoader()
				.getResourceAsStream(DUTY_REFERENCE_RESOURCE);
		assertNotNull(stream, "Missing resource " + DUTY_REFERENCE_RESOURCE);
		List<InvControlDutyReference> references = new ArrayList<InvControlDutyReference>();
		try(BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line = reader.readLine();
			while((line = reader.readLine()) != null) {
				if(line.trim().isEmpty()) {
					continue;
				}
				String[] columns = line.split(",");
				references.add(new InvControlDutyReference(columns[0], columns[1],
						Integer.valueOf(columns[2]).intValue(), columns[3],
						Double.valueOf(columns[4]).doubleValue(),
						Double.valueOf(columns[5]).doubleValue()));
			}
		}
		return references;
	}

	private static class InvControlReference {
		private final String caseId;
		private final String generatorId;
		private final double expectedPKw;
		private final double expectedQKvar;

		private InvControlReference(String caseId, String generatorId, double expectedPKw,
				double expectedQKvar) {
			this.caseId = caseId;
			this.generatorId = generatorId;
			this.expectedPKw = expectedPKw;
			this.expectedQKvar = expectedQKvar;
		}
	}

	private static class InvControlDutyReference {
		private final String caseId;
		private final String masterFile;
		private final int stepIndex;
		private final String generatorId;
		private final double expectedPKw;
		private final double expectedQKvar;

		private InvControlDutyReference(String caseId, String masterFile, int stepIndex,
				String generatorId, double expectedPKw, double expectedQKvar) {
			this.caseId = caseId;
			this.masterFile = masterFile;
			this.stepIndex = stepIndex;
			this.generatorId = generatorId;
			this.expectedPKw = expectedPKw;
			this.expectedQKvar = expectedQKvar;
		}
	}
}
