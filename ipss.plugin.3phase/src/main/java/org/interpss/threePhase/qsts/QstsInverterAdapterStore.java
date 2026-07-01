package org.interpss.threePhase.qsts;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.interpss.core.threephase.AclfGen3Phase;

public class QstsInverterAdapterStore {
	private final Map<String, InverterGenAdapter> adaptersByGeneratorId = new LinkedHashMap<>();
	private final Map<String, QstsControlCurve> curvesById = new LinkedHashMap<>();

	public InverterGenAdapter register(AclfGen3Phase generator) {
		return register(new InverterGenAdapter(generator));
	}

	public InverterGenAdapter register(InverterGenAdapter adapter) {
		if(adapter == null) {
			return null;
		}
		for(QstsControlCurve curve : curvesById.values()) {
			adapter.addCurve(curve);
		}
		adaptersByGeneratorId.put(normalize(adapter.getGeneratorId()), adapter);
		return adapter;
	}

	public QstsInverterAdapterStore addCurve(QstsControlCurve curve) {
		if(curve != null) {
			curvesById.put(normalize(curve.getId()), curve);
			for(InverterGenAdapter adapter : adaptersByGeneratorId.values()) {
				adapter.addCurve(curve);
			}
		}
		return this;
	}

	public QstsControlCurve getCurve(String curveId) {
		return curvesById.get(normalize(curveId));
	}

	public InverterGenAdapter get(String generatorId) {
		return adaptersByGeneratorId.get(normalize(generatorId));
	}

	public int size() {
		return adaptersByGeneratorId.size();
	}

	public Collection<InverterGenAdapter> adapters() {
		return Collections.unmodifiableCollection(adaptersByGeneratorId.values());
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}
}
