package org.interpss.plugin.sensitivity;

import java.util.ArrayList;
import java.util.List;

import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.AnalysisType;
import org.interpss.plugin.sensitivity.SensitivityResult.Block;
import org.interpss.plugin.sensitivity.SensitivityResult.Manifest;
import org.interpss.plugin.sensitivity.SensitivityResult.Row;
import org.interpss.plugin.sensitivity.SensitivityResult.RunSnapshot;

public final class InMemorySensitivityResultSink implements SensitivityResultSink {
	private String resultId;
	private String studyId;
	private List<AnalysisType> types;
	private RunSnapshot snapshot;
	private final List<Row> rows = new ArrayList<>();
	private Manifest manifest;

	@Override public void begin(String resultId, String studyId, List<AnalysisType> types, RunSnapshot snapshot) {
		this.resultId = resultId; this.studyId = studyId; this.types = List.copyOf(types); this.snapshot = snapshot; rows.clear(); manifest = null;
	}
	@Override public void accept(Block block) { rows.addAll(block.rows()); }
	@Override public Manifest complete(Manifest manifest) { this.manifest = manifest; return manifest; }
	@Override public Manifest fail(Manifest manifest, Throwable error) { this.manifest = manifest; return manifest; }
	public List<Row> rows() { return List.copyOf(rows); }
	public Manifest manifest() { return manifest; }
}
