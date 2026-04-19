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

public class MatpowerCase9241PegaseTest extends CorePluginTestSetup {
	private static final String CASE9241PEGASE_FILE = "testData/adpter/matpower/case9241pegase.m";
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
		algo.setMaxIterations(20);
		algo.setTolerance(0.001);

		return algo.loadflow();
	}

	private void assertCase9241PegaseImportedData(AclfNetwork net) {
		AclfBus bus4231 = net.getBus("Bus4231");
		AclfBus bus2 = net.getBus("Bus2");
		AclfBus bus6 = net.getBus("Bus6");
		AclfBus bus8 = net.getBus("Bus8");
		AclfBus bus18 = net.getBus("Bus18");
		AclfBus bus107 = net.getBus("Bus107");
		assertNotNull(bus4231);
		assertNotNull(bus2);
		assertNotNull(bus6);
		assertNotNull(bus8);
		assertNotNull(bus18);
		assertNotNull(bus107);

		assertTrue(bus4231.isSwing());
		assertEquals(0.0, bus4231.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus4231.getLoadQ(), DATA_TOL);
		assertEquals(1.042866, bus4231.getVoltageMag(), DATA_TOL);
		assertEquals(0.0, bus4231.getVoltageAng(UnitType.Deg), DATA_TOL);
		assertEquals(26.4124, bus4231.getGenP(), DATA_TOL);
		assertEquals(6.0418, bus4231.getGenQ(), DATA_TOL);

		assertTrue(bus2.isGenPV());
		assertEquals(0.0, bus2.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus2.getLoadQ(), DATA_TOL);
		assertEquals(-0.814, bus2.getGenP(), DATA_TOL);
		assertEquals(0.0664, bus2.getGenQ(), DATA_TOL);
		assertEquals(1.031734, bus2.getVoltageMag(), DATA_TOL);

		assertTrue(bus6.isGenPV());
		assertEquals(0.0, bus6.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus6.getLoadQ(), DATA_TOL);
		assertEquals(7.01, bus6.getGenP(), DATA_TOL);
		assertEquals(1.8379, bus6.getGenQ(), DATA_TOL);
		assertEquals(1.038476, bus6.getVoltageMag(), DATA_TOL);

		assertTrue(bus8.isGenPV());
		assertEquals(0.0, bus8.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus8.getLoadQ(), DATA_TOL);
		assertEquals(4.997, bus8.getGenP(), DATA_TOL);
		assertEquals(-0.0095, bus8.getGenQ(), DATA_TOL);
		assertEquals(1.023005, bus8.getVoltageMag(), DATA_TOL);

		assertTrue(bus18.isGenPV());
		assertEquals(0.0, bus18.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus18.getLoadQ(), DATA_TOL);
		assertEquals(4.675, bus18.getGenP(), DATA_TOL);
		assertEquals(2.0191, bus18.getGenQ(), DATA_TOL);
		assertEquals(1.026728, bus18.getVoltageMag(), DATA_TOL);

		assertTrue(bus107.isGenPV());
		assertEquals(0.0, bus107.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus107.getLoadQ(), DATA_TOL);
		assertEquals(5.7, bus107.getGenP(), DATA_TOL);
		assertEquals(-0.7596, bus107.getGenQ(), DATA_TOL);
		assertEquals(1.083135, bus107.getVoltageMag(), DATA_TOL);

		AclfBranch branch60776929 = net.getBranch("Bus6077", "Bus6929", "1");
		AclfBranch branch22037638 = net.getBranch("Bus2203", "Bus7638", "1");
		AclfBranch branch44637638 = net.getBranch("Bus4463", "Bus7638", "1");
		assertNotNull(branch60776929);
		assertNotNull(branch22037638);
		assertNotNull(branch44637638);

		assertTrue(branch60776929.isXfr());
		assertTrue(NumericUtil.equals(branch60776929.getZ(), new Complex(0.00135, 0.01379), DATA_TOL));
		assertEquals(1711.0, branch60776929.getRatingMva1(), DATA_TOL);
		assertEquals(1.052632, branch60776929.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch60776929.getToTurnRatio(), DATA_TOL);

		assertTrue(branch22037638.isXfr());
		assertTrue(NumericUtil.equals(branch22037638.getZ(), new Complex(0.000342, 0.033341), DATA_TOL));
		assertEquals(724.0, branch22037638.getRatingMva1(), DATA_TOL);
		assertEquals(0.905856, branch22037638.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch22037638.getToTurnRatio(), DATA_TOL);

		assertTrue(branch44637638.isPSXfr());
		assertTrue(NumericUtil.equals(branch44637638.getZ(), new Complex(0.000121, 0.028823), DATA_TOL));
		assertEquals(705.0, branch44637638.getRatingMva1(), DATA_TOL);
		assertEquals(0.953802, branch44637638.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch44637638.getToTurnRatio(), DATA_TOL);
		AclfPSXformerAdapter psXfr44637638 = branch44637638.toPSXfr();
		assertEquals(0.305147, psXfr44637638.getFromAngle(UnitType.Deg), DATA_TOL);
	}

	@Test
	public void testCase9241PegaseImport() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE9241PEGASE_FILE);

		assertNotNull(net);
		assertEquals(9241, net.getNoBus());
		assertEquals(16049, net.getNoBranch());
		assertCase9241PegaseImportedData(net);
	}

	@Test
	public void testCase9241PegaseNonDivergentPowerFlow() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE9241PEGASE_FILE);
		net.setPolarCoordinate(false);

		assertCase9241PegaseImportedData(net);

		boolean solved = runNonDivergentPowerflow(net);
		assertTrue(solved && net.isLfConverged(), AclfOutFunc.loadFlowSummary(net).toString());
	}

	@Test
	public void testCase9241PegasePowerFlow() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE9241PEGASE_FILE);
		net.setPolarCoordinate(false);

		assertCase9241PegaseImportedData(net);

		boolean solved = runPowerflow(net);
		assertTrue(solved && net.isLfConverged(), AclfOutFunc.loadFlowSummary(net).toString());
	}
}