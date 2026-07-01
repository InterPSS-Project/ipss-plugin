package org.interpss.threePhase.qsts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QstsScheduleData {
	private final QstsProfileRegistry profileRegistry;
	private final List<QstsProfileBinding> profileBindings;
	private final QstsGlobalOptions globalOptions;

	public QstsScheduleData(QstsProfileRegistry profileRegistry, List<QstsProfileBinding> profileBindings,
			QstsGlobalOptions globalOptions) {
		this.profileRegistry = profileRegistry == null ? new QstsProfileRegistry() : profileRegistry;
		this.profileBindings = profileBindings == null
				? Collections.emptyList()
				: Collections.unmodifiableList(new ArrayList<>(profileBindings));
		this.globalOptions = globalOptions;
	}

	public QstsProfileRegistry getProfileRegistry() {
		return profileRegistry;
	}

	public List<QstsProfileBinding> getProfileBindings() {
		return profileBindings;
	}

	public QstsGlobalOptions getGlobalOptions() {
		return globalOptions;
	}
}
