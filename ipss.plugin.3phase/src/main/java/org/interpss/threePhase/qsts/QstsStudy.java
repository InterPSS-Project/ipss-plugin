package org.interpss.threePhase.qsts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.powerflow.control.CapacitorBankControl;
import org.interpss.threePhase.powerflow.DistributionPFMethod;
import org.interpss.threePhase.powerflow.DistributionPostSolveOutputMode;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.control.CapacitorControlData;
import org.interpss.threePhase.powerflow.control.InverterControlData;
import org.interpss.threePhase.powerflow.control.InverterControlModel.InverterControlResult;
import org.interpss.threePhase.powerflow.control.RegulatorControlData;
import org.interpss.threePhase.powerflow.control.RegulatorTapControl;
import org.interpss.threePhase.qsts.control.QstsControlAction;
import org.interpss.threePhase.qsts.control.QstsControlQueue;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.IBranch3Phase;
import com.interpss.core.threephase.IBus3Phase;
import com.interpss.core.threephase.AclfGen3Phase;
import com.interpss.core.threephase.AclfLoad3Phase;
import com.interpss.core.threephase.INetwork3Phase;
import com.interpss.core.net.Branch;

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
	private DistributionPostSolveOutputMode postSolveOutputMode = DistributionPostSolveOutputMode.VOLTAGE_AND_SWING_POWER;
	private QstsResultSamplingMode resultSamplingMode = QstsResultSamplingMode.FULL;
	private int maxPowerFlowIterations = 100;
	private double tolerance = 1.0e-6;
	private boolean initializeFirstStepVoltages = true;
	private List<RegulatorControlData> regulatorControls = Collections.emptyList();
	private List<CapacitorControlData> capacitorControls = Collections.emptyList();
	private List<InverterControlData> inverterControls = Collections.emptyList();
	private QstsInverterAdapterStore inverterAdapterStore = new QstsInverterAdapterStore();
	private final CapacitorBankControl qstsCapacitorControl = new CapacitorBankControl();
	private final RegulatorTapControl qstsRegulatorTapControl = new RegulatorTapControl();
	private final QstsControlQueue controlQueue = new QstsControlQueue();
	private final Map<String, Integer> operationCountByControlKey = new java.util.LinkedHashMap<>();
	private Map<AclfBranch, String> branchElementClassByBranch = Collections.emptyMap();

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

	public QstsStudy setBranchElementClasses(Map<AclfBranch, String> branchElementClassByBranch) {
		if(branchElementClassByBranch == null || branchElementClassByBranch.isEmpty()) {
			this.branchElementClassByBranch = Collections.emptyMap();
		}
		else {
			this.branchElementClassByBranch = new IdentityHashMap<>(branchElementClassByBranch);
		}
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

	public QstsStudy setPostSolveOutputMode(DistributionPostSolveOutputMode postSolveOutputMode) {
		this.postSolveOutputMode = postSolveOutputMode == null
				? DistributionPostSolveOutputMode.VOLTAGE_AND_SWING_POWER : postSolveOutputMode;
		return this;
	}

	public QstsStudy setResultSamplingMode(QstsResultSamplingMode resultSamplingMode) {
		this.resultSamplingMode = resultSamplingMode == null
				? QstsResultSamplingMode.FULL : resultSamplingMode;
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
		QstsRuntimeProfile profile = QstsRuntimeProfile.enabled();
		long setupStartNanos = profile == null ? 0L : System.nanoTime();
		DistributionPowerFlowAlgorithm algorithm = powerFlowAlgorithm == null
				? ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(network)
				: powerFlowAlgorithm;
		algorithm.setNetwork(network);
		algorithm.setPFMethod(pfMethod);
		algorithm.setPostSolveOutputMode(postSolveOutputMode);
		algorithm.setMaxIteration(maxPowerFlowIterations);
		algorithm.setMaxControlIterations(maxControlIterations);
		algorithm.setTolerance(tolerance);
		algorithm.setRegulatorControls(regulatorControls);
		algorithm.setCapacitorControls(capacitorControls);
		stateApplier.initializeLoadNortonReferences();
		registerMissingInverterAdapters();
		boolean controlsEnabled = controlMode != QstsControlMode.OFF && maxControlIterations > 0;
		boolean delayedCapacitorControls = usesDelayedControlQueue();
		boolean steppedRegulatorControls = usesSteppedRegulatorControls(controlsEnabled);
		algorithm.setFixedPointYMatrixCacheEnabled(qstsFixedPointYMatrixCacheEnabled(
				delayedCapacitorControls));
		algorithm.setRegulatorControlEnabled(controlsEnabled && !steppedRegulatorControls
				&& !regulatorControls.isEmpty());
		algorithm.setCapacitorControlEnabled(controlsEnabled && !delayedCapacitorControls
				&& !capacitorControls.isEmpty());
		boolean staticStateReuseEnabled = qstsStaticStateReuseEnabled(delayedCapacitorControls);
		boolean solvedReusableStaticState = false;
		if(profile != null) {
			profile.addSetup(System.nanoTime() - setupStartNanos);
		}

		List<QstsStepResult> steps = new ArrayList<>();
		for(int i = 0; i < numberOfSteps; i++) {
			long stepStartNanos = profile == null ? 0L : System.nanoTime();
			int scheduleIndex = startIndex + i;
			double hour = startHour + i * stepSizeHours;
			QstsStepContext context = new QstsStepContext(i, scheduleIndex, hour, mode,
					stepSizeHours, loadMultiplier, controlMode);
			long stateStartNanos = profile == null ? 0L : System.nanoTime();
			boolean stateChanged = stateApplier.apply(context);
			if(profile != null) {
				profile.addStateApply(System.nanoTime() - stateStartNanos);
			}
			long controlStartNanos = profile == null ? 0L : System.nanoTime();
			if(delayedCapacitorControls) {
				qstsCapacitorControl.applyConfiguredStates(network, capacitorControls);
			}
			int actionCount = processQueuedActions(delayedCapacitorControls, hour * 3600.0);
			if(profile != null) {
				profile.addControls(System.nanoTime() - controlStartNanos);
			}
			algorithm.setInitBusVoltageEnabled(i == 0 ? initializeFirstStepVoltages : false);
			long powerFlowStartNanos = profile == null ? 0L : System.nanoTime();
			boolean reuseSolvedState = solvedReusableStaticState && !stateChanged && actionCount == 0;
			PowerFlowControlResult controlResult = reuseSolvedState
					? PowerFlowControlResult.reusedSolvedState()
					: runPowerFlowControlLoop(algorithm, context, controlsEnabled, profile);
			if(profile != null) {
				profile.addPowerFlow(System.nanoTime() - powerFlowStartNanos,
						controlResult.powerFlowIterations, controlResult.reusedSolvedState);
			}
			boolean converged = controlResult.converged;
			List<QstsInverterControlSample> inverterControlSamples = controlResult.inverterControlSamples;
			controlStartNanos = profile == null ? 0L : System.nanoTime();
			if(converged && delayedCapacitorControls) {
				qstsCapacitorControl.scheduleDelayed(network, capacitorControls, controlQueue,
						hour * 3600.0);
			}
			if(profile != null) {
				profile.addControls(System.nanoTime() - controlStartNanos);
			}
			String failureReason = converged ? null
					: "Distribution power flow did not converge at step " + i
							+ " mode=" + mode + " hour=" + hour;
			long outputStartNanos = profile == null ? 0L : System.nanoTime();
			steps.add(createStepResult(context, converged, controlResult.powerFlowIterations,
					failureReason, actionCount, inverterControlSamples));
			if(converged && steppedRegulatorControls) {
				boolean regulatorChanged = qstsRegulatorTapControl.apply(network, regulatorControls, false);
				if(regulatorChanged) {
					algorithm.clearFixedPointYMatrixCache();
					solvedReusableStaticState = false;
				}
			}
			if(profile != null) {
				profile.addOutputs(System.nanoTime() - outputStartNanos);
				profile.finishStep(System.nanoTime() - stepStartNanos, converged);
			}
			if(!converged) {
				break;
			}
			if(staticStateReuseEnabled) {
				solvedReusableStaticState = converged;
			}
		}
		if(profile != null) {
			profile.print();
		}
		return new QstsResult(steps);
	}

	private QstsStepResult createStepResult(QstsStepContext context, boolean converged,
			int iterationCount, String failureReason, int actionCount,
			List<QstsInverterControlSample> inverterControlSamples) {
		if(resultSamplingMode == QstsResultSamplingMode.NONE) {
			return new QstsStepResult(context, converged, iterationCount, Double.NaN,
					failureReason, actionCount, Collections.emptyList(), Collections.emptyList(),
					Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
					Collections.emptyList(), Collections.emptyList());
		}
		return new QstsStepResult(context, converged, iterationCount, Double.NaN,
				failureReason, actionCount, sampleBusVoltages(context), sampleLoadPowers(context),
				sampleGeneratorPowers(context), sampleBranchPowers(context),
				sampleCapacitorStates(context), sampleRegulatorTaps(context), inverterControlSamples);
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

	private boolean usesSteppedRegulatorControls(boolean controlsEnabled) {
		return controlsEnabled && controlMode == QstsControlMode.STATIC && !regulatorControls.isEmpty();
	}

	private boolean qstsFixedPointYMatrixCacheEnabled(boolean delayedCapacitorControls) {
		return pfMethod == DistributionPFMethod.Fixed_Point
				&& !delayedCapacitorControls
				&& !Boolean.getBoolean("ipss.qsts.disableFixedPointSymbolReuse")
				&& !Boolean.getBoolean("ipss.qsts.disableFixedPointValueUpdate");
	}

	private boolean qstsStaticStateReuseEnabled(boolean delayedCapacitorControls) {
		return !Boolean.getBoolean("ipss.qsts.disableStaticStateReuse")
				&& !delayedCapacitorControls
				&& controlMode == QstsControlMode.OFF
				&& inverterControls.isEmpty();
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
			int phaseMask = activeBusPhaseMask(bus);
			addVoltage(samples, context, bus.getId(), "A", voltage == null ? null : voltage.a_0,
					phaseActive(phaseMask, 0));
			addVoltage(samples, context, bus.getId(), "B", voltage == null ? null : voltage.b_1,
					phaseActive(phaseMask, 1));
			addVoltage(samples, context, bus.getId(), "C", voltage == null ? null : voltage.c_2,
					phaseActive(phaseMask, 2));
		}
		return samples;
	}

	private List<QstsDevicePowerSample> sampleLoadPowers(QstsStepContext context) {
		List<QstsDevicePowerSample> samples = new ArrayList<>();
		Set<String> capacitorLoadIds = capacitorLoadIds();
		for(IBus3Phase bus : network.getThreePhaseBusList()) {
			Map<Object, Boolean> sampled = new IdentityHashMap<>();
			for(AclfLoad3Phase load : bus.getPhaseLoadList()) {
				if(isCapacitorLoad(load, capacitorLoadIds)) {
					continue;
				}
				addLoadSample(samples, context, load, bus);
				sampled.put(load, Boolean.TRUE);
			}
			if(bus instanceof BaseAclfBus) {
				for(Object loadObject : ((BaseAclfBus<?, ?>) bus).getContributeLoadList()) {
					if(sampled.containsKey(loadObject)
							|| !(loadObject instanceof AclfLoad3Phase)) {
						continue;
					}
					AclfLoad3Phase load = (AclfLoad3Phase) loadObject;
					if(isCapacitorLoad(load, capacitorLoadIds)) {
						continue;
					}
					addLoadSample(samples, context, load, bus);
				}
			}
		}
		return samples;
	}

	private Set<String> capacitorLoadIds() {
		Set<String> ids = new HashSet<>();
		for(CapacitorControlData control : capacitorControls) {
			if(control.getCapacitorId() != null) {
				ids.add(control.getCapacitorId().toLowerCase(Locale.ROOT));
			}
		}
		return ids;
	}

	private boolean isCapacitorLoad(AclfLoad3Phase load, Set<String> capacitorLoadIds) {
		return load != null
				&& ((load.getId() != null
						&& capacitorLoadIds.contains(load.getId().toLowerCase(Locale.ROOT)))
					|| isParsedCapacitorLoad(load));
	}

	private boolean isParsedCapacitorLoad(AclfLoad3Phase load) {
		if(load.getCode() != AclfLoadCode.CONST_Z) {
			return false;
		}
		Complex3x1 power = load.getInit3PhaseLoad();
		return isCapacitorPower(power == null ? null : power.a_0)
				&& isCapacitorPower(power == null ? null : power.b_1)
				&& isCapacitorPower(power == null ? null : power.c_2)
				&& (isActiveCapacitorPhase(power == null ? null : power.a_0)
						|| isActiveCapacitorPhase(power == null ? null : power.b_1)
						|| isActiveCapacitorPhase(power == null ? null : power.c_2));
	}

	private boolean isCapacitorPower(Complex power) {
		if(power == null) {
			return true;
		}
		return Math.abs(power.getReal()) <= 1.0e-12
				&& power.getImaginary() <= 1.0e-12;
	}

	private boolean isActiveCapacitorPhase(Complex power) {
		return power != null
				&& Math.abs(power.getReal()) <= 1.0e-12
				&& power.getImaginary() < -1.0e-12;
	}

	private static void addLoadSample(List<QstsDevicePowerSample> samples, QstsStepContext context,
			AclfLoad3Phase load, IBus3Phase bus) {
		Complex3x1 power = QstsLoadPowerSampler.solvedPower(load, bus);
		int phaseMask = phaseCodeMask(load.getPhaseCode()) & activeBusPhaseMask(bus);
		addPower(samples, context, "load", load.getId(), "A", power == null ? null : power.a_0,
				phaseActive(phaseMask, 0));
		addPower(samples, context, "load", load.getId(), "B", power == null ? null : power.b_1,
				phaseActive(phaseMask, 1));
		addPower(samples, context, "load", load.getId(), "C", power == null ? null : power.c_2,
				phaseActive(phaseMask, 2));
	}

	private List<QstsDevicePowerSample> sampleGeneratorPowers(QstsStepContext context) {
		List<QstsDevicePowerSample> samples = new ArrayList<>();
		for(IBus3Phase bus : network.getThreePhaseBusList()) {
			Map<Object, Boolean> sampled = new IdentityHashMap<>();
			for(AclfGen3Phase generator : bus.getPhaseGenList()) {
				addGeneratorSample(samples, context, generator, bus);
				sampled.put(generator, Boolean.TRUE);
			}
			if(bus instanceof BaseAclfBus) {
				for(Object generatorObject : ((BaseAclfBus<?, ?>) bus).getContributeGenList()) {
					if(sampled.containsKey(generatorObject)
							|| !(generatorObject instanceof AclfGen3Phase)) {
						continue;
					}
					addGeneratorSample(samples, context, (AclfGen3Phase) generatorObject, bus);
				}
			}
		}
		return samples;
	}

	private List<QstsBranchPowerSample> sampleBranchPowers(QstsStepContext context) {
		List<QstsBranchPowerSample> samples = new ArrayList<>();
		double phaseBaseKva = aclfNetwork().getBaseKva() / 3.0;
		for(AclfBranch branch : (List<AclfBranch>) aclfNetwork().getBranchList()) {
			if(!branch.isActive() || !(branch instanceof IBranch3Phase)
					|| !(branch.getFromBus() instanceof IBus3Phase)
					|| !(branch.getToBus() instanceof IBus3Phase)
					|| isOpenDssSourceImpedanceBranch(branch)) {
				continue;
			}
			IBranch3Phase branch3Phase = (IBranch3Phase) branch;
			Complex3x1 fromVoltage = ((IBus3Phase) branch.getFromBus()).get3PhaseVotlages();
			Complex3x1 toVoltage = ((IBus3Phase) branch.getToBus()).get3PhaseVotlages();
			if(fromVoltage == null || toVoltage == null) {
				continue;
			}
			Complex3x1 fromCurrent = branch3Phase.getYffabc().multiply(fromVoltage)
					.add(branch3Phase.getYftabc().multiply(toVoltage));
			Complex3x1 toCurrent = branch3Phase.getYttabc().multiply(toVoltage)
					.add(branch3Phase.getYtfabc().multiply(fromVoltage));
			addBranchPowerSamples(samples, context, branch, 1, branch.getFromBus().getId(),
					fromVoltage.multiply(fromCurrent.conjugate()).multiply(phaseBaseKva));
			addBranchPowerSamples(samples, context, branch, 2, branch.getToBus().getId(),
					toVoltage.multiply(toCurrent.conjugate()).multiply(phaseBaseKva));
		}
		return samples;
	}

	private List<QstsCapacitorStateSample> sampleCapacitorStates(QstsStepContext context) {
		List<QstsCapacitorStateSample> samples = new ArrayList<>();
		double phaseBaseKva = aclfNetwork().getBaseKva() / 3.0;
		Set<String> controlledCapacitors = capacitorLoadIds();
		for(CapacitorControlData control : capacitorControls) {
			Complex3x1 power = qstsCapacitorControl.capacitorPower(network, control.getCapacitorId());
			Complex total = add(add(power.a_0, power.b_1), power.c_2);
			String key = qstsCapacitorControl.controlActionKey(control);
			int operationCount = operationCountByControlKey.getOrDefault(key, Integer.valueOf(0)).intValue();
			samples.add(new QstsCapacitorStateSample(context.getStepIndex(), context.getHour(),
					control.getCapacitorId(), control.isClosed(), total.getImaginary(),
					total.getImaginary() * phaseBaseKva, operationCount));
		}
		for(IBus3Phase bus : network.getThreePhaseBusList()) {
			Map<Object, Boolean> sampled = new IdentityHashMap<>();
			for(AclfLoad3Phase load : bus.getPhaseLoadList()) {
				addFixedCapacitorStateSample(samples, context, load, bus, controlledCapacitors,
						phaseBaseKva);
				sampled.put(load, Boolean.TRUE);
			}
			if(bus instanceof BaseAclfBus) {
				for(Object loadObject : ((BaseAclfBus<?, ?>) bus).getContributeLoadList()) {
					if(!sampled.containsKey(loadObject) && loadObject instanceof AclfLoad3Phase) {
						addFixedCapacitorStateSample(samples, context, (AclfLoad3Phase) loadObject,
								bus, controlledCapacitors, phaseBaseKva);
					}
				}
			}
		}
		return samples;
	}

	private void addFixedCapacitorStateSample(List<QstsCapacitorStateSample> samples,
			QstsStepContext context, AclfLoad3Phase load, IBus3Phase bus,
			Set<String> controlledCapacitors, double phaseBaseKva) {
		if(load == null || load.getId() == null
				|| controlledCapacitors.contains(load.getId().toLowerCase(Locale.ROOT))
				|| !isParsedCapacitorLoad(load)) {
			return;
		}
		Complex3x1 power = QstsLoadPowerSampler.solvedPower(load, bus);
		Complex total = add(add(power == null ? null : power.a_0,
				power == null ? null : power.b_1), power == null ? null : power.c_2);
		samples.add(new QstsCapacitorStateSample(context.getStepIndex(), context.getHour(),
				load.getId(), total.getImaginary() < -1.0e-12, total.getImaginary(),
				total.getImaginary() * phaseBaseKva, 0));
	}

	private List<QstsRegulatorTapSample> sampleRegulatorTaps(QstsStepContext context) {
		List<QstsRegulatorTapSample> samples = new ArrayList<>();
		for(RegulatorControlData control : regulatorControls) {
			samples.add(new QstsRegulatorTapSample("",
					context.getStepIndex(), context.getHour(), control.getId(),
					control.getBranchName(), control.getWinding(), control.getTapWinding(),
					control.getPhaseCode() == null ? "" : control.getPhaseCode().name(),
					control.getTapPosition(), control.getTapRatio(), control.getTargetVoltage(),
					control.getBandwidth(), control.getPtRatio(), control.getDelaySeconds(),
					control.getRegulatedBusId() == null ? "" : control.getRegulatedBusId(),
					control.getLineDropR(), control.getLineDropX(), control.getVLimit()));
		}
		return samples;
	}

	private PowerFlowControlResult runPowerFlowControlLoop(DistributionPowerFlowAlgorithm algorithm,
			QstsStepContext context, boolean controlsEnabled, QstsRuntimeProfile profile) {
		List<QstsInverterControlSample> inverterControlSamples = Collections.emptyList();
		boolean converged = false;
		int powerFlowIterations = 0;
		for(int controlIteration = 0; controlIteration < Math.max(1, maxControlIterations); controlIteration++) {
			converged = algorithm.powerflow();
			powerFlowIterations += algorithm.getIterationCount();
			algorithm.setInitBusVoltageEnabled(false);
			if(!converged || !controlsEnabled || inverterControls.isEmpty()) {
				return new PowerFlowControlResult(converged, inverterControlSamples, powerFlowIterations);
			}
			long inverterControlStartNanos = profile == null ? 0L : System.nanoTime();
			InverterControlPassResult inverterResult = applyInverterControls(context);
			if(profile != null) {
				profile.addControls(System.nanoTime() - inverterControlStartNanos);
			}
			inverterControlSamples = inverterResult.samples;
			if(!inverterResult.changed) {
				return new PowerFlowControlResult(true, inverterControlSamples, powerFlowIterations);
			}
		}
		return new PowerFlowControlResult(converged, inverterControlSamples, powerFlowIterations);
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
			AclfGen3Phase generator = findGenerator(control.getGeneratorId());
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

	public AclfGen3Phase findGenerator(String generatorId) {
		for(IBus3Phase bus : network.getThreePhaseBusList()) {
			for(AclfGen3Phase generator : bus.getPhaseGenList()) {
				if(matches(generator.getId(), generatorId)) {
					return generator;
				}
			}
			if(bus instanceof BaseAclfBus) {
				for(Object generatorObject : ((BaseAclfBus<?, ?>) bus).getContributeGenList()) {
					if(generatorObject instanceof AclfGen3Phase
							&& matches(((AclfGen3Phase) generatorObject).getId(), generatorId)) {
						return (AclfGen3Phase) generatorObject;
					}
				}
			}
		}
		return null;
	}

	private IBus3Phase findGeneratorBus(AclfGen3Phase targetGenerator) {
		for(IBus3Phase bus : network.getThreePhaseBusList()) {
			for(AclfGen3Phase generator : bus.getPhaseGenList()) {
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
			AclfGen3Phase generator, IBus3Phase bus) {
		Complex3x1 power = generator.getPower3Phase(UnitType.PU);
		int phaseMask = phaseCodeMask(generator.getPhaseCode()) & activeBusPhaseMask(bus);
		addPower(samples, context, "generator", generator.getId(), "A", power == null ? null : power.a_0,
				phaseActive(phaseMask, 0));
		addPower(samples, context, "generator", generator.getId(), "B", power == null ? null : power.b_1,
				phaseActive(phaseMask, 1));
		addPower(samples, context, "generator", generator.getId(), "C", power == null ? null : power.c_2,
				phaseActive(phaseMask, 2));
	}

	private static void addVoltage(List<QstsBusVoltageSample> samples, QstsStepContext context,
			String busId, String phase, Complex voltage, boolean activePhase) {
		if(!activePhase) {
			return;
		}
		samples.add(new QstsBusVoltageSample(context.getStepIndex(), context.getHour(), busId, phase,
				voltage == null ? Double.NaN : voltage.abs(),
				voltage == null ? Double.NaN : Math.toDegrees(voltage.getArgument())));
	}

	private static void addPower(List<QstsDevicePowerSample> samples, QstsStepContext context,
			String deviceClass, String deviceId, String phase, Complex power, boolean activePhase) {
		if(!activePhase) {
			return;
		}
		samples.add(new QstsDevicePowerSample(context.getStepIndex(), context.getHour(), deviceClass,
				deviceId, phase, power == null ? Double.NaN : power.getReal(),
				power == null ? Double.NaN : power.getImaginary()));
	}

	private void addBranchPowerSamples(List<QstsBranchPowerSample> samples,
			QstsStepContext context, AclfBranch branch, int terminal, String busId, Complex3x1 power) {
		int phaseMask = branch instanceof IBranch3Phase
				? phaseCodeMask(((IBranch3Phase) branch).getPhaseCode()) : 0b111;
		addBranchPowerSample(samples, context, branch, terminal, busId, "A",
				power == null ? null : power.a_0, phaseActive(phaseMask, 0));
		addBranchPowerSample(samples, context, branch, terminal, busId, "B",
				power == null ? null : power.b_1, phaseActive(phaseMask, 1));
		addBranchPowerSample(samples, context, branch, terminal, busId, "C",
				power == null ? null : power.c_2, phaseActive(phaseMask, 2));
	}

	private void addBranchPowerSample(List<QstsBranchPowerSample> samples,
			QstsStepContext context, AclfBranch branch, int terminal, String busId, String phase,
			Complex power, boolean activePhase) {
		if(!activePhase) {
			return;
		}
		if(power == null || !Double.isFinite(power.getReal()) || !Double.isFinite(power.getImaginary())) {
			return;
		}
		samples.add(new QstsBranchPowerSample(context.getStepIndex(), context.getHour(),
				branchElementClass(branch), branchElementId(branch), terminal, busId, phase,
				power.getReal(), power.getImaginary()));
	}

	private String branchElementClass(AclfBranch branch) {
		String elementClass = this.branchElementClassByBranch.get(branch);
		if(elementClass != null && !elementClass.isBlank()) {
			return elementClass;
		}
		return branch.isXfr() ? "transformer" : "line";
	}

	private static String branchElementId(AclfBranch branch) {
		String name = branch.getName();
		return name == null || name.isBlank() ? branch.getId() : name;
	}

	private static boolean isOpenDssSourceImpedanceBranch(AclfBranch branch) {
		String name = branch.getName();
		if(name == null || !name.toLowerCase(Locale.ROOT).startsWith("vsource_")) {
			return false;
		}
		return branch.getFromBus() != null
				&& branch.getFromBus().getId() != null
				&& branch.getFromBus().getId().toLowerCase(Locale.ROOT).endsWith("_vsource");
	}

	private static int activeBusPhaseMask(IBus3Phase bus) {
		int branchMask = connectedBranchPhaseMask(bus);
		if(branchMask != 0) {
			return branchMask;
		}
		int mask = 0;
		for(AclfLoad3Phase load : bus.getPhaseLoadList()) {
			mask |= phaseCodeMask(load.getPhaseCode());
		}
		for(AclfGen3Phase generator : bus.getPhaseGenList()) {
			mask |= phaseCodeMask(generator.getPhaseCode());
		}
		return mask == 0 ? 0b111 : mask;
	}

	private static int connectedBranchPhaseMask(IBus3Phase bus) {
		int mask = 0;
		if(bus instanceof BaseAclfBus) {
			for(Object branchObject : ((BaseAclfBus<?, ?>) bus).getBranchIterable()) {
				if(branchObject instanceof Branch
						&& ((Branch) branchObject).isActive()
						&& branchObject instanceof IBranch3Phase) {
					mask |= phaseCodeMask(((IBranch3Phase) branchObject).getPhaseCode());
				}
			}
		}
		return mask;
	}

	private static boolean phaseActive(int phaseMask, int phaseIndex) {
		return (phaseMask & (1 << phaseIndex)) != 0;
	}

	private static int phaseCodeMask(PhaseCode phaseCode) {
		String phase = phaseCode == null ? "ABC" : phaseCode.toString();
		if("ABC".equals(phase)) {
			return 0b111;
		}
		if("A".equals(phase)) {
			return 0b001;
		}
		if("B".equals(phase)) {
			return 0b010;
		}
		if("C".equals(phase)) {
			return 0b100;
		}
		if("AB".equals(phase)) {
			return 0b011;
		}
		if("AC".equals(phase)) {
			return 0b101;
		}
		if("BC".equals(phase)) {
			return 0b110;
		}
		return 0b111;
	}

	private static Complex add(Complex left, Complex right) {
		Complex a = left == null ? Complex.ZERO : left;
		Complex b = right == null ? Complex.ZERO : right;
		return a.add(b);
	}

	private static Complex totalPower(AclfGen3Phase generator) {
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
		private final int powerFlowIterations;
		private final boolean reusedSolvedState;

		private PowerFlowControlResult(boolean converged,
				List<QstsInverterControlSample> inverterControlSamples, int powerFlowIterations) {
			this(converged, inverterControlSamples, powerFlowIterations, false);
		}

		private PowerFlowControlResult(boolean converged,
				List<QstsInverterControlSample> inverterControlSamples, int powerFlowIterations,
				boolean reusedSolvedState) {
			this.converged = converged;
			this.inverterControlSamples = inverterControlSamples;
			this.powerFlowIterations = powerFlowIterations;
			this.reusedSolvedState = reusedSolvedState;
		}

		private static PowerFlowControlResult reusedSolvedState() {
			return new PowerFlowControlResult(true, Collections.emptyList(), 0, true);
		}
	}

	private static class QstsRuntimeProfile {
		private long setupNanos;
		private long stateApplyNanos;
		private long controlsNanos;
		private long powerFlowNanos;
		private long outputsNanos;
		private long totalStepNanos;
		private int steps;
		private int convergedSteps;
		private int powerFlowIterations;
		private int reusedPowerFlowSteps;

		private static QstsRuntimeProfile enabled() {
			return Boolean.getBoolean("ipss.qsts.profile") ? new QstsRuntimeProfile() : null;
		}

		private void addSetup(long nanos) {
			setupNanos += nanos;
		}

		private void addStateApply(long nanos) {
			stateApplyNanos += nanos;
		}

		private void addControls(long nanos) {
			controlsNanos += nanos;
		}

		private void addPowerFlow(long nanos, int iterations, boolean reusedSolvedState) {
			powerFlowNanos += nanos;
			powerFlowIterations += iterations;
			if(reusedSolvedState) {
				reusedPowerFlowSteps++;
			}
		}

		private void addOutputs(long nanos) {
			outputsNanos += nanos;
		}

		private void finishStep(long nanos, boolean converged) {
			totalStepNanos += nanos;
			steps++;
			if(converged) {
				convergedSteps++;
			}
		}

		private void print() {
			int divisor = Math.max(1, steps);
			System.out.printf("[QSTS profile] steps=%d converged_steps=%d pf_iterations=%d pf_iterations_per_step=%.6f setup_ms=%.3f%n",
					Integer.valueOf(steps), Integer.valueOf(convergedSteps),
					Integer.valueOf(powerFlowIterations), Double.valueOf(powerFlowIterations / (double) divisor),
					Double.valueOf(toMillis(setupNanos)));
			System.out.printf("[QSTS profile] reused_powerflow_steps=%d%n",
					Integer.valueOf(reusedPowerFlowSteps));
			System.out.printf("[QSTS profile] per_step_ms total=%.6f state_apply=%.6f controls=%.6f powerflow=%.6f outputs=%.6f%n",
					Double.valueOf(toMillis(totalStepNanos) / divisor),
					Double.valueOf(toMillis(stateApplyNanos) / divisor),
					Double.valueOf(toMillis(controlsNanos) / divisor),
					Double.valueOf(toMillis(powerFlowNanos) / divisor),
					Double.valueOf(toMillis(outputsNanos) / divisor));
		}

		private static double toMillis(long nanos) {
			return nanos / 1_000_000.0;
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
