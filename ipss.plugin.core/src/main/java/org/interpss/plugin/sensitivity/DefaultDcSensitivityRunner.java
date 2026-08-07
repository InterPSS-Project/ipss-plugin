package org.interpss.plugin.sensitivity;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.EndpointType.SYSTEM_SLACK;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;

import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.CalculationOptions;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.CandidateType;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.Direction;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.EndpointRef;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.EndpointType;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.GeneratorFactor;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.InjectionGroupDefinition;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.InjectionGroupMember;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.InterfaceMember;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.InterfaceAggregation;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.LinearInterface;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.MonitorSet;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.ParticipationProfile;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.ResultRetentionPolicy;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.SensitivitySpec;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.TransactorRole;
import org.interpss.plugin.sensitivity.SensitivityResult.Diagnostic;
import org.interpss.plugin.sensitivity.SensitivityResult.Diagnostic.Severity;
import org.interpss.plugin.sensitivity.SensitivityResult.Manifest;
import org.interpss.plugin.sensitivity.SensitivityResult.ResolvedEndpoint;
import org.interpss.plugin.sensitivity.SensitivityResult.Row;
import org.interpss.plugin.sensitivity.SensitivityResult.RunSnapshot;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.solver.DclfSensitivityAnalyzer;
import com.interpss.core.algo.dclf.solver.DclfSensitivityAnalyzer.EndpointSensitivityPanel;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfOutageBranch;
import com.interpss.core.funcImpl.AclfNetHelper;

/** Default runner that adds portable study semantics around the existing core solvers. */
public final class DefaultDcSensitivityRunner implements DcSensitivityRunner {
	private final DclfSensitivityAnalyzer analyzer;

	public DefaultDcSensitivityRunner() {
		this(new DclfSensitivityAnalyzer());
	}

	DefaultDcSensitivityRunner(DclfSensitivityAnalyzer analyzer) {
		this.analyzer = Objects.requireNonNull(analyzer);
	}

	@Override
	public Manifest run(BaseAclfNetwork<?, ?> network, DcSensitivityStudyDefinition study,
			SensitivityResultSink sink) {
		Objects.requireNonNull(network, "Network is required");
		Objects.requireNonNull(study, "Study is required");
		Objects.requireNonNull(sink, "Result sink is required");
		synchronized (network) {
			return runExclusive(network, study, sink);
		}
	}

	private Manifest runExclusive(BaseAclfNetwork<?, ?> network, DcSensitivityStudyDefinition study,
			SensitivityResultSink sink) {
		RunContext context = new RunContext(network, study, sink);
		Map<String, Boolean> originalBusStatus = snapshotBusStatus(network);
		try {
			context.prepareIslands();
			context.begin();
			for (SensitivitySpec spec : study.analyses()) context.run(spec);
			return context.complete();
		} catch (RuntimeException ex) {
			context.fail(ex);
			throw ex;
		} catch (Exception ex) {
			context.fail(ex);
			throw new IllegalStateException("DC sensitivity calculation failed", ex);
		} finally {
			restoreBusStatus(network, originalBusStatus);
			analyzer.clearCache();
		}
	}

	private final class RunContext {
		private final BaseAclfNetwork<?, ?> net;
		private final DcSensitivityStudyDefinition study;
		private final CalculationOptions options;
		private final SensitivityResultSink sink;
		private final String resultId = UUID.randomUUID().toString();
		private final List<Diagnostic> diagnostics = new ArrayList<>();
		private final List<String> disabledBusIds = new ArrayList<>();
		private final Map<String, ResolvedEndpoint> resolvedEndpoints = new LinkedHashMap<>();
		private final List<Row> block = new ArrayList<>();
		private long candidateCount;
		private long storedRowCount;
		private long blockFirstIndex;
		private boolean begun;
		private SensitivitySpec currentSpec;

		RunContext(BaseAclfNetwork<?, ?> net, DcSensitivityStudyDefinition study, SensitivityResultSink sink) {
			this.net = net;
			this.study = study;
			this.options = study.options();
			this.sink = sink;
		}

		void prepareIslands() {
			String configuredFingerprint = study.network().fingerprint();
			String actualFingerprint = networkFingerprint(net);
			if (!configuredFingerprint.isBlank() && !configuredFingerprint.equals(actualFingerprint)) {
				warning("NETWORK_FINGERPRINT_MISMATCH",
						"Study fingerprint differs from the active network; portable resolution will be used", configuredFingerprint);
			}
			AclfNetHelper helper = new AclfNetHelper(net);
			if (helper.checkSwingRefBus()) return;
			if (options.strict()) throw new IllegalArgumentException("Network contains an island without a reference bus");
			Map<String, Boolean> before = snapshotBusStatus(net);
			if (!helper.assignSwingBusTurnOffIslandBus()) {
				throw new IllegalStateException("Unable to isolate the reference-bus island");
			}
			for (BaseAclfBus<?, ?> bus : net.getBusList()) {
				if (Boolean.TRUE.equals(before.get(bus.getId())) && !bus.isActive()) disabledBusIds.add(bus.getId());
			}
			warning("NON_REFERENCE_ISLAND_DISABLED",
					"Temporarily disabled " + disabledBusIds.size() + " bus(es) outside the reference island", "");
		}

