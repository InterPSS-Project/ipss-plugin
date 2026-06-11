package org.interpss.threePhase.system;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.dstab.DStab1PLoad;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.OpenDSSStaticDataParser;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.powerflow.DistributionPFMethod;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.qa.OpenDssDataQaUtils;
import org.interpss.threePhase.qa.OpenDssDataQaUtils.ComparisonResult;
import org.interpss.threePhase.qa.OpenDssDataQaUtils.VoltageReference;
import org.interpss.threePhase.powerflow.control.RegulatorControlData;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.threephase.IBus3Phase;
import com.interpss.core.threephase.IBranch3Phase;
import com.interpss.core.threephase.AclfLoad3Phase;
import com.interpss.core.threephase.Static3PBus;
import com.interpss.core.threephase.Static3PBranch;
import com.interpss.core.threephase.Static3PNetwork;
import com.interpss.core.net.Branch;
import org.interpss.IpssCorePlugin;
import org.interpss.core.sparse.solver.klusolvex.KlusolveXPerformanceCounters;


public class OpenDssParserPowerFlowComparisonTest {
	private static final double VOLTAGE_MAG_TOLERANCE_PU = 2.0e-2;
	private static final double VOLTAGE_ANGLE_TOLERANCE_DEG = 2.0;

	@Test
	public void ieee13ParserImportsUpstreamFeeder() throws IOException {
		ComparisonResult result = assertMatchesDssPythonReference(
				"testData/feeder/IEEE13",
				"IEEE13Nodeckt.dss",
				"opendss-reference/ieee13-dss-python-voltage-reference.csv",
				VOLTAGE_MAG_TOLERANCE_PU,
				OpenDssTapProfile.IEEE13_SOLVED);
		System.out.println(result.summary("IEEE13"));
	}

	@Test
	public void ieee123ParserPowerFlowMatchesDssPythonReference() throws IOException {
		ComparisonResult result = assertMatchesDssPythonReference(
				"testData/feeder/IEEE123",
				"IEEE123Master.dss",
				"opendss-reference/ieee123-dss-python-voltage-reference.csv",
				2.0e-3,
				OpenDssTapProfile.IEEE123_DSS_PYTHON);
		System.out.println(result.summary("IEEE123"));
	}

