package org.interpss.plugin.sensitivity;

import java.util.List;

import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.AnalysisType;
import org.interpss.plugin.sensitivity.SensitivityResult.Block;
import org.interpss.plugin.sensitivity.SensitivityResult.Manifest;
import org.interpss.plugin.sensitivity.SensitivityResult.RunSnapshot;

public interface SensitivityResultSink {
	void begin(String resultId, String studyId, List<AnalysisType> types, RunSnapshot snapshot);
	void accept(Block block);
	Manifest complete(Manifest manifest);
	Manifest fail(Manifest manifest, Throwable error);
}
