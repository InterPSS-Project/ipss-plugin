package org.interpss.plugin.sensitivity;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.AnalysisType;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.CalculationOptions;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.EndpointCatalog;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.EndpointRef;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.EndpointType;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.GeneratorFactor;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.DirectionExpansion;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.GeneratedDirectionSet;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.InjectionGroupDefinition;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.InjectionGroupMember;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.InjectionMemberType;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.InterfaceMember;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.InterfaceAggregation;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.LinearInterface;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.MonitorSet;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.LodfSpec;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.MultiOutageLodfSpec;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.NetworkReference;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.OutageGroup;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.PtdfSpec;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.ParticipationProfile;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.ResultRetentionPolicy;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.ShiftFactorSpec;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.TransactorRole;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.SuperAreaDefinition;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.WeightedAreaRef;
import org.interpss.plugin.sensitivity.SensitivityResult.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class DefaultDcSensitivityRunnerTest extends CorePluginTestSetup {
	private static final String MONITOR = "Bus1->Bus2(1)";

	@Test
	void jsonRoundTripPreservesPortableStudy() {
		DcSensitivityStudyDefinition input = busStudy("Bus2", "Bus3");
		String json = new DcSensitivityJsonCodec().toJson(input);
		DcSensitivityStudyDefinition output = new DcSensitivityJsonCodec().fromJson(json);
		assertEquals(input, output);
		assertTrue(json.contains("\"analyses\""));
		assertTrue(json.contains("\"type\": \"PTDF\""));

		JsonObject legacy = JsonParser.parseString(json).getAsJsonObject();
		JsonObject ptdf = legacy.remove("analyses").getAsJsonArray().get(0).getAsJsonObject();
		ptdf.remove("type");
		legacy.addProperty("analysisType", "PTDF");
		legacy.add("ptdf", ptdf);
		assertEquals(input, new DcSensitivityJsonCodec().fromJson(legacy.toString()));
	}

	@Test
	void singleAnalysisApiValidatesTheExplicitTypeAgainstTheSpec() {
		PtdfSpec spec = (PtdfSpec) busStudy("Bus2", "Bus3").analyses().getFirst();
		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(
				DcSensitivityStudyDefinition.CURRENT_SCHEMA_VERSION, "single", "single PTDF",
				new NetworkReference("", ""), EndpointCatalog.empty(), AnalysisType.PTDF, spec,
				CalculationOptions.defaults());

		assertEquals(List.of(spec), study.analyses());
		assertEquals(List.of(AnalysisType.PTDF), study.analysisTypes());
		assertThrows(IllegalArgumentException.class, () -> new DcSensitivityStudyDefinition(
				DcSensitivityStudyDefinition.CURRENT_SCHEMA_VERSION, "bad", "mismatched",
				new NetworkReference("", ""), EndpointCatalog.empty(), AnalysisType.LODF, spec,
				CalculationOptions.defaults()));
	}

	@Test
	void busPtdfMatchesExistingCoreApi() throws Exception {
		AclfNetwork net = loadIeee14();
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		var manifest = new DefaultDcSensitivityRunner().run(net, busStudy("Bus2", "Bus3"), sink);

		ContingencyAnalysisAlgorithm scalar = createContingencyAnalysisAlgorithm(net);
		scalar.calculateDclf(DclfMethod.STD);
		double expected = scalar.pTransferDistFactor("Bus2", "Bus3", net.getBranch(MONITOR));
		assertEquals(expected, sink.rows().getFirst().factor(), 1.0e-10);
		assertEquals(1, manifest.candidateCount());
		assertTrue(manifest.complete());
	}

	@Test
	void ptdfThresholdKeepsNegativeFactorsByMagnitude() throws Exception {
		AclfNetwork net = loadIeee14();
		List<String> monitorIds = net.getBranchList().stream()
				.filter(AclfBranch::isActive)
				.map(AclfBranch::getId)
				.toList();
		PtdfSpec spec = new PtdfSpec(List.of(new DcSensitivityStudyDefinition.Direction(
				"transaction", "Bus 4 to Bus 5", EndpointRef.bus("Bus4"), EndpointRef.bus("Bus5"), true)),
				new MonitorSet(monitorIds, List.of()));
		DcSensitivityStudyDefinition fullStudy = new DcSensitivityStudyDefinition(
				DcSensitivityStudyDefinition.CURRENT_SCHEMA_VERSION, "ptdf-full", "PTDF full",
				new NetworkReference("", ""), EndpointCatalog.empty(), List.of(spec),
				new CalculationOptions(DclfMethod.STD, null, ResultRetentionPolicy.FULL, 0.0,
						100, 100.0, false, 1024));
		InMemorySensitivityResultSink fullSink = new InMemorySensitivityResultSink();
		new DefaultDcSensitivityRunner().run(net, fullStudy, fullSink);
		Row negative = fullSink.rows().stream()
				.filter(row -> row.factor() < -1.0e-6)
				.max(Comparator.comparingDouble(row -> Math.abs(row.factor())))
				.orElseThrow();

		DcSensitivityStudyDefinition thresholdedStudy = new DcSensitivityStudyDefinition(
				DcSensitivityStudyDefinition.CURRENT_SCHEMA_VERSION, "ptdf-thresholded", "PTDF thresholded",
				new NetworkReference("", ""), EndpointCatalog.empty(), List.of(spec),
				new CalculationOptions(DclfMethod.STD, null, ResultRetentionPolicy.THRESHOLDED,
						Math.abs(negative.factor()) - 1.0e-9, 100, 100.0, false, 1024));
		InMemorySensitivityResultSink thresholdedSink = new InMemorySensitivityResultSink();
		new DefaultDcSensitivityRunner().run(loadIeee14(), thresholdedStudy, thresholdedSink);

		assertTrue(thresholdedSink.rows().stream().anyMatch(row ->
						row.monitorId().equals(negative.monitorId()) && row.factor() < 0.0),
				"PTDF retention threshold must compare against absolute factor magnitude");
	}

	@Test
	void areaUsesEqualActiveGeneratorParticipation() throws Exception {
		AclfNetwork net = loadIeee14();
		EndpointRef area = new EndpointRef(EndpointType.AREA, net.getBus("Bus2").getAreaId(), "");
		DcSensitivityStudyDefinition study = study(area, EndpointRef.bus("Bus14"), EndpointCatalog.empty());
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		new DefaultDcSensitivityRunner().run(net, study, sink);

		Map<String, Double> weights = sink.manifest().snapshot().resolvedEndpoints().values().stream()
				.filter(endpoint -> endpoint.configuredType().equals("AREA"))
				.findFirst().orElseThrow().busWeights();
		assertFalse(weights.isEmpty());
		assertEquals(1.0, weights.values().stream().mapToDouble(Double::doubleValue).sum(), 1.0e-12);
		assertTrue(weights.keySet().stream().allMatch(id -> net.getBus(id).getAreaId().equals(area.targetId())));
	}

	@Test
	void injectionGroupSupportsGeneratorLoadBusAndNestedGroups() throws Exception {
		AclfNetwork net = loadIeee14();
		String generatorId = net.getBus("Bus2").getContributeGenList().getFirst().getId();
		String loadId = net.getBus("Bus3").getContributeLoadList().getFirst().getId();
		InjectionGroupDefinition child = new InjectionGroupDefinition("child", "child", List.of(
				new InjectionGroupMember(InjectionMemberType.GENERATOR, "Bus2", generatorId, "", 2.0),
				new InjectionGroupMember(InjectionMemberType.LOAD, "Bus3", loadId, "", 1.0)));
		InjectionGroupDefinition parent = new InjectionGroupDefinition("parent", "parent", List.of(
				new InjectionGroupMember(InjectionMemberType.INJECTION_GROUP, "", "", "child", 1.0),
				new InjectionGroupMember(InjectionMemberType.BUS, "Bus4", "", "", 1.0)));
		EndpointCatalog catalog = new EndpointCatalog(Map.of(), Map.of("child", child, "parent", parent), Map.of());
		DcSensitivityStudyDefinition study = study(
				new EndpointRef(EndpointType.INJECTION_GROUP, "parent", ""), EndpointRef.bus("Bus14"), catalog);
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		new DefaultDcSensitivityRunner().run(net, study, sink);

		Map<String, Double> weights = sink.manifest().snapshot().resolvedEndpoints().values().stream()
				.filter(endpoint -> endpoint.configuredId().equals("parent")).findFirst().orElseThrow().busWeights();
		assertEquals(0.5, weights.get("Bus2"), 1.0e-12);
		assertEquals(0.25, weights.get("Bus3"), 1.0e-12);
		assertEquals(0.25, weights.get("Bus4"), 1.0e-12);
		assertTrue(sink.manifest().diagnostics().stream()
				.anyMatch(diagnostic -> diagnostic.code().equals("INJECTION_GROUP_FACTORS_NORMALIZED")
						&& diagnostic.message().contains("normalized to 1.0 (100%)")));
	}

	@Test
	void missingPortableSinkWarnsAndFallsBackToSlack() throws Exception {
		AclfNetwork net = loadIeee14();
		DcSensitivityStudyDefinition study = study(EndpointRef.bus("Bus2"), EndpointRef.bus("not-in-this-case"), EndpointCatalog.empty());
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		new DefaultDcSensitivityRunner().run(net, study, sink);

		Row row = sink.rows().getFirst();
		String slackId = net.getBusList().stream().filter(bus -> bus.isRefBus() || bus.isSwing())
				.map(bus -> bus.getId()).findFirst().orElseThrow();
		assertEquals(slackId, row.sinkId());
		assertTrue(sink.manifest().diagnostics().stream().anyMatch(d -> d.code().equals("SINK_FALLBACK_TO_SLACK")));
	}

	@Test
	void unresolvedSourceDoesNotSuppressLaterSinkSlackFallback() throws Exception {
		AclfNetwork net = loadIeee14();
		EndpointRef missing = EndpointRef.bus("not-in-this-case");
		PtdfSpec spec = new PtdfSpec(List.of(
				new DcSensitivityStudyDefinition.Direction("bad-source", "bad source", missing, EndpointRef.bus("Bus3"), true),
				new DcSensitivityStudyDefinition.Direction("fallback-sink", "fallback sink", EndpointRef.bus("Bus2"), missing, true)),
				new MonitorSet(List.of(MONITOR), List.of()));
		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(1, "role-cache", "role cache",
				new NetworkReference("", ""), EndpointCatalog.empty(), List.of(spec), CalculationOptions.defaults());
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		new DefaultDcSensitivityRunner().run(net, study, sink);
		assertEquals(1, sink.rows().size());
		assertEquals("fallback-sink", sink.rows().getFirst().directionId());
		assertTrue(sink.manifest().diagnostics().stream().anyMatch(d -> d.code().equals("SINK_FALLBACK_TO_SLACK")));
	}

	@Test
	void thresholdAndTransferScalingAreAppliedAtResultBoundary() throws Exception {
		AclfNetwork net = loadIeee14();
		CalculationOptions options = new CalculationOptions(DclfMethod.STD, null,
				ResultRetentionPolicy.FULL, 0.0, 10, 250.0, false, 1);
		DcSensitivityStudyDefinition base = busStudy("Bus2", "Bus3");
		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(base.schemaVersion(), base.id(), base.name(),
				base.network(), base.endpoints(), base.analyses(), options);
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		new DefaultDcSensitivityRunner().run(net, study, sink);
		assertEquals(sink.rows().getFirst().factor() * 250.0, sink.rows().getFirst().incrementalFlowMw(), 1.0e-10);
		assertNotNull(sink.manifest());
	}

	@Test
	void shiftFactorUsesTheSharedTransferKernel() throws Exception {
		AclfNetwork net = loadIeee14();
		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(1, "sf", "shift", new NetworkReference("", ""),
				EndpointCatalog.empty(), List.of(new ShiftFactorSpec(EndpointRef.bus("Bus2"), TransactorRole.SELLER, List.of(),
						List.of(EndpointRef.bus("Bus3")), new MonitorSet(List.of(MONITOR), List.of()))),
				CalculationOptions.defaults());
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		new DefaultDcSensitivityRunner().run(net, study, sink);
		ContingencyAnalysisAlgorithm scalar = createContingencyAnalysisAlgorithm(net);
		scalar.calculateDclf(DclfMethod.STD);
		assertEquals(scalar.pTransferDistFactor("Bus2", "Bus3", net.getBranch(MONITOR)),
				sink.rows().getFirst().factor(), 1.0e-10);
		assertEquals("BUS", sink.rows().getFirst().candidateType());
		assertEquals("Bus3", sink.rows().getFirst().candidateId());
	}

	@Test
	void generatedShiftFactorGeneratorCandidatesKeepDeviceIdentity() throws Exception {
		AclfNetwork net = loadIeee14();
		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(1, "sf-generators", "shift",
				new NetworkReference("", ""), EndpointCatalog.empty(), List.of(new ShiftFactorSpec(EndpointRef.bus("Bus14"), TransactorRole.BUYER,
						List.of(DcSensitivityStudyDefinition.CandidateType.GENERATOR), List.of(),
						new MonitorSet(List.of(MONITOR), List.of()))),
				CalculationOptions.defaults());
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		new DefaultDcSensitivityRunner().run(net, study, sink);
		long activeGenerators = net.getBusList().stream().filter(bus -> bus.isActive())
				.flatMap(bus -> bus.getContributeGenList().stream()).filter(generator -> generator.isActive()).count();
		assertEquals(activeGenerators, sink.rows().size());
		assertTrue(sink.rows().stream().allMatch(row -> row.candidateType().equals("GENERATOR")));
		assertTrue(sink.rows().stream().allMatch(row -> row.candidateId().contains("/")));
	}

	@Test
	void lodfMatchesExistingCoreApi() throws Exception {
		AclfNetwork net = loadIeee14();
		String outageId = "Bus2->Bus3(1)";
		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(1, "lodf", "lodf", new NetworkReference("", ""),
				EndpointCatalog.empty(), List.of(new LodfSpec(List.of(outageId), new MonitorSet(List.of(MONITOR), List.of()))),
				CalculationOptions.defaults());
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		new DefaultDcSensitivityRunner().run(net, study, sink);
		ContingencyAnalysisAlgorithm scalar = createContingencyAnalysisAlgorithm(net);
		scalar.calculateDclf(DclfMethod.STD);
		var outage = com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch(
				scalar.getDclfAlgoBranch(outageId), com.interpss.core.contingency.ContingencyBranchOutageType.OPEN);
		assertEquals(scalar.lineOutageDFactor(outage, net.getBranch(MONITOR)), sink.rows().getFirst().factor(), 1.0e-10);
	}

	@Test
	void oneStudyRunsPtdfAndLodfSpecifications() throws Exception {
		AclfNetwork net = loadIeee14();
		String outageId = "Bus2->Bus3(1)";
		MonitorSet monitors = new MonitorSet(List.of(MONITOR), List.of());
		PtdfSpec ptdf = new PtdfSpec(List.of(new DcSensitivityStudyDefinition.Direction(
				"transfer", "Bus 2 to Bus 3", EndpointRef.bus("Bus2"), EndpointRef.bus("Bus3"), true)), monitors);
		LodfSpec lodf = new LodfSpec(List.of(outageId), monitors);
		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(
				DcSensitivityStudyDefinition.CURRENT_SCHEMA_VERSION, "combined", "PTDF and LODF",
				new NetworkReference("", ""), EndpointCatalog.empty(), List.of(ptdf, lodf),
				CalculationOptions.defaults());
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		var manifest = new DefaultDcSensitivityRunner().run(net, study, sink);

		assertEquals(List.of(AnalysisType.PTDF, AnalysisType.LODF), manifest.analysisTypes());
		assertEquals(List.of(AnalysisType.PTDF, AnalysisType.LODF),
				sink.rows().stream().map(Row::analysisType).toList());
		assertEquals(2, manifest.candidateCount());
		assertEquals(2, DcSensitivityStudyEstimator.estimateCandidateCount(net, study));
		assertEquals(study, new DcSensitivityJsonCodec().fromJson(new DcSensitivityJsonCodec().toJson(study)));
	}

	@Test
	void multiOutageLodfProducesOneCoefficientPerOutageAndMonitor() throws Exception {
		AclfNetwork net = loadIeee14();
		List<String> outages = List.of("Bus2->Bus3(1)", "Bus4->Bus5(1)");
		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(1, "mlodf", "multi", new NetworkReference("", ""),
				EndpointCatalog.empty(), List.of(new MultiOutageLodfSpec(List.of(new OutageGroup("g1", "group", outages)),
						new MonitorSet(List.of(MONITOR), List.of()))), CalculationOptions.defaults());
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		var manifest = new DefaultDcSensitivityRunner().run(net, study, sink);
		assertEquals(2, manifest.candidateCount());
		assertEquals(outages, sink.rows().stream().map(Row::outageId).toList());
		assertTrue(sink.rows().stream().allMatch(row -> Double.isFinite(row.factor())));
		ContingencyAnalysisAlgorithm scalar = createContingencyAnalysisAlgorithm(net);
		scalar.calculateDclf(DclfMethod.STD);
		scalar.getOutageBranchList().clear();
		for (String id : outages) scalar.getOutageBranchList().add(
				com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch(
						scalar.getDclfAlgoBranch(id), com.interpss.core.contingency.ContingencyBranchOutageType.OPEN));
		Object inverse = scalar.calMultiOutageInvE_PTDF("expected");
		double[] expected = scalar.calMultiOutageLODFs(net.getBranch(MONITOR), inverse);
		for (int i = 0; i < outages.size(); i++) {
			int factorIndex = scalar.getOutageBranchList().get(i).getBranch().getSortNumber();
			assertEquals(expected[factorIndex], sink.rows().get(i).factor(), 1.0e-10);
		}
	}

	@Test
	void parquetSinkWritesPartitionsAndManifest(@TempDir Path directory) throws Exception {
		AclfNetwork net = loadIeee14();
		var manifest = new DefaultDcSensitivityRunner().run(net, busStudy("Bus2", "Bus3"),
				new ParquetSensitivityResultSink(directory));
		assertEquals(1, manifest.partitionUris().size());
		assertTrue(Files.exists(directory.resolve("partition-000000.parquet")));
		assertTrue(Files.readString(directory.resolve("manifest.json")).contains("\"complete\": true"));
	}

	@Test
	void generatedCartesianDirectionsExpandWithoutPersistingPairRows() throws Exception {
		AclfNetwork net = loadIeee14();
		PtdfSpec spec = new PtdfSpec(List.of(), List.of(new GeneratedDirectionSet("set", "generated",
				List.of(EndpointRef.bus("Bus2"), EndpointRef.bus("Bus3")),
				List.of(EndpointRef.bus("Bus4"), EndpointRef.bus("Bus5")), DirectionExpansion.CARTESIAN)),
				new MonitorSet(List.of(MONITOR), List.of()));
		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(1, "generated", "generated",
				new NetworkReference("", ""), EndpointCatalog.empty(), List.of(spec), CalculationOptions.defaults());
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		var manifest = new DefaultDcSensitivityRunner().run(net, study, sink);
		assertEquals(4, manifest.candidateCount());
		assertEquals(List.of("set-0-0", "set-0-1", "set-1-0", "set-1-1"),
				sink.rows().stream().map(Row::directionId).toList());
	}

	@Test
	void nonReferenceIslandIsDisabledForRunAndBusStatusIsRestored() throws Exception {
		AclfNetwork net = loadIeee14();
		net.getBranch("Bus9->Bus14(1)").setStatus(false);
		net.getBranch("Bus13->Bus14(1)").setStatus(false);
		assertTrue(net.getBus("Bus14").isActive());
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		var manifest = new DefaultDcSensitivityRunner().run(net, busStudy("Bus2", "Bus3"), sink);
		assertTrue(manifest.snapshot().temporarilyDisabledBusIds().contains("Bus14"));
		assertTrue(net.getBus("Bus14").isActive());
	}

	@Test
	void explicitGeneratorProfileControlsRegionalParticipation() throws Exception {
		AclfNetwork net = loadIeee14();
		String areaId = net.getBus("Bus1").getAreaId();
		String gen1 = net.getBus("Bus1").getContributeGenList().getFirst().getId();
		String gen2 = net.getBus("Bus2").getContributeGenList().getFirst().getId();
		ParticipationProfile profile = new ParticipationProfile("rated", "rated participation", List.of(
				new GeneratorFactor("Bus1", gen1, 3.0), new GeneratorFactor("Bus2", gen2, 1.0)));
		EndpointCatalog catalog = new EndpointCatalog(Map.of(), Map.of(), Map.of("rated", profile));
		EndpointRef source = new EndpointRef(EndpointType.AREA, areaId, "rated");
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		new DefaultDcSensitivityRunner().run(net, study(source, EndpointRef.bus("Bus14"), catalog), sink);
		Map<String, Double> weights = sink.manifest().snapshot().resolvedEndpoints().values().stream()
				.filter(endpoint -> endpoint.configuredType().equals("AREA")).findFirst().orElseThrow().busWeights();
		assertEquals(Map.of("Bus1", 0.75, "Bus2", 0.25), weights);
	}

	@Test
	void superAreaResolvesNativeAreaReferences() throws Exception {
		AclfNetwork net = loadIeee14();
		String areaId = net.getBus("Bus1").getAreaId();
		SuperAreaDefinition superArea = new SuperAreaDefinition("north", "North", List.of(new WeightedAreaRef(areaId, 1.0)));
		EndpointCatalog catalog = new EndpointCatalog(Map.of("north", superArea), Map.of(), Map.of());
		EndpointRef source = new EndpointRef(EndpointType.SUPER_AREA, "north", "");
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		new DefaultDcSensitivityRunner().run(net, study(source, EndpointRef.bus("Bus14"), catalog), sink);
		assertEquals(1.0, sink.manifest().snapshot().resolvedEndpoints().values().stream()
				.filter(endpoint -> endpoint.configuredType().equals("SUPER_AREA")).findFirst().orElseThrow()
				.busWeights().values().stream().mapToDouble(Double::doubleValue).sum(), 1.0e-12);
	}

	@Test
	void zoneResolvesOnlineGeneratorsAndNormalizesThem() throws Exception {
		AclfNetwork net = loadIeee14();
		String zoneId = net.getBusList().stream().filter(bus -> bus.isActive())
				.filter(bus -> bus.getContributeGenList().stream().anyMatch(generator -> generator.isActive()))
				.findFirst().orElseThrow().getZoneId();
		EndpointRef source = new EndpointRef(EndpointType.ZONE, zoneId, "");
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		new DefaultDcSensitivityRunner().run(net, study(source, EndpointRef.bus("Bus14"), EndpointCatalog.empty()), sink);
		Map<String, Double> weights = sink.manifest().snapshot().resolvedEndpoints().values().stream()
				.filter(endpoint -> endpoint.configuredType().equals("ZONE")).findFirst().orElseThrow().busWeights();
		assertFalse(weights.isEmpty());
		assertEquals(1.0, weights.values().stream().mapToDouble(Double::doubleValue).sum(), 1.0e-12);
	}

	@Test
	void linearInterfaceFactorIsSignedSumOfMemberBranchFactors() throws Exception {
		AclfNetwork net = loadIeee14();
		String second = "Bus2->Bus3(1)";
		LinearInterface monitored = new LinearInterface("interface", "interface", List.of(
				new InterfaceMember(MONITOR, 1.0), new InterfaceMember(second, -2.0)));
		PtdfSpec spec = new PtdfSpec(List.of(new DcSensitivityStudyDefinition.Direction(
				"d", "d", EndpointRef.bus("Bus2"), EndpointRef.bus("Bus3"), true)),
				MonitorSet.empty().addInterfaces(List.of(monitored)));
		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(1, "interface", "interface",
				new NetworkReference("", ""), EndpointCatalog.empty(), List.of(spec), CalculationOptions.defaults());
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		new DefaultDcSensitivityRunner().run(net, study, sink);
		ContingencyAnalysisAlgorithm scalar = createContingencyAnalysisAlgorithm(net);
		scalar.calculateDclf(DclfMethod.STD);
		double expected = scalar.pTransferDistFactor("Bus2", "Bus3", net.getBranch(MONITOR))
				- 2.0 * scalar.pTransferDistFactor("Bus2", "Bus3", net.getBranch(second));
		assertEquals(expected, sink.rows().getFirst().factor(), 1.0e-10);
		assertEquals("interface", sink.rows().getFirst().monitorId());
		assertEquals(InterfaceAggregation.SUM, monitored.aggregation());
	}

	@Test
	void monitorSetFluentlyAddsBranchesInterfacesAndAnotherSet() {
		LinearInterface monitored = new LinearInterface("interface", "interface",
				List.of(new InterfaceMember(MONITOR, 1.0)));
		MonitorSet empty = MonitorSet.empty();
		MonitorSet branches = empty.addBranches(List.of(MONITOR));
		MonitorSet interfaces = empty.addInterfaces(List.of(monitored));
		MonitorSet combined = MonitorSet.empty().addAll(branches.branchIds(), interfaces.interfaces());

		assertTrue(empty.branchIds().isEmpty());
		assertTrue(empty.interfaces().isEmpty());
		assertEquals(List.of(MONITOR), combined.branchIds());
		assertEquals(List.of(monitored), combined.interfaces());
	}

	@Test
	void normalizedWeightedInterfaceNormalizesAbsoluteMemberWeights() throws Exception {
		AclfNetwork net = loadIeee14();
		String second = "Bus2->Bus3(1)";
		LinearInterface monitored = new LinearInterface("weighted-interface", "weighted interface", List.of(
				new InterfaceMember(MONITOR, 1.0), new InterfaceMember(second, -2.0)),
				InterfaceAggregation.NORMALIZED_WEIGHTED_SUM);
		PtdfSpec spec = new PtdfSpec(List.of(new DcSensitivityStudyDefinition.Direction(
				"d", "d", EndpointRef.bus("Bus2"), EndpointRef.bus("Bus3"), true)),
				MonitorSet.empty().addInterfaces(List.of(monitored)));
		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(1, "weighted-interface", "weighted interface",
				new NetworkReference("", ""), EndpointCatalog.empty(), List.of(spec), CalculationOptions.defaults());
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		new DefaultDcSensitivityRunner().run(net, study, sink);

		ContingencyAnalysisAlgorithm scalar = createContingencyAnalysisAlgorithm(net);
		scalar.calculateDclf(DclfMethod.STD);
		double expected = scalar.pTransferDistFactor("Bus2", "Bus3", net.getBranch(MONITOR)) / 3.0
				- 2.0 * scalar.pTransferDistFactor("Bus2", "Bus3", net.getBranch(second)) / 3.0;
		assertEquals(expected, sink.rows().getFirst().factor(), 1.0e-10);
		assertTrue(sink.manifest().diagnostics().stream()
				.anyMatch(diagnostic -> diagnostic.code().equals("INTERFACE_WEIGHTS_NORMALIZED")));
		DcSensitivityStudyDefinition roundTrip = new DcSensitivityJsonCodec().fromJson(
				new DcSensitivityJsonCodec().toJson(study));
		PtdfSpec roundTripSpec = (PtdfSpec) roundTrip.analyses().getFirst();
		assertEquals(InterfaceAggregation.NORMALIZED_WEIGHTED_SUM,
				roundTripSpec.monitors().interfaces().getFirst().aggregation());
	}

	@Test
	void strictStudyRejectsUnnormalizedWeightedInterface() throws Exception {
		AclfNetwork net = loadIeee14();
		LinearInterface monitored = new LinearInterface("weighted-interface", "weighted interface", List.of(
				new InterfaceMember(MONITOR, 1.0), new InterfaceMember("Bus2->Bus3(1)", -2.0)),
				InterfaceAggregation.NORMALIZED_WEIGHTED_SUM);
		PtdfSpec spec = new PtdfSpec(List.of(new DcSensitivityStudyDefinition.Direction(
				"d", "d", EndpointRef.bus("Bus2"), EndpointRef.bus("Bus3"), true)),
				MonitorSet.empty().addInterfaces(List.of(monitored)));
		CalculationOptions strict = new CalculationOptions(DclfMethod.STD, null,
				ResultRetentionPolicy.FULL, 0.0, 10, 100.0, true, 16);
		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(1, "strict-interface", "strict interface",
				new NetworkReference("", ""), EndpointCatalog.empty(), List.of(spec), strict);

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> new DefaultDcSensitivityRunner().run(net, study, new InMemorySensitivityResultSink()));
		assertTrue(error.getMessage().contains("expected 1.0"));
	}

	@Test
	void injectionGroupCycleIsRejected() throws Exception {
		AclfNetwork net = loadIeee14();
		InjectionGroupDefinition first = new InjectionGroupDefinition("first", "first", List.of(
				new InjectionGroupMember(InjectionMemberType.INJECTION_GROUP, "", "", "second", 1.0)));
		InjectionGroupDefinition second = new InjectionGroupDefinition("second", "second", List.of(
				new InjectionGroupMember(InjectionMemberType.INJECTION_GROUP, "", "", "first", 1.0)));
		EndpointCatalog catalog = new EndpointCatalog(Map.of(), Map.of("first", first, "second", second), Map.of());
		assertThrows(IllegalArgumentException.class, () -> new DefaultDcSensitivityRunner().run(net,
				study(new EndpointRef(EndpointType.INJECTION_GROUP, "first", ""), EndpointRef.bus("Bus14"), catalog),
				new InMemorySensitivityResultSink()));
	}

	@Test
	void strictStudyDisablesMissingSinkFallback() throws Exception {
		AclfNetwork net = loadIeee14();
		DcSensitivityStudyDefinition base = busStudy("Bus2", "missing");
		CalculationOptions strict = new CalculationOptions(DclfMethod.STD, null,
				ResultRetentionPolicy.FULL, 0.0, 10, 100.0, true, 16);
		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(base.schemaVersion(), base.id(), base.name(),
				base.network(), base.endpoints(), base.analyses(), strict);
		assertThrows(IllegalArgumentException.class,
				() -> new DefaultDcSensitivityRunner().run(net, study, new InMemorySensitivityResultSink()));
	}

	@Test
	void strictStudyRejectsOmittedSink() throws Exception {
		AclfNetwork net = loadIeee14();
		CalculationOptions strict = new CalculationOptions(DclfMethod.STD, null,
				ResultRetentionPolicy.FULL, 0.0, 10, 100.0, true, 16);
		PtdfSpec spec = new PtdfSpec(List.of(new DcSensitivityStudyDefinition.Direction(
				"missing-sink", "missing sink", EndpointRef.bus("Bus2"), null, true)),
				new MonitorSet(List.of(MONITOR), List.of()));
		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(1, "strict", "strict",
				new NetworkReference("", ""), EndpointCatalog.empty(), List.of(spec), strict);
		assertThrows(IllegalArgumentException.class,
				() -> new DefaultDcSensitivityRunner().run(net, study, new InMemorySensitivityResultSink()));
	}

	@Test
	void sourceSinkReversalNegatesPtdf() throws Exception {
		AclfNetwork net = loadIeee14();
		InMemorySensitivityResultSink forward = new InMemorySensitivityResultSink();
		InMemorySensitivityResultSink reverse = new InMemorySensitivityResultSink();
		new DefaultDcSensitivityRunner().run(net, busStudy("Bus2", "Bus3"), forward);
		new DefaultDcSensitivityRunner().run(net, busStudy("Bus3", "Bus2"), reverse);
		assertEquals(-forward.rows().getFirst().factor(), reverse.rows().getFirst().factor(), 1.0e-10);
	}

	@Test
	void thresholdRetentionDoesNotMaterializeRejectedResultRows() throws Exception {
		AclfNetwork net = loadIeee14();
		DcSensitivityStudyDefinition base = busStudy("Bus2", "Bus3");
		CalculationOptions options = new CalculationOptions(DclfMethod.STD, null,
				ResultRetentionPolicy.THRESHOLDED, Double.MAX_VALUE, 10, 100.0, false, 16);
		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(base.schemaVersion(), base.id(), base.name(),
				base.network(), base.endpoints(), base.analyses(), options);
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		var manifest = new DefaultDcSensitivityRunner().run(net, study, sink);
		assertEquals(1, manifest.candidateCount());
		assertEquals(0, manifest.storedRowCount());
		assertTrue(sink.rows().isEmpty());
	}

	@Test
	void millionGeneratedDirectionMonitorPairsRemainStreamed() throws Exception {
		AclfNetwork net = loadIeee14();
		List<EndpointRef> sources = Collections.nCopies(1_000, EndpointRef.bus("Bus2"));
		List<EndpointRef> sinks = Collections.nCopies(1_000, EndpointRef.bus("Bus3"));
		PtdfSpec spec = new PtdfSpec(List.of(), List.of(new GeneratedDirectionSet("million", "million", sources, sinks,
				DirectionExpansion.CARTESIAN)), new MonitorSet(List.of(MONITOR), List.of()));
		CalculationOptions options = new CalculationOptions(DclfMethod.STD, null,
				ResultRetentionPolicy.THRESHOLDED, Double.MAX_VALUE, 10, 100.0, false, 256);
		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(1, "million", "million",
				new NetworkReference("", ""), EndpointCatalog.empty(), List.of(spec), options);
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		var manifest = new DefaultDcSensitivityRunner().run(net, study, sink);
		assertEquals(1_000_000, manifest.candidateCount());
		assertEquals(0, manifest.storedRowCount());
		assertTrue(sink.rows().isEmpty());
	}

	@Test
	void estimatorDetectsMillionCandidatePortableStudy() throws Exception {
		AclfNetwork net = loadIeee14();
		List<EndpointRef> sources = Collections.nCopies(1_000, EndpointRef.bus("Bus2"));
		List<EndpointRef> sinks = Collections.nCopies(1_000, EndpointRef.bus("Bus3"));
		PtdfSpec spec = new PtdfSpec(List.of(), List.of(new GeneratedDirectionSet("million", "million", sources, sinks,
				DirectionExpansion.CARTESIAN)), new MonitorSet(List.of(MONITOR), List.of()));
		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(1, "million", "million",
				new NetworkReference("", ""), EndpointCatalog.empty(), List.of(spec), CalculationOptions.defaults());
		assertEquals(1_000_000L, DcSensitivityStudyEstimator.estimateCandidateCount(net, study));
		assertTrue(DcSensitivityStudyEstimator.estimateCandidateCount(net, study)
				> DcSensitivityStudyEstimator.MAX_INLINE_RESULT_CANDIDATES);
	}

	@Test
	void cancellationLeavesAnIncompleteManifest() throws Exception {
		AclfNetwork net = loadIeee14();
		InMemorySensitivityResultSink sink = new InMemorySensitivityResultSink();
		try {
			Thread.currentThread().interrupt();
			assertThrows(java.util.concurrent.CancellationException.class,
					() -> new DefaultDcSensitivityRunner().run(net, busStudy("Bus2", "Bus3"), sink));
			assertNotNull(sink.manifest());
			assertFalse(sink.manifest().complete());
			assertTrue(sink.manifest().diagnostics().stream().anyMatch(d -> d.code().equals("CALCULATION_CANCELLED")));
		} finally {
			Thread.interrupted();
		}
	}

	private static DcSensitivityStudyDefinition busStudy(String source, String sink) {
		return study(EndpointRef.bus(source), EndpointRef.bus(sink), EndpointCatalog.empty());
	}

	private static DcSensitivityStudyDefinition study(EndpointRef source, EndpointRef sink, EndpointCatalog catalog) {
		return new DcSensitivityStudyDefinition(1, "study-1", "test", new NetworkReference("portable", ""),
				catalog, List.of(new PtdfSpec(List.of(new DcSensitivityStudyDefinition.Direction(
						"d1", "direction", source, sink, true)), new MonitorSet(List.of(MONITOR), List.of()))),
				new CalculationOptions(DclfMethod.STD, null, ResultRetentionPolicy.FULL, 0.0, 100, 100.0, false, 2));
	}

	private static AclfNetwork loadIeee14() throws Exception {
		AclfNetwork net = CorePluginFactory.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee").getAclfNet();
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setLfMethod(AclfMethodType.NR);
		assertTrue(algo.loadflow());
		return net;
	}
}
