package org.interpss.threePhase.qsts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QstsStepContext {
	private final int stepIndex;
	private final int scheduleIndex;
	private final double hour;
	private final QstsMode mode;
	private final double stepSizeHours;
	private final double loadMultiplier;
	private final QstsControlMode controlMode;
	private final List<String> diagnostics = new ArrayList<>();

	public QstsStepContext(int stepIndex, int scheduleIndex, double hour, QstsMode mode,
			double stepSizeHours, double loadMultiplier, QstsControlMode controlMode) {
		this.stepIndex = stepIndex;
		this.scheduleIndex = scheduleIndex;
		this.hour = hour;
		this.mode = mode == null ? QstsMode.SNAPSHOT : mode;
		this.stepSizeHours = stepSizeHours;
		this.loadMultiplier = loadMultiplier;
		this.controlMode = controlMode == null ? QstsControlMode.OFF : controlMode;
	}

	public int getStepIndex() {
		return stepIndex;
	}

	public int getScheduleIndex() {
		return scheduleIndex;
	}

	public double getHour() {
		return hour;
	}

	public QstsMode getMode() {
		return mode;
	}

	public double getStepSizeHours() {
		return stepSizeHours;
	}

	public double getLoadMultiplier() {
		return loadMultiplier;
	}

	public QstsControlMode getControlMode() {
		return controlMode;
	}

	public void addDiagnostic(String message) {
		if(message != null && !message.trim().isEmpty()) {
			diagnostics.add(message);
		}
	}

	public List<String> getDiagnostics() {
		return Collections.unmodifiableList(diagnostics);
	}
}
