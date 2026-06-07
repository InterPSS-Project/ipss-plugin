package org.interpss.threePhase.qsts;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import com.interpss.core.threephase.IPhaseGen;

public class QstsStorageStateStore {
	private final Map<Object, QstsStorageBaseState> statesByGenerator = new IdentityHashMap<>();

	public QstsStorageBaseState register(IPhaseGen generator, double baseKva, double kwRated,
			double kwhRated, double storedKwh, double reserveKwh,
			double chargeEfficiency, double dischargeEfficiency) {
		if(generator == null) {
			throw new IllegalArgumentException("QSTS storage state store requires IPhaseGen");
		}
		QstsStorageBaseState state = new QstsStorageBaseState(generator, baseKva, kwRated,
				kwhRated, storedKwh, reserveKwh, chargeEfficiency, dischargeEfficiency);
		statesByGenerator.put(generator, state);
		return state;
	}

	public QstsStorageBaseState get(Object generator) {
		return statesByGenerator.get(generator);
	}

	public boolean contains(Object generator) {
		return statesByGenerator.containsKey(generator);
	}

	public int size() {
		return statesByGenerator.size();
	}

	public Collection<QstsStorageBaseState> states() {
		return Collections.unmodifiableCollection(statesByGenerator.values());
	}
}
