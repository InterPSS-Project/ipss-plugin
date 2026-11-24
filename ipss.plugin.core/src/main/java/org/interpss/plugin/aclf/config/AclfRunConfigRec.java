package org.interpss.plugin.aclf.config;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Set;

import com.interpss.common.datatype.BaseJSONBean;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.util.FileUtil;

import com.interpss.core.aclf.AclfNetModelType;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.AdjustApplyType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.LoadflowAlgorithmInitializer;
import org.interpss.fadapter.psse.PsseLoadflowSolutionSettings;
import com.interpss.core.algo.LoadflowAlgorithm.VscCapabilityPriority;
import com.interpss.core.algo.NrMethodConfig;
import com.interpss.core.algo.impl.solver.CoordinatedControlZbrNrSolver;
import com.interpss.core.algo.impl.solver.DefaultNrSolver;
import com.interpss.core.algo.impl.solver.ZbrNrSolver;
import com.interpss.core.algo.NrOptimizeAlgoType;
import com.interpss.core.algo.config.ControlInitializationMode;
import com.interpss.core.algo.config.GenQLimitInitializationMode;
import com.interpss.core.algo.config.RemoteQControlMode;

/**
 * Versioned JSON contract for configuring one ACLF run.
 *
 * <p>The record contains solver and control policy only; parser selection and
 * file-format choices belong to the import layer. The intended precedence is
 * imported RAW settings first and explicit fields in this record second.
 * Omitted JSON fields preserve the imported/current Core value, including
 * primitive fields. A programmatically constructed record retains the legacy
 * complete-bean application semantics.</p>
 *
 * <p>Adjustment-family switches are independent. Numerical NR controls are
 * applied even when adjustment is disabled: normal full Newton may use a global
 * variable update limit, while optimizer/minimum-scale settings are consulted
 * only when {@link #nonDivergent} is true.</p>
 */
public class AclfRunConfigRec extends BaseJSONBean {
	public static final int CURRENT_SCHEMA_VERSION = 1;

	private transient Set<String> explicitJsonFields;

	public int schemaVersion = CURRENT_SCHEMA_VERSION;
	public AclfMethodType lfMethod = AclfMethodType.NR;
	public boolean polarCoordinate = true;
	public double tolerance = 0.0001;
	public UnitType tolUnitType = UnitType.PU;
	public int maxIterations = 20;
	public boolean autoSetZeroZBranch = true;
	public ZeroZBranchProcessingMode zeroZBranchProcessingMode;
	public boolean turnOffIslandBus = true;
	public boolean autoTurnLine2Xfr = true;
	
	public boolean busLoadLowVoltAdj = true;
	public double vConstPMin = 0.7;
	public double vConstIMin = 0.5;
	
	/**
	 * Compatibility master gate for adjustment processing. Individual control
	 * family flags below remain authoritative. Keep enabled by default while
	 * older callers still depend on this gate; remove it once all callers use
	 * only the family-specific settings.
	 */
	public boolean includeAdjustments = true;
	public boolean activateAllAdjCtrl = false;
	
	public boolean applyLimitControl = true;
	public boolean pvBusLimitControl = true;
	public boolean pqBusLimitControl = true;
	public boolean limitBackoffCheck = false;
	public boolean checkGenQLimImmediate = false;
	
	public boolean applyVoltAdjust = true;
	public boolean applyDiscreteAdjust = true;
	public boolean remoteQBusControl = true;
	public boolean switchedShuntAdjust = true;
	public boolean svcFactsAdjust = true;
	public boolean xfrTapControl = true;
	public boolean hvdcTapControl = true;
	
	public boolean applyPowerAdjust = true;
	public boolean psXfrPControl = true;

	// Core 1.3.12 solver controls. Wrapper types keep null = "leave the
	// algorithm default (or replayed case mode) untouched".
	public Boolean areaInterchangeControl;
	public Integer maxAreaInterchangeAdjustmentsPerIteration;
	public Double maxAreaInterchangeAngleStepRad;
	public Double maxAreaInterchangePowerStepPu;
	public Double areaInterchangeAdjustmentFactor;
	public Integer maxPvLimitAdjustmentsPerIteration;
	// Couple eligible detailed two-terminal LCC injections directly into the
	// coordinated NR Jacobian. Null/false preserves the legacy outer-loop model.
	public Boolean reducedLccCouplingEnabled;

	// An omitted field inherits the imported control defaults. Explicit false
	// opts out of inherited switches and saved-state initialization. Explicit
	// true also applies the imported area-interchange mode, when present.
	public boolean savedSolutionReplay = false;

	// Capture the core load-flow detailed iteration/message log in run output.
	public boolean messageLoggingEnabled = false;

	// Advanced ACLF run controls. Wrapper types keep null = "leave imported,
	// current, or core default state untouched".
	public Boolean initBusVoltage;
	public Boolean nrStepSizeOptimization;
	public Boolean allowSwingBusWithoutActiveGenerator;
	public Boolean keepRawFileSettings;
	public Boolean savedAreaInterchangeReplay;
	public Boolean rawSettingsReplaySavedDiscreteControls;
	public Boolean rawSettingsSavedDiscreteReplay;
	public Boolean rawSettingsSensitivityFallback;
	public Boolean rawSettingsFallbackVariableUpdate;
	public Boolean rawSettingsFallbackRegularizeB11;
	public Integer rawSettingsMaxIterationsOverride;
	public AclfNetModelType aclfNetModelType;
	public Boolean configureBusLowVoltageAdjustment;
	public Boolean initializeHvdcLoadflowWithTapControl;
	public Boolean disableAllAdjustmentControls;
	public Boolean enableSwitchedShuntAdjustForAllAreas;
	public Boolean restoreDevicelessBusCodes;
	public Boolean zeroInactiveSwitchedShunts;
	public Boolean coordinatedControlZbrSolver;
	public Boolean coordinatedControlAugmentedZbr;
	public Double coordinatedControlTolerance;
	public Boolean coordinatedControlEnableRemoteQ;
	public Boolean coordinatedControlReplaySavedState;
	public Boolean coordinatedControlUseVoltageTolerance;
	// Default-off coordinated-control experiments. Keeping these options in the
	// shared plugin record lets desktop, CLI, MCP, and large-case runners consume
	// one JSON contract without case-specific parsers.
	public Boolean coordinatedControlEnableInnerTaps;
	/** Shared inner/outer transformer tap-ratio step ceiling in pu. */
	public Double tapChangeLimit;
	public Boolean coordinatedPqControlEnabled;
	public Boolean coupledLccPqControlEnabled;
	public Boolean adaptiveLccRecoveryEnabled;
	public Boolean coupledVscPqControlEnabled;
	public VscCapabilityPriority vscCapabilityPriority;

	public Double hvdcLfSwitchFactor;
	public Double hvdcOuterAdjustmentFactor;
	public Boolean hvdcOuterControlEnabled;
	public Integer maxHvdcOuterOscillationsBeforeLock;
	public Integer maxAreaInterchangeTrialIterations;

	public RemoteQControlMode remoteQControlMode;
	public Integer maxRemoteQAdjustmentsPerIteration;
	public Integer maxSwitchedShuntAdjustmentsPerIteration;
	public Integer maxSvcAdjustmentsPerIteration;
	public Integer maxTapAdjustmentsPerIteration;
	public Integer maxPsXfrPAdjustmentsPerIteration;

	public Double voltageAdjustmentThreshold;
	public Double highGainVoltageControlDampingThreshold;
	public Double highGainVoltageControlMaxAccelerationFactor;
	public Boolean localContinuousSvcAsPv;
	public Double minimumLocalContinuousSvcPvSensitivity;
	public Double minimumTapVoltageSensitivity;

	public Integer maxTapAndShuntAdjustmentIterations;
	public Integer maxTapAdjustmentIterations;
	public Integer maxSwitchedShuntAdjustmentIterations;

	public Boolean requirePriorPvLimitViolation;
	public Integer minPvLimitStableIterations;
	public Integer minPvLimitControlIteration;
	public Integer maxPvLimitControlIteration;
	public Integer maxPvLimitRecoveryIterations;
	public Integer maxPvLimitBuses;
	public Double minSavedPvLimitAngleMismatch;
	public ControlInitializationMode controlInitializationMode;

	/** @deprecated use {@link #controlInitializationMode}. */
	@Deprecated
	public GenQLimitInitializationMode genQLimitInitializationMode;
	public Integer maxSavedSolutionQLimitSeeds;
	public Integer maxAdditionalSavedSolutionQLimitBuses;
	public Double savedSolutionQLimitTolerance;
	public Double savedSolutionVoltageEvidenceTolerance;
	
	// NR method config
	public boolean nonDivergent = false;
	public NrOptimizeAlgoType optAlgo = defaultNrOptimizeAlgoType();
	public boolean variableUpdateLimit = false;
	public double deltaVAngLimit = 0.2;
	public double deltaVMagLimit = 0.1;
	public boolean stopNoSolutionFound = false;
	public double minScaleFactor = 0.01;
	
	// Adjustment/Control settings
	public int limitCtrlStartPoint = 10;
	public double limitCtrlTolearnceFactor = 10.0;
	public AdjustApplyType limitCtrlApplyType = AdjustApplyType.DURING_ITERATION;
	
	// Mod 26-03: use absolute value for voltAdjTolerance
	public int voltAdjStartPoint = 10;
	public double voltAdjTolearnce = 0.005;
	/** Minimum retained inverse voltage response; explicit legacy values remain supported. */
	public double dQ_dVThreshold = 0.1;
	public AdjustApplyType voltAdjApplyType = AdjustApplyType.DURING_ITERATION;
	
	public int powerAdjStartPoint = 10;
	public double powerAdjTolearnceFactor = 10.0;
	public AdjustApplyType powerAdjApplyType = AdjustApplyType.POST_ITERATION;
	
	public double pvLimitAccFactor = 1.0;
	public double pqLimitAccFactor = 1.0;
	public double reQBusAccFactor = 1.0;
	public double svcAccFactor = 1.0;
	public double xfrTapAccFactor = 1.0;
	public double psXfrPContrlAccFactor = 1.0;

	/**
	 * Cubic step optimizer is {@code NrOptimizeAlgoType} value 2. Older ipss-core JARs on the
	 * classpath may omit the {@code CUBIC_EQN} literal; avoid a static reference to it.
	 */
	private static NrOptimizeAlgoType defaultNrOptimizeAlgoType() {
		NrOptimizeAlgoType cubic = NrOptimizeAlgoType.get(2);
		return cubic != null ? cubic : NrOptimizeAlgoType.BINARY_SEARCH;
	}

	private static NrOptimizeAlgoType deserializeNrOptimizeAlgoType(JsonElement json, Type typeOfT,
			JsonDeserializationContext context) throws JsonParseException {
		if (json == null || json.isJsonNull()) {
			return defaultNrOptimizeAlgoType();
		}
		String name = json.getAsString();
		try {
			return NrOptimizeAlgoType.valueOf(name);
		} catch (IllegalArgumentException ex) {
			if ("CUBIC_EQN".equals(name) || "CubicEqn".equals(name)) {
				return defaultNrOptimizeAlgoType();
			}
			throw ex;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends BaseJSONBean> T fromString(String json) {
		return (T) parseJson(json, true);
	}

	public static AclfRunConfigRec fromJson(String json) {
		return parseJson(json, true);
	}

	/**
	 * Applies only fields explicitly present in {@code json} over an existing
	 * case-derived configuration. This keeps imported solution settings as defaults
	 * while making selected JSON fields authoritative.
	 */
	public static AclfRunConfigRec overlayJson(AclfRunConfigRec base, String json) {
		AclfRunConfigRec overlay = fromJson(json);
		AclfRunConfigRec merged = copyOf(base);
		for (String fieldName : overlay.explicitJsonFields) {
			java.lang.reflect.Field source = field(overlay.getClass(), fieldName);
			java.lang.reflect.Field target = field(merged.getClass(), fieldName);
			if (source == null || target == null) {
				continue;
			}
			try {
				target.set(merged, source.get(overlay));
			} catch (IllegalAccessException ex) {
				throw new IllegalStateException(
						"Unable to apply AC loadflow config field '" + fieldName + "'", ex);
			}
		}
		if (merged.explicitJsonFields != null) {
			merged.explicitJsonFields.addAll(overlay.explicitJsonFields);
		}
		return merged;
	}

	public boolean explicitlyConfigures(String fieldName) {
		return explicitJsonFields == null || explicitJsonFields.contains(fieldName);
	}

	public boolean wasParsedFromJson() {
		return explicitJsonFields != null;
	}

	public static AclfRunConfigRec copyOf(AclfRunConfigRec config) {
		AclfRunConfigRec copy = new AclfRunConfigRec();
		if (config == null) {
			return copy;
		}
		for (java.lang.reflect.Field field : config.getClass().getFields()) {
			if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			try {
				java.lang.reflect.Field target = field(copy.getClass(), field.getName());
				if (target != null) {
					target.set(copy, field.get(config));
				}
			} catch (IllegalAccessException ex) {
				throw new IllegalStateException("Unable to copy AC loadflow run config", ex);
			}
		}
		if (config.explicitJsonFields != null) {
			copy.explicitJsonFields = new LinkedHashSet<>(config.explicitJsonFields);
		}
		return copy;
	}

	private static AclfRunConfigRec parseJson(
			String json,
			boolean trackExplicitFields) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		validateAclfJsonRoot(root);
		AclfRunConfigRec config = gson().fromJson(json, AclfRunConfigRec.class);
		config.validateSchemaVersion();
		if (trackExplicitFields) {
			config.explicitJsonFields = new LinkedHashSet<>(root.keySet());
			config.explicitJsonFields.removeIf(name -> root.get(name).isJsonNull());
		}
		return config;
	}

	private static Gson gson() {
		return new GsonBuilder().registerTypeAdapter(NrOptimizeAlgoType.class,
				(JsonDeserializer<NrOptimizeAlgoType>) AclfRunConfigRec::deserializeNrOptimizeAlgoType).create();
	}

	private static void validateAclfJsonRoot(JsonObject root) {
		if (root.has("disablePhaseShifterDeviceControls")) {
			throw new IllegalArgumentException(
					"disablePhaseShifterDeviceControls was removed; use psXfrPControl");
		}
		if (root.has("areaInterchangeControlEnabled")) {
			throw new IllegalArgumentException(
					"areaInterchangeControlEnabled was removed; use areaInterchangeControl");
		}
		if (root.has("removeAreaInterchangeControls")) {
			throw new IllegalArgumentException(
					"removeAreaInterchangeControls was removed; use areaInterchangeControl=false");
		}
		if (root.has("halCoordinatedControlProfile")) {
			throw new IllegalArgumentException(
					"halCoordinatedControlProfile was removed; specify the ACLF control fields directly");
		}
		if (root.has("psseVersion") || root.has("psseDirectParser")) {
			throw new IllegalArgumentException(
					"psseVersion and psseDirectParser are case-loader options, not ACLF run config fields");
		}
	}

	private static java.lang.reflect.Field field(Class<?> type, String name) {
		try {
			return type.getField(name);
		} catch (NoSuchFieldException ex) {
			return null;
		}
	}

	/**
	 * Fails fast when a client sends a schema whose field meanings may differ.
	 * Silent best-effort parsing would be unsafe for control booleans because an
	 * omitted or renamed field can materially change the solved active set.
	 */
	public void validateSchemaVersion() {
		if (this.schemaVersion != CURRENT_SCHEMA_VERSION) {
			throw new IllegalArgumentException("Unsupported ACLF run config schema version "
					+ this.schemaVersion + "; expected " + CURRENT_SCHEMA_VERSION);
		}
	}
	
	public static AclfRunConfigRec loadAclfRunConfig(String configFilename) throws IOException {
    	String json = FileUtil.readFileAsString(configFilename);
    	//System.out.println("Loaded saved AC Loadflow config: \n" + json);
    	return new AclfRunConfigRec().fromString(json);
	}
	
	/** Serialize only supplied JSON fields so a round trip does not invent overrides. */
	@Override
	public String toString() {
		JsonObject root = gson().toJsonTree(this).getAsJsonObject();
		if (this.explicitJsonFields != null) {
			root.keySet().removeIf(name -> !name.equals("schemaVersion")
					&& !this.explicitJsonFields.contains(name));
		}
		return new GsonBuilder().setPrettyPrinting().create().toJson(root);
	}

	public void saveAclfRunConfig(String configFilename) {
		JsonObject root = JsonParser.parseString(this.toString()).getAsJsonObject();
		if (this.explicitJsonFields != null) {
			root.keySet().removeIf(name -> !name.equals("schemaVersion")
					&& !this.explicitJsonFields.contains(name));
		}
		String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
		FileUtil.writeText2File(configFilename, json);
	}
	
	/**
	 * Apply this record after Core factory/import initialization. Parsed JSON
	 * contributes only explicitly supplied fields; omitted fields inherit Core
	 * and imported values. The network's existing coordinates/master gate are
	 * preserved unless requested here. No equipment status is activated.
	 */
	public void applyTo(LoadflowAlgorithm algorithm) {
		boolean polar = explicitlyConfigures("polarCoordinate")
				? this.polarCoordinate : algorithm.getAclfNet().isPolarCoordinate();
		boolean adjustments = explicitlyConfigures("includeAdjustments")
				? this.includeAdjustments : algorithm.getLfAdjAlgo().isApplyAdjustAlgo();
		configAclfRun(algorithm, polar, adjustments, true);
	}

	/** Configure the explicitly requested solver using effective control authority. */
	private void configureCoordinatedSolver(LoadflowAlgorithm algorithm) {
		if (!Boolean.TRUE.equals(this.coordinatedControlZbrSolver))
			return;
		if (!algorithm.getAclfNet().isPolarCoordinate())
			throw new IllegalArgumentException("Coordinated ZBR control requires polar coordinates");
		if (algorithm.getLfMethod() != AclfMethodType.NR) {
			if ((algorithm.getLfMethod() != AclfMethodType.PQ_NR
					&& algorithm.getLfMethod() != AclfMethodType.PQ)
					|| !algorithm.isCoordinatedPqControlEnabled())
				throw new IllegalArgumentException(
						"Coordinated ZBR control requires NR or coordinated PQ/PQ_NR");
			// Core owns the native-PQ session and its state-driven NR handoff.
			// Install only the electrical formulation here; installing NR-owned
			// control rows during the predictor would duplicate control ownership.
			algorithm.getLfCalculator().setNrSolver(
					!Boolean.FALSE.equals(this.coordinatedControlAugmentedZbr)
							? new ZbrNrSolver(algorithm.getAclfNet(), algorithm.getNrMethodConfig())
							: new DefaultNrSolver(algorithm.getAclfNet(), algorithm.getNrMethodConfig()));
			return;
		}
		var adjust = algorithm.getLfAdjAlgo();
		var voltage = adjust.getVoltAdjConfig();
		boolean voltageAuthority = adjust.isApplyAdjustAlgo() && voltage.isAdjust();
		boolean innerRemote = adjust.getRemoteQControlMode() == RemoteQControlMode.INNER_PQV;
		boolean includeRemoteQ = voltageAuthority && innerRemote && voltage.isReQBusControl()
				&& !Boolean.FALSE.equals(this.coordinatedControlEnableRemoteQ);
		boolean includeSvc = voltageAuthority && innerRemote && voltage.isSvcFactsAdjust();
		boolean replay = this.coordinatedControlReplaySavedState != null
				? this.coordinatedControlReplaySavedState
				: adjust.getLimitCtrlConfig().getControlInitializationMode()
						== ControlInitializationMode.SAVED_SOLUTION_REPLAY;
		double voltageTolerance = !Boolean.FALSE.equals(this.coordinatedControlUseVoltageTolerance)
				? Math.max(0.0, voltage.getAdjTolerance()) : 0.0;
		var solver = new CoordinatedControlZbrNrSolver(algorithm.getAclfNet(),
				algorithm.getNrMethodConfig(), this.coordinatedControlTolerance != null
						? this.coordinatedControlTolerance : algorithm.getTolerance(),
				includeSvc, adjust.getMaxRemoteQAdjustmentsPerIteration(), replay,
				voltageTolerance, !Boolean.FALSE.equals(this.coordinatedControlAugmentedZbr),
				includeRemoteQ);
		solver.setFullNewtonTapControlEnabled(voltageAuthority && voltage.isXfrTapControl()
				&& algorithm.isFullNewtonTapControlEnabled());
		if (Double.isFinite(algorithm.getTapChangeLimit()))
			solver.setTapChangeLimit(algorithm.getTapChangeLimit());
		solver.setReducedLccCouplingEnabled(algorithm.isCoupledLccPqControlEnabled()
				|| Boolean.TRUE.equals(this.reducedLccCouplingEnabled));
		solver.setReducedVscCouplingEnabled(algorithm.isCoupledVscPqControlEnabled());
		algorithm.getLfCalculator().setNrSolver(solver);
	}

	public void configAclfRun(LoadflowAlgorithm algo, boolean polarCooridnate, boolean appluAdjust, boolean psseConfig) {
		validateSchemaVersion();
		Object imported = algo.getAclfNet().getExtraInfo().get(
				LoadflowAlgorithmInitializer.NETWORK_EXTRA_INFO_KEY);
		if (imported instanceof PsseLoadflowSolutionSettings settings) {
			if (!psseConfig || (explicitlyConfigures("savedSolutionReplay") && !this.savedSolutionReplay)) {
				settings.suppressSharedControlDefaults(algo);
			} else if (this.savedSolutionReplay) {
				var report = settings.applyTo(algo, algo.getAclfNet(),
						PsseLoadflowSolutionSettings.ApplicationPolicy.SAVED_SOLUTION_REPLAY);
				algo.getAclfNet().getExtraInfo().put(
						PsseLoadflowSolutionSettings.APPLICATION_REPORT_EXTRA_INFO_KEY, report);
			}
		}
		double baseMVA = algo.getAclfNet().getBaseMva();

		if (explicitlyConfigures("lfMethod"))
			algo.setLfMethod(this.lfMethod);
		if (this.coordinatedPqControlEnabled != null)
			algo.setCoordinatedPqControlEnabled(this.coordinatedPqControlEnabled);
		if (this.coupledLccPqControlEnabled != null)
			algo.setCoupledLccPqControlEnabled(this.coupledLccPqControlEnabled);
		if (this.adaptiveLccRecoveryEnabled != null)
			algo.setAdaptiveLccRecoveryEnabled(this.adaptiveLccRecoveryEnabled);
		else if (Boolean.FALSE.equals(this.coordinatedControlZbrSolver))
			algo.setAdaptiveLccRecoveryEnabled(false);
		if (this.coupledVscPqControlEnabled != null)
			algo.setCoupledVscPqControlEnabled(this.coupledVscPqControlEnabled);
		if (this.vscCapabilityPriority != null)
			algo.setVscCapabilityPriority(this.vscCapabilityPriority);
		if (this.coordinatedControlEnableInnerTaps != null)
			algo.setFullNewtonTapControlEnabled(
					this.coordinatedControlEnableInnerTaps);
		if (this.tapChangeLimit != null)
			algo.setTapChangeLimit(this.tapChangeLimit);

		NrMethodConfig nrConfig = algo.getNrMethodConfig();
		// Configure either coordinate system before reconfiguring an existing solver.
		if (algo.getAclfNet().isPolarCoordinate() != polarCooridnate) {
			algo.getAclfNet().setPolarCoordinate(polarCooridnate);

			// re-configure the Nr solver with the updated config
			if (algo.getLfCalculator().getNrSolver() != null)
				algo.getLfCalculator().getNrSolver().reConfigSolver(nrConfig);
		}

		// Set tolerance and max iterations
		double tolPU = this.tolUnitType == UnitType.mVA? this.tolerance/baseMVA : this.tolerance;
		if (explicitlyConfigures("tolerance"))
			algo.setTolerance(tolPU);
		if (explicitlyConfigures("maxIterations"))
			algo.setMaxIterations(this.maxIterations);
		if (explicitlyConfigures("messageLoggingEnabled"))
			algo.setDetailedIterationLoggingEnabled(this.messageLoggingEnabled);
		if (explicitlyConfigures("autoSetZeroZBranch"))
			algo.getDataCheckConfig().setAutoSetZeroZBranch(this.autoSetZeroZBranch);
		if (explicitlyConfigures("turnOffIslandBus")) {
			algo.setTurnOffIslandBus(this.turnOffIslandBus);
			algo.getDataCheckConfig().setTurnOffIslandBus(this.turnOffIslandBus);
		}
		if (explicitlyConfigures("autoTurnLine2Xfr"))
			algo.getDataCheckConfig().setAutoTurnLine2Xfr(this.autoTurnLine2Xfr);
		if (explicitlyConfigures("busLoadLowVoltAdj"))
			algo.getAclfNet().getBusLoadLowVoltConfig().setApplyVoltAdjust(this.busLoadLowVoltAdj);
		if (explicitlyConfigures("vConstPMin"))
			algo.getAclfNet().getBusLoadLowVoltConfig().setVConstPMin(this.vConstPMin);
		if (explicitlyConfigures("vConstIMin"))
			algo.getAclfNet().getBusLoadLowVoltConfig().setVConstIMin(this.vConstIMin);
		// include adjustments/controls
		if (!appluAdjust || (explicitlyConfigures("includeAdjustments") && !this.includeAdjustments)) {
			algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		}
		else {
			if (explicitlyConfigures("includeAdjustments"))
				algo.getLfAdjAlgo().setApplyAdjustAlgo(true);
			if (explicitlyConfigures("activateAllAdjCtrl"))
				algo.getLfAdjAlgo().setActivateAllAdjust(this.activateAllAdjCtrl);
			if (explicitlyConfigures("applyLimitControl"))
				algo.getLfAdjAlgo().getLimitCtrlConfig().setAdjust(this.applyLimitControl);
			if (explicitlyConfigures("pvBusLimitControl"))
				algo.getLfAdjAlgo().getLimitCtrlConfig().setPvLimitControl(this.pvBusLimitControl);
			if (explicitlyConfigures("pqBusLimitControl"))
				algo.getLfAdjAlgo().getLimitCtrlConfig().setPqLimitControl(this.pqBusLimitControl);
			if (explicitlyConfigures("limitBackoffCheck"))
				algo.getLfAdjAlgo().getLimitCtrlConfig().setLimitBackoffCheck(this.limitBackoffCheck);
			if (explicitlyConfigures("checkGenQLimImmediate"))
				algo.getLfAdjAlgo().getLimitCtrlConfig().setCheckGenQLimitImmediate(this.checkGenQLimImmediate);
			if (explicitlyConfigures("applyVoltAdjust"))
				algo.getLfAdjAlgo().getVoltAdjConfig().setAdjust(this.applyVoltAdjust);
			if (explicitlyConfigures("applyDiscreteAdjust"))
				algo.getLfAdjAlgo().getVoltAdjConfig().setDiscreteAdjust(this.applyDiscreteAdjust);
			if (explicitlyConfigures("remoteQBusControl"))
				algo.getLfAdjAlgo().getVoltAdjConfig().setReQBusControl(this.remoteQBusControl);
			if (explicitlyConfigures("switchedShuntAdjust"))
				algo.getLfAdjAlgo().getVoltAdjConfig().setSwitchedShuntAdjust(this.switchedShuntAdjust);
			if (explicitlyConfigures("svcFactsAdjust"))
				algo.getLfAdjAlgo().getVoltAdjConfig().setSvcFactsAdjust(this.svcFactsAdjust);
			if (explicitlyConfigures("xfrTapControl"))
				algo.getLfAdjAlgo().getVoltAdjConfig().setXfrTapControl(this.xfrTapControl);
			if (explicitlyConfigures("hvdcTapControl"))
				algo.getLfAdjAlgo().getVoltAdjConfig().setHvdcTapControl(this.hvdcTapControl);
			if (explicitlyConfigures("applyPowerAdjust"))
				algo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(this.applyPowerAdjust);
			if (explicitlyConfigures("psXfrPControl"))
				algo.getLfAdjAlgo().getPowerAdjConfig().setPsXfrPControl(this.psXfrPControl);
			// Core 1.3.12 solver controls; null fields leave defaults untouched
			if (this.areaInterchangeControl != null)
				algo.getNetAdjAlgo().setAreaInterchangeControlEnabled(this.areaInterchangeControl);
			if (this.maxAreaInterchangeAdjustmentsPerIteration != null)
				algo.getNetAdjAlgo().setMaxAreaInterchangeAdjustmentsPerIteration(this.maxAreaInterchangeAdjustmentsPerIteration);
			if (this.maxAreaInterchangeAngleStepRad != null)
				algo.getNetAdjAlgo().setMaxAreaInterchangeAngleStep(this.maxAreaInterchangeAngleStepRad);
			if (this.maxAreaInterchangePowerStepPu != null)
				algo.getNetAdjAlgo().setMaxAreaInterchangePowerStep(this.maxAreaInterchangePowerStepPu);
			if (this.areaInterchangeAdjustmentFactor != null)
				algo.getNetAdjAlgo().setAreaInterchangeAdjustmentFactor(this.areaInterchangeAdjustmentFactor);
			if (this.maxPvLimitAdjustmentsPerIteration != null)
				algo.getLfAdjAlgo().setMaxPvLimitAdjustmentsPerIteration(this.maxPvLimitAdjustmentsPerIteration);

			// Adj/Ctrl Setting tab inputs to be processed
			if (explicitlyConfigures("limitCtrlStartPoint"))
				algo.getLfAdjAlgo().getLimitCtrlConfig().setStartPoint(this.limitCtrlStartPoint);
			if (explicitlyConfigures("limitCtrlTolearnceFactor"))
				algo.getLfAdjAlgo().getLimitCtrlConfig().setToleranceFactor(this.limitCtrlTolearnceFactor);
			if (explicitlyConfigures("limitCtrlApplyType"))
				algo.getLfAdjAlgo().getLimitCtrlConfig().setAdjustAppType(this.limitCtrlApplyType);
			// Mod 26-03: use absolute value for voltAdjTolerance
			if (explicitlyConfigures("voltAdjStartPoint"))
				algo.getLfAdjAlgo().getVoltAdjConfig().setStartPoint(this.voltAdjStartPoint);
			if (explicitlyConfigures("voltAdjTolearnce"))
				algo.getLfAdjAlgo().getVoltAdjConfig().setAdjTolerance(this.voltAdjTolearnce);
			if (explicitlyConfigures("dQ_dVThreshold"))
				algo.getLfAdjAlgo().getVoltAdjConfig().setDQ_dVThreshold(this.dQ_dVThreshold);
			if (explicitlyConfigures("voltAdjApplyType"))
				algo.getLfAdjAlgo().getVoltAdjConfig().setAdjustAppType(this.voltAdjApplyType);
			if (explicitlyConfigures("powerAdjStartPoint"))
				algo.getLfAdjAlgo().getPowerAdjConfig().setStartPoint(this.powerAdjStartPoint);
			if (explicitlyConfigures("powerAdjTolearnceFactor"))
				algo.getLfAdjAlgo().getPowerAdjConfig().setToleranceFactor(this.powerAdjTolearnceFactor);
			if (explicitlyConfigures("powerAdjApplyType"))
				algo.getLfAdjAlgo().getPowerAdjConfig().setAdjustAppType(this.powerAdjApplyType);
			if (explicitlyConfigures("pvLimitAccFactor"))
				algo.getLfAdjAlgo().getAccFactorConfig().setPvLimitAccFactor(this.pvLimitAccFactor);
			if (explicitlyConfigures("pqLimitAccFactor"))
				algo.getLfAdjAlgo().getAccFactorConfig().setPqLimitAccFactor(this.pqLimitAccFactor);
			if (explicitlyConfigures("reQBusAccFactor"))
				algo.getLfAdjAlgo().getAccFactorConfig().setReQBusAccFactor(this.reQBusAccFactor);
			if (explicitlyConfigures("svcAccFactor"))
				algo.getLfAdjAlgo().getAccFactorConfig().setSvcAdjustAccFactor(this.svcAccFactor);
			if (explicitlyConfigures("xfrTapAccFactor"))
				algo.getLfAdjAlgo().getAccFactorConfig().setXfrTapControlAccFactor(this.xfrTapAccFactor);
			if (explicitlyConfigures("psXfrPContrlAccFactor"))
				algo.getLfAdjAlgo().getAccFactorConfig().setPsXfrPControlAccFactor(this.psXfrPContrlAccFactor);

		}

		// Engine options shared by desktop, CLI and QA; null preserves Core/import state.
		if (this.initBusVoltage != null)
			algo.setInitBusVoltage(this.initBusVoltage);
		if (this.nrStepSizeOptimization != null)
			algo.getNrMethodConfig().setStepSizeOptimization(this.nrStepSizeOptimization);
		if (this.allowSwingBusWithoutActiveGenerator != null)
			algo.getDataCheckConfig().setAllowSwingBusWithoutActiveGenerator(this.allowSwingBusWithoutActiveGenerator);
		if (this.aclfNetModelType != null) {
			algo.getAclfNet().setAclfNetModelType(this.aclfNetModelType);
			if (this.aclfNetModelType != AclfNetModelType.ZBR_MODEL
					&& algo.getLfCalculator().getNrSolver().getClass() == ZbrNrSolver.class) {
				algo.getLfCalculator().setNrSolver(null);
			}
		}
		if (this.hvdcLfSwitchFactor != null)
			algo.setHvdcLfSwitchFactor(this.hvdcLfSwitchFactor);
		if (this.hvdcOuterAdjustmentFactor != null)
			algo.getLfCalculator().setHvdcOuterAdjustmentFactor(this.hvdcOuterAdjustmentFactor);
		if (this.hvdcOuterControlEnabled != null)
			algo.getLfCalculator().setHvdcOuterControlEnabled(this.hvdcOuterControlEnabled);
		if (this.maxHvdcOuterOscillationsBeforeLock != null)
			algo.getLfCalculator().setMaxHvdcOuterOscillationsBeforeLock(this.maxHvdcOuterOscillationsBeforeLock);
		if (this.maxAreaInterchangeTrialIterations != null)
			algo.getLfCalculator().setMaxAreaInterchangeTrialIterations(this.maxAreaInterchangeTrialIterations);
		if (this.remoteQControlMode != null)
			algo.getLfAdjAlgo().setRemoteQControlMode(this.remoteQControlMode);
		else if (Boolean.FALSE.equals(this.coordinatedControlZbrSolver))
			algo.getLfAdjAlgo().setRemoteQControlMode(RemoteQControlMode.OUTER_LOOP);
		if (this.maxRemoteQAdjustmentsPerIteration != null)
			algo.getLfAdjAlgo().setMaxRemoteQAdjustmentsPerIteration(this.maxRemoteQAdjustmentsPerIteration);
		if (this.maxSwitchedShuntAdjustmentsPerIteration != null)
			algo.getLfAdjAlgo().setMaxSwitchedShuntAdjustmentsPerIteration(this.maxSwitchedShuntAdjustmentsPerIteration);
		if (this.maxSvcAdjustmentsPerIteration != null)
			algo.getLfAdjAlgo().setMaxSvcAdjustmentsPerIteration(this.maxSvcAdjustmentsPerIteration);
		if (this.maxTapAdjustmentsPerIteration != null)
			algo.getLfAdjAlgo().setMaxTapAdjustmentsPerIteration(this.maxTapAdjustmentsPerIteration);
		if (this.maxPsXfrPAdjustmentsPerIteration != null)
			algo.getLfAdjAlgo().setMaxPsXfrPAdjustmentsPerIteration(this.maxPsXfrPAdjustmentsPerIteration);
		if (this.voltageAdjustmentThreshold != null)
			algo.getLfAdjAlgo().setVoltageAdjustmentThreshold(this.voltageAdjustmentThreshold);
		if (this.localContinuousSvcAsPv != null)
			algo.getLfAdjAlgo().setLocalContinuousSvcAsPv(this.localContinuousSvcAsPv);
		if (this.minimumLocalContinuousSvcPvSensitivity != null)
			algo.getLfAdjAlgo().setMinimumLocalContinuousSvcPvSensitivity(this.minimumLocalContinuousSvcPvSensitivity);
		if (this.minimumTapVoltageSensitivity != null)
			algo.getLfAdjAlgo().setMinimumTapVoltageSensitivity(this.minimumTapVoltageSensitivity);
		if (this.maxTapAndShuntAdjustmentIterations != null)
			algo.getLfAdjAlgo().setMaxTapAndShuntAdjustmentIterations(this.maxTapAndShuntAdjustmentIterations);
		if (this.maxTapAdjustmentIterations != null)
			algo.getLfAdjAlgo().setMaxTapAdjustmentIterations(this.maxTapAdjustmentIterations);
		if (this.maxSwitchedShuntAdjustmentIterations != null)
			algo.getLfAdjAlgo().setMaxSwitchedShuntAdjustmentIterations(this.maxSwitchedShuntAdjustmentIterations);
		if (this.requirePriorPvLimitViolation != null)
			algo.getLfAdjAlgo().setRequirePriorPvLimitViolation(this.requirePriorPvLimitViolation);
		if (this.minPvLimitStableIterations != null)
			algo.getLfAdjAlgo().setMinPvLimitStableIterations(this.minPvLimitStableIterations);
		if (this.minPvLimitControlIteration != null)
			algo.getLfAdjAlgo().setMinPvLimitControlIteration(this.minPvLimitControlIteration);
		if (this.maxPvLimitControlIteration != null)
			algo.getLfAdjAlgo().setMaxPvLimitControlIteration(this.maxPvLimitControlIteration);
		if (this.maxPvLimitRecoveryIterations != null)
			algo.getLfAdjAlgo().setMaxPvLimitRecoveryIterations(this.maxPvLimitRecoveryIterations);
		if (this.maxPvLimitBuses != null)
			algo.getLfAdjAlgo().setMaxPvLimitBuses(this.maxPvLimitBuses);
		if (this.minSavedPvLimitAngleMismatch != null)
			algo.getLfAdjAlgo().setMinSavedPvLimitAngleMismatch(this.minSavedPvLimitAngleMismatch);
		if (this.controlInitializationMode != null)
			algo.getLfAdjAlgo().getLimitCtrlConfig().setControlInitializationMode(this.controlInitializationMode);
		if (this.maxSavedSolutionQLimitSeeds != null)
			algo.getLfAdjAlgo().getLimitCtrlConfig().setMaxSavedSolutionQLimitSeeds(this.maxSavedSolutionQLimitSeeds);
		if (this.maxAdditionalSavedSolutionQLimitBuses != null)
			algo.getLfAdjAlgo().getLimitCtrlConfig().setMaxAdditionalSavedSolutionQLimitBuses(this.maxAdditionalSavedSolutionQLimitBuses);
		if (this.savedSolutionQLimitTolerance != null)
			algo.getLfAdjAlgo().getLimitCtrlConfig().setSavedSolutionQLimitTolerance(this.savedSolutionQLimitTolerance);
		if (this.savedSolutionVoltageEvidenceTolerance != null)
			algo.getLfAdjAlgo().getLimitCtrlConfig().setSavedSolutionVoltageEvidenceTolerance(this.savedSolutionVoltageEvidenceTolerance);
		if (this.highGainVoltageControlDampingThreshold != null
				&& this.highGainVoltageControlMaxAccelerationFactor != null)
			algo.getLfAdjAlgo().setHighGainVoltageControlDamping(
					this.highGainVoltageControlDampingThreshold,
					this.highGainVoltageControlMaxAccelerationFactor);

		// Numerical NR settings are independent of adjustment/control inclusion.
		// In particular, PSS/E applies DVLIM during normal full Newton solves.
		if (explicitlyConfigures("nonDivergent"))
			algo.setNonDivergent(this.nonDivergent);
		if (explicitlyConfigures("variableUpdateLimit"))
			algo.setVariableUpdateLimit(this.variableUpdateLimit);
		if (explicitlyConfigures("deltaVAngLimit"))
			algo.setDeltaVAngLimit(this.deltaVAngLimit);
		if (explicitlyConfigures("deltaVMagLimit"))
			algo.setDeltaVMagLimit(this.deltaVMagLimit);
		if (algo.isNonDivergent()) {
			if (algo.getLfMethod() == AclfMethodType.NR && explicitlyConfigures("optAlgo"))
					nrConfig.setOptAlgo(this.optAlgo);
			if (explicitlyConfigures("stopNoSolutionFound"))
				algo.setStopNoSolutionFound(this.stopNoSolutionFound);
			if (explicitlyConfigures("minScaleFactor"))
				algo.setMinScaleFactor(this.minScaleFactor);
		}
		if (algo.getLfMethod() == AclfMethodType.NR && algo.getLfCalculator().getNrSolver() != null)
			algo.getLfCalculator().getNrSolver().reConfigSolver(nrConfig);
		configureCoordinatedSolver(algo);
	}
}
