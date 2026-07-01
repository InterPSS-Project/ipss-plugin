package org.interpss.threePhase.qsts;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import com.interpss.core.threephase.AclfGen3Phase;

public class QstsGeneratorStateStore {
	private final Map<Object, QstsGeneratorBaseState> statesByGenerator = new IdentityHashMap<>();

	public QstsGeneratorBaseState register(Object generator) {
		if(!(generator instanceof AclfGen3Phase)) {
			throw new IllegalArgumentException("QSTS generator state store requires AclfGen3Phase");
		}
		return statesByGenerator.computeIfAbsent(generator, QstsGeneratorBaseState::new);
	}

	public QstsGeneratorBaseState get(Object generator) {
		return statesByGenerator.get(generator);
	}

	public int size() {
		return statesByGenerator.size();
	}

	public Collection<QstsGeneratorBaseState> states() {
		return Collections.unmodifiableCollection(statesByGenerator.values());
	}

	public void refreshAllFromGenerators() {
		for(QstsGeneratorBaseState state : statesByGenerator.values()) {
			state.refreshFromGenerator();
		}
	}

	public void restoreAll() {
		for(QstsGeneratorBaseState state : statesByGenerator.values()) {
			state.restore();
		}
	}
}
