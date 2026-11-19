package org.interpss.core.adapter.matpower;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.fadapter.matpower.MatpowerDirectParser;
import org.interpss.fadapter.matpower.MatpowerOpfDirectParser;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
	@TempDir Path temporaryDirectory;

	@ParameterizedTest
	@CsvSource({"0.0, 1000.0", "1.0, 1000.0", "230.0, 230000.0"})
	void testNominalVoltagePreservesPerUnitPowerFlow(double baseKv, double expectedBaseVolts) throws Exception {
		String fixture = """
				function mpc = nominal_voltage
				mpc.version = '2';
				mpc.baseMVA = 100;
				mpc.bus = [
				1 3 0 0 0 0 1 1.04 0 %1$.16g 1 1.1 0.9;
				2 2 0 0 0 0 1 1.01 0 %1$.16g 1 1.1 0.9;
				3 1 40 15 0 0 1 1.0 0 %1$.16g 1 1.1 0.9;
				];
				mpc.gen = [
				1 30 0 100 -100 1.04 100 1 200 0;
				2 10 0 100 -100 1.01 100 1 200 0;
				];
				mpc.branch = [
				1 2 0.02 0.15 0.04 100 100 100 0 0 1 -360 360;
				2 3 0.01 0.10 0.02 100 100 100 0 0 1 -360 360;
				];
				""";
		Path input = temporaryDirectory.resolve("nominal-voltage.m");
		Files.writeString(input, String.format(Locale.ROOT, fixture, baseKv));
		AclfNetwork net = loadMatpowerCase(input.toString());
		var opf = new MatpowerOpfDirectParser().parse(input.toString());
		for (int number = 1; number <= 3; number++) {
			assertEquals(expectedBaseVolts, net.getBus("Bus" + number).getBaseVoltage(), 1e-12);
			assertEquals(expectedBaseVolts, opf.getBus("Bus" + number).getBaseVoltage(), 1e-12);
		}
		Path reference = temporaryDirectory.resolve("declared-voltage.m");
		Files.writeString(reference, String.format(Locale.ROOT, fixture, 230.0));
		AclfNetwork referenceNet = loadMatpowerCase(reference.toString());
		var algorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algorithm.getLfAdjAlgo().setApplyAdjustAlgo(false);
		algorithm.setTolerance(1e-10);
		assertTrue(algorithm.loadflow());
		var referenceAlgorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(referenceNet);
		referenceAlgorithm.getLfAdjAlgo().setApplyAdjustAlgo(false);
		referenceAlgorithm.setTolerance(1e-10);
		assertTrue(referenceAlgorithm.loadflow());
		for (int number = 1; number <= 3; number++) {
			assertEquals(0.0, net.getBus("Bus" + number).getVoltage()
					.subtract(referenceNet.getBus("Bus" + number).getVoltage()).abs(), 1e-10);
		}
	}

	@ParameterizedTest
	@CsvSource({
		"0.0, 0.0, 0.08, 1",
		"1.0, 0.0, 0.08, 1",
		"0.9, 0.0, 0.08, 1",
		"1.1, 0.0, 0.08, 1",
		"0.0, 17.0, 0.08, 1",
		"0.92, -13.0, 0.08, 1",
		"1.1, 11.0, -0.04, 1",
		"0.9, 0.0, 0.0, 1",
		"0.0, 0.0, 0.08, 0",
		"0.9, 0.0, 0.08, 0",
		"0.92, -13.0, 0.08, 0"
	})
	void testBranchChargingAdmittanceAndTerminalPower(double ratio, double angle, double charging, int status)
			throws Exception {
		Path input = temporaryDirectory.resolve("charging.m");
		Files.writeString(input, String.format(Locale.ROOT, """
				function mpc = charging
				mpc.version = '2';
				mpc.baseMVA = 100;
				mpc.bus = [
				1 3 0 0 0 0 1 1.04 0 230 1 1.1 0.9;
				2 1 40 15 0 0 1 1.0 0 230 1 1.1 0.9;
				];
				mpc.gen = [
				1 40 15 100 -100 1.04 100 1 200 0;
				];
				mpc.branch = [
				1 2 0.02 0.15 %.16g 100 100 100 %.16g %.16g %d -360 360;
				];
				""", charging, ratio, angle, status));
		AclfNetwork net = loadMatpowerCase(input.toString());
		AclfBranch branch = net.getBranch("Bus1", "Bus2", "1");
		assertEquals(status == 1, branch.isActive());
		assertEquals(0.0, net.getBus("Bus1").getShuntY().abs(), 1e-12);
		assertEquals(0.0, net.getBus("Bus2").getShuntY().abs(), 1e-12);
		double tap = ratio == 0.0 ? 1.0 : ratio;
		assertBranchChargingStamp(branch, tap, angle, charging);
		assertBranchChargingStamp(new MatpowerOpfDirectParser().parse(input.toString())
				.getBranch("Bus1", "Bus2", "1"), tap, angle, charging);
		branch.setStatus(false);
		assertBranchChargingStamp(branch, tap, angle, charging);
		branch.setStatus(true);
		assertBranchChargingStamp(branch, tap, angle, charging);
		if (ratio != 0.0 || angle != 0.0) {
			assertFalse(branch.isFromShuntOnBusSide());
			branch.setFromTurnRatio(1.07);
			assertBranchChargingStamp(branch, 1.07, angle, charging);
		}
	}

	private static void assertBranchChargingStamp(AclfBranch branch, double tap, double angle, double charging) {
		Complex seriesY = new Complex(0.02, 0.15).reciprocal();
		Complex terminalY = seriesY.add(new Complex(0.0, charging / 2.0));
		Complex complexTap = new Complex(Math.cos(Math.toRadians(angle)), Math.sin(Math.toRadians(angle)))
				.multiply(tap);
		Complex yff = terminalY.divide(tap * tap);
		Complex ytt = terminalY;
		Complex yft = seriesY.negate().divide(complexTap.conjugate());
		Complex ytf = seriesY.negate().divide(complexTap);
		if (!branch.isActive()) {
			yff = ytt = yft = ytf = Complex.ZERO;
		}
		assertEquals(0.0, branch.yff().subtract(yff).abs(), 1e-12, "Yff");
		assertEquals(0.0, branch.ytt().subtract(ytt).abs(), 1e-12, "Ytt");
		assertEquals(0.0, branch.yft().subtract(yft).abs(), 1e-12, "Yft");
		assertEquals(0.0, branch.ytf().subtract(ytf).abs(), 1e-12, "Ytf");
		branch.getFromAclfBus().setVoltage(1.04, 0.07);
		branch.getToAclfBus().setVoltage(0.97, -0.04);
		Complex vf = branch.getFromAclfBus().getVoltage();
		Complex vt = branch.getToAclfBus().getVoltage();
		Complex sf = vf.multiply(yff.multiply(vf).add(yft.multiply(vt)).conjugate());
		Complex st = vt.multiply(ytf.multiply(vf).add(ytt.multiply(vt)).conjugate());
		assertEquals(0.0, branch.powerFrom2To().subtract(sf).abs(), 1e-12, "From terminal power");
		assertEquals(0.0, branch.powerTo2From().subtract(st).abs(), 1e-12, "To terminal power");
	}

	private AclfNetwork loadMatpowerCase(String path) throws Exception {
		return CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.MATPOWER)
				.load(path)
				.getAclfNet();
	}

	private String runLoadflowWithDiagnostics(AclfNetwork net, AclfMethodType method) throws Exception {
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setLfMethod(method);
		algo.setInitBusVoltage(method != AclfMethodType.PQ);
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
	public void testMatpowerDirectParserLoadsCase() throws Exception {
		AclfNetwork net = new MatpowerDirectParser().parse(CASE9_FILE);

		assertNotNull(net);
		assertEquals(9, net.getNoBus());
		assertEquals(9, net.getNoBranch());
		assertTrue(net.getBus("Bus1").isSwing());
	}

	@Test
	public void directParserSupportsSparseBusNumbersAboveOneHundredThousand() throws Exception {
		Path caseFile = temporaryDirectory.resolve("case_high_bus_number.m");
		Files.writeString(caseFile, """
				function mpc = case_high_bus_number
				mpc.version = '2';
				mpc.baseMVA = 100;
				mpc.bus = [
				111180 3 0 0 0 0 1 1.0 0 230 1 1.1 0.9;
				];
				mpc.gen = [
				111180 60 0 50 -50 1.0 100 1 80 0;
				111180 20 0 20 -20 1.0 100 1 30 0;
				];
				mpc.branch = [
				];
				""");

		AclfNetwork net = new MatpowerDirectParser().parse(caseFile.toString());

		assertNotNull(net.getBus("Bus111180"));
		assertEquals(2, net.getBus("Bus111180").getContributeGenList().size());
		assertEquals("1", net.getBus("Bus111180").getContributeGenList().get(0).getId());
		assertEquals("2", net.getBus("Bus111180").getContributeGenList().get(1).getId());
	}

	@Test
	public void testCase30PowerFlow() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE30_FILE);
		net.setPolarCoordinate(false);

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
	public void testCase30PqPowerFlow() throws Exception {
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

		String diagnostic = runLoadflowWithDiagnostics(net, AclfMethodType.PQ);
		if (diagnostic != null) {
			fail(diagnostic);
		}
	}

	@Test
	public void testCase118PowerFlow() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE118_FILE);
		net.setPolarCoordinate(false);

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
	public void testCase118PqPowerFlow() throws Exception {
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

		String diagnostic = runLoadflowWithDiagnostics(net, AclfMethodType.PQ);
		if (diagnostic != null) {
			fail(diagnostic);
		}
	}

	@Test
	public void testCase2736SpPowerFlow() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE2736SP_FILE);
		net.setPolarCoordinate(false);

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
