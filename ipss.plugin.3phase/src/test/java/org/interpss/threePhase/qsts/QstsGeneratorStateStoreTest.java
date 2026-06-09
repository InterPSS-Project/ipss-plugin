package org.interpss.threePhase.qsts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.AclfGen3Phase;
import com.interpss.core.threephase.Static3PGen;
import com.interpss.core.threephase.Static3PhaseFactory;

public class QstsGeneratorStateStoreTest {

	@Test
	void generatorBaseStateScalesAndRestoresGeneratorPower() {
		Static3PGen generator = Static3PhaseFactory.eINSTANCE.createStatic3PGen();
		generator.setId("pv1");
		generator.setCode(AclfGenCode.GEN_PQ);
		generator.setMvaBase(1.0);
		generator.setGen(new Complex(0.6, 0.2));
		generator.setPower3Phase(new Complex3x1(new Complex(0.2, 0.05),
				new Complex(0.2, 0.05), new Complex(0.2, 0.05)), UnitType.PU);
		QstsGeneratorBaseState state = new QstsGeneratorBaseState(generator);

		state.applyMultiplier(0.5, 0.25);

		assertEquals(0.3, generator.getGen().getReal(), 1.0e-12);
		assertEquals(0.0375, generator.getGen().getImaginary(), 1.0e-12);
		assertEquals(0.1, generator.getPower3Phase(UnitType.PU).a_0.getReal(), 1.0e-12);
		assertEquals(0.0125, generator.getPower3Phase(UnitType.PU).a_0.getImaginary(), 1.0e-12);

		state.restore();

		assertEquals(0.6, generator.getGen().getReal(), 1.0e-12);
		assertEquals(0.15, generator.getGen().getImaginary(), 1.0e-12);
		assertEquals(0.2, generator.getPower3Phase(UnitType.PU).b_1.getReal(), 1.0e-12);
		assertEquals(0.05, generator.getPower3Phase(UnitType.PU).b_1.getImaginary(), 1.0e-12);
	}

	@Test
	void generatorStateStoreRegistersAndRestoresAllGenerators() {
		Static3PGen generator = Static3PhaseFactory.eINSTANCE.createStatic3PGen();
		generator.setId("gen1");
		generator.setGen(new Complex(0.4, 0.1));
		QstsGeneratorStateStore store = new QstsGeneratorStateStore();
		store.register(generator);

		generator.setGen(new Complex(0.0, 0.0));
		store.restoreAll();

		assertEquals(1, store.size());
		assertEquals(0.4, generator.getGen().getReal(), 1.0e-12);
		assertEquals(0.1, generator.getGen().getImaginary(), 1.0e-12);
	}

	@Test
	void onePhaseGeneratorBaseStateScalesAndRestoresPhasePower() {
		TestPhaseGen generator = new TestPhaseGen("pv1", PhaseCode.B,
				new Complex3x1(Complex.ZERO, new Complex(0.2, 0.05), Complex.ZERO));
		QstsGeneratorStateStore store = new QstsGeneratorStateStore();
		QstsGeneratorBaseState state = store.register(generator);

		state.applyMultiplier(0.5, 0.25);

		assertEquals(0.1, generator.getPower1Phase(PhaseCode.B, UnitType.PU).getReal(), 1.0e-12);
		assertEquals(0.0125, generator.getPower1Phase(PhaseCode.B, UnitType.PU).getImaginary(), 1.0e-12);
		assertEquals(0.2, state.getOnePhasePower().getReal(), 1.0e-12);

		store.restoreAll();

		assertEquals(0.2, generator.getPower1Phase(PhaseCode.B, UnitType.PU).getReal(), 1.0e-12);
		assertEquals(0.05, generator.getPower1Phase(PhaseCode.B, UnitType.PU).getImaginary(), 1.0e-12);
	}

	private static class TestPhaseGen implements AclfGen3Phase {
		private final String id;
		private final PhaseCode phaseCode;
		private Complex3x1 power;

		TestPhaseGen(String id, PhaseCode phaseCode, Complex3x1 power) {
			this.id = id;
			this.phaseCode = phaseCode;
			this.power = power;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public double getMvaBase() {
			return 1.0;
		}

		@Override
		public PhaseCode getPhaseCode() {
			return phaseCode;
		}

		@Override
		public Complex3x1 getPower3Phase(UnitType unit) {
			return power;
		}

		@Override
		public void setPower3Phase(Complex3x1 power, UnitType unit) {
			this.power = power;
		}
	}
}
