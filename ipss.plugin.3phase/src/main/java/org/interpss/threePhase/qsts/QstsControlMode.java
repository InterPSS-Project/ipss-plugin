package org.interpss.threePhase.qsts;

import java.util.Locale;

public enum QstsControlMode {
	OFF,
	STATIC,
	TIME,
	EVENT,
	FROZEN;

	public static QstsControlMode from(String value) {
		if(value == null || value.trim().isEmpty()) {
			return OFF;
		}
		return QstsControlMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
	}
}
