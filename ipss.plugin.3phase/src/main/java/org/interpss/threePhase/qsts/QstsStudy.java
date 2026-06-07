package org.interpss.threePhase.qsts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.powerflow.control.CapacitorBankControl;
import org.interpss.threePhase.powerflow.DistributionPFMethod;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.control.CapacitorControlData;
import org.interpss.threePhase.powerflow.control.InverterControlData;
import org.interpss.threePhase.powerflow.control.InverterControlModel.InverterControlResult;
import org.interpss.threePhase.powerflow.control.RegulatorControlData;
import org.interpss.threePhase.qsts.control.QstsControlAction;
import org.interpss.threePhase.qsts.control.QstsControlCompensationPolicy;
import org.interpss.threePhase.qsts.control.QstsControlQueue;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.threephase.IBus3Phase;
import com.interpss.core.threephase.IPhaseGen;
import com.interpss.core.threephase.IPhaseLoad;
import com.interpss.core.threephase.INetwork3Phase;

public class QstsStudy {
	private final INetwork3Phase network;
	private final QstsScheduleData scheduleData;
	private QstsStateApplier stateApplier;
	private DistributionPowerFlowAlgorithm powerFlowAlgorithm;
	private QstsMode mode;
	private int startIndex;
	private int numberOfSteps;
	private double stepSizeHours;
	private double startHour;
	private double loadMultiplier;
	private QstsControlMode controlMode;
	private int maxControlIterations;
	private DistributionPFMethod pfMethod = DistributionPFMethod.Fixed_Point;
	private int maxPowerFlowIterations = 100;
	private double tolerance = 1.0e-6;
	private boolean initializeFirstStepVoltages = true;
	private List<RegulatorControlData> regulatorControls = Collections.emptyList();
	private List<CapacitorControlData> capacitorControls = Collections.emptyList();
	private List<InverterControlData> inverterControls = Collections.emptyList();
	private QstsInverterAdapterStore inverterAdapterStore = new QstsInverterAdapterStore();
	private QstsControlCompensationPolicy controlCompensationPolicy = new QstsControlCompensationPolicy();
	private final CapacitorBankControl qstsCapacitorControl = new CapacitorBankControl();
	private final QstsControlQueue controlQueue = new QstsControlQueue();
	private final Map<String, Integer> operationCountByControlKey = new java.util.LinkedHashMap<>();

	private QstsStudy(INetwork3Phase network, QstsScheduleData scheduleData) {
		if(network == null) {
			throw new IllegalArgumentException("QSTS study requires a three-phase network");
		}
		this.network = network;
		this.scheduleData = scheduleData == null
				? new QstsScheduleData(new QstsProfileRegistry(), null, null)
				: scheduleData;
		QstsGlobalOptions options = this.scheduleData.getGlobalOptions();
		this.mode = options == null ? QstsMode.SNAPSHOT : QstsMode.from(options.getMode());
		this.numberOfSteps = options != null && options.getNumberOfSteps() != null
				? options.getNumberOfSteps() : 1;
		this.stepSizeHours = options != null && options.getStepSizeHours() != null
				? options.getStepSizeHours() : 1.0;
		this.startHour = options != null && options.getHour() != null ? options.getHour() : 0.0;
		this.loadMultiplier = options != null && options.getLoadMult() != null
				? options.getLoadMult() : 1.0;
		this.controlMode = options == null ? QstsControlMode.OFF : QstsControlMode.from(options.getControlMode());
		this.maxControlIterations = options != null && options.getMaxControl() != null
				? options.getMaxControl() : 0;
		this.stateApplier = QstsStateApplier.fromNetwork(network, this.scheduleData);
	}

	public static QstsStudy from(INetwork3Phase network, QstsScheduleData scheduleData) {
		return new QstsStudy(network, scheduleData);
	}

	public QstsStudy setStateApplier(QstsStateApplier stateApplier) {
		this.stateApplier = stateApplier == null ? QstsStateApplier.fromNetwork(network, scheduleData) : stateApplier;
		return this;
	}

	public QstsStudy setPowerFlowAlgorithm(DistributionPowerFlowAlgorithm powerFlowAlgorithm) {
		this.powerFlowAlgorithm = powerFlowAlgorithm;
		return this;
	}

	public QstsStudy setRegulatorControls(List<RegulatorControlData> regulatorControls) {
		this.regulatorControls = regulatorControls == null ? Collections.emptyList() : regulatorControls;
		return this;
	}

