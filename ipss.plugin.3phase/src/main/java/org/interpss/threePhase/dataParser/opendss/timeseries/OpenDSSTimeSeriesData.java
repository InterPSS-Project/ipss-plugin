package org.interpss.threePhase.dataParser.opendss.timeseries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.interpss.threePhase.qsts.QstsProfileBinding;
import org.interpss.threePhase.qsts.QstsProfile;
import org.interpss.threePhase.qsts.QstsProfileRegistry;
import org.interpss.threePhase.qsts.QstsScheduleData;
import org.interpss.threePhase.qsts.QstsGeneratorStateStore;
import org.interpss.threePhase.qsts.QstsControlCurve;
import org.interpss.threePhase.qsts.QstsInverterAdapterStore;
import org.interpss.threePhase.qsts.QstsLoadStateStore;
import org.interpss.threePhase.qsts.QstsStorageStateStore;
import org.interpss.threePhase.powerflow.control.InverterControlData;

public class OpenDSSTimeSeriesData {
	private final OpenDSSShapeRegistry shapeRegistry = new OpenDSSShapeRegistry();
	private final Map<String, OpenDSSTemperatureShape> temperatureShapesById = new LinkedHashMap<>();
	private final Map<String, OpenDSSProfileBinding> loadBindingsById = new LinkedHashMap<>();
	private final Map<String, OpenDSSProfileBinding> generatorBindingsById = new LinkedHashMap<>();
	private final Map<String, OpenDSSGeneratorModel> generatorModelsById = new LinkedHashMap<>();
	private final List<InverterControlData> inverterControls = new ArrayList<>();
	private final List<OpenDSSParserDiagnostic> diagnostics = new ArrayList<>();
	private final OpenDSSGlobalTimeSeriesOptions globalOptions = new OpenDSSGlobalTimeSeriesOptions();
	private final QstsLoadStateStore loadStateStore = new QstsLoadStateStore();
	private final QstsGeneratorStateStore generatorStateStore = new QstsGeneratorStateStore();
	private final QstsStorageStateStore storageStateStore = new QstsStorageStateStore();
	private final QstsInverterAdapterStore inverterAdapterStore = new QstsInverterAdapterStore();

	public OpenDSSShapeRegistry getShapeRegistry() {
		return shapeRegistry;
	}

	public void addTemperatureShape(OpenDSSTemperatureShape shape) {
		if(shape != null) {
			temperatureShapesById.put(normalize(shape.getId()), shape);
		}
	}

	public OpenDSSTemperatureShape getTemperatureShape(String shapeId) {
		return temperatureShapesById.get(normalize(shapeId));
	}

	public Collection<OpenDSSTemperatureShape> getTemperatureShapes() {
		return Collections.unmodifiableCollection(temperatureShapesById.values());
	}

	public OpenDSSProfileBinding getOrCreateLoadBinding(String loadId) {
		String key = normalize(loadId);
		return loadBindingsById.computeIfAbsent(key, ignored -> new OpenDSSProfileBinding("load", loadId));
	}

	public OpenDSSProfileBinding getLoadBinding(String loadId) {
		return loadBindingsById.get(normalize(loadId));
	}

	public Collection<OpenDSSProfileBinding> getLoadBindings() {
		return Collections.unmodifiableCollection(loadBindingsById.values());
	}

	public OpenDSSProfileBinding getOrCreateGeneratorBinding(String generatorId) {
		String key = normalize(generatorId);
		return generatorBindingsById.computeIfAbsent(key,
				ignored -> new OpenDSSProfileBinding("generator", generatorId));
	}

	public OpenDSSProfileBinding getGeneratorBinding(String generatorId) {
		return generatorBindingsById.get(normalize(generatorId));
	}

	public Collection<OpenDSSProfileBinding> getGeneratorBindings() {
		return Collections.unmodifiableCollection(generatorBindingsById.values());
	}

	public void addGeneratorModel(OpenDSSGeneratorModel model) {
		if(model != null) {
			generatorModelsById.put(normalize(model.getId()), model);
		}
	}

	public OpenDSSGeneratorModel getGeneratorModel(String generatorId) {
		return generatorModelsById.get(normalize(generatorId));
	}

	public Collection<OpenDSSGeneratorModel> getGeneratorModels() {
		return Collections.unmodifiableCollection(generatorModelsById.values());
	}

	public void addInverterControl(InverterControlData control) {
		if(control != null) {
			inverterControls.add(control);
		}
	}

	public List<InverterControlData> getInverterControls() {
		return Collections.unmodifiableList(inverterControls);
	}