		void begin() {
			sink.begin(resultId, study.id(), study.analysisTypes(), snapshot());
			begun = true;
		}

		void run(SensitivitySpec spec) throws Exception {
			currentSpec = spec;
			if (spec instanceof DcSensitivityStudyDefinition.PtdfSpec ptdf) runPtdf(ptdf);
			else if (spec instanceof DcSensitivityStudyDefinition.ShiftFactorSpec shiftFactor) runShiftFactors(shiftFactor);
			else if (spec instanceof DcSensitivityStudyDefinition.LodfSpec lodf) runLodf(lodf);
			else if (spec instanceof DcSensitivityStudyDefinition.MultiOutageLodfSpec multi) runMultiOutageLodf(multi);
			else throw new IllegalArgumentException("Unsupported sensitivity specification " + spec.getClass().getName());
		}

		void runPtdf(DcSensitivityStudyDefinition.PtdfSpec spec) {
			List<Monitor> monitors = resolveMonitors(spec.monitors());
			runDirectionRefs(allDirections(spec), monitors);
		}

		void runShiftFactors(DcSensitivityStudyDefinition.ShiftFactorSpec spec) {
			ResolvedEndpoint transactor = resolve(spec.transactor(), spec.role() == TransactorRole.BUYER, "shift-factor transactor");
			if (transactor == null) return;
			List<ShiftCandidate> candidates = new ArrayList<>();
			for (EndpointRef endpoint : spec.explicitCandidates()) {
				candidates.add(new ShiftCandidate(endpoint, endpoint.type().name(), endpoint.targetId()));
			}
			for (CandidateType type : spec.candidateTypes()) candidates.addAll(generateCandidates(type));
			List<ResolvedDirection> directions = new ArrayList<>();
			Set<String> seen = new LinkedHashSet<>();
			for (ShiftCandidate candidate : candidates) {
				checkCancelled();
				String key = candidate.type() + ":" + candidate.id();
				if (!seen.add(key)) continue;
				ResolvedEndpoint resolvedCandidate = resolve(candidate.endpoint(), spec.role() == TransactorRole.SELLER,
						"shift-factor candidate " + candidate.id());
				if (resolvedCandidate == null) continue;
				if (transactor.busWeights().equals(resolvedCandidate.busWeights())) {
					warning("IDENTICAL_SOURCE_AND_SINK", "Shift-factor candidate resolves to the fixed transactor", candidate.id());
					continue;
				}
				Direction direction = spec.role() == TransactorRole.SELLER
						? new Direction(key, key, spec.transactor(), candidate.endpoint(), true)
						: new Direction(key, key, candidate.endpoint(), spec.transactor(), true);
				directions.add(spec.role() == TransactorRole.SELLER
						? new ResolvedDirection(direction, transactor, resolvedCandidate, candidate.type(), candidate.id())
						: new ResolvedDirection(direction, resolvedCandidate, transactor, candidate.type(), candidate.id()));
			}
			runTransfers(directions, resolveMonitors(spec.monitors()));
		}

		void runTransfers(List<ResolvedDirection> directions, List<Monitor> monitors) {
			if (directions.isEmpty() || monitors.isEmpty()) return;
			List<AclfBranch> panelBranches = uniqueMonitorBranches(monitors);
			ContingencyAnalysisAlgorithm algo = analyzer.createCalculatedDclfAlgorithm(net, options.method(), study.id());
			EndpointSensitivityPanel panel = analyzer.solveEndpointSensitivityPanel(
					algo, panelBranches, DclfSensitivityAnalyzer.DEFAULT_ENDPOINT_RHS_BATCH_SIZE);
			for (ResolvedDirection direction : directions) {
				checkCancelled();
				evaluateTransfer(direction, monitors, panel);
			}
		}

