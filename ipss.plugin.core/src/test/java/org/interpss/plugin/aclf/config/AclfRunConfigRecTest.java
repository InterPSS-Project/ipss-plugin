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

class AclfRunConfigRecTest {
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
