package org.interpss.plugin.aclf.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.algo.LoadflowAlgorithm;

class AclfRunConfigRecTest {

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
