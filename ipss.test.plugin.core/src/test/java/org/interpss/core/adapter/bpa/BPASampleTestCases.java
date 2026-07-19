package org.interpss.core.adapter.bpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.fadapter.bpa.BPADirectParser;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;

/**
 * BPA 9-bus sample coverage via {@code BPAFormat} facade and {@link BPADirectParser}.
 */
public class BPASampleTestCases extends CorePluginTestSetup {

	@Test
	public void ieee9FacadeLoadflow() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.BPA)
				.load("testData/adpter/bpa/IEEE9.dat")
				.getAclfNet();

		assertEquals(9, net.getNoActiveBus());
		assertTrue(net.getNoActiveBranch() >= 9, "IEEE9 has 6 lines + 3 xfrs");
		assertNotNull(net.getBus("Bus1"));
		assertEquals(AclfGenCode.SWING, net.getBus("Bus1").getGenCode());

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.getLfAdjAlgo().getLimitCtrlConfig().setCheckGenQLimitImmediate(false);
		algo.loadflow();

		assertTrue(net.isLfConverged());
		AclfBus swingBus = net.getBus("Bus1");
		AclfSwingBusAdapter swing = swingBus.toSwingBus();
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal() - 1.0586) < 0.01);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary() - 0.4366) < 0.01);
	}

	@Test
	public void ieee9DirectParser() throws Exception {
		AclfNetwork net = new BPADirectParser().parse("testData/adpter/bpa/IEEE9.dat");
		assertEquals(9, net.getNoActiveBus());
		assertTrue(net.getNoActiveBranch() >= 9);
		assertEquals(AclfGenCode.SWING, net.getBus("Bus1").getGenCode());
		assertEquals(100.0, net.getBaseKva() / 1000.0, 1.0E-6, "default BPA base is 100 MVA");
	}

	@Test
	public void test009FacadeLoadflow() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.BPA)
				.load("testData/adpter/bpa/Test009bpa.DAT")
				.getAclfNet();

		assertEquals(9, net.getNoActiveBus());
		assertTrue(net.getBusList().stream().anyMatch(b -> b.getGenCode() == AclfGenCode.GEN_PV),
				"Test009 uses BQ → PV buses");

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.loadflow();

		assertTrue(net.isLfConverged());
		AclfBus swingBus = net.getBus("Bus1");
		AclfSwingBusAdapter swing = swingBus.toSwingBus();
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal() - 0.7164) < 0.01);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary() - 0.2705) < 0.01);
	}
}
