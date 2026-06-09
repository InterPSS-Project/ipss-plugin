package org.interpss.threePhase.qsts;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class QstsProfileRegistry {
	private final Map<String, QstsProfile> profilesById = new LinkedHashMap<>();

	public void add(QstsProfile profile) {
		String key = normalize(profile.getId());
		if(profilesById.containsKey(key)) {
			throw new IllegalArgumentException("Duplicate QSTS profile id: " + profile.getId());
		}
		profilesById.put(key, profile);
	}

	public QstsProfile get(String id) {
		return profilesById.get(normalize(id));
	}

	public int size() {
		return profilesById.size();
	}

	public Collection<QstsProfile> profiles() {
		return Collections.unmodifiableCollection(profilesById.values());
	}

	private static String normalize(String id) {
		return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
	}
}
