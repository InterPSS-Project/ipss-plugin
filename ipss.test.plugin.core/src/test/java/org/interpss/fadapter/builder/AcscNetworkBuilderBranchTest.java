package org.interpss.fadapter.builder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.util.NumericUtil;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.XFormerConnectCode;

/**
 * Unit tests for AcscNetworkBuilder line Z0 and transformer zero-seq CC mapping.
 */
public class AcscNetworkBuilderBranchTest extends CorePluginTestSetup {

	private static final double TOL = 1.0E-6;

	@Test
	public void setLineZeroSeqData_setsZ0AndHalfCharging() throws Exception {
		AcscNetwork acsc = AcscBuilderTestFixture.createTwoBusWithLineOnly();
		AcscNetworkBuilder builder = new AcscNetworkBuilder(acsc);

		builder.setLineZeroSeqData("Bus1", "Bus2", "1",
				0.02, 0.08, 0.04, 0.0, 0.0, 0.0, 0.0);

		AcscBranch line = (AcscBranch) acsc.getBranch("Bus1", "Bus2", "1");
		assertTrue(NumericUtil.equals(line.getZ0(), new Complex(0.02, 0.08), TOL));
		assertEquals(0.02, line.getHB0(), TOL);
	}

	@Test
	public void setLineZeroSeqData_reverseOrientationLookup() throws Exception {
		AcscNetwork acsc = AcscBuilderTestFixture.createTwoBusWithLineOnly();
		AcscNetworkBuilder builder = new AcscNetworkBuilder(acsc);

		builder.setLineZeroSeqData("Bus2", "Bus1", "1",
				0.03, 0.09, 0.06, 0.0, 0.0, 0.0, 0.0);

		AcscBranch line = (AcscBranch) acsc.getBranch("Bus1", "Bus2", "1");
		assertTrue(NumericUtil.equals(line.getZ0(), new Complex(0.03, 0.09), TOL));
		assertEquals(0.03, line.getHB0(), TOL);
	}