		void runDirectionRefs(Iterable<Direction> directions, List<Monitor> monitors) {
			if (monitors.isEmpty()) return;
			List<AclfBranch> panelBranches = uniqueMonitorBranches(monitors);
			ContingencyAnalysisAlgorithm algo = analyzer.createCalculatedDclfAlgorithm(net, options.method(), study.id());
			EndpointSensitivityPanel panel = analyzer.solveEndpointSensitivityPanel(
					algo, panelBranches, DclfSensitivityAnalyzer.DEFAULT_ENDPOINT_RHS_BATCH_SIZE);
			for (Direction direction : directions) {
				checkCancelled();
				if (!direction.included()) continue;
				if (direction.sink() == null) {
					if (options.strict()) {
						throw new IllegalArgumentException("Direction " + direction.id()
								+ " omits a sink and strict mode disables slack fallback");
					}
					warning("SINK_DEFAULTED_TO_SLACK", "Direction " + direction.id() + " omits a sink; using the slack bus", direction.id());
				}
				ResolvedEndpoint source = resolve(direction.source(), false, "direction " + direction.id() + " source");
				ResolvedEndpoint sinkEndpoint = resolve(direction.effectiveSink(), true, "direction " + direction.id() + " sink");
				if (source != null && sinkEndpoint != null) {
					if (source.busWeights().equals(sinkEndpoint.busWeights())) {
						String message = "Direction " + direction.id() + " resolves to identical source and sink participation";
						if (options.strict()) throw new IllegalArgumentException(message);
						warning("IDENTICAL_SOURCE_AND_SINK", message, direction.id());
						continue;
					}
					evaluateTransfer(new ResolvedDirection(direction, source, sinkEndpoint), monitors, panel);
				}
			}
		}

		private void evaluateTransfer(ResolvedDirection direction, List<Monitor> monitors, EndpointSensitivityPanel panel) {
			Map<String, Double> transfer = new LinkedHashMap<>();
			direction.source().busWeights().forEach((id, value) -> transfer.merge(id, value, Double::sum));
			direction.sink().busWeights().forEach((id, value) -> transfer.merge(id, -value, Double::sum));
			TopKAccumulator retained = options.retention() == ResultRetentionPolicy.TOP_K
					? new TopKAccumulator(Math.min(options.topK(), monitors.size())) : null;
			for (Monitor monitor : monitors) {
				double factor = 0.0;
				for (int i = 0; i < monitor.branches().size(); i++) {
					factor += monitor.coefficients().get(i) * branchFactor(monitor.branches().get(i), transfer, panel);
				}
				long candidateIndex = candidateCount++;
				switch (options.retention()) {
					case FULL -> emitTransferRow(direction, monitor.id(), factor, candidateIndex);
					case THRESHOLDED -> {
						if (Math.abs(factor) >= options.threshold()) {
							emitTransferRow(direction, monitor.id(), factor, candidateIndex);
						}
					}
					case TOP_K -> retained.offer(candidateIndex, monitor.id(), factor);
				}
			}
			if (retained != null) {
				for (FactorCandidate value : retained.descending()) {
					emitTransferRow(direction, value.monitorId(), value.factor(), value.candidateIndex());
				}
			}
		}

		private void emitTransferRow(ResolvedDirection direction, String monitorId, double factor, long candidateIndex) {
			emit(row(direction.direction().id(), direction.source(), direction.sink(), monitorId, "", factor,
					direction.candidateType(), direction.candidateId()), candidateIndex);
		}

		void runLodf(DcSensitivityStudyDefinition.LodfSpec spec) {
			List<Monitor> monitors = resolveMonitors(spec.monitors());
			ContingencyAnalysisAlgorithm algo = analyzer.createCalculatedDclfAlgorithm(net, options.method(), study.id());
			for (String outageId : spec.outageBranchIds()) {
				checkCancelled();
				AclfBranch outageBranch = activeBranch(outageId, "outage");
				if (outageBranch == null) continue;
				DclfOutageBranch outage = createCaOutageBranch(algo.getDclfAlgoBranch(outageId), ContingencyBranchOutageType.OPEN);
				long firstCandidateIndex = candidateCount;
				List<Row> rows = new ArrayList<>(monitors.size());
				for (Monitor monitor : monitors) {
					double factor = 0.0;
					for (int i = 0; i < monitor.branches().size(); i++) {
						AclfBranch branch = monitor.branches().get(i);
						double lodf = branch.getId().equals(outageId) ? -1.0 : analyzer.lineOutageDFactor(algo, outage, branch);
						factor += monitor.coefficients().get(i) * lodf;
					}
					candidateCount++;
					rows.add(row(outageId, null, null, monitor.id(), outageId, factor, "", ""));
				}
				emitRetained(rows, firstCandidateIndex);
			}
		}

