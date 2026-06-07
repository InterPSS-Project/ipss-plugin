package org.interpss.threePhase.qsts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.Static1Gen;
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
		Static1Gen generator = new Static1Gen("pv1", PhaseCode.B, new Complex(0.2, 0.05));
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
}