	public void addDiagnostic(OpenDSSParserDiagnostic diagnostic) {
		if(diagnostic != null) {
			diagnostics.add(diagnostic);
		}
	}

	public List<OpenDSSParserDiagnostic> getDiagnostics() {
		return Collections.unmodifiableList(diagnostics);
	}

	public OpenDSSGlobalTimeSeriesOptions getGlobalOptions() {
		return globalOptions;
	}

	public QstsLoadStateStore getLoadStateStore() {
		return loadStateStore;
	}

	public QstsGeneratorStateStore getGeneratorStateStore() {
		return generatorStateStore;
	}

	public QstsStorageStateStore getStorageStateStore() {
		return storageStateStore;
	}

	public QstsInverterAdapterStore getInverterAdapterStore() {
		return inverterAdapterStore;
	}

	public void addControlCurve(QstsControlCurve curve) {
		inverterAdapterStore.addCurve(curve);
	}

	public QstsControlCurve getControlCurve(String curveId) {
		return inverterAdapterStore.getCurve(curveId);
	}

	public QstsScheduleData toQstsScheduleData() {
		QstsProfileRegistry profileRegistry = shapeRegistry.toQstsProfileRegistry();
		addDerivedPvSystemProfiles(profileRegistry);
		List<QstsProfileBinding> bindings = new ArrayList<>();
		for(OpenDSSProfileBinding binding : loadBindingsById.values()) {
			bindings.add(binding.toQstsProfileBinding());
		}
		for(OpenDSSProfileBinding binding : generatorBindingsById.values()) {
			bindings.add(binding.toQstsProfileBinding());
		}
		return new QstsScheduleData(profileRegistry, bindings,
				globalOptions.toQstsGlobalOptions());
	}

	private void addDerivedPvSystemProfiles(QstsProfileRegistry profileRegistry) {
		for(OpenDSSGeneratorModel model : generatorModelsById.values()) {
			if(!"pvsystem".equalsIgnoreCase(model.getDeviceClass())) {
				continue;
			}
			OpenDSSProfileBinding binding = getGeneratorBinding(model.getId());
			OpenDSSLoadShape irradianceShape = shapeRegistry.get(model.getDailyShapeId());
			if(binding == null || irradianceShape == null || model.getKw() == 0.0 || model.getPmpp() <= 0.0) {
				continue;
			}
			int pointCount = irradianceShape.getPointCount() + 1;
			double[] pMult = new double[pointCount];
			double[] qMult = new double[pointCount];
			pMult[0] = 1.0;
			qMult[0] = 1.0;
			OpenDSSTemperatureShape temperatureShape = getTemperatureShape(model.getDailyTemperatureShapeId());
			for(int i = 1; i < pointCount; i++) {
				double temperature = temperatureForStep(model, temperatureShape, i);
				double activePowerKw = availablePvPowerKw(model, model.getIrradiance(), temperature);
				double multiplier = Double.isFinite(activePowerKw) ? activePowerKw / model.getKw() : 1.0;
				pMult[i] = multiplier;
				qMult[i] = multiplier;
			}
			String profileId = model.getId() + "_pvsystem_daily";
			profileRegistry.add(new QstsProfile(profileId, new double[0], pMult, qMult,
					Collections.singletonList("Derived from OpenDSS PVSystem daily irradiance and temperature shapes")));
			binding.setShapeId(OpenDSSProfileType.DAILY, profileId);
		}
	}

	private double temperatureForStep(OpenDSSGeneratorModel model, OpenDSSTemperatureShape shape, int stepIndex) {
		if(shape == null || shape.getPointCount() == 0) {
			return model.getTemperature();
		}
		int index = Math.min(stepIndex, shape.getPointCount() - 1);
		return shape.getTemperature()[index];
	}

	private double availablePvPowerKw(OpenDSSGeneratorModel model, double irradiance, double temperature) {
		if(model.getPmpp() <= 0.0) {
			return Double.NaN;
		}
		double panelPowerKw = model.getPmpp() * irradiance * model.getPctPmpp() / 100.0
				* curveValue(model.getPvsTCurveId(), temperature, 1.0);
		double puPower = model.getKva() > 0.0 ? panelPowerKw / model.getKva() : panelPowerKw / model.getPmpp();
		return panelPowerKw * curveValue(model.getEfficiencyCurveId(), puPower, 1.0);
	}

	private double curveValue(String curveId, double x, double defaultValue) {
		QstsControlCurve curve = getControlCurve(curveId);
		return curve == null ? defaultValue : curve.evaluate(x);
	}

	private static String normalize(String id) {
		return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
	}
}