		void runMultiOutageLodf(DcSensitivityStudyDefinition.MultiOutageLodfSpec spec) throws Exception {
			List<Monitor> monitors = resolveMonitors(spec.monitors());
			ContingencyAnalysisAlgorithm algo = analyzer.createCalculatedDclfAlgorithm(net, options.method(), study.id());
			for (var group : spec.outageGroups()) {
				checkCancelled();
				List<String> outageIds = new ArrayList<>();
				for (String id : group.branchIds()) if (activeBranch(id, "outage group " + group.id()) != null) outageIds.add(id);
				if (outageIds.isEmpty()) continue;
				algo.getOutageBranchList().clear();
				List<DclfOutageBranch> outageBranches = new ArrayList<>(outageIds.size());
				for (String id : outageIds) {
					DclfOutageBranch outage = createCaOutageBranch(
							algo.getDclfAlgoBranch(id), ContingencyBranchOutageType.OPEN);
					outageBranches.add(outage);
					algo.getOutageBranchList().add(outage);
				}
				Object inverse = algo.calMultiOutageInvE_PTDF(group.id());
				long firstCandidateIndex = candidateCount;
				List<Row> rows = new ArrayList<>();
				for (Monitor monitor : monitors) {
					double[] factors = new double[outageIds.size()];
					for (int i = 0; i < monitor.branches().size(); i++) {
						double[] branchFactors = algo.calMultiOutageLODFs(monitor.branches().get(i), inverse);
						for (int j = 0; j < factors.length; j++) {
							int factorIndex = outageBranches.get(j).getBranch().getSortNumber();
							if (factorIndex >= 0 && factorIndex < branchFactors.length) {
								factors[j] += monitor.coefficients().get(i) * branchFactors[factorIndex];
							}
						}
					}
					for (int j = 0; j < factors.length; j++) {
						candidateCount++;
						rows.add(row(group.id(), null, null, monitor.id(), outageIds.get(j), factors[j], "", ""));
					}
				}
				emitRetained(rows, firstCandidateIndex);
			}
		}

		private List<ShiftCandidate> generateCandidates(CandidateType type) {
			List<ShiftCandidate> result = new ArrayList<>();
			switch (type) {
				case BUS -> net.getBusList().stream().filter(BaseAclfBus::isActive)
						.forEach(bus -> result.add(new ShiftCandidate(EndpointRef.bus(bus.getId()), "BUS", bus.getId())));
				case GENERATOR -> net.getBusList().stream().filter(BaseAclfBus::isActive).forEach(bus ->
						bus.getContributeGenList().stream().filter(AclfGen::isActive).forEach(generator ->
								result.add(new ShiftCandidate(EndpointRef.bus(bus.getId()), "GENERATOR",
										bus.getId() + "/" + generator.getId()))));
				case LOAD -> net.getBusList().stream().filter(BaseAclfBus::isActive).forEach(bus ->
						bus.getContributeLoadList().stream().filter(AclfLoad::isActive).forEach(load ->
								result.add(new ShiftCandidate(EndpointRef.bus(bus.getId()), "LOAD",
										bus.getId() + "/" + load.getId()))));
				case AREA -> net.getAreaMap().keySet().forEach(id -> result.add(new ShiftCandidate(
						new EndpointRef(EndpointType.AREA, id, ""), "AREA", id)));
				case ZONE -> net.getZoneMap().keySet().forEach(id -> result.add(new ShiftCandidate(
						new EndpointRef(EndpointType.ZONE, id, ""), "ZONE", id)));
				case SUPER_AREA -> study.endpoints().superAreas().keySet().forEach(id -> result.add(new ShiftCandidate(
						new EndpointRef(EndpointType.SUPER_AREA, id, ""), "SUPER_AREA", id)));
				case INJECTION_GROUP -> study.endpoints().injectionGroups().keySet()
						.forEach(id -> result.add(new ShiftCandidate(
								new EndpointRef(EndpointType.INJECTION_GROUP, id, ""), "INJECTION_GROUP", id)));
			}
			return result;
		}

