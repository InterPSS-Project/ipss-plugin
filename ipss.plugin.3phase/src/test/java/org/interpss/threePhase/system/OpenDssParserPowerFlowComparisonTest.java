package org.interpss.threePhase.system;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.powerflow.DistributionPFMethod;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.net.Branch;


public class OpenDssParserPowerFlowComparisonTest {
	private static final double VOLTAGE_MAG_TOLERANCE_PU = 2.0e-2;
	private static final double VOLTAGE_ANGLE_TOLERANCE_DEG = 2.0;

	@Test
	public void ieee13ParserImportsUpstreamFeeder() throws IOException {
		ComparisonResult result = assertMatchesDssPythonReference(
				"testData/feeder/IEEE13",
				"IEEE13Nodeckt.dss",
				"opendss-reference/ieee13-dss-python-voltage-reference.csv",
				VOLTAGE_MAG_TOLERANCE_PU);
		System.out.println(result.summary("IEEE13"));
	}

	@Test
	public void ieee123ParserPowerFlowMatchesDssPythonReference() throws IOException {
		ComparisonResult result = assertMatchesDssPythonReference(
				"testData/feeder/IEEE123",
				"IEEE123Master.dss",
				"opendss-reference/ieee123-dss-python-voltage-reference.csv",
				8.0e-2);
		System.out.println(result.summary("IEEE123"));
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
		assertBus21MatchesDssPython(fixedPointBus.get3PhaseVotlages(), 2.0e-4, "Fixed-point");
		assertBus21MatchesDssPython(fbsBus.get3PhaseVotlages(), 2.0e-3, "FBS");
		System.out.println("IEEE123 bus 21 phase check: FP="
				+ phaseMagnitudes(fixedPointBus.get3PhaseVotlages())
				+ " FBS="
				+ phaseMagnitudes(fbsBus.get3PhaseVotlages()));
		System.out.println(busComparisonSummary("21", fixedPointNet, fbsNet));
		System.out.println(maxCurrentMismatch(fixedPointNet).summary("IEEE123 fixed-point"));
		System.out.println(maxCurrentMismatch(fbsNet).summary("IEEE123 FBS"));
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
		OpenDSSDataParser parser = new OpenDSSDataParser();
		assertTrue(parser.parseFeederData(feederFolder, masterFile));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		if(applySolvedIeee123RegulatorTaps) {
			applySolvedIeee123RegulatorTaps(parser);
		}
		DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(DistributionPFMethod.Forward_Backword_Sweep);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.setMaxIteration(200);
		powerFlow.setTolerance(1.0e-4);
		assertTrue(powerFlow.powerflow());

		ComparisonResult result = compareVoltages(distNet, readReferences(referenceResource));
		assertTrue(result.maxMagError < voltageMagTolerancePu,
				"Max voltage magnitude error " + result.maxMagError + " at " + result.maxMagLabel);
		assertTrue(result.maxAngleError < VOLTAGE_ANGLE_TOLERANCE_DEG,
				"Max voltage angle error " + result.maxAngleError + " deg at " + result.maxAngleLabel);
		return result;
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

	private static ComparisonResult compareVoltages(DStabNetwork3Phase distNet, List<VoltageReference> references) {
		double maxMagError = 0.0;
		double maxAngleError = 0.0;
		String maxMagLabel = "";
		String maxAngleLabel = "";

		for (VoltageReference reference : references) {
			DStab3PBus bus = distNet.getBus(reference.bus);
			assertNotNull(bus, "Missing parsed bus: " + reference.bus);
			Complex voltage = phaseVoltage(bus.get3PhaseVotlages(), reference.phase);
			double magError = Math.abs(voltage.abs() - reference.vmagPu);
			double angleError = Math.abs(wrappedAngleDeg(Math.toDegrees(voltage.getArgument()) - reference.angleDeg));
			if (magError > maxMagError) {
				maxMagError = magError;
				maxMagLabel = reference.label();
			}
			if (angleError > maxAngleError) {
				maxAngleError = angleError;
				maxAngleLabel = reference.label();
			}
		}
		return new ComparisonResult(maxMagError, maxMagLabel, maxAngleError, maxAngleLabel);
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

	private static void setToTap(OpenDSSDataParser parser, String branchName, double tap) {
		DStab3PBranch branch = parser.getBranchByName(branchName);
		assertNotNull(branch, "Missing regulator branch: " + branchName);
		branch.setToTurnRatio(tap);
		assertEquals(tap, branch.getToTurnRatio(), 1.0e-10, "Regulator tap was not applied: " + branchName);
	}

	private static List<VoltageReference> readReferences(String resourcePath) throws IOException {
		InputStream stream = OpenDssParserPowerFlowComparisonTest.class.getClassLoader().getResourceAsStream(resourcePath);
		assertNotNull(stream, "Missing reference resource: " + resourcePath);
		List<VoltageReference> references = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line = reader.readLine();
			assertTrue("case,bus,phase,vmag_pu,angle_deg".equals(line), "Unexpected reference CSV header: " + line);
			while ((line = reader.readLine()) != null) {
				String[] fields = line.split(",");
				assertTrue(fields.length == 5, "Malformed reference CSV line: " + line);
				references.add(new VoltageReference(
						fields[0],
						fields[1].toLowerCase(),
						Integer.parseInt(fields[2]),
						Double.parseDouble(fields[3]),
						Double.parseDouble(fields[4])));
			}
		}
		return references;
	}

	private static Complex phaseVoltage(Complex3x1 voltage, int phase) {
		if (phase == 1) {
			return voltage.a_0;
		}
		if (phase == 2) {
			return voltage.b_1;
		}
		if (phase == 3) {
			return voltage.c_2;
		}
		throw new IllegalArgumentException("Unsupported phase: " + phase);
	}

	private static double wrappedAngleDeg(double angleDeg) {
		double wrapped = angleDeg;
		while (wrapped > 180.0) {
			wrapped -= 360.0;
		}
		while (wrapped <= -180.0) {
			wrapped += 360.0;
		}
		return wrapped;
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

	private static final class VoltageReference {
		private final String caseName;
		private final String bus;
		private final int phase;
		private final double vmagPu;
		private final double angleDeg;

		private VoltageReference(String caseName, String bus, int phase, double vmagPu, double angleDeg) {
			this.caseName = caseName;
			this.bus = bus;
			this.phase = phase;
			this.vmagPu = vmagPu;
			this.angleDeg = angleDeg;
		}

		private String label() {
			return caseName + ":" + bus + "." + phase;
		}
	}

	private static final class ComparisonResult {
		private final double maxMagError;
		private final String maxMagLabel;
		private final double maxAngleError;
		private final String maxAngleLabel;

		private ComparisonResult(double maxMagError, String maxMagLabel, double maxAngleError, String maxAngleLabel) {
			this.maxMagError = maxMagError;
			this.maxMagLabel = maxMagLabel;
			this.maxAngleError = maxAngleError;
			this.maxAngleLabel = maxAngleLabel;
		}

		private String summary(String caseName) {
			return String.format("%s comparison: max |V| error %.6f pu at %s, max angle error %.6f deg at %s",
					caseName, this.maxMagError, this.maxMagLabel, this.maxAngleError, this.maxAngleLabel);
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
}
