package org.interpss.plugin.sensitivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.interpss.core.algo.dclf.DclfMethod;

/** Portable, versioned definition of a lossless-DC sensitivity study. */
public record DcSensitivityStudyDefinition(
		int schemaVersion,
		String id,
		String name,
		NetworkReference network,
		EndpointCatalog endpoints,
		List<SensitivitySpec> analyses,
		CalculationOptions options) {

	public static final int CURRENT_SCHEMA_VERSION = 2;

	public DcSensitivityStudyDefinition {
		if (schemaVersion < CURRENT_SCHEMA_VERSION) schemaVersion = CURRENT_SCHEMA_VERSION;
		id = text(id);
		name = text(name);
		network = network == null ? new NetworkReference("", "") : network;
		endpoints = endpoints == null ? EndpointCatalog.empty() : endpoints;
		analyses = list(analyses);
		if (analyses.isEmpty()) throw new IllegalArgumentException("At least one sensitivity specification is required");
		if (analyses.stream().anyMatch(java.util.Objects::isNull))
			throw new IllegalArgumentException("Sensitivity specifications cannot contain null");
		options = options == null ? CalculationOptions.defaults() : options;
	}

	/** Convenience API for a study containing exactly one sensitivity specification. */
	public DcSensitivityStudyDefinition(int schemaVersion, String id, String name,
			NetworkReference network, EndpointCatalog endpoints, AnalysisType analysisType,
			SensitivitySpec analysis, CalculationOptions options) {
		this(schemaVersion, id, name, network, endpoints,
				List.of(requireMatchingAnalysis(analysisType, analysis)), options);
	}

	public List<AnalysisType> analysisTypes() { return analyses.stream().map(SensitivitySpec::type).toList(); }

	private static SensitivitySpec requireMatchingAnalysis(AnalysisType analysisType, SensitivitySpec analysis) {
		if (analysisType == null) throw new IllegalArgumentException("Sensitivity analysis type is required");
		if (analysis == null) throw new IllegalArgumentException("Sensitivity specification is required");
		if (analysis.type() != analysisType) {
			throw new IllegalArgumentException("Sensitivity specification type " + analysis.type()
					+ " does not match requested analysis type " + analysisType);
		}
		return analysis;
	}

	private static String text(String value) { return value == null ? "" : value.trim(); }
	private static <T> List<T> list(List<T> value) { return value == null ? List.of() : List.copyOf(value); }
	private static <K,V> Map<K,V> map(Map<K,V> value) { return value == null ? Map.of() : Map.copyOf(value); }

	public enum AnalysisType { PTDF, SHIFT_FACTOR, LODF, MULTI_OUTAGE_LODF }
	public enum EndpointType { BUS, AREA, ZONE, SUPER_AREA, INJECTION_GROUP, SYSTEM_SLACK }
	public enum InjectionMemberType { GENERATOR, LOAD, BUS, INJECTION_GROUP }
	public enum CandidateType { BUS, GENERATOR, LOAD, AREA, ZONE, SUPER_AREA, INJECTION_GROUP }
	public enum TransactorRole { SELLER, BUYER }
	public enum ParticipationPolicy { EQUAL_ACTIVE_GENERATORS, EXPLICIT_FACTORS }
	public enum ResultRetentionPolicy { FULL, THRESHOLDED, TOP_K }
	public enum InterfaceAggregation {
		/** Preserve configured coefficients and sum the signed member-line responses. */
		SUM,
		/** Normalize signed coefficients by their absolute total before aggregation. */
		NORMALIZED_WEIGHTED_SUM
	}
	public enum DirectionExpansion { CARTESIAN, PAIRED }
	public sealed interface SensitivitySpec permits PtdfSpec, ShiftFactorSpec, LodfSpec, MultiOutageLodfSpec {
		AnalysisType type();
		MonitorSet monitors();
	}

	public record NetworkReference(String uri, String fingerprint) {
		public NetworkReference { uri = text(uri); fingerprint = text(fingerprint); }
	}

	public record EndpointCatalog(
			Map<String, SuperAreaDefinition> superAreas,
			Map<String, InjectionGroupDefinition> injectionGroups,
			Map<String, ParticipationProfile> participationProfiles) {
		public EndpointCatalog {
			superAreas = map(superAreas); injectionGroups = map(injectionGroups);
			participationProfiles = map(participationProfiles);
		}
		public static EndpointCatalog empty() { return new EndpointCatalog(Map.of(), Map.of(), Map.of()); }
	}

	public record EndpointRef(EndpointType type, String targetId, String participationProfileId) {
		public EndpointRef {
			if (type == null) throw new IllegalArgumentException("Endpoint type is required");
			targetId = text(targetId); participationProfileId = text(participationProfileId);
			if (type != EndpointType.SYSTEM_SLACK && targetId.isBlank())
				throw new IllegalArgumentException(type + " target id is required");
		}
		public static EndpointRef bus(String id) { return new EndpointRef(EndpointType.BUS, id, ""); }
		public static EndpointRef slack() { return new EndpointRef(EndpointType.SYSTEM_SLACK, "", ""); }
	}

	public record SuperAreaDefinition(String id, String name, List<WeightedAreaRef> areas) {
		public SuperAreaDefinition { id = text(id); name = text(name); areas = list(areas); }
	}
	public record WeightedAreaRef(String areaId, double weight) {
		public WeightedAreaRef { areaId = text(areaId); if (!(weight > 0)) weight = 1.0; }
	}

	public record InjectionGroupDefinition(String id, String name, List<InjectionGroupMember> members) {
		public InjectionGroupDefinition { id = text(id); name = text(name); members = list(members); }
	}
	public record InjectionGroupMember(
			InjectionMemberType type, String busId, String deviceId, String groupId, double weight) {
		public InjectionGroupMember {
			if (type == null) throw new IllegalArgumentException("Injection group member type is required");
			busId = text(busId); deviceId = text(deviceId); groupId = text(groupId);
			if (!(weight > 0) || !Double.isFinite(weight)) throw new IllegalArgumentException("Member weight must be positive");
		}
	}

	public record ParticipationProfile(String id, String name, List<GeneratorFactor> factors) {
		public ParticipationProfile { id = text(id); name = text(name); factors = list(factors); }
	}
	public record GeneratorFactor(String busId, String generatorId, double factor) {
		public GeneratorFactor {
			busId = text(busId); generatorId = text(generatorId);
			if (!(factor > 0) || !Double.isFinite(factor)) throw new IllegalArgumentException("Generator factor must be positive");
		}
	}

	public record Direction(String id, String name, EndpointRef source, EndpointRef sink, boolean included) {
		public Direction { id = text(id); name = text(name); if (source == null) throw new IllegalArgumentException("Direction source is required"); }
		public EndpointRef effectiveSink() { return sink == null ? EndpointRef.slack() : sink; }
	}

	public record MonitorSet(List<String> branchIds, List<LinearInterface> interfaces) {
		public MonitorSet { branchIds = list(branchIds); interfaces = list(interfaces); }

		public static MonitorSet empty() {
			return new MonitorSet(List.of(), List.of());
		}

		/** Returns a new set containing the existing and additional branch monitors. */
		public MonitorSet addBranches(List<String> additions) {
			List<String> combined = new ArrayList<>(branchIds);
			combined.addAll(list(additions));
			return new MonitorSet(combined, interfaces);
		}

		/** Returns a new set containing the existing and additional interface monitors. */
		public MonitorSet addInterfaces(List<LinearInterface> additions) {
			List<LinearInterface> combined = new ArrayList<>(interfaces);
			combined.addAll(list(additions));
			return new MonitorSet(branchIds, combined);
		}

		/** Returns a new set containing the existing monitors and both supplied collections. */
		public MonitorSet addAll(List<String> branchAdditions, List<LinearInterface> interfaceAdditions) {
			return addBranches(branchAdditions).addInterfaces(interfaceAdditions);
		}

		/** Returns a new set containing all monitors from this set and another set. */
		public MonitorSet addAll(MonitorSet additions) {
			if (additions == null) return this;
			return addAll(additions.branchIds(), additions.interfaces());
		}
	}
	/** A monitored interface whose aggregation defaults to the physical signed sum. */
	public record LinearInterface(String id, String name, List<InterfaceMember> members,
			InterfaceAggregation aggregation) {
		public LinearInterface {
			id = text(id); name = text(name); members = list(members);
			aggregation = aggregation == null ? InterfaceAggregation.SUM : aggregation;
		}
		public LinearInterface(String id, String name, List<InterfaceMember> members) {
			this(id, name, members, InterfaceAggregation.SUM);
		}
	}
	public record InterfaceMember(String branchId, double coefficient) {
		public InterfaceMember { branchId = text(branchId); if (!Double.isFinite(coefficient) || coefficient == 0) throw new IllegalArgumentException("Interface coefficient must be finite and non-zero"); }
	}

	public record GeneratedDirectionSet(String id, String name, List<EndpointRef> sources,
			List<EndpointRef> sinks, DirectionExpansion expansion) {
		public GeneratedDirectionSet {
			id = text(id); name = text(name); sources = list(sources); sinks = list(sinks);
			expansion = expansion == null ? DirectionExpansion.CARTESIAN : expansion;
			if (expansion == DirectionExpansion.PAIRED && sources.size() != sinks.size())
				throw new IllegalArgumentException("Paired direction sources and sinks must have the same size");
		}
	}
	public record PtdfSpec(List<Direction> directions, List<GeneratedDirectionSet> generatedDirections, MonitorSet monitors)
			implements SensitivitySpec {
		public PtdfSpec {
			directions = list(directions); generatedDirections = list(generatedDirections);
			monitors = monitors == null ? MonitorSet.empty() : monitors;
		}
		public PtdfSpec(List<Direction> directions, MonitorSet monitors) { this(directions, List.of(), monitors); }
		@Override public AnalysisType type() { return AnalysisType.PTDF; }
	}
	public record ShiftFactorSpec(EndpointRef transactor, TransactorRole role,
			List<CandidateType> candidateTypes, List<EndpointRef> explicitCandidates, MonitorSet monitors)
			implements SensitivitySpec {
		public ShiftFactorSpec {
			if (transactor == null || role == null) throw new IllegalArgumentException("Shift Factor transactor and role are required");
			candidateTypes = list(candidateTypes); explicitCandidates = list(explicitCandidates);
			monitors = monitors == null ? MonitorSet.empty() : monitors;
		}
		@Override public AnalysisType type() { return AnalysisType.SHIFT_FACTOR; }
	}
	public record LodfSpec(List<String> outageBranchIds, MonitorSet monitors) implements SensitivitySpec {
		public LodfSpec { outageBranchIds = list(outageBranchIds); monitors = monitors == null ? MonitorSet.empty() : monitors; }
		@Override public AnalysisType type() { return AnalysisType.LODF; }
	}
	public record MultiOutageLodfSpec(List<OutageGroup> outageGroups, MonitorSet monitors) implements SensitivitySpec {
		public MultiOutageLodfSpec { outageGroups = list(outageGroups); monitors = monitors == null ? MonitorSet.empty() : monitors; }
		@Override public AnalysisType type() { return AnalysisType.MULTI_OUTAGE_LODF; }
	}
	public record OutageGroup(String id, String name, List<String> branchIds) {
		public OutageGroup { id = text(id); name = text(name); branchIds = list(branchIds); }
	}

	public record CalculationOptions(
			DclfMethod method, ParticipationPolicy defaultParticipation,
			ResultRetentionPolicy retention, double threshold, int topK,
			double transferMw, boolean strict, int resultBlockSize) {
		public CalculationOptions {
			method = method == null ? DclfMethod.STD : method;
			defaultParticipation = defaultParticipation == null ? ParticipationPolicy.EQUAL_ACTIVE_GENERATORS : defaultParticipation;
			retention = retention == null ? ResultRetentionPolicy.THRESHOLDED : retention;
			if (!Double.isFinite(threshold) || threshold < 0) threshold = 0;
			if (topK <= 0) topK = 100;
			if (!Double.isFinite(transferMw) || transferMw <= 0) transferMw = 100.0;
			if (resultBlockSize <= 0) resultBlockSize = 1024;
		}
		public static CalculationOptions defaults() {
			return new CalculationOptions(DclfMethod.STD, ParticipationPolicy.EQUAL_ACTIVE_GENERATORS,
					ResultRetentionPolicy.THRESHOLDED, 0.0, 100, 100.0, false, 1024);
		}
	}
}
