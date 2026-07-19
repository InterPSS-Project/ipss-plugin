package org.interpss.core.adapter.builder.dstab;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.builder.DStabNetworkBuilder;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.core.net.OriginalDataFormat;

/**
 * Shared topology fixtures for DStabNetworkBuilder unit tests.
 */
final class DStabBuilderTestFixture {

	private DStabBuilderTestFixture() {}

	/**
	 * One-bus swing network with a contribute gen (no machine yet).
	 */
	static DStabNetworkBuilder createBuilder() throws Exception {
		DStabilityNetwork net = DStabObjectFactory.createDStabilityNetwork();
		net.setBaseKva(100000.0);
		AclfNetworkBuilder topo = new AclfNetworkBuilder(net);
		topo.setNetworkInfo("ut", "ut", 100000.0, OriginalDataFormat.PSSE);
		topo.addBus("Bus1", "Gen", 1L, 16500.0, 1.04, 0.0, null, null, null);
		topo.setSwingBus("Bus1", 1.04, 0.0);
		topo.addContributeGen("Bus1", "1", true, 0.7164, 0.2705, 100.0, 1.04,
				0.0, 0.0, 0.0, 0.0, new Complex(0.0, 0.04), null, 0.0, null, 0.0, 0.0);
		return new DStabNetworkBuilder(net);
	}

	/**
	 * Same as {@link #createBuilder()} plus a GENROU machine for exciter/governor tests.
	 */
	static DStabNetworkBuilder createWithMachine() throws Exception {
		DStabNetworkBuilder builder = createBuilder();
		builder.addGenrou("Bus1", "1",
				100.0, 16.5,
				8.0, 0.03, 0.4, 0.05,
				5.0, 0.0,
				1.8, 1.7, 0.3, 0.55, 0.25, 0.15,
				0.0, 0.0);
		return builder;
	}
}
