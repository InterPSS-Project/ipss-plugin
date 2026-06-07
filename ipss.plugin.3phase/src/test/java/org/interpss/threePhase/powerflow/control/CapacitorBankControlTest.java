package org.interpss.threePhase.powerflow.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.powerflow.control.CapacitorControlData.ControlType;
import org.interpss.threePhase.powerflow.control.CapacitorControlData.PhaseSelection;
import org.interpss.threePhase.qsts.control.QstsControlQueue;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.IPhaseLoad;
import com.interpss.core.threephase.Static3PBus;
import com.interpss.core.threephase.Static3PLoad;
import com.interpss.core.threephase.Static3PNetwork;
import com.interpss.core.threephase.Static3PhaseFactory;

public class CapacitorBankControlTest {
	@Test
	void voltageControlSwitchesStaticCapacitorLoad() throws InterpssException {
		Static3PNetwork network = Static3PhaseFactory.eINSTANCE.createStatic3PNetwork();
		network.setBaseKva(10000.0);
		Static3PBus bus = Static3PhaseFactory.eINSTANCE.createStatic3PBus();
		bus.setId("capbus");
		bus.setBaseVoltage(12470.0);
		bus.setGenCode(AclfGenCode.SWING);
		network.addBus(bus);

		Static3PLoad capacitor = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
		capacitor.setId("cap1");
		capacitor.setCode(AclfLoadCode.CONST_Z);
		((IPhaseLoad) capacitor).set3PhaseLoad(new Complex3x1(new Complex(0.0, -0.01),
				new Complex(0.0, -0.01), new Complex(0.0, -0.01)));
		bus.getContributeLoadList().add(capacitor);

		CapacitorControlData control = new CapacitorControlData("ctrl1", "cap1", "", 1,
				ControlType.VOLTAGE, 118.0, 121.0, 60.0, 1.0, false, 0.0, 0.0,
				0.0, 0.0, PhaseCode.ABC, PhaseSelection.AVG);
		CapacitorBankControl capacitorControl = new CapacitorBankControl();

		bus.set3PhaseVotlages(balancedVoltage(1.02));
		assertTrue(capacitorControl.apply(network, List.of(control)));
		assertFalse(control.isClosed());
		assertEquals(0.0, capacitor.getInit3PhaseLoad().a_0.abs(), 1.0e-12);

		bus.set3PhaseVotlages(balancedVoltage(0.975));
		assertTrue(capacitorControl.apply(network, List.of(control)));
		assertTrue(control.isClosed());
		assertEquals(-0.01, capacitor.getInit3PhaseLoad().a_0.getImaginary(), 1.0e-12);
	}

	@Test
	void delayedVoltageControlSchedulesAndCancelsCapacitorSwitching() throws InterpssException {
		Static3PNetwork network = Static3PhaseFactory.eINSTANCE.createStatic3PNetwork();
		network.setBaseKva(10000.0);
		Static3PBus bus = Static3PhaseFactory.eINSTANCE.createStatic3PBus();
		bus.setId("capbus");
		bus.setBaseVoltage(12470.0);
		bus.setGenCode(AclfGenCode.SWING);
		network.addBus(bus);

		Static3PLoad capacitor = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
		capacitor.setId("cap1");
		capacitor.setCode(AclfLoadCode.CONST_Z);
		((IPhaseLoad) capacitor).set3PhaseLoad(new Complex3x1(new Complex(0.0, -0.01),
				new Complex(0.0, -0.01), new Complex(0.0, -0.01)));
		bus.getContributeLoadList().add(capacitor);

		CapacitorControlData control = new CapacitorControlData("ctrl1", "cap1", "", 1,
				ControlType.VOLTAGE, 118.0, 121.0, 60.0, 1.0, false, 0.0, 0.0,
				5.0, 10.0, PhaseCode.ABC, PhaseSelection.AVG);
		CapacitorBankControl capacitorControl = new CapacitorBankControl();
		QstsControlQueue queue = new QstsControlQueue();

		bus.set3PhaseVotlages(balancedVoltage(1.02));
		assertEquals(1, capacitorControl.scheduleDelayed(network, List.of(control), queue, 0.0));
		assertEquals(0, queue.processUntil(9.999));
		assertTrue(control.isClosed());
		assertEquals(-0.01, capacitor.getInit3PhaseLoad().a_0.getImaginary(), 1.0e-12);

		bus.set3PhaseVotlages(balancedVoltage(1.0));
		assertEquals(0, capacitorControl.scheduleDelayed(network, List.of(control), queue, 5.0));
		assertEquals(0, queue.processUntil(10.0));
		assertTrue(control.isClosed());

		bus.set3PhaseVotlages(balancedVoltage(1.02));
		assertEquals(1, capacitorControl.scheduleDelayed(network, List.of(control), queue, 10.0));
		assertEquals(1, queue.processUntil(20.0));
		assertFalse(control.isClosed());
		assertEquals(0.0, capacitor.getInit3PhaseLoad().a_0.abs(), 1.0e-12);
	}

	private static Complex3x1 balancedVoltage(double magnitude) {
		return new Complex3x1(new Complex(magnitude, 0.0),
				new Complex(-0.5 * magnitude, -0.8660254037844386 * magnitude),
				new Complex(-0.5 * magnitude, 0.8660254037844386 * magnitude));
	}
}
