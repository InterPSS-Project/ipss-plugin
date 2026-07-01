package org.interpss.threePhase.dataParser.opendss.timeseries;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.interpss.threePhase.qsts.QstsProfileRegistry;

public class OpenDSSShapeRegistry {
	private final Map<String, OpenDSSLoadShape> shapesById = new LinkedHashMap<>();

	public void add(OpenDSSLoadShape shape) {
		shapesById.put(normalize(shape.getId()), shape);
	}

	public OpenDSSLoadShape get(String id) {
		return shapesById.get(normalize(id));
	}

	public int size() {
		return shapesById.size();
	}

	public Collection<OpenDSSLoadShape> shapes() {
		return Collections.unmodifiableCollection(shapesById.values());
	}

	public QstsProfileRegistry toQstsProfileRegistry() {
		QstsProfileRegistry registry = new QstsProfileRegistry();
		for(OpenDSSLoadShape shape : shapesById.values()) {
			registry.add(shape.toQstsProfile());
		}
		return registry;
	}

	private static String normalize(String id) {
		return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
	}
}
