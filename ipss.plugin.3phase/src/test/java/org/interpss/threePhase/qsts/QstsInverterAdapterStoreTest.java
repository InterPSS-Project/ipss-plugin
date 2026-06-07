package org.interpss.threePhase.qsts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.powerflow.control.InverterControlData;
import org.interpss.threePhase.powerflow.control.InverterControlModel.InverterControlResult;
import org.junit.jupiter.api.Test;

import com.interpss.core.threephase.Static3PGen;
import com.interpss.core.threephase.Static3PhaseFactory;

public class QstsInverterAdapterStoreTest {
	@Test
	void storeIndexesConcreteAdapterByGeneratorId() {
		Static3PGen generator = Static3PhaseFactory.eINSTANCE.createStatic3PGen();
		generator.setId("PV1");
		generator.setPower3Phase(new Complex3x1(new Complex(0.01, 0.0),
				new Complex(0.01, 0.0), new Complex(0.01, 0.0)), UnitType.PU);
		InverterGenAdapter adapter = new InverterGenAdapter(generator);
		QstsInverterAdapterStore store = new QstsInverterAdapterStore();

		store.register(adapter);

		assertEquals(1, store.size());
		assertSame(adapter, store.get("pv1"));
		assertSame(generator, store.get("PV1").getGenerator());
	}

	@Test
	void adapterResolvesVoltVarCurveFromTerminalVoltageWithoutExtendingPhaseGen() {
		Static3PGen generator = generator("pv1", 0.1, 0.0);
		InverterGenAdapter adapter = new InverterGenAdapter(generator)
				.addCurve(new QstsControlCurve("vv1", new double[] {0.95, 1.05},
						new double[] {1000.0, -1000.0}))
				.setTerminalVoltagePu(1.0);
		InverterControlData control = new InverterControlData("inv1", "pv1",
				InverterControlData.ControlMode.VOLTVAR, "vv1", 20000.0,
				-5000.0, 5000.0, 0.0, true);

		InverterControlResult result = adapter.apply(control,
				new QstsStepContext(0, 0, 0.0, QstsMode.DAILY, 1.0, 1.0, QstsControlMode.STATIC),
				100000.0);

		assertTrue(result.isApplied());
		assertEquals(0.0, result.getReactivePowerKvar(), 1.0e-12);
		assertEquals(0.0, total(generator).getImaginary(), 1.0e-12);
	}

	@Test
	void adapterResolvesWattVarCurveFromCurrentActivePower() {
		Static3PGen generator = generator("pv1", 0.1, 0.0);
		InverterGenAdapter adapter = new InverterGenAdapter(generator)
				.addCurve(new QstsControlCurve("wvar1", new double[] {0.0, 10000.0},
						new double[] {0.0, 2000.0}));
		InverterControlData control = new InverterControlData("inv1", "pv1",
				InverterControlData.ControlMode.WATTVAR, "wvar1", 20000.0,
				-5000.0, 5000.0, 0.0, true);

		InverterControlResult result = adapter.apply(control,
				new QstsStepContext(0, 0, 0.0, QstsMode.DAILY, 1.0, 1.0, QstsControlMode.STATIC),
				100000.0);

		assertTrue(result.isApplied());
		assertEquals(2000.0, result.getReactivePowerKvar(), 1.0e-12);
		assertEquals(0.02, total(generator).getImaginary(), 1.0e-12);
	}

	@Test
	void adapterUsesCapabilityDataForRatedKvaAndReactiveLimits() {
		Static3PGen generator = generator("pv1", 0.1, 0.0);
		InverterGenAdapter adapter = new InverterGenAdapter(generator)
				.setCapabilityData(new InverterCapabilityData(12000.0, Double.NaN,
						-1000.0, 1000.0, 0.0, 0.0, true));
		InverterControlData control = new InverterControlData("inv1", "pv1",
				InverterControlData.ControlMode.VOLTVAR, "", 0.0, Double.NaN,
				Double.NaN, 0.0, Double.NaN, 3000.0, Double.NaN, true);

		InverterControlResult result = adapter.apply(control,
				new QstsStepContext(0, 0, 0.0, QstsMode.DAILY, 1.0, 1.0, QstsControlMode.STATIC),
				100000.0);

		assertTrue(result.isApplied());
		assertTrue(result.isLimited());
		assertEquals(1000.0, result.getReactivePowerKvar(), 1.0e-12);
		assertEquals(0.01, total(generator).getImaginary(), 1.0e-12);
	}

