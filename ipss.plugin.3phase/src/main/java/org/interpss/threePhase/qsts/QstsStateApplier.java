package org.interpss.threePhase.qsts;

import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.threephase.IBus3Phase;
import com.interpss.core.threephase.AclfGen3Phase;
import com.interpss.core.threephase.AclfLoad3Phase;
import com.interpss.core.threephase.INetwork3Phase;

public class QstsStateApplier {
	private final QstsScheduleData scheduleData;
	private final QstsLoadStateStore loadStateStore;
	private final QstsGeneratorStateStore generatorStateStore;
	private final QstsStorageStateStore storageStateStore;
	private final Map<String, QstsProfileBinding> bindingsByKey = new LinkedHashMap<>();
	private final Map<QstsLoadBaseState, QstsProfileBinding> loadBindingsByState = new IdentityHashMap<>();
	private final QstsLoadMultiplierResolver multiplierResolver;

	public QstsStateApplier(QstsScheduleData scheduleData, QstsLoadStateStore loadStateStore,
			QstsGeneratorStateStore generatorStateStore) {
		this(scheduleData, loadStateStore, generatorStateStore, null);
	}

	public QstsStateApplier(QstsScheduleData scheduleData, QstsLoadStateStore loadStateStore,
			QstsGeneratorStateStore generatorStateStore, QstsStorageStateStore storageStateStore) {
		this.scheduleData = scheduleData == null
				? new QstsScheduleData(new QstsProfileRegistry(), null, null)
				: scheduleData;
		this.loadStateStore = loadStateStore == null ? new QstsLoadStateStore() : loadStateStore;
		this.generatorStateStore = generatorStateStore == null ? new QstsGeneratorStateStore() : generatorStateStore;
		this.storageStateStore = storageStateStore == null ? new QstsStorageStateStore() : storageStateStore;
		for(QstsProfileBinding binding : this.scheduleData.getProfileBindings()) {
			bindingsByKey.put(key(binding.getDeviceClass(), binding.getDeviceId()), binding);
		}
		this.multiplierResolver = new QstsLoadMultiplierResolver(this.scheduleData.getProfileRegistry());
		for(QstsLoadBaseState state : this.loadStateStore.states()) {
			loadBindingsByState.put(state, bindingFor("load", state.getLoadId()));
		}
		initializeLoadNortonReferences();
	}

	public static QstsStateApplier fromNetwork(INetwork3Phase network, QstsScheduleData scheduleData) {
		QstsLoadStateStore loadStore = new QstsLoadStateStore();
		QstsGeneratorStateStore generatorStore = new QstsGeneratorStateStore();
		registerNetworkDevices(network, loadStore, generatorStore);
		return new QstsStateApplier(scheduleData, loadStore, generatorStore);
	}

	public static void registerNetworkDevices(INetwork3Phase network, QstsLoadStateStore loadStore,
			QstsGeneratorStateStore generatorStore) {
		if(network == null) {
			return;
		}
		for(IBus3Phase bus : network.getThreePhaseBusList()) {
			Map<Object, Boolean> registered = new IdentityHashMap<>();
			if(loadStore != null) {
				for(AclfLoad3Phase load : bus.getPhaseLoadList()) {
					loadStore.register(load);
					registered.put(load, Boolean.TRUE);
				}
			}
			if(generatorStore != null) {
				for(AclfGen3Phase generator : bus.getPhaseGenList()) {
					generatorStore.register(generator);
					registered.put(generator, Boolean.TRUE);
				}
			}
			if(bus instanceof BaseAclfBus) {
				BaseAclfBus<?, ?> aclfBus = (BaseAclfBus<?, ?>) bus;
				if(loadStore != null) {
					for(Object load : aclfBus.getContributeLoadList()) {
						if(registered.containsKey(load)) {
							continue;
						}
						if(load instanceof AclfLoad3Phase) {
							loadStore.register(load);
						}
					}
				}
				if(generatorStore != null) {
					for(Object generator : aclfBus.getContributeGenList()) {
					if(!registered.containsKey(generator)
							&& generator instanceof AclfGen3Phase) {
						generatorStore.register(generator);
					}
					}
				}
			}
		}
	}

