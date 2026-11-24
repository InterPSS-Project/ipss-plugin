package org.interpss.plugin.aclf.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.algo.config.ControlInitializationMode;
import com.interpss.core.algo.config.RemoteQControlMode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.LoadflowAlgorithm.VscCapabilityPriority;

class AclfRunConfigRecTest {
    @Test
    void omittedSensitivityCutoffUsesTheCoreDefault() {
        assertEquals(.1, new AclfRunConfigRec().dQ_dVThreshold, 0.);
        for (String json : new String[] {"{}", "{\"lfMethod\":\"NR\"}"}) {
            var config = AclfRunConfigRec.fromJson(json);
            assertEquals(.1, config.dQ_dVThreshold, 0.);
            var algorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(CoreObjectFactory.createAclfNetwork());
            assertEquals(.1, algorithm.getLfAdjAlgo().getVoltAdjConfig().getDQ_dVThreshold(), 0.);
            config.configAclfRun(algorithm, true, true, false);
            assertEquals(.1, algorithm.getLfAdjAlgo().getVoltAdjConfig().getDQ_dVThreshold(), 0.);
        }
    }

    @Test
    void explicitSensitivityCutoffsRoundTripAndApplyIncludingTheLegacyValue() {
        for (double value : new double[] {0., .1, 1.}) {
            var config = AclfRunConfigRec.fromJson("{\"dQ_dVThreshold\":" + value + "}");
            var parsed = AclfRunConfigRec.fromJson(config.toString());
            assertEquals(value, parsed.dQ_dVThreshold, 0.);
            var algorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(CoreObjectFactory.createAclfNetwork());
            parsed.configAclfRun(algorithm, true, true, false);
            assertEquals(value, algorithm.getLfAdjAlgo().getVoltAdjConfig().getDQ_dVThreshold(), 0.);
        }
    }

    @Test
    void missingOverlayFieldPreservesAnExplicitLegacySensitivityCutoff() {
        var defaults = new AclfRunConfigRec();
        defaults.dQ_dVThreshold = 1.;
        assertEquals(1., AclfRunConfigRec.overlayJson(defaults, "{}").dQ_dVThreshold, 0.);
    }

	@Test
	void islandTurnOffSettingAppliesToTopologyInitializationAndDataCheck() {
		var algorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(CoreObjectFactory.createAclfNetwork());
		algorithm.setTurnOffIslandBus(false);
		algorithm.getDataCheckConfig().setTurnOffIslandBus(true);
		AclfRunConfigRec.fromJson("{}").applyTo(algorithm);
		assertFalse(algorithm.isTurnOffIslandBus());
		assertTrue(algorithm.getDataCheckConfig().isTurnOffIslandBus());

		AclfRunConfigRec.fromJson("{\"turnOffIslandBus\":true}").applyTo(algorithm);
		assertTrue(algorithm.isTurnOffIslandBus());
		assertTrue(algorithm.getDataCheckConfig().isTurnOffIslandBus());

		AclfRunConfigRec.fromJson("{\"turnOffIslandBus\":false}").applyTo(algorithm);
		assertFalse(algorithm.isTurnOffIslandBus());
		assertFalse(algorithm.getDataCheckConfig().isTurnOffIslandBus());
	}

