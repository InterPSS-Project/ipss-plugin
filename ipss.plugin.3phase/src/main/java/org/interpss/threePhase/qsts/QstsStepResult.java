package org.interpss.threePhase.qsts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QstsStepResult {
	private final int stepIndex;
	private final double hour;
	private final QstsMode mode;
	private final boolean converged;
	private final int iterationCount;
	private final double maxMismatch;
	private final String failureReason;
	private final int actionCount;
	private final List<String> diagnostics;
	private final List<QstsBusVoltageSample> busVoltages;
	private final List<QstsDevicePowerSample> loadPowers;
	private final List<QstsDevicePowerSample> generatorPowers;
	private final List<QstsBranchPowerSample> branchPowers;
	private final List<QstsCapacitorStateSample> capacitorStates;
	private final List<QstsInverterControlSample> inverterControls;

	public QstsStepResult(QstsStepContext context, boolean converged, int iterationCount,
			double maxMismatch, String failureReason, int actionCount,
			List<QstsBusVoltageSample> busVoltages, List<QstsDevicePowerSample> loadPowers,
			List<QstsDevicePowerSample> generatorPowers) {
		this(context, converged, iterationCount, maxMismatch, failureReason, actionCount,
				busVoltages, loadPowers, generatorPowers, null);
	}

	public QstsStepResult(QstsStepContext context, boolean converged, int iterationCount,
			double maxMismatch, String failureReason, int actionCount,
			List<QstsBusVoltageSample> busVoltages, List<QstsDevicePowerSample> loadPowers,
			List<QstsDevicePowerSample> generatorPowers, List<QstsCapacitorStateSample> capacitorStates) {
		this(context, converged, iterationCount, maxMismatch, failureReason, actionCount,
				busVoltages, loadPowers, generatorPowers, capacitorStates, null);
	}

	public QstsStepResult(QstsStepContext context, boolean converged, int iterationCount,
			double maxMismatch, String failureReason, int actionCount,
			List<QstsBusVoltageSample> busVoltages, List<QstsDevicePowerSample> loadPowers,
			List<QstsDevicePowerSample> generatorPowers, List<QstsCapacitorStateSample> capacitorStates,
			List<QstsInverterControlSample> inverterControls) {
		this(context, converged, iterationCount, maxMismatch, failureReason, actionCount,
				busVoltages, loadPowers, generatorPowers, null, capacitorStates, inverterControls);
	}

	public QstsStepResult(QstsStepContext context, boolean converged, int iterationCount,
			double maxMismatch, String failureReason, int actionCount,
			List<QstsBusVoltageSample> busVoltages, List<QstsDevicePowerSample> loadPowers,
			List<QstsDevicePowerSample> generatorPowers, List<QstsBranchPowerSample> branchPowers,
			List<QstsCapacitorStateSample> capacitorStates,
			List<QstsInverterControlSample> inverterControls) {
		this.stepIndex = context.getStepIndex();
		this.hour = context.getHour();
		this.mode = context.getMode();
		this.converged = converged;
		this.iterationCount = iterationCount;
		this.maxMismatch = maxMismatch;
		this.failureReason = failureReason;
		this.actionCount = actionCount;
		this.diagnostics = Collections.unmodifiableList(new ArrayList<>(context.getDiagnostics()));
		this.busVoltages = copy(busVoltages);
		this.loadPowers = copy(loadPowers);
		this.generatorPowers = copy(generatorPowers);
		this.branchPowers = copy(branchPowers);
		this.capacitorStates = copy(capacitorStates);
		this.inverterControls = copy(inverterControls);
	}

	public int getStepIndex() {
		return stepIndex;
	}

	public double getHour() {
		return hour;
	}

	public QstsMode getMode() {
		return mode;
	}

	public boolean isConverged() {
		return converged;
	}

	public int getIterationCount() {
		return iterationCount;
	}

	public double getMaxMismatch() {
		return maxMismatch;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public int getActionCount() {
		return actionCount;
	}

	public List<String> getDiagnostics() {
		return diagnostics;
	}

	public List<QstsBusVoltageSample> getBusVoltages() {
		return busVoltages;
	}

	public List<QstsDevicePowerSample> getLoadPowers() {
		return loadPowers;
	}

	public List<QstsDevicePowerSample> getGeneratorPowers() {
		return generatorPowers;
	}

	public List<QstsBranchPowerSample> getBranchPowers() {
		return branchPowers;
	}

	public List<QstsCapacitorStateSample> getCapacitorStates() {
		return capacitorStates;
	}

	public List<QstsInverterControlSample> getInverterControls() {
		return inverterControls;
	}

	private static <T> List<T> copy(List<T> values) {
		return values == null ? Collections.emptyList()
				: Collections.unmodifiableList(new ArrayList<>(values));
	}
}
