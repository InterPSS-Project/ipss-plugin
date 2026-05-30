package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelDataExtractor;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

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
	}

	@Test
	public void verifiesDistOpfOnGridappsdDistopfOpenDssSmallLinecodeCases() {
		verifyGridappsdDistopfLinecodeCase("test_line_unbal_load");
		verifyGridappsdDistopfLinecodeCase("test_line_unbal_line");
		verifyGridappsdDistopfLinecodeCase("test_line_unbal_load_unbal_line");
	}

	@Test
	public void verifiesDistOpfOnGridappsdDistopfOpenDssGeometryCases() {
		verifyGridappsdDistopfGeometryCase("2Bus", "n2");
		verifyGridappsdDistopfGeometryCase("2BusD", "n2");
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
		assertTrue(powerFlow.powerflow(), caseName);
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