	public void apply(QstsStepContext context) {
		applyLoads(context);
		applyGenerators(context);
		applyStorage(context);
	}

	public boolean hasTimeVaryingBindings() {
		for(QstsProfileBinding binding : scheduleData.getProfileBindings()) {
			for(String profileId : binding.getProfileIdsByType().values()) {
				QstsProfile profile = scheduleData.getProfileRegistry().get(profileId);
				if(profile != null && profile.getPointCount() > 0) {
					return true;
				}
			}
		}
		return false;
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

	public void initializeLoadNortonReferences() {
		for(QstsLoadBaseState state : loadStateStore.states()) {
			state.applyFixedPointNortonMultiplier(1.0, 1.0);
		}
	}

	private void applyLoads(QstsStepContext context) {
		for(QstsLoadBaseState state : loadStateStore.states()) {
			QstsProfileBinding binding = loadBindingFor(state);
			if(binding == null && unity(context.getLoadMultiplier())) {
				continue;
			}
			QstsLoadMultiplier multiplier = multiplierResolver
					.resolve(binding, context.getMode(), context.getScheduleIndex(),
							context.getLoadMultiplier());
			state.applyMultiplier(multiplier.getPMultiplier(), multiplier.getQMultiplier());
		}
	}

	private void applyGenerators(QstsStepContext context) {
		for(QstsGeneratorBaseState state : generatorStateStore.states()) {
			if(storageStateStore.contains(state.getGenerator())) {
				continue;
			}
			QstsProfileBinding binding = generatorBindingFor(state.getGeneratorId());
			if(binding == null) {
				continue;
			}
			QstsLoadMultiplier multiplier = multiplierResolver
					.resolve(binding, context.getMode(), context.getScheduleIndex(), 1.0);
			state.applyMultiplier(multiplier.getPMultiplier(), multiplier.getQMultiplier());
		}
	}

	private void applyStorage(QstsStepContext context) {
		for(QstsStorageBaseState state : storageStateStore.states()) {
			QstsProfileBinding binding = storageBindingFor(state.getStorageId());
			if(binding == null) {
				continue;
			}
			QstsLoadMultiplier multiplier = multiplierResolver
					.resolve(binding, context.getMode(), context.getScheduleIndex(), 1.0);
			state.applyScheduledMultiplier(multiplier.getPMultiplier(),
					multiplier.getQMultiplier(), context.getStepSizeHours());
		}
	}

	private QstsProfileBinding loadBindingFor(QstsLoadBaseState state) {
		if(loadBindingsByState.containsKey(state)) {
			return loadBindingsByState.get(state);
		}
		QstsProfileBinding binding = bindingFor("load", state.getLoadId());
		loadBindingsByState.put(state, binding);
		return binding;
	}

	private QstsProfileBinding generatorBindingFor(String deviceId) {
		QstsProfileBinding binding = bindingFor("generator", deviceId);
		if(binding == null) {
			binding = bindingFor("pvsystem", deviceId);
		}
		if(binding == null) {
			binding = bindingFor("storage", deviceId);
		}
		return binding;
	}

	private QstsProfileBinding storageBindingFor(String deviceId) {
		QstsProfileBinding binding = bindingFor("storage", deviceId);
		if(binding == null) {
			binding = bindingFor("generator", deviceId);
		}
		return binding;
	}

	private QstsProfileBinding bindingFor(String deviceClass, String deviceId) {
		return bindingsByKey.get(key(deviceClass, deviceId));
	}

	private static String key(String deviceClass, String deviceId) {
		return normalize(deviceClass) + ":" + normalize(deviceId);
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean unity(double value) {
		return !Double.isFinite(value) || Math.abs(value - 1.0) <= 1.0e-12;
	}
}