	public QstsStudy setCapacitorControls(List<CapacitorControlData> capacitorControls) {
		this.capacitorControls = capacitorControls == null ? Collections.emptyList() : capacitorControls;
		return this;
	}

	public QstsStudy setInverterControls(List<InverterControlData> inverterControls) {
		this.inverterControls = inverterControls == null ? Collections.emptyList() : inverterControls;
		return this;
	}

	public QstsStudy setInverterAdapterStore(QstsInverterAdapterStore inverterAdapterStore) {
		this.inverterAdapterStore = inverterAdapterStore == null
				? new QstsInverterAdapterStore() : inverterAdapterStore;
		return this;
	}

	public QstsStudy setControlCompensationPolicy(QstsControlCompensationPolicy controlCompensationPolicy) {
		this.controlCompensationPolicy = controlCompensationPolicy == null
				? new QstsControlCompensationPolicy() : controlCompensationPolicy;
		return this;
	}

	public QstsStudy setMode(QstsMode mode) {
		this.mode = mode == null ? QstsMode.SNAPSHOT : mode;
		return this;
	}

	public QstsStudy setStartIndex(int startIndex) {
		this.startIndex = Math.max(0, startIndex);
		return this;
	}

	public QstsStudy setNumberOfSteps(int numberOfSteps) {
		this.numberOfSteps = Math.max(1, numberOfSteps);
		return this;
	}

	public QstsStudy setStepSizeHours(double stepSizeHours) {
		this.stepSizeHours = stepSizeHours > 0.0 ? stepSizeHours : 1.0;
		return this;
	}

	public QstsStudy setStartHour(double startHour) {
		this.startHour = startHour;
		return this;
	}

	public QstsStudy setLoadMultiplier(double loadMultiplier) {
		this.loadMultiplier = loadMultiplier;
		return this;
	}

	public QstsStudy setControlMode(QstsControlMode controlMode) {
		this.controlMode = controlMode == null ? QstsControlMode.OFF : controlMode;
		return this;
	}

	public QstsStudy setMaxControlIterations(int maxControlIterations) {
		this.maxControlIterations = Math.max(0, maxControlIterations);
		return this;
	}

	public QstsStudy setPfMethod(DistributionPFMethod pfMethod) {
		this.pfMethod = pfMethod == null ? DistributionPFMethod.Fixed_Point : pfMethod;
		return this;
	}

	public QstsStudy setMaxPowerFlowIterations(int maxPowerFlowIterations) {
		this.maxPowerFlowIterations = Math.max(1, maxPowerFlowIterations);
		return this;
	}

	public QstsStudy setTolerance(double tolerance) {
		this.tolerance = tolerance;
		return this;
	}

	public QstsStudy setInitializeFirstStepVoltages(boolean initializeFirstStepVoltages) {
		this.initializeFirstStepVoltages = initializeFirstStepVoltages;
		return this;
	}

	public QstsResult run() {
		DistributionPowerFlowAlgorithm algorithm = powerFlowAlgorithm == null
				? ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(network)
				: powerFlowAlgorithm;
		algorithm.setNetwork(network);
		algorithm.setPFMethod(pfMethod);
		algorithm.setMaxIteration(maxPowerFlowIterations);
		algorithm.setTolerance(tolerance);
		algorithm.setRegulatorControls(regulatorControls);
		algorithm.setCapacitorControls(capacitorControls);
		registerMissingInverterAdapters();
		boolean controlsEnabled = controlMode != QstsControlMode.OFF && maxControlIterations > 0;
		boolean delayedCapacitorControls = usesDelayedControlQueue();
		algorithm.setFixedPointYMatrixCacheEnabled(controlCompensationPolicy.canReuseFixedPointYMatrix(
				pfMethod, controlsEnabled, regulatorControls, capacitorControls, inverterControls));
		algorithm.setRegulatorControlEnabled(controlsEnabled && !regulatorControls.isEmpty());
		algorithm.setCapacitorControlEnabled(controlsEnabled && !delayedCapacitorControls
				&& !capacitorControls.isEmpty());

		List<QstsStepResult> steps = new ArrayList<>();
		for(int i = 0; i < numberOfSteps; i++) {
			int scheduleIndex = startIndex + i;
			double hour = startHour + i * stepSizeHours;
			QstsStepContext context = new QstsStepContext(i, scheduleIndex, hour, mode,
					stepSizeHours, loadMultiplier, controlMode);
			stateApplier.apply(context);
			if(delayedCapacitorControls) {
				qstsCapacitorControl.applyConfiguredStates(network, capacitorControls);
			}
			int actionCount = processQueuedActions(delayedCapacitorControls, hour * 3600.0);
			algorithm.setInitBusVoltageEnabled(i == 0 ? initializeFirstStepVoltages : false);
			PowerFlowControlResult controlResult = runPowerFlowControlLoop(algorithm, context,
					controlsEnabled);
			boolean converged = controlResult.converged;
			List<QstsInverterControlSample> inverterControlSamples = controlResult.inverterControlSamples;
			if(converged && delayedCapacitorControls) {
				qstsCapacitorControl.scheduleDelayed(network, capacitorControls, controlQueue,
						hour * 3600.0);
			}
			String failureReason = converged ? null
					: "Distribution power flow did not converge at step " + i
							+ " mode=" + mode + " hour=" + hour;
			steps.add(new QstsStepResult(context, converged, algorithm.getIterationCount(),
					Double.NaN, failureReason, actionCount, sampleBusVoltages(context),
					sampleLoadPowers(context), sampleGeneratorPowers(context),
					sampleCapacitorStates(context), inverterControlSamples));
			if(!converged) {
				break;
			}
		}
		return new QstsResult(steps);
	}