		private ResolvedEndpoint resolve(EndpointRef ref, boolean sinkRole, String label) {
			String endpointKey = endpointKey(ref);
			String roleKey = endpointKey + (sinkRole ? "|SINK" : "|SOURCE");
			ResolvedEndpoint cached = resolvedEndpoints.get(roleKey);
			if (cached == null) cached = resolvedEndpoints.get(endpointKey);
			if (cached != null) return cached.busWeights().isEmpty() ? null : cached;
			List<Diagnostic> local = new ArrayList<>();
			Map<String, Double> raw = new LinkedHashMap<>();
			boolean slackFallback = false;
			switch (ref.type()) {
				case BUS -> addBus(raw, ref.targetId(), 1.0, local, label);
				case SYSTEM_SLACK -> addSlack(raw, local, label);
				case AREA -> addRegionGenerators(raw, ref.targetId(), true, 1.0, ref.participationProfileId(), local, label);
				case ZONE -> addRegionGenerators(raw, ref.targetId(), false, 1.0, ref.participationProfileId(), local, label);
				case SUPER_AREA -> addSuperArea(raw, ref, local, label);
				case INJECTION_GROUP -> addInjectionGroup(raw, ref.targetId(), 1.0, new HashSet<>(), local, label);
			}
			if (raw.isEmpty() && sinkRole && ref.type() != SYSTEM_SLACK && !options.strict()) {
				slackFallback = true;
				Diagnostic fallback = new Diagnostic(Severity.WARNING, "SINK_FALLBACK_TO_SLACK",
						label + " did not resolve; using the network slack bus", ref.targetId());
				local.add(fallback); diagnostics.add(fallback);
				addSlack(raw, local, label);
			}
			if (ref.type() == EndpointType.INJECTION_GROUP && !raw.isEmpty()) {
				double total = raw.values().stream().mapToDouble(Double::doubleValue).sum();
				if (Double.isFinite(total) && Math.abs(total - 1.0) > 1.0e-9) {
					Diagnostic diagnostic = new Diagnostic(Severity.WARNING,
							"INJECTION_GROUP_FACTORS_NORMALIZED",
							label + " participation factors total " + total
									+ "; factors were normalized to 1.0 (100%)",
							ref.targetId());
					local.add(diagnostic);
					diagnostics.add(diagnostic);
				}
			}
			Map<String, Double> normalized = normalize(raw);
			if (normalized.isEmpty()) {
				String message = label + " has no active participants";
				if (options.strict()) throw new IllegalArgumentException(message);
				Diagnostic diagnostic = new Diagnostic(Severity.WARNING, "ENDPOINT_SKIPPED", message, ref.targetId());
				local.add(diagnostic); diagnostics.add(diagnostic);
			}
			ResolvedEndpoint result = new ResolvedEndpoint(ref.type().name(), ref.targetId(),
					normalized.isEmpty() ? "UNRESOLVED" : (slackFallback ? SYSTEM_SLACK.name() : ref.type().name()),
					ref.type() == SYSTEM_SLACK || slackFallback ? slackBusId() : ref.targetId(), normalized, local);
			resolvedEndpoints.put(slackFallback || normalized.isEmpty() ? roleKey : endpointKey, result);
			return normalized.isEmpty() ? null : result;
		}

		private void addSuperArea(Map<String, Double> raw, EndpointRef ref, List<Diagnostic> local, String label) {
			var definition = study.endpoints().superAreas().get(ref.targetId());
			if (definition == null) { missing(local, "SUPER_AREA_NOT_FOUND", label, ref.targetId()); return; }
			for (var area : definition.areas()) {
				addRegionGenerators(raw, area.areaId(), true, area.weight(), ref.participationProfileId(), local, label);
			}
		}

		private void addRegionGenerators(Map<String, Double> raw, String regionId, boolean area, double multiplier,
				String profileId, List<Diagnostic> local, String label) {
			if ((area ? net.getArea(regionId) : net.getZone(regionId)) == null) {
				missing(local, area ? "AREA_NOT_FOUND" : "ZONE_NOT_FOUND", label, regionId); return;
			}
			ParticipationProfile profile = profileId.isBlank() ? null : study.endpoints().participationProfiles().get(profileId);
			if (profileId.isBlank() && options.defaultParticipation() == DcSensitivityStudyDefinition.ParticipationPolicy.EXPLICIT_FACTORS) {
				missing(local, "PARTICIPATION_PROFILE_REQUIRED", label, regionId);
				return;
			}
			if (!profileId.isBlank() && profile == null) { missing(local, "PARTICIPATION_PROFILE_NOT_FOUND", label, profileId); return; }
			Map<String, Double> explicit = new HashMap<>();
			if (profile != null) for (GeneratorFactor factor : profile.factors()) {
				BaseAclfBus<?, ?> factorBus = net.getBus(factor.busId());
				AclfGen factorGenerator = factorBus == null ? null : factorBus.getContributeGen(factor.generatorId());
				if (factorBus == null || !factorBus.isActive() || factorGenerator == null || !factorGenerator.isActive()) {
					missing(local, "PARTICIPATION_GENERATOR_NOT_FOUND_OR_INACTIVE", label,
							factor.busId() + "/" + factor.generatorId());
					continue;
				}
				explicit.put(factor.busId() + "\u0000" + factor.generatorId(), factor.factor());
			}
			for (BaseAclfBus<?, ?> bus : net.getBusList()) {
				if (!bus.isActive() || !(area ? regionId.equals(bus.getAreaId()) : regionId.equals(bus.getZoneId()))) continue;
				for (AclfGen gen : bus.getContributeGenList()) {
					if (!gen.isActive()) continue;
					double factor = profile == null ? 1.0 : explicit.getOrDefault(bus.getId() + "\u0000" + gen.getId(), 0.0);
					if (factor > 0.0) raw.merge(bus.getId(), multiplier * factor, Double::sum);
				}
			}
		}

		private void addInjectionGroup(Map<String, Double> raw, String groupId, double multiplier, Set<String> path,
				List<Diagnostic> local, String label) {
			InjectionGroupDefinition group = study.endpoints().injectionGroups().get(groupId);
			if (group == null) { missing(local, "INJECTION_GROUP_NOT_FOUND", label, groupId); return; }
			if (!path.add(groupId)) throw new IllegalArgumentException("Injection group cycle detected at " + groupId);
			for (InjectionGroupMember member : group.members()) {
				double weight = multiplier * member.weight();
				switch (member.type()) {
					case BUS -> addBus(raw, member.busId(), weight, local, label);
					case GENERATOR -> addGenerator(raw, member.busId(), member.deviceId(), weight, local, label);
					case LOAD -> addLoad(raw, member.busId(), member.deviceId(), weight, local, label);
					case INJECTION_GROUP -> addInjectionGroup(raw, member.groupId(), weight, path, local, label);
				}
			}
			path.remove(groupId);
		}

