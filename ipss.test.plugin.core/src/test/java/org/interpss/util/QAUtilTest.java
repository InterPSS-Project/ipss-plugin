package org.interpss.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;

class QAUtilTest {
	@Test
	void voltageComparisonAlignsTheArbitraryGlobalAngleReference() {
		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		AclfBus bus1 = CoreObjectFactory.createAclfBus("Bus1", net).get();
		AclfBus bus2 = CoreObjectFactory.createAclfBus("Bus2", net).get();
		bus1.setVoltage(new Complex(1.02, 0.0));
		bus2.setVoltage(new Complex(0.98, 0.12));

		AclfNetwork reference = net.jsonCopy();
		double angle = Math.toRadians(30.0);
		Complex rotation = new Complex(Math.cos(angle), Math.sin(angle));
		reference.getBus("Bus1").setVoltage(bus1.getVoltage().multiply(rotation));
		reference.getBus("Bus2").setVoltage(bus2.getVoltage().multiply(rotation));

		assertTrue(QAUtil.getMaxBusVoltageDiff(net, reference) > 0.5);
		assertEquals(0.0,
				QAUtil.getMaxBusVoltageDiffAngleAligned(net, reference), 1.0e-12);
	}

	@Test
	void angleAlignmentDoesNotHideVoltageMagnitudeMismatch() {
		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		AclfBus bus = CoreObjectFactory.createAclfBus("Bus1", net).get();
		bus.setVoltage(new Complex(1.00, 0.0));

		AclfNetwork reference = net.jsonCopy();
		reference.getBus("Bus1").setVoltage(new Complex(0.97, 0.0));

		assertEquals(0.03,
				QAUtil.getMaxBusVoltageDiffAngleAligned(net, reference), 1.0e-12);
	}

	@Test
	void voltageComparisonAlignsEachDisconnectedAcIslandIndependently() throws Exception {
		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		AclfBus bus1 = CoreObjectFactory.createAclfBus("Bus1", net).get();
		AclfBus bus2 = CoreObjectFactory.createAclfBus("Bus2", net).get();
		AclfBus bus3 = CoreObjectFactory.createAclfBus("Bus3", net).get();
		AclfBus bus4 = CoreObjectFactory.createAclfBus("Bus4", net).get();
		connect(net, "Bus1", "Bus2");
		connect(net, "Bus3", "Bus4");
		bus1.setVoltage(new Complex(1.02, 0.00));
		bus2.setVoltage(new Complex(0.98, 0.12));
		bus3.setVoltage(new Complex(1.01, -0.04));
		bus4.setVoltage(new Complex(0.99, 0.08));

		AclfNetwork reference = net.jsonCopy();
		rotate(reference.getBus("Bus1"), 20.0);
		rotate(reference.getBus("Bus2"), 20.0);
		rotate(reference.getBus("Bus3"), -15.0);
		rotate(reference.getBus("Bus4"), -15.0);

		assertTrue(QAUtil.getMaxBusVoltageDiffAngleAligned(net, reference) > 0.1);
		assertEquals(0.0,
				QAUtil.getMaxBusVoltageDiffAngleAlignedByIsland(net, reference), 1.0e-12);
	}

	@Test
	void angleComparisonUsesEachIslandsType3BusAsItsReference() throws Exception {
		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		AclfBus bus1 = CoreObjectFactory.createAclfBus("Bus1", net).get();
		AclfBus bus2 = CoreObjectFactory.createAclfBus("Bus2", net).get();
		AclfBus bus3 = CoreObjectFactory.createAclfBus("Bus3", net).get();
		AclfBus bus4 = CoreObjectFactory.createAclfBus("Bus4", net).get();
		connect(net, "Bus1", "Bus2");
		connect(net, "Bus3", "Bus4");
		bus1.setGenCode(AclfGenCode.SWING);
		bus3.setGenCode(AclfGenCode.SWING);
		bus1.setVoltage(new Complex(1.00, 0.00));
		bus2.setVoltage(new Complex(0.98, 0.12));
		bus3.setVoltage(new Complex(1.01, -0.04));
		bus4.setVoltage(new Complex(0.99, 0.08));

		AclfNetwork reference = net.jsonCopy();
		rotate(reference.getBus("Bus1"), 20.0);
		rotate(reference.getBus("Bus2"), 20.5);
		rotate(reference.getBus("Bus3"), -15.0);
		rotate(reference.getBus("Bus4"), -15.0);

		assertEquals(2, QAUtil.getActiveAcIslandType3BusIds(net).size());
		assertTrue(QAUtil.getActiveAcIslandType3BusIds(net).contains(
				java.util.Set.of("Bus1")));
		assertTrue(QAUtil.getActiveAcIslandType3BusIds(net).contains(
				java.util.Set.of("Bus3")));
		assertEquals(0.5,
				QAUtil.getMaxBusVoltageAngleDiffAtType3ReferenceDeg(net, reference),
				1.0e-12);
	}

	@Test
	void angleComparisonRejectsDifferentType3Assignments() throws Exception {
		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		AclfBus bus1 = CoreObjectFactory.createAclfBus("Bus1", net).get();
		AclfBus bus2 = CoreObjectFactory.createAclfBus("Bus2", net).get();
		connect(net, "Bus1", "Bus2");
		bus1.setGenCode(AclfGenCode.SWING);
		bus1.setVoltage(new Complex(1.0, 0.0));
		bus2.setVoltage(new Complex(0.99, 0.02));

		AclfNetwork reference = net.jsonCopy();
		reference.getBus("Bus1").setGenCode(AclfGenCode.GEN_PV);
		reference.getBus("Bus2").setGenCode(AclfGenCode.SWING);

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> QAUtil.getMaxBusVoltageAngleDiffAtType3ReferenceDeg(net, reference));
		assertTrue(error.getMessage().contains("Type-3 bus assignments differ"));
	}

	@Test
	void inactiveGeneratorBusIsExcludedFromVoltageComparison() {
		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		AclfBus bus = CoreObjectFactory.createAclfBus("Bus1", net).get();
		bus.setGenCode(AclfGenCode.GEN_PQ);
		bus.setVoltage(new Complex(1.06, 0.0));
		bus.setStatus(false);

		AclfNetwork reference = net.jsonCopy();
		bus.setVoltage(Complex.ZERO);

		assertEquals(0.0, QAUtil.getMaxBusVoltageDiff(net, reference));
	}

	private static void connect(AclfNetwork net, String fromBusId, String toBusId)
			throws Exception {
		AclfBranch branch = CoreObjectFactory.createAclfBranch();
		branch.setCircuitNumber("1");
		branch.setStatus(true);
		net.addBranch(branch, fromBusId, toBusId);
	}

	private static void rotate(AclfBus bus, double degrees) {
		double angle = Math.toRadians(degrees);
		bus.setVoltage(bus.getVoltage().multiply(
				new Complex(Math.cos(angle), Math.sin(angle))));
	}
}
