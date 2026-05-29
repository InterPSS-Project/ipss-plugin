package org.interpss.threePhase.opf.dist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DistOpfScheduleResult {

	private final List<DistOpfResult> periodResults = new ArrayList<DistOpfResult>();

	public DistOpfScheduleResult addPeriodResult(DistOpfResult result) {
		periodResults.add(result);
		return this;
	}

	public List<DistOpfResult> getPeriodResults() {
		return Collections.unmodifiableList(periodResults);
	}

	public DistOpfResult getPeriodResult(int periodIndex) {
		return periodResults.get(periodIndex);
	}

	public boolean isSolved() {
		for (DistOpfResult result : periodResults) {
			if (!result.isSolved()) {
				return false;
			}
		}
		return true;
	}
}
