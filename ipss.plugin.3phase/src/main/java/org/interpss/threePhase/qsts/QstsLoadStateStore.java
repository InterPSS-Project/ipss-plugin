package org.interpss.threePhase.qsts;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import com.interpss.core.threephase.AclfLoad3Phase;

public class QstsLoadStateStore {
	private final Map<Object, QstsLoadBaseState> statesByLoad = new IdentityHashMap<>();

	public QstsLoadBaseState register(Object load) {
		if(!(load instanceof AclfLoad3Phase)) {
			throw new IllegalArgumentException("QSTS load state store requires AclfLoad3Phase");
		}
		return statesByLoad.computeIfAbsent(load, QstsLoadBaseState::new);
	}

	public int size() {
		return statesByLoad.size();
	}

	public Collection<QstsLoadBaseState> states() {
		return Collections.unmodifiableCollection(statesByLoad.values());
	}

	public void refreshAllFromLoads() {
		for(QstsLoadBaseState state : statesByLoad.values()) {
			state.refreshFromLoad();
		}
	}

	public void restoreAll() {
		for(QstsLoadBaseState state : statesByLoad.values()) {
			state.restore();
		}
	}
}
