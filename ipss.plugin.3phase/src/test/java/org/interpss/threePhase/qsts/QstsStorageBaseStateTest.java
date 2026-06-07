package org.interpss.threePhase.qsts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.jupiter.api.Test;

import com.interpss.core.threephase.Static3PGen;
import com.interpss.core.threephase.Static3PhaseFactory;

public class QstsStorageBaseStateTest {
	@Test
	void dischargingIsPositiveGeneratorInjectionAndRespectsReserveAndEfficiency() {
		Static3PGen generator = storageGenerator();
		QstsStorageBaseState state = new QstsStorageBaseState(generator, 1000.0,
				500.0, 1000.0, 300.0, 200.0, 0.95, 0.90);

		double actualKw = state.applyScheduledPower(500.0, 1.0);

		assertEquals(90.0, actualKw, 1.0e-12);
		assertEquals(200.0, state.getStoredKwh(), 1.0e-12);
		assertEquals(0.09, total(generator).getReal(), 1.0e-12);
	}

	@Test
	void chargingIsNegativeGeneratorInjectionAndRespectsCapacityAndEfficiency() {
		Static3PGen generator = storageGenerator();
		QstsStorageBaseState state = new QstsStorageBaseState(generator, 1000.0,
				500.0, 1000.0, 950.0, 100.0, 0.80, 0.90);

		double actualKw = state.applyScheduledPower(-500.0, 1.0);

		assertEquals(-62.5, actualKw, 1.0e-12);
		assertEquals(1000.0, state.getStoredKwh(), 1.0e-12);
		assertEquals(-0.0625, total(generator).getReal(), 1.0e-12);
	}

	private static Static3PGen storageGenerator() {
		Static3PGen generator = Static3PhaseFactory.eINSTANCE.createStatic3PGen();
		generator.setId("batt1");
		generator.setMvaBase(1.0);
		generator.setPower3Phase(new Complex3x1(new Complex(0.0, 0.0),
				new Complex(0.0, 0.0), new Complex(0.0, 0.0)), UnitType.PU);
		return generator;
	}

	private static Complex total(Static3PGen generator) {
		Complex3x1 power = generator.getPower3Phase(UnitType.PU);
		return power.a_0.add(power.b_1).add(power.c_2);
	}
}