	public QstsStateApplier getStateApplier() {
		return stateApplier;
	}

	public int getMaxControlIterations() {
		return maxControlIterations;
	}

	private boolean usesDelayedControlQueue() {
		return controlMode == QstsControlMode.TIME || controlMode == QstsControlMode.EVENT;
	}

	private int processQueuedActions(boolean enabled, double timeSeconds) {
		if(!enabled) {
			return 0;
		}
		return controlQueue.processUntil(timeSeconds, this::recordAppliedControlAction);
	}

	private void recordAppliedControlAction(QstsControlAction action) {
		String key = action.getKey();
		operationCountByControlKey.put(key, Integer.valueOf(
				operationCountByControlKey.getOrDefault(key, Integer.valueOf(0)).intValue() + 1));
	}

	private List<QstsBusVoltageSample> sampleBusVoltages(QstsStepContext context) {
		List<QstsBusVoltageSample> samples = new ArrayList<>();
		for(IBus3Phase bus : network.getThreePhaseBusList()) {
			Complex3x1 voltage = bus.get3PhaseVotlages();
			addVoltage(samples, context, bus.getId(), "A", voltage == null ? null : voltage.a_0);
			addVoltage(samples, context, bus.getId(), "B", voltage == null ? null : voltage.b_1);
			addVoltage(samples, context, bus.getId(), "C", voltage == null ? null : voltage.c_2);
		}
		return samples;
	}

	private List<QstsDevicePowerSample> sampleLoadPowers(QstsStepContext context) {
		List<QstsDevicePowerSample> samples = new ArrayList<>();
		for(IBus3Phase bus : network.getThreePhaseBusList()) {
			Map<Object, Boolean> sampled = new IdentityHashMap<>();
			for(IPhaseLoad load : bus.getPhaseLoadList()) {
				addLoadSample(samples, context, load);
				sampled.put(load, Boolean.TRUE);
			}
			if(bus instanceof BaseAclfBus) {
				for(Object loadObject : ((BaseAclfBus<?, ?>) bus).getContributeLoadList()) {
					if(sampled.containsKey(loadObject)
							|| !(loadObject instanceof IPhaseLoad)) {
						continue;
					}
					addLoadSample(samples, context, (IPhaseLoad) loadObject);
				}
			}
		}
		return samples;
	}

	private static void addLoadSample(List<QstsDevicePowerSample> samples, QstsStepContext context,
			IPhaseLoad load) {
		Complex3x1 power = load.getInit3PhaseLoad();
		addPower(samples, context, "load", load.getId(), "A", power == null ? null : power.a_0);
		addPower(samples, context, "load", load.getId(), "B", power == null ? null : power.b_1);
		addPower(samples, context, "load", load.getId(), "C", power == null ? null : power.c_2);
	}

