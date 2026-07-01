package org.interpss.threePhase.dataParser.opendss.timeseries;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.interpss.threePhase.qsts.QstsDeviceStatus;
import org.interpss.threePhase.qsts.QstsProfileBinding;

public class OpenDSSProfileBinding {
	private final String deviceClass;
	private final String deviceId;
	private final EnumMap<OpenDSSProfileType, String> shapeIdsByType = new EnumMap<>(OpenDSSProfileType.class);
	private QstsDeviceStatus status = QstsDeviceStatus.DEFAULT;

	public OpenDSSProfileBinding(String deviceClass, String deviceId) {
		if(deviceClass == null || deviceClass.trim().isEmpty()) {
			throw new IllegalArgumentException("OpenDSS binding device class is required");
		}
		if(deviceId == null || deviceId.trim().isEmpty()) {
			throw new IllegalArgumentException("OpenDSS binding device id is required");
		}
		this.deviceClass = deviceClass.trim().toLowerCase(Locale.ROOT);
		this.deviceId = deviceId.trim().toLowerCase(Locale.ROOT);
	}

	public String getDeviceClass() {
		return deviceClass;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public void setShapeId(OpenDSSProfileType type, String shapeId) {
		if(type != null && shapeId != null && !shapeId.trim().isEmpty()) {
			shapeIdsByType.put(type, shapeId.trim().toLowerCase(Locale.ROOT));
		}
	}

	public String getShapeId(OpenDSSProfileType type) {
		return shapeIdsByType.get(type);
	}

	public Map<OpenDSSProfileType, String> getShapeIdsByType() {
		return Collections.unmodifiableMap(shapeIdsByType);
	}

	public QstsDeviceStatus getStatus() {
		return status;
	}

	public void setStatus(QstsDeviceStatus status) {
		if(status != null) {
			this.status = status;
		}
	}

	public QstsProfileBinding toQstsProfileBinding() {
		Map<String, String> profileIdsByType = new LinkedHashMap<>();
		for(Map.Entry<OpenDSSProfileType, String> entry : shapeIdsByType.entrySet()) {
			profileIdsByType.put(entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
		}
		return new QstsProfileBinding(deviceClass, deviceId, profileIdsByType, status);
	}
}
