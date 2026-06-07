package org.interpss.threePhase.qsts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QstsResult {
	private final List<QstsStepResult> stepResults;

	public QstsResult(List<QstsStepResult> stepResults) {
		this.stepResults = stepResults == null
				? Collections.emptyList()
				: Collections.unmodifiableList(new ArrayList<>(stepResults));
	}

	public List<QstsStepResult> getStepResults() {
		return stepResults;
	}

	public boolean isConverged() {
		return stepResults.stream().allMatch(QstsStepResult::isConverged);
	}

	public QstsStepResult getStep(int stepIndex) {
		return stepResults.get(stepIndex);
	}

	public List<QstsBusVoltageSample> getBusVoltages() {
		List<QstsBusVoltageSample> samples = new ArrayList<>();
		for(QstsStepResult step : stepResults) {
			samples.addAll(step.getBusVoltages());
		}
		return Collections.unmodifiableList(samples);
	}
}