	private List<QstsDevicePowerSample> sampleGeneratorPowers(QstsStepContext context) {
		List<QstsDevicePowerSample> samples = new ArrayList<>();
		for(IBus3Phase bus : network.getThreePhaseBusList()) {
			Map<Object, Boolean> sampled = new IdentityHashMap<>();
			for(IPhaseGen generator : bus.getPhaseGenList()) {
				addGeneratorSample(samples, context, generator);
				sampled.put(generator, Boolean.TRUE);
			}
			if(bus instanceof BaseAclfBus) {
				for(Object generatorObject : ((BaseAclfBus<?, ?>) bus).getContributeGenList()) {
					if(sampled.containsKey(generatorObject)
							|| !(generatorObject instanceof IPhaseGen)) {
						continue;
					}
					addGeneratorSample(samples, context, (IPhaseGen) generatorObject);
				}
			}
		}
		return samples;
	}

	private List<QstsCapacitorStateSample> sampleCapacitorStates(QstsStepContext context) {
		List<QstsCapacitorStateSample> samples = new ArrayList<>();
		for(CapacitorControlData control : capacitorControls) {
			Complex3x1 power = qstsCapacitorControl.capacitorPower(network, control.getCapacitorId());
			Complex total = add(add(power.a_0, power.b_1), power.c_2);
			String key = qstsCapacitorControl.controlActionKey(control);
			int operationCount = operationCountByControlKey.getOrDefault(key, Integer.valueOf(0)).intValue();
			samples.add(new QstsCapacitorStateSample(context.getStepIndex(), context.getHour(),
					control.getCapacitorId(), control.isClosed(), total.getImaginary(),
					total.getImaginary() * aclfNetwork().getBaseKva(), operationCount));
		}
		return samples;
	}

	private PowerFlowControlResult runPowerFlowControlLoop(DistributionPowerFlowAlgorithm algorithm,
			QstsStepContext context, boolean controlsEnabled) {
		List<QstsInverterControlSample> inverterControlSamples = Collections.emptyList();
		boolean converged = false;
		for(int controlIteration = 0; controlIteration < Math.max(1, maxControlIterations); controlIteration++) {
			converged = algorithm.powerflow();
			algorithm.setInitBusVoltageEnabled(false);
			if(!converged || !controlsEnabled || inverterControls.isEmpty()) {
				return new PowerFlowControlResult(converged, inverterControlSamples);
			}
			InverterControlPassResult inverterResult = applyInverterControls(context);
			inverterControlSamples = inverterResult.samples;
			if(!inverterResult.changed) {
				return new PowerFlowControlResult(true, inverterControlSamples);
			}
		}
		return new PowerFlowControlResult(converged, inverterControlSamples);
	}

	private InverterControlPassResult applyInverterControls(QstsStepContext context) {
		List<QstsInverterControlSample> samples = new ArrayList<>();
		boolean changed = false;
		for(InverterControlData control : inverterControls) {
			InverterGenAdapter adapter = inverterAdapterStore.get(control.getGeneratorId());
			if(adapter == null) {
				samples.add(inverterSample(context, control, false, 0.0, 0.0, false, "missing_generator"));
				continue;
			}
			IBus3Phase bus = findGeneratorBus(adapter.getGenerator());
			if(bus != null) {
				adapter.setTerminalVoltage(bus.get3PhaseVotlages());
			}
			Complex before = totalPower(adapter.getGenerator());
			InverterControlResult result = adapter.apply(control, context, aclfNetwork().getBaseKva());
			Complex after = totalPower(adapter.getGenerator());
			changed = changed || powerChanged(before, after);
			samples.add(inverterSample(context, control, result.isApplied(), result.getActivePowerKw(),
					result.getReactivePowerKvar(), result.isLimited(),
					result.isApplied() ? "" : "missing_setpoint"));
		}
		return new InverterControlPassResult(samples, changed);
	}

	private boolean powerChanged(Complex before, Complex after) {
		double baseKva = aclfNetwork().getBaseKva();
		double threshold = Math.max(1.0e-6, Math.abs(tolerance) * baseKva);
		return Math.abs(after.getReal() - before.getReal()) * baseKva > threshold
				|| Math.abs(after.getImaginary() - before.getImaginary()) * baseKva > threshold;
	}

	private void registerMissingInverterAdapters() {
		for(InverterControlData control : inverterControls) {
			if(inverterAdapterStore.get(control.getGeneratorId()) != null) {
				continue;
			}
			IPhaseGen generator = findGenerator(control.getGeneratorId());
			if(generator != null) {
				inverterAdapterStore.register(generator);
			}
		}
	}

