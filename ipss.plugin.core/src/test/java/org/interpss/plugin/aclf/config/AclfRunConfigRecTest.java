package org.interpss.plugin.aclf.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
}