		private void addBus(Map<String, Double> raw, String busId, double weight, List<Diagnostic> local, String label) {
			BaseAclfBus<?, ?> bus = net.getBus(busId);
			if (bus == null || !bus.isActive()) { missing(local, "BUS_NOT_FOUND_OR_INACTIVE", label, busId); return; }
			raw.merge(busId, weight, Double::sum);
		}

		private void addGenerator(Map<String, Double> raw, String busId, String genId, double weight,
				List<Diagnostic> local, String label) {
			BaseAclfBus<?, ?> bus = net.getBus(busId);
			AclfGen gen = bus == null ? null : bus.getContributeGen(genId);
			if (bus == null || !bus.isActive() || gen == null || !gen.isActive()) {
				missing(local, "GENERATOR_NOT_FOUND_OR_INACTIVE", label, busId + "/" + genId); return;
			}
			raw.merge(busId, weight, Double::sum);
		}

		private void addLoad(Map<String, Double> raw, String busId, String loadId, double weight,
				List<Diagnostic> local, String label) {
			BaseAclfBus<?, ?> bus = net.getBus(busId);
			AclfLoad load = bus == null ? null : bus.getContributeLoad(loadId);
			if (bus == null || !bus.isActive() || load == null || !load.isActive()) {
				missing(local, "LOAD_NOT_FOUND_OR_INACTIVE", label, busId + "/" + loadId); return;
			}
			raw.merge(busId, weight, Double::sum);
		}

		private void addSlack(Map<String, Double> raw, List<Diagnostic> local, String label) {
			String id = slackBusId();
			if (id.isBlank()) { missing(local, "SLACK_BUS_NOT_FOUND", label, ""); return; }
			addBus(raw, id, 1.0, local, label);
		}

		private String slackBusId() {
			try {
				String id = net.getRefBusId();
				if (id != null && !id.isBlank() && net.getBus(id) != null && net.getBus(id).isActive()) return id;
			} catch (RuntimeException ignored) {
				// Some imported cases identify the reference only through the bus model.
			}
			return net.getBusList().stream().filter(BaseAclfBus::isActive).filter(bus -> bus.isRefBus() || bus.isSwing())
					.map(BaseAclfBus::getId).findFirst().orElse("");
		}

		private void missing(List<Diagnostic> local, String code, String label, String id) {
			String message = label + " references a missing or inactive object: " + id;
			if (options.strict()) throw new IllegalArgumentException(message);
			Diagnostic diagnostic = new Diagnostic(Severity.WARNING, code, message, id);
			local.add(diagnostic); diagnostics.add(diagnostic);
		}

		private List<Monitor> resolveMonitors(MonitorSet set) {
			List<Monitor> result = new ArrayList<>();
			List<String> branchIds = set.branchIds().isEmpty() && set.interfaces().isEmpty()
					? net.getBranchList().stream().filter(AclfBranch::isActive).map(AclfBranch::getId).toList()
					: set.branchIds();
			for (String id : branchIds) {
				AclfBranch branch = activeBranch(id, "monitor");
				if (branch != null) result.add(new Monitor(id, List.of(branch), List.of(1.0)));
			}
			for (LinearInterface definition : set.interfaces()) {
				List<AclfBranch> branches = new ArrayList<>();
				List<Double> coefficients = new ArrayList<>();
				for (InterfaceMember member : definition.members()) {
					AclfBranch branch = activeBranch(member.branchId(), "interface " + definition.id());
					if (branch != null) { branches.add(branch); coefficients.add(member.coefficient()); }
				}
				if (!coefficients.isEmpty()
						&& definition.aggregation() == InterfaceAggregation.NORMALIZED_WEIGHTED_SUM) {
					double absoluteWeightTotal = coefficients.stream()
							.mapToDouble(Math::abs)
							.sum();
					if (!(absoluteWeightTotal > 0.0) || !Double.isFinite(absoluteWeightTotal)) {
						throw new IllegalArgumentException("Interface " + definition.id()
								+ " has an invalid absolute member weight total: " + absoluteWeightTotal);
					}
					if (Math.abs(absoluteWeightTotal - 1.0) > 1.0e-9) {
						String message = "Interface " + definition.id() + " absolute member weights total "
								+ absoluteWeightTotal + "; expected 1.0";
						if (options.strict()) throw new IllegalArgumentException(message);
						warning("INTERFACE_WEIGHTS_NORMALIZED", message + "; weights were normalized",
								definition.id());
						for (int i = 0; i < coefficients.size(); i++) {
							coefficients.set(i, coefficients.get(i) / absoluteWeightTotal);
						}
					}
				}
				if (!branches.isEmpty()) result.add(new Monitor(definition.id(), branches, coefficients));
			}
			return result;
		}

		private List<AclfBranch> uniqueMonitorBranches(List<Monitor> monitors) {
			Map<String, AclfBranch> unique = new LinkedHashMap<>();
			for (Monitor monitor : monitors) {
				for (AclfBranch branch : monitor.branches()) unique.putIfAbsent(branch.getId(), branch);
			}
			return List.copyOf(unique.values());
		}

		private AclfBranch activeBranch(String id, String label) {
			AclfBranch branch = net.getBranch(id);
			if (branch != null && branch.isActive()) return branch;
			String message = label + " branch is missing or inactive: " + id;
			if (options.strict()) throw new IllegalArgumentException(message);
			warning("BRANCH_NOT_FOUND_OR_INACTIVE", message, id);
			return null;
		}

		private double branchFactor(AclfBranch branch, Map<String, Double> transfer, EndpointSensitivityPanel panel) {
			int from = sortNumber(branch.getFromAclfBus());
			int to = sortNumber(branch.getToAclfBus());
			double angle = 0.0;
			for (var entry : transfer.entrySet()) {
				int injection = sortNumber(net.getBus(entry.getKey()));
				if (injection < 0) continue;
				double fromValue = from < 0 ? 0.0 : panel.value(from, injection);
				double toValue = to < 0 ? 0.0 : panel.value(to, injection);
				angle += entry.getValue() * (fromValue - toValue);
			}
			double reactance = branch.getAdjustedZ().getImaginary();
			if (reactance == 0.0 || !Double.isFinite(reactance)) return 0.0;
			return -(1.0 / reactance) * angle;
		}

		private Row row(String directionId, ResolvedEndpoint source, ResolvedEndpoint sinkEndpoint,
				String monitorId, String outageId, double factor, String candidateType, String candidateId) {
			return new Row(currentSpec.type(), directionId,
					source == null ? "" : source.configuredId(), sinkEndpoint == null ? "" : sinkEndpoint.resolvedId(),
					monitorId, outageId, candidateType, candidateId, factor, options.transferMw(), factor * options.transferMw());
		}

		private void emitRetained(List<Row> rows, long firstCandidateIndex) {
			List<Row> retained = switch (options.retention()) {
				case FULL -> rows;
				case THRESHOLDED -> rows.stream().filter(row -> Math.abs(row.factor()) >= options.threshold()).toList();
				case TOP_K -> rows.stream().sorted(Comparator.comparingDouble((Row row) -> Math.abs(row.factor())).reversed())
						.limit(options.topK()).toList();
			};
			for (Row row : retained) {
				emit(row, firstCandidateIndex++);
			}
		}

		private void emit(Row row, long candidateIndex) {
			if (block.isEmpty()) blockFirstIndex = candidateIndex;
			block.add(row);
			storedRowCount++;
			if (block.size() >= options.resultBlockSize()) flush();
		}

		private void flush() {
			if (block.isEmpty()) return;
			sink.accept(new SensitivityResult.Block(blockFirstIndex, List.copyOf(block)));
			block.clear();
		}

		Manifest complete() {
			flush();
			Manifest manifest = manifest(true, List.of());
			return sink.complete(manifest);
		}

		void fail(Throwable error) {
			if (begun) {
				String code = error instanceof CancellationException ? "CALCULATION_CANCELLED" : "CALCULATION_FAILED";
				Diagnostic diagnostic = new Diagnostic(Severity.ERROR, code,
						error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(), "");
				diagnostics.add(diagnostic);
				flush();
				sink.fail(manifest(false, List.of()), error);
			}
		}

		private Manifest manifest(boolean complete, List<String> partitions) {
			return new Manifest(resultId, study.id(), study.analysisTypes(), candidateCount, storedRowCount,
					complete, snapshot(), partitions, diagnostics);
		}

		private RunSnapshot snapshot() {
			return new RunSnapshot(study.id(), study.schemaVersion(), study.network().uri(), networkFingerprint(net),
					disabledBusIds, resolvedEndpoints, diagnostics);
		}

		private void warning(String code, String message, String objectId) {
			diagnostics.add(new Diagnostic(Severity.WARNING, code, message, objectId));
		}

		private void checkCancelled() {
			if (Thread.currentThread().isInterrupted()) throw new CancellationException("Sensitivity study was cancelled");
		}
	}

