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
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.NrMethodConfig;
import com.interpss.core.algo.NrOptimizeAlgoType;
import com.interpss.core.funcImpl.AclfAdjCtrlFunction;

public class MatpowerCase3012WpTest extends CorePluginTestSetup {
	private static final String CASE3012WP_FILE = "testData/adpter/matpower/case3012wp.m";
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
		algo.setTolerance(0.005);

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
		algo.setTolerance(0.005);

		return algo.loadflow();
	}

	private void assertCase3012WpImportedData(AclfNetwork net) {
		AclfBus bus24 = net.getBus("Bus24");
		AclfBus bus37 = net.getBus("Bus37");
		AclfBus bus122 = net.getBus("Bus122");
		AclfBus bus208 = net.getBus("Bus208");
		AclfBus bus244 = net.getBus("Bus244");
		AclfBus bus234 = net.getBus("Bus234");
		assertNotNull(bus24);
		assertNotNull(bus37);
		assertNotNull(bus122);
		assertNotNull(bus208);
		assertNotNull(bus244);
		assertNotNull(bus234);

		assertTrue(bus24.isGenPV());
		assertEquals(0.1005, bus24.getLoadP(), DATA_TOL);
		assertEquals(0.2010, bus24.getLoadQ(), DATA_TOL);
		assertEquals(1.10, bus24.getGenP(), DATA_TOL);
		assertEquals(0.8519, bus24.getGenQ(), DATA_TOL);
		assertEquals(1.08673, bus24.getVoltageMag(), DATA_TOL);

		assertTrue(bus37.isSwing());
		assertEquals(0.4868, bus37.getLoadP(), DATA_TOL);
		assertEquals(0.8621, bus37.getLoadQ(), DATA_TOL);
		assertEquals(7.40, bus37.getGenP(), DATA_TOL);
		assertEquals(1.4934, bus37.getGenQ(), DATA_TOL);
		assertEquals(1.11, bus37.getVoltageMag(), DATA_TOL);
		assertEquals(0.0, bus37.getVoltageAng(UnitType.Deg), DATA_TOL);

		assertTrue(bus122.isGenPV());
		assertEquals(0.1005, bus122.getLoadP(), DATA_TOL);
		assertEquals(0.4421, bus122.getLoadQ(), DATA_TOL);
		assertEquals(2.25, bus122.getGenP(), DATA_TOL);
		assertEquals(0.6133, bus122.getGenQ(), DATA_TOL);
		assertEquals(1.11, bus122.getVoltageMag(), DATA_TOL);

		assertTrue(bus208.isGenPV());
		assertEquals(0.0314, bus208.getLoadP(), DATA_TOL);
		assertEquals(0.0151, bus208.getLoadQ(), DATA_TOL);
		assertEquals(1.88, bus208.getGenP(), DATA_TOL);
		assertEquals(1.0, bus208.getGenQ(), DATA_TOL);

		assertTrue(bus244.isGenPV());
		assertEquals(0.0, bus244.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus244.getLoadQ(), DATA_TOL);
		assertEquals(2.20, bus244.getGenP(), DATA_TOL);
		assertEquals(0.1007, bus244.getGenQ(), DATA_TOL);
		assertEquals(1.11, bus244.getVoltageMag(), DATA_TOL);

		assertEquals(0.0, bus234.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus234.getLoadQ(), DATA_TOL);
		assertNotNull(bus234.getShuntY());
		assertEquals(0.7, bus234.getShuntY().getImaginary(), DATA_TOL);

		AclfBranch branch911 = net.getBranch("Bus9", "Bus11", "1");
		AclfBranch branch3740 = net.getBranch("Bus37", "Bus40", "1");
		AclfBranch branch5556 = net.getBranch("Bus55", "Bus56", "1");
		assertNotNull(branch911);
		assertNotNull(branch3740);
		assertNotNull(branch5556);

		assertTrue(branch911.isXfr());
		assertTrue(NumericUtil.equals(branch911.getZ(), new Complex(0.00064, 0.0305), DATA_TOL));
		assertEquals(400.0, branch911.getRatingMva1(), DATA_TOL);
		assertEquals(1.10224, branch911.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch911.getToTurnRatio(), DATA_TOL);

		assertTrue(branch3740.isXfr());
		assertTrue(NumericUtil.equals(branch3740.getZ(), new Complex(0.00036, 0.02519), DATA_TOL));
		assertEquals(500.0, branch3740.getRatingMva1(), DATA_TOL);
		assertEquals(1.0686, branch3740.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch3740.getToTurnRatio(), DATA_TOL);

		assertTrue(branch5556.isXfr());
		assertTrue(NumericUtil.equals(branch5556.getZ(), new Complex(0.00034, 0.02097), DATA_TOL));
		assertEquals(500.0, branch5556.getRatingMva1(), DATA_TOL);
		assertEquals(1.0755, branch5556.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch5556.getToTurnRatio(), DATA_TOL);
	}

	@Test
	public void testCase3012WpImport() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE3012WP_FILE);

		assertNotNull(net);
		assertEquals(3012, net.getNoBus());
		assertEquals(3572, net.getNoBranch());
		assertCase3012WpImportedData(net);
	}

	@Test
	public void testCase3012WpNonDivergentPowerFlow() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE3012WP_FILE);
		net.setPolarCoordinate(false);

		assertCase3012WpImportedData(net);

		boolean solved = runNonDivergentPowerflow(net);
		assertTrue(solved && net.isLfConverged(), AclfOutFunc.loadFlowSummary(net).toString());
	}

    @Test
	public void testCase3012WpPowerFlow() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE3012WP_FILE);
		net.setPolarCoordinate(false);

		assertCase3012WpImportedData(net);

		boolean solved = runPowerflow(net);
		assertTrue(solved && net.isLfConverged(), AclfOutFunc.loadFlowSummary(net).toString());
	}
}