package org.interpss.threePhase.qsts;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class QstsProfileBinding {
	private final String deviceClass;
	private final String deviceId;
	private final Map<String, String> profileIdsByType;
	private final QstsDeviceStatus status;

	public QstsProfileBinding(String deviceClass, String deviceId, Map<String, String> profileIdsByType,
			QstsDeviceStatus status) {
		if(deviceClass == null || deviceClass.trim().isEmpty()) {
			throw new IllegalArgumentException("QSTS binding device class is required");
		}
		if(deviceId == null || deviceId.trim().isEmpty()) {
			throw new IllegalArgumentException("QSTS binding device id is required");
		}
		this.deviceClass = deviceClass;
		this.deviceId = deviceId;
		this.profileIdsByType = new LinkedHashMap<>();
		if(profileIdsByType != null) {
			for(Map.Entry<String, String> entry : profileIdsByType.entrySet()) {
				if(entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
					this.profileIdsByType.put(normalize(entry.getKey()), entry.getValue().trim());
				}
			}
		}
		this.status = status == null ? QstsDeviceStatus.DEFAULT : status;
	}

	public String getDeviceClass() {
		return deviceClass;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public Map<String, String> getProfileIdsByType() {
		return Collections.unmodifiableMap(profileIdsByType);
	}

	public String getProfileId(String profileType) {
		return profileIdsByType.get(normalize(profileType));
	}

	public QstsDeviceStatus getStatus() {
		return status;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}
}
