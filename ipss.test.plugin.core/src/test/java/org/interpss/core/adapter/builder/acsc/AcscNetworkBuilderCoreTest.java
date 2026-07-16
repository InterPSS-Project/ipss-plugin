package org.interpss.core.adapter.builder.acsc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.builder.AcscNetworkBuilder;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.util.NumericUtil;
import org.junit.jupiter.api.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.AcscGen;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.BaseAcscBus;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.BusScCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;

/**
 * Unit tests for AcscNetworkBuilder core APIs: constructors, bus SC codes/grounding,
 * generator sequence Z, load/switched-shunt sequence Y, and missing-ID no-ops.
 */
public class AcscNetworkBuilderCoreTest extends CorePluginTestSetup {

	private static final double TOL = 1.0E-6;

	@Test
	public void constructorsAndAccessor() {
		AcscNetworkBuilder empty = new AcscNetworkBuilder();
		assertNotNull(empty.getAcscNetwork());
		assertEquals(0, empty.getAcscNetwork().getNoBus());

		AcscNetwork existing = CoreObjectFactory.createAcscNetwork();
		AcscNetworkBuilder wrapped = new AcscNetworkBuilder(existing);
		assertSame(existing, wrapped.getAcscNetwork());

		// BaseAcscNetwork ctor accepts DStab; getAcscNetwork() only valid for AcscNetwork
		DStabilityNetwork dstab = DStabObjectFactory.createDStabilityNetwork();
		AcscNetworkBuilder baseWrapped = new AcscNetworkBuilder(dstab);
		assertDoesNotThrow(() -> baseWrapped.setLoadNegSeqShuntY("missing", 0.0, 0.0));
	}

	@Test
	public void setContributingAndNonContributingBus() throws Exception {
		AcscNetwork acsc = AcscBuilderTestFixture.createTwoBusWithLineAndXfr();
		AcscNetworkBuilder builder = new AcscNetworkBuilder(acsc);

		builder.setContributingBus("Bus2");
		BaseAcscBus<?, ?> loadBus = (BaseAcscBus<?, ?>) acsc.getBus("Bus2");
		assertEquals(BusScCode.CONTRIBUTE, loadBus.getScCode());

		builder.setNonContributingBus("Bus2");
		assertEquals(BusScCode.NON_CONTRI, loadBus.getScCode());
		assertTrue(NumericUtil.equals(loadBus.getScGenZ1(), NumericConstant.LargeBusZ, TOL));
		assertTrue(NumericUtil.equals(loadBus.getScGenZ2(), NumericConstant.LargeBusZ, TOL));
		assertTrue(NumericUtil.equals(loadBus.getScGenZ0(), NumericConstant.LargeBusZ, TOL));
		assertEquals(BusGroundCode.UNGROUNDED, loadBus.getGrounding().getGroundCode());
		assertTrue(NumericUtil.equals(loadBus.getGrounding().getZ(), NumericConstant.LargeBusZ, TOL));
	}

	@Test
	public void setBusGrounding() throws Exception {
		AcscNetwork acsc = AcscBuilderTestFixture.createTwoBusWithLineAndXfr();
		AcscNetworkBuilder builder = new AcscNetworkBuilder(acsc);

		Complex zg = new Complex(0.01, 0.05);
		builder.setBusGrounding("Bus1", BusGroundCode.ZGROUNDED, zg);

		BaseAcscBus<?, ?> bus = (BaseAcscBus<?, ?>) acsc.getBus("Bus1");
		assertEquals(BusGroundCode.ZGROUNDED, bus.getGrounding().getGroundCode());
		assertTrue(NumericUtil.equals(bus.getGrounding().getZ(), zg, TOL));

		builder.setBusGrounding("Bus1", BusGroundCode.SOLID_GROUNDED, null);
		assertEquals(BusGroundCode.SOLID_GROUNDED, bus.getGrounding().getGroundCode());
		assertTrue(NumericUtil.equals(bus.getGrounding().getZ(), zg, TOL));
	}

	@Test
	public void setGenSequenceImpedances() throws Exception {
		AcscNetwork acsc = AcscBuilderTestFixture.createTwoBusWithLineAndXfr();
		AcscNetworkBuilder builder = new AcscNetworkBuilder(acsc);

		builder.setGenPosSeqZ("Bus1", "1", 0.0, 0.25);
		builder.setGenNegSeqZ("Bus1", "1", 0.01, 0.20);
		builder.setGenZeroSeqZ("Bus1", "1", 0.02, 0.15);

		AcscGen gen = (AcscGen) ((BaseAcscBus<?, ?>) acsc.getBus("Bus1")).getContributeGenList().get(0);
		assertTrue(NumericUtil.equals(gen.getPosGenZ(), new Complex(0.0, 0.25), TOL));
		assertTrue(NumericUtil.equals(gen.getNegGenZ(), new Complex(0.01, 0.20), TOL));
		assertTrue(NumericUtil.equals(gen.getZeroGenZ(), new Complex(0.02, 0.15), TOL));
	}

	@Test
	public void setLoadAndSwitchedShuntSequenceY() throws Exception {
		AcscNetwork acsc = AcscBuilderTestFixture.createTwoBusWithLineAndXfr();
		AcscNetworkBuilder builder = new AcscNetworkBuilder(acsc);

		builder.setLoadNegSeqShuntY("Bus2", 0.1, 0.2);
		builder.setLoadZeroSeqShuntY("Bus2", 0.05, 0.08);
		builder.setSwitchedShuntZeroSeqY("Bus2", 0.0, 0.03);

		BaseAcscBus<?, ?> bus = (BaseAcscBus<?, ?>) acsc.getBus("Bus2");
		assertTrue(NumericUtil.equals(bus.getScLoadShuntY2(), new Complex(0.1, 0.2), TOL));
		assertTrue(NumericUtil.equals(bus.getScLoadShuntY0(), new Complex(0.05, 0.08), TOL));
		assertTrue(NumericUtil.equals(bus.getScSwitchedShuntY0(), new Complex(0.0, 0.03), TOL));
	}

	@Test
	public void missingIdsDoNotThrow() throws Exception {
		AcscNetwork acsc = AcscBuilderTestFixture.createTwoBusWithLineAndXfr();
		AcscNetworkBuilder builder = new AcscNetworkBuilder(acsc);

		assertDoesNotThrow(() -> {
			builder.setContributingBus("missing");
			builder.setNonContributingBus("missing");
			builder.setBusGrounding("missing", BusGroundCode.ZGROUNDED, new Complex(0.1, 0.1));
			builder.setGenPosSeqZ("missing", "1", 0.0, 0.2);
			builder.setGenNegSeqZ("Bus1", "missing", 0.0, 0.2);
			builder.setGenZeroSeqZ("Bus1", "99", 0.0, 0.2);
			builder.setLoadNegSeqShuntY("missing", 0.1, 0.1);
			builder.setLoadZeroSeqShuntY("missing", 0.1, 0.1);
			builder.setSwitchedShuntZeroSeqY("missing", 0.0, 0.1);
		});
	}
}
