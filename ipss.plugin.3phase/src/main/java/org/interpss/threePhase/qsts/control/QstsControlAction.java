package org.interpss.threePhase.qsts.control;

public interface QstsControlAction {
	String getKey();

	double getExecuteTimeSeconds();

	boolean apply();
}
