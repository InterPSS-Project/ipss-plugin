package org.interpss.core.adapter.psse.json.aclf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.interpss.CorePluginTestSetup;
import org.interpss.dep.QA.compare.aclf.AclfBranchDataComparator;
import org.interpss.dep.QA.compare.aclf.AclfBusDataComparator;
import org.interpss.dep.QA.compare.aclf.AclfNetDataComparator;
import org.interpss.dep.QA.compare.aclf.AclfNetModelComparator;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.fadapter.psse.PSSEJsonDirectParser;
import org.interpss.fadapter.psse.PsseLoadflowSolutionSettings;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.psse.export.PSSEJsonExporter;
import org.interpss.fadapter.psse.export.PSSERawExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.AclfAdjustObjectFactory;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adj.BusBranchControlType;
import com.interpss.core.aclf.adj.RemoteQBus;
import com.interpss.core.aclf.facts.StaticVarCompensator;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.hvdc.ConverterType;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcLine2TVSC;
import com.interpss.core.aclf.hvdc.ThyConverter;
import com.interpss.core.aclf.hvdc.HvdcControlSide;
import com.interpss.core.aclf.hvdc.HvdcControlMode;
import com.interpss.core.aclf.hvdc.HvdcOperationMode;
import com.interpss.core.aclf.hvdc.VSCAcControlMode;
import com.interpss.core.aclf.hvdc.VSCConverter;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.algo.LoadflowAlgorithmInitializer;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.OriginalDataFormat;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.apache.commons.math3.complex.Complex;