	@Test
	void schemaVersionDefaultsToOneAndRejectsUnsupportedVersions() {
		AclfRunConfigRec config = new AclfRunConfigRec();
		assertEquals(AclfRunConfigRec.CURRENT_SCHEMA_VERSION, config.schemaVersion);
		assertTrue(config.toString().contains("\"schemaVersion\": 1"));

		AclfRunConfigRec legacy = new AclfRunConfigRec().fromString("{\"lfMethod\":\"NR\"}");
		assertEquals(AclfRunConfigRec.CURRENT_SCHEMA_VERSION, legacy.schemaVersion);

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> new AclfRunConfigRec().fromString("{\"schemaVersion\":2}"));
		assertTrue(error.getMessage().contains("schema version 2"));
	}

	@Test
	void configJsonRejectsRemovedAclfFields() {
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> AclfRunConfigRec.fromJson("{\"areaInterchangeControlEnabled\":false}"))
						.getMessage().contains("areaInterchangeControl"));
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> AclfRunConfigRec.fromJson("{\"removeAreaInterchangeControls\":true}"))
						.getMessage().contains("areaInterchangeControl=false"));
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> AclfRunConfigRec.fromJson("{\"halCoordinatedControlProfile\":true}"))
						.getMessage().contains("ACLF control fields directly"));
	}

	@Test
	void messageLoggingEnabledDefaultsFalseForExistingConfigs() {
		AclfRunConfigRec config = new AclfRunConfigRec().fromString("{\"lfMethod\":\"NR\"}");

		assertFalse(config.messageLoggingEnabled);
	}

	@Test
	void messageLoggingEnabledRoundTripsThroughJson() {
		AclfRunConfigRec config = new AclfRunConfigRec();
		config.messageLoggingEnabled = true;

		AclfRunConfigRec parsed = new AclfRunConfigRec().fromString(config.toString());

		assertTrue(parsed.messageLoggingEnabled);
	}

	@Test
	void reducedLccCouplingIsOptInAndRoundTripsThroughJson() {
		AclfRunConfigRec existing =
				new AclfRunConfigRec().fromString("{\"lfMethod\":\"NR\"}");
		assertNull(existing.reducedLccCouplingEnabled);

		AclfRunConfigRec config = new AclfRunConfigRec();
		config.reducedLccCouplingEnabled = true;
		AclfRunConfigRec parsed = new AclfRunConfigRec().fromString(config.toString());

		assertTrue(parsed.reducedLccCouplingEnabled);
	}

	@Test
	void advancedAclfControlsParseInSharedConfigModel() {
		AclfRunConfigRec config = AclfRunConfigRec.fromJson("""
				{
				  "schemaVersion": 1,
				  "coordinatedControlZbrSolver": true,
				  "remoteQControlMode": "INNER_PQV",
				  "maxRemoteQAdjustmentsPerIteration": 100,
				  "controlInitializationMode": "SAVED_SOLUTION_REPLAY",
				  "localContinuousSvcAsPv": true,
				  "hvdcOuterControlEnabled": true,
				  "maxSwitchedShuntAdjustmentIterations": 40,
				  "zeroZBranchProcessingMode": "CONSOLIDATE"
				}
				""");

		assertTrue(config.wasParsedFromJson());
		assertTrue(config.explicitlyConfigures("remoteQControlMode"));
		assertFalse(config.explicitlyConfigures("tolerance"));
		assertTrue(config.coordinatedControlZbrSolver);
		assertEquals(RemoteQControlMode.INNER_PQV, config.remoteQControlMode);
		assertEquals(100, config.maxRemoteQAdjustmentsPerIteration);
		assertEquals(ControlInitializationMode.SAVED_SOLUTION_REPLAY,
				config.controlInitializationMode);
		assertTrue(config.localContinuousSvcAsPv);
		assertTrue(config.hvdcOuterControlEnabled);
		assertEquals(40, config.maxSwitchedShuntAdjustmentIterations);
		assertEquals(ZeroZBranchProcessingMode.CONSOLIDATE,
				config.zeroZBranchProcessingMode);
	}

	@Test
	void coordinatedControlExperimentsParseAndApplyFromSharedJson() {
		AclfRunConfigRec config = AclfRunConfigRec.fromJson("""
				{
				  "schemaVersion": 1,
				  "coordinatedControlEnableInnerTaps": true,
				  "tapChangeLimit": 0.0125,
				  "coordinatedPqControlEnabled": true,
				  "coupledLccPqControlEnabled": true,
				  "coupledVscPqControlEnabled": true,
				  "vscCapabilityPriority": "VOLTAGE_TARGET"
				}
				""");
		LoadflowAlgorithm algorithm = LoadflowAlgoObjectFactory
				.createLoadflowAlgorithm(CoreObjectFactory.createAclfNetwork());

		config.configAclfRun(algorithm, true, false, false);

		assertTrue(config.explicitlyConfigures(
				"coupledVscPqControlEnabled"));
		assertTrue(algorithm.isFullNewtonTapControlEnabled());
		assertEquals(0.0125, algorithm.getTapChangeLimit(), 1.0e-12);
		assertTrue(algorithm.isCoordinatedPqControlEnabled());
		assertTrue(algorithm.isCoupledLccPqControlEnabled());
		assertTrue(algorithm.isCoupledVscPqControlEnabled());
		assertEquals(VscCapabilityPriority.VOLTAGE_TARGET,
				algorithm.getVscCapabilityPriority());
	}

	@Test
	void aclfJsonOverlayKeepsCaseDefaultsForMissingFields() {
		AclfRunConfigRec caseDefaults = new AclfRunConfigRec();
		caseDefaults.tolerance = 0.17;
		caseDefaults.maxIterations = 47;
		caseDefaults.xfrTapControl = true;

		AclfRunConfigRec merged = AclfRunConfigRec.overlayJson(caseDefaults, """
				{
				  "schemaVersion": 1,
				  "maxIterations": 12,
				  "xfrTapControl": false
				}
				""");

		assertEquals(12, merged.maxIterations);
		assertFalse(merged.xfrTapControl);
		assertEquals(0.17, merged.tolerance, 1.0e-12);
		assertTrue(merged.includeAdjustments);
	}

	@Test
	void aclfJsonPreservesAdjustmentMasterSetting() {
		assertTrue(AclfRunConfigRec.fromJson("{}").includeAdjustments);
		assertFalse(AclfRunConfigRec.fromJson(
				"{\"includeAdjustments\":false}").includeAdjustments);
		assertTrue(AclfRunConfigRec.fromJson(
				"{\"includeAdjustments\":true}").includeAdjustments);
	}

	@Test
	void normalNrAppliesVariableUpdateLimitsWithoutAdjustmentControls() {
		AclfRunConfigRec config = new AclfRunConfigRec();
		config.nonDivergent = false;
		config.variableUpdateLimit = true;
		config.deltaVMagLimit = 0.42;
		config.deltaVAngLimit = 0.73;
		LoadflowAlgorithm algorithm = LoadflowAlgoObjectFactory
				.createLoadflowAlgorithm(CoreObjectFactory.createAclfNetwork());

		config.configAclfRun(algorithm, true, false, false);

		assertFalse(algorithm.isNonDivergent());
		assertTrue(algorithm.isVariableUpdateLimit());
		assertEquals(0.42, algorithm.getDeltaVMagLimit(), 1.0e-12);
		assertEquals(0.73, algorithm.getDeltaVAngLimit(), 1.0e-12);
	}
}
