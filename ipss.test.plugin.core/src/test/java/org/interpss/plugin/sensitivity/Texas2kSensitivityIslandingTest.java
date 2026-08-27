package org.interpss.plugin.sensitivity;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.AnalysisType;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.CalculationOptions;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.Direction;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.EndpointCatalog;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.EndpointRef;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.MonitorSet;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.NetworkReference;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.PtdfSpec;
import org.interpss.plugin.sensitivity.SensitivityResult.Block;
import org.interpss.plugin.sensitivity.SensitivityResult.Manifest;
import org.interpss.plugin.sensitivity.SensitivityResult.RunSnapshot;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.funcImpl.AclfNetHelper;

import org.interpss.fadapter.psse.PSSEDirectParser;
class Texas2kSensitivityIslandingTest extends CorePluginTestSetup {
	private static final String CASE =
			"testData/adpter/psse/v36/Texas2k_series24_case1_2016summerPeak_v36_labeled.RAW";
	private static final String SOURCE_BUS = "Bus1004";
	private static final String SINK_BUS = "Bus1006";
	private static final String MONITOR_BRANCH = "Bus1001->Bus1064(1)";

	@Test
	void disablesSecondIslandBeforeTexas2kDcSensitivityAndRestoresItAfterward() throws Exception {
		TwoIslandCase fixture = createTwoIslandCase();
		AclfNetwork net = fixture.net();
		BaseAclfBus<?, ?> islandBus = fixture.islandBus();

		DcSensitivityStudyDefinition study = new DcSensitivityStudyDefinition(
				DcSensitivityStudyDefinition.CURRENT_SCHEMA_VERSION,
				"texas2k-two-islands", "Texas 2K two-island PTDF",
				new NetworkReference(CASE, ""), EndpointCatalog.empty(),
				List.of(new PtdfSpec(List.of(new Direction("west-transfer", "West transfer",
						EndpointRef.bus(SOURCE_BUS), EndpointRef.bus(SINK_BUS), true)),
						new MonitorSet(List.of(MONITOR_BRANCH), List.of()))),
				CalculationOptions.defaults());

		InMemorySensitivityResultSink results = new InMemorySensitivityResultSink();
		AtomicBoolean preflightObserved = new AtomicBoolean();
		SensitivityResultSink observingSink = new SensitivityResultSink() {
			@Override
			public void begin(String resultId, String studyId, List<AnalysisType> types, RunSnapshot snapshot) {
				assertFalse(islandBus.isActive(), "The non-reference island must be disabled before analysis begins");
				assertEquals(List.of(islandBus.getId()), snapshot.temporarilyDisabledBusIds());
				preflightObserved.set(true);
				results.begin(resultId, studyId, types, snapshot);
			}

			@Override
			public void accept(Block block) {
				assertFalse(islandBus.isActive(), "The non-reference island must remain disabled during analysis");
				results.accept(block);
			}

			@Override public Manifest complete(Manifest manifest) { return results.complete(manifest); }
			@Override public Manifest fail(Manifest manifest, Throwable error) { return results.fail(manifest, error); }
		};

		Manifest manifest = new DefaultDcSensitivityRunner().run(net, study, observingSink);

		assertTrue(preflightObserved.get());
		assertTrue(manifest.complete());
		assertEquals(1, manifest.candidateCount());
		assertEquals(1, results.rows().size());
		assertTrue(Double.isFinite(results.rows().getFirst().factor()));
		assertTrue(manifest.diagnostics().stream()
				.anyMatch(diagnostic -> diagnostic.code().equals("NON_REFERENCE_ISLAND_DISABLED")
						&& diagnostic.message().contains("1 bus(es)")));
		assertTrue(islandBus.isActive(), "The runner must restore the caller's original bus status");
	}

	@Test
	void directTexas2kPtdfWithoutIslandingPreflightFailsOnSingularMatrix() throws Exception {
		TwoIslandCase fixture = createTwoIslandCase();
		ContingencyAnalysisAlgorithm direct = createContingencyAnalysisAlgorithm(fixture.net());

		assertFalse(direct.calculateDclf(DclfMethod.STD),
				"Unprocessed two-island B matrix must not be accepted as a valid DCLF solution");
		assertFalse(direct.isDclfCalculated());
		double invalidFactor = direct.pTransferDistFactor(
				SOURCE_BUS, SINK_BUS, fixture.net().getBranch(MONITOR_BRANCH));
		assertEquals(0.0, invalidFactor, 0.0,
				"The direct PTDF API currently converts its internal matrix error to a zero factor");
		Exception matrixFailure = assertThrows(Exception.class,
				() -> direct.getDclfSolver().getSenPAngle(SOURCE_BUS));
		String failureText = exceptionMessages(matrixFailure).toLowerCase();
		assertTrue(failureText.contains("singular") || failureText.contains("b1ii = 0"),
				() -> "Expected a singular-matrix failure but received: " + exceptionMessages(matrixFailure));
	}

	private static TwoIslandCase createTwoIslandCase() throws Exception {
		AclfNetwork net = loadTexas2k();
		AclfNetHelper connectedHelper = new AclfNetHelper(net);
		assertTrue(connectedHelper.checkSwingRefBus(), "Texas 2K fixture must start as one reference-bus island");
		String referenceBusId = net.getBusList().stream()
				.filter(BaseAclfBus::isActive)
				.filter(bus -> bus.isRefBus() || bus.isSwing())
				.map(BaseAclfBus::getId)
				.findFirst()
				.orElseThrow(() -> new AssertionError("Texas 2K fixture has no active reference bus"));

		Set<String> protectedBuses = Set.of(SOURCE_BUS, SINK_BUS,
				net.getBranch(MONITOR_BRANCH).getFromBusId(), net.getBranch(MONITOR_BRANCH).getToBusId());
		BaseAclfBus<?, ?> islandBus = net.getBusList().stream()
				.filter(BaseAclfBus::isActive)
				.filter(bus -> !bus.isRefBus() && !bus.isSwing())
				.filter(bus -> !protectedBuses.contains(bus.getId()))
				.filter(bus -> bus.getContributeGenList().stream().noneMatch(generator -> generator.isActive()))
				.filter(bus -> activeIncidentBranches(net, bus.getId()).size() == 1)
				.findFirst()
				.orElseThrow(() -> new AssertionError("Texas 2K fixture has no suitable radial non-generator bus"));
		AclfBranch islandTie = activeIncidentBranches(net, islandBus.getId()).getFirst();
		islandTie.setStatus(false);

		AclfNetHelper islandedHelper = new AclfNetHelper(net);
		assertFalse(islandedHelper.checkSwingRefBus(), "The opened radial tie must create an island without a reference bus");
		Set<String> referenceIsland = islandedHelper.calConnectedSubArea(referenceBusId);
		long activeBusCount = net.getBusList().stream().filter(BaseAclfBus::isActive).count();
		assertEquals(activeBusCount - 1, referenceIsland.size(), "The case must contain exactly two active islands");
		assertFalse(referenceIsland.contains(islandBus.getId()));
		return new TwoIslandCase(net, islandBus);
	}

	private static String exceptionMessages(Throwable error) {
		StringBuilder messages = new StringBuilder();
		for (Throwable current = error; current != null; current = current.getCause()) {
			if (current.getMessage() != null) messages.append(current.getMessage()).append(' ');
		}
		return messages.toString().trim();
	}

	private static List<AclfBranch> activeIncidentBranches(AclfNetwork net, String busId) {
		return net.getBranchList().stream()
				.filter(AclfBranch::isActive)
				.filter(branch -> busId.equals(branch.getFromBusId()) || busId.equals(branch.getToBusId()))
				.toList();
	}

	private static AclfNetwork loadTexas2k() throws Exception {
		return new PSSEDirectParser().parse(CASE);
	}

	private record TwoIslandCase(AclfNetwork net, BaseAclfBus<?, ?> islandBus) {}
}
