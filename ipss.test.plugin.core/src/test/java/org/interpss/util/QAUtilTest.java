package org.interpss.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;

import com.interpss.core.CoreObjectFactory;
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
}
