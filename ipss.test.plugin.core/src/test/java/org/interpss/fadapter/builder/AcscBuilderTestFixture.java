package org.interpss.fadapter.builder;

import org.apache.commons.math3.complex.Complex;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.net.OriginalDataFormat;

/**
 * Shared topology fixtures for AcscNetworkBuilder unit tests.
 */
final class AcscBuilderTestFixture {

	private AcscBuilderTestFixture() {}

	/**
	 * Two-bus network: Bus1 swing+gen, Bus2 load, one line (ckt 1) and one 2W xfr (ckt T1).
	 */
	static AcscNetwork createTwoBusWithLineAndXfr() throws Exception {
		AcscNetwork acsc = CoreObjectFactory.createAcscNetwork();
		AclfNetworkBuilder topo = new AclfNetworkBuilder(acsc);
		topo.setNetworkInfo("ut", "ut", 100000.0, OriginalDataFormat.PSSE);
		topo.addBus("Bus1", "Gen", 1L, 138000.0, 1.0, 0.0, null, null, null);
		topo.addBus("Bus2", "Load", 2L, 138000.0, 1.0, 0.0, null, null, null);
		topo.setSwingBus("Bus1", 1.0, 0.0);
		topo.addContributeGen("Bus1", "1", true, 0.5, 0.1, 100.0, 1.0,
				0.3, -0.2, 1.0, 0.0, new Complex(0.0, 0.2), null, 0.0, null, 0.0, 0.0);
		topo.addContributeLoad("Bus2", "1", true, new Complex(0.3, 0.1), null, null, null, false);
		topo.addLine("Bus1", "Bus2", "1", new Complex(0.01, 0.1), null, null, null, 100.0, 0.0, 0.0, true);
		topo.addXformer2W("Bus1", "Bus2", "T1", new Complex(0.0, 0.1), 1.0, 1.0,
				null, null, 100.0, 0.0, 0.0, 0, true);
		return acsc;
	}

	/**
	 * Two-bus network with only a 2W transformer (ckt T1) for grounding CC tests.
	 */
	static AcscNetwork createTwoBusWithXfrOnly() throws Exception {
		AcscNetwork acsc = CoreObjectFactory.createAcscNetwork();
		AclfNetworkBuilder topo = new AclfNetworkBuilder(acsc);
		topo.setNetworkInfo("ut", "ut", 100000.0, OriginalDataFormat.PSSE);
		topo.addBus("Bus1", "Gen", 1L, 138000.0, 1.0, 0.0, null, null, null);
		topo.addBus("Bus2", "Load", 2L, 138000.0, 1.0, 0.0, null, null, null);
		topo.setSwingBus("Bus1", 1.0, 0.0);
		topo.addContributeGen("Bus1", "1", true, 0.5, 0.1, 100.0, 1.0,
				0.3, -0.2, 1.0, 0.0, new Complex(0.0, 0.2), null, 0.0, null, 0.0, 0.0);
		topo.addXformer2W("Bus1", "Bus2", "T1", new Complex(0.0, 0.1), 1.0, 1.0,
				null, null, 100.0, 0.0, 0.0, 0, true);
		return acsc;
	}

	/**
	 * Two-bus network with only a line (ckt 1).
	 */
	static AcscNetwork createTwoBusWithLineOnly() throws Exception {
		AcscNetwork acsc = CoreObjectFactory.createAcscNetwork();
		AclfNetworkBuilder topo = new AclfNetworkBuilder(acsc);
		topo.setNetworkInfo("ut", "ut", 100000.0, OriginalDataFormat.PSSE);
		topo.addBus("Bus1", "Gen", 1L, 138000.0, 1.0, 0.0, null, null, null);
		topo.addBus("Bus2", "Load", 2L, 138000.0, 1.0, 0.0, null, null, null);
		topo.setSwingBus("Bus1", 1.0, 0.0);
		topo.addContributeGen("Bus1", "1", true, 0.5, 0.1, 100.0, 1.0,
				0.3, -0.2, 1.0, 0.0, new Complex(0.0, 0.2), null, 0.0, null, 0.0, 0.0);
		topo.addLine("Bus1", "Bus2", "1", new Complex(0.01, 0.1), null, null, null, 100.0, 0.0, 0.0, true);
		return acsc;
	}
}
