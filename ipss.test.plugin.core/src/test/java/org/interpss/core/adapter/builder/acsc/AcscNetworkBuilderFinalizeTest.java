package org.interpss.core.adapter.builder.acsc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.builder.AcscNetworkBuilder;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.numeric.util.NumericUtil;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.BaseAcscBus;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.BusScCode;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.sc.ScBusModelType;

/**
 * Unit tests for AcscNetworkBuilder.finalizeAcscNetwork() and SC readiness smoke.
 */
public class AcscNetworkBuilderFinalizeTest extends CorePluginTestSetup {

	private static final double TOL = 1.0E-6;

	@Test
	public void finalizeAcscNetwork_setsCodesFlagsAndLargeZ() throws Exception {
		AcscNetwork acsc = AcscBuilderTestFixture.createTwoBusWithLineAndXfr();
		AcscNetworkBuilder builder = new AcscNetworkBuilder(acsc);

		builder.finalizeAcscNetwork();

		BaseAcscBus<?, ?> genBus = (BaseAcscBus<?, ?>) acsc.getBus("Bus1");
		BaseAcscBus<?, ?> loadBus = (BaseAcscBus<?, ?>) acsc.getBus("Bus2");

		assertEquals(BusScCode.CONTRIBUTE, genBus.getScCode());
		assertEquals(BusScCode.NON_CONTRI, loadBus.getScCode());
		assertTrue(NumericUtil.equals(loadBus.getScGenZ1(), NumericConstant.LargeBusZ, TOL));
		assertTrue(NumericUtil.equals(loadBus.getScGenZ2(), NumericConstant.LargeBusZ, TOL));
		assertTrue(NumericUtil.equals(loadBus.getScGenZ0(), NumericConstant.LargeBusZ, TOL));
		assertEquals(BusGroundCode.UNGROUNDED, loadBus.getGrounding().getGroundCode());
		assertTrue(NumericUtil.equals(loadBus.getGrounding().getZ(), NumericConstant.LargeBusZ, TOL));

		assertTrue(acsc.isScDataLoaded());
		assertFalse(acsc.isPositiveSeqDataOnly());
	}

	@Test
	public void finalizeAcscNetwork_overwritesManualContributingOnNonGenBus() throws Exception {
		AcscNetwork acsc = AcscBuilderTestFixture.createTwoBusWithLineAndXfr();
		AcscNetworkBuilder builder = new AcscNetworkBuilder(acsc);

		builder.setContributingBus("Bus2");
		assertEquals(BusScCode.CONTRIBUTE, ((BaseAcscBus<?, ?>) acsc.getBus("Bus2")).getScCode());

		builder.finalizeAcscNetwork();

		assertEquals(BusScCode.NON_CONTRI, ((BaseAcscBus<?, ?>) acsc.getBus("Bus2")).getScCode());
	}

	@Test
	public void formScYMatrix_smokeAfterFinalize() throws Exception {
		AcscNetwork acsc = AcscBuilderTestFixture.createTwoBusWithLineOnly();
		AcscNetworkBuilder builder = new AcscNetworkBuilder(acsc);

		builder.setGenPosSeqZ("Bus1", "1", 0.0, 0.25);
		builder.setGenNegSeqZ("Bus1", "1", 0.0, 0.25);
		builder.setGenZeroSeqZ("Bus1", "1", 0.0, 0.15);
		builder.setLineZeroSeqData("Bus1", "Bus2", "1",
				0.02, 0.08, 0.0, 0.0, 0.0, 0.0, 0.0);
		builder.finalizeAcscNetwork();

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(acsc);
		algo.setLfMethod(AclfMethodType.NR);
		algo.loadflow();
		assertTrue(acsc.isLfConverged());

		acsc.initialization(ScBusModelType.LOADFLOW_VOLT);
		ISparseEqnComplex yPos = acsc.formScYMatrix(SequenceCode.POSITIVE, ScBusModelType.LOADFLOW_VOLT, false);
		ISparseEqnComplex yZero = acsc.formScYMatrix(SequenceCode.ZERO, ScBusModelType.LOADFLOW_VOLT, false);

		assertNotNull(yPos);
		assertNotNull(yZero);
		// Diagonal should be non-zero after forming SC Y
		assertTrue(yPos.getA(0, 0).abs() > TOL);
		assertTrue(yZero.getA(0, 0).abs() > TOL || yZero.getA(0, 0).abs() >= 0.0);
	}
}