	private QstsInverterControlSample inverterSample(QstsStepContext context, InverterControlData control,
			boolean applied, double activePowerKw, double reactivePowerKvar, boolean limited, String reason) {
		return new QstsInverterControlSample(context.getStepIndex(), context.getHour(),
				control.getId(), control.getGeneratorId(), control.getControlMode().name(),
				applied, activePowerKw, reactivePowerKvar, limited, reason);
	}

	public IPhaseGen findGenerator(String generatorId) {
		for(IBus3Phase bus : network.getThreePhaseBusList()) {
			for(IPhaseGen generator : bus.getPhaseGenList()) {
				if(matches(generator.getId(), generatorId)) {
					return generator;
				}
			}
			if(bus instanceof BaseAclfBus) {
				for(Object generatorObject : ((BaseAclfBus<?, ?>) bus).getContributeGenList()) {
					if(generatorObject instanceof IPhaseGen
							&& matches(((IPhaseGen) generatorObject).getId(), generatorId)) {
						return (IPhaseGen) generatorObject;
					}
				}
			}
		}
		return null;
	}

	private IBus3Phase findGeneratorBus(IPhaseGen targetGenerator) {
		for(IBus3Phase bus : network.getThreePhaseBusList()) {
			for(IPhaseGen generator : bus.getPhaseGenList()) {
				if(generator == targetGenerator) {
					return bus;
				}
			}
			if(bus instanceof BaseAclfBus) {
				for(Object generatorObject : ((BaseAclfBus<?, ?>) bus).getContributeGenList()) {
					if(generatorObject == targetGenerator) {
						return bus;
					}
				}
			}
		}
		return null;
	}

	private static void addGeneratorSample(List<QstsDevicePowerSample> samples, QstsStepContext context,
			IPhaseGen generator) {
		Complex3x1 power = generator.getPower3Phase(UnitType.PU);
		addPower(samples, context, "generator", generator.getId(), "A", power == null ? null : power.a_0);
		addPower(samples, context, "generator", generator.getId(), "B", power == null ? null : power.b_1);
		addPower(samples, context, "generator", generator.getId(), "C", power == null ? null : power.c_2);
	}

	private static void addVoltage(List<QstsBusVoltageSample> samples, QstsStepContext context,
			String busId, String phase, Complex voltage) {
		samples.add(new QstsBusVoltageSample(context.getStepIndex(), context.getHour(), busId, phase,
				voltage == null ? Double.NaN : voltage.abs(),
				voltage == null ? Double.NaN : Math.toDegrees(voltage.getArgument())));
	}

	private static void addPower(List<QstsDevicePowerSample> samples, QstsStepContext context,
			String deviceClass, String deviceId, String phase, Complex power) {
		samples.add(new QstsDevicePowerSample(context.getStepIndex(), context.getHour(), deviceClass,
				deviceId, phase, power == null ? Double.NaN : power.getReal(),
				power == null ? Double.NaN : power.getImaginary()));
	}

	private static Complex add(Complex left, Complex right) {
		Complex a = left == null ? Complex.ZERO : left;
		Complex b = right == null ? Complex.ZERO : right;
		return a.add(b);
	}

	private static Complex totalPower(IPhaseGen generator) {
		if(generator == null) {
			return Complex.ZERO;
		}
		Complex3x1 power = generator.getPower3Phase(UnitType.PU);
		if(power == null) {
			return Complex.ZERO;
		}
		return add(add(power.a_0, power.b_1), power.c_2);
	}

	private static boolean matches(String actual, String expected) {
		return actual != null && expected != null && actual.equalsIgnoreCase(expected);
	}

	private BaseAclfNetwork<?, ?> aclfNetwork() {
		if(network instanceof BaseAclfNetwork) {
			return (BaseAclfNetwork<?, ?>) network;
		}
		throw new IllegalArgumentException("QSTS power flow requires an INetwork3Phase that also extends BaseAclfNetwork");
	}

	private static class PowerFlowControlResult {
		private final boolean converged;
		private final List<QstsInverterControlSample> inverterControlSamples;

		private PowerFlowControlResult(boolean converged,
				List<QstsInverterControlSample> inverterControlSamples) {
			this.converged = converged;
			this.inverterControlSamples = inverterControlSamples;
		}
	}

	private static class InverterControlPassResult {
		private final List<QstsInverterControlSample> samples;
		private final boolean changed;

		private InverterControlPassResult(List<QstsInverterControlSample> samples, boolean changed) {
			this.samples = samples;
			this.changed = changed;
		}
	}
}
