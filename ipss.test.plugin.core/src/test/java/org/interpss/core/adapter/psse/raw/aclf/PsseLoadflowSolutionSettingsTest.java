package org.interpss.core.adapter.psse.raw.aclf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.fadapter.psse.PsseLoadflowSolutionSettings;
import org.interpss.fadapter.psse.PsseLoadflowSolutionSettings.ApplicationReport;
import org.interpss.fadapter.psse.PsseLoadflowSolutionSettings.ApplicationPolicy;
import org.interpss.fadapter.psse.PsseLoadflowSolutionSettings.MappingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

class PsseLoadflowSolutionSettingsTest {

	@TempDir
	Path tempDir;

	@Test
	void preservesCompleteProfileAndAppliesExactMappings() throws Exception {
		String raw = Files.readString(Path.of("testData/psse/v34/sample_v34.raw"))
				.replace("THRSHZ=0.0001, PQBRAK=0.7, BLOWUP=5.0",
						"THRSHZ=2.9E-4, PQBRAK=0.65, BLOWUP=5.0, FUTUREKEY=7.5")
				.replace("ITMXN=100", "ITMXN=43")
				.replace("TOLN=0.1", "TOLN=0.25")
				.replace("DVLIM=0.99", "DVLIM=0.42")
				.replace("ADJTHR=0.005", "ADJTHR=0.007")
				.replace("MXTPSS=99", "MXTPSS=17")
				.replace("ACTAPS=0", "ACTAPS=1")
				.replace("PHSHFT=0", "PHSHFT=1")
				.replace("DCTAPS=1", "DCTAPS=0")
				.replace("NONDIV=0", "NONDIV=1");
		Path input = tempDir.resolve("settings-v34.raw");
		Files.writeString(input, raw);

		PSSEDirectParser parser = new PSSEDirectParser();
		AclfNetwork network = parser.parse(input.toString());
		PsseLoadflowSolutionSettings settings = parser.getSolutionSettings();

		assertNotNull(settings);
		assertEquals(34, settings.sourceVersion());
		assertEquals(2.9E-4, settings.general().thrshz(), 1.0E-12);
		assertEquals(0.65, settings.general().pqbrak(), 1.0E-12);
		assertEquals(43, settings.newton().itmxn());
		assertEquals(0.25, settings.newton().toln(), 1.0E-12);
		assertEquals(0.007, settings.adjust().adjthr(), 1.0E-12);
		assertEquals(17, settings.adjust().mxtpss());
		assertEquals("FDNS", settings.solver().activity());
		assertEquals(1, settings.solver().nondiv());
		assertEquals("1", settings.rawValues().get("SOLVER").get("NONDIV"),
				"NONDIV should remain available as raw metadata");
		assertEquals("7.5", settings.rawValues().get("GENERAL").get("FUTUREKEY"),
				"unknown keys must remain available to future mappers");
		assertTrue(settings.rawLines().stream().anyMatch(line -> line.startsWith("RATING,")),
				"unmapped system-wide records must remain preserved verbatim");
		assertEquals(2.9E-4, network.getZeroZBranchThreshold(), 1.0E-12);
		assertEquals(0.65, network.getBusLoadLowVoltConfig().getVConstPMin(), 1.0E-12);

		LoadflowAlgorithm algorithm =
				LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network);

		assertEquals(43, algorithm.getMaxIterations());
		assertEquals(0.0025, algorithm.getTolerance(), 1.0E-12,
				"TOLN is in MVA and must be divided by the 100-MVA case base");
		assertTrue(algorithm.isVariableUpdateLimit());
		assertEquals(0.42, algorithm.getDeltaVMagLimit(), 1.0E-12,
				"DVLIM is the maximum relative voltage-magnitude correction");
		assertEquals(Double.MAX_VALUE, algorithm.getDeltaVAngLimit(), 0.0,
				"DVLIM must not introduce an independent angle cap");
		assertEquals(0.007,
				algorithm.getLfAdjAlgo().getVoltageAdjustmentThreshold(), 1.0E-12);
		assertEquals(17,
				algorithm.getLfAdjAlgo().getMaxTapAndShuntAdjustmentIterations());
		assertTrue(algorithm.getLfAdjAlgo().getVoltAdjConfig().isXfrTapControl());
		assertTrue(algorithm.getLfAdjAlgo().getPowerAdjConfig().isPsXfrPControl());
		assertTrue(algorithm.getLfAdjAlgo().getVoltAdjConfig().isHvdcTapControl(),
				"automatic numerical settings must not override study control modes");
		assertTrue(algorithm.getLfAdjAlgo().getVoltAdjConfig().isSwitchedShuntAdjust());
		assertTrue(algorithm.getNetAdjAlgo().isAreaInterchangeControlEnabled());
		assertFalse(algorithm.getNrMethodConfig().isNonDivergent());

		ApplicationReport report = (ApplicationReport) network.getExtraInfo().get(
				PsseLoadflowSolutionSettings.APPLICATION_REPORT_EXTRA_INFO_KEY);
		assertNotNull(report);
		assertTrue(report.mappings().stream().anyMatch(mapping ->
				mapping.field().equals("NEWTON.TOLN")
						&& mapping.status() == MappingStatus.APPLIED));
		assertTrue(report.mappings().stream().anyMatch(mapping ->
				mapping.field().equals("NEWTON.DVLIM")
						&& mapping.status() == MappingStatus.APPLIED));
		assertTrue(report.mappings().stream().anyMatch(mapping ->
				mapping.field().equals("NEWTON.VCTOLV")
						&& mapping.status() == MappingStatus.UNSUPPORTED));
		assertTrue(report.mappings().stream().anyMatch(mapping ->
				mapping.field().equals("GENERAL.FUTUREKEY")
						&& mapping.status() == MappingStatus.UNSUPPORTED),
				"unknown fields must be preserved and reported");

		settings.applyTo(algorithm, network, ApplicationPolicy.SAVED_SOLUTION_REPLAY);
		assertFalse(algorithm.getLfAdjAlgo().getVoltAdjConfig().isHvdcTapControl());
		assertFalse(algorithm.getNetAdjAlgo().isAreaInterchangeControlEnabled());
		assertTrue(algorithm.getNrMethodConfig().isNonDivergent());
	}

	@Test
	void nonBinarySolverModesArePreservedWithoutLossyBooleanMapping() {
		PsseLoadflowSolutionSettings settings = PsseLoadflowSolutionSettings.builder(36)
				.addLine("SOLVER, FNSL, ACTAPS=2, AREAIN=3")
				.build();
		AclfNetwork network = com.interpss.core.CoreObjectFactory.createAclfNetwork();
		LoadflowAlgorithm algorithm =
				LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network);

		ApplicationReport report = settings.applyTo(algorithm, network,
				ApplicationPolicy.SAVED_SOLUTION_REPLAY);

		assertTrue(report.mappings().stream().anyMatch(mapping ->
				mapping.field().equals("SOLVER.ACTAPS")
						&& mapping.status() == MappingStatus.UNSUPPORTED));
		assertTrue(report.mappings().stream().anyMatch(mapping ->
				mapping.field().equals("SOLVER.AREAIN")
						&& mapping.status() == MappingStatus.UNSUPPORTED));
	}
}