	@Test
	void adapterCapsVoltWattTargetByAvailableActivePower() {
		Static3PGen generator = generator("pv1", 0.0, 0.0);
		InverterGenAdapter adapter = new InverterGenAdapter(generator)
				.setCapabilityData(new InverterCapabilityData(10000.0, 3000.0,
						Double.NaN, Double.NaN, 0.0, 0.0, true));
		InverterControlData control = new InverterControlData("inv1", "pv1",
				InverterControlData.ControlMode.VOLTWATT, "", 0.0, Double.NaN,
				Double.NaN, 0.0, 8000.0, Double.NaN, Double.NaN, true);

		InverterControlResult result = adapter.apply(control,
				new QstsStepContext(0, 0, 0.0, QstsMode.DAILY, 1.0, 1.0, QstsControlMode.STATIC),
				100000.0);

		assertTrue(result.isApplied());
		assertEquals(3000.0, result.getActivePowerKw(), 1.0e-12);
		assertEquals(0.03, total(generator).getReal(), 1.0e-12);
	}

	@Test
	void adapterHonorsCapabilityEnableAndCutInState() {
		Static3PGen disabledGenerator = generator("pv-disabled", 0.1, 0.0);
		InverterGenAdapter disabledAdapter = new InverterGenAdapter(disabledGenerator)
				.setCapabilityData(new InverterCapabilityData(12000.0, Double.NaN,
						Double.NaN, Double.NaN, 0.0, 0.0, false));
		InverterControlData reactiveControl = new InverterControlData("inv1", "pv-disabled",
				InverterControlData.ControlMode.VOLTVAR, "", 0.0, Double.NaN,
				Double.NaN, 0.0, Double.NaN, 1000.0, Double.NaN, true);

		InverterControlResult disabledResult = disabledAdapter.apply(reactiveControl,
				new QstsStepContext(0, 0, 0.0, QstsMode.DAILY, 1.0, 1.0, QstsControlMode.STATIC),
				100000.0);

		assertFalse(disabledResult.isApplied());
		assertEquals(0.0, total(disabledGenerator).getImaginary(), 1.0e-12);

		Static3PGen cutInGenerator = generator("pv-cutin", 0.01, 0.0);
		InverterGenAdapter cutInAdapter = new InverterGenAdapter(cutInGenerator)
				.setCapabilityData(new InverterCapabilityData(12000.0, Double.NaN,
						Double.NaN, Double.NaN, 2000.0, 1000.0, true));
		InverterControlData cutInControl = new InverterControlData("inv2", "pv-cutin",
				InverterControlData.ControlMode.VOLTVAR, "", 0.0, Double.NaN,
				Double.NaN, 0.0, Double.NaN, 1000.0, Double.NaN, true);

		InverterControlResult belowCutIn = cutInAdapter.apply(cutInControl,
				new QstsStepContext(0, 0, 0.0, QstsMode.DAILY, 1.0, 1.0, QstsControlMode.STATIC),
				100000.0);
		assertFalse(belowCutIn.isApplied());
		assertEquals(0.0, total(cutInGenerator).getImaginary(), 1.0e-12);

		cutInGenerator.setPower3Phase(new Complex3x1(new Complex(0.01, 0.0),
				new Complex(0.01, 0.0), new Complex(0.01, 0.0)), UnitType.PU);
		InverterControlResult afterCutIn = cutInAdapter.apply(cutInControl,
				new QstsStepContext(1, 1, 1.0, QstsMode.DAILY, 1.0, 1.0, QstsControlMode.STATIC),
				100000.0);

		assertTrue(afterCutIn.isApplied());
		assertEquals(1000.0, afterCutIn.getReactivePowerKvar(), 1.0e-12);
		assertEquals(0.01, total(cutInGenerator).getImaginary(), 1.0e-12);
	}

	private static Static3PGen generator(String id, double pPu, double qPu) {
		Static3PGen generator = Static3PhaseFactory.eINSTANCE.createStatic3PGen();
		generator.setId(id);
		generator.setPower3Phase(new Complex3x1(new Complex(pPu / 3.0, qPu / 3.0),
				new Complex(pPu / 3.0, qPu / 3.0), new Complex(pPu / 3.0, qPu / 3.0)), UnitType.PU);
		return generator;
	}

	private static Complex total(Static3PGen generator) {
		Complex3x1 power = generator.getPower3Phase(UnitType.PU);
		return power.a_0.add(power.b_1).add(power.c_2);
	}
}
