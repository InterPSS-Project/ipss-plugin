package org.interpss.threePhase.powerflow.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.powerflow.control.InverterControlData.ControlMode;
import org.interpss.threePhase.powerflow.control.InverterControlModel.InverterControlResult;
import org.junit.jupiter.api.Test;

import com.interpss.core.threephase.Static3PGen;
import com.interpss.core.threephase.Static3PhaseFactory;
import com.interpss.core.threephase.IPhaseGen;
import com.interpss.core.threephase.Static1Gen;
import com.interpss.core.acsc.PhaseCode;

public class InverterControlModelTest {
	@Test
	void reactiveSetpointIsLimitedByStaticKvaCapabilityAndPreservesGeneratorSign() {
		Static3PGen generator = Static3PhaseFactory.eINSTANCE.createStatic3PGen();
		generator.setId("pv1");
		generator.setMvaBase(1.0);
		generator.setPower3Phase(new Complex3x1(new Complex(0.08 / 3.0, 0.0),
				new Complex(0.08 / 3.0, 0.0), new Complex(0.08 / 3.0, 0.0)), UnitType.PU);
		InverterControlData control = new InverterControlData("inv1", "pv1", ControlMode.VOLTVAR,
				"vv1", 100.0, Double.NaN, Double.NaN, 0.0, true);

		InverterControlResult result = new InverterControlModel()
				.applyReactivePowerSetpoint(generator, 1000.0, control, 90.0);

		assertTrue(result.isApplied());
		assertTrue(result.isLimited());
		assertEquals(60.0, result.getReactivePowerKvar(), 1.0e-12);
		Complex3x1 power = generator.getPower3Phase(UnitType.PU);
		assertEquals(0.08, power.a_0.add(power.b_1).add(power.c_2).getReal(), 1.0e-12);
		assertEquals(0.06, power.a_0.add(power.b_1).add(power.c_2).getImaginary(), 1.0e-12);
	}

	@Test
	void reactiveSetpointHonorsExplicitKvarLimitsInsideCapability() {
		Static3PGen generator = Static3PhaseFactory.eINSTANCE.createStatic3PGen();
		generator.setId("pv1");
		generator.setMvaBase(1.0);
		generator.setPower3Phase(new Complex3x1(new Complex(0.02 / 3.0, 0.0),
				new Complex(0.02 / 3.0, 0.0), new Complex(0.02 / 3.0, 0.0)), UnitType.PU);
		InverterControlData control = new InverterControlData("inv1", "pv1", ControlMode.VOLTVAR,
				"vv1", 100.0, -30.0, 25.0, 0.0, true);

		InverterControlResult result = new InverterControlModel()
				.applyReactivePowerSetpoint(generator, 1000.0, control, -50.0);

		assertTrue(result.isLimited());
		assertEquals(-30.0, result.getReactivePowerKvar(), 1.0e-12);
		assertEquals(-0.03, generator.getPower3Phase(UnitType.PU)
				.a_0.add(generator.getPower3Phase(UnitType.PU).b_1)
				.add(generator.getPower3Phase(UnitType.PU).c_2).getImaginary(), 1.0e-12);
	}

	@Test
	void onePhaseReactiveSetpointUsesSinglePhaseCapabilityAndSignConvention() {
		Static1Gen generator = new Static1Gen("pv1", PhaseCode.C, new Complex(0.08, 0.0));
		InverterControlData control = new InverterControlData("inv1", "pv1", ControlMode.VOLTVAR,
				"vv1", 100.0, Double.NaN, Double.NaN, 0.0, true);

		InverterControlResult result = new InverterControlModel()
				.applyReactivePowerSetpoint(generator, 1000.0, control, 90.0);

		assertTrue(result.isLimited());
		assertEquals(60.0, result.getReactivePowerKvar(), 1.0e-12);
		assertEquals(0.08, generator.getPower1Phase(PhaseCode.C, UnitType.PU).getReal(), 1.0e-12);
		assertEquals(0.06, generator.getPower1Phase(PhaseCode.C, UnitType.PU).getImaginary(), 1.0e-12);
	}

	@Test
	void phaseGeneratorReactiveSetpointSupportsTwoPhaseVector() {
		TestPhaseGen generator = new TestPhaseGen("pv-ab", PhaseCode.AB,
				new Complex3x1(new Complex(0.04, 0.0), new Complex(0.04, 0.0), Complex.ZERO));
		InverterControlData control = new InverterControlData("inv1", "pv-ab", ControlMode.VOLTVAR,
				"vv1", 100.0, Double.NaN, Double.NaN, 0.0, true);

		InverterControlResult result = new InverterControlModel()
				.applyReactivePowerSetpoint(generator, 1000.0, control, 40.0);

		assertTrue(result.isApplied());
		assertEquals(40.0, result.getReactivePowerKvar(), 1.0e-12);
		assertEquals(0.02, generator.getPower3Phase(UnitType.PU).a_0.getImaginary(), 1.0e-12);
		assertEquals(0.02, generator.getPower3Phase(UnitType.PU).b_1.getImaginary(), 1.0e-12);
		assertEquals(0.0, generator.getPower3Phase(UnitType.PU).c_2.getImaginary(), 1.0e-12);
	}

	private static class TestPhaseGen implements IPhaseGen {
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