	@Test
	public void setXfrZeroSeqData_cc1_ygYg() throws Exception {
		assertXfrCc(1, 0.01, 0.05,
				BusGroundCode.ZGROUNDED, XFormerConnectCode.WYE, new Complex(0.01, 0.05),
				BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0, 0.0));
	}

	@Test
	public void setXfrZeroSeqData_cc1_solidWhenRgXgZero() throws Exception {
		assertXfrCc(1, 0.0, 0.0,
				BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0, 0.0),
				BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0, 0.0));
	}

	@Test
	public void setXfrZeroSeqData_cc2_gyDelta() throws Exception {
		assertXfrCc(2, 0.02, 0.04,
				BusGroundCode.ZGROUNDED, XFormerConnectCode.WYE, new Complex(0.02, 0.04),
				BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0.0, 0.0));
	}

	@Test
	public void setXfrZeroSeqData_cc3_deltaGy() throws Exception {
		assertXfrCc(3, 0.02, 0.04,
				BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0.0, 0.0),
				BusGroundCode.ZGROUNDED, XFormerConnectCode.WYE, new Complex(0.02, 0.04));
	}

	@Test
	public void setXfrZeroSeqData_cc4_deltaDelta() throws Exception {
		assertXfrCc(4, 0.0, 0.0,
				BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0.0, 0.0),
				BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0.0, 0.0));
	}

	@Test
	public void setXfrZeroSeqData_cc5_limitedSupport() throws Exception {
		assertXfrCc(5, 0.03, 0.06,
				BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0, 0.0),
				BusGroundCode.ZGROUNDED, XFormerConnectCode.WYE, new Complex(0.03, 0.06));
	}

	@Test
	public void setXfrZeroSeqData_cc6_sameAsCc2() throws Exception {
		assertXfrCc(6, 0.01, 0.02,
				BusGroundCode.ZGROUNDED, XFormerConnectCode.WYE, new Complex(0.01, 0.02),
				BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0.0, 0.0));
	}

	@Test
	public void setXfrZeroSeqData_cc7_sameAsCc3() throws Exception {
		assertXfrCc(7, 0.01, 0.02,
				BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0.0, 0.0),
				BusGroundCode.ZGROUNDED, XFormerConnectCode.WYE, new Complex(0.01, 0.02));
	}

	@Test
	public void setXfrZeroSeqData_cc8_dualGrounding() throws Exception {
		AcscNetwork acsc = AcscBuilderTestFixture.createTwoBusWithXfrOnly();
		AcscNetworkBuilder builder = new AcscNetworkBuilder(acsc);

		builder.setXfrZeroSeqData("Bus1", "Bus2", "T1",
				8, 0.01, 0.05, 0.0, 0.1, 0.02, 0.03);

		AcscBranch xfr = (AcscBranch) acsc.getBranch("Bus1", "Bus2", "T1");
		assertTrue(NumericUtil.equals(xfr.getZ0(), new Complex(0.0, 0.1), TOL));
		assertEquals(BusGroundCode.ZGROUNDED, xfr.getFromGrounding().getGroundCode());
		assertEquals(XFormerConnectCode.WYE, xfr.getFromGrounding().getXfrConnectCode());
		assertTrue(NumericUtil.equals(xfr.getFromGrounding().getZ(), new Complex(0.01, 0.05), TOL));
		assertEquals(BusGroundCode.ZGROUNDED, xfr.getToGrounding().getGroundCode());
		assertEquals(XFormerConnectCode.WYE, xfr.getToGrounding().getXfrConnectCode());
		assertTrue(NumericUtil.equals(xfr.getToGrounding().getZ(), new Complex(0.02, 0.03), TOL));
	}

	@Test
	public void setXfrZeroSeqData_unknownCcDoesNotThrow() throws Exception {
		AcscNetwork acsc = AcscBuilderTestFixture.createTwoBusWithXfrOnly();
		AcscNetworkBuilder builder = new AcscNetworkBuilder(acsc);

		assertDoesNotThrow(() -> builder.setXfrZeroSeqData("Bus1", "Bus2", "T1",
				99, 0.0, 0.0, 0.0, 0.1, 0.0, 0.0));

		AcscBranch xfr = (AcscBranch) acsc.getBranch("Bus1", "Bus2", "T1");
		assertTrue(NumericUtil.equals(xfr.getZ0(), new Complex(0.0, 0.1), TOL));
	}

	@Test
	public void missingBranchDoesNotThrow() throws Exception {
		AcscNetwork acsc = AcscBuilderTestFixture.createTwoBusWithLineOnly();
		AcscNetworkBuilder builder = new AcscNetworkBuilder(acsc);

		assertDoesNotThrow(() -> {
			builder.setLineZeroSeqData("Bus1", "Bus2", "99",
					0.01, 0.02, 0.0, 0.0, 0.0, 0.0, 0.0);
			builder.setXfrZeroSeqData("Bus1", "Bus2", "99",
					1, 0.0, 0.0, 0.0, 0.1, 0.0, 0.0);
		});
	}

	private void assertXfrCc(int cc, double rg, double xg,
			BusGroundCode fromGround, XFormerConnectCode fromConnect, Complex fromZ,
			BusGroundCode toGround, XFormerConnectCode toConnect, Complex toZ) throws Exception {
		AcscNetwork acsc = AcscBuilderTestFixture.createTwoBusWithXfrOnly();
		AcscNetworkBuilder builder = new AcscNetworkBuilder(acsc);

		builder.setXfrZeroSeqData("Bus1", "Bus2", "T1",
				cc, rg, xg, 0.0, 0.12, 0.0, 0.0);

		AcscBranch xfr = (AcscBranch) acsc.getBranch("Bus1", "Bus2", "T1");
		assertTrue(NumericUtil.equals(xfr.getZ0(), new Complex(0.0, 0.12), TOL));
		assertEquals(fromGround, xfr.getFromGrounding().getGroundCode());
		assertEquals(fromConnect, xfr.getFromGrounding().getXfrConnectCode());
		assertTrue(NumericUtil.equals(xfr.getFromGrounding().getZ(), fromZ, TOL));
		assertEquals(toGround, xfr.getToGrounding().getGroundCode());
		assertEquals(toConnect, xfr.getToGrounding().getXfrConnectCode());
		assertTrue(NumericUtil.equals(xfr.getToGrounding().getZ(), toZ, TOL));
	}
}
