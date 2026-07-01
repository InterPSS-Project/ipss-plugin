package org.interpss.threePhase.qsts;

import java.util.Locale;

public enum QstsMode {
	SNAPSHOT,
	DAILY,
	YEARLY,
	DUTY;

	public static QstsMode from(String value) {
		if(value == null || value.trim().isEmpty()) {
			return SNAPSHOT;
		}
		return QstsMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
	}

	public String profileTypeKey() {
		return name().toLowerCase(Locale.ROOT);
	}
}
