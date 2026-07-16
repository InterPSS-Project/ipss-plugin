package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter;
import org.interpss.dstab.control.exc.ieee.y1981.dc1.IEEE1981DC1Exciter;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.exc.simple.SimpleExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.mach.Machine;

/**
 * Unit tests for DStabNetworkBuilder exciter APIs.
 */
public class DStabNetworkBuilderExciterTest extends CorePluginTestSetup {

	private static final double TOL = 1.0E-6;

	@Test
	public void addExcIeeet1_setsDataAndAttaches() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		Ieee1968Type1Exciter exc = builder.addExcIeeet1("Bus1", "1",
				0.02, 200.0, 0.05, 5.0, -5.0,
				1.0, 0.5, 0.03, 1.0,
				3.0, 0.1, 4.0, 0.2);

		assertNotNull(exc);
		assertEquals(0.02, exc.getData().getTr(), TOL);
		assertEquals(200.0, exc.getData().getKa(), TOL);
		assertEquals(0.05, exc.getData().getTa(), TOL);
		assertEquals(5.0, exc.getData().getVrmax(), TOL);
		assertEquals(-5.0, exc.getData().getVrmin(), TOL);
		assertEquals(1.0, exc.getData().getKe(), TOL);
		assertEquals(0.5, exc.getData().getTe(), TOL);
		assertEquals(0.03, exc.getData().getKf(), TOL);
		assertEquals(1.0, exc.getData().getTf(), TOL);
		assertEquals(3.0, exc.getData().getE1(), TOL);
		assertEquals(0.1, exc.getData().getSeE1(), TOL);
		assertEquals(4.0, exc.getData().getE2(), TOL);
		assertEquals(0.2, exc.getData().getSeE2(), TOL);

		Machine mach = builder.getDStabNetwork().getMachine("Bus1-mach1");
		assertSame(exc, mach.getExciter());
	}

	@Test
	public void addExcIeee1981Dc1_setsData_andTeZeroBecomes001() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		IEEE1981DC1Exciter exc = builder.addExcIeee1981Dc1("Bus1", "1",
				40.0, 0.05, 1.0, 1.0,
				5.0, -5.0,
				1.0, 0.0, 0.03, 1.0,
				3.0, 0.1, 4.0, 0.2);

		assertNotNull(exc);
		assertEquals(40.0, exc.getData().getKa(), TOL);
		assertEquals(0.05, exc.getData().getTa(), TOL);
		assertEquals(1.0, exc.getData().getTc(), TOL);
		assertEquals(1.0, exc.getData().getTb(), TOL);
		assertEquals(0.001, exc.getData().getTe(), TOL);
		assertEquals(0.1, exc.getData().getSe_e1(), TOL);
		assertEquals(0.2, exc.getData().getSe_e2(), TOL);
		assertSame(exc, builder.getDStabNetwork().getMachine("Bus1-mach1").getExciter());
	}

	@Test
	public void addExcIeee1981St1_setsData() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		IEEE1981ST1Exciter exc = builder.addExcIeee1981St1("Bus1", "1",
				200.0, 0.01, 1.0, 1.0,
				5.0, -5.0,
				0.03, 1.0, 0.1,
				0.2, -0.2);

		assertNotNull(exc);
		assertEquals(200.0, exc.getData().getKa(), TOL);
		assertEquals(0.01, exc.getData().getTa(), TOL);
		assertEquals(0.1, exc.getData().getKc(), TOL);
		assertEquals(0.2, exc.getData().getVimax(), TOL);
		assertEquals(-0.2, exc.getData().getVimin(), TOL);
		assertSame(exc, builder.getDStabNetwork().getMachine("Bus1-mach1").getExciter());
	}

	@Test
	public void addExcSimple_setsData() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		SimpleExciter exc = builder.addExcSimple("Bus1", "1", 50.0, 0.05, 5.0, -5.0);

		assertNotNull(exc);
		assertEquals(50.0, exc.getData().getKa(), TOL);
		assertEquals(0.05, exc.getData().getTa(), TOL);
		assertEquals(5.0, exc.getData().getVrmax(), TOL);
		assertEquals(-5.0, exc.getData().getVrmin(), TOL);
		assertSame(exc, builder.getDStabNetwork().getMachine("Bus1-mach1").getExciter());
	}

	@Test
	public void noMachine_returnsNull() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		assertNull(builder.addExcIeeet1("Bus1", "1",
				0.02, 200.0, 0.05, 5.0, -5.0, 1.0, 0.5, 0.03, 1.0, 3.0, 0.1, 4.0, 0.2));
		assertNull(builder.addExcIeee1981Dc1("Bus1", "1",
				40.0, 0.05, 1.0, 1.0, 5.0, -5.0, 1.0, 0.5, 0.03, 1.0, 3.0, 0.1, 4.0, 0.2));
		assertNull(builder.addExcIeee1981St1("Bus1", "1",
				200.0, 0.01, 1.0, 1.0, 5.0, -5.0, 0.03, 1.0, 0.1, 0.2, -0.2));
		assertNull(builder.addExcSimple("Bus1", "1", 50.0, 0.05, 5.0, -5.0));
	}

	@Test
	public void missingBus_returnsNull() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		assertNull(builder.addExcIeeet1("missing", "1",
				0.02, 200.0, 0.05, 5.0, -5.0, 1.0, 0.5, 0.03, 1.0, 3.0, 0.1, 4.0, 0.2));
		assertNull(builder.addExcSimple("missing", "1", 50.0, 0.05, 5.0, -5.0));
	}
}
