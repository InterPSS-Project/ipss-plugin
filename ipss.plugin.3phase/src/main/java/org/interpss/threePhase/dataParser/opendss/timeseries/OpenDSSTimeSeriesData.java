package org.interpss.threePhase.dataParser.opendss.timeseries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.interpss.threePhase.qsts.QstsProfileBinding;
import org.interpss.threePhase.qsts.QstsScheduleData;
import org.interpss.threePhase.qsts.QstsGeneratorStateStore;
import org.interpss.threePhase.qsts.QstsLoadStateStore;
import org.interpss.threePhase.qsts.QstsStorageStateStore;
import org.interpss.threePhase.powerflow.control.InverterControlData;

public class OpenDSSTimeSeriesData {
	private final OpenDSSShapeRegistry shapeRegistry = new OpenDSSShapeRegistry();
	private final Map<String, OpenDSSProfileBinding> loadBindingsById = new LinkedHashMap<>();
	private final Map<String, OpenDSSProfileBinding> generatorBindingsById = new LinkedHashMap<>();
	private final Map<String, OpenDSSGeneratorModel> generatorModelsById = new LinkedHashMap<>();
	private final List<InverterControlData> inverterControls = new ArrayList<>();
	private final List<OpenDSSParserDiagnostic> diagnostics = new ArrayList<>();
	private final OpenDSSGlobalTimeSeriesOptions globalOptions = new OpenDSSGlobalTimeSeriesOptions();
	private final QstsLoadStateStore loadStateStore = new QstsLoadStateStore();
	private final QstsGeneratorStateStore generatorStateStore = new QstsGeneratorStateStore();
	private final QstsStorageStateStore storageStateStore = new QstsStorageStateStore();

	public OpenDSSShapeRegistry getShapeRegistry() {
		return shapeRegistry;
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

	public QstsScheduleData toQstsScheduleData() {
		List<QstsProfileBinding> bindings = new ArrayList<>();
		for(OpenDSSProfileBinding binding : loadBindingsById.values()) {
			bindings.add(binding.toQstsProfileBinding());
		}
		for(OpenDSSProfileBinding binding : generatorBindingsById.values()) {
			bindings.add(binding.toQstsProfileBinding());
		}
		return new QstsScheduleData(shapeRegistry.toQstsProfileRegistry(), bindings,
				globalOptions.toQstsGlobalOptions());
	}

	private static String normalize(String id) {
		return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
	}
}
