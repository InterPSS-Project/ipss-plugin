package org.interpss.plugin.sensitivity;

import java.util.List;
import java.util.Map;

import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.AnalysisType;

public final class SensitivityResult {
	private SensitivityResult() {}

	public record Diagnostic(Severity severity, String code, String message, String objectId) {
		public enum Severity { INFO, WARNING, ERROR }
	}

	public record ResolvedEndpoint(String configuredType, String configuredId,
			String resolvedType, String resolvedId, Map<String, Double> busWeights,
			List<Diagnostic> diagnostics) {
		public ResolvedEndpoint {
			busWeights = busWeights == null ? Map.of() : Map.copyOf(busWeights);
			diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
		}
	}

	public record RunSnapshot(String studyId, int schemaVersion, String networkReference,
			String networkFingerprint, List<String> temporarilyDisabledBusIds,
			Map<String, ResolvedEndpoint> resolvedEndpoints, List<Diagnostic> diagnostics) {
		public RunSnapshot {
			temporarilyDisabledBusIds = temporarilyDisabledBusIds == null ? List.of() : List.copyOf(temporarilyDisabledBusIds);
			resolvedEndpoints = resolvedEndpoints == null ? Map.of() : Map.copyOf(resolvedEndpoints);
			diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
		}
	}

	/** One retained sensitivity. {@code factor} is dimensionless (multiply by 100 for percent). */
	public record Row(AnalysisType analysisType, String directionId, String sourceId,
			String sinkId, String monitorId, String outageId, String candidateType,
			String candidateId, double factor, double transferMw, double incrementalFlowMw) {}

	public record Block(long firstCandidateIndex, List<Row> rows) {
		public Block { rows = rows == null ? List.of() : List.copyOf(rows); }
	}

	public record Manifest(String resultId, String studyId, List<AnalysisType> analysisTypes,
			long candidateCount, long storedRowCount, boolean complete,
			RunSnapshot snapshot, List<String> partitionUris, List<Diagnostic> diagnostics) {
		public Manifest {
			analysisTypes = analysisTypes == null ? List.of() : List.copyOf(analysisTypes);
			partitionUris = partitionUris == null ? List.of() : List.copyOf(partitionUris);
			diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
		}
		/** First analysis for compatibility with schema-v1 consumers. */
		@Deprecated(forRemoval = false)
		public AnalysisType analysisType() { return analysisTypes.isEmpty() ? null : analysisTypes.getFirst(); }
	}
}
