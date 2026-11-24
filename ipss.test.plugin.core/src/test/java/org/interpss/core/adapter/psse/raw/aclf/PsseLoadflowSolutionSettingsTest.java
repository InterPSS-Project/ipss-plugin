package org.interpss.core.adapter.psse.raw.aclf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.fadapter.psse.PSSEJsonDirectParser;
import org.interpss.fadapter.psse.export.PSSEJsonExporter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.aclf.config.AclfRunConfigRec;
import org.interpss.fadapter.psse.PsseLoadflowSolutionSettings;
import org.interpss.fadapter.psse.PsseLoadflowSolutionSettings.ApplicationReport;
import org.interpss.fadapter.psse.PsseLoadflowSolutionSettings.ApplicationPolicy;
import org.interpss.fadapter.psse.PsseLoadflowSolutionSettings.MappingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetModelType;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.config.ControlInitializationMode;
import com.interpss.core.algo.config.RemoteQControlMode;
import com.interpss.core.algo.impl.solver.DefaultNrSolver;

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
				.replace("TAPLIM=0.0", "TAPLIM=0.035")
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
		assertEquals(0.035, settings.adjust().taplim(), 1.0E-12);
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
		assertEquals(0.035, algorithm.getTapChangeLimit(), 1.0E-12);
		assertEquals(17,
				algorithm.getLfAdjAlgo().getMaxTapAndShuntAdjustmentIterations());
		assertTrue(algorithm.getLfAdjAlgo().getVoltAdjConfig().isXfrTapControl());
		assertTrue(algorithm.getLfAdjAlgo().getPowerAdjConfig().isPsXfrPControl());
		assertFalse(algorithm.getLfAdjAlgo().getVoltAdjConfig().isHvdcTapControl());
		assertTrue(algorithm.getLfAdjAlgo().getVoltAdjConfig().isSwitchedShuntAdjust());
		assertFalse(algorithm.getNetAdjAlgo().isAreaInterchangeControlEnabled());
		assertTrue(algorithm.getNrMethodConfig().isNonDivergent());

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
				mapping.field().equals("ADJUST.TAPLIM")
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
	void rawAreaModeTwoUsesAssignedLoadAreaAndPreservesItInRawx() throws Exception {
		String raw = Files.readString(Path.of("testData/psse/v34/sample_v34.raw"))
				.replace("AREAIN=0", "AREAIN=2")
				.replace("153,'1 ',   1,   1,   1,", "153,'1 ',   1,   2,   1,");
		Path input = tempDir.resolve("area-interchange-v34.raw");
		Files.writeString(input, raw);
		AclfNetwork network = new PSSEDirectParser().parse(input.toString());
		var load = network.getBus("Bus153").getContributeLoad("1");
		assertEquals(2, load.getAreaNumber());
		double tiesOnly = network.areaOutputPower(1, UnitType.PU);

		LoadflowAlgorithm algorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network);
		var config = new AclfRunConfigRec();
		config.savedSolutionReplay = true;
		config.applyTo(algorithm);
		assertTrue(algorithm.getNetAdjAlgo().isAreaInterchangeControlEnabled());
		assertTrue(network.isAreaInterchangeIncludingLoads());
		assertEquals(tiesOnly + 2.0, network.areaOutputPower(1, UnitType.PU), 1e-9);
		ApplicationReport report = (ApplicationReport) network.getExtraInfo().get(
				PsseLoadflowSolutionSettings.APPLICATION_REPORT_EXTRA_INFO_KEY);
		assertTrue(report.mappings().stream().anyMatch(mapping ->
				mapping.field().equals("SOLVER.AREAIN") && mapping.status() == MappingStatus.APPLIED));

		Path output = tempDir.resolve("area-interchange-v34.rawx");
		new PSSEJsonExporter(network).export(output);
		AclfNetwork reimported = new PSSEJsonDirectParser().parse(output.toString());
		assertEquals(2, reimported.getBus("Bus153").getContributeLoad("1").getAreaNumber());
	}

	@Test
	void explicitReplayOptOutPreservesNumericalOverridesAndRestoresControlDefaults() throws Exception {
		var network = new PSSEDirectParser().parse("testData/psse/v34/sample_v34.raw");
		var algorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network);
		assertFalse(algorithm.getLfAdjAlgo().getVoltAdjConfig().isXfrTapControl());
		algorithm.setTolerance(0.004);
		AclfRunConfigRec.fromJson("{\"schemaVersion\":1,\"savedSolutionReplay\":false}").applyTo(algorithm);
		assertTrue(algorithm.getLfAdjAlgo().getVoltAdjConfig().isXfrTapControl());
		assertEquals(0.004, algorithm.getTolerance(), 0.0);
		assertEquals(ControlInitializationMode.COLD_START,
				algorithm.getLfAdjAlgo().getLimitCtrlConfig().getControlInitializationMode());
	}

	@Test
	void legacyRawAndRawxInheritTheSameAcDefaultsWithoutSolverRecords() throws Exception {
		var raw = new PSSEDirectParser().parse("testData/adpter/psse/v33/Kundur_2area_LCC_HVDC.raw");
		var path = tempDir.resolve("legacy.rawx");
		new PSSEJsonExporter(raw).export(path);
		var rawx = new PSSEJsonDirectParser().parse(path.toString());
		for (var network : java.util.List.of(raw, rawx)) {
			// A second algorithm can be created for a network already marked for ZBR.
			for (int attempt = 0; attempt < 2; attempt++) {
				var algorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network);
				assertEquals(AclfNetModelType.ZBR_MODEL, network.getAclfNetModelType());
				assertEquals(ControlInitializationMode.SAVED_SOLUTION_REPLAY,
						algorithm.getLfAdjAlgo().getLimitCtrlConfig().getControlInitializationMode());
				assertEquals(RemoteQControlMode.INNER_PQV, algorithm.getLfAdjAlgo().getRemoteQControlMode());
				assertFalse(algorithm.getNetAdjAlgo().isAreaInterchangeControlEnabled());
			}
		}
	}

	@Test
	void explicitRegularNetworkUsesItsElectricalSolver() throws Exception {
		var network = new PSSEDirectParser().parse("testData/psse/v34/sample_v34.raw");
		var algorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network);
		AclfRunConfigRec.fromJson("{\"schemaVersion\":1,\"aclfNetModelType\":\"REGULAR\"}").applyTo(algorithm);
		assertEquals(AclfNetModelType.REGULAR, network.getAclfNetModelType());
		assertEquals(DefaultNrSolver.class, algorithm.getLfCalculator().getNrSolver().getClass());
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
