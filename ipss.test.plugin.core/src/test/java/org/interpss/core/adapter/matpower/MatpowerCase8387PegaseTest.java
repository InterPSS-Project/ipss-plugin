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

public class MatpowerCase8387PegaseTest extends CorePluginTestSetup {
	private static final String CASE8387PEGASE_FILE = "testData/adpter/matpower/case8387pegase.m";
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

	private void assertCase8387PegaseImportedData(AclfNetwork net) {
		AclfBus bus3853 = net.getBus("Bus3853");
		AclfBus bus5 = net.getBus("Bus5");
		AclfBus bus14 = net.getBus("Bus14");
		AclfBus bus43 = net.getBus("Bus43");
		AclfBus bus96 = net.getBus("Bus96");
		AclfBus bus110 = net.getBus("Bus110");
		assertNotNull(bus3853);
		assertNotNull(bus5);
		assertNotNull(bus14);
		assertNotNull(bus43);
		assertNotNull(bus96);
		assertNotNull(bus110);

		assertTrue(bus3853.isSwing());
		assertEquals(0.0, bus3853.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus3853.getLoadQ(), DATA_TOL);
		assertEquals(1.083974, bus3853.getVoltageMag(), DATA_TOL);
		assertEquals(0.0, bus3853.getVoltageAng(UnitType.Deg), DATA_TOL);
		assertEquals(26.4124, bus3853.getGenP(), DATA_TOL);
		assertEquals(4.3557, bus3853.getGenQ(), DATA_TOL);

		assertTrue(bus5.isGenPV());
		assertEquals(3.434, bus5.getLoadP(), DATA_TOL);
		assertEquals(0.531, bus5.getLoadQ(), DATA_TOL);
		assertNotNull(bus5.getShuntY());
		assertEquals(0.2, bus5.getShuntY().getImaginary(), DATA_TOL);
		assertEquals(10.444, bus5.getGenP(), DATA_TOL);
		assertEquals(1.404, bus5.getGenQ(), DATA_TOL);
		assertEquals(1.060105, bus5.getVoltageMag(), DATA_TOL);

		assertTrue(bus14.isGenPV());
		assertEquals(0.0, bus14.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus14.getLoadQ(), DATA_TOL);
		assertEquals(4.675, bus14.getGenP(), DATA_TOL);
		assertEquals(1.04, bus14.getGenQ(), DATA_TOL);
		assertEquals(1.040474, bus14.getVoltageMag(), DATA_TOL);

		assertTrue(bus43.isGenPV());
		assertEquals(1.518, bus43.getLoadP(), DATA_TOL);
		assertEquals(0.483, bus43.getLoadQ(), DATA_TOL);
		assertEquals(0.073, bus43.getGenP(), DATA_TOL);
		assertEquals(0.018, bus43.getGenQ(), DATA_TOL);
		assertEquals(1.026, bus43.getVoltageMag(), DATA_TOL);

		assertTrue(bus96.isGenPV());
		assertEquals(0.0, bus96.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus96.getLoadQ(), DATA_TOL);
		assertEquals(5.7, bus96.getGenP(), DATA_TOL);
		assertEquals(0.102, bus96.getGenQ(), DATA_TOL);
		assertEquals(1.103763, bus96.getVoltageMag(), DATA_TOL);

		assertTrue(bus110.isGenPV());
		assertEquals(0.0, bus110.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus110.getLoadQ(), DATA_TOL);
		assertEquals(8.613, bus110.getGenP(), DATA_TOL);
		assertEquals(1.123, bus110.getGenQ(), DATA_TOL);
		assertEquals(1.066, bus110.getVoltageMag(), DATA_TOL);

		AclfBranch branch12166469 = net.getBranch("Bus1216", "Bus6469", "1");
		AclfBranch branch74865134 = net.getBranch("Bus7486", "Bus5134", "1");
		AclfBranch branch40616931 = net.getBranch("Bus4061", "Bus6931", "1");
		assertNotNull(branch12166469);
		assertNotNull(branch74865134);
		assertNotNull(branch40616931);

		assertTrue(branch12166469.isXfr());
		assertTrue(NumericUtil.equals(branch12166469.getZ(), new Complex(0.006356, 0.142878), DATA_TOL));
		assertEquals(119.999944, branch12166469.getRatingMva1(), DATA_TOL);
		assertEquals(1.026667, branch12166469.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch12166469.getToTurnRatio(), DATA_TOL);

		assertTrue(branch74865134.isXfr());
		assertTrue(NumericUtil.equals(branch74865134.getZ(), new Complex(0.001002, 0.059033), DATA_TOL));
		assertEquals(119.999909, branch74865134.getRatingMva1(), DATA_TOL);
		assertEquals(1.052632, branch74865134.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch74865134.getToTurnRatio(), DATA_TOL);

		assertTrue(branch40616931.isPSXfr());
		assertTrue(NumericUtil.equals(branch40616931.getZ(), new Complex(0.000121, 0.025184), DATA_TOL));
		assertEquals(720.000011, branch40616931.getRatingMva1(), DATA_TOL);
		assertEquals(0.966268, branch40616931.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch40616931.getToTurnRatio(), DATA_TOL);
		AclfPSXformerAdapter psXfr40616931 = branch40616931.toPSXfr();
		assertEquals(14.9238, psXfr40616931.getFromAngle(UnitType.Deg), DATA_TOL);
	}

	@Test
	public void testCase8387PegaseImport() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE8387PEGASE_FILE);

		assertNotNull(net);
		assertEquals(8387, net.getNoBus());
		assertEquals(14561, net.getNoBranch());
		assertCase8387PegaseImportedData(net);
	}

	@Test
	public void testCase8387PegaseNonDivergentPowerFlow() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE8387PEGASE_FILE);
		net.setPolarCoordinate(false);

		assertCase8387PegaseImportedData(net);

		boolean solved = runNonDivergentPowerflow(net);
		assertTrue(solved && net.isLfConverged(), AclfOutFunc.loadFlowSummary(net).toString());
	}

	@Test
	public void testCase8387PegasePowerFlow() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE8387PEGASE_FILE);
		net.setPolarCoordinate(false);

		assertCase8387PegaseImportedData(net);

		boolean solved = runPowerflow(net);
		assertTrue(solved && net.isLfConverged(), AclfOutFunc.loadFlowSummary(net).toString());
	}
}