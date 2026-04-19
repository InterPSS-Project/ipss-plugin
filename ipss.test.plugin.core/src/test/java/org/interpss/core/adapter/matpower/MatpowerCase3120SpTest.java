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

public class MatpowerCase3120SpTest extends CorePluginTestSetup {
	private static final String CASE3120SP_FILE = "testData/adpter/matpower/case3120sp.m";
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

	private void assertCase3120SpImportedData(AclfNetwork net) {
		AclfBus bus22 = net.getBus("Bus22");
		AclfBus bus23 = net.getBus("Bus23");
		AclfBus bus37 = net.getBus("Bus37");
		AclfBus bus96 = net.getBus("Bus96");
		AclfBus bus230 = net.getBus("Bus230");
		AclfBus bus321 = net.getBus("Bus321");
		assertNotNull(bus22);
		assertNotNull(bus23);
		assertNotNull(bus37);
		assertNotNull(bus96);
		assertNotNull(bus230);
		assertNotNull(bus321);

		assertTrue(bus22.isGenPV());
		assertEquals(0.10, bus22.getLoadP(), DATA_TOL);
		assertEquals(0.20, bus22.getLoadQ(), DATA_TOL);
		assertEquals(1.80, bus22.getGenP(), DATA_TOL);
		assertEquals(0.2285, bus22.getGenQ(), DATA_TOL);
		assertEquals(1.0, bus22.getVoltageMag(), DATA_TOL);

		assertTrue(bus23.isGenPV());
		assertEquals(0.10, bus23.getLoadP(), DATA_TOL);
		assertEquals(0.20, bus23.getLoadQ(), DATA_TOL);
		assertEquals(1.80, bus23.getGenP(), DATA_TOL);
		assertEquals(0.3593, bus23.getGenQ(), DATA_TOL);
		assertEquals(1.0, bus23.getVoltageMag(), DATA_TOL);
        assertEquals(1.0, bus23.getVoltageMag(), DATA_TOL);
        assertEquals(1.06818, bus23.getDesiredVoltMag(), DATA_TOL);

		assertTrue(bus37.isSwing());
		assertEquals(0.60, bus37.getLoadP(), DATA_TOL);
		assertEquals(1.20, bus37.getLoadQ(), DATA_TOL);
		assertEquals(10.50, bus37.getGenP(), DATA_TOL);
		assertEquals(1.0476, bus37.getGenQ(), DATA_TOL);
		assertEquals(1.04, bus37.getVoltageMag(), DATA_TOL);
		assertEquals(0.0, bus37.getVoltageAng(UnitType.Deg), DATA_TOL);

		assertTrue(bus96.isGenPV());
		assertEquals(0.32, bus96.getLoadP(), DATA_TOL);
		assertEquals(1.42, bus96.getLoadQ(), DATA_TOL);
		assertEquals(7.40, bus96.getGenP(), DATA_TOL);
		assertEquals(0.7270, bus96.getGenQ(), DATA_TOL);
		assertEquals(1.0, bus96.getVoltageMag(), DATA_TOL);

		assertEquals(0.0, bus230.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus230.getLoadQ(), DATA_TOL);
		assertNotNull(bus230.getShuntY());
		assertEquals(0.7, bus230.getShuntY().getImaginary(), DATA_TOL);

		assertEquals(0.0, bus321.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus321.getLoadQ(), DATA_TOL);
		assertNotNull(bus321.getShuntY());
		assertEquals(0.45, bus321.getShuntY().getImaginary(), DATA_TOL);

		AclfBranch branch911 = net.getBranch("Bus9", "Bus11", "1");
		AclfBranch branch3537 = net.getBranch("Bus35", "Bus37", "1");
		AclfBranch branch5354 = net.getBranch("Bus53", "Bus54", "1");
		assertNotNull(branch911);
		assertNotNull(branch3537);
		assertNotNull(branch5354);

		assertTrue(branch911.isXfr());
		assertTrue(NumericUtil.equals(branch911.getZ(), new Complex(0.00064, 0.0305), DATA_TOL));
		assertEquals(400.0, branch911.getRatingMva1(), DATA_TOL);
		assertEquals(1.09271, branch911.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch911.getToTurnRatio(), DATA_TOL);

		assertTrue(branch3537.isXfr());
		assertTrue(NumericUtil.equals(branch3537.getZ(), new Complex(0.00036, 0.02519), DATA_TOL));
		assertEquals(500.0, branch3537.getRatingMva1(), DATA_TOL);
		assertEquals(1.07313, branch3537.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch3537.getToTurnRatio(), DATA_TOL);

		assertTrue(branch5354.isXfr());
		assertTrue(NumericUtil.equals(branch5354.getZ(), new Complex(0.00034, 0.02097), DATA_TOL));
		assertEquals(500.0, branch5354.getRatingMva1(), DATA_TOL);
		assertEquals(1.0755, branch5354.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch5354.getToTurnRatio(), DATA_TOL);
	}

	@Test
	public void testCase3120SpImport() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE3120SP_FILE);

		assertNotNull(net);
		assertEquals(3120, net.getNoBus());
		assertEquals(3693, net.getNoBranch());
		assertCase3120SpImportedData(net);
	}

	@Test
	public void testCase3120SpNonDivergentPowerFlow() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE3120SP_FILE);
		net.setPolarCoordinate(false);

		assertCase3120SpImportedData(net);

		boolean solved = runNonDivergentPowerflow(net);
		assertTrue(solved && net.isLfConverged(), AclfOutFunc.loadFlowSummary(net).toString());
	}
}