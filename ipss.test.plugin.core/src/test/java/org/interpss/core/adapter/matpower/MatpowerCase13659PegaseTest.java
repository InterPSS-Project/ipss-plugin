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

public class MatpowerCase13659PegaseTest extends CorePluginTestSetup {
	private static final String CASE13659PEGASE_FILE = "testData/adpter/matpower/case13659pegase.m";
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

	private void assertCase13659PegaseImportedData(AclfNetwork net) {
		AclfBus bus1 = net.getBus("Bus1");
		AclfBus bus12 = net.getBus("Bus12");
		AclfBus bus105 = net.getBus("Bus105");
		AclfBus bus208 = net.getBus("Bus208");
		AclfBus bus234 = net.getBus("Bus234");
		AclfBus bus258 = net.getBus("Bus258");
		assertNotNull(bus1);
		assertNotNull(bus12);
		assertNotNull(bus105);
		assertNotNull(bus208);
		assertNotNull(bus234);
		assertNotNull(bus258);

		assertTrue(bus1.isSwing());
		assertEquals(1.031695, bus1.getVoltageMag(), DATA_TOL);
		assertEquals(0.0, bus1.getVoltageAng(UnitType.Deg), DATA_TOL);
		assertEquals(0.4233, bus1.getGenP(), DATA_TOL);
		assertEquals(0.1572, bus1.getGenQ(), DATA_TOL);

		assertTrue(bus12.isGenPV());
		assertEquals(1.004054, bus12.getVoltageMag(), DATA_TOL);
		assertEquals(96.865589, bus12.getVoltageAng(UnitType.Deg), DATA_TOL);
		assertEquals(2.3858, bus12.getGenP(), DATA_TOL);
		assertEquals(-0.0409, bus12.getGenQ(), DATA_TOL);

		assertTrue(bus105.isLoad());
		assertEquals(0.135, bus105.getLoadP(), DATA_TOL);
		assertEquals(0.148, bus105.getLoadQ(), DATA_TOL);
		assertNotNull(bus105.getShuntY());
		assertEquals(1.32435168, bus105.getShuntY().getImaginary(), DATA_TOL);

		assertTrue(bus208.isLoad());
		assertEquals(2.696, bus208.getLoadP(), DATA_TOL);
		assertEquals(0.657, bus208.getLoadQ(), DATA_TOL);
		assertEquals(1.094867, bus208.getVoltageMag(), DATA_TOL);

		assertTrue(bus234.isLoad());
		assertEquals(-5.9744, bus234.getLoadP(), DATA_TOL);
		assertEquals(-3.2686, bus234.getLoadQ(), DATA_TOL);

		assertTrue(bus258.isGenPV());
		assertEquals(0.955439, bus258.getVoltageMag(), DATA_TOL);
		assertEquals(48.700819, bus258.getVoltageAng(UnitType.Deg), DATA_TOL);
		assertEquals(0.082, bus258.getGenP(), DATA_TOL);
		assertEquals(-0.0256, bus258.getGenQ(), DATA_TOL);

		AclfBranch branch885910123 = net.getBranch("Bus8859", "Bus10123", "1");
		AclfBranch branch7565756 = net.getBranch("Bus7565", "Bus756", "1");
		assertNotNull(branch885910123);
		assertNotNull(branch7565756);

		assertTrue(branch885910123.isXfr());
		assertTrue(NumericUtil.equals(branch885910123.getZ(), new Complex(0.00135, 0.01379), DATA_TOL));
		assertEquals(1.052632, branch885910123.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch885910123.getToTurnRatio(), DATA_TOL);

		assertTrue(branch7565756.isPSXfr());
		assertTrue(NumericUtil.equals(branch7565756.getZ(), new Complex(0.000454, 0.028947), DATA_TOL));
		assertEquals(1.013237, branch7565756.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch7565756.getToTurnRatio(), DATA_TOL);
		AclfPSXformerAdapter psXfr7565756 = branch7565756.toPSXfr();
		assertEquals(-0.010791, psXfr7565756.getFromAngle(UnitType.Deg), DATA_TOL);
	}

	@Test
	public void testCase13659PegaseImport() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE13659PEGASE_FILE);

		assertNotNull(net);
		assertEquals(13659, net.getNoBus());
		assertEquals(20467, net.getNoBranch());

		AclfBus bus1 = net.getBus("Bus1");
		AclfBus bus12 = net.getBus("Bus12");
		AclfBus bus258 = net.getBus("Bus258");
		assertNotNull(bus1);
		assertNotNull(bus12);
		assertNotNull(bus258);
		assertTrue(bus1.isSwing());
		assertTrue(bus12.isGenPV());
		assertTrue(bus258.isGenPV());
		assertCase13659PegaseImportedData(net);
	}

	@Test
	public void testCase13659PegaseNonDivergentPowerFlow() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE13659PEGASE_FILE);
		net.setPolarCoordinate(false);

		assertCase13659PegaseImportedData(net);

		boolean solved = runNonDivergentPowerflow(net);
		assertTrue(solved && net.isLfConverged(), AclfOutFunc.loadFlowSummary(net).toString());
	}
}