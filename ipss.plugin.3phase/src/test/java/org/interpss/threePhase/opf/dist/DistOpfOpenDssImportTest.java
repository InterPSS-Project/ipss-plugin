package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.dstab.DStab1PLoad;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelDataExtractor;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfOpenDssImportTest {

	@Test
	public void extractsExistingOpenDssIeee123Feeder() {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.parseFeederData("testData/feeder/IEEE123", "IEEE123Master_Modified_v2.dss");
		parser.calcVoltageBases();
		parser.convertActualValuesToPU(1.0);

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		DistOpfModelData data = new DistOpfModelDataExtractor().extract(distNet);

		assertEquals("150", data.getSwingBusId());
		assertTrue(data.getBuses().size() > 100);
		assertTrue(data.getBranches().size() > 100);
		assertEquals(data.getBuses().size() - 1, data.getBranches().size());
	}

	@Test
	public void extractsExistingOpenDssIeee13FeederForDistOpf() {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.parseFeederData("testData/feeder/IEEE13", "IEEE13Nodeckt.dss");
		parser.calcVoltageBases();
		parser.convertActualValuesToPU(1.0);

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		DistOpfModelData data = new DistOpfModelDataExtractor().extract(distNet);

		assertTrue(data.getBuses().size() >= 13);
		assertEquals(data.getBuses().size() - 1, data.getBranches().size());
	}

	@Test
	public void verifiesDistOpfOnOpenDssTwoBusWithFixedPointPowerFlow() {
		DStabNetwork3Phase distNet = openDssNetwork("testData/feeder/DistOPF2Bus", "DistOPF2Bus.dss");

		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setTolerance(1.0e-6);
		assertTrue(powerFlow.powerflow());
		DStab3PBus loadBus = distNet.getBus("loadbus");
		assertTrue(loadBus.get3PhaseVotlages().absMax() > 0.9);

		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(distNet)
				.setOptions(relaxedOrToolsOptions())
				.solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(Boolean.TRUE, result.getPowerFlowConverged());
		assertTrue(result.getPowerFlowIterationCount() > 0);
		assertTrue(result.getMaxPowerFlowVoltageViolation() < 1.0e-6);
		assertTrue(result.getMaxPowerFlowBranchLimitViolation() < 1.0e-6);
	}

	@Test
	public void verifiesDistOpfOnOpenDssIeee123WithFixedPointPowerFlow() {
		OpenDSSDataParser parser = openDssParser("testData/feeder/IEEE123", "IEEE123Master_Modified_v2.dss");
		parser.getBranchByName("reg1a").setToTurnRatio(1.0438);
		DStabNetwork3Phase distNet = parser.getDistNetwork();

		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setTolerance(1.0e-4);
		assertTrue(powerFlow.powerflow());
		DStab3PBus bus150r = distNet.getBus("150r");
		assertTrue(Math.abs(bus150r.get3PhaseVotlages().a_0.abs() - 1.0437) < 1.0e-3);

		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(distNet)
				.setOptions(relaxedOrToolsOptions())
				.solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(Boolean.TRUE, result.getPowerFlowConverged());
		assertTrue(result.getPowerFlowIterationCount() > 0);
		assertTrue(result.getMaxPowerFlowVoltageViolation() < 1.0e-6);
		assertTrue(result.getMaxPowerFlowBranchLimitViolation() < 1.0e-6);
	}

	@Test
	public void verifiesDistOpfOnGridappsdDistopfOpenDssTestLine() {
		verifyGridappsdDistopfLinecodeCase("test_line");

		DStabNetwork3Phase distNet = openDssNetwork(
				"testData/feeder/DistOPFGridappsdDss/test_line",
				"main-InterPSS.dss");
		Complex3x3 expected = gridappsdTestLinePu();
		assertTrue(branchByName(distNet, "12").getZabc().subtract(expected).absMax() < 1.0e-10);
		assertTrue(branchByName(distNet, "23").getZabc().subtract(expected).absMax() < 1.0e-10);
	}

	@Test
	public void verifiesDistOpfOnGridappsdDistopfOpenDssSmallLinecodeCases() {
		verifyGridappsdDistopfLinecodeCase("test_line_unbal_load");
		verifyGridappsdDistopfLinecodeCase("test_line_unbal_line");
		verifyGridappsdDistopfLinecodeCase("test_line_unbal_load_unbal_line");
	}

	@Test
	public void importsGridappsdDistopfRegulatorCaseWithFixedPointPowerFlow() {
		DStabNetwork3Phase distNet = openDssNetwork(
				"testData/feeder/DistOPFGridappsdDss/test_reg",
				"main-InterPSS.dss");

		assertEquals(PhaseCode.A, branchByName(distNet, "reg1").getPhaseCode());
		assertEquals(PhaseCode.B, branchByName(distNet, "reg2").getPhaseCode());
		assertEquals(PhaseCode.C, branchByName(distNet, "reg3").getPhaseCode());
		assertEquals(122.0 * 20.0 * Math.sqrt(3.0) / 4160.0,
				branchByName(distNet, "reg1").getToTurnRatio(), 1.0e-6);

		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setTolerance(1.0e-4);
		assertTrue(powerFlow.powerflow());
		assertTrue(distNet.getBus("3").get3PhaseVotlages().absMax() > 0.75);

		DistOpfModelData data = new DistOpfModelDataExtractor().extract(distNet);
		assertEquals(4.998, data.getBranches().get(0).getThermalLimitPu(), 1.0e-6);

		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(distNet)
				.setOptions(relaxedOrToolsOptions())
				.solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(Boolean.TRUE, result.getPowerFlowConverged());
		assertTrue(result.getMaxPowerFlowVoltageViolation() < 1.0e-6);
	}

	@Test
	public void verifiesDistOpfOnGridappsdDistopfOpenDssGeometryCases() {
		verifyGridappsdDistopfGeometryCase("2Bus", "n2");
		verifyGridappsdDistopfGeometryCase("2BusD", "n2");
		verifyGridappsdDistopfGeometryCase("2Bus_1phase", "n2");
	}

	@Test
	public void verifiesGridappsdDistopfThreeBusGeometryImportAgainstOpenDssReference() {
		DStabNetwork3Phase distNet = openDssNetwork(
				"testData/feeder/DistOPFGridappsdDss/3Bus",
				"main-InterPSS.dss");

		DistOpfModelData data = new DistOpfModelDataExtractor().extract(distNet);
		assertEquals("sourcebus", data.getSwingBusId());
		assertEquals(2, data.getBranches().size());

		DStab3PBranch line2 = (DStab3PBranch) distNet.getBranch("n2->n3(1)");
		Complex3x3 expected = gridappsdFourBusGeometryReferenceLine2Pu().multiply(1.0 / 3.0);
		assertTrue(line2.getZabc().subtract(expected).absMax() < 1.0e-6);
	}

	@Test
	public void importsGridappsdDistopfFourBusYyTransformerCase() {
		DStabNetwork3Phase distNet = openDssNetwork(
				"testData/feeder/DistOPFGridappsdDss/4Bus-YY-Bal",
				"main-InterPSS.dss");

		DistOpfModelData data = new DistOpfModelDataExtractor().extract(distNet);
		assertEquals("sourcebus", data.getSwingBusId());
		assertEquals(3, data.getBranches().size());

		Complex expectedTransformerZ = new Complex(1.0 / 600.0, 1.0 / 100.0);
		Complex actualTransformerZ = branchByName(distNet, "t1").getZ();
		assertEquals(expectedTransformerZ.getReal(), actualTransformerZ.getReal(), 1.0e-10);
		assertEquals(expectedTransformerZ.getImaginary(), actualTransformerZ.getImaginary(), 1.0e-10);
		assertEquals(0.75,
				((DStab1PLoad) distNet.getBus("n4").getThreePhaseLoadList().get(0)).getVminpu(),
				1.0e-12);

		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setTolerance(1.0e-4);
		assertTrue(powerFlow.powerflow());

		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(distNet)
				.setOptions(relaxedOrToolsOptions())
				.solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(Boolean.TRUE, result.getPowerFlowConverged());
	}

	private static DStabNetwork3Phase openDssNetwork(String folderPath, String feederFile) {
		return openDssParser(folderPath, feederFile).getDistNetwork();
	}

	private static OpenDSSDataParser openDssParser(String folderPath, String feederFile) {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.parseFeederData(folderPath, feederFile);
		parser.calcVoltageBases();
		parser.convertActualValuesToPU(1.0);
		return parser;
	}

	private static void verifyGridappsdDistopfLinecodeCase(String caseName) {
		DStabNetwork3Phase distNet = openDssNetwork(
				"testData/feeder/DistOPFGridappsdDss/" + caseName,
				"main-InterPSS.dss");

		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setTolerance(1.0e-4);
		boolean converged = powerFlow.powerflow();
		if (!converged && caseName.equals("4Bus-YY-Bal")) {
			distNet.getBusList().forEach(bus -> System.out.println(bus.getId() + " base=" + bus.getBaseVoltage()
					+ " v=" + ((DStab3PBus) bus).get3PhaseVotlages()));
			distNet.getBranchList().forEach(branch -> System.out.println(branch.getId() + " name=" + branch.getName()
					+ " z=" + ((DStab3PBranch) branch).getZ()
					+ " ratios=" + ((DStab3PBranch) branch).getFromTurnRatio()
					+ "," + ((DStab3PBranch) branch).getToTurnRatio()));
		}
		assertTrue(converged, caseName);
		DStab3PBus loadBus = distNet.getBus("3");
		assertTrue(loadBus.get3PhaseVotlages().absMax() > 0.75, caseName);

		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(distNet)
				.setOptions(relaxedOrToolsOptions())
				.solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus(), caseName);
		assertEquals(Boolean.TRUE, result.getPowerFlowConverged(), caseName);
		assertTrue(result.getPowerFlowIterationCount() > 0, caseName);
		assertTrue(result.getMaxPowerFlowVoltageViolation() < 1.0e-6, caseName);
		assertTrue(result.getMaxPowerFlowBranchLimitViolation() < 1.0e-6, caseName);
	}

	private static void verifyGridappsdDistopfGeometryCase(String caseName, String loadBusId) {
		DStabNetwork3Phase distNet = openDssNetwork(
				"testData/feeder/DistOPFGridappsdDss/" + caseName,
				"main-InterPSS.dss");

		DistOpfModelData data = new DistOpfModelDataExtractor().extract(distNet);
		assertEquals("sourcebus", data.getSwingBusId(), caseName);
		assertTrue(data.getBranches().size() >= 1, caseName);

		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setTolerance(1.0e-4);
		assertTrue(powerFlow.powerflow(), caseName);
		DStab3PBus loadBus = distNet.getBus(loadBusId);
		assertTrue(loadBus.get3PhaseVotlages().absMax() > 0.70, caseName);

		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(distNet)
				.setOptions(relaxedOrToolsOptions())
				.solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus(), caseName);
		assertEquals(Boolean.TRUE, result.getPowerFlowConverged(), caseName);
		assertTrue(result.getPowerFlowIterationCount() > 0, caseName);
		assertTrue(result.getMaxPowerFlowVoltageViolation() < 1.0e-6, caseName);
		assertTrue(result.getMaxPowerFlowBranchLimitViolation() < 1.0e-6, caseName);
	}

	private static DistOpfOptions relaxedOrToolsOptions() {
		return new DistOpfOptions()
				.setSolverType(DistOpfSolverType.ORTOOLS)
				.setMinVoltagePu(0.0)
				.setMaxVoltagePu(2.0)
				.setPowerFlowTolerance(1.0e-4);
	}

	private static DStab3PBranch branchByName(DStabNetwork3Phase distNet, String branchName) {
		return (DStab3PBranch) distNet.getBranchList().stream()
				.filter(branch -> branchName.equals(branch.getName()))
				.findFirst()
				.orElseThrow();
	}

	private static Complex3x3 gridappsdTestLinePu() {
		double lengthMi = 2000.0 / 5280.0;
		double zBase = 4.16 * 4.16;
		Complex self = new Complex(0.3, 1.0).multiply(lengthMi / zBase);
		Complex mutual = new Complex(0.1, 0.5).multiply(lengthMi / zBase);
		Complex3x3 zabc = new Complex3x3();
		zabc.aa = self;
		zabc.ab = mutual;
		zabc.ac = mutual;
		zabc.ba = mutual;
		zabc.bb = self;
		zabc.bc = mutual;
		zabc.ca = mutual;
		zabc.cb = mutual;
		zabc.cc = self;
		return zabc;
	}

	private static Complex3x3 gridappsdFourBusGeometryReferenceLine2Pu() {
		Complex3x3 zabc = new Complex3x3();
		zabc.aa = new Complex(0.037555348629970634, 0.08848522434789394);
		zabc.ab = new Complex(0.012799734119631217, 0.041176539792255815);
		zabc.ac = new Complex(0.012597392964900048, 0.0315943165225697);
		zabc.ba = zabc.ab;
		zabc.bb = new Complex(0.03830033198138786, 0.08603346172797469);
		zabc.bc = new Complex(0.012968503211444628, 0.034772109690860135);
		zabc.ca = zabc.ac;
		zabc.cb = zabc.bc;
		zabc.cc = new Complex(0.03787720145332743, 0.0874201326245422);
		return zabc;
	}
}
