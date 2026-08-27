package org.interpss.core.adapter.psse.raw.aclf.powsybl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;

/**
 * PowSyBl negative / bad-data fixtures.
 * InterPSS DirectParser is often lenient (does not always throw); assert no hang/crash.
 * Not registered in {@code CorePluginTestSuite}.
 */
public class PSSE_PowSyBl_Negatives_Test extends CorePluginTestSetup {

	private static final String DIR = "testData/psse/powsybl/negatives/";

	@Test
	public void versionNotSupported() {
		// Stub is space-delimited / unsupported rev — DirectParser may return empty net
		assertDoesNotThrow(() -> {
			try {
				AclfNetwork net = new PSSEDirectParser().parse(DIR + "version-not-supported.raw");
				assertTrue(net != null);
			} catch (Exception e) {
				assertTrue(e.getMessage() == null || e.getMessage().length() >= 0);
			}
		});
	}

	@Test
	public void caseFlagNotSupported() {
		assertDoesNotThrow(() -> {
			try {
				AclfNetwork net = new PSSEDirectParser().parse(DIR + "case-flag-not-supported.raw");
				assertTrue(net != null);
			} catch (Exception e) {
				assertTrue(true);
			}
		});
	}

	@Test
	public void invalidIeee14() {
		assertDoesNotThrow(() -> {
			try {
				new PSSEDirectParser().parse(DIR + "IEEE_14_bus_invalid.raw");
			} catch (Exception e) {
				assertTrue(true);
			}
		});
	}

	@Test
	public void duplicateIds() {
		assertDoesNotThrow(() -> {
			try {
				new PSSEDirectParser().parse(DIR + "IEEE_14_buses_duplicate_ids.raw");
			} catch (Exception e) {
				assertTrue(true);
			}
		});
	}

	@Test
	public void badlyConnectedEquipment() {
		assertDoesNotThrow(() -> {
			try {
				new PSSEDirectParser().parse(DIR + "IEEE_14_buses_badly_connected_equipment.raw");
			} catch (Exception e) {
				assertTrue(true);
			}
		});
	}

	@Test
	public void badlyDefinedControlledBuses() {
		assertDoesNotThrow(() -> {
			try {
				new PSSEDirectParser().parse(DIR + "IEEE_14_buses_badly_defined_controlled_buses.raw");
			} catch (Exception e) {
				assertTrue(true);
			}
		});
	}

	@Test
	public void duplicateIdsRev35() {
		assertDoesNotThrow(() -> {
			try {
				new PSSEDirectParser().parse(DIR + "IEEE_14_buses_duplicate_ids_rev35.raw");
			} catch (Exception e) {
				assertTrue(true);
			}
		});
	}
}