	private static Iterable<Direction> allDirections(DcSensitivityStudyDefinition.PtdfSpec spec) {
		return () -> new Iterator<>() {
			private final Iterator<Direction> explicit = spec.directions().iterator();
			private int setIndex;
			private int sourceIndex;
			private int sinkIndex;
			private Direction next;

			@Override public boolean hasNext() {
				if (next != null) return true;
				if (explicit.hasNext()) { next = explicit.next(); return true; }
				while (setIndex < spec.generatedDirections().size()) {
					var set = spec.generatedDirections().get(setIndex);
					if (set.sources().isEmpty() || set.sinks().isEmpty() || sourceIndex >= set.sources().size()) {
						setIndex++; sourceIndex = 0; sinkIndex = 0; continue;
					}
					EndpointRef source = set.sources().get(sourceIndex);
					EndpointRef sink = set.expansion() == DcSensitivityStudyDefinition.DirectionExpansion.PAIRED
							? set.sinks().get(sourceIndex) : set.sinks().get(sinkIndex);
					String id = set.id() + "-" + sourceIndex + "-" + sinkIndex;
					next = new Direction(id, set.name(), source, sink, true);
					if (set.expansion() == DcSensitivityStudyDefinition.DirectionExpansion.PAIRED) {
						sourceIndex++;
					} else if (++sinkIndex >= set.sinks().size()) {
						sinkIndex = 0; sourceIndex++;
					}
					return true;
				}
				return false;
			}

			@Override public Direction next() {
				if (!hasNext()) throw new NoSuchElementException();
				Direction value = next; next = null; return value;
			}
		};
	}

	private record Monitor(String id, List<AclfBranch> branches, List<Double> coefficients) {}
	private record FactorCandidate(long candidateIndex, String monitorId, double factor) {}
	private record ShiftCandidate(EndpointRef endpoint, String type, String id) {}
	private record ResolvedDirection(Direction direction, ResolvedEndpoint source, ResolvedEndpoint sink,
			String candidateType, String candidateId) {
		ResolvedDirection(Direction direction, ResolvedEndpoint source, ResolvedEndpoint sink) {
			this(direction, source, sink, "", "");
		}
	}

	/** Fixed-size primitive accumulator; rejected candidates allocate no objects. */
	private static final class TopKAccumulator {
		private final long[] candidateIndexes;
		private final String[] monitorIds;
		private final double[] factors;
		private int size;

		TopKAccumulator(int limit) {
			candidateIndexes = new long[Math.max(0, limit)];
			monitorIds = new String[candidateIndexes.length];
			factors = new double[candidateIndexes.length];
		}

		void offer(long candidateIndex, String monitorId, double factor) {
			if (factors.length == 0) return;
			int slot;
			if (size < factors.length) {
				slot = size++;
			} else {
				slot = 0;
				for (int i = 1; i < size; i++) {
					if (Math.abs(factors[i]) < Math.abs(factors[slot])) slot = i;
				}
				if (Math.abs(factor) <= Math.abs(factors[slot])) return;
			}
			candidateIndexes[slot] = candidateIndex;
			monitorIds[slot] = monitorId;
			factors[slot] = factor;
		}

		List<FactorCandidate> descending() {
			List<FactorCandidate> result = new ArrayList<>(size);
			for (int i = 0; i < size; i++) result.add(new FactorCandidate(candidateIndexes[i], monitorIds[i], factors[i]));
			result.sort(Comparator.comparingDouble((FactorCandidate value) -> Math.abs(value.factor())).reversed());
			return result;
		}
	}

	private static int sortNumber(BaseAclfBus<?, ?> bus) {
		return bus == null || bus.isRefBus() ? -1 : bus.getSortNumber();
	}

	private static Map<String, Double> normalize(Map<String, Double> raw) {
		double total = raw.values().stream().mapToDouble(Double::doubleValue).sum();
		if (!(total > 0.0) || !Double.isFinite(total)) return Map.of();
		Map<String, Double> normalized = new LinkedHashMap<>();
		raw.forEach((id, value) -> normalized.put(id, value / total));
		return normalized;
	}

	private static String endpointKey(EndpointRef ref) {
		return ref.type() + ":" + ref.targetId() + ":" + ref.participationProfileId();
	}

	private static Map<String, Boolean> snapshotBusStatus(BaseAclfNetwork<?, ?> net) {
		Map<String, Boolean> result = new LinkedHashMap<>();
		for (BaseAclfBus<?, ?> bus : net.getBusList()) result.put(bus.getId(), bus.isStatus());
		return result;
	}

	private static void restoreBusStatus(BaseAclfNetwork<?, ?> net, Map<String, Boolean> status) {
		for (BaseAclfBus<?, ?> bus : net.getBusList()) {
			Boolean original = status.get(bus.getId());
			if (original != null && bus.isStatus() != original) bus.setStatus(original);
		}
	}

	private static String networkFingerprint(BaseAclfNetwork<?, ?> net) {
		return net.getId() + ":" + net.getNoBus() + ":" + net.getNoBranch();
	}
}
