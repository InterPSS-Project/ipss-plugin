package org.interpss.core.adapter.matpower;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfPSXformerAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.datatype.Mismatch;
import com.interpss.core.net.Branch;

public class MatpowerFormatTest extends CorePluginTestSetup {
	private static final String CASE9_FILE = "testData/adpter/matpower/case9.m";
	private static final String CASE30_FILE = "testData/adpter/matpower/case30.m";
	private static final String CASE118_FILE = "testData/adpter/matpower/case118.m";
	private static final String CASE2736SP_FILE = "testData/adpter/matpower/case2736sp.m";
	private static final double DATA_TOL = 1.0E-6;

	private AclfNetwork loadMatpowerCase(String path) throws Exception {
		return CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.MATPOWER)
				.load(path)
				.getAclfNet();
	}

	private String runLoadflowWithDiagnostics(AclfNetwork net, AclfMethodType method) throws Exception {
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setLfMethod(method);
		algo.setInitBusVoltage(true);
		algo.getNrMethodConfig().setNonDivergent(true);
		algo.setMaxIterations(50);

		boolean solved = algo.loadflow();
		if (solved && net.isLfConverged()) {
			return null;
		}

		return buildNonConvergenceReport(net, method, solved);
	}

	private String buildNonConvergenceReport(AclfNetwork net, AclfMethodType method, boolean solved) {
		Mismatch mismatch = net.maxMismatch(method);
		StringBuilder sb = new StringBuilder();
		sb.append("Load flow did not converge for method ")
				.append(method)
				.append(", solved=")
				.append(solved)
				.append(", isLfConverged=")
				.append(net.isLfConverged())
				.append('\n');
		sb.append("Max mismatch: ").append(mismatch).append('\n');
		sb.append(AclfOutFunc.maxMismatchToString(net, "diag "));
		sb.append(AclfOutFunc.loadFlowSummary(net)).append('\n');
		appendBusDiagnostics(sb, (AclfBus) mismatch.maxPBus, method, "maxP");
		if (mismatch.maxQBus != mismatch.maxPBus) {
			appendBusDiagnostics(sb, (AclfBus) mismatch.maxQBus, method, "maxQ");
		}
		return sb.toString();
	}

	private void appendBusDiagnostics(StringBuilder sb, AclfBus bus, AclfMethodType method, String label) {
		if (bus == null) {
			return;
		}

		sb.append(label)
				.append(" bus ")
				.append(bus.getId())
				.append(": mismatch=")
				.append(bus.mismatch(method))
				.append(", vm=")
				.append(bus.getVoltageMag())
				.append(", vaDeg=")
				.append(bus.getVoltageAng(UnitType.Deg))
				.append(", gen=")
				.append(new Complex(bus.getGenP(), bus.getGenQ()))
				.append(", load=")
				.append(new Complex(bus.getLoadP(), bus.getLoadQ()))
				.append(", shunt=")
				.append(bus.getShuntY())
				.append('\n');

		for (Branch branch : bus.getBranchIterable()) {
			if (!(branch instanceof AclfBranch)) {
				continue;
			}

			AclfBranch aclfBranch = (AclfBranch) branch;
			sb.append("  branch ")
					.append(aclfBranch.getId())
					.append(": from=")
					.append(aclfBranch.getFromBus().getId())
					.append(", to=")
					.append(aclfBranch.getToBus().getId())
					.append(", active=")
					.append(aclfBranch.isActive())
					.append(", type=")
					.append(aclfBranch.isPSXfr() ? "PSXFR" : aclfBranch.isXfr() ? "XFR" : "LINE")
					.append(", z=")
					.append(aclfBranch.getZ())
					.append(", rating=")
					.append(aclfBranch.getRatingMva1())
					.append(", turns=")
					.append(aclfBranch.getFromTurnRatio())
					.append("/")
					.append(aclfBranch.getToTurnRatio())
					.append('\n');
		}
	}

	private void assertCase30ImportedData(AclfNetwork net) {
		AclfBus bus1 = net.getBus("Bus1");
		AclfBus bus2 = net.getBus("Bus2");
		AclfBus bus5 = net.getBus("Bus5");
		AclfBus bus13 = net.getBus("Bus13");
		AclfBus bus24 = net.getBus("Bus24");
		assertNotNull(bus1);
		assertNotNull(bus2);
		assertNotNull(bus5);
		assertNotNull(bus13);
		assertNotNull(bus24);

		assertEquals(1.0, bus1.getVoltageMag(), DATA_TOL);
		assertEquals(0.0, bus1.getVoltageAng(UnitType.Deg), DATA_TOL);
		assertEquals(0.2354, bus1.getGenP(), DATA_TOL);
		assertEquals(0.0, bus1.getGenQ(), DATA_TOL);

		assertEquals(0.217, bus2.getLoadP(), DATA_TOL);
		assertEquals(0.127, bus2.getLoadQ(), DATA_TOL);
		assertEquals(0.6097, bus2.getGenP(), DATA_TOL);

		assertEquals(0.0, bus5.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus5.getLoadQ(), DATA_TOL);
		assertNotNull(bus5.getShuntY());
		assertEquals(0.0019, bus5.getShuntY().getImaginary(), DATA_TOL);

		assertEquals(0.37, bus13.getGenP(), DATA_TOL);
		assertEquals(1.0, bus13.getVoltageMag(), DATA_TOL);
		assertEquals(0.0, bus13.getVoltageAng(UnitType.Deg), DATA_TOL);

		assertEquals(0.087, bus24.getLoadP(), DATA_TOL);
		assertEquals(0.067, bus24.getLoadQ(), DATA_TOL);
		assertNotNull(bus24.getShuntY());
		assertEquals(0.0004, bus24.getShuntY().getImaginary(), DATA_TOL);

		assertTrue(NumericUtil.equals(net.getBranch("Bus1", "Bus2", "1").getZ(), new Complex(0.02, 0.06), DATA_TOL));
		assertEquals(130.0, net.getBranch("Bus1", "Bus2", "1").getRatingMva1(), DATA_TOL);
		assertTrue(NumericUtil.equals(net.getBranch("Bus6", "Bus9", "1").getZ(), new Complex(0.0, 0.21), DATA_TOL));
		assertEquals(65.0, net.getBranch("Bus6", "Bus9", "1").getRatingMva1(), DATA_TOL);
		assertTrue(NumericUtil.equals(net.getBranch("Bus6", "Bus28", "1").getZ(), new Complex(0.02, 0.06), DATA_TOL));
	}

	private void assertCase118ImportedData(AclfNetwork net) {
		AclfBus bus1 = net.getBus("Bus1");
		AclfBus bus5 = net.getBus("Bus5");
		AclfBus bus69 = net.getBus("Bus69");
		AclfBus bus116 = net.getBus("Bus116");
		assertNotNull(bus1);
		assertNotNull(bus5);
		assertNotNull(bus69);
		assertNotNull(bus116);

		assertEquals(0.51, bus1.getLoadP(), DATA_TOL);
		assertEquals(0.27, bus1.getLoadQ(), DATA_TOL);
		assertEquals(0.955, bus1.getVoltageMag(), DATA_TOL);
		assertEquals(10.67, bus1.getVoltageAng(UnitType.Deg), DATA_TOL);

		assertNotNull(bus5.getShuntY());
		assertEquals(-0.4, bus5.getShuntY().getImaginary(), DATA_TOL);

		assertTrue(bus69.isSwing());
		assertEquals(1.035, bus69.getVoltageMag(), DATA_TOL);
		assertEquals(30.0, bus69.getVoltageAng(UnitType.Deg), DATA_TOL);

		assertEquals(1.84, bus116.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus116.getLoadQ(), DATA_TOL);

		AclfBranch branch12 = net.getBranch("Bus1", "Bus2", "1");
		AclfBranch branch8687 = net.getBranch("Bus86", "Bus87", "1");
		AclfBranch branch68116 = net.getBranch("Bus68", "Bus116", "1");
		assertNotNull(branch12);
		assertNotNull(branch8687);
		assertNotNull(branch68116);

		assertTrue(NumericUtil.equals(branch12.getZ(), new Complex(0.0303, 0.0999), DATA_TOL));
		assertTrue(branch8687.isXfr());
		assertEquals(1.0, branch8687.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch8687.getToTurnRatio(), DATA_TOL);
		assertTrue(branch68116.isXfr());
		assertEquals(1.0, branch68116.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch68116.getToTurnRatio(), DATA_TOL);
	}

	private void assertCase2736SpImportedData(AclfNetwork net) {
		AclfBus bus1 = net.getBus("Bus1");
		AclfBus bus26 = net.getBus("Bus26");
		AclfBus bus28 = net.getBus("Bus28");
		AclfBus bus77 = net.getBus("Bus77");
		AclfBus bus133 = net.getBus("Bus133");
		assertNotNull(bus1);
		assertNotNull(bus26);
		assertNotNull(bus28);
		assertNotNull(bus77);
		assertNotNull(bus133);

		assertEquals(1.1010186, bus1.getVoltageMag(), DATA_TOL);
		assertEquals(36.775787, bus1.getVoltageAng(UnitType.Deg), DATA_TOL);
		assertEquals(0.0, bus1.getLoadP(), DATA_TOL);
		assertEquals(0.0, bus1.getLoadQ(), DATA_TOL);

		assertTrue(bus26.isGenPV());
		assertEquals(0.61358, bus26.getLoadP(), DATA_TOL);
		assertEquals(0.72054, bus26.getLoadQ(), DATA_TOL);
		assertEquals(1.11, bus26.getVoltageMag(), DATA_TOL);

		assertTrue(bus28.isSwing());
		assertEquals(0.49, bus28.getLoadP(), DATA_TOL);
		assertEquals(0.74, bus28.getLoadQ(), DATA_TOL);

		assertTrue(bus77.isGenPV());
		assertEquals(0.27, bus77.getLoadP(), DATA_TOL);
		assertEquals(0.70, bus77.getLoadQ(), DATA_TOL);
		assertEquals(1.1017006, bus77.getVoltageMag(), DATA_TOL);

		assertTrue(bus133.isGenPV());
		assertEquals(0.11215, bus133.getLoadP(), DATA_TOL);
		assertEquals(0.083, bus133.getLoadQ(), DATA_TOL);
		assertEquals(1.11, bus133.getVoltageMag(), DATA_TOL);

		AclfBranch branch78 = net.getBranch("Bus7", "Bus8", "1");
		AclfBranch branch2628 = net.getBranch("Bus26", "Bus28", "1");
		assertNotNull(branch78);
		assertNotNull(branch2628);

		assertTrue(branch78.isPSXfr());
		assertTrue(NumericUtil.equals(branch78.getZ(), new Complex(0.00064, 0.0305), DATA_TOL));
		assertEquals(400.0, branch78.getRatingMva1(), DATA_TOL);
		assertEquals(1.0435, branch78.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch78.getToTurnRatio(), DATA_TOL);
		AclfPSXformerAdapter psXfr78 = branch78.toPSXfr();
		assertEquals(0.6, psXfr78.getFromAngle(UnitType.Deg), DATA_TOL);

		assertTrue(branch2628.isXfr());
		assertTrue(NumericUtil.equals(branch2628.getZ(), new Complex(0.00036, 0.02519), DATA_TOL));
		assertEquals(500.0, branch2628.getRatingMva1(), DATA_TOL);
		assertEquals(1.067, branch2628.getFromTurnRatio(), DATA_TOL);
		assertEquals(1.0, branch2628.getToTurnRatio(), DATA_TOL);
	}

	@Test
	public void testCorePluginFactoryLoadsMatpowerCase() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE9_FILE);

		assertNotNull(net);
		assertEquals(9, net.getNoBus());
		assertEquals(9, net.getNoBranch());

		AclfBus bus1 = net.getBus("Bus1");
		AclfBus bus2 = net.getBus("Bus2");
		AclfBus bus5 = net.getBus("Bus5");
		assertNotNull(bus1);
		assertNotNull(bus2);
		assertNotNull(bus5);
		assertTrue(bus1.isSwing());
		assertTrue(bus2.isGenPV());
		assertTrue(bus5.isLoad());
	}

	@Test
	public void testIpssAdapterDslLoadsMatpowerCase() throws Exception {
		AclfNetwork net = IpssAdapter
				.importAclfNet(CASE9_FILE)
				.setFormat(IpssAdapter.FileFormat.MATPOWER)
				.load()
				.getImportedObj();

		assertNotNull(net);
		assertEquals(9, net.getNoBus());
		assertEquals(9, net.getNoBranch());
		assertTrue(net.getBus("Bus1").isSwing());
	}

	@Test
	public void testCase30PowerFlow() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE30_FILE);

		assertNotNull(net);
		assertEquals(30, net.getNoBus());
		assertEquals(41, net.getNoBranch());

		AclfBus bus1 = net.getBus("Bus1");
		AclfBus bus13 = net.getBus("Bus13");
		AclfBus bus5 = net.getBus("Bus5");
		assertNotNull(bus1);
		assertNotNull(bus13);
		assertNotNull(bus5);
		assertTrue(bus1.isSwing());
		assertTrue(bus13.isGenPV());
		assertNotNull(bus5.getShuntY());
		assertCase30ImportedData(net);

		String diagnostic = runLoadflowWithDiagnostics(net, AclfMethodType.NR);
		if (diagnostic != null) {
			fail(diagnostic);
		}
	}

	@Test
	public void testCase118PowerFlow() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE118_FILE);

		assertNotNull(net);
		assertEquals(118, net.getNoBus());
		assertEquals(186, net.getNoBranch());

		AclfBus bus1 = net.getBus("Bus1");
		AclfBus bus5 = net.getBus("Bus5");
		AclfBus bus69 = net.getBus("Bus69");
		assertNotNull(bus1);
		assertNotNull(bus5);
		assertNotNull(bus69);
		assertTrue(bus69.isSwing());
		assertNotNull(bus5.getShuntY());
		assertCase118ImportedData(net);

		String diagnostic = runLoadflowWithDiagnostics(net, AclfMethodType.NR);
		if (diagnostic != null) {
			fail(diagnostic);
		}
	}

	@Test
	public void testCase2736SpPowerFlow() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE2736SP_FILE);

		assertNotNull(net);
		assertEquals(2736, net.getNoBus());
		assertEquals(3504, net.getNoBranch());

		AclfBus bus28 = net.getBus("Bus28");
		AclfBus bus77 = net.getBus("Bus77");
		AclfBus bus133 = net.getBus("Bus133");
		assertNotNull(bus28);
		assertNotNull(bus77);
		assertNotNull(bus133);
		assertTrue(bus28.isSwing());
		assertTrue(bus77.isGenPV());
		assertTrue(bus133.isGenPV());
		assertCase2736SpImportedData(net);

		String diagnostic = runLoadflowWithDiagnostics(net, AclfMethodType.NR);
		if (diagnostic != null) {
			fail(diagnostic);
		}
	}

}