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

	/**
	 * Returns per-phase load powers in per unit on {@code networkBaseKva / 3}.
	 * Multiply each P/Q value by that base to obtain kW/kvar.
	 * @see QstsDevicePowerSample
	 */
	public List<QstsDevicePowerSample> getLoadPowers() {
		List<QstsDevicePowerSample> samples = new ArrayList<>();
		for(QstsStepResult step : stepResults) {
			samples.addAll(step.getLoadPowers());
		}
		return Collections.unmodifiableList(samples);
	}

	/**
	 * Returns per-phase generator powers in per unit on {@code networkBaseKva}.
	 * Multiply each P/Q value by that full network base to obtain kW/kvar.
	 * Includes PV/storage; positive active power is injection and charging is negative.
	 * @see QstsDevicePowerSample
	 */
	public List<QstsDevicePowerSample> getGeneratorPowers() {
		List<QstsDevicePowerSample> samples = new ArrayList<>();
		for(QstsStepResult step : stepResults) {
			samples.addAll(step.getGeneratorPowers());
		}
		return Collections.unmodifiableList(samples);
	}

	public List<QstsBranchPowerSample> getBranchPowers() {
		List<QstsBranchPowerSample> samples = new ArrayList<>();
		for(QstsStepResult step : stepResults) {
			samples.addAll(step.getBranchPowers());
		}
		return Collections.unmodifiableList(samples);
	}

	public List<QstsCapacitorStateSample> getCapacitorStates() {
		List<QstsCapacitorStateSample> samples = new ArrayList<>();
		for(QstsStepResult step : stepResults) {
			samples.addAll(step.getCapacitorStates());
		}
		return Collections.unmodifiableList(samples);
	}

	public List<QstsInverterControlSample> getInverterControls() {
		List<QstsInverterControlSample> samples = new ArrayList<>();
		for(QstsStepResult step : stepResults) {
			samples.addAll(step.getInverterControls());
		}
		return Collections.unmodifiableList(samples);
	}
}
