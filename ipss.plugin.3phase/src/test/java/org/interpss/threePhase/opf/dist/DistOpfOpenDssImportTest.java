package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		DStabNetwork3Phase distNet = openDssNetwork(
				"testData/feeder/DistOPFGridappsdDss/test_line",
				"main-InterPSS.dss");

		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setTolerance(1.0e-4);
		assertTrue(powerFlow.powerflow());
		DStab3PBus loadBus = distNet.getBus("3");
		assertTrue(loadBus.get3PhaseVotlages().absMax() > 0.75);

		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(distNet)
				.setOptions(relaxedOrToolsOptions())
				.solve();

		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(Boolean.TRUE, result.getPowerFlowConverged());
		assertTrue(result.getPowerFlowIterationCount() > 0);
		assertTrue(result.getMaxPowerFlowVoltageViolation() < 1.0e-6);
		assertTrue(result.getMaxPowerFlowBranchLimitViolation() < 1.0e-6);
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

	private static DistOpfOptions relaxedOrToolsOptions() {
		return new DistOpfOptions()
				.setSolverType(DistOpfSolverType.ORTOOLS)
				.setMinVoltagePu(0.0)
				.setMaxVoltagePu(2.0)
				.setPowerFlowTolerance(1.0e-4);
	}
}