import com.interpss.core.CoreObjectFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class PSSEJsonExporterRoundTripTest extends CorePluginTestSetup {

	private static final double TOL = 1.0E-8;

	@TempDir
	Path tempDir;

	@Test
	public void ieee9RawxExportsAndParsesToEquivalentNetwork() throws Exception {
		AclfNetwork original = new PSSEJsonDirectParser()
				.parse("testData/adpter/psse/json/ieee9.rawx");

		Path exported = tempDir.resolve("ieee9-roundtrip.rawx");
		new PSSEJsonExporter(original).export(exported);

		AclfNetwork roundTrip = new PSSEJsonDirectParser().parse(exported.toString());

		AclfNetModelComparator comparator = new AclfNetModelComparator(
				new AclfNetDataComparator(TOL),
				new AclfBusDataComparator(TOL),
				new AclfBranchDataComparator(TOL));
		assertTrue(comparator.compare(original, roundTrip), comparator::getMsg);
	}

	@ParameterizedTest
	@CsvSource({
			"30,../ipss.plugin.core/testData/psse/v30/IEEE39bus_v30.raw",
			"31,testData/psse/v31/sample_v31.raw",
			"32,testData/psse/v32/sample_v32.raw",
			"33,testData/psse/v33/sample_v33.raw",
			"34,testData/psse/v34/sample_v34.raw",
			"35,testData/psse/v35/sample_v35.raw",
			"36,testData/psse/v36/sample_v36.raw"
	})
	public void rawExportsAndParsesToEquivalentNetworkByVersion(
			int version, String rawFile) throws Exception {
		AclfNetwork original = new PSSEDirectParser(version).parse(rawFile);

		Path exported = tempDir.resolve("sample_v" + version + "-roundtrip.raw");
		new PSSERawExporter(original, version).export(exported);

		AclfNetwork roundTrip = new PSSEDirectParser(version).parse(exported.toString());

		AclfNetModelComparator comparator = new AclfNetModelComparator(
				new AclfNetDataComparator(TOL),
				new AclfBusDataComparator(TOL),
				new AclfBranchDataComparator(TOL));
		assertTrue(comparator.compare(original, roundTrip),
				() -> rawFile + " -> " + exported + comparator.getMsg());
	}

	@Test
	public void rawV35ExportsAsV36AndParsesToEquivalentNetwork() throws Exception {
		AclfNetwork original = new PSSEDirectParser(35)
				.parse("testData/psse/v35/sample_v35.raw");

		Path exported = tempDir.resolve("sample_v35-to-v36.raw");
		new PSSERawExporter(original, 36).export(exported);

		AclfNetwork roundTrip = new PSSEDirectParser(36).parse(exported.toString());

		AclfNetModelComparator comparator = new AclfNetModelComparator(
				new AclfNetDataComparator(TOL),
				new AclfBusDataComparator(TOL),
				new AclfBranchDataComparator(TOL));
		assertTrue(comparator.compare(original, roundTrip),
				() -> "testData/psse/v35/sample_v35.raw -> " + exported + comparator.getMsg());
	}

	@ParameterizedTest
	@CsvSource({
		"false,true,true,4",
		"true,false,true,2",
		"true,true,false,3"
	})
	public void rawExportPreservesSingleOfflineThreeWinding(
			boolean winding1, boolean winding2, boolean winding3,
			int expectedStatus) throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.setNetworkInfo("three-winding", "three-winding", 100000.0,
				OriginalDataFormat.PSSE);
		builder.addBus("Bus1", "B1", 1L, 230000.0, 1.0, 0.0,
				null, null, null);
		builder.addBus("Bus2", "B2", 2L, 115000.0, 1.0, 0.0,
				null, null, null);
		builder.addBus("Bus3", "B3", 3L, 13800.0, 1.0, 0.0,
				null, null, null);
		builder.addXformer3W(
				"Bus1", "Bus2", "Bus3", "1",
				new Complex(0.0, 0.1), new Complex(0.0, 0.2),
				new Complex(0.0, 0.15),
				1.0, 1.0, 1.0, Complex.ZERO, 1.0, 0.0,
				!winding1, !winding2, !winding3,
				false, 0.0, 0.0, 0.0, true);
		builder.finalizeNetwork();

		Path exported = tempDir.resolve("three-winding-stat-"
				+ expectedStatus + ".raw");
		new PSSERawExporter(builder.getNetwork(), 35).export(exported);
		AclfNetwork roundTrip = new PSSEDirectParser(35)
				.parse(exported.toString());
		Aclf3WBranch imported = (Aclf3WBranch) roundTrip.getSpecialBranchList()
				.stream().filter(Aclf3WBranch.class::isInstance)
				.findFirst().orElseThrow();

		assertEquals(winding1, imported.getFromAclfBranch().isStatus());
		assertEquals(winding2, imported.getToAclfBranch().isStatus());
		assertEquals(winding3, imported.getTertAclfBranch().isStatus());
		assertTrue(Files.readString(exported).contains("," + expectedStatus
				+ ",1,1.0"));
	}

	@Test
	public void solvedRawDoesNotSynthesizeGeneratorsForVscTerminalState()
			throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.setNetworkInfo("vsc", "vsc", 100000.0,
				OriginalDataFormat.PSSE);
		builder.addBus("Bus1", "Rectifier", 1L, 230000.0, 1.0, 0.0,
				null, null, null);
		builder.addBus("Bus2", "Inverter", 2L, 230000.0, 1.0, 0.0,
				null, null, null);
		builder.addBus("Bus3", "Pilot", 3L, 230000.0, 1.0, 0.0,
				null, null, null);
		HvdcLine2TVSC<AclfBus> vsc = builder.addHvdcLine2TVSC(
				"VSC1", "VSC", "Bus1", "Bus2", true, 5.0, 300.0);
		builder.setVSCConverter((VSCConverter) vsc.getRecConverter(), "Bus1",
				HvdcControlMode.DC_VOLTAGE, 500.0,
				VSCAcControlMode.AC_VOLTAGE, 1.02,
				300.0, 100.0, -100.0, "Bus3", 100.0);
		builder.setVSCConverter((VSCConverter) vsc.getInvConverter(), "Bus2",
				HvdcControlMode.DC_POWER, 200.0,
				VSCAcControlMode.AC_REACTIVE_POWER, 0.0,
				300.0, 100.0, -100.0, null, 0.0);
		vsc.getRecConverter().setLossA(1600.0);
		vsc.getRecConverter().setLossB(3.4);
		vsc.getRecConverter().setMinimumLoss(1110.0);
		vsc.getRecConverter().setAcCurrentRating(1085.0);
		vsc.getInvConverter().setLossA(900.0);
		vsc.getInvConverter().setLossB(2.1);
		vsc.getInvConverter().setMinimumLoss(750.0);
		vsc.getInvConverter().setAcCurrentRating(1032.0);
		builder.finalizeNetwork();

		Path exported = tempDir.resolve("vsc-without-synthetic-generators.raw");
		new PSSERawExporter(builder.getNetwork(), 35, true).export(exported);
		Complex rectifierTerminalPower = vsc.getRecConverter().powerIntoConverter();
		Complex inverterTerminalPower = vsc.getInvConverter().powerIntoConverter();
		JsonObject vscSection = new PSSEJsonExporter(builder.getNetwork(), true)
				.export().getAsJsonObject("network").getAsJsonObject("vscdc");
		assertEquals(rectifierTerminalPower.getReal(),
				rawxNumber(vscSection, 0, "ipss_p_into_converter_1_pu"), TOL);
		assertEquals(rectifierTerminalPower.getImaginary(),
				rawxNumber(vscSection, 0, "ipss_q_into_converter_1_pu"), TOL);
		assertEquals(inverterTerminalPower.getReal(),
				rawxNumber(vscSection, 0, "ipss_p_into_converter_2_pu"), TOL);
		assertEquals(inverterTerminalPower.getImaginary(),
				rawxNumber(vscSection, 0, "ipss_q_into_converter_2_pu"), TOL);
		String rawText = Files.readString(exported);
		assertTrue(rawText.contains("/* [IPSS_SOLVED_STATE, terminal=1,"));
		assertTrue(rawText.contains("p_into_converter_pu="));
		assertTrue(rawText.contains("q_into_converter_pu="));
		AclfNetwork roundTrip = new PSSEDirectParser(35)
				.parse(exported.toString());

		assertTrue(roundTrip.getBus("Bus1").getContributeGenList().isEmpty());
		assertTrue(roundTrip.getBus("Bus2").getContributeGenList().isEmpty());
		HvdcLine2TVSC<?> imported = (HvdcLine2TVSC<?>) roundTrip
				.getSpecialBranchList().stream()
				.filter(HvdcLine2TVSC.class::isInstance)
				.findFirst().orElseThrow();
		assertEquals(1600.0, imported.getRecConverter().getLossA(), TOL);
		assertEquals(3.4, imported.getRecConverter().getLossB(), TOL);
		assertEquals(1110.0, imported.getRecConverter().getMinimumLoss(), TOL);
		assertEquals(900.0, imported.getInvConverter().getLossA(), TOL);
		assertEquals(2.1, imported.getInvConverter().getLossB(), TOL);
		assertEquals(750.0, imported.getInvConverter().getMinimumLoss(), TOL);
		assertEquals(1085.0, imported.getRecConverter().getAcCurrentRating(), TOL);
		assertEquals(1032.0, imported.getInvConverter().getAcCurrentRating(), TOL);
		assertEquals(VSCAcControlMode.AC_POWER_FACTOR,
				imported.getRecConverter().getAcControlMode());
		assertEquals(0.0,
				imported.getRecConverter()
						.calPowerIntoNetOnConverterBase(UnitType.mVA)
						.getImaginary(), TOL);

		Path exportedRawx = tempDir.resolve("vsc-with-losses.rawx");
		new PSSEJsonExporter(builder.getNetwork(), true).export(exportedRawx);
		AclfNetwork rawxRoundTrip = new PSSEJsonDirectParser()
				.parse(exportedRawx.toString());
		HvdcLine2TVSC<?> rawxImported = (HvdcLine2TVSC<?>) rawxRoundTrip
				.getSpecialBranchList().stream()
				.filter(HvdcLine2TVSC.class::isInstance)
				.findFirst().orElseThrow();
		assertEquals(1600.0, rawxImported.getRecConverter().getLossA(), TOL);
		assertEquals(3.4, rawxImported.getRecConverter().getLossB(), TOL);
		assertEquals(1110.0, rawxImported.getRecConverter().getMinimumLoss(), TOL);
		assertEquals(1085.0, rawxImported.getRecConverter().getAcCurrentRating(), TOL);
		assertEquals(900.0, rawxImported.getInvConverter().getLossA(), TOL);
		assertEquals(2.1, rawxImported.getInvConverter().getLossB(), TOL);
		assertEquals(750.0, rawxImported.getInvConverter().getMinimumLoss(), TOL);
		assertEquals(1032.0, rawxImported.getInvConverter().getAcCurrentRating(), TOL);
	}

	@Test
	public void solvedRawPreservesLimitedLocalVscReactiveOutput()
			throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.setNetworkInfo("limited-vsc", "limited-vsc", 100000.0,
				OriginalDataFormat.PSSE);
		builder.addBus("Bus1", "Rectifier", 1L, 230000.0, 1.03, 0.0,
				null, null, null);
		builder.addBus("Bus2", "Inverter", 2L, 230000.0, 1.0, 0.0,
				null, null, null);
		HvdcLine2TVSC<AclfBus> vsc = builder.addHvdcLine2TVSC(
				"VSC1", "VSC", "Bus1", "Bus2", true, 5.0, 300.0);
		builder.setVSCConverter((VSCConverter) vsc.getRecConverter(), "Bus1",
				HvdcControlMode.DC_POWER, 5.0,
				VSCAcControlMode.AC_VOLTAGE, 1.02,
				300.0, 95.0, -155.0, null, 100.0);
		builder.setVSCConverter((VSCConverter) vsc.getInvConverter(), "Bus2",
				HvdcControlMode.DC_VOLTAGE, 500.0,
				VSCAcControlMode.AC_REACTIVE_POWER, 0.0,
				300.0, 100.0, -100.0, null, 0.0);
		builder.finalizeNetwork();
		AclfBus terminal = builder.getNetwork().getBus("Bus1");
		terminal.setGenCode(AclfGenCode.GEN_PQ);
		terminal.setGenP(0.05);
		terminal.setGenQ(-1.55);

		Path exported = tempDir.resolve("limited-vsc-solved.raw");
		new PSSERawExporter(builder.getNetwork(), 35, true).export(exported);
		AclfNetwork roundTrip = new PSSEDirectParser(35).parse(exported.toString());
		HvdcLine2TVSC<?> imported = (HvdcLine2TVSC<?>) roundTrip
				.getSpecialBranchList().stream()
				.filter(HvdcLine2TVSC.class::isInstance)
				.findFirst().orElseThrow();
		VSCConverter<?> importedRectifier = imported.getRecConverter();

		assertEquals(VSCAcControlMode.AC_POWER_FACTOR,
				importedRectifier.getAcControlMode());
		assertEquals(5.0 / Math.hypot(5.0, 155.0),
				importedRectifier.getAcSetPoint(), TOL);
		assertEquals(-155.0,
				importedRectifier.calPowerIntoNetOnConverterBase(UnitType.mVA)
						.getImaginary(), TOL);
		assertEquals(95.0, importedRectifier.getQMvarLimit().getMax(), TOL);
		assertEquals(-155.0, importedRectifier.getQMvarLimit().getMin(), TOL);
	}

	@Test
	public void rawV36PreservesSystemWideSolutionSettings() throws Exception {
		AclfNetwork original = new PSSEDirectParser(36)
				.parse("../ipss.plugin.core/testData/psse/v36/"
						+ "Texas2k_series24_case1_2016summerPeak_v36.RAW");
		PsseLoadflowSolutionSettings originalSettings = (PsseLoadflowSolutionSettings)
				original.getExtraInfo().get(
						LoadflowAlgorithmInitializer.NETWORK_EXTRA_INFO_KEY);

		Path exported = tempDir.resolve("texas2k-settings-v36.raw");
		new PSSERawExporter(original, 36).export(exported);
		AclfNetwork roundTrip = new PSSEDirectParser(36).parse(exported.toString());
		PsseLoadflowSolutionSettings roundTripSettings = (PsseLoadflowSolutionSettings)
				roundTrip.getExtraInfo().get(
						LoadflowAlgorithmInitializer.NETWORK_EXTRA_INFO_KEY);

		assertEquals(originalSettings.rawLines(), roundTripSettings.rawLines());
		String raw = Files.readString(exported);
		assertTrue(raw.contains("NEWTON, ITMXN=20, ACCN=1.0, TOLN=0.1"));
		assertTrue(raw.contains("ADJUST, ADJTHR=0.005, ACCTAP=1.0"));

		Path exportedRawx = tempDir.resolve("texas2k-settings.rawx");
		new PSSEJsonExporter(original).export(exportedRawx);
		AclfNetwork rawxRoundTrip = new PSSEJsonDirectParser()
				.parse(exportedRawx.toString());
		PsseLoadflowSolutionSettings rawxSettings =
				(PsseLoadflowSolutionSettings) rawxRoundTrip.getExtraInfo().get(
						LoadflowAlgorithmInitializer.NETWORK_EXTRA_INFO_KEY);
		assertEquals(originalSettings.rawLines(), rawxSettings.rawLines());
		assertEquals(originalSettings.sourceVersion(), rawxSettings.sourceVersion());
		assertEquals(originalSettings.general().thrshz(),
				rawxRoundTrip.getZeroZBranchThreshold(), TOL);
		assertEquals(originalSettings.general().pqbrak(),
				rawxRoundTrip.getBusLoadLowVoltConfig().getVConstPMin(), TOL);
	}

	@Test
	public void solvedRawGeneratorRecordsMatchSolvedBusGeneration() throws Exception {
		AclfNetwork solved = new PSSEDirectParser(35)
				.parse("testData/psse/v35/ieee9_v35.raw");
		assertTrue(LoadflowAlgoObjectFactory.createLoadflowAlgorithm(solved).loadflow());

		Path exported = tempDir.resolve("ieee9-solved-v35.raw");
		new PSSERawExporter(solved, 35, true).export(exported);
		AclfNetwork roundTrip = new PSSEDirectParser(35).parse(exported.toString());

		for (AclfBus solvedBus : solved.getBusList()) {
			AclfBus roundTripBus = roundTrip.getBus(solvedBus.getId());
			double solvedP = solvedBus.isSwing()
					? solvedBus.calNetGenResults().getReal()
					: solvedBus.getGenP();
			double solvedQ = solvedBus.isSwing() || solvedBus.isGenPV()
					? solvedBus.calNetGenResults().getImaginary()
					: solvedBus.getGenQ();
			double exportedP = roundTripBus.getContributeGenList().stream()
					.filter(AclfGen::isActive)
					.mapToDouble(gen -> gen.getGen().getReal())
					.sum();
			double exportedQ = roundTripBus.getContributeGenList().stream()
					.filter(AclfGen::isActive)
					.mapToDouble(gen -> gen.getGen().getImaginary())
					.sum();
			assertEquals(solvedP, exportedP, TOL, solvedBus.getId() + " active generator P");
			assertEquals(solvedQ, exportedQ, TOL, solvedBus.getId() + " active generator Q");
		}
	}

	@Test
	public void solvedRawReconcilesRuntimePqBusQWithoutChangingGeneratorP() throws Exception {
		AclfNetwork solved = new PSSEDirectParser(35)
				.parse("testData/psse/v35/ieee9_v35.raw");
		assertTrue(LoadflowAlgoObjectFactory.createLoadflowAlgorithm(solved).loadflow());
		AclfBus runtimePqBus = solved.getBusList().stream()
				.filter(AclfBus::isGenPV)
				.findFirst()
				.orElseThrow();
		AclfGen sourceGenerator = runtimePqBus.getContributeGenList().stream()
				.filter(AclfGen::isActive)
				.findFirst()
				.orElseThrow();
		double sourceP = sourceGenerator.getGen().getReal();
		LimitType sourceQLimit = sourceGenerator.getQGenLimit();
		double solvedQ = sourceGenerator.getGen().getImaginary() + 0.0125;
		runtimePqBus.setGenCode(AclfGenCode.GEN_PQ);
		runtimePqBus.setGenQ(solvedQ);

		Path exported = tempDir.resolve("ieee9-runtime-pq-solved-v35.raw");
		new PSSERawExporter(solved, 35, true).export(exported);
		AclfNetwork roundTrip = new PSSEDirectParser(35).parse(exported.toString());
		AclfBus importedBus = roundTrip.getBus(runtimePqBus.getId());
		AclfGen importedGenerator = importedBus.getContributeGenList().stream()
				.filter(AclfGen::isActive)
				.findFirst()
				.orElseThrow();

		assertEquals(sourceP, importedGenerator.getGen().getReal(), TOL);
		assertEquals(solvedQ, importedBus.getContributeGenList().stream()
				.filter(AclfGen::isActive)
				.mapToDouble(gen -> gen.getGen().getImaginary())
				.sum(), TOL);
		assertEquals(sourceQLimit.getMax(), importedGenerator.getQGenLimit().getMax(), TOL);
		assertEquals(sourceQLimit.getMin(), importedGenerator.getQGenLimit().getMin(), TOL);
	}

	@Test
	public void solvedRawPreservesEnabledGeneratorRemoteVoltageControl() throws Exception {
		AclfNetwork solved = new PSSEDirectParser(35)
				.parse("testData/psse/v35/ieee9_v35.raw");
		AclfBus generatorBus = solved.getBusList().stream()
				.filter(AclfBus::isGenPV)
				.findFirst()
				.orElseThrow();
		AclfBus remoteBus = solved.getBusList().stream()
				.filter(bus -> bus != generatorBus && bus.isActive()
						&& !bus.isSwing() && !bus.isGenPV())
				.findFirst()
				.orElseThrow();
		generatorBus.getContributeGenList().stream()
				.filter(AclfGen::isActive)
				.forEach(gen -> gen.setRemoteVControlBusId(remoteBus.getId()));
		generatorBus.setGenCode(AclfGenCode.GEN_PQ);
		RemoteQBus remoteControl = AclfAdjustObjectFactory.createRemoteQBus(
				generatorBus, BusBranchControlType.BUS_VOLTAGE, remoteBus.getId()).get();
		remoteControl.setControlStatus(true);
		remoteControl.setVSpecified(generatorBus.getDesiredVoltMag());

		Path exported = tempDir.resolve("ieee9-remote-q-solved-v35.raw");
		new PSSERawExporter(solved, 35, true).export(exported);
		AclfNetwork roundTrip = new PSSEDirectParser(35).parse(exported.toString());
		AclfBus importedBus = roundTrip.getBus(generatorBus.getId());

		assertTrue(importedBus.isRemoteQBus());
		assertTrue(importedBus.getRemoteQBus().isControlStatus());
		assertEquals(remoteBus.getId(), importedBus.getRemoteQBus().getRemoteBus().getId());
	}

	@Test
	public void solvedRawKeepsNullLimitGeneratorQFixed() throws Exception {
		AclfNetwork solved = new PSSEDirectParser(35)
				.parse("testData/psse/v35/ieee9_v35.raw");
		assertTrue(LoadflowAlgoObjectFactory.createLoadflowAlgorithm(solved).loadflow());
		AclfBus runtimePqBus = solved.getBusList().stream()
				.filter(AclfBus::isGenPV)
				.findFirst()
				.orElseThrow();
		AclfGen regulatingGenerator = runtimePqBus.getContributeGenList().stream()
				.filter(AclfGen::isActive)
				.findFirst()
				.orElseThrow();
		double fixedQ = 0.01;
		AclfGen fixedGenerator = CoreObjectFactory.createAclfGen("FIXED");
		fixedGenerator.setStatus(true);
		fixedGenerator.setGen(new Complex(0.0, fixedQ));
		fixedGenerator.setMvaBase(solved.getBaseMva());
		runtimePqBus.getContributeGenList().add(fixedGenerator);
		assertTrue(fixedGenerator.getQGenLimit() == null);

		double targetQ = regulatingGenerator.getGen().getImaginary() + fixedQ + 0.0125;
		runtimePqBus.setGenCode(AclfGenCode.GEN_PQ);
		runtimePqBus.setGenQ(targetQ);

		Path exported = tempDir.resolve("ieee9-null-q-limit-solved-v35.raw");
		new PSSERawExporter(solved, 35, true).export(exported);
		AclfBus importedBus = new PSSEDirectParser(35).parse(exported.toString())
				.getBus(runtimePqBus.getId());
		AclfGen importedFixed = importedBus.getContributeGenList().stream()
				.filter(gen -> "FIXED".equals(gen.getId()))
				.findFirst()
				.orElseThrow();

		assertEquals(fixedQ, importedFixed.getGen().getImaginary(), TOL);
		assertEquals(targetQ, importedBus.getContributeGenList().stream()
				.filter(AclfGen::isActive)
				.mapToDouble(gen -> gen.getGen().getImaginary())
				.sum(), TOL);
	}

	@Test
	public void solvedRawRestoresZeroRangeGeneratorQToItsFixedLimit() throws Exception {
		AclfNetwork solved = new PSSEDirectParser(35)
				.parse("testData/psse/v35/ieee9_v35.raw");
		assertTrue(LoadflowAlgoObjectFactory.createLoadflowAlgorithm(solved).loadflow());
		AclfBus runtimePqBus = solved.getBusList().stream()
				.filter(AclfBus::isGenPV)
				.findFirst()
				.orElseThrow();
		AclfGen regulatingGenerator = runtimePqBus.getContributeGenList().stream()
				.filter(AclfGen::isActive)
				.findFirst()
				.orElseThrow();
		AclfGen fixedGenerator = CoreObjectFactory.createAclfGen("FIXED_ZERO");
		fixedGenerator.setStatus(true);
		fixedGenerator.setGen(new Complex(0.0, -0.04));
		fixedGenerator.setQGenLimit(new LimitType(0.0, 0.0));
		fixedGenerator.setMvaBase(solved.getBaseMva());
		runtimePqBus.getContributeGenList().add(fixedGenerator);

		double targetQ = regulatingGenerator.getGen().getImaginary() + 0.0125;
		runtimePqBus.setGenCode(AclfGenCode.GEN_PQ);
		runtimePqBus.setGenQ(targetQ);

		Path exported = tempDir.resolve("ieee9-fixed-zero-q-solved-v35.raw");
		new PSSERawExporter(solved, 35, true).export(exported);
		AclfBus importedBus = new PSSEDirectParser(35).parse(exported.toString())
				.getBus(runtimePqBus.getId());
		AclfGen importedFixed = importedBus.getContributeGenList().stream()
				.filter(gen -> "FIXED_ZERO".equals(gen.getId()))
				.findFirst()
				.orElseThrow();

		assertEquals(0.0, importedFixed.getGen().getImaginary(), TOL);
		assertEquals(targetQ, importedBus.getContributeGenList().stream()
				.filter(AclfGen::isActive)
				.mapToDouble(gen -> gen.getGen().getImaginary())
				.sum(), TOL);
	}

	@Test
	public void rawPreservesHighPrecisionBusVoltageMagnitudeAndAngle() throws Exception {
		AclfNetwork original = new PSSEDirectParser(35)
				.parse("testData/psse/v35/ieee9_v35.raw");
		AclfBus bus = original.getBusList().get(0);
		double expectedMagnitude = 1.012345678901234;
		double expectedAngleDegrees = -12.345678901234;
		bus.setVoltageMag(expectedMagnitude);
		bus.setVoltageAng(Math.toRadians(expectedAngleDegrees));

		Path exported = tempDir.resolve("ieee9-high-precision-voltage-v35.raw");
		new PSSERawExporter(original, 35).export(exported);
		String busPrefix = bus.getNumber() + ",";
		String busRecord = Files.readAllLines(exported).stream()
				.filter(line -> line.startsWith(busPrefix))
				.findFirst()
				.orElseThrow();
		String[] fields = busRecord.split(",");

		assertEquals(Double.toString(expectedMagnitude), fields[7]);
		assertEquals(Double.toString(expectedAngleDegrees), fields[8]);

		AclfBus importedBus = new PSSEDirectParser(35).parse(exported.toString())
				.getBus(bus.getId());
		assertEquals(expectedMagnitude, importedBus.getVoltageMag(), 1.0e-15);
		assertEquals(expectedAngleDegrees,
				Math.toDegrees(importedBus.getVoltageAng()), 1.0e-13);
	}

	@Test
	public void solvedRawSerializesLimitViolatingPvBusAsPqActiveSet() throws Exception {
		AclfNetwork solved = new PSSEDirectParser(35)
				.parse("testData/psse/v35/ieee9_v35.raw");
		assertTrue(LoadflowAlgoObjectFactory.createLoadflowAlgorithm(solved).loadflow());
		AclfBus violatedPvBus = solved.getBusList().stream()
				.filter(AclfBus::isGenPV)
				.findFirst()
				.orElseThrow();
		double solvedQ = violatedPvBus.calNetGenResults().getImaginary();
		violatedPvBus.getContributeGenList().stream()
				.filter(AclfGen::isActive)
				.forEach(gen -> gen.setQGenLimit(new LimitType(
						solvedQ - 0.01, solvedQ - 0.02)));

		Path exported = tempDir.resolve("ieee9-violated-pv-solved-v35.raw");
		new PSSERawExporter(solved, 35, true).export(exported);
		AclfNetwork roundTrip = new PSSEDirectParser(35).parse(exported.toString());
		AclfBus importedBus = roundTrip.getBus(violatedPvBus.getId());

		assertTrue(importedBus.isGenPQ());
		assertEquals(solvedQ, importedBus.getContributeGenList().stream()
				.filter(AclfGen::isActive)
				.mapToDouble(gen -> gen.getGen().getImaginary())
				.sum(), TOL);
	}

	@Test
	public void rawV34KeepsSwitchedShuntsInTheirOwnSection() throws Exception {
		AclfNetwork original = new PSSEDirectParser(34)
				.parse("testData/psse/v34/sample_v34.raw");
		AclfBus bus = original.getBusList().get(0);
		SwitchedShunt shunt = AclfAdjustObjectFactory.createSwitchedShunt(bus);
		shunt.setId("1");
		shunt.setStatus(true);
		shunt.setControlMode(AclfAdjustControlMode.FIXED);
		shunt.setRemoteBusBranchId(bus.getId());
		shunt.setRemoteBus(bus);
		shunt.setBInit(-0.25);

		Path exported = tempDir.resolve("sample-v34-switched-shunt.raw");
		new PSSERawExporter(original, 34).export(exported);
		AclfNetwork roundTrip = new PSSEDirectParser(34).parse(exported.toString());

		AclfBus importedBus = roundTrip.getBus(bus.getId());
		assertTrue(importedBus.isSwitchedShunt());
		assertEquals(1, importedBus.getSwitchedShuntList().size());
		assertEquals(-0.25,
				importedBus.getSwitchedShuntList().get(0).getBActual(), TOL);
	}

	@Test
	public void solvedRawWritesBlockedSwitchedShuntAsFixedMode() throws Exception {
		AclfNetwork original = new PSSEDirectParser(34)
				.parse("testData/psse/v34/sample_v34.raw");
		AclfBus bus = original.getBusList().get(0);
		SwitchedShunt shunt = AclfAdjustObjectFactory.createSwitchedShunt(bus);
		shunt.setId("1");
		shunt.setStatus(true);
		shunt.setControlMode(AclfAdjustControlMode.DISCRETE);
		shunt.setRemoteBusBranchId(bus.getId());
		shunt.setRemoteBus(bus);
		shunt.setBActual(-0.25);
		shunt.setAdjustStatus(false);

		Path exported = tempDir.resolve("sample-v34-blocked-switched-shunt.raw");
		new PSSERawExporter(original, 34, true).export(exported);
		SwitchedShunt imported = new PSSEDirectParser(34).parse(exported.toString())
				.getBus(bus.getId()).getSwitchedShuntList().get(0);

		assertEquals(AclfAdjustControlMode.FIXED, imported.getControlMode());
		assertEquals(-0.25, imported.getBActual(), TOL);
	}

	@Test
	public void solvedRawFixesOnlySwitchedShuntOutsideSolvedTolerance() throws Exception {
		AclfNetwork original = new PSSEDirectParser(34)
				.parse("testData/psse/v34/sample_v34.raw");
		AclfBus bus = original.getBusList().get(0);
		bus.setVoltageMag(1.0);
		SwitchedShunt shunt = AclfAdjustObjectFactory.createSwitchedShunt(bus);
		shunt.setId("UT");
		shunt.setStatus(true);
		shunt.setControlMode(AclfAdjustControlMode.DISCRETE);
		shunt.setRemoteBusBranchId(bus.getId());
		shunt.setRemoteBus(bus);
		shunt.setDesiredControlRange(new LimitType(1.01, 1.01));
		shunt.setBActual(-0.25);
		shunt.setAdjustStatus(true);
		original.getExtraInfo().put(
				LoadflowAlgorithm.SOLVED_VOLTAGE_CONTROL_TOLERANCE_EXTRA_INFO_KEY,
				0.005);

		Path exported = tempDir.resolve("sample-v34-unsettled-switched-shunt.raw");
		new PSSERawExporter(original, 34, true).export(exported);
		SwitchedShunt imported = new PSSEDirectParser(34).parse(exported.toString())
				.getBus(bus.getId()).getSwitchedShuntList().stream()
				.filter(candidate -> Math.abs(candidate.getBActual() + 0.25) < TOL)
				.findFirst().orElseThrow();

		assertEquals(AclfAdjustControlMode.FIXED, imported.getControlMode());
		assertEquals(-0.25, imported.getBActual(), TOL);
	}

	@Test
	public void rawV34PreservesTransformerImpedanceCorrectionTables() throws Exception {
		AclfNetwork original = new PSSEDirectParser(34)
				.parse("testData/psse/v34/sample_v34.raw");
		AclfBranch originalBranch = original.getBranchList().stream()
				.filter(branch -> branch.getXfrZTableNumber() > 0)
				.filter(branch -> original.getXfrZTableEntry(branch.getXfrZTableNumber()) != null)
				.findFirst()
				.orElseThrow();
		int tableNumber = originalBranch.getXfrZTableNumber();

		Path exported = tempDir.resolve("sample-v34-xfr-z-table.raw");
		new PSSERawExporter(original, 34, true).export(exported);
		AclfNetwork roundTrip = new PSSEDirectParser(34).parse(exported.toString());
		AclfBranch importedBranch = roundTrip.getBranch(
				originalBranch.getFromBusId(), originalBranch.getToBusId(),
				originalBranch.getCircuitNumber());

		assertEquals(tableNumber, importedBranch.getXfrZTableNumber());
		assertEquals(original.getXfrZTableEntry(tableNumber).getPointSet().getPoints().size(),
				roundTrip.getXfrZTableEntry(tableNumber).getPointSet().getPoints().size());
		assertEquals(original.getXfrZTableEntry(tableNumber).getPointSet().getPoints().get(0).y,
				roundTrip.getXfrZTableEntry(tableNumber).getPointSet().getPoints().get(0).y);
	}

	@Test
	public void rawV30PreservesTransformerImpedanceCorrectionPairs() throws Exception {
		AclfNetwork original = new PSSEDirectParser(30)
				.parse("../ipss.plugin.core/testData/psse/v30/psse_mthvdc.raw");
		Path exported = tempDir.resolve("psse-mthvdc-v30.raw");
		new PSSERawExporter(original, 30).export(exported);
		AclfNetwork roundTrip = new PSSEDirectParser(30).parse(exported.toString());

		assertEquals(original.getXfrZTableEntry(1).getPointSet().getPoints().size(),
				roundTrip.getXfrZTableEntry(1).getPointSet().getPoints().size());
		assertEquals(original.getXfrZTableEntry(1).getPointSet().getPoints().get(0).y,
				roundTrip.getXfrZTableEntry(1).getPointSet().getPoints().get(0).y);
	}

	@Test
	public void rawPreservesThreeWindingStatusWhenAStoredTerminalBusIsInactive()
			throws Exception {
		AclfNetwork original = new PSSEDirectParser(34)
				.parse("testData/psse/v34/sample_v34.raw");
		Aclf3WBranch original3W = original.getSpecialBranchList().stream()
				.filter(Aclf3WBranch.class::isInstance)
				.map(Aclf3WBranch.class::cast)
				.findFirst()
				.orElseThrow();
		original3W.setStatus(true);
		original3W.getFromAclfBranch().setStatus(true);
		original3W.getToAclfBranch().setStatus(false);
		original3W.getTertAclfBranch().setStatus(true);
		original3W.getToBus().setStatus(false);

		Path exported = tempDir.resolve("sample-v34-three-winding-status.raw");
		new PSSERawExporter(original, 34).export(exported);
		AclfNetwork roundTrip = new PSSEDirectParser(34).parse(exported.toString());
		Aclf3WBranch imported3W = roundTrip.getSpecialBranchList().stream()
				.filter(Aclf3WBranch.class::isInstance)
				.map(Aclf3WBranch.class::cast)
				.filter(branch -> branch.getFromBusId().equals(original3W.getFromBusId())
						&& branch.getToBusId().equals(original3W.getToBusId())
						&& branch.getTertiaryBus().getId()
								.equals(original3W.getTertiaryBus().getId()))
				.findFirst()
				.orElseThrow();

		assertTrue(imported3W.isStatus());
		assertTrue(imported3W.getFromAclfBranch().isStatus());
		assertFalse(imported3W.getToAclfBranch().isStatus());
		assertTrue(imported3W.getTertAclfBranch().isStatus());
	}

	@Test
	public void solvedRawV34PreservesSvcActualSusceptance() throws Exception {
		AclfNetwork original = new PSSEDirectParser(34)
				.parse("testData/psse/v34/sample_v34.raw");
		AclfBus originalBus = original.getBusList().stream()
				.filter(bus -> !bus.getStaticVarCompensatorList().isEmpty())
				.findFirst()
				.orElseThrow();
		StaticVarCompensator originalSvc = originalBus.getStaticVarCompensatorList().get(0);
		originalSvc.setBActual(-0.25);
		originalSvc.setControlStatus(false);

		Path exported = tempDir.resolve("sample-v34-solved-svc.raw");
		new PSSERawExporter(original, 34, true).export(exported);
		AclfNetwork roundTrip = new PSSEDirectParser(34).parse(exported.toString());
		AclfBus importedBus = roundTrip.getBus(originalBus.getId());
		StaticVarCompensator importedSvc = importedBus.getStaticVarCompensatorList().get(0);

		assertFalse(importedSvc.isStatus());
		assertEquals(-0.25, importedSvc.getBActual(), TOL);
		assertEquals(-0.25, importedBus.toCapacitorBus().getB(false), TOL);
	}

	@Test
	public void solvedRawPreservesSvcSusceptanceLimitAwayFromNominalVoltage()
			throws Exception {
		AclfNetwork original = new PSSEDirectParser(34)
				.parse("testData/psse/v34/sample_v34.raw");
		AclfBus originalBus = original.getBusList().stream()
				.filter(bus -> !bus.getStaticVarCompensatorList().isEmpty())
				.findFirst()
				.orElseThrow();
		StaticVarCompensator originalSvc =
				originalBus.getStaticVarCompensatorList().get(0);
		double expectedVoltage = 0.9274339412762561;
		LimitType expectedLimit = originalSvc.getBLimit(false);
		double expectedB = 0.9 * expectedLimit.getMin();
		originalBus.setVoltageMag(expectedVoltage);
		originalSvc.setBActual(expectedB);
		originalSvc.setControlStatus(true);
		originalSvc.setAdjustStatus(true);

		Path exported = tempDir.resolve("sample-v34-solved-svc-limit.raw");
		new PSSERawExporter(original, 34, true).export(exported);
		AclfBus importedBus = new PSSEDirectParser(34)
				.parse(exported.toString()).getBus(originalBus.getId());
		StaticVarCompensator importedSvc =
				importedBus.getStaticVarCompensatorList().get(0);

		assertEquals(expectedB, importedSvc.getBActual(), TOL);
		assertEquals(expectedLimit.getMax(),
				importedSvc.getBLimit(false).getMax(), TOL);
		assertEquals(expectedLimit.getMin(),
				importedSvc.getBLimit(false).getMin(), TOL);
	}

	@Test
	public void solvedRawExportsLimitBoundSvcAsFixedOutput() throws Exception {
		AclfNetwork original = new PSSEDirectParser(34)
				.parse("testData/psse/v34/sample_v34.raw");
		AclfBus originalBus = original.getBusList().stream()
				.filter(bus -> !bus.getStaticVarCompensatorList().isEmpty())
				.findFirst()
				.orElseThrow();
		StaticVarCompensator originalSvc = originalBus.getStaticVarCompensatorList().get(0);
		double lowerLimit = originalSvc.getBLimit(false).getMin();
		originalSvc.setBActual(lowerLimit);
		originalSvc.setControlStatus(true);

		Path exported = tempDir.resolve("sample-v34-solved-limit-svc.raw");
		new PSSERawExporter(original, 34, true).export(exported);
		AclfNetwork roundTrip = new PSSEDirectParser(34).parse(exported.toString());
		AclfBus importedBus = roundTrip.getBus(originalBus.getId());
		StaticVarCompensator importedSvc = importedBus.getStaticVarCompensatorList().get(0);

		assertFalse(importedSvc.isStatus());
		assertEquals(lowerLimit, importedBus.toCapacitorBus().getB(false), TOL);
	}

	@Test
	public void solvedRawUsesAcceptedLccPowerInsteadOfStaleDemand() throws Exception {
		AclfNetwork solved = new PSSEDirectParser(33).parse(
				"testData/adpter/psse/v33/Kundur_2area_LCC_HVDC.raw");
		assertTrue(LoadflowAlgoObjectFactory.createLoadflowAlgorithm(solved).loadflow());
		HvdcLine2TLCC<?> line = (HvdcLine2TLCC<?>) solved.getSpecialBranchList().stream()
				.filter(HvdcLine2TLCC.class::isInstance)
				.findFirst()
				.orElseThrow();
		ThyConverter<?> controlled = line.getControlSide() == HvdcControlSide.INVERTER
				? line.getInverter() : line.getRectifier();
		double acceptedPowerMw = Math.abs(
				controlled.getDcVoltage() * controlled.getIdc()) / 1.0e6;
		double acceptedScheduledVoltageKv = (line.getInverter().getDcVoltage()
				+ line.getInverter().getIdc() * line.getCompondR(UnitType.Ohm)) / 1.0e3;
		double acceptedRectifierP = line.getRectifier().powerIntoConverter().getReal();
		double acceptedRectifierQ = line.getRectifier().powerIntoConverter().getImaginary();
		double acceptedInverterP = line.getInverter().powerIntoConverter().getReal();
		double acceptedInverterQ = line.getInverter().powerIntoConverter().getImaginary();
		double acceptedRectifierAngle = line.getRectifier().getFiringAng();
		double acceptedInverterAngle = line.getInverter().getFiringAng();
		long acceptedRectifierTapPosition = tapPosition(line.getRectifier());
		long acceptedInverterTapPosition = tapPosition(line.getInverter());
		// Preserve the accepted converter state even when the final AC-only settling
		// pass moved terminal voltage after the last detailed-LCC refresh.
		solved.getBus(line.getFromBusId()).setVoltageMag(
				solved.getBus(line.getFromBusId()).getVoltageMag() * 1.01);
		solved.getBus(line.getToBusId()).setVoltageMag(
				solved.getBus(line.getToBusId()).getVoltageMag() * 1.01);
		line.setPowerDemand(acceptedPowerMw + 100.0, UnitType.mW);
		line.setScheduledDCVoltage(acceptedScheduledVoltageKv + 50.0, UnitType.kV);

		Path exported = tempDir.resolve("kundur-lcc-solved-v35.raw");
		new PSSERawExporter(solved, 35, true).export(exported);
		Path exportedRawx = tempDir.resolve("kundur-lcc-solved.rawx");
		PSSEJsonExporter rawxExporter = new PSSEJsonExporter(solved, true);
		rawxExporter.export(exportedRawx);
		JsonObject lccSection = rawxExporter.export()
				.getAsJsonObject("network").getAsJsonObject("twotermdc");
		assertEquals(acceptedRectifierTapPosition,
				rawxLong(lccSection, 0, "ipss_tap_pos_r"));
		assertEquals(acceptedRectifierAngle,
				rawxNumber(lccSection, 0, "ipss_alpha_r_deg"), TOL);
		assertEquals(acceptedRectifierP,
				rawxNumber(lccSection, 0, "ipss_p_into_converter_r_pu"), TOL);
		assertEquals(acceptedRectifierQ,
				rawxNumber(lccSection, 0, "ipss_q_into_converter_r_pu"), TOL);
		assertEquals(acceptedInverterTapPosition,
				rawxLong(lccSection, 0, "ipss_tap_pos_i"));
		assertEquals(acceptedInverterAngle,
				rawxNumber(lccSection, 0, "ipss_gamma_i_deg"), TOL);
		assertEquals(acceptedInverterP,
				rawxNumber(lccSection, 0, "ipss_p_into_converter_i_pu"), TOL);
		assertEquals(acceptedInverterQ,
				rawxNumber(lccSection, 0, "ipss_q_into_converter_i_pu"), TOL);
		String rawText = Files.readString(exported);
		assertTrue(rawText.contains("firing_angle_deg="));
		assertTrue(rawText.contains("extinction_angle_deg="));
		assertTrue(rawText.contains("discrete_tap_position="));
		AclfNetwork rawxRoundTrip = new PSSEJsonDirectParser()
				.parse(exportedRawx.toString());
		assertTrue(rawxRoundTrip.getSpecialBranchList().stream()
				.anyMatch(HvdcLine2TLCC.class::isInstance));
		AclfNetwork roundTrip = new PSSEDirectParser(35).parse(exported.toString());
		HvdcLine2TLCC<?> imported = (HvdcLine2TLCC<?>) roundTrip.getSpecialBranchList().stream()
				.filter(HvdcLine2TLCC.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals(acceptedPowerMw, imported.getPowerDemand(UnitType.mW), 1.0e-6);
		assertEquals(acceptedScheduledVoltageKv,
				imported.getScheduledDCVoltage(UnitType.kV), 1.0e-6);
		assertTrue(imported.calculateLoadflow(
				LoadflowAlgoObjectFactory.createDefaultVoltageAdjControlConfig()));
		assertEquals(acceptedRectifierP,
				imported.getRectifier().powerIntoConverter().getReal(), 1.0e-6);
		assertEquals(acceptedRectifierQ,
				imported.getRectifier().powerIntoConverter().getImaginary(), 1.0e-6);
		assertEquals(acceptedInverterP,
				imported.getInverter().powerIntoConverter().getReal(), 1.0e-6);
		assertEquals(acceptedInverterQ,
				imported.getInverter().powerIntoConverter().getImaginary(), 1.0e-6);
	}

	private static long tapPosition(ThyConverter<?> converter) {
		return Math.round((converter.getXformerTapSetting()
				- converter.getXformerTapLimit().getMin())
				/ converter.getXformerTapStepSize());
	}

	private static double rawxNumber(JsonObject section, int rowIndex, String field) {
		return rawxValue(section, rowIndex, field).getAsDouble();
	}

	private static long rawxLong(JsonObject section, int rowIndex, String field) {
		return rawxValue(section, rowIndex, field).getAsLong();
	}

	private static com.google.gson.JsonElement rawxValue(
			JsonObject section, int rowIndex, String field) {
		JsonArray fields = section.getAsJsonArray("fields");
		for (int i = 0; i < fields.size(); i++) {
			if (field.equals(fields.get(i).getAsString())) {
				return section.getAsJsonArray("data").get(rowIndex)
						.getAsJsonArray().get(i);
			}
		}
		throw new AssertionError("Missing RAWX field " + field);
	}

	@Test
	public void solvedRawEquivalencesOnlyLccControlsFixedByTheSolver() throws Exception {
		AclfNetwork solved = new PSSEDirectParser(33).parse(
				"testData/adpter/psse/v33/Kundur_2area_LCC_HVDC.raw");
		assertTrue(LoadflowAlgoObjectFactory.createLoadflowAlgorithm(solved).loadflow());
		HvdcLine2TLCC<?> line = (HvdcLine2TLCC<?>) solved.getSpecialBranchList().stream()
				.filter(HvdcLine2TLCC.class::isInstance)
				.findFirst()
				.orElseThrow();
		Complex rectifierPq = line.getRectifier().powerIntoConverter();
		Complex inverterPq = line.getInverter().powerIntoConverter();
		solved.getExtraInfo().put(
				LoadflowAlgorithm.FIXED_LCC_HVDC_IDS_EXTRA_INFO_KEY,
				java.util.Set.of(line.getId()));

		Path exported = tempDir.resolve("kundur-lcc-fixed-solved-v35.raw");
		new PSSERawExporter(solved, 35, true).export(exported);
		AclfNetwork roundTrip = new PSSEDirectParser(35).parse(exported.toString());
		HvdcLine2TLCC<?> imported = (HvdcLine2TLCC<?>) roundTrip.getSpecialBranchList().stream()
				.filter(HvdcLine2TLCC.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertFalse(imported.isActive());
		AclfLoad rectifierEquivalent = roundTrip.getBus(line.getFromBusId())
				.getContributeLoadList().stream()
				.filter(load -> "HR".equals(load.getId()))
				.findFirst().orElseThrow();
		AclfLoad inverterEquivalent = roundTrip.getBus(line.getToBusId())
				.getContributeLoadList().stream()
				.filter(load -> "HI".equals(load.getId()))
				.findFirst().orElseThrow();
		assertEquals(rectifierPq.getReal(), rectifierEquivalent.getLoadCP().getReal(), TOL);
		assertEquals(rectifierPq.getImaginary(), rectifierEquivalent.getLoadCP().getImaginary(), TOL);
		assertEquals(inverterPq.getReal(), inverterEquivalent.getLoadCP().getReal(), TOL);
		assertEquals(inverterPq.getImaginary(), inverterEquivalent.getLoadCP().getImaginary(), TOL);
	}

	@Test
	public void solvedRawUsesUniqueLoadIdsForFixedLccEquivalentsAtSharedTerminal()
			throws Exception {
		AclfNetworkBuilder builder = new AclfNetworkBuilder();
		builder.setNetworkInfo("shared-lcc", "shared-lcc", 100000.0,
				OriginalDataFormat.PSSE);
		builder.addBus("Bus1", "Shared rectifier", 1L, 230000.0,
				1.0, 0.0, null, null, null);
		builder.addBus("Bus2", "Inverter 1", 2L, 230000.0,
				1.0, 0.0, null, null, null);
		builder.addBus("Bus3", "Inverter 2", 3L, 230000.0,
				1.0, 0.0, null, null, null);

		HvdcLine2TLCC<AclfBus> first = addLcc(builder, "L1", "Bus2", 200.0);
		HvdcLine2TLCC<AclfBus> second = addLcc(builder, "L2", "Bus3", 150.0);
		assertTrue(first.initLoadflow());
		assertTrue(second.initLoadflow());
		double expectedP = first.getRectifier().powerIntoConverter().getReal()
				+ second.getRectifier().powerIntoConverter().getReal();
		double expectedQ = first.getRectifier().powerIntoConverter().getImaginary()
				+ second.getRectifier().powerIntoConverter().getImaginary();
		AclfNetwork solved = builder.getNetwork();
		solved.getExtraInfo().put(
				LoadflowAlgorithm.FIXED_LCC_HVDC_IDS_EXTRA_INFO_KEY,
				java.util.Set.of(first.getId(), second.getId()));

		Path exported = tempDir.resolve("shared-fixed-lcc-solved-v35.raw");
		new PSSERawExporter(solved, 35, true).export(exported);
		AclfBus importedRectifier = new PSSEDirectParser(35)
				.parse(exported.toString()).getBus("Bus1");
		List<AclfLoad> equivalents = importedRectifier.getContributeLoadList().stream()
				.filter(AclfLoad::isActive)
				.toList();

		assertEquals(2, equivalents.size());
		assertEquals(2L, equivalents.stream().map(AclfLoad::getId).distinct().count());
		assertEquals(expectedP, equivalents.stream()
				.mapToDouble(load -> load.getLoadCP().getReal()).sum(), TOL);
		assertEquals(expectedQ, equivalents.stream()
				.mapToDouble(load -> load.getLoadCP().getImaginary()).sum(), TOL);
	}

	private static HvdcLine2TLCC<AclfBus> addLcc(AclfNetworkBuilder builder,
			String id, String inverterBusId, double powerMw) throws Exception {
		HvdcLine2TLCC<AclfBus> line = builder.addHvdcLine2TLCC(
				id, id, "Bus1", inverterBusId, true, false,
				HvdcControlMode.DC_POWER, HvdcOperationMode.REC1_INV1,
				10.0, powerMw, 0.0, true, 500.0, 0.0, 0.1,
				ConverterType.RECTIFIER);
		builder.setLCCRectifier(line, 2, 5.0, 30.0, 0.5, 5.0,
				230.0, 1.0, 1.0, 1.1, 0.9, 0.0125, 0.0, 15.0);
		builder.setLCCInverter(line, 2, 15.0, 25.0, 0.5, 5.0,
				230.0, 1.0, 1.0, 1.1, 0.9, 0.0125, 0.0, 20.0);
		return line;
	}

 }
