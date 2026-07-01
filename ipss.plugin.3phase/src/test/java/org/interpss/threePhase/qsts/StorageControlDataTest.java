package org.interpss.threePhase.qsts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class StorageControlDataTest {
	@Test
	void storesGenericDispatchConfigurationWithoutOpenDssTypes() {
		StorageControlData control = new StorageControlData("ctrl1", "batt1",
				StorageControlData.DispatchMode.PEAK_SHAVE,
				StorageControlData.ChargeMode.LOW_LOAD, "line.main", 2,
				5000.0, 1500.0, 200.0, 100.0, 3000.0, 1200.0, 250.0, true);

		assertEquals("ctrl1", control.getId());
		assertEquals("batt1", control.getStorageId());
		assertEquals(StorageControlData.DispatchMode.PEAK_SHAVE, control.getDispatchMode());
		assertEquals(StorageControlData.ChargeMode.LOW_LOAD, control.getChargeMode());
		assertEquals("line.main", control.getMonitoredElementId());
		assertEquals(2, control.getMonitoredTerminal());
		assertEquals(5000.0, control.getDischargeTargetKw(), 1.0e-12);
		assertEquals(1500.0, control.getChargeTargetKw(), 1.0e-12);
		assertEquals(200.0, control.getDischargeDeadbandKw(), 1.0e-12);
		assertEquals(100.0, control.getChargeDeadbandKw(), 1.0e-12);
		assertEquals(3000.0, control.getMaxDischargePowerKw(), 1.0e-12);
		assertEquals(1200.0, control.getMaxChargePowerKw(), 1.0e-12);
		assertEquals(250.0, control.getReserveKwh(), 1.0e-12);
		assertTrue(control.isEnabled());
	}

	@Test
	void defaultsAndNormalizesOptionalLimits() {
		StorageControlData control = new StorageControlData(null, null, null, null,
				null, -1, 0.0, 0.0, -200.0, -100.0, Double.NaN, -1200.0,
				-250.0, false);

		assertEquals("", control.getId());
		assertEquals("", control.getStorageId());
		assertEquals(StorageControlData.DispatchMode.FOLLOW, control.getDispatchMode());
		assertEquals(StorageControlData.ChargeMode.OFF, control.getChargeMode());
		assertEquals("", control.getMonitoredElementId());
		assertEquals(1, control.getMonitoredTerminal());
		assertEquals(0.0, control.getDischargeDeadbandKw(), 1.0e-12);
		assertEquals(0.0, control.getChargeDeadbandKw(), 1.0e-12);
		assertTrue(Double.isNaN(control.getMaxDischargePowerKw()));
		assertEquals(0.0, control.getMaxChargePowerKw(), 1.0e-12);
		assertEquals(0.0, control.getReserveKwh(), 1.0e-12);
		assertFalse(control.isEnabled());
	}
}
