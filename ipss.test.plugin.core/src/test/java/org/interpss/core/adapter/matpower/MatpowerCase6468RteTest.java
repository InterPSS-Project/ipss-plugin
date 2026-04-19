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

public class MatpowerCase6468RteTest extends CorePluginTestSetup {
	private static final String CASE6468RTE_FILE = "testData/adpter/matpower/case6468rte.m";
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

	private void assertCase6468RteImportedData(AclfNetwork net) {
		AclfBus bus57 = net.getBus("Bus57");
		AclfBus bus102 = net.getBus("Bus102");
		AclfBus bus144 = net.getBus("Bus144");
		AclfBus bus153 = net.getBus("Bus153");
		AclfBus bus4736 = net.getBus("Bus4736");
		assertNotNull(bus57);
		assertNotNull(bus102);
		assertNotNull(bus144);
		assertNotNull(bus153);
		assertNotNull(bus4736);

		assertTrue(bus4736.isSwing());
		assertEquals(0.0, bus4736.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus4736.getLoadQ(), DATA_TOL);
		assertEquals(1.0604, bus4736.getVoltageMag(), DATA_TOL);
		assertEquals(1.88592643, bus4736.getVoltageAng(UnitType.Deg), DATA_TOL);
		assertEquals(0.0, bus4736.getGenP(), DATA_TOL);
		assertEquals(0.0, bus4736.getGenQ(), DATA_TOL);

		assertTrue(bus57.isGenPV());
		assertEquals(0.0, bus57.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus57.getLoadQ(), DATA_TOL);
		assertEquals(0.0, bus57.getGenP(), DATA_TOL);
		assertEquals(0.01, bus57.getGenQ(), DATA_TOL);
		assertEquals(1.059, bus57.getVoltageMag(), DATA_TOL);

		assertTrue(bus102.isGenPV());
		assertEquals(0.0, bus102.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus102.getLoadQ(), DATA_TOL);
		assertEquals(0.1034, bus102.getGenP(), DATA_TOL);
		assertEquals(-0.0482, bus102.getGenQ(), DATA_TOL);
		assertEquals(1.02, bus102.getVoltageMag(), DATA_TOL);

		assertEquals(0.0, bus144.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus144.getLoadQ(), DATA_TOL);
		assertNotNull(bus144.getShuntY());
		assertEquals(-0.64, bus144.getShuntY().getImaginary(), DATA_TOL);

		assertEquals(0.342, bus153.getLoadP(), DATA_TOL);
		assertEquals(0.059, bus153.getLoadQ(), DATA_TOL);
		assertNotNull(bus153.getShuntY());
		assertEquals(0.077, bus153.getShuntY().getImaginary(), DATA_TOL);

		AclfBranch branch47354736 = net.getBranch("Bus4735", "Bus4736", "1");
		AclfBranch branch60336229 = net.getBranch("Bus6033", "Bus6229", "1");
		AclfBranch branch62356205 = net.getBranch("Bus6235", "Bus6205", "1");
		assertNotNull(branch47354736);
		assertNotNull(branch60336229);
		assertNotNull(branch62356205);

		assertTrue(branch47354736.isXfr());
		assertTrue(NumericUtil.equals(branch47354736.getZ(), new Complex(0.000208, 0.016413), DATA_TOL));
		assertEquals(1769.0, branch47354736.getRatingMva1(), DATA_TOL);
		assertEquals(0.947871, branch47354736.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch47354736.getToTurnRatio(), DATA_TOL);

		assertTrue(branch60336229.isPSXfr());
		assertTrue(NumericUtil.equals(branch60336229.getZ(), new Complex(0.000198, 0.004938), DATA_TOL));
		assertEquals(0.0, branch60336229.getRatingMva1(), DATA_TOL);
		assertEquals(1.126727, branch60336229.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch60336229.getToTurnRatio(), DATA_TOL);
		AclfPSXformerAdapter psXfr60336229 = branch60336229.toPSXfr();
		assertEquals(-6.75, psXfr60336229.getFromAngle(UnitType.Deg), DATA_TOL);

		assertTrue(branch62356205.isPSXfr());
		assertTrue(NumericUtil.equals(branch62356205.getZ(), new Complex(0.000062, 0.00675), DATA_TOL));
		assertEquals(1629.0, branch62356205.getRatingMva1(), DATA_TOL);
		assertEquals(1.133606, branch62356205.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch62356205.getToTurnRatio(), DATA_TOL);
		AclfPSXformerAdapter psXfr62356205 = branch62356205.toPSXfr();
		assertEquals(-15.01, psXfr62356205.getFromAngle(UnitType.Deg), DATA_TOL);
	}

	@Test
	public void testCase6468RteImport() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE6468RTE_FILE);

		assertNotNull(net);
		assertEquals(6468, net.getNoBus());
		assertEquals(9000, net.getNoBranch());
		assertCase6468RteImportedData(net);
	}

	@Test
	public void testCase6468RteNonDivergentPowerFlow() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE6468RTE_FILE);
		net.setPolarCoordinate(false);

		assertCase6468RteImportedData(net);

		boolean solved = runNonDivergentPowerflow(net);
		assertTrue(solved && net.isLfConverged(), AclfOutFunc.loadFlowSummary(net).toString());
	}
}