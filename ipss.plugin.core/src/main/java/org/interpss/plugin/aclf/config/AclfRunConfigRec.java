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
import com.interpss.core.algo.NrMethodConfig;
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
 * Nullable advanced fields preserve an imported/current value when absent,
 * while primitive fields define schema defaults.</p>
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
	
	public boolean includeAdjustments = false;
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

	// Apply the imported PSS/E saved-solution SOLVER activity flags
	// (ACTAPS/AREAIN/PHSHFT/DCTAPS/SWSHNT/NONDIV) when the network carries
	// PsseLoadflowSolutionSettings. The desktop run service honors this flag.
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
	public double dQ_dVThreshold = 1.0;
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
		return (T) parseJson(json, false, false);
	}

	public static AclfRunConfigRec fromJson(String json) {
		return parseJson(json, true, true);
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
		merged.explicitJsonFields = new LinkedHashSet<>(overlay.explicitJsonFields);
		merged.includeAdjustments = true;
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
			boolean trackExplicitFields,
			boolean forceIncludeAdjustments) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		validateAclfJsonRoot(root);
		AclfRunConfigRec config = gson().fromJson(json, AclfRunConfigRec.class);
		config.validateSchemaVersion();
		if (trackExplicitFields) {
			config.explicitJsonFields = new LinkedHashSet<>(root.keySet());
		}
		if (forceIncludeAdjustments) {
			config.includeAdjustments = true;
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
	
	public void saveAclfRunConfig(String configFilename) {
		String json = this.toString();
		FileUtil.writeText2File(configFilename, json);
	}
	
	public void configAclfRun(LoadflowAlgorithm algo, boolean polarCooridnate, boolean appluAdjust, boolean psseConfig) {
		double baseMVA = algo.getAclfNet().getBaseMva();
		
        algo.setLfMethod(this.lfMethod);
        
        NrMethodConfig nrConfig = algo.getNrMethodConfig();
        // the default AclfNet coordinate is polar coordinate
        if (!polarCooridnate) {
        	// we need to set the AclfNet coordinate to rectangular coordinate before reconfiguring the NR solver
			algo.getAclfNet().setPolarCoordinate(false);
			
			// re-configure the Nr solver with the updated config
			algo.getLfCalculator().getNrSolver().reConfigSolver(nrConfig);
		}

        // Set tolerance and max iterations
        double tolPU = this.tolUnitType == UnitType.mVA? this.tolerance/baseMVA : this.tolerance;
        algo.setTolerance(tolPU);
        algo.setMaxIterations(this.maxIterations);
        algo.setDetailedIterationLoggingEnabled(this.messageLoggingEnabled);
        
        algo.getDataCheckConfig().setAutoSetZeroZBranch(this.autoSetZeroZBranch);
        algo.getDataCheckConfig().setTurnOffIslandBus(this.turnOffIslandBus);
    	algo.getDataCheckConfig().setAutoTurnLine2Xfr(this.autoTurnLine2Xfr);
    	
    	algo.getAclfNet().getBusLoadLowVoltConfig().setApplyVoltAdjust(this.busLoadLowVoltAdj);
    	algo.getAclfNet().getBusLoadLowVoltConfig().setVConstPMin(this.vConstPMin);
		algo.getAclfNet().getBusLoadLowVoltConfig().setVConstIMin(this.vConstIMin);
        
        // include adjustments/controls
        if (!appluAdjust) {
    		// not need to turn off the power adjustment, since the turn of Adjust will turn off the power adjustment
            //algo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(false);
            algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
        } 
        else {
        	algo.getLfAdjAlgo().setApplyAdjustAlgo(true);
        	algo.getLfAdjAlgo().setActivateAllAdjust(this.activateAllAdjCtrl);
        	
        	algo.getLfAdjAlgo().getLimitCtrlConfig().setAdjust(this.applyLimitControl);
        	algo.getLfAdjAlgo().getLimitCtrlConfig().setPvLimitControl(this.pvBusLimitControl);
        	algo.getLfAdjAlgo().getLimitCtrlConfig().setPqLimitControl(this.pqBusLimitControl);
        	algo.getLfAdjAlgo().getLimitCtrlConfig().setLimitBackoffCheck(this.limitBackoffCheck);
        	algo.getLfAdjAlgo().getLimitCtrlConfig().setCheckGenQLimitImmediate(this.checkGenQLimImmediate);
        	
        	algo.getLfAdjAlgo().getVoltAdjConfig().setAdjust(this.applyVoltAdjust);
        	algo.getLfAdjAlgo().getVoltAdjConfig().setDiscreteAdjust(this.applyDiscreteAdjust);
			algo.getLfAdjAlgo().getVoltAdjConfig().setReQBusControl(this.remoteQBusControl);	
			algo.getLfAdjAlgo().getVoltAdjConfig().setSwitchedShuntAdjust(this.switchedShuntAdjust);
			algo.getLfAdjAlgo().getVoltAdjConfig().setSvcFactsAdjust(this.svcFactsAdjust);
			algo.getLfAdjAlgo().getVoltAdjConfig().setXfrTapControl(this.xfrTapControl);
			algo.getLfAdjAlgo().getVoltAdjConfig().setHvdcTapControl(this.hvdcTapControl);

        	algo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(this.applyPowerAdjust);
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
        	algo.getLfAdjAlgo().getLimitCtrlConfig().setStartPoint(this.limitCtrlStartPoint);
        	algo.getLfAdjAlgo().getLimitCtrlConfig().setToleranceFactor(this.limitCtrlTolearnceFactor);
        	algo.getLfAdjAlgo().getLimitCtrlConfig().setAdjustAppType(this.limitCtrlApplyType);

        	// Mod 26-03: use absolute value for voltAdjTolerance
        	algo.getLfAdjAlgo().getVoltAdjConfig().setStartPoint(this.voltAdjStartPoint);
        	algo.getLfAdjAlgo().getVoltAdjConfig().setAdjTolerance(this.voltAdjTolearnce);
        	algo.getLfAdjAlgo().getVoltAdjConfig().setDQ_dVThreshold(this.dQ_dVThreshold);
        	algo.getLfAdjAlgo().getVoltAdjConfig().setAdjustAppType(this.voltAdjApplyType);

        	algo.getLfAdjAlgo().getPowerAdjConfig().setStartPoint(this.powerAdjStartPoint);
        	algo.getLfAdjAlgo().getPowerAdjConfig().setToleranceFactor(this.powerAdjTolearnceFactor);
        	algo.getLfAdjAlgo().getPowerAdjConfig().setAdjustAppType(this.powerAdjApplyType);
        	
        	algo.getLfAdjAlgo().getAccFactorConfig().setPvLimitAccFactor(this.pvLimitAccFactor);
			algo.getLfAdjAlgo().getAccFactorConfig().setPqLimitAccFactor(this.pqLimitAccFactor);
			algo.getLfAdjAlgo().getAccFactorConfig().setReQBusAccFactor(this.reQBusAccFactor);
			algo.getLfAdjAlgo().getAccFactorConfig().setSvcAdjustAccFactor(this.svcAccFactor);
			algo.getLfAdjAlgo().getAccFactorConfig().setXfrTapControlAccFactor(this.xfrTapAccFactor);
			algo.getLfAdjAlgo().getAccFactorConfig().setPsXfrPControlAccFactor(this.psXfrPContrlAccFactor);
        
            // PSS/E setting tab inputs to be processed
            if (psseConfig) {
				// PSS/E setting tab inputs to be processed
            	algo.getLfAdjAlgo().initialize(lfAdjAlgo -> {
            	  	// TODO do PSS/E specific configuration before running the Loadflow
            	});
			}
        }

		// Numerical NR settings are independent of adjustment/control inclusion.
		// In particular, PSS/E applies DVLIM during normal full Newton solves.
		algo.setNonDivergent(this.nonDivergent);
		algo.setVariableUpdateLimit(this.variableUpdateLimit);
		algo.setDeltaVAngLimit(this.deltaVAngLimit);
		algo.setDeltaVMagLimit(this.deltaVMagLimit);
		if (this.nonDivergent) {
			if (this.lfMethod == AclfMethodType.NR)
				nrConfig.setOptAlgo(this.optAlgo);
			algo.setStopNoSolutionFound(this.stopNoSolutionFound);
			algo.setMinScaleFactor(this.minScaleFactor);
		}
		if (this.lfMethod == AclfMethodType.NR)
			algo.getLfCalculator().getNrSolver().reConfigSolver(nrConfig);
	}
}