	@Test
	public void ieee123RegulatorTapControlImprovesDssPythonReferenceMismatch() throws IOException {
		OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
		assertTrue(parser.parseFeederData("testData/feeder/IEEE123", "IEEE123Master.dss"));
		assertTrue(parser.getRegulatorParser().getRegControlCount() >= 7,
				"IEEE123 RegControl records should be parsed");
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));
		List<RegulatorControlData> controls = parser.getRegulatorControls();
		assertTrue(controls.size() >= 7, "IEEE123 regulator controls should be available to power flow");

		Static3PNetwork distNet = parser.getStaticNetwork();
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(200);
		powerFlow.setTolerance(1.0e-4);
		powerFlow.setRegulatorControls(controls);
		powerFlow.setRegulatorControlEnabled(true);
		assertTrue(powerFlow.powerflow(), "Power flow with regulator tap controls failed, iterations="
				+ powerFlow.getIterationCount());
		List<VoltageReference> references = readReferences("opendss-reference/ieee123-dss-python-voltage-reference.csv");
		ComparisonResult result = compareVoltages(distNet, references);
		System.out.println(result.summary("IEEE123 regulator control"));
		assertTrue(result.maxMagError < 5.0e-2,
				"Max voltage magnitude error " + result.maxMagError + " at " + result.maxMagLabel);
		assertTrue(result.maxAngleError < VOLTAGE_ANGLE_TOLERANCE_DEG,
				"Max voltage angle error " + result.maxAngleError + " deg at " + result.maxAngleLabel);
	}

	@Test
	public void ieee8500ParserPowerFlowMatchesDssPythonReference() throws IOException {
		ComparisonResult result = assertMatchesDssPythonReference(
				"testData/feeder/IEEE8500",
				"Master-InterPSS.dss",
				"opendss-reference/ieee8500-controls-off-dss-python-voltage-reference.csv",
				8.0e-2,
				false,
				DistributionPFMethod.Fixed_Point,
				false);
		System.out.println(result.summary("IEEE8500"));
	}

	@Test
	public void ckt7ParserPowerFlowMatchesDssPythonReference() throws IOException {
		ComparisonResult result = assertMatchesDssPythonReference(
				"testData/feeder/Ckt7",
				"Master_ckt7.dss",
				"opendss-reference/ckt7-controls-off-dss-python-voltage-reference.csv",
				8.0e-2,
				false,
				DistributionPFMethod.Fixed_Point,
				false);
		System.out.println(result.summary("Ckt7"));
	}

	@Test
	@Disabled("FBS diagnostic only: IEEE8500 active comparison uses fixed-point")
	public void ieee8500PowerFlowConvergesWithRegControlsDisabled() throws IOException {
		assertIeee8500PowerFlowConvergesWithRegControlsDisabled(DistributionPFMethod.Forward_Backword_Sweep);
	}

	@Test
	public void ieee8500FixedPointConvergesWithRegControlsDisabled() throws IOException {
		assertIeee8500PowerFlowConvergesWithRegControlsDisabled(DistributionPFMethod.Fixed_Point);
	}

	@Test
	@Disabled("Diagnostic only: checks whether IEEE8500 DSS mismatch is fixed-point tolerance limited")
	public void ieee8500FixedPointToleranceSensitivityDiagnostic() throws IOException {
		List<VoltageReference> references = readReferences(
				"opendss-reference/ieee8500-controls-off-dss-python-voltage-reference.csv");
		for(double tolerance : new double[] {1.0e-4, 1.0e-6, 1.0e-8}) {
			OpenDSSDataParser parser = new OpenDSSDataParser();
			parser.setRegControlEnabled(false);
			assertTrue(parser.parseFeederData("testData/feeder/IEEE8500", "Master-InterPSS.dss"));
			assertTrue(parser.calcVoltageBases());
			assertTrue(parser.convertActualValuesToPU(1.0));

			DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(parser.getDistNetwork());
			powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
			powerFlow.setInitBusVoltageEnabled(true);
			powerFlow.setMaxIteration(1000);
			powerFlow.setTolerance(tolerance);
			assertTrue(powerFlow.powerflow(), "Power flow failed at tolerance=" + tolerance
					+ ", iterations=" + powerFlow.getIterationCount());
			ComparisonResult result = compareVoltages(parser.getDistNetwork(), references);
			System.out.println("IEEE8500 fixed-point tolerance=" + tolerance
					+ ", iterations=" + powerFlow.getIterationCount()
					+ ", " + result.summary("IEEE8500"));
		}
	}

	@Test
	@Disabled("Diagnostic only: exports solved IEEE8500 InterPSS load powers/currents for DSS-Python comparison")
	public void ieee8500InterpssSolvedLoadExportDiagnostic() throws IOException {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/IEEE8500", "Master-InterPSS.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(parser.getDistNetwork());
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(1000);
		powerFlow.setTolerance(1.0e-8);
		assertTrue(powerFlow.powerflow(), "IEEE8500 fixed-point failed, iterations="
				+ powerFlow.getIterationCount());

		Path output = Path.of("target", "load-comparison", "interpss-ieee8500-loads.csv");
		Files.createDirectories(output.getParent());
		Files.writeString(output, interpssLoadCsv(parser.getDistNetwork()), StandardCharsets.UTF_8);
		System.out.println("Wrote InterPSS load export: " + output.toAbsolutePath());
	}

	@Test
	@Disabled("Diagnostic only: exports solved IEEE8500 InterPSS branch powers/currents for DSS-Python comparison")
	public void ieee8500InterpssSolvedBranchExportDiagnostic() throws IOException {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/IEEE8500", "Master-InterPSS.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(parser.getDistNetwork());
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(1000);
		powerFlow.setTolerance(1.0e-8);
		assertTrue(powerFlow.powerflow(), "IEEE8500 fixed-point failed, iterations="
				+ powerFlow.getIterationCount());

		Path output = Path.of("target", "load-comparison", "interpss-ieee8500-branches.csv");
		Files.createDirectories(output.getParent());
		Files.writeString(output, interpssBranchCsv(parser.getDistNetwork()), StandardCharsets.UTF_8);
		System.out.println("Wrote InterPSS branch export: " + output.toAbsolutePath());
	}

	@Test
	public void ieee8500YMatrixComponentAudit() throws IOException {
		OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/IEEE8500", "Master-InterPSS.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		printStaticYMatrixComponentAudit("IEEE8500", parser.getStaticNetwork());
	}

	@Test
	@Disabled("Diagnostic only: prints Ckt24 floating phase components for Y-matrix investigation")
	public void ckt24YMatrixComponentAudit() throws IOException {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/Ckt24", "master_ckt24_interpss.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		printYMatrixComponentAudit("Ckt24", parser.getDistNetwork());
	}

	@Test
	@Disabled("Diagnostic only: exports solved Ckt24 InterPSS load powers/currents for DSS-Python comparison")
	public void ckt24InterpssSolvedLoadExportDiagnostic() throws IOException {
		DStabNetwork3Phase distNet = solveCkt24FixedPoint();
		Path output = Path.of("target", "load-comparison", "interpss-ckt24-loads.csv");
		Files.createDirectories(output.getParent());
		Files.writeString(output, interpssLoadCsv(distNet), StandardCharsets.UTF_8);
		System.out.println("Wrote Ckt24 InterPSS load export: " + output.toAbsolutePath());
	}

	@Test
	@Disabled("Diagnostic only: exports solved Ckt24 InterPSS branch powers/currents for DSS-Python comparison")
	public void ckt24InterpssSolvedBranchExportDiagnostic() throws IOException {
		DStabNetwork3Phase distNet = solveCkt24FixedPoint();
		Path output = Path.of("target", "load-comparison", "interpss-ckt24-branches.csv");
		Files.createDirectories(output.getParent());
		Files.writeString(output, interpssBranchCsv(distNet), StandardCharsets.UTF_8);
		System.out.println("Wrote Ckt24 InterPSS branch export: " + output.toAbsolutePath());
	}

	@Test
	@Disabled("Diagnostic only: prints Ckt24 local service-device Y/current/KCL data")
	public void ckt24WorstServiceDeviceDiagnostic() throws IOException {
		DStabNetwork3Phase distNet = solveCkt24FixedPoint();
		printIncidentBranches(distNet, "n300463");
		printIncidentBranches(distNet, "g2100nj7400_n300463_sec");
		printIncidentBranches(distNet, "g2100nj7400_n300463_sec_1");
		printPhysicalBranchDiagnostic(distNet, "05410_g2100nj7400");
		printPhysicalBranchDiagnostic(distNet, "g2100nj7400_n300463_sec_1");
	}

	@Test
	public void ckt24ParserTurnsOffSwingDisconnectedIslands() throws IOException {
		OpenDSSStaticDataParser parser = parseStaticOpenDss(
				"testData/feeder/Ckt24", "master_ckt24_interpss.dss", false);

		Static3PNetwork distNet = parser.getStaticNetwork();
		assertEquals(0, distNet.getBusList().stream()
				.filter(bus -> bus.isActive() && bus.getBaseVoltage() <= 0.0)
				.count(), "Active Ckt24 buses must have source-derived voltage bases");
		assertEquals(0, distNet.getBranchList().stream()
				.filter(branch -> branch.isActive()
						&& (!branch.getFromBus().isActive() || !branch.getToBus().isActive()))
				.count(), "Active Ckt24 branches must not connect to inactive island buses");
	}

	@Test
	public void ckt24BusbarLineUsesMinimumPuImpedance() throws IOException {
		OpenDSSStaticDataParser parser = parseStaticOpenDss(
				"testData/feeder/Ckt24", "master_ckt24_interpss.dss", false);

		Static3PBranch busbar = findStaticBranchByName(parser.getStaticNetwork(), "fdr_05410");
		assertNotNull(busbar, "Missing Ckt24 busbar branch");
		double busbarZAbs = busbar.getZabc().absMax();
		assertTrue(busbar.getZabc().absMax() > 1.0e-8,
				"Ckt24 busbar line should stay above the static solver inversion tolerance: zAbs="
						+ busbarZAbs + ", zaa=" + busbar.getZabc().aa
						+ ", zbb=" + busbar.getZabc().bb + ", zcc=" + busbar.getZabc().cc);
		assertTrue(busbar.getZabc().absMax() < 1.0e-5,
				"Ckt24 busbar line should remain a near-zero OpenDSS connectivity stub");
	}

	@Test
	public void ckt24MultiPhaseLineCanUseSinglePhaseLineCode() throws IOException {
		OpenDSSStaticDataParser parser = parseStaticOpenDss(
				"testData/feeder/Ckt24", "master_ckt24_interpss.dss", false);

		Static3PBranch branch = findStaticBranchByName(parser.getStaticNetwork(), "05410_93062ug");
		assertNotNull(branch, "Missing Ckt24 multi-phase line with one-phase linecode");
		assertTrue(getPhaseValue(branch.getZabc(), 0, 0).abs() > 1.0e-8,
				"phase A should be populated");
		assertTrue(getPhaseValue(branch.getZabc(), 1, 1).abs() > 1.0e-8,
				"phase B should be populated");
		assertTrue(getPhaseValue(branch.getZabc(), 2, 2).abs() > 1.0e-8,
				"phase C should be populated");
	}

	@Test
	public void ckt24TriplexServiceLineMatchesDssKronReducedImpedance() throws IOException {
		OpenDSSStaticDataParser parser = parseStaticOpenDss(
				"testData/feeder/Ckt24", "master_ckt24_interpss.dss", false);

		Static3PBranch branch = findStaticBranchByName(parser.getStaticNetwork(), "g2100nj7400_n300463_sec_1");
		assertNotNull(branch, "Missing Ckt24 triplex service branch");
		double baseVoltage = branch.getToBus().getBaseVoltage();
		double zBase = baseVoltage * baseVoltage / (parser.getStaticNetwork().getBaseKva() * 1000.0);
		Complex actualOhm = branch.getZabc().aa.multiply(zBase);
		assertEquals(0.20348571067718263 * 0.150, actualOhm.getReal(), 1.0e-9,
				"Ckt24 2/0_2/0 service-line R should match DSS Kron-reduced linecode");
		assertEquals(0.06809402900259268 * 0.150, actualOhm.getImaginary(), 1.0e-9,
				"Ckt24 2/0_2/0 service-line X should match DSS Kron-reduced linecode");
	}

	@Test
	public void ckt24LoadWithoutKwUsesOpenDssDefaultKw() throws IOException {
		OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/Ckt24", "master_ckt24_interpss.dss"));

		Static3PBus bus = parser.getStaticNetwork().getBus("g2102aa7100_n284314_sec_1");
		assertNotNull(bus, "Missing Ckt24 load-default bus");
		AclfLoad3Phase load = bus.getPhaseLoadList().stream()
				.filter(candidate -> "440273200".equals(candidate.getId()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing Ckt24 OpenDSS default-kW load"));
		assertEquals(10.0, load.getLoadCP().getReal(), 1.0e-9,
				"OpenDSS defaults missing kW to 10 even when xfkVA=0");
		assertEquals(10.0 * Math.tan(Math.acos(0.98)), load.getLoadCP().getImaginary(), 1.0e-9,
				"OpenDSS computes missing kvar from default kW and pf");
	}

	@Test
	public void ckt24StepTransformerParsesPercentRsAsSeriesResistance() throws IOException {
		OpenDSSStaticDataParser parser = parseStaticOpenDss(
				"testData/feeder/Ckt24", "master_ckt24_interpss.dss", false);

		Static3PBranch transformer = findStaticBranchByName(parser.getStaticNetwork(), "step_05410_g2101cd0200");
		assertNotNull(transformer, "Missing Ckt24 step transformer with %rs data");
		Complex zPhaseB = transformer.getZabc().bb;
		assertEquals(0.00946080416900833, zPhaseB.getReal(), 1.0e-12,
				"Ckt24 step transformer %rs=(0.4725,0.4725) should produce nonzero series R");
		assertEquals(0.04059636074637965, zPhaseB.getImaginary(), 1.0e-12,
				"Ckt24 step transformer xhl should stay unchanged while parsing %rs");
	}

	@Test
	public void ckt24CircuitSourceImpedanceCreatesTheveninBranch() throws IOException {
		OpenDSSStaticDataParser parser = parseStaticOpenDss(
				"testData/feeder/Ckt24", "master_ckt24_interpss.dss", false);

		Static3PNetwork distNet = parser.getStaticNetwork();
		Static3PBus idealSourceBus = distNet.getBus("sourcebus_vsource");
		Static3PBus sourceBus = distNet.getBus("sourcebus");
		assertNotNull(idealSourceBus, "Circuit source impedance should create an internal ideal source bus");
		assertNotNull(sourceBus, "Ckt24 source bus should remain the DSS-reported terminal bus");
		assertTrue(idealSourceBus.isSwing(), "Internal ideal source bus should be the swing bus");
		assertTrue(!sourceBus.isSwing(), "DSS sourcebus should be downstream of the Vsource impedance");

		Static3PBranch sourceBranch = findStaticBranchByName(distNet, "vsource_sourcebus");
		assertNotNull(sourceBranch, "Missing parsed Ckt24 Vsource Thevenin branch");
		double zBase = 230.0 * 230.0;
		assertEquals(1.7766666666666666 / zBase, sourceBranch.getZabc().aa.getReal(), 1.0e-12,
				"Ckt24 Vsource positive/zero sequence impedance should map to phase self R");
		assertEquals(9.663333333333334 / zBase, sourceBranch.getZabc().aa.getImaginary(), 1.0e-12,
				"Ckt24 Vsource positive/zero sequence impedance should map to phase self X");
		assertEquals(1.1466666666666667 / zBase, sourceBranch.getZabc().ab.getReal(), 1.0e-12,
				"Ckt24 Vsource positive/zero sequence impedance should map to phase mutual R");
		assertEquals(2.9433333333333334 / zBase, sourceBranch.getZabc().ab.getImaginary(), 1.0e-12,
				"Ckt24 Vsource positive/zero sequence impedance should map to phase mutual X");
	}

	@Test
	public void ckt24OverheadLineGeometryAddsOpenDssCapacitanceShunt() throws IOException {
		OpenDSSStaticDataParser parser = parseStaticOpenDss(
				"testData/feeder/Ckt24", "master_ckt24_interpss.dss", false);

		Static3PBranch branch = findStaticBranchByName(parser.getStaticNetwork(), "05410_339820oh");
		assertNotNull(branch, "Missing Ckt24 overhead geometry line");
		assertNotNull(branch.getFromShuntYabc(), "OpenDSS LineGeometry should create line charging shunt");

		double baseVa = parser.getStaticNetwork().getBaseKva() * 1000.0;
		double vbase = branch.getFromBus().getBaseVoltage();
		Complex3x3 yFrom = physicalY(branch.getFromShuntYabc(), baseVa, vbase);
		assertEquals(0.0, yFrom.aa.getReal(), 1.0e-12);
		assertEquals(4.09366860e-7, yFrom.aa.getImaginary(), 1.0e-11);
		assertEquals(-1.12101718e-7, yFrom.ab.getImaginary(), 1.0e-11);
		assertEquals(-5.50265960e-8, yFrom.ac.getImaginary(), 1.0e-11);
		assertEquals(4.39535181e-7, yFrom.bb.getImaginary(), 1.0e-11);
		assertEquals(-1.12101718e-7, yFrom.bc.getImaginary(), 1.0e-11);
		assertEquals(4.09366860e-7, yFrom.cc.getImaginary(), 1.0e-11);
	}

	@Test
	public void ckt24SubstationTransformerParsesSpacedPercentRsContinuation() throws IOException {
		OpenDSSStaticDataParser parser = parseStaticOpenDss(
				"testData/feeder/Ckt24", "master_ckt24_interpss.dss", true);

		Static3PBranch transformer = findStaticBranchByName(parser.getStaticNetwork(), "subxfmr");
		assertNotNull(transformer, "Missing Ckt24 substation transformer");
		double baseVa = parser.getStaticNetwork().getBaseKva() * 1000.0;
		double fromVbase = transformer.getFromBus().getBaseVoltage();
		double toVbase = transformer.getToBus().getBaseVoltage();

		Complex yffAa = physicalY(transformer.getYffabc().aa, baseVa, fromVbase, fromVbase);
		Complex yffAb = physicalY(transformer.getYffabc().ab, baseVa, fromVbase, fromVbase);
		Complex yftAa = physicalY(transformer.getYftabc().aa, baseVa, fromVbase, toVbase);
		Complex yftAb = physicalY(transformer.getYftabc().ab, baseVa, fromVbase, toVbase);
		Complex yftAc = physicalY(transformer.getYftabc().ac, baseVa, fromVbase, toVbase);
		Complex yttAa = physicalY(transformer.getYttabc().aa, baseVa, toVbase, toVbase);
		assertEquals(0.0001203896, yffAa.getReal(), 1.0e-10);
		assertEquals(-0.00533225602, yffAa.getImaginary(), 1.0e-10);
		assertEquals(-0.0000601947999, yffAb.getReal(), 1.0e-10);
		assertEquals(0.00266612801, yffAb.getImaginary(), 1.0e-10);
		assertEquals(-0.000695069679, yftAa.getReal(), 1.0e-10);
		assertEquals(0.0307857945, yftAa.getImaginary(), 1.0e-10);
		assertEquals(0.000695069679, yftAb.getReal(), 1.0e-10);
		assertEquals(-0.0307857945, yftAb.getImaginary(), 1.0e-10);
		assertEquals(0.0, yftAc.abs(), 1.0e-12);
		assertEquals(0.00809402625, yttAa.getReal(), 1.0e-10);
		assertEquals(-0.355483735, yttAa.getImaginary(), 1.0e-10);

		transformer.setToTurnRatio(1.0125);
		Complex tappedYftAa = physicalY(transformer.getYftabc().aa, baseVa, fromVbase, toVbase);
		Complex tappedYftAb = physicalY(transformer.getYftabc().ab, baseVa, fromVbase, toVbase);
		Complex tappedYttAa = physicalY(transformer.getYttabc().aa, baseVa, toVbase, toVbase);
		assertEquals(-0.000686488571, tappedYftAa.getReal(), 1.0e-10);
		assertEquals(0.0304057230, tappedYftAa.getImaginary(), 1.0e-10);
		assertEquals(0.000686488571, tappedYftAb.getReal(), 1.0e-10);
		assertEquals(-0.0304057230, tappedYftAb.getImaginary(), 1.0e-10);
		assertEquals(0.00789540741, tappedYttAa.getReal(), 1.0e-10);
		assertEquals(-0.346760559, tappedYttAa.getImaginary(), 5.0e-8);
	}

	private static void printYMatrixComponentAudit(String label, DStabNetwork3Phase distNet) {
		OpenDssDataQaUtils.YMatrixAudit audit = OpenDssDataQaUtils.yMatrixAudit(distNet);
		System.out.println(audit.summary(label));

		System.out.println(label + " Y audit loaded/floating components:");
		audit.components.stream()
				.filter(component -> !component.hasSwing && component.hasLoad())
				.limit(20)
				.forEach(component -> System.out.println("  " + component.summary(distNet)));

		System.out.println(label + " Y audit weakest diagonal components:");
		audit.components.stream()
				.sorted(Comparator.comparingDouble(OpenDssDataQaUtils.ComponentAudit::minDiagAbs))
				.limit(20)
				.forEach(component -> System.out.println("  " + component.summary(distNet)));

		System.out.println(label + " Y audit explicit center-tap transformer samples:");
		int printedExplicitXfr = 0;
		for(Object branchObj : distNet.getBranchList()) {
			DStab3PBranch branch = (DStab3PBranch) branchObj;
			if(branch.isActive() && branch.hasExplicitYabc()) {
				System.out.println("  " + branch.getName()
						+ " " + branch.getFromBus().getId() + "->" + branch.getToBus().getId()
						+ " phase=" + branch.getPhaseCode()
						+ " fromBaseV=" + branch.getFromBus().getBaseVoltage()
						+ " toBaseV=" + branch.getToBus().getBaseVoltage()
						+ " yff=" + branch.getYffabc().absMax()
						+ " yft=" + branch.getYftabc().absMax()
						+ " ytt=" + branch.getYttabc().absMax());
				if(++printedExplicitXfr >= 20) {
					break;
				}
			}
		}

		System.out.println(label + " Y audit largest branch admittances:");
		OpenDssDataQaUtils.branchesByAdmittance(distNet).stream().limit(20)
				.forEach(branch -> System.out.println("  " + branch.getName()
						+ " " + branch.getFromBus().getId() + "->" + branch.getToBus().getId()
						+ " phase=" + branch.getPhaseCode()
						+ " line=" + branch.isLine()
						+ " xfr=" + branch.isXfr()
						+ " explicit=" + branch.hasExplicitYabc()
						+ " zAbs=" + branch.getZabc().absMax()
						+ " yff=" + branch.getYffabc().absMax()
						+ " yft=" + branch.getYftabc().absMax()
						+ " ytf=" + branch.getYtfabc().absMax()
						+ " ytt=" + branch.getYttabc().absMax()
						+ " fromBaseV=" + branch.getFromBus().getBaseVoltage()
				+ " toBaseV=" + branch.getToBus().getBaseVoltage()));
	}

	private static void printStaticYMatrixComponentAudit(String label, Static3PNetwork distNet) {
		System.out.println(label + " static Y audit: buses=" + distNet.getNoBus()
				+ ", branches=" + distNet.getNoBranch());
		try {
			distNet.formYMatrixABCForPowerflow();
		} catch(Exception e) {
			throw new IllegalStateException("Failed to form static Y matrix for " + label, e);
		}
		System.out.println(label + " static Y audit largest branch admittances:");
		List<Static3PBranch> activeBranches = new ArrayList<>();
		for(Static3PBranch branch : distNet.getBranchList()) {
			if(branch.isActive()) {
				activeBranches.add(branch);
			}
		}
		activeBranches.stream()
				.sorted(Comparator.comparingDouble(
						(Static3PBranch branch) -> branchMaxYAbs((IBranch3Phase) branch)).reversed())
				.limit(20)
				.forEach(branch -> System.out.println("  " + branch.getName()
						+ " " + branch.getFromBus().getId() + "->" + branch.getToBus().getId()
						+ " phase=" + branch.getPhaseCode()
						+ " line=" + branch.isLine()
						+ " xfr=" + branch.isXfr()
						+ " explicit=" + branch.hasExplicitYabc()
						+ " zAbs=" + branch.getZabc().absMax()
						+ " yff=" + branch.getYffabc().absMax()
						+ " yft=" + branch.getYftabc().absMax()
						+ " ytf=" + branch.getYtfabc().absMax()
						+ " ytt=" + branch.getYttabc().absMax()
						+ " fromBaseV=" + branch.getFromBus().getBaseVoltage()
						+ " toBaseV=" + branch.getToBus().getBaseVoltage()));
	}

	private static double branchMaxYAbs(IBranch3Phase branch) {
		return Math.max(
				Math.max(branch.getYffabc().absMax(), branch.getYftabc().absMax()),
				Math.max(branch.getYtfabc().absMax(), branch.getYttabc().absMax()));
	}

	private static Static3PBranch findStaticBranchByName(Static3PNetwork distNet, String branchName) {
		for(Static3PBranch branch : distNet.getBranchList()) {
			String id = branch.getId().toLowerCase();
			String name = branch.getName() == null ? "" : branch.getName().toLowerCase();
			if(id.equals(branchName) || id.endsWith(":" + branchName) || name.equals(branchName)) {
				return branch;
			}
		}
		return null;
	}

	@Test
	@Disabled("Diagnostic only: prints IEEE8500 floating phase components for Y-matrix investigation")
	public void ieee8500YMatrixSingularityDiagnostic() throws IOException {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/IEEE8500", "Master-InterPSS.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		int nodeCount = distNet.getNoBus() * 3;
		List<List<Integer>> graph = new ArrayList<>(nodeCount);
		for(int i = 0; i < nodeCount; i++) {
			graph.add(new ArrayList<>());
		}

		for(Branch branch : distNet.getBranchList()) {
			if(!branch.isActive()) {
				continue;
			}
			DStab3PBranch branch3p = (DStab3PBranch) branch;
			int from = branch.getFromBus().getSortNumber();
			int to = branch.getToBus().getSortNumber();
			addPhaseEdges(graph, from, to, branch3p.getYftabc());
			addPhaseEdges(graph, to, from, branch3p.getYtfabc());
		}

		boolean[] seen = new boolean[nodeCount];
		int printed = 0;
		for(BaseAclfBus<?, ?> bus : distNet.getBusList()) {
			if(!bus.isActive()) {
				continue;
			}
			for(int phase = 0; phase < 3; phase++) {
				int start = bus.getSortNumber() * 3 + phase;
				if(seen[start] || graph.get(start).isEmpty()) {
					continue;
				}
				ArrayDeque<Integer> queue = new ArrayDeque<>();
				List<Integer> component = new ArrayList<>();
				boolean hasSwing = false;
				seen[start] = true;
				queue.add(start);
				while(!queue.isEmpty()) {
					int node = queue.remove();
					component.add(node);
					BaseAclfBus<?, ?> nodeBus = busBySortNumber(distNet, node / 3);
					hasSwing = hasSwing || nodeBus.isSwing();
					for(int next : graph.get(node)) {
						if(!seen[next]) {
							seen[next] = true;
							queue.add(next);
						}
					}
				}
				if(!hasSwing) {
					if(printed++ < 20) {
						System.out.println("Floating phase component size=" + component.size()
								+ ", sample=" + phaseComponentSample(distNet, component, 12));
					}
				}
			}
		}
		System.out.println("Floating phase component count=" + printed);
	}

	@Test
	@Disabled("Diagnostic only: reduces IEEE8500 around m1009763 service-transformer subtrees")
	public void ieee8500ReducedPathToFirstInvalidFixedPointBus() throws IOException {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/IEEE8500", "Master-InterPSS.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		String targetBusId = "m1009763";
		List<Branch> path = sourceToTargetPath(distNet, targetBusId);
		System.out.println("IEEE8500 source-to-" + targetBusId + " branch count=" + path.size());
		for(Branch branch : path) {
			DStab3PBranch branch3p = (DStab3PBranch) branch;
			System.out.println("  " + branch.getFromBus().getId() + " -> " + branch.getToBus().getId()
					+ " id=" + branch.getId()
					+ " name=" + branch.getName()
					+ " phase=" + branch3p.getPhaseCode()
					+ " type=" + (branch3p.isXfr() ? "xfr" : "line")
					+ " yftAbs=" + branch3p.getYftabc().absMax()
					+ " ytfAbs=" + branch3p.getYtfabc().absMax());
		}

		Set<String> retainedBranchIds = new HashSet<>();
		Set<String> retainedBusIds = new HashSet<>();
		for(Branch branch : path) {
			retainedBranchIds.add(branch.getId());
			retainedBusIds.add(branch.getFromBus().getId());
			retainedBusIds.add(branch.getToBus().getId());
		}
		List<Branch> targetIncidentBranches = offPathBranchesAtBus(distNet, targetBusId, retainedBranchIds);
		Map<String, RetainedNetwork> targetSubtrees = new HashMap<>();
		for(Branch branch : targetIncidentBranches) {
			targetSubtrees.put(branch.getId(), collectSubtreeBehindBranch(distNet, branch, targetBusId, retainedBusIds));
		}
		retainOnly(distNet, retainedBranchIds, retainedBusIds);
		runReducedCase(distNet, "source-to-" + targetBusId + " trunk");
		for(Branch branch : targetIncidentBranches) {
			Set<String> oneBranchIds = new HashSet<>(retainedBranchIds);
			Set<String> oneBusIds = new HashSet<>(retainedBusIds);
			oneBranchIds.add(branch.getId());
			oneBusIds.add(branch.getFromBus().getId());
			oneBusIds.add(branch.getToBus().getId());
			retainOnly(distNet, oneBranchIds, oneBusIds);
			runReducedCase(distNet, "trunk + branch " + branch.getName() + " "
					+ branch.getFromBus().getId() + "->" + branch.getToBus().getId());
		}
		for(Branch branch : targetIncidentBranches) {
			RetainedNetwork subtree = targetSubtrees.get(branch.getId());
			printRetainedBranches(distNet, subtree);
			Set<String> subtreeBranchIds = new HashSet<>(retainedBranchIds);
			Set<String> subtreeBusIds = new HashSet<>(retainedBusIds);
			subtreeBranchIds.addAll(subtree.branchIds);
			subtreeBusIds.addAll(subtree.busIds);
			retainOnly(distNet, subtreeBranchIds, subtreeBusIds);
			runReducedCase(distNet, "trunk + subtree " + branch.getName()
					+ " branches=" + subtree.branchIds.size() + " buses=" + subtree.busIds.size());
		}
	}

	@Test
	@Disabled("Diagnostic only: disables parsed IEEE8500 capacitor loads to isolate convergence residuals")
	public void ieee8500PowerFlowCapacitorSensitivityDiagnostic() throws IOException {
		for(DistributionPFMethod method : new DistributionPFMethod[] {
				DistributionPFMethod.Fixed_Point,
				DistributionPFMethod.Forward_Backword_Sweep}) {
			OpenDSSDataParser parser = new OpenDSSDataParser();
			parser.setRegControlEnabled(false);
			assertTrue(parser.parseFeederData("testData/feeder/IEEE8500", "Master-InterPSS.dss"));
			assertTrue(parser.calcVoltageBases());
			assertTrue(parser.convertActualValuesToPU(1.0));

			int disabledCaps = deactivateParsedCapacitors(parser.getDistNetwork());
			DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(parser.getDistNetwork());
			powerFlow.setPFMethod(method);
			powerFlow.setInitBusVoltageEnabled(true);
			powerFlow.setMaxIteration(1000);
			powerFlow.setTolerance(1.0e-4);
			boolean converged = powerFlow.powerflow();
			System.out.println("IEEE8500 " + method + " with parsed capacitors disabled: converged="
					+ converged + ", iterations=" + powerFlow.getIterationCount()
					+ ", disabledCaps=" + disabledCaps);
			printLocalVoltageMagnitudes(parser.getDistNetwork(),
					List.of("regxfmr_hvmv_sub_lsb", "m3032977", "m1166366", "sx2862616c"));
			if(!converged) {
				printIeee8500FailureDiagnostics(parser.getDistNetwork(), method, powerFlow.getIterationCount());
			}
		}
	}

	@Test
	@Disabled("Diagnostic only: traces largest IEEE8500 controls-off DSS-Python mismatch region")
	public void ieee8500LargestMismatchRegionDiagnostic() throws IOException {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/IEEE8500", "Master-InterPSS.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(1000);
		powerFlow.setTolerance(1.0e-4);
		assertTrue(powerFlow.powerflow(), "Power flow failed, iterations=" + powerFlow.getIterationCount());

		List<VoltageReference> references = readReferences(
				"opendss-reference/ieee8500-controls-off-dss-python-voltage-reference.csv");
		printTopVoltageMismatches(distNet, references, 12);
		printLocalVoltageComparison(distNet, references,
				List.of("l3312692", "m3016088", "l3082993", "l3177894", "l2989571"));
		printSourcePathVoltageErrors(distNet, references, "l3312692", 1, 2.0e-3);
		printSourcePathVoltageDropErrors(distNet, references, "l3312692", 1, 5.0e-5);
		printSourcePath(distNet, "l3312692");
		printSourcePathBranchCurrents(distNet, "l3312692", "hvmv_sub_hsb");
		printLocalVoltageComparison(distNet, references,
				List.of("l3101194", "l2862616", "x2862616c", "sx2862616c"));
		printSourcePathVoltageErrors(distNet, references, "sx2862616c", 3, 2.0e-3);
		printSourcePathVoltageDropErrors(distNet, references, "sx2862616c", 3, 2.0e-4);
		printIncidentBranches(distNet, "sourcebus");
		printIncidentBranches(distNet, "hvmv_sub_hsb");
		printIncidentBranches(distNet, "regxfmr_hvmv_sub_lsb");
		printIncidentBranches(distNet, "m3032977");
		printIncidentBranches(distNet, "m3016088");
		printIncidentBranches(distNet, "l3312692");
		printThreePhaseCurrentBalance(distNet, "hvmv_sub_hsb");
		printThreePhaseCurrentBalance(distNet, "regxfmr_hvmv_sub_lsb");
		printThreePhaseCurrentBalance(distNet, "m3032977");
		printThreePhaseCurrentBalance(distNet, "m3016088");
		printThreePhaseCurrentBalance(distNet, "l3312692");
		printIncidentBranches(distNet, "m1166366");
		printIncidentBranches(distNet, "m1166368");
		printIncidentBranches(distNet, "l2973833");
		printIncidentBranches(distNet, "l2862616");
		printIncidentBranches(distNet, "x2862616c");
		printIncidentBranches(distNet, "sx2862616c");
		printPhysicalBranchDiagnostic(distNet, "hvmv_sub");
		printPhysicalBranchDiagnostic(distNet, "ln6504018-1");
		printPhysicalBranchDiagnostic(distNet, "ln6504020-2");
		printPhysicalBranchDiagnostic(distNet, "ln6318761-1");
	}

	@Test
	@Disabled("Diagnostic only: exports IEEE8500 voltage mismatch versus source depth")
	public void ieee8500VoltageDepthExportDiagnostic() throws IOException {
		List<VoltageReference> references = readReferences(
				"opendss-reference/ieee8500-controls-off-dss-python-voltage-reference.csv");
		for(double tolerance : new double[] {1.0e-4, 1.0e-6, 1.0e-8}) {
			OpenDSSDataParser parser = new OpenDSSDataParser();
			parser.setRegControlEnabled(false);
			assertTrue(parser.parseFeederData("testData/feeder/IEEE8500", "Master-InterPSS.dss"));
			assertTrue(parser.calcVoltageBases());
			assertTrue(parser.convertActualValuesToPU(1.0));

			DStabNetwork3Phase distNet = parser.getDistNetwork();
			DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
			powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
			powerFlow.setInitBusVoltageEnabled(true);
			powerFlow.setMaxIteration(1000);
			powerFlow.setTolerance(tolerance);
			assertTrue(powerFlow.powerflow(), "Power flow failed at tolerance=" + tolerance
					+ ", iterations=" + powerFlow.getIterationCount());

			Path output = Path.of("target", "load-comparison",
					"ieee8500-voltage-depth-tol-" + toleranceLabel(tolerance) + ".csv");
			Files.createDirectories(output.getParent());
			Files.writeString(output, voltageDepthCsv(distNet, references), StandardCharsets.UTF_8);
			ComparisonResult result = compareVoltages(distNet, activeReferences(distNet, references));
			System.out.println("Wrote IEEE8500 voltage-depth export tolerance=" + tolerance
					+ ", iterations=" + powerFlow.getIterationCount()
					+ ", " + result.summary("IEEE8500")
					+ ": " + output.toAbsolutePath());
		}
	}

	@Test
	@Disabled("Diagnostic only: exports Ckt24 voltage mismatch versus source depth")
	public void ckt24VoltageDepthExportDiagnostic() throws IOException {
		System.out.println(IpssCorePlugin.configureSparseSolverFromSystemProperties().message());
		List<VoltageReference> references = readReferences(
				"opendss-reference/ckt24-controls-off-dss-python-voltage-reference.csv");
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/Ckt24", "master_ckt24_interpss.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(1000);
		powerFlow.setTolerance(1.0e-6);
		assertTrue(powerFlow.powerflow(), "Ckt24 fixed-point failed, iterations=" + powerFlow.getIterationCount());

		references = activeReferences(distNet, references);
		Path output = Path.of("target", "load-comparison", "ckt24-voltage-depth.csv");
		Files.createDirectories(output.getParent());
		Files.writeString(output, voltageDepthCsv(distNet, references), StandardCharsets.UTF_8);
		ComparisonResult result = compareVoltages(distNet, references);
		System.out.println("Wrote Ckt24 voltage-depth export, iterations=" + powerFlow.getIterationCount()
				+ ", " + result.summary("Ckt24") + ": " + output.toAbsolutePath());
		if(KlusolveXPerformanceCounters.isEnabled()) {
			System.out.println(KlusolveXPerformanceCounters.summary());
		}
	}

	@Test
	@Disabled("Diagnostic only: compares Ckt24 voltage mismatch across fixed-point tolerances")
	public void ckt24ToleranceSensitivityDiagnostic() throws IOException {
		System.out.println(IpssCorePlugin.configureSparseSolverFromSystemProperties().message());
		List<VoltageReference> references = readReferences(
				"opendss-reference/ckt24-controls-off-dss-python-voltage-reference.csv");

		for(double tolerance : List.of(1.0e-6, 1.0e-4, 1.0e-3)) {
			OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
			parser.setRegControlEnabled(false);
			assertTrue(parser.parseFeederData("testData/feeder/Ckt24", "master_ckt24_interpss.dss"));
			assertTrue(parser.calcVoltageBases());
			assertTrue(parser.convertActualValuesToPU(1.0));

			Static3PNetwork distNet = parser.getStaticNetwork();
			DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
			powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
			powerFlow.setInitBusVoltageEnabled(true);
			powerFlow.setMaxIteration(1000);
			powerFlow.setTolerance(tolerance);
			assertTrue(powerFlow.powerflow(), "Ckt24 fixed-point failed at tolerance=" + tolerance
					+ ", iterations=" + powerFlow.getIterationCount());

			ComparisonResult result = compareVoltages(distNet, activeReferences(distNet, references));
			System.out.println("Ckt24 tolerance=" + tolerance
					+ ", iterations=" + powerFlow.getIterationCount()
					+ ", " + result.summary("Ckt24"));
		}
	}

	@Test
	@Disabled("Diagnostic only: exports Ckt7 voltage mismatch versus source depth")
	public void ckt7VoltageDepthExportDiagnostic() throws IOException {
		List<VoltageReference> references = readReferences(
				"opendss-reference/ckt7-controls-off-dss-python-voltage-reference.csv");
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/Ckt7", "Master_ckt7.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(1000);
		powerFlow.setTolerance(1.0e-6);
		assertTrue(powerFlow.powerflow(), "Ckt7 fixed-point failed, iterations=" + powerFlow.getIterationCount());

		references = activeReferences(distNet, references);
		Path output = Path.of("target", "load-comparison", "ckt7-voltage-depth.csv");
		Files.createDirectories(output.getParent());
		Files.writeString(output, voltageDepthCsv(distNet, references), StandardCharsets.UTF_8);
		ComparisonResult result = compareVoltages(distNet, references);
		System.out.println("Wrote Ckt7 voltage-depth export, iterations=" + powerFlow.getIterationCount()
				+ ", " + result.summary("Ckt7") + ": " + output.toAbsolutePath());
	}

	@Test
	@Disabled("Diagnostic only: exports source-path voltage mismatch for the Ckt24 worst-error region")
	public void ckt24WorstPathVoltageMismatchDiagnostic() throws IOException {
		List<VoltageReference> references = readReferences(
				"opendss-reference/ckt24-controls-off-dss-python-voltage-reference.csv");
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/Ckt24", "master_ckt24_interpss.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(1000);
		powerFlow.setTolerance(1.0e-6);
		assertTrue(powerFlow.powerflow(), "Ckt24 fixed-point failed, iterations=" + powerFlow.getIterationCount());

		Path output = Path.of("target", "load-comparison", "ckt24-worst-path-voltage.csv");
		Files.createDirectories(output.getParent());
		Files.writeString(output, sourcePathVoltageMismatchCsv(distNet, references, "n284034"),
				StandardCharsets.UTF_8);
		System.out.println("Wrote Ckt24 worst-path voltage diagnostic: " + output.toAbsolutePath());
	}

	@Test
	@Disabled("Diagnostic only: checks whether the Ckt24 voltage mismatch is driven by the near-zero line impedance floor")
	public void ckt24LineImpedanceFloorSensitivityDiagnostic() throws IOException {
		List<VoltageReference> references = readReferences(
				"opendss-reference/ckt24-controls-off-dss-python-voltage-reference.csv");
		StringBuilder csv = new StringBuilder();
		csv.append("min_line_z_pu,converged,iterations,max_mag_error_pu,max_mag_label,max_angle_error_deg,max_angle_label\n");
		for(double minLineZ : new double[] {1.0e-4, 1.0e-5, 1.0e-6, 1.0e-8, 0.0}) {
			OpenDSSDataParser parser = new OpenDSSDataParser();
			parser.setRegControlEnabled(false);
			parser.setMinLineSeriesImpedancePu(minLineZ);
			assertTrue(parser.parseFeederData("testData/feeder/Ckt24", "master_ckt24_interpss.dss"));
			assertTrue(parser.calcVoltageBases());
			assertTrue(parser.convertActualValuesToPU(1.0));

			DStabNetwork3Phase distNet = parser.getDistNetwork();
			DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
			powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
			powerFlow.setInitBusVoltageEnabled(true);
			powerFlow.setMaxIteration(1000);
			powerFlow.setTolerance(1.0e-6);
			boolean converged = powerFlow.powerflow();
			if(converged) {
				ComparisonResult result = compareVoltages(distNet, activeReferences(distNet, references));
				csv.append(minLineZ).append(",true,")
						.append(powerFlow.getIterationCount()).append(",")
						.append(result.maxMagError).append(",")
						.append(result.maxMagLabel).append(",")
						.append(result.maxAngleError).append(",")
						.append(result.maxAngleLabel).append("\n");
				System.out.println("Ckt24 floor=" + minLineZ
						+ ", iterations=" + powerFlow.getIterationCount()
						+ ", " + result.summary("Ckt24"));
			}
			else {
				csv.append(minLineZ).append(",false,")
						.append(powerFlow.getIterationCount()).append(",,,,\n");
				System.out.println("Ckt24 floor=" + minLineZ
						+ ", failed, iterations=" + powerFlow.getIterationCount());
			}
		}
		Path output = Path.of("target", "load-comparison", "ckt24-line-impedance-floor-sensitivity.csv");
		Files.createDirectories(output.getParent());
		Files.writeString(output, csv.toString(), StandardCharsets.UTF_8);
		System.out.println("Wrote Ckt24 line-impedance floor sensitivity diagnostic: " + output.toAbsolutePath());
	}

	@Test
	@Disabled("Diagnostic only: exports Ckt24 residual voltage mismatch versus source depth with zero line impedance floor")
	public void ckt24ZeroFloorVoltageDepthExportDiagnostic() throws IOException {
		List<VoltageReference> references = readReferences(
				"opendss-reference/ckt24-controls-off-dss-python-voltage-reference.csv");
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.setRegControlEnabled(false);
		parser.setMinLineSeriesImpedancePu(0.0);
		assertTrue(parser.parseFeederData("testData/feeder/Ckt24", "master_ckt24_interpss.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(1000);
		powerFlow.setTolerance(1.0e-6);
		assertTrue(powerFlow.powerflow(), "Ckt24 fixed-point failed, iterations=" + powerFlow.getIterationCount());

		references = activeReferences(distNet, references);
		Path output = Path.of("target", "load-comparison", "ckt24-voltage-depth-floor-0.csv");
		Files.createDirectories(output.getParent());
		Files.writeString(output, voltageDepthCsv(distNet, references), StandardCharsets.UTF_8);
		ComparisonResult result = compareVoltages(distNet, references);
		System.out.println("Wrote Ckt24 zero-floor voltage-depth export, iterations="
				+ powerFlow.getIterationCount() + ", " + result.summary("Ckt24")
				+ ": " + output.toAbsolutePath());
	}

	@Test
	@Disabled("Diagnostic only: exports source-path voltage mismatch for the Ckt24 zero-floor worst-error region")
	public void ckt24ZeroFloorWorstPathVoltageMismatchDiagnostic() throws IOException {
		List<VoltageReference> references = readReferences(
				"opendss-reference/ckt24-controls-off-dss-python-voltage-reference.csv");
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.setRegControlEnabled(false);
		parser.setMinLineSeriesImpedancePu(0.0);
		assertTrue(parser.parseFeederData("testData/feeder/Ckt24", "master_ckt24_interpss.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(1000);
		powerFlow.setTolerance(1.0e-6);
		assertTrue(powerFlow.powerflow(), "Ckt24 fixed-point failed, iterations=" + powerFlow.getIterationCount());

		Path output = Path.of("target", "load-comparison", "ckt24-zero-floor-worst-path-voltage.csv");
		Files.createDirectories(output.getParent());
		Files.writeString(output, sourcePathVoltageMismatchCsv(distNet, references, "g2100nj7400_n300463_sec_1"),
				StandardCharsets.UTF_8);
		System.out.println("Wrote Ckt24 zero-floor worst-path voltage diagnostic: " + output.toAbsolutePath());
	}

	@Test
	@Disabled("Diagnostic only: exports Ckt24 FBS voltage mismatch versus source depth")
	public void ckt24FbsVoltageDepthExportDiagnostic() throws IOException {
		List<VoltageReference> references = readReferences(
				"opendss-reference/ckt24-controls-off-dss-python-voltage-reference.csv");
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/Ckt24", "master_ckt24_interpss.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(DistributionPFMethod.Forward_Backword_Sweep);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(1000);
		powerFlow.setTolerance(1.0e-6);
		assertTrue(powerFlow.powerflow(), "Ckt24 FBS failed, iterations=" + powerFlow.getIterationCount());

		references = activeReferences(distNet, references);
		Path output = Path.of("target", "load-comparison", "ckt24-fbs-voltage-depth.csv");
		Files.createDirectories(output.getParent());
		Files.writeString(output, voltageDepthCsv(distNet, references), StandardCharsets.UTF_8);
		ComparisonResult result = compareVoltages(distNet, references);
		System.out.println("Wrote Ckt24 FBS voltage-depth export, active reference points=" + references.size()
				+ ", iterations=" + powerFlow.getIterationCount()
				+ ", " + result.summary("Ckt24 FBS") + ": " + output.toAbsolutePath());
	}

	@Test
	@Disabled("Diagnostic only: prints IEEE8500 downstream path branch currents for DSS-Python comparison")
	public void ieee8500DownstreamBranchCurrentPathDiagnostic() throws IOException {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/IEEE8500", "Master-InterPSS.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(1000);
		powerFlow.setTolerance(1.0e-4);
		assertTrue(powerFlow.powerflow(), "Power flow failed, iterations=" + powerFlow.getIterationCount());

		printPhasePathBranchCurrents(distNet, "hvmv_sub_hsb", "m1166366", 3);
		printPhasePathBranchCurrents(distNet, "m1166366", "l2862616", 3);
		printThreePhaseCurrentBalance(distNet, "hvmv_sub_hsb");
		printThreePhaseCurrentBalance(distNet, "regxfmr_hvmv_sub_lsb");
		printThreePhaseCurrentBalance(distNet, "_hvmv_sub_lsb");
		printThreePhaseCurrentBalance(distNet, "hvmv_sub_48332");
		printThreePhaseCurrentBalance(distNet, "q16483");
		printThreePhaseCurrentBalance(distNet, "q16483_cap");
		printThreePhaseCurrentBalance(distNet, "q16642");
		printThreePhaseCurrentBalance(distNet, "q16642_cap");
	}

	@Test
	@Disabled("Diagnostic only: isolates IEEE13 fixed-point invalid-voltage source near bus 675")
	public void ieee13FixedPointInvalidVoltageDiagnostic() throws IOException {
		for(String scenario : List.of("base", "no-cap-675", "no-line-shunts", "no-cap-675-no-line-shunts")) {
			OpenDSSDataParser parser = new OpenDSSDataParser();
			assertTrue(parser.parseFeederData("testData/feeder/IEEE13", "IEEE13Nodeckt.dss"));
			assertTrue(parser.calcVoltageBases());
			assertTrue(parser.convertActualValuesToPU(1.0));
			DStabNetwork3Phase distNet = parser.getDistNetwork();
			if(scenario.contains("no-cap-675")) {
				DStab3PBus bus675 = distNet.getBus("675");
				assertNotNull(bus675, "Missing IEEE13 bus 675");
				bus675.getThreePhaseLoadList().removeIf(load -> "cap1".equals(load.getId()));
			}
			if(scenario.contains("no-line-shunts")) {
				for(Object branchObj : distNet.getBranchList()) {
					DStab3PBranch branch = (DStab3PBranch) branchObj;
					if(branch.isLine()) {
						branch.setFromShuntYabc(null);
						branch.setToShuntYabc(null);
					}
				}
			}
			DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
			powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
			powerFlow.setInitBusVoltageEnabled(true);
			powerFlow.setMaxIteration(50);
			powerFlow.setTolerance(1.0e-4);
			boolean converged = powerFlow.powerflow();
			System.out.println("IEEE13 fixed-point diagnostic " + scenario + ": converged=" + converged
					+ ", iterations=" + powerFlow.getIterationCount());
			printIncidentBranches(distNet, "675");
			printIncidentBranches(distNet, "692");
			printIncidentBranches(distNet, "671");
		}
	}

	@Test
	@Disabled("Diagnostic only: prints InterPSS device Y blocks for DSS-Python Yprim comparison")
	public void ieee8500InterpssDeviceYprimDiagnostic() throws IOException {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/IEEE8500", "Master-InterPSS.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		for(String branchName : new String[] {
				"hvmv_sub_hsb",
				"hvmv_sub",
				"feeder_rega",
				"feeder_regb",
				"feeder_regc",
				"ln6290228-5",
				"ln5653480-1",
				"t5338976b",
				"t227944551c"}) {
			DStab3PBranch branch = parser.getBranchByName(branchName);
			assertNotNull(branch, "Missing branch for InterPSS Y diagnostic: " + branchName);
			printInterpssBranchY(branch);
		}
		printInterpssBusCapacitorLoads(parser.getDistNetwork(), "r42246");
	}

	@Test
	@Disabled("Diagnostic only: prints InterPSS physical Y blocks for 1P_1/0_AXNJ_DB linecode comparison")
	public void ieee8500Linecode1pAxnjYprimDiagnostic() throws IOException {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/IEEE8500", "Master-InterPSS.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		for(String branchName : new String[] {
				"ln81001717-1",
				"ln81043320-4",
				"ln81043320-3",
				"ln81043320-1",
				"ln81048089-1",
				"ln81048109-1"}) {
			printPhysicalBranchYDiagnostic(distNet, branchName);
		}
	}

	private static void printRetainedBranches(DStabNetwork3Phase distNet, RetainedNetwork retained) {
		for(Branch branch : distNet.getBranchList()) {
			if(retained.branchIds.contains(branch.getId())) {
				DStab3PBranch branch3p = (DStab3PBranch) branch;
				System.out.println("  subtree branch " + branch.getFromBus().getId() + " -> "
						+ branch.getToBus().getId() + " name=" + branch.getName()
						+ " phase=" + branch3p.getPhaseCode()
						+ " type=" + (branch3p.isXfr() ? "xfr" : "line")
						+ " explicitY=" + branch3p.hasExplicitYabc()
						+ " yffAbs=" + branch3p.getYffabc().absMax()
						+ " yftAbs=" + branch3p.getYftabc().absMax()
						+ " yttAbs=" + branch3p.getYttabc().absMax());
			}
		}
	}

	private static List<Branch> offPathBranchesAtBus(DStabNetwork3Phase distNet, String busId, Set<String> pathBranchIds) {
		BaseAclfBus<?, ?> bus = distNet.getBus(busId);
		if(bus == null) {
			throw new IllegalArgumentException("Bus not found: " + busId);
		}
		List<Branch> branches = new ArrayList<>();
		for(Branch branch : bus.getBranchIterable()) {
			if(branch.isActive() && !pathBranchIds.contains(branch.getId())) {
				DStab3PBranch branch3p = (DStab3PBranch) branch;
				System.out.println("IEEE8500 off-path branch at " + busId + ": "
						+ branch.getFromBus().getId() + " -> " + branch.getToBus().getId()
						+ " name=" + branch.getName()
						+ " phase=" + branch3p.getPhaseCode()
						+ " type=" + (branch3p.isXfr() ? "xfr" : "line")
						+ " yftAbs=" + branch3p.getYftabc().absMax()
						+ " ytfAbs=" + branch3p.getYtfabc().absMax());
				branches.add(branch);
			}
		}
		return branches;
	}

	private static RetainedNetwork collectSubtreeBehindBranch(
			DStabNetwork3Phase distNet,
			Branch rootBranch,
			String rootBusId,
			Set<String> pathBusIds) {
		RetainedNetwork retained = new RetainedNetwork();
		retained.branchIds.add(rootBranch.getId());
		retained.busIds.add(rootBranch.getFromBus().getId());
		retained.busIds.add(rootBranch.getToBus().getId());

		BaseAclfBus<?, ?> rootBus = distNet.getBus(rootBusId);
		BaseAclfBus<?, ?> startBus = (BaseAclfBus<?, ?>) rootBranch.getOppositeBus(rootBus);
		ArrayDeque<BaseAclfBus<?, ?>> queue = new ArrayDeque<>();
		Set<String> seen = new HashSet<>();
		queue.add(startBus);
		seen.add(rootBusId);
		seen.add(startBus.getId());
		while(!queue.isEmpty()) {
			BaseAclfBus<?, ?> bus = queue.remove();
			retained.busIds.add(bus.getId());
			for(Branch branch : bus.getBranchIterable()) {
				if(!branch.isActive()) {
					continue;
				}
				BaseAclfBus<?, ?> next = (BaseAclfBus<?, ?>) branch.getOppositeBus(bus);
				if(pathBusIds.contains(next.getId())) {
					continue;
				}
				retained.branchIds.add(branch.getId());
				retained.busIds.add(next.getId());
				if(seen.add(next.getId())) {
					queue.add(next);
				}
			}
		}
		return retained;
	}

	private static final class RetainedNetwork {
		private final Set<String> branchIds = new HashSet<>();
		private final Set<String> busIds = new HashSet<>();
	}

	private static void addPhaseEdges(List<List<Integer>> graph, int fromSort, int toSort, Complex3x3 y) {
		Complex[][] values = {
				{y.aa, y.ab, y.ac},
				{y.ba, y.bb, y.bc},
				{y.ca, y.cb, y.cc}
		};
		for(int fromPhase = 0; fromPhase < 3; fromPhase++) {
			for(int toPhase = 0; toPhase < 3; toPhase++) {
				if(values[fromPhase][toPhase] != null && values[fromPhase][toPhase].abs() > 1.0e-12) {
					int fromNode = fromSort * 3 + fromPhase;
					int toNode = toSort * 3 + toPhase;
					graph.get(fromNode).add(toNode);
					graph.get(toNode).add(fromNode);
				}
			}
		}
	}

	private static List<List<Integer>> phaseConnectivityGraph(DStabNetwork3Phase distNet) {
		int nodeCount = distNet.getNoBus() * 3;
		List<List<Integer>> graph = new ArrayList<>(nodeCount);
		for(int i = 0; i < nodeCount; i++) {
			graph.add(new ArrayList<>());
		}
		for(Object branchObj : distNet.getBranchList()) {
			Branch branch = (Branch) branchObj;
			if(branch.isActive()) {
				DStab3PBranch branch3p = (DStab3PBranch) branch;
				int from = branch.getFromBus().getSortNumber();
				int to = branch.getToBus().getSortNumber();
				addPhaseEdges(graph, from, to, branch3p.getYftabc());
				addPhaseEdges(graph, to, from, branch3p.getYtfabc());
			}
		}
		return graph;
	}

	private static List<ComponentAudit> phaseComponentAudits(DStabNetwork3Phase distNet, List<List<Integer>> graph) {
		boolean[] seen = new boolean[graph.size()];
		List<ComponentAudit> audits = new ArrayList<>();
		for(BaseAclfBus<?, ?> bus : distNet.getBusList()) {
			if(!bus.isActive()) {
				continue;
			}
			for(int phase = 0; phase < 3; phase++) {
				int start = bus.getSortNumber() * 3 + phase;
				if(seen[start] || graph.get(start).isEmpty()) {
					continue;
				}
				ArrayDeque<Integer> queue = new ArrayDeque<>();
				List<Integer> component = new ArrayList<>();
				seen[start] = true;
				queue.add(start);
				while(!queue.isEmpty()) {
					int node = queue.remove();
					component.add(node);
					for(int next : graph.get(node)) {
						if(!seen[next]) {
							seen[next] = true;
							queue.add(next);
						}
					}
				}
				audits.add(componentAudit(distNet, component));
			}
		}
		return audits;
	}

	private static ComponentAudit componentAudit(DStabNetwork3Phase distNet, List<Integer> component) {
		Set<String> busIds = new HashSet<>();
		Set<Integer> nodeSet = new HashSet<>(component);
		boolean hasSwing = false;
		int loadBusCount = 0;
		int singlePhaseLoadCount = 0;
		int threePhaseLoadCount = 0;
		double minDiagAbs = Double.POSITIVE_INFINITY;
		String minDiagLabel = "";
		for(int node : component) {
			DStab3PBus bus = (DStab3PBus) busBySortNumber(distNet, node / 3);
			busIds.add(bus.getId());
			hasSwing = hasSwing || bus.isSwing();
			if(hasAnyLoad(bus)) {
				loadBusCount++;
				singlePhaseLoadCount += bus.getSinglePhaseLoadList().size();
				threePhaseLoadCount += bus.getThreePhaseLoadList().size();
			}
			double diagAbs = phaseDiagonalAbs(bus.getYiiAbcForPowerflow(), node % 3);
			if(diagAbs < minDiagAbs) {
				minDiagAbs = diagAbs;
				minDiagLabel = bus.getId() + "." + phaseLabel(node % 3);
			}
		}

		int branchCount = 0;
		int lineCount = 0;
		int xfrCount = 0;
		int explicitXfrCount = 0;
		int triplexLikeCount = 0;
		double maxOffDiagAbs = 0.0;
		String branchSamples = "";
		for(Object branchObj : distNet.getBranchList()) {
			DStab3PBranch branch = (DStab3PBranch) branchObj;
			if(!branch.isActive() || !branchTouchesComponent(branch, nodeSet)) {
				continue;
			}
			branchCount++;
			if(branch.isLine()) {
				lineCount++;
			}
			if(branch.isXfr()) {
				xfrCount++;
			}
			if(branch.hasExplicitYabc()) {
				explicitXfrCount++;
			}
			if(isTriplexLike(branch)) {
				triplexLikeCount++;
			}
			maxOffDiagAbs = Math.max(maxOffDiagAbs, branch.getYftabc().absMax());
			maxOffDiagAbs = Math.max(maxOffDiagAbs, branch.getYtfabc().absMax());
			if(branchSamples.length() < 320) {
				if(branchSamples.length() > 0) {
					branchSamples += "; ";
				}
				branchSamples += branch.getName() + "(" + branch.getFromBus().getId()
						+ "->" + branch.getToBus().getId()
						+ ",phase=" + branch.getPhaseCode()
						+ ",xfr=" + branch.isXfr()
						+ ",explicit=" + branch.hasExplicitYabc() + ")";
			}
		}

		return new ComponentAudit(component, busIds, hasSwing, loadBusCount,
				singlePhaseLoadCount, threePhaseLoadCount, minDiagAbs, minDiagLabel,
				branchCount, lineCount, xfrCount, explicitXfrCount, triplexLikeCount,
				maxOffDiagAbs, branchSamples);
	}

	private static boolean branchTouchesComponent(DStab3PBranch branch, Set<Integer> nodeSet) {
		int from = branch.getFromBus().getSortNumber();
		int to = branch.getToBus().getSortNumber();
		return blockTouchesComponent(branch.getYftabc(), from, to, nodeSet)
				|| blockTouchesComponent(branch.getYtfabc(), to, from, nodeSet);
	}

	private static boolean blockTouchesComponent(Complex3x3 y, int fromSort, int toSort, Set<Integer> nodeSet) {
		for(int fromPhase = 0; fromPhase < 3; fromPhase++) {
			for(int toPhase = 0; toPhase < 3; toPhase++) {
				Complex value = getPhaseValue(y, fromPhase, toPhase);
				if(value != null && value.abs() > 1.0e-12
						&& (nodeSet.contains(fromSort * 3 + fromPhase)
								|| nodeSet.contains(toSort * 3 + toPhase))) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasAnyLoad(DStab3PBus bus) {
		return !bus.getSinglePhaseLoadList().isEmpty() || !bus.getThreePhaseLoadList().isEmpty();
	}

	private static double branchMaxYAbs(DStab3PBranch branch) {
		return Math.max(
				Math.max(branch.getYffabc().absMax(), branch.getYftabc().absMax()),
				Math.max(branch.getYtfabc().absMax(), branch.getYttabc().absMax()));
	}

	private static double phaseDiagonalAbs(Complex3x3 y, int phase) {
		return getPhaseValue(y, phase, phase).abs();
	}

	private static Complex getPhaseValue(Complex3x3 matrix, int row, int col) {
		if(row == 0 && col == 0) return matrix.aa;
		if(row == 0 && col == 1) return matrix.ab;
		if(row == 0 && col == 2) return matrix.ac;
		if(row == 1 && col == 0) return matrix.ba;
		if(row == 1 && col == 1) return matrix.bb;
		if(row == 1 && col == 2) return matrix.bc;
		if(row == 2 && col == 0) return matrix.ca;
		if(row == 2 && col == 1) return matrix.cb;
		return matrix.cc;
	}

	private static boolean isTriplexLike(DStab3PBranch branch) {
		String name = branch.getName() == null ? "" : branch.getName().toLowerCase();
		String from = branch.getFromBus().getId().toLowerCase();
		String to = branch.getToBus().getId().toLowerCase();
		return name.contains("triplex") || from.startsWith("x") || to.startsWith("x")
				|| branch.getPhaseCode() == PhaseCode.AB;
	}

	private static String phaseLabel(int phase) {
		return phase == 0 ? "A" : phase == 1 ? "B" : "C";
	}

	private static String toleranceLabel(double tolerance) {
		return String.format(Locale.ROOT, "%.0e", tolerance).replace("-", "m");
	}

	private static final class ComponentAudit {
		private final List<Integer> component;
		private final Set<String> busIds;
		private final boolean hasSwing;
		private final int loadBusCount;
		private final int singlePhaseLoadCount;
		private final int threePhaseLoadCount;
		private final double minDiagAbs;
		private final String minDiagLabel;
		private final int branchCount;
		private final int lineCount;
		private final int xfrCount;
		private final int explicitXfrCount;
		private final int triplexLikeCount;
		private final double maxOffDiagAbs;
		private final String branchSamples;

		private ComponentAudit(List<Integer> component, Set<String> busIds, boolean hasSwing,
				int loadBusCount, int singlePhaseLoadCount, int threePhaseLoadCount,
				double minDiagAbs, String minDiagLabel, int branchCount, int lineCount,
				int xfrCount, int explicitXfrCount, int triplexLikeCount, double maxOffDiagAbs,
				String branchSamples) {
			this.component = component;
			this.busIds = busIds;
			this.hasSwing = hasSwing;
			this.loadBusCount = loadBusCount;
			this.singlePhaseLoadCount = singlePhaseLoadCount;
			this.threePhaseLoadCount = threePhaseLoadCount;
			this.minDiagAbs = minDiagAbs;
			this.minDiagLabel = minDiagLabel;
			this.branchCount = branchCount;
			this.lineCount = lineCount;
			this.xfrCount = xfrCount;
			this.explicitXfrCount = explicitXfrCount;
			this.triplexLikeCount = triplexLikeCount;
			this.maxOffDiagAbs = maxOffDiagAbs;
			this.branchSamples = branchSamples;
		}

		private boolean hasSwing() {
			return this.hasSwing;
		}

		private boolean hasLoad() {
			return this.loadBusCount > 0;
		}

		private double minDiagAbs() {
			return this.minDiagAbs;
		}

		private String summary(DStabNetwork3Phase distNet) {
			return "nodes=" + this.component.size()
					+ ", buses=" + this.busIds.size()
					+ ", swing=" + this.hasSwing
					+ ", loadBuses=" + this.loadBusCount
					+ ", loads1p=" + this.singlePhaseLoadCount
					+ ", loads3p=" + this.threePhaseLoadCount
					+ ", branches=" + this.branchCount
					+ ", lines=" + this.lineCount
					+ ", xfr=" + this.xfrCount
					+ ", explicitXfr=" + this.explicitXfrCount
					+ ", triplexLike=" + this.triplexLikeCount
					+ ", minDiag=" + this.minDiagAbs + "@" + this.minDiagLabel
					+ ", maxOffDiag=" + this.maxOffDiagAbs
					+ ", sampleNodes=[" + phaseComponentSample(distNet, this.component, 10)
					+ "], sampleBranches=[" + this.branchSamples + "]";
		}
	}

	private static BaseAclfBus<?, ?> busBySortNumber(DStabNetwork3Phase distNet, int sortNumber) {
		for(BaseAclfBus<?, ?> bus : distNet.getBusList()) {
			if(bus.getSortNumber() == sortNumber) {
				return bus;
			}
		}
		throw new IllegalArgumentException("No bus for sort number " + sortNumber);
	}

	private static String phaseComponentSample(DStabNetwork3Phase distNet, List<Integer> component, int limit) {
		String[] phases = {"A", "B", "C"};
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < component.size() && i < limit; i++) {
			if(i > 0) {
				sb.append(", ");
			}
			int node = component.get(i);
			sb.append(busBySortNumber(distNet, node / 3).getId()).append('.').append(phases[node % 3]);
		}
		return sb.toString();
	}

	private static List<Branch> sourceToTargetPath(DStabNetwork3Phase distNet, String targetBusId) {
		BaseAclfBus<?, ?> sourceBus = null;
		for(BaseAclfBus<?, ?> bus : distNet.getBusList()) {
			if(bus.isSwing()) {
				sourceBus = bus;
				break;
			}
		}
		if(sourceBus == null) {
			throw new IllegalStateException("No swing bus in IEEE8500 network");
		}
		BaseAclfBus<?, ?> targetBus = distNet.getBus(targetBusId);
		if(targetBus == null) {
			throw new IllegalArgumentException("Target bus not found: " + targetBusId);
		}

		ArrayDeque<BaseAclfBus<?, ?>> queue = new ArrayDeque<>();
		Set<String> seen = new HashSet<>();
		Map<String, Branch> parentBranch = new HashMap<>();
		queue.add(sourceBus);
		seen.add(sourceBus.getId());
		while(!queue.isEmpty()) {
			BaseAclfBus<?, ?> bus = queue.remove();
			if(bus.getId().equals(targetBusId)) {
				break;
			}
			for(Branch branch : bus.getBranchIterable()) {
				if(!branch.isActive()) {
					continue;
				}
				BaseAclfBus<?, ?> next = (BaseAclfBus<?, ?>) branch.getOppositeBus(bus);
				if(seen.add(next.getId())) {
					parentBranch.put(next.getId(), branch);
					queue.add(next);
				}
			}
		}
		if(!parentBranch.containsKey(targetBusId)) {
			throw new IllegalStateException("No path to " + targetBusId);
		}
		List<Branch> path = new ArrayList<>();
		String current = targetBusId;
		while(!current.equals(sourceBus.getId())) {
			Branch branch = parentBranch.get(current);
			path.add(branch);
			current = branch.getFromBus().getId().equals(current)
					? branch.getToBus().getId()
					: branch.getFromBus().getId();
		}
		Collections.reverse(path);
		return path;
	}

	private static void retainOnly(DStabNetwork3Phase distNet, Set<String> branchIds, Set<String> busIds) {
		for(Branch branch : distNet.getBranchList()) {
			branch.setStatus(branchIds.contains(branch.getId()));
		}
		for(BaseAclfBus<?, ?> bus : distNet.getBusList()) {
			bus.setStatus(busIds.contains(bus.getId()));
		}
	}

	private static void runReducedCase(DStabNetwork3Phase distNet, String label) {
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(20);
		powerFlow.setTolerance(1.0e-4);
		boolean converged = powerFlow.powerflow();
		System.out.println("IEEE8500 reduced " + label + ": fixed-point converged="
				+ converged + ", iterations=" + powerFlow.getIterationCount());
	}

	private static void assertIeee8500PowerFlowConvergesWithRegControlsDisabled(DistributionPFMethod method)
			throws IOException {
		OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/IEEE8500", "Master-InterPSS.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		Static3PNetwork distNet = parser.getStaticNetwork();
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(method);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(1000);
		powerFlow.setTolerance(1.0e-4);
		boolean converged = powerFlow.powerflow();
		if(!converged) {
			printStaticYMatrixComponentAudit("IEEE8500 " + method, distNet);
		}
		assertTrue(converged, method + " power flow failed with regulator controls disabled, iterations="
				+ powerFlow.getIterationCount());
		if(method == DistributionPFMethod.Fixed_Point) {
			assertTrue(!powerFlow.isFixedPointFallbackUsed(),
					"Fixed-point power flow fell back to another method, iterations=" + powerFlow.getIterationCount());
		}
	}

	private static void printIeee8500FailureDiagnostics(DStabNetwork3Phase distNet, DistributionPFMethod method,
			int iterationCount) {
		CurrentMismatch calculatedMismatch = maxCurrentMismatch(distNet);
		System.out.println(calculatedMismatch.summary("IEEE8500 " + method + " calculated-branch-current final"));
		if(method == DistributionPFMethod.Forward_Backword_Sweep) {
			CurrentMismatch storedMismatch = maxStoredSweepCurrentMismatch(distNet);
			System.out.println(storedMismatch.summary("IEEE8500 " + method + " stored-sweep-current final"));
		}
		VoltageRange voltageRange = voltageRange(distNet);
		System.out.println(voltageRange.summary("IEEE8500 " + method + " final voltage"));
		printTopCurrentMismatchBuses(distNet, "IEEE8500 " + method + " final", 8);
		System.out.println("IEEE8500 " + method + " final diagnostics complete, iterations=" + iterationCount);
	}

	private static int deactivateParsedCapacitors(DStabNetwork3Phase distNet) {
		int count = 0;
		for(Object busObj : distNet.getBusList()) {
			DStab3PBus bus = (DStab3PBus) busObj;
			for(DStab3PLoad load : bus.getThreePhaseLoadList()) {
				if(isParsedCapacitor(load)) {
					load.setStatus(false);
					count++;
				}
			}
		}
		return count;
	}

	private static boolean isParsedCapacitor(DStab3PLoad load) {
		Complex3x1 value = load.getInit3PhaseLoad();
		return load.getCode() == AclfLoadCode.CONST_Z
				&& isCapacitorPhase(value.a_0)
				&& isCapacitorPhase(value.b_1)
				&& isCapacitorPhase(value.c_2)
				&& (value.a_0.abs() > 0.0 || value.b_1.abs() > 0.0 || value.c_2.abs() > 0.0);
	}

	private static boolean isCapacitorPhase(Complex value) {
		return Math.abs(value.getReal()) < 1.0e-10 && value.getImaginary() <= 1.0e-10;
	}

	private static void printInterpssBranchY(DStab3PBranch branch) {
		System.out.println("InterPSS branch Y diagnostic: name=" + branch.getName()
				+ " id=" + branch.getId()
				+ " " + branch.getFromBus().getId() + "->" + branch.getToBus().getId()
				+ " phase=" + branch.getPhaseCode()
				+ " line=" + branch.isLine()
				+ " xfr=" + branch.isXfr()
				+ " explicit=" + branch.hasExplicitYabc());
		printMatrix("  Yff", branch.getYffabc());
		printMatrix("  Yft", branch.getYftabc());
		printMatrix("  Ytf", branch.getYtfabc());
		printMatrix("  Ytt", branch.getYttabc());
	}

	private static void printInterpssBusCapacitorLoads(DStabNetwork3Phase distNet, String busId) {
		DStab3PBus bus = distNet.getBus(busId);
		assertNotNull(bus, "Missing bus for capacitor Y diagnostic: " + busId);
		for(DStab3PLoad load : bus.getThreePhaseLoadList()) {
			if(isParsedCapacitor(load)) {
				System.out.println("InterPSS capacitor-as-load diagnostic: bus=" + busId
						+ " load=" + load.getId()
						+ " code=" + load.getCode()
						+ " conn=" + load.getLoadConnectionType()
						+ " nominalKV=" + load.getNominalKV()
						+ " initLoadPu=" + phaseValues(load.getInit3PhaseLoad()));
			}
		}
	}

	private static void printMatrix(String label, Complex3x3 matrix) {
		System.out.println(label);
		System.out.println("    " + formatComplex(getPhaseValue(matrix, 0, 0))
				+ "  " + formatComplex(getPhaseValue(matrix, 0, 1))
				+ "  " + formatComplex(getPhaseValue(matrix, 0, 2)));
		System.out.println("    " + formatComplex(getPhaseValue(matrix, 1, 0))
				+ "  " + formatComplex(getPhaseValue(matrix, 1, 1))
				+ "  " + formatComplex(getPhaseValue(matrix, 1, 2)));
		System.out.println("    " + formatComplex(getPhaseValue(matrix, 2, 0))
				+ "  " + formatComplex(getPhaseValue(matrix, 2, 1))
				+ "  " + formatComplex(getPhaseValue(matrix, 2, 2)));
	}

	private static String formatComplex(Complex value) {
		return String.format("%.9g%+.9gj", value.getReal(), value.getImaginary());
	}

	@Test
	public void ieee123PowerFlowMatchesDssPythonReferenceWithSolvedRegulatorTaps() throws IOException {
		ComparisonResult result = assertMatchesDssPythonReference(
				"testData/feeder/IEEE123",
				"IEEE123Master.dss",
				"opendss-reference/ieee123-dss-python-voltage-reference.csv",
				5.0e-2,
				true);
		System.out.println(result.summary("IEEE123 DSS-Python taps"));
	}

	@Test
	public void centerTappedServiceTransformerMiniCaseConverges() throws IOException {
		OpenDSSStaticDataParser parser = parseStaticOpenDss("testData/feeder/CenterTapMini", "Master.dss",
				true);

		Static3PNetwork distNet = parser.getStaticNetwork();
		Static3PBranch transformer = findStaticBranchByName(distNet, "service");
		assertNotNull(transformer, "Missing center-tapped service transformer");
		assertTrue(transformer.hasExplicitYabc(), "Center-tapped transformer should use explicit Y blocks");
		assertEquals(208.0, distNet.getBus("secondary").getBaseVoltage(), 2.0);

		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(50);
		powerFlow.setTolerance(1.0e-6);
		assertTrue(powerFlow.powerflow(), "Center-tapped mini case failed, iterations="
				+ powerFlow.getIterationCount());

		Complex3x1 vabc = distNet.getBus("loadbus").get3PhaseVotlages();
		assertEquals(1.0, vabc.a_0.abs(), 0.08);
		assertEquals(1.0, vabc.b_1.abs(), 0.08);
		assertEquals(1.0, splitPhaseLineVoltagePu(vabc), 0.08);
		assertTrue(vabc.c_2.abs() < 1.0e-3);

		ComparisonResult result = compareVoltages(distNet,
				readReferences("opendss-reference/centertap-mini-dss-python-voltage-reference.csv"));
		assertMiniDssPythonComparison(result, "CenterTapMini DSS-Python");
	}

	@Test
	public void centerTappedTwoPhaseWyeLoadMiniCaseConverges() throws IOException {
		assertCenterTappedMiniCaseConverges("testData/feeder/CenterTapMiniTwoPhaseLoad",
				"opendss-reference/centertap-mini-two-phase-load-dss-python-voltage-reference.csv",
				"CenterTapMiniTwoPhaseLoad DSS-Python",
				"Center-tapped two-phase wye load mini case failed");
	}

	@Test
	public void centerTappedTwoPhaseConstZLoadMiniCaseConverges() throws IOException {
		assertCenterTappedMiniCaseConverges("testData/feeder/CenterTapMiniConstZLoad",
				"opendss-reference/centertap-mini-const-z-load-dss-python-voltage-reference.csv",
				"CenterTapMiniConstZLoad DSS-Python",
				"Center-tapped two-phase const-Z load mini case failed");
	}

	@Test
	public void centerTappedLowVoltageTwoPhaseWyeLoadUsesOpenDssVminFallback() throws IOException {
		assertCenterTappedLowVoltageMiniCaseConverges("testData/feeder/CenterTapMiniLowVoltageTwoPhaseLoad",
				"opendss-reference/centertap-mini-low-voltage-two-phase-load-dss-python-voltage-reference.csv",
				"CenterTapMiniLowVoltageTwoPhaseLoad DSS-Python",
				"Center-tapped low-voltage two-phase wye load mini case failed");
	}

	@Test
	public void centerTappedLowVoltageSinglePhaseLoadsUseOpenDssVminFallback() throws IOException {
		assertCenterTappedLowVoltageMiniCaseConverges("testData/feeder/CenterTapMiniLowVoltageSinglePhaseLoads",
				"opendss-reference/centertap-mini-low-voltage-single-phase-loads-dss-python-voltage-reference.csv",
				"CenterTapMiniLowVoltageSinglePhaseLoads DSS-Python",
				"Center-tapped low-voltage single-phase load mini case failed");
	}

	@Test
	public void centerTappedSinglePhaseDeltaLoadMiniCaseConverges() throws IOException {
		assertCenterTappedMiniCaseConverges("testData/feeder/CenterTapMiniDeltaLoad",
				"opendss-reference/centertap-mini-delta-load-dss-python-voltage-reference.csv",
				"CenterTapMiniDeltaLoad DSS-Python",
				"Center-tapped single-phase delta load mini case failed");
	}

	@Test
	public void openDssLoadModelsMiniCaseMatchesDssPythonReference() throws IOException {
		ComparisonResult result = assertMatchesDssPythonReference(
				"testData/feeder/OpenDSSLoadModelMini",
				"Master.dss",
				"opendss-reference/opendss-load-model-mini-dss-python-voltage-reference.csv",
				2.0e-3);
		System.out.println(result.summary("OpenDSS load model mini DSS-Python"));
	}

	private static void assertCenterTappedMiniCaseConverges(String feederFolder, String referenceResource,
			String comparisonLabel, String failureMessage) throws IOException {
		OpenDSSStaticDataParser parser = parseStaticOpenDss(feederFolder, "Master.dss", true);

		Static3PNetwork distNet = parser.getStaticNetwork();
		Static3PBranch transformer = findStaticBranchByName(distNet, "service");
		assertNotNull(transformer, "Missing center-tapped service transformer");
		assertTrue(transformer.hasExplicitYabc(), "Center-tapped transformer should use explicit Y blocks");
		assertEquals(208.0, distNet.getBus("secondary").getBaseVoltage(), 2.0);

		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(50);
		powerFlow.setTolerance(1.0e-6);
		assertTrue(powerFlow.powerflow(), failureMessage + ", iterations=" + powerFlow.getIterationCount());

		Complex3x1 vabc = distNet.getBus("loadbus").get3PhaseVotlages();
		assertEquals(1.0, vabc.a_0.abs(), 0.08);
		assertEquals(1.0, vabc.b_1.abs(), 0.08);
		assertEquals(1.0, splitPhaseLineVoltagePu(vabc), 0.08);
		assertTrue(vabc.c_2.abs() < 1.0e-3);

		ComparisonResult result = compareVoltages(distNet, readReferences(referenceResource));
		assertMiniDssPythonComparison(result, comparisonLabel);
	}

	private static void assertCenterTappedLowVoltageMiniCaseConverges(String feederFolder, String referenceResource,
			String comparisonLabel, String failureMessage) throws IOException {
		OpenDSSStaticDataParser parser = parseStaticOpenDss(feederFolder, "Master.dss", true);

		Static3PNetwork distNet = parser.getStaticNetwork();
		Static3PBranch transformer = findStaticBranchByName(distNet, "service");
		assertNotNull(transformer, "Missing center-tapped service transformer");
		assertTrue(transformer.hasExplicitYabc(), "Center-tapped transformer should use explicit Y blocks");
		assertEquals(208.0, distNet.getBus("secondary").getBaseVoltage(), 2.0);

		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(50);
		powerFlow.setTolerance(1.0e-8);
		assertTrue(powerFlow.powerflow(), failureMessage + ", iterations=" + powerFlow.getIterationCount());

		Complex3x1 vabc = distNet.getBus("loadbus").get3PhaseVotlages();
		assertTrue(vabc.a_0.abs() < 0.88, "phase 1 should be below Vminpu to exercise OpenDSS fallback");
		assertTrue(vabc.b_1.abs() < 0.88, "phase 2 should be below Vminpu to exercise OpenDSS fallback");
		assertTrue(splitPhaseLineVoltagePu(vabc) < 0.88,
				"split-phase line voltage should be below Vminpu to exercise OpenDSS fallback");
		assertTrue(vabc.c_2.abs() < 1.0e-3);

		ComparisonResult result = compareVoltages(distNet, readReferences(referenceResource));
		assertMiniDssPythonComparison(result, comparisonLabel);
	}

	private static double splitPhaseLineVoltagePu(Complex3x1 vabc) {
		return vabc.a_0.subtract(vabc.b_1).abs()/2.0;
	}

	private static void assertMiniDssPythonComparison(ComparisonResult result, String label) {
		System.out.println(result.summary(label));
		assertTrue(result.maxMagError < 2.0e-3,
				label + " max voltage magnitude error " + result.maxMagError + " at " + result.maxMagLabel);
		assertTrue(result.maxAngleError < VOLTAGE_ANGLE_TOLERANCE_DEG,
				label + " max voltage angle error " + result.maxAngleError + " deg at " + result.maxAngleLabel);
	}

	@Test
	public void ieee123FixedPointHasSmallCurrentMismatchWithSolvedRegulatorTaps() throws IOException {
		DStabNetwork3Phase distNet = solveIeee123WithSolvedRegulatorTaps(DistributionPFMethod.Fixed_Point, 1.0e-8);

		CurrentMismatch mismatch = maxCurrentMismatch(distNet);
		System.out.println(mismatch.summary("IEEE123 fixed-point DSS-Python taps"));
		assertTrue(mismatch.maxAbs < 1.0e-5,
				"Max fixed-point current mismatch " + mismatch.maxAbs + " at " + mismatch.label);

		ComparisonResult result = compareVoltages(distNet,
				readReferences("opendss-reference/ieee123-dss-python-voltage-reference.csv"));
		System.out.println(result.summary("IEEE123 fixed-point DSS-Python taps"));
	}

	@Test
	public void ieee123RegulatorPingPongFixedPointVersusFbs() throws IOException {
		DStabNetwork3Phase fixedPointNet = solveIeee123WithSolvedRegulatorTaps(DistributionPFMethod.Fixed_Point, 1.0e-8);
		DStabNetwork3Phase fbsNet = solveIeee123WithSolvedRegulatorTaps(DistributionPFMethod.Forward_Backword_Sweep, 1.0e-8);

		for(String regulator : new String[] {"reg1a", "reg2a", "reg3a", "reg4a", "reg3c", "reg4b", "reg4c"}) {
			System.out.println(regulatorPingPongSummary(regulator, fixedPointNet, fbsNet));
		}
	}

	@Test
	public void ieee123Bus21PhaseDiagnostic() throws IOException {
		DStabNetwork3Phase fixedPointNet = solveIeee123WithSolvedRegulatorTaps(DistributionPFMethod.Fixed_Point, 1.0e-8);
		DStabNetwork3Phase fbsNet = solveIeee123WithSolvedRegulatorTaps(DistributionPFMethod.Forward_Backword_Sweep, 1.0e-8);

		DStab3PBranch phaseBLine = findBranchByName(fbsNet, "l21");
		assertNotNull(phaseBLine, "Missing IEEE123 line L21");
		assertEquals(PhaseCode.B, phaseBLine.getPhaseCode(), "Line L21 should be parsed as phase B only");

		DStab3PBus fixedPointBus = fixedPointNet.getBus("21");
		DStab3PBus fbsBus = fbsNet.getBus("21");
		assertTrue(fixedPointBus.get3PhaseVotlages().b_1.abs() > 0.9, "Fixed-point bus 21 phase B should be active");
		assertTrue(fbsBus.get3PhaseVotlages().b_1.abs() > 0.9, "FBS bus 21 phase B should be active");
		assertBus21MatchesDssPython(fixedPointBus.get3PhaseVotlages(), 5.0e-4, "Fixed-point");
		assertBus21MatchesDssPython(fbsBus.get3PhaseVotlages(), 2.0e-3, "FBS");
		System.out.println("IEEE123 bus 21 phase check: FP="
				+ phaseMagnitudes(fixedPointBus.get3PhaseVotlages())
				+ " FBS="
				+ phaseMagnitudes(fbsBus.get3PhaseVotlages()));
		System.out.println(busComparisonSummary("21", fixedPointNet, fbsNet));
		CurrentMismatch fbsStoredMismatch = maxStoredSweepCurrentMismatch(fbsNet);
		System.out.println(fbsStoredMismatch.summary("IEEE123 FBS stored-current"));
		assertTrue(fbsStoredMismatch.maxAbs < 1.0e-5,
				"Max FBS stored-current mismatch " + fbsStoredMismatch.maxAbs + " at " + fbsStoredMismatch.label);
	}

	private static ComparisonResult assertMatchesDssPythonReference(String feederFolder, String masterFile, String referenceResource)
			throws IOException {
		return assertMatchesDssPythonReference(feederFolder, masterFile, referenceResource, VOLTAGE_MAG_TOLERANCE_PU);
	}

	private static ComparisonResult assertMatchesDssPythonReference(String feederFolder, String masterFile, String referenceResource,
			double voltageMagTolerancePu)
			throws IOException {
		return assertMatchesDssPythonReference(feederFolder, masterFile, referenceResource, voltageMagTolerancePu, false);
	}

	private static ComparisonResult assertMatchesDssPythonReference(String feederFolder, String masterFile, String referenceResource,
			double voltageMagTolerancePu, boolean applySolvedIeee123RegulatorTaps)
			throws IOException {
		return assertMatchesDssPythonReference(feederFolder, masterFile, referenceResource, voltageMagTolerancePu,
				applySolvedIeee123RegulatorTaps ? OpenDssTapProfile.IEEE123_DSS_PYTHON : OpenDssTapProfile.NONE);
	}

	private static ComparisonResult assertMatchesDssPythonReference(String feederFolder, String masterFile, String referenceResource,
			double voltageMagTolerancePu, OpenDssTapProfile tapProfile)
			throws IOException {
		return assertMatchesDssPythonReference(feederFolder, masterFile, referenceResource, voltageMagTolerancePu,
				tapProfile, DistributionPFMethod.Fixed_Point);
	}

	private static ComparisonResult assertMatchesDssPythonReference(String feederFolder, String masterFile, String referenceResource,
			double voltageMagTolerancePu, boolean applySolvedIeee123RegulatorTaps, DistributionPFMethod method)
			throws IOException {
		return assertMatchesDssPythonReference(feederFolder, masterFile, referenceResource, voltageMagTolerancePu,
				applySolvedIeee123RegulatorTaps ? OpenDssTapProfile.IEEE123_DSS_PYTHON : OpenDssTapProfile.NONE,
				method, true);
	}

	private static ComparisonResult assertMatchesDssPythonReference(String feederFolder, String masterFile, String referenceResource,
			double voltageMagTolerancePu, boolean applySolvedIeee123RegulatorTaps, DistributionPFMethod method,
			boolean regControlEnabled)
			throws IOException {
		return assertMatchesDssPythonReference(feederFolder, masterFile, referenceResource, voltageMagTolerancePu,
				applySolvedIeee123RegulatorTaps ? OpenDssTapProfile.IEEE123_DSS_PYTHON : OpenDssTapProfile.NONE,
				method, regControlEnabled);
	}

	private static ComparisonResult assertMatchesDssPythonReference(String feederFolder, String masterFile, String referenceResource,
			double voltageMagTolerancePu, OpenDssTapProfile tapProfile, DistributionPFMethod method)
			throws IOException {
		return assertMatchesDssPythonReference(feederFolder, masterFile, referenceResource, voltageMagTolerancePu,
				tapProfile, method, true);
	}

	private static ComparisonResult assertMatchesDssPythonReference(String feederFolder, String masterFile, String referenceResource,
			double voltageMagTolerancePu, OpenDssTapProfile tapProfile, DistributionPFMethod method,
			boolean regControlEnabled)
			throws IOException {
		OpenDSSStaticDataParser parser = parseStaticOpenDss(feederFolder, masterFile, regControlEnabled);

		Static3PNetwork distNet = parser.getStaticNetwork();
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(method);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(feederFolder.contains("8500") ? 1000 : 200);
		powerFlow.setTolerance(1.0e-4);
		if(tapProfile != OpenDssTapProfile.NONE) {
			powerFlow.setRegulatorControls(parser.getRegulatorControls());
			powerFlow.setRegulatorControlEnabled(true);
		}
		assertTrue(powerFlow.powerflow(), "Power flow failed, iterations=" + powerFlow.getIterationCount());

		List<VoltageReference> references = readReferences(referenceResource);
		ComparisonResult result = tapProfile == OpenDssTapProfile.IEEE13_SOLVED
				? compareVoltages(distNet, references, "650", 1)
				: compareVoltages(distNet, references);
		assertTrue(result.maxMagError < voltageMagTolerancePu,
				"Max voltage magnitude error " + result.maxMagError + " at " + result.maxMagLabel);
		assertTrue(result.maxAngleError < VOLTAGE_ANGLE_TOLERANCE_DEG,
				"Max voltage angle error " + result.maxAngleError + " deg at " + result.maxAngleLabel);
		return result;
	}

	private static OpenDSSStaticDataParser parseStaticOpenDss(String feederFolder, String masterFile,
			boolean regControlEnabled) {
		OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.setRegControlEnabled(regControlEnabled);
		assertTrue(parser.parseFeederData(feederFolder, masterFile));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));
		return parser;
	}

	private static DStabNetwork3Phase solveIeee123WithSolvedRegulatorTaps(DistributionPFMethod method, double tolerance)
			throws IOException {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		assertTrue(parser.parseFeederData("testData/feeder/IEEE123", "IEEE123Master.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));
		applySolvedIeee123RegulatorTaps(parser);

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(method);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(200);
		powerFlow.setTolerance(tolerance);
		assertTrue(powerFlow.powerflow());
		return distNet;
	}

	private static DStabNetwork3Phase solveCkt24FixedPoint() throws IOException {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData("testData/feeder/Ckt24", "master_ckt24_interpss.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(1000);
		powerFlow.setTolerance(1.0e-6);
		assertTrue(powerFlow.powerflow(), "Ckt24 fixed-point failed, iterations=" + powerFlow.getIterationCount());
		return distNet;
	}

	private static ComparisonResult compareVoltages(BaseAclfNetwork<?, ?> distNet, List<VoltageReference> references) {
		return OpenDssDataQaUtils.compareVoltages(distNet, references);
	}

	private static ComparisonResult compareVoltages(BaseAclfNetwork<?, ?> distNet, List<VoltageReference> references,
			String angleReferenceBus, int angleReferencePhase) {
		return OpenDssDataQaUtils.compareVoltages(distNet, references, angleReferenceBus, angleReferencePhase);
	}

	private static double referenceAngleOffsetDeg(DStabNetwork3Phase distNet, List<VoltageReference> references,
			String busId, int phase) {
		VoltageReference reference = references.stream()
				.filter(ref -> ref.bus.equals(busId) && ref.phase == phase)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Missing angle reference: " + busId + "." + phase));
		DStab3PBus bus = distNet.getBus(busId);
		assertNotNull(bus, "Missing parsed angle-reference bus: " + busId);
		return wrappedAngleDeg(reference.angleDeg
				- Math.toDegrees(phaseVoltage(bus.get3PhaseVotlages(), phase).getArgument()));
	}

	private static void printTopVoltageMismatches(DStabNetwork3Phase distNet, List<VoltageReference> references, int limit) {
		List<VoltageMismatch> mismatches = new ArrayList<>();
		for(VoltageReference reference : references) {
			DStab3PBus bus = distNet.getBus(reference.bus);
			if(bus == null) {
				continue;
			}
			Complex voltage = phaseVoltage(bus.get3PhaseVotlages(), reference.phase);
			mismatches.add(new VoltageMismatch(reference, voltage.abs(),
					Math.toDegrees(voltage.getArgument())));
		}
		mismatches.sort(Comparator.comparingDouble(VoltageMismatch::magError).reversed());
		System.out.println("IEEE8500 controls-off top voltage magnitude mismatches:");
		for(int i = 0; i < Math.min(limit, mismatches.size()); i++) {
			System.out.println("  #" + (i + 1) + " " + mismatches.get(i).summary());
		}
	}

	private static void printLocalVoltageComparison(DStabNetwork3Phase distNet, List<VoltageReference> references,
			List<String> busIds) {
		Map<String, VoltageReference> byBusPhase = new HashMap<>();
		for(VoltageReference reference : references) {
			byBusPhase.put(reference.bus + "." + reference.phase, reference);
		}
		System.out.println("IEEE8500 local voltage comparison:");
		for(String busId : busIds) {
			DStab3PBus bus = distNet.getBus(busId);
			assertNotNull(bus, "Missing local bus: " + busId);
			for(int phase = 1; phase <= 3; phase++) {
				VoltageReference reference = byBusPhase.get(busId + "." + phase);
				if(reference == null) {
					continue;
				}
				Complex voltage = phaseVoltage(bus.get3PhaseVotlages(), phase);
				double angleDeg = Math.toDegrees(voltage.getArgument());
				System.out.printf("  %s.%d InterPSS |V|=%.9f angle=%.6f DSS |V|=%.9f angle=%.6f dV=%.9f dAng=%.6f%n",
						busId, phase, voltage.abs(), angleDeg, reference.vmagPu, reference.angleDeg,
						voltage.abs() - reference.vmagPu, wrappedAngleDeg(angleDeg - reference.angleDeg));
			}
		}
	}

	private static void printSourcePath(DStabNetwork3Phase distNet, String targetBusId) {
		List<Branch> path = sourceToTargetPath(distNet, targetBusId);
		System.out.println("IEEE8500 source-to-" + targetBusId + " path branch count=" + path.size());
		for(Branch branch : path) {
			DStab3PBranch branch3p = (DStab3PBranch) branch;
			DStab3PBus from = (DStab3PBus) branch.getFromBus();
			DStab3PBus to = (DStab3PBus) branch.getToBus();
			System.out.println("  " + from.getId() + " -> " + to.getId()
					+ " id=" + branch.getId()
					+ " name=" + branch.getName()
					+ " phase=" + branch3p.getPhaseCode()
					+ " type=" + (branch3p.isXfr() ? "xfr" : "line")
					+ " Vfrom=" + phaseMagnitudes(from.get3PhaseVotlages())
					+ " Vto=" + phaseMagnitudes(to.get3PhaseVotlages())
					+ " yftAbs=" + branch3p.getYftabc().absMax()
					+ " ytfAbs=" + branch3p.getYtfabc().absMax());
		}
	}

	private static void printLocalVoltageMagnitudes(DStabNetwork3Phase distNet, List<String> busIds) {
		System.out.println("IEEE8500 local InterPSS voltage magnitudes:");
		for(String busId : busIds) {
			DStab3PBus bus = distNet.getBus(busId);
			if(bus == null) {
				System.out.println("  " + busId + " missing");
				continue;
			}
			System.out.println("  " + busId + " " + phaseMagnitudes(bus.get3PhaseVotlages()));
		}
	}

	private static String voltageDepthCsv(DStabNetwork3Phase distNet, List<VoltageReference> references) {
		return OpenDssDataQaUtils.voltageDepthCsv(distNet, references);
	}

	private static String sourcePathVoltageMismatchCsv(DStabNetwork3Phase distNet,
			List<VoltageReference> references, String targetBusId) {
		Map<String, VoltageReference> referenceByNode = new HashMap<>();
		for(VoltageReference reference : references) {
			referenceByNode.put(reference.bus + "." + reference.phase, reference);
		}

		StringBuilder builder = new StringBuilder();
		builder.append("depth,branch,from_bus,to_bus,bus,phase,interpss_vmag,dss_vmag,dv_pu,interpss_angle_deg,dss_angle_deg,dangle_deg,z_aa,z_bb,z_cc\n");
		DStab3PBus sourceBus = null;
		for(Object busObj : distNet.getBusList()) {
			DStab3PBus bus = (DStab3PBus) busObj;
			if(bus.isActive() && bus.isSwing()) {
				sourceBus = bus;
				break;
			}
		}
		if(sourceBus != null) {
			appendPathVoltageRows(builder, sourceBus, null, null, 0, referenceByNode);
		}

		String upstreamBusId = sourceBus == null ? null : sourceBus.getId();
		int depth = 0;
		for(Branch branchObj : sourceToTargetPath(distNet, targetBusId)) {
			DStab3PBranch branch = (DStab3PBranch) branchObj;
			DStab3PBus downstreamBus = (DStab3PBus) (branch.getFromBus().getId().equals(upstreamBusId)
					? branch.getToBus() : branch.getFromBus());
			appendPathVoltageRows(builder, downstreamBus, branch, upstreamBusId, ++depth, referenceByNode);
			upstreamBusId = downstreamBus.getId();
		}
		return builder.toString();
	}

	private static void appendPathVoltageRows(StringBuilder builder, DStab3PBus bus, DStab3PBranch branch,
			String upstreamBusId, int depth, Map<String, VoltageReference> referenceByNode) {
		for(int phase = 1; phase <= 3; phase++) {
			VoltageReference reference = referenceByNode.get(bus.getId() + "." + phase);
			if(reference == null) {
				continue;
			}
			Complex voltage = phaseVoltage(bus.get3PhaseVotlages(), phase);
			double interpssAngle = Math.toDegrees(voltage.getArgument());
			builder.append(depth).append(",")
					.append(branch == null ? "" : branch.getName()).append(",")
					.append(upstreamBusId == null ? "" : upstreamBusId).append(",")
					.append(bus.getId()).append(",")
					.append(bus.getId()).append(",")
					.append(phase).append(",")
					.append(String.format("%.12g", voltage.abs())).append(",")
					.append(String.format("%.12g", reference.vmagPu)).append(",")
					.append(String.format("%.12g", voltage.abs() - reference.vmagPu)).append(",")
					.append(String.format("%.12g", interpssAngle)).append(",")
					.append(String.format("%.12g", reference.angleDeg)).append(",")
					.append(String.format("%.12g", wrappedAngleDeg(interpssAngle - reference.angleDeg)))
					.append(",")
					.append(branch == null ? "" : branch.getZabc().aa).append(",")
					.append(branch == null ? "" : branch.getZabc().bb).append(",")
					.append(branch == null ? "" : branch.getZabc().cc)
					.append("\n");
		}
	}

	private static void deactivateZeroBaseIslands(DStabNetwork3Phase distNet) {
		OpenDssDataQaUtils.deactivateZeroBaseIslands(distNet);
	}

	private static List<VoltageReference> activeReferences(DStabNetwork3Phase distNet, List<VoltageReference> references) {
		return OpenDssDataQaUtils.activeReferences(distNet, references);
	}

	private static List<VoltageReference> activeReferences(BaseAclfNetwork<?, ?> distNet,
			List<VoltageReference> references) {
		return references.stream()
				.filter(reference -> {
					BaseAclfBus<?, ?> bus = distNet.getBus(reference.bus);
					return bus != null && bus.isActive();
				})
				.toList();
	}

	private static String interpssLoadCsv(DStabNetwork3Phase distNet) {
		StringBuilder builder = new StringBuilder();
		builder.append("source,name,bus,load_class,code,conn,phase,nominal_kv,vminpu,vmaxpu,p_kw,q_kvar,current_abs_sum_a,current_abs_max_a,currents,powers_kva\n");
		double baseKva1P = distNet.getBaseKva()/3.0;
		for(DStab3PBus bus : distNet.getBusList()) {
			Complex3x1 vabc = bus.get3PhaseVotlages();
			double baseCurrentA = baseKva1P/(bus.getBaseVoltage()*1.0e-3/Math.sqrt(3.0));
			for(DStab1PLoad load : bus.getSinglePhaseLoadList()) {
				Complex3x1 injection = load.getEquivCurrInj(vabc);
				Complex3x1 loadCurrent = injection.multiply(-1.0);
				Complex3x1 power = phasePowers(vabc, loadCurrent);
				appendInterpssLoadRow(builder, load.getId(), bus.getId(), "single",
						load.getCode().toString(), load.getLoadConnectionType().toString(),
						load.getPhaseCode().toString(), load.getNominalKV(), load.getVminpu(), load.getVmaxpu(),
						loadCurrent, power, baseKva1P, baseCurrentA);
			}
			for(DStab3PLoad load : bus.getThreePhaseLoadList()) {
				DStab1PLoad loadBase = (DStab1PLoad) load;
				Complex3x1 injection = load.getEquivCurrInj(vabc);
				Complex3x1 loadCurrent = injection.multiply(-1.0);
				Complex3x1 power = phasePowers(vabc, loadCurrent);
				appendInterpssLoadRow(builder, load.getId(), bus.getId(), "three",
						load.getCode().toString(), load.getLoadConnectionType().toString(),
						load.getPhaseCode().toString(), load.getNominalKV(), loadBase.getVminpu(), loadBase.getVmaxpu(),
						loadCurrent, power, baseKva1P, baseCurrentA);
			}
		}
		return builder.toString();
	}

	private static String interpssBranchCsv(DStabNetwork3Phase distNet) {
		StringBuilder builder = new StringBuilder();
		builder.append("source,class,name,bus,from_bus,to_bus,phase,p_kw,q_kvar,current_abs_sum_a,current_abs_max_a,currents,powers_kva\n");
		double baseVa = distNet.getBaseKva() * 1000.0;
		for(Branch branch : distNet.getBranchList()) {
			if(!branch.isActive()) {
				continue;
			}
			DStab3PBranch branch3p = (DStab3PBranch) branch;
			DStab3PBus fromBus = (DStab3PBus) branch.getFromBus();
			DStab3PBus toBus = (DStab3PBus) branch.getToBus();
			Complex3x1 fromCurrentPu = branch3p.calc3PhaseCurrentFrom2To();
			Complex3x1 toCurrentPu = branch3p.calc3PhaseCurrentTo2From();
			double fromIbase = baseVa / (Math.sqrt(3.0) * fromBus.getBaseVoltage());
			double toIbase = baseVa / (Math.sqrt(3.0) * toBus.getBaseVoltage());
			Complex3x1 fromCurrent = fromCurrentPu.multiply(fromIbase);
			Complex3x1 toCurrent = toCurrentPu.multiply(toIbase);
			Complex3x1 fromPower = physicalPhasePowers(fromBus.get3PhaseVotlages(), fromBus.getBaseVoltage(), fromCurrent);
			Complex3x1 toPower = physicalPhasePowers(toBus.get3PhaseVotlages(), toBus.getBaseVoltage(), toCurrent);
			Complex totalPower = fromPower.a_0.add(fromPower.b_1).add(fromPower.c_2)
					.add(toPower.a_0).add(toPower.b_1).add(toPower.c_2);
			double currentAbsSum = fromCurrent.a_0.abs() + fromCurrent.b_1.abs() + fromCurrent.c_2.abs()
					+ toCurrent.a_0.abs() + toCurrent.b_1.abs() + toCurrent.c_2.abs();
			double currentAbsMax = Collections.max(List.of(fromCurrent.a_0.abs(), fromCurrent.b_1.abs(),
					fromCurrent.c_2.abs(), toCurrent.a_0.abs(), toCurrent.b_1.abs(), toCurrent.c_2.abs()));
			builder.append("interpss,")
					.append(branch3p.isXfr() ? "Transformer" : "Line").append(",")
					.append(csv(branch.getName().toLowerCase())).append(",")
					.append(csv(fromBus.getId().toLowerCase() + ";" + toBus.getId().toLowerCase())).append(",")
					.append(csv(fromBus.getId().toLowerCase())).append(",")
					.append(csv(toBus.getId().toLowerCase())).append(",")
					.append(csv(branch3p.getPhaseCode().toString())).append(",")
					.append(String.format("%.12g", totalPower.getReal())).append(",")
					.append(String.format("%.12g", totalPower.getImaginary())).append(",")
					.append(String.format("%.12g", currentAbsSum)).append(",")
					.append(String.format("%.12g", currentAbsMax)).append(",")
					.append(csv(formatComplex(fromCurrent.a_0) + ";" + formatComplex(fromCurrent.b_1) + ";"
							+ formatComplex(fromCurrent.c_2) + ";" + formatComplex(toCurrent.a_0) + ";"
							+ formatComplex(toCurrent.b_1) + ";" + formatComplex(toCurrent.c_2))).append(",")
					.append(csv(formatComplex(fromPower.a_0) + ";" + formatComplex(fromPower.b_1) + ";"
							+ formatComplex(fromPower.c_2) + ";" + formatComplex(toPower.a_0) + ";"
							+ formatComplex(toPower.b_1) + ";" + formatComplex(toPower.c_2))).append("\n");
		}
		return builder.toString();
	}

	private static Complex3x1 physicalPhasePowers(Complex3x1 voltagePu, double baseVoltageLl, Complex3x1 currentA) {
		double phaseBaseV = baseVoltageLl / Math.sqrt(3.0);
		return new Complex3x1(
				voltagePu.a_0.multiply(phaseBaseV).multiply(currentA.a_0.conjugate()).divide(1000.0),
				voltagePu.b_1.multiply(phaseBaseV).multiply(currentA.b_1.conjugate()).divide(1000.0),
				voltagePu.c_2.multiply(phaseBaseV).multiply(currentA.c_2.conjugate()).divide(1000.0));
	}

	private static Complex3x1 phasePowers(Complex3x1 voltage, Complex3x1 current) {
		return new Complex3x1(
				voltage.a_0.multiply(current.a_0.conjugate()),
				voltage.b_1.multiply(current.b_1.conjugate()),
				voltage.c_2.multiply(current.c_2.conjugate()));
	}

	private static void appendInterpssLoadRow(StringBuilder builder, String name, String bus, String loadClass,
			String code, String connection, String phase, double nominalKv, double vminpu, double vmaxpu,
			Complex3x1 currentPu, Complex3x1 powerPu, double baseKva1P, double baseCurrentA) {
		Complex totalPower = powerPu.a_0.add(powerPu.b_1).add(powerPu.c_2).multiply(baseKva1P);
		Complex currentA = currentPu.a_0.multiply(baseCurrentA);
		Complex currentB = currentPu.b_1.multiply(baseCurrentA);
		Complex currentC = currentPu.c_2.multiply(baseCurrentA);
		double currentAbsSum = currentA.abs() + currentB.abs() + currentC.abs();
		double currentAbsMax = Math.max(currentA.abs(), Math.max(currentB.abs(), currentC.abs()));
		builder.append("interpss,")
				.append(csv(name.toLowerCase())).append(",")
				.append(csv(bus.toLowerCase())).append(",")
				.append(csv(loadClass)).append(",")
				.append(csv(code)).append(",")
				.append(csv(connection)).append(",")
				.append(csv(phase)).append(",")
				.append(String.format("%.12g", nominalKv)).append(",")
				.append(String.format("%.12g", vminpu)).append(",")
				.append(String.format("%.12g", vmaxpu)).append(",")
				.append(String.format("%.12g", totalPower.getReal())).append(",")
				.append(String.format("%.12g", totalPower.getImaginary())).append(",")
				.append(String.format("%.12g", currentAbsSum)).append(",")
				.append(String.format("%.12g", currentAbsMax)).append(",")
				.append(csv(formatComplex(currentA) + ";" + formatComplex(currentB) + ";" + formatComplex(currentC))).append(",")
				.append(csv(formatComplex(powerPu.a_0.multiply(baseKva1P)) + ";"
						+ formatComplex(powerPu.b_1.multiply(baseKva1P)) + ";"
						+ formatComplex(powerPu.c_2.multiply(baseKva1P)))).append("\n");
	}

	private static String csv(String value) {
		if(value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
			return value;
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private static void printSourcePathVoltageErrors(DStabNetwork3Phase distNet, List<VoltageReference> references,
			String targetBusId, int phase, double thresholdPu) {
		Map<String, VoltageReference> byBusPhase = new HashMap<>();
		for(VoltageReference reference : references) {
			byBusPhase.put(reference.bus + "." + reference.phase, reference);
		}
		List<Branch> path = sourceToTargetPath(distNet, targetBusId);
		List<String> busIds = new ArrayList<>();
		if(!path.isEmpty()) {
			busIds.add(path.get(0).getFromBus().getId());
		}
		for(Branch branch : path) {
			busIds.add(branch.getToBus().getId());
		}
		double previousError = Double.NaN;
		System.out.println("IEEE8500 source-to-" + targetBusId + " phase-" + phase
				+ " voltage error path points above " + thresholdPu + " pu:");
		for(String busId : busIds) {
			VoltageReference reference = byBusPhase.get(busId + "." + phase);
			DStab3PBus bus = distNet.getBus(busId);
			if(reference == null || bus == null) {
				continue;
			}
			Complex voltage = phaseVoltage(bus.get3PhaseVotlages(), phase);
			double error = voltage.abs() - reference.vmagPu;
			double errorStep = Double.isNaN(previousError) ? 0.0 : error - previousError;
			previousError = error;
			if(Math.abs(error) >= thresholdPu || Math.abs(errorStep) >= 5.0e-4) {
				System.out.printf("  %s.%d InterPSS=%.9f DSS=%.9f dV=%.9f dVstep=%.9f%n",
						busId, phase, voltage.abs(), reference.vmagPu, error, errorStep);
			}
		}
	}

	private static void printSourcePathVoltageDropErrors(DStabNetwork3Phase distNet, List<VoltageReference> references,
			String targetBusId, int phase, double thresholdPu) {
		Map<String, VoltageReference> byBusPhase = new HashMap<>();
		for(VoltageReference reference : references) {
			byBusPhase.put(reference.bus + "." + reference.phase, reference);
		}
		List<Branch> path = sourceToTargetPath(distNet, targetBusId);
		String upstreamBusId = path.isEmpty() ? null : path.get(0).getFromBus().getId();
		System.out.println("IEEE8500 source-to-" + targetBusId + " phase-" + phase
				+ " complex voltage-drop differences above " + thresholdPu + " pu:");
		for(Branch branch : path) {
			if(upstreamBusId == null) {
				break;
			}
			BaseAclfBus<?, ?> upstreamBus = (BaseAclfBus<?, ?>) (branch.getFromBus().getId().equals(upstreamBusId)
					? branch.getFromBus() : branch.getToBus());
			BaseAclfBus<?, ?> downstreamBus = (BaseAclfBus<?, ?>) (branch.getFromBus().getId().equals(upstreamBusId)
					? branch.getToBus() : branch.getFromBus());
			VoltageReference upstreamRef = byBusPhase.get(upstreamBus.getId() + "." + phase);
			VoltageReference downstreamRef = byBusPhase.get(downstreamBus.getId() + "." + phase);
			if(upstreamRef != null && downstreamRef != null) {
				Complex interpssDrop = phaseVoltage(((DStab3PBus) upstreamBus).get3PhaseVotlages(), phase)
						.subtract(phaseVoltage(((DStab3PBus) downstreamBus).get3PhaseVotlages(), phase));
				Complex dssDrop = upstreamRef.phasor().subtract(downstreamRef.phasor());
				Complex dropDiff = interpssDrop.subtract(dssDrop);
				if(dropDiff.abs() >= thresholdPu) {
					System.out.printf("  %s -> %s name=%s type=%s dDrop=%s |dDrop|=%.9f IPSSdrop=%s DSSdrop=%s%n",
							upstreamBus.getId(), downstreamBus.getId(), branch.getName(),
							((DStab3PBranch) branch).isXfr() ? "xfr" : "line",
							complexSummary(dropDiff), dropDiff.abs(),
							complexSummary(interpssDrop), complexSummary(dssDrop));
				}
			}
			upstreamBusId = downstreamBus.getId();
		}
	}

	private static void printIncidentBranches(DStabNetwork3Phase distNet, String busId) {
		DStab3PBus bus = distNet.getBus(busId);
		assertNotNull(bus, "Missing incident-branch bus: " + busId);
		System.out.println("IEEE8500 incident branches at " + busId + ":");
		for(Branch branch : bus.getBranchIterable()) {
			if(!branch.isActive()) {
				continue;
			}
			DStab3PBranch branch3p = (DStab3PBranch) branch;
			System.out.println("  " + branch.getFromBus().getId() + " -> " + branch.getToBus().getId()
					+ " id=" + branch.getId()
					+ " name=" + branch.getName()
					+ " phase=" + branch3p.getPhaseCode()
					+ " type=" + (branch3p.isXfr() ? "xfr" : "line")
					+ " yff=" + branch3p.getYffabc().absMax()
					+ " yft=" + branch3p.getYftabc().absMax()
					+ " ytf=" + branch3p.getYtfabc().absMax()
					+ " ytt=" + branch3p.getYttabc().absMax()
					+ " Ifrom=" + phaseValues(branch3p.calc3PhaseCurrentFrom2To())
					+ " Ito=" + phaseValues(branch3p.calc3PhaseCurrentTo2From()));
		}
		System.out.println("  loadInjection=" + phaseValues(bus.calc3PhEquivCurInj()));
		System.out.println("  voltage=" + phaseMagnitudes(bus.get3PhaseVotlages()));
	}

	private static void printPhysicalBranchDiagnostic(DStabNetwork3Phase distNet, String branchName) {
		DStab3PBranch branch = findBranchByName(distNet, branchName);
		assertNotNull(branch, "Missing physical branch diagnostic target: " + branchName);
		DStab3PBus fromBus = (DStab3PBus) branch.getFromBus();
		DStab3PBus toBus = (DStab3PBus) branch.getToBus();
		double baseVa = distNet.getBaseKva() * 1000.0;
		double fromVbase = fromBus.getBaseVoltage();
		double toVbase = toBus.getBaseVoltage();
		double fromIbase = baseVa / (Math.sqrt(3.0) * fromVbase);
		double toIbase = baseVa / (Math.sqrt(3.0) * toVbase);

		System.out.println("IEEE8500 physical branch diagnostic: " + branchName
				+ " id=" + branch.getId()
				+ " name=" + branch.getName()
				+ " type=" + (branch.isXfr() ? "xfr" : "line")
				+ " from=" + fromBus.getId() + " baseVll=" + fromVbase
				+ " to=" + toBus.getId() + " baseVll=" + toVbase
				+ " fromIbase=" + fromIbase
				+ " toIbase=" + toIbase);
		System.out.println("  fromVphys=" + physicalVoltageValues(fromBus.get3PhaseVotlages(), fromVbase));
		System.out.println("  toVphys=" + physicalVoltageValues(toBus.get3PhaseVotlages(), toVbase));
		System.out.println("  fromIphys=" + physicalCurrentValues(branch.calc3PhaseCurrentFrom2To(), fromIbase));
		System.out.println("  toIphys=" + physicalCurrentValues(branch.calc3PhaseCurrentTo2From(), toIbase));
		printPhysicalYBlock("  Yff phys", branch.getYffabc(), baseVa, fromVbase, fromVbase);
		printPhysicalYBlock("  Yft phys", branch.getYftabc(), baseVa, fromVbase, toVbase);
		printPhysicalYBlock("  Ytf phys", branch.getYtfabc(), baseVa, toVbase, fromVbase);
		printPhysicalYBlock("  Ytt phys", branch.getYttabc(), baseVa, toVbase, toVbase);
	}

	private static void printPhysicalBranchYDiagnostic(DStabNetwork3Phase distNet, String branchName) {
		DStab3PBranch branch = findBranchByName(distNet, branchName);
		assertNotNull(branch, "Missing physical branch Y diagnostic target: " + branchName);
		DStab3PBus fromBus = (DStab3PBus) branch.getFromBus();
		DStab3PBus toBus = (DStab3PBus) branch.getToBus();
		double baseVa = distNet.getBaseKva() * 1000.0;
		double fromVbase = fromBus.getBaseVoltage();
		double toVbase = toBus.getBaseVoltage();

		System.out.println("IEEE8500 physical branch Y diagnostic: " + branchName
				+ " id=" + branch.getId()
				+ " name=" + branch.getName()
				+ " phase=" + branch.getPhaseCode()
				+ " type=" + (branch.isXfr() ? "xfr" : "line")
				+ " from=" + fromBus.getId() + " baseVll=" + fromVbase
				+ " to=" + toBus.getId() + " baseVll=" + toVbase);
		printPhysicalYBlock("  Yff phys", branch.getYffabc(), baseVa, fromVbase, fromVbase);
		printPhysicalYBlock("  Yft phys", branch.getYftabc(), baseVa, fromVbase, toVbase);
		printPhysicalYBlock("  Ytf phys", branch.getYtfabc(), baseVa, toVbase, fromVbase);
		printPhysicalYBlock("  Ytt phys", branch.getYttabc(), baseVa, toVbase, toVbase);
	}

	private static String physicalVoltageValues(Complex3x1 value, double baseVoltageLl) {
		double phaseBase = baseVoltageLl / Math.sqrt(3.0);
		return phaseValues(value.multiply(phaseBase));
	}

	private static String physicalCurrentValues(Complex3x1 value, double currentBase) {
		return phaseValues(value.multiply(currentBase));
	}

	private static void printPhysicalYBlock(String label, Complex3x3 ypu, double baseVa,
			double currentSideVbaseLl, double voltageSideVbaseLl) {
		printMatrix(label, ypu.multiply(physicalYScale(baseVa, currentSideVbaseLl, voltageSideVbaseLl)));
	}

	private static Complex physicalY(Complex ypu, double baseVa, double currentSideVbaseLl,
			double voltageSideVbaseLl) {
		return ypu.multiply(physicalYScale(baseVa, currentSideVbaseLl, voltageSideVbaseLl));
	}

	private static Complex3x3 physicalY(Complex3x3 ypu, double baseVa, double vbaseLl) {
		return ypu.multiply(physicalYScale(baseVa, vbaseLl, vbaseLl));
	}

	private static double physicalYScale(double baseVa, double currentSideVbaseLl, double voltageSideVbaseLl) {
		return baseVa / (currentSideVbaseLl * voltageSideVbaseLl);
	}

	private static void printSourcePathBranchCurrents(DStabNetwork3Phase distNet, String targetBusId,
			String startBusId) {
		List<Branch> path = sourceToTargetPath(distNet, targetBusId);
		double baseVa = distNet.getBaseKva() * 1000.0;
		String upstreamBusId = path.isEmpty() ? null : path.get(0).getFromBus().getId();
		boolean printing = startBusId == null;
		System.out.println("IEEE8500 InterPSS downstream path currents to " + targetBusId
				+ " starting at " + startBusId);
		System.out.println("seq,element,type,fromBus,toBus,phase,fromIA,fromIB,fromIC,toIA,toIB,toIC");
		int seq = 0;
		for(Branch branch : path) {
			if(upstreamBusId == null) {
				break;
			}
			DStab3PBus upstreamBus = (DStab3PBus) (branch.getFromBus().getId().equals(upstreamBusId)
					? branch.getFromBus() : branch.getToBus());
			DStab3PBus downstreamBus = (DStab3PBus) (branch.getFromBus().getId().equals(upstreamBusId)
					? branch.getToBus() : branch.getFromBus());
			if(startBusId != null && upstreamBus.getId().equals(startBusId)) {
				printing = true;
			}
			if(printing) {
				DStab3PBranch branch3p = (DStab3PBranch) branch;
				double fromIbase = baseVa / (Math.sqrt(3.0) * upstreamBus.getBaseVoltage());
				double toIbase = baseVa / (Math.sqrt(3.0) * downstreamBus.getBaseVoltage());
				Complex3x1 fromCurrent = branch.getFromBus().getId().equals(upstreamBus.getId())
						? branch3p.calc3PhaseCurrentFrom2To()
						: branch3p.calc3PhaseCurrentTo2From();
				Complex3x1 toCurrent = branch.getFromBus().getId().equals(upstreamBus.getId())
						? branch3p.calc3PhaseCurrentTo2From()
						: branch3p.calc3PhaseCurrentFrom2To();
				System.out.println(csv(seq, branch.getName(), branch3p.isXfr() ? "Transformer" : "Line",
						upstreamBus.getId(), downstreamBus.getId(), branch3p.getPhaseCode(),
						fromCurrent.multiply(fromIbase), toCurrent.multiply(toIbase)));
			}
			upstreamBusId = downstreamBus.getId();
			seq++;
		}
	}

	private static void printPhasePathBranchCurrents(DStabNetwork3Phase distNet, String startBusId,
			String targetBusId, int phase) {
		List<Branch> path = phasePath(distNet, startBusId, targetBusId, phase);
		double baseVa = distNet.getBaseKva() * 1000.0;
		String upstreamBusId = startBusId;
		System.out.println("IEEE8500 InterPSS phase-" + phase + " path currents from " + startBusId
				+ " to " + targetBusId);
		System.out.println("seq,element,type,fromBus,toBus,phase,fromIA,fromIB,fromIC,toIA,toIB,toIC");
		int seq = 0;
		for(Branch branch : path) {
			DStab3PBus upstreamBus = (DStab3PBus) (branch.getFromBus().getId().equals(upstreamBusId)
					? branch.getFromBus() : branch.getToBus());
			DStab3PBus downstreamBus = (DStab3PBus) (branch.getFromBus().getId().equals(upstreamBusId)
					? branch.getToBus() : branch.getFromBus());
			DStab3PBranch branch3p = (DStab3PBranch) branch;
			double fromIbase = baseVa / (Math.sqrt(3.0) * upstreamBus.getBaseVoltage());
			double toIbase = baseVa / (Math.sqrt(3.0) * downstreamBus.getBaseVoltage());
			Complex3x1 fromCurrent = branch.getFromBus().getId().equals(upstreamBus.getId())
					? branch3p.calc3PhaseCurrentFrom2To()
					: branch3p.calc3PhaseCurrentTo2From();
			Complex3x1 toCurrent = branch.getFromBus().getId().equals(upstreamBus.getId())
					? branch3p.calc3PhaseCurrentTo2From()
					: branch3p.calc3PhaseCurrentFrom2To();
			System.out.println(csv(seq, branch.getName(), branch3p.isXfr() ? "Transformer" : "Line",
					upstreamBus.getId(), downstreamBus.getId(), branch3p.getPhaseCode(),
					fromCurrent.multiply(fromIbase), toCurrent.multiply(toIbase)));
			upstreamBusId = downstreamBus.getId();
			seq++;
		}
	}

	private static void printThreePhaseCurrentBalance(DStabNetwork3Phase distNet, String busId) {
		DStab3PBus bus = distNet.getBus(busId);
		assertNotNull(bus, "Missing current-balance bus: " + busId);
		double baseVa = distNet.getBaseKva() * 1000.0;
		double currentBase = baseVa / (Math.sqrt(3.0) * bus.getBaseVoltage());
		Complex3x1 branchCurrent = new Complex3x1();
		System.out.println("IEEE8500 3-phase current balance at " + busId
				+ " baseVll=" + bus.getBaseVoltage()
				+ " Ibase=" + currentBase
				+ " Vpu=" + phaseValues(bus.get3PhaseVotlages())
				+ " Vphys=" + physicalVoltageValues(bus.get3PhaseVotlages(), bus.getBaseVoltage()));
		for(Branch branchObj : bus.getBranchIterable()) {
			if(!branchObj.isActive()) {
				continue;
			}
			DStab3PBranch branch = (DStab3PBranch) branchObj;
			boolean fromSide = branch.getFromBus().getId().equals(busId);
			Complex3x1 current = fromSide
					? branch.calc3PhaseCurrentFrom2To()
					: branch.calc3PhaseCurrentTo2From();
			branchCurrent = branchCurrent.add(current);
			System.out.println("  branch " + (fromSide ? "from" : "to")
					+ " " + branch.getFromBus().getId() + " -> " + branch.getToBus().getId()
					+ " name=" + branch.getName()
					+ " phase=" + branch.getPhaseCode()
					+ " Ipu=" + phaseValues(current)
					+ " Iphys=" + physicalCurrentValues(current, currentBase));
		}
		Complex3x1 injection = bus.calc3PhEquivCurInj();
		Complex3x1 residual = branchCurrent.subtract(injection);
		System.out.println("  branchSumIpu=" + phaseValues(branchCurrent)
				+ " branchSumIphys=" + physicalCurrentValues(branchCurrent, currentBase));
		System.out.println("  injectionIpu=" + phaseValues(injection)
				+ " injectionIphys=" + physicalCurrentValues(injection, currentBase));
		System.out.println("  residualIpu=" + phaseValues(residual)
				+ " residualIphys=" + physicalCurrentValues(residual, currentBase));
		for(DStab3PLoad load : bus.getThreePhaseLoadList()) {
			System.out.println("  load " + load.getId()
					+ " code=" + load.getCode()
					+ " phase=" + load.getPhaseCode()
					+ " conn=" + load.getLoadConnectionType()
					+ " nominalKV=" + load.getNominalKV()
					+ " initLoadPu=" + phaseValues(load.getInit3PhaseLoad()));
		}
	}

	private static List<Branch> phasePath(DStabNetwork3Phase distNet, String startBusId, String targetBusId,
			int phase) {
		ArrayDeque<String> queue = new ArrayDeque<>();
		Map<String, Branch> previousBranch = new HashMap<>();
		Map<String, String> previousBus = new HashMap<>();
		Set<String> visited = new HashSet<>();
		queue.add(startBusId);
		visited.add(startBusId);
		while(!queue.isEmpty() && !visited.contains(targetBusId)) {
			String busId = queue.removeFirst();
			DStab3PBus bus = distNet.getBus(busId);
			if(bus == null) {
				continue;
			}
			for(Branch branchObj : bus.getBranchIterable()) {
				if(!branchObj.isActive()) {
					continue;
				}
				DStab3PBranch branch = (DStab3PBranch) branchObj;
				if(!branchHasPhase(branch, phase)) {
					continue;
				}
				String otherBusId = branch.getFromBus().getId().equals(busId)
						? branch.getToBus().getId() : branch.getFromBus().getId();
				if(visited.add(otherBusId)) {
					previousBranch.put(otherBusId, branchObj);
					previousBus.put(otherBusId, busId);
					queue.add(otherBusId);
				}
			}
		}
		assertTrue(visited.contains(targetBusId),
				"No phase-" + phase + " path found from " + startBusId + " to " + targetBusId);
		List<Branch> path = new ArrayList<>();
		String cursor = targetBusId;
		while(!cursor.equals(startBusId)) {
			path.add(previousBranch.get(cursor));
			cursor = previousBus.get(cursor);
		}
		Collections.reverse(path);
		return path;
	}

	private static boolean branchHasPhase(DStab3PBranch branch, int phase) {
		PhaseCode code = branch.getPhaseCode();
		if(code == PhaseCode.ABC) {
			return true;
		}
		if(phase == 1) {
			return code == PhaseCode.A || code == PhaseCode.AB || code == PhaseCode.AC;
		}
		if(phase == 2) {
			return code == PhaseCode.B || code == PhaseCode.AB || code == PhaseCode.BC;
		}
		if(phase == 3) {
			return code == PhaseCode.C || code == PhaseCode.AC || code == PhaseCode.BC;
		}
		return false;
	}

	private static String csv(int seq, String elementName, String type, String fromBus, String toBus,
			PhaseCode phaseCode, Complex3x1 fromCurrent, Complex3x1 toCurrent) {
		return seq + "," + elementName + "," + type + "," + fromBus + "," + toBus + "," + phaseCode
				+ "," + complexCsv(fromCurrent.a_0)
				+ "," + complexCsv(fromCurrent.b_1)
				+ "," + complexCsv(fromCurrent.c_2)
				+ "," + complexCsv(toCurrent.a_0)
				+ "," + complexCsv(toCurrent.b_1)
				+ "," + complexCsv(toCurrent.c_2);
	}

	private static String complexCsv(Complex value) {
		return String.format("%.9f%+.9fj", value.getReal(), value.getImaginary());
	}

	private static void applySolvedIeee123RegulatorTaps(OpenDSSDataParser parser) {
		setToTap(parser, "reg1a", 1.0375);
		setToTap(parser, "reg2a", 1.0);
		setToTap(parser, "reg3a", 1.0125);
		setToTap(parser, "reg4a", 1.0625);
		setToTap(parser, "reg3c", 1.0);
		setToTap(parser, "reg4b", 1.025);
		setToTap(parser, "reg4c", 1.0375);
	}

	private static void applySolvedIeee13RegulatorTaps(OpenDSSDataParser parser) {
		setToTap(parser, "reg1", 1.0625);
		setToTap(parser, "reg2", 1.0500);
		setToTap(parser, "reg3", 1.06875);
	}

	private static void setToTap(OpenDSSDataParser parser, String branchName, double tap) {
		AclfBranch branch = parser.getThreePhaseBranchByName(branchName);
		assertNotNull(branch, "Missing regulator branch: " + branchName);
		AclfBranch tapBranch = activeRegulatorTapBranch(parser, branch);
		if(tapBranch instanceof IBranch3Phase && ((IBranch3Phase) tapBranch).hasPhaseTurnRatio()) {
			IBranch3Phase branch3P = (IBranch3Phase) tapBranch;
			double[] ratios = branch3P.getToTurnRatioABC();
			PhaseCode requestedPhase = branch instanceof IBranch3Phase
					? ((IBranch3Phase) branch).getPhaseCode()
					: branch3P.getPhaseCode();
			setPhaseTap(ratios, requestedPhase, tap);
			branch3P.setToTurnRatioABC(ratios[0], ratios[1], ratios[2]);
			assertPhaseTap(branch3P.getToTurnRatioABC(), requestedPhase, tap, branchName);
		}
		else {
			tapBranch.setToTurnRatio(tap);
			assertEquals(tap, tapBranch.getToTurnRatio(), 1.0e-10, "Regulator tap was not applied: " + branchName);
		}
	}

	private static AclfBranch activeRegulatorTapBranch(OpenDSSDataParser parser, AclfBranch branch) {
		if(branch.isActive()) {
			return branch;
		}
		if(!(branch instanceof IBranch3Phase)) {
			return branch;
		}
		List<? extends AclfBranch> branches = parser.isStaticNetworkMode()
				? parser.getStaticNetwork().getBranchList()
				: parser.getDistNetwork().getBranchList();
		for(AclfBranch candidate : branches) {
			if(candidate.isActive()
					&& candidate instanceof IBranch3Phase
					&& ((IBranch3Phase) candidate).hasPhaseTurnRatio()
					&& candidate.getFromBus().getId().equals(branch.getFromBus().getId())
					&& candidate.getToBus().getId().equals(branch.getToBus().getId())) {
				return candidate;
			}
		}
		return branch;
	}

	private static void setPhaseTap(double[] ratios, PhaseCode phaseCode, double tap) {
		for(int phase = 1; phase <= 3; phase++) {
			if(phaseCodeIncludes(phaseCode, phase)) {
				ratios[phase - 1] = tap;
			}
		}
	}

	private static void assertPhaseTap(double[] ratios, PhaseCode phaseCode, double tap, String branchName) {
		for(int phase = 1; phase <= 3; phase++) {
			if(phaseCodeIncludes(phaseCode, phase)) {
				assertEquals(tap, ratios[phase - 1], 1.0e-10,
						"Regulator tap was not applied: " + branchName + " phase " + phase);
			}
		}
	}

	private static boolean phaseCodeIncludes(PhaseCode code, int phase) {
		if(code == PhaseCode.ABC) {
			return true;
		}
		if(phase == 1) {
			return code == PhaseCode.A || code == PhaseCode.AB || code == PhaseCode.AC;
		}
		if(phase == 2) {
			return code == PhaseCode.B || code == PhaseCode.AB || code == PhaseCode.BC;
		}
		if(phase == 3) {
			return code == PhaseCode.C || code == PhaseCode.AC || code == PhaseCode.BC;
		}
		return false;
	}

	private enum OpenDssTapProfile {
		NONE,
		IEEE13_SOLVED,
		IEEE123_DSS_PYTHON
	}

	private static List<VoltageReference> readReferences(String resourcePath) throws IOException {
		return OpenDssDataQaUtils.readVoltageReferences(
				OpenDssParserPowerFlowComparisonTest.class, resourcePath);
	}

	private static Complex phaseVoltage(Complex3x1 voltage, int phase) {
		return OpenDssDataQaUtils.phaseVoltage(voltage, phase);
	}

	private static double wrappedAngleDeg(double angleDeg) {
		return OpenDssDataQaUtils.wrappedAngleDeg(angleDeg);
	}

	private static CurrentMismatch maxCurrentMismatch(DStabNetwork3Phase distNet) {
		double maxAbs = 0.0;
		String label = "";
		for(Object busObj : distNet.getBusList()) {
			DStab3PBus bus = (DStab3PBus) busObj;
			if(!bus.isActive() || bus.isSwing()) {
				continue;
			}

			Complex3x1 branchCurrent = new Complex3x1();
			for(Branch branchObj : bus.getBranchIterable()) {
				if(!branchObj.isActive()) {
					continue;
				}
				DStab3PBranch branch = (DStab3PBranch) branchObj;
				if(branch.getFromBus().getId().equals(bus.getId())) {
					branchCurrent = branchCurrent.add(branch.calc3PhaseCurrentFrom2To());
				}
				else {
					branchCurrent = branchCurrent.add(branch.calc3PhaseCurrentTo2From());
				}
			}

			Complex3x1 busCurrentInjection = bus.calc3PhEquivCurInj();
			Complex3x1 residual = branchCurrent.subtract(busCurrentInjection);
			double abs = residual.absMax();
			if(abs > maxAbs) {
				maxAbs = abs;
				label = bus.getId();
			}
		}
		return new CurrentMismatch(maxAbs, label);
	}

	private static void printTopCurrentMismatchBuses(DStabNetwork3Phase distNet, String label, int count) {
		List<CurrentMismatchRecord> records = new ArrayList<>();
		for(Object busObj : distNet.getBusList()) {
			DStab3PBus bus = (DStab3PBus) busObj;
			if(!bus.isActive() || bus.isSwing()) {
				continue;
			}

			Complex3x1 branchCurrent = new Complex3x1();
			for(Branch branchObj : bus.getBranchIterable()) {
				if(!branchObj.isActive()) {
					continue;
				}
				DStab3PBranch branch = (DStab3PBranch) branchObj;
				if(branch.getFromBus().getId().equals(bus.getId())) {
					branchCurrent = branchCurrent.add(branch.calc3PhaseCurrentFrom2To());
				}
				else {
					branchCurrent = branchCurrent.add(branch.calc3PhaseCurrentTo2From());
				}
			}

			Complex3x1 injection = bus.calc3PhEquivCurInj();
			Complex3x1 residual = branchCurrent.subtract(injection);
			records.add(new CurrentMismatchRecord(bus.getId(), residual, branchCurrent, injection,
					bus.get3PhaseVotlages()));
		}
		records.sort(Comparator.comparingDouble(CurrentMismatchRecord::maxAbs).reversed());
		int printed = Math.min(count, records.size());
		for(int i = 0; i < printed; i++) {
			System.out.println(records.get(i).summary(label, i + 1));
		}
	}

	private static VoltageRange voltageRange(DStabNetwork3Phase distNet) {
		double min = Double.POSITIVE_INFINITY;
		double max = 0.0;
		String minLabel = "";
		String maxLabel = "";
		for(Object busObj : distNet.getBusList()) {
			DStab3PBus bus = (DStab3PBus) busObj;
			if(!bus.isActive()) {
				continue;
			}
			Complex3x1 voltage = bus.get3PhaseVotlages();
			for(int phase = 0; phase < 3; phase++) {
				double abs = phaseVoltage(voltage, phase + 1).abs();
				if(abs < min && abs > 1.0e-9) {
					min = abs;
					minLabel = bus.getId() + "." + phaseLabel(phase);
				}
				if(abs > max) {
					max = abs;
					maxLabel = bus.getId() + "." + phaseLabel(phase);
				}
			}
		}
		return new VoltageRange(min, minLabel, max, maxLabel);
	}

	private static CurrentMismatch maxStoredSweepCurrentMismatch(DStabNetwork3Phase distNet) {
		double maxAbs = 0.0;
		String label = "";
		for(Object busObj : distNet.getBusList()) {
			DStab3PBus bus = (DStab3PBus) busObj;
			if(!bus.isActive() || bus.isSwing()) {
				continue;
			}

			Complex3x1 branchCurrent = new Complex3x1();
			for(Branch branchObj : bus.getBranchIterable()) {
				if(!branchObj.isActive()) {
					continue;
				}
				DStab3PBranch branch = (DStab3PBranch) branchObj;
				if(branch.getFromBus().getId().equals(bus.getId())) {
					branchCurrent = branchCurrent.add(currentOrZero(branch.getCurrentAbcAtFromSide()));
				}
				else {
					branchCurrent = branchCurrent.add(currentOrZero(branch.getCurrentAbcAtToSide()).multiply(-1.0));
				}
			}

			Complex3x1 residual = branchCurrent.subtract(bus.calc3PhEquivCurInj());
			double abs = residual.absMax();
			if(abs > maxAbs) {
				maxAbs = abs;
				label = bus.getId();
			}
		}
		return new CurrentMismatch(maxAbs, label);
	}

	private static Complex3x1 currentOrZero(Complex3x1 current) {
		return current == null ? new Complex3x1() : current;
	}

	private static String regulatorPingPongSummary(String branchName, DStabNetwork3Phase fixedPointNet,
			DStabNetwork3Phase fbsNet) {
		DStab3PBranch fpBranch = findBranchByName(fixedPointNet, branchName);
		DStab3PBranch fbsBranch = findBranchByName(fbsNet, branchName);
		assertNotNull(fpBranch, "Missing fixed-point regulator branch: " + branchName);
		assertNotNull(fbsBranch, "Missing FBS regulator branch: " + branchName);

		String fromBusId = fpBranch.getFromBus().getId();
		String toBusId = fpBranch.getToBus().getId();
		DStab3PBus fpFromBus = fixedPointNet.getBus(fromBusId);
		DStab3PBus fpToBus = fixedPointNet.getBus(toBusId);
		DStab3PBus fbsFromBus = fbsNet.getBus(fromBusId);
		DStab3PBus fbsToBus = fbsNet.getBus(toBusId);

		Complex3x1 fromDiff = fbsFromBus.get3PhaseVotlages().subtract(fpFromBus.get3PhaseVotlages());
		Complex3x1 toDiff = fbsToBus.get3PhaseVotlages().subtract(fpToBus.get3PhaseVotlages());
		return String.format(
				"%s id=%s name=%s phase=%s xfr=%s line=%s phaseTap=%s %s->%s tap=%.5f FP/FBS max |dV| from=%.6f to=%.6f FP from=%s FBS from=%s FP to=%s FBS to=%s",
				branchName,
				fbsBranch.getId(),
				fbsBranch.getName(),
				fbsBranch.getPhaseCode(),
				fbsBranch.isXfr(),
				fbsBranch.isLine(),
				fbsBranch.hasPhaseTurnRatio(),
				fromBusId,
				toBusId,
				fbsBranch.getToTurnRatio(),
				fromDiff.absMax(),
				toDiff.absMax(),
				phaseMagnitudes(fpFromBus.get3PhaseVotlages()),
				phaseMagnitudes(fbsFromBus.get3PhaseVotlages()),
				phaseMagnitudes(fpToBus.get3PhaseVotlages()),
				phaseMagnitudes(fbsToBus.get3PhaseVotlages()));
	}

	private static String busComparisonSummary(String busId, DStabNetwork3Phase fixedPointNet, DStabNetwork3Phase fbsNet) {
		DStab3PBus fpBus = fixedPointNet.getBus(busId);
		DStab3PBus fbsBus = fbsNet.getBus(busId);
		assertNotNull(fpBus, "Missing fixed-point bus: " + busId);
		assertNotNull(fbsBus, "Missing FBS bus: " + busId);

		return String.format("bus %s FP V=%s Iinj=%s FBS V=%s Iinj=%s dIinj=%s",
				busId,
				phaseMagnitudes(fpBus.get3PhaseVotlages()),
				phaseValues(fpBus.calc3PhEquivCurInj()),
				phaseMagnitudes(fbsBus.get3PhaseVotlages()),
				phaseValues(fbsBus.calc3PhEquivCurInj()),
				phaseValues(fbsBus.calc3PhEquivCurInj().subtract(fpBus.calc3PhEquivCurInj())));
	}

	private static void assertBus21MatchesDssPython(Complex3x1 voltage, double tolerance, String methodName) {
		assertEquals(0.991993571001, voltage.a_0.abs(), tolerance,
				methodName + " bus 21 phase A should match DSS-Python");
		assertEquals(1.026056067460, voltage.b_1.abs(), tolerance,
				methodName + " bus 21 phase B should match DSS-Python");
		assertEquals(1.004793197194, voltage.c_2.abs(), tolerance,
				methodName + " bus 21 phase C should match DSS-Python");
	}

	private static DStab3PBranch findBranchByName(DStabNetwork3Phase distNet, String branchName) {
		for(Object branchObj : distNet.getBranchList()) {
			DStab3PBranch branch = (DStab3PBranch) branchObj;
			String id = branch.getId().toLowerCase();
			String name = branch.getName() == null ? "" : branch.getName().toLowerCase();
			if(id.equals(branchName) || id.endsWith(":" + branchName) || name.equals(branchName)) {
				return branch;
			}
		}
		return null;
	}

	private static String phaseMagnitudes(Complex3x1 voltage) {
		return String.format("[%.6f, %.6f, %.6f]",
				voltage.a_0.abs(), voltage.b_1.abs(), voltage.c_2.abs());
	}

	private static String phaseValues(Complex3x1 value) {
		return String.format("[%.6f%+.6fi, %.6f%+.6fi, %.6f%+.6fi]",
				value.a_0.getReal(), value.a_0.getImaginary(),
				value.b_1.getReal(), value.b_1.getImaginary(),
				value.c_2.getReal(), value.c_2.getImaginary());
	}

	private static String complexSummary(Complex value) {
		return String.format("%.9f%+.9fi", value.getReal(), value.getImaginary());
	}

	private static final class VoltageMismatch {
		private final VoltageReference reference;
		private final double interpssMag;
		private final double interpssAngleDeg;

		private VoltageMismatch(VoltageReference reference, double interpssMag, double interpssAngleDeg) {
			this.reference = reference;
			this.interpssMag = interpssMag;
			this.interpssAngleDeg = interpssAngleDeg;
		}

		private double magError() {
			return Math.abs(this.interpssMag - this.reference.vmagPu);
		}

		private double angleError() {
			return Math.abs(wrappedAngleDeg(this.interpssAngleDeg - this.reference.angleDeg));
		}

		private String summary() {
			return String.format("%s InterPSS |V|=%.9f DSS |V|=%.9f dV=%.9f dAng=%.6f",
					this.reference.label(), this.interpssMag, this.reference.vmagPu,
					this.interpssMag - this.reference.vmagPu, angleError());
		}
	}

	private static final class CurrentMismatch {
		private final double maxAbs;
		private final String label;

		private CurrentMismatch(double maxAbs, String label) {
			this.maxAbs = maxAbs;
			this.label = label;
		}

		private String summary(String caseName) {
			return String.format("%s current mismatch: max %.9g pu at %s",
					caseName, this.maxAbs, this.label);
		}
	}

	private static final class CurrentMismatchRecord {
		private final String busId;
		private final Complex3x1 residual;
		private final Complex3x1 branchCurrent;
		private final Complex3x1 injection;
		private final Complex3x1 voltage;

		private CurrentMismatchRecord(String busId, Complex3x1 residual, Complex3x1 branchCurrent,
				Complex3x1 injection, Complex3x1 voltage) {
			this.busId = busId;
			this.residual = residual;
			this.branchCurrent = branchCurrent;
			this.injection = injection;
			this.voltage = voltage;
		}

		private double maxAbs() {
			return this.residual.absMax();
		}

		private String summary(String label, int rank) {
			return String.format(
					"%s current mismatch #%d bus=%s max=%.9g residual=%s branchCurrent=%s injection=%s voltageMag=%s",
					label, rank, this.busId, maxAbs(), phaseValues(this.residual),
					phaseValues(this.branchCurrent), phaseValues(this.injection), phaseMagnitudes(this.voltage));
		}
	}

	private static final class VoltageRange {
		private final double min;
		private final String minLabel;
		private final double max;
		private final String maxLabel;

		private VoltageRange(double min, String minLabel, double max, String maxLabel) {
			this.min = min;
			this.minLabel = minLabel;
			this.max = max;
			this.maxLabel = maxLabel;
		}

		private String summary(String label) {
			return String.format("%s range: min %.9g pu at %s, max %.9g pu at %s",
					label, this.min, this.minLabel, this.max, this.maxLabel);
		}
	}
}
