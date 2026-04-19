package org.interpss.core.adapter.matpower;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfPSXformerAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.NrMethodConfig;
import com.interpss.core.algo.NrOptimizeAlgoType;
import com.interpss.core.funcImpl.AclfAdjCtrlFunction;

public class MatpowerCase3375WpTest extends CorePluginTestSetup {
	private static final String CASE3375WP_FILE = "testData/adpter/matpower/case3375wp.m";
	private static final double DATA_TOL = 1.0E-6;

	private AclfNetwork loadMatpowerCase(String path) throws Exception {
		return CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.MATPOWER)
				.load(path)
				.getAclfNet();
	}

	private boolean runNonDivergentPowerflow(AclfNetwork net) throws Exception {
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);

		algo.getDataCheckConfig().setTurnOffIslandBus(true);
		algo.getDataCheckConfig().setAutoTurnLine2Xfr(true);
		algo.setLfMethod(AclfMethodType.NR);
		algo.setHvdcLfSwitchFactor(5);
		AclfAdjCtrlFunction.disableAllAdjControls.accept(algo);
		algo.getLfAdjAlgo().getLimitCtrlConfig().setCheckGenQLimitImmediate(true);
		algo.getLfAdjAlgo().getLimitCtrlConfig().setLimitBackoffCheck(false);
		algo.getLfAdjAlgo().getVoltAdjConfig().setHvdcTapControl(true);

		NrMethodConfig config = algo.getNrMethodConfig();
		config.setNonDivergent(true);
		config.setOptAlgo(NrOptimizeAlgoType.BINARY_SEARCH);
		algo.getLfCalculator().getNrSolver().reConfigSolver(config);

		algo.setMaxIterations(20);
		algo.setTolerance(0.001);

		return algo.loadflow();
	}

     private boolean runPowerflow(AclfNetwork net) throws Exception {
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);

		algo.getDataCheckConfig().setTurnOffIslandBus(true);
		algo.getDataCheckConfig().setAutoTurnLine2Xfr(true);
		algo.setLfMethod(AclfMethodType.NR);
		algo.setHvdcLfSwitchFactor(5);
		// AclfAdjCtrlFunction.disableAllAdjControls.accept(algo);
		// algo.getLfAdjAlgo().getLimitCtrlConfig().setCheckGenQLimitImmediate(false);
		// algo.getLfAdjAlgo().getLimitCtrlConfig().setLimitBackoffCheck(false);
		// algo.getLfAdjAlgo().getVoltAdjConfig().setHvdcTapControl(true);

		// NrMethodConfig config = algo.getNrMethodConfig();
		// config.setNonDivergent(true);
		// config.setOptAlgo(NrOptimizeAlgoType.BINARY_SEARCH);
		// algo.getLfCalculator().getNrSolver().reConfigSolver(config);

		algo.setMaxIterations(20);
		algo.setTolerance(0.001);

		return algo.loadflow();
	}

	private void assertCase3375WpImportedData(AclfNetwork net) {
		AclfBus bus37 = net.getBus("Bus37");
		AclfBus bus10071 = net.getBus("Bus10071");
		AclfBus bus10118 = net.getBus("Bus10118");
		AclfBus bus10129 = net.getBus("Bus10129");
		AclfBus bus10225 = net.getBus("Bus10225");
		AclfBus bus10247 = net.getBus("Bus10247");
		assertNotNull(bus37);
		assertNotNull(bus10071);
		assertNotNull(bus10118);
		assertNotNull(bus10129);
		assertNotNull(bus10225);
		assertNotNull(bus10247);

		assertTrue(bus37.isSwing());
		assertEquals(0.487, bus37.getLoadP(), DATA_TOL);
		assertEquals(0.862, bus37.getLoadQ(), DATA_TOL);
		assertEquals(1.11, bus37.getVoltageMag(), DATA_TOL);
		assertEquals(0.0, bus37.getVoltageAng(UnitType.Deg), DATA_TOL);

		assertTrue(bus10071.isGenPV());
		assertEquals(0.16, bus10071.getLoadP(), DATA_TOL);
		assertEquals(0.08, bus10071.getLoadQ(), DATA_TOL);
		assertEquals(1.339, bus10071.getGenP(), DATA_TOL);
		assertEquals(0.007, bus10071.getGenQ(), DATA_TOL);
		assertEquals(1.07617, bus10071.getVoltageMag(), DATA_TOL);

		assertTrue(bus10118.isGenPV());
		assertEquals(0.0, bus10118.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus10118.getLoadQ(), DATA_TOL);
		assertEquals(10.8, bus10118.getGenP(), DATA_TOL);
		assertEquals(0.634, bus10118.getGenQ(), DATA_TOL);
		assertEquals(1.04097, bus10118.getVoltageMag(), DATA_TOL);

		assertTrue(bus10129.isGenPV());
		assertEquals(0.002, bus10129.getLoadP(), DATA_TOL);
		assertEquals(1.0, bus10129.getLoadQ(), DATA_TOL);
		assertEquals(16.56, bus10129.getGenP(), DATA_TOL);
		assertEquals(-4.326, bus10129.getGenQ(), DATA_TOL);
		assertEquals(1.04538, bus10129.getVoltageMag(), DATA_TOL);

		assertTrue(bus10225.isGenPV());
		assertEquals(1.957, bus10225.getLoadP(), DATA_TOL);
		assertEquals(1.266, bus10225.getLoadQ(), DATA_TOL);
		assertEquals(0.778, bus10225.getGenP(), DATA_TOL);
		assertEquals(1.318, bus10225.getGenQ(), DATA_TOL);
		assertEquals(1.06248, bus10225.getVoltageMag(), DATA_TOL);

		assertTrue(bus10247.isGenPV());
		assertEquals(0.14, bus10247.getLoadP(), DATA_TOL);
		assertEquals(0.10, bus10247.getLoadQ(), DATA_TOL);
		assertEquals(1.022, bus10247.getGenP(), DATA_TOL);
		assertEquals(-0.017, bus10247.getGenQ(), DATA_TOL);
		assertEquals(1.12, bus10247.getVoltageMag(), DATA_TOL);

		AclfBranch branch1008710088 = net.getBranch("Bus10087", "Bus10088", "1");
		AclfBranch branch1004210232 = net.getBranch("Bus10042", "Bus10232", "1");
		AclfBranch branch1013510134 = net.getBranch("Bus10135", "Bus10134", "1");
		assertNotNull(branch1008710088);
		assertNotNull(branch1004210232);
		assertNotNull(branch1013510134);

		assertTrue(branch1008710088.isXfr());
		assertTrue(NumericUtil.equals(branch1008710088.getZ(), new Complex(0.00035, 0.02784), DATA_TOL));
		assertEquals(0.0, branch1008710088.getRatingMva1(), DATA_TOL);
		assertEquals(0.95238, branch1008710088.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch1008710088.getToTurnRatio(), DATA_TOL);

		assertTrue(branch1004210232.isXfr());
		assertTrue(NumericUtil.equals(branch1004210232.getZ(), new Complex(0.0013, 0.04918), DATA_TOL));
		assertEquals(0.0, branch1004210232.getRatingMva1(), DATA_TOL);
		assertEquals(1.015, branch1004210232.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch1004210232.getToTurnRatio(), DATA_TOL);

		assertTrue(branch1013510134.isPSXfr());
		assertTrue(NumericUtil.equals(branch1013510134.getZ(), new Complex(0.00025, 0.01342), DATA_TOL));
		assertEquals(0.0, branch1013510134.getRatingMva1(), DATA_TOL);
		assertEquals(0.99474, branch1013510134.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch1013510134.getToTurnRatio(), DATA_TOL);
		AclfPSXformerAdapter psXfr = branch1013510134.toPSXfr();
		assertEquals(3.0, psXfr.getFromAngle(UnitType.Deg), DATA_TOL);
	}

	@Test
	public void testCase3375WpImport() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE3375WP_FILE);

		assertNotNull(net);
		assertEquals(3374, net.getNoBus());
		assertEquals(4161, net.getNoBranch());
		assertCase3375WpImportedData(net);
	}

	@Test
	public void testCase3375WpNonDivergentPowerFlow() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE3375WP_FILE);
		net.setPolarCoordinate(false);

		assertCase3375WpImportedData(net);

		boolean solved = runNonDivergentPowerflow(net);
		assertTrue(solved && net.isLfConverged(), AclfOutFunc.loadFlowSummary(net).toString());
	}

    
	@Test
	public void testCase3375WpPowerFlow() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE3375WP_FILE);
		net.setPolarCoordinate(false);

		assertCase3375WpImportedData(net);

		boolean solved = runPowerflow(net);
		assertTrue(solved && net.isLfConverged(), AclfOutFunc.loadFlowSummary(net).toString());
	}
}