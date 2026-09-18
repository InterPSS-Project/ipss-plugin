package org.interpss.core.aclf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcControlMode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.impl.solver.CoordinatedControlZbrNrSolver;
import com.interpss.core.algo.impl.solver.LccCoupledInjectionModel;

class Kundur_2Area_LccReducedJacobianParityTest extends CorePluginTestSetup {
	private static final String DATA_ROOT = "testData/adpter/psse/v33/";
	private static final double SOLVE_TOLERANCE_PU = 1.0e-4;

	@Test
	void defaultPowerOrderMatchesSequentialSolution() throws Exception {
		assertReducedParity(DATA_ROOT + "Kundur_2area_LCC_HVDC.raw", false);
	}

	@Test
	void inverterPowerOrderMatchesSequentialSolution() throws Exception {
		assertReducedParity(DATA_ROOT + "Kundur_2area_LCC_HVDC_PsetOnInv.raw", false);
	}

	@Test
	void currentOrderMatchesSequentialSolution() throws Exception {
		assertReducedParity(DATA_ROOT + "Kundur_2area_LCC_HVDC_current_control.raw", true);
	}

	@Test
	void firingAngleLimitCaseMatchesSequentialSolution() throws Exception {
		assertReducedParity(DATA_ROOT + "Kundur_2area_LCC_HVDC_fireangle_limit.raw", false);
	}

	@Test
	void postLimitRectifierAngleInverterCurrentModeMatchesSettledSequentialSolution()
			throws Exception {
		assertForcedModeParity(HvdcControlMode.FIRING_ANGLE,
				HvdcControlMode.DC_CURRENT);
	}

	@Test
	void postLimitRectifierCurrentInverterAngleModeMatchesSettledSequentialSolution()
			throws Exception {
		assertForcedModeParity(HvdcControlMode.DC_CURRENT,
				HvdcControlMode.FIRING_ANGLE);
	}

	@Test
	void normalModeMatchesSequentialSolutionFromFlatAndPerturbedStarts()
			throws Exception {
		String rawPath = DATA_ROOT + "Kundur_2area_LCC_HVDC.raw";
		SolvedCase sequential = solve(rawPath, false, false, StartMode.SAVED);
		assertStartModeParity(sequential,
				solve(rawPath, false, true, StartMode.FLAT), StartMode.FLAT);
		assertStartModeParity(sequential,
				solve(rawPath, false, true, StartMode.PERTURBED),
				StartMode.PERTURBED);
	}

	@Test
	void parsedKundurDerivativesMatchAtSolvedAndStressedVoltages()
			throws Exception {
		String[] rawPaths = {
				DATA_ROOT + "Kundur_2area_LCC_HVDC.raw",
				DATA_ROOT + "Kundur_2area_LCC_HVDC_PsetOnInv.raw",
				DATA_ROOT + "Kundur_2area_LCC_HVDC_current_control.raw",
				DATA_ROOT + "Kundur_2area_LCC_HVDC_fireangle_limit.raw"};
		for (String rawPath : rawPaths) {
			SolvedCase solved = solve(rawPath,
					rawPath.contains("current_control"), true);
			LccCoupledInjectionModel model =
					solved.solver.getLccCoupledModels().get(0);
			double rectifierVoltage = solved.line.getRectifier().getBus()
					.getVoltageMag();
			double inverterVoltage = solved.line.getInverter().getBus()
					.getVoltageMag();
			String liveState = lccState(solved.line);

			assertKundurDerivativeAudit(model, rectifierVoltage,
					inverterVoltage, rawPath + " solved");
			double stressedRectifierVoltage = 0.97 * rectifierVoltage;
			double stressedInverterVoltage = 0.95 * inverterVoltage;
			boolean stressedPointAdmitted = model.evaluate(
					stressedRectifierVoltage, stressedInverterVoltage);
			if (!stressedPointAdmitted) {
				assertEquals(LccCoupledInjectionModel.Status.ACTIVE_SET_BOUNDARY,
						model.getStatus(), rawPath + ": " + model.getDiagnostic());
				assertTrue(model.getDiagnostic().contains("transaction"),
						rawPath + ": " + model.getDiagnostic());
				stressedRectifierVoltage = 1.03 * rectifierVoltage;
				stressedInverterVoltage = 1.05 * inverterVoltage;
				stressedPointAdmitted = model.evaluate(stressedRectifierVoltage,
						stressedInverterVoltage);
			}
			if (stressedPointAdmitted) {
				assertKundurDerivativeAudit(model, stressedRectifierVoltage,
						stressedInverterVoltage, rawPath + " stressed");
			}
			else {
				assertTrue(rawPath.contains("fireangle_limit"),
						rawPath + ": unexpected stressed fallback: "
								+ model.getDiagnostic());
				assertEquals(LccCoupledInjectionModel.Status.ACTIVE_SET_BOUNDARY,
						model.getStatus(), rawPath + ": " + model.getDiagnostic());
				assertTrue(model.getDiagnostic().contains("transaction"),
						rawPath + ": " + model.getDiagnostic());
			}
			assertTrue(model.evaluate(rectifierVoltage, inverterVoltage),
					model.getDiagnostic());
			assertEquals(liveState, lccState(solved.line),
					rawPath + " derivative probes mutated live LCC state");
		}
	}

	@Test
	void repeatedReducedRunsAreDeterministic() throws Exception {
		String rawPath = DATA_ROOT + "Kundur_2area_LCC_HVDC.raw";
		SolvedCase reference = null;
		for (int run = 0; run < 3; run++) {
			SolvedCase actual = solve(rawPath, false, true);
			if (reference == null) {
				reference = actual;
				continue;
			}
			for (AclfBus expectedBus : reference.network.getBusList()) {
				AclfBus actualBus = actual.network.getBus(expectedBus.getId());
				assertEquals(expectedBus.getVoltageMag(), actualBus.getVoltageMag(),
						1.0e-12, "run " + run + " voltage at " + expectedBus.getId());
				assertEquals(expectedBus.getVoltageAng(), actualBus.getVoltageAng(),
						1.0e-12, "run " + run + " angle at " + expectedBus.getId());
			}
			assertEquals(lccState(reference.line), lccState(actual.line),
					"run " + run + " converter state");
			assertEquals(reference.solver.getJMatrixStructureSignature(),
					actual.solver.getJMatrixStructureSignature(),
					"run " + run + " matrix structure");
			assertEquals(reference.solver.getLccCoupledModels().get(0)
					.getActiveSetSignature(), actual.solver.getLccCoupledModels().get(0)
					.getActiveSetSignature(), "run " + run + " active set");
		}
	}

	private static void assertStartModeParity(SolvedCase sequential,
			SolvedCase reduced, StartMode startMode) {
		for (AclfBus expectedBus : sequential.network.getBusList()) {
			AclfBus actualBus = reduced.network.getBus(expectedBus.getId());
			assertEquals(expectedBus.getVoltageMag(), actualBus.getVoltageMag(),
					2.0e-5, startMode + " voltage at " + expectedBus.getId());
			assertEquals(expectedBus.getVoltageAng(), actualBus.getVoltageAng(),
					2.0e-5, startMode + " angle at " + expectedBus.getId());
		}
		assertEquals(sequential.line.getRectifier().powerIntoConverter().getReal(),
				reduced.line.getRectifier().powerIntoConverter().getReal(),
				SOLVE_TOLERANCE_PU, startMode.toString());
		assertEquals(sequential.line.getRectifier().powerIntoConverter().getImaginary(),
				reduced.line.getRectifier().powerIntoConverter().getImaginary(),
				SOLVE_TOLERANCE_PU, startMode.toString());
		assertEquals(sequential.line.getInverter().powerIntoConverter().getReal(),
				reduced.line.getInverter().powerIntoConverter().getReal(),
				SOLVE_TOLERANCE_PU, startMode.toString());
		assertEquals(sequential.line.getInverter().powerIntoConverter().getImaginary(),
				reduced.line.getInverter().powerIntoConverter().getImaginary(),
				SOLVE_TOLERANCE_PU, startMode.toString());
		LccCoupledInjectionModel model = reduced.solver.getLccCoupledModels().get(0);
		assertEquals(LccCoupledInjectionModel.Status.ELIGIBLE, model.getStatus(),
				startMode + ": " + model.getDiagnostic());
		assertTrue(model.getMaxScaledResidual() < 1.0e-10,
				startMode + " LCC residual=" + model.getMaxScaledResidual());
	}

	private static void assertForcedModeParity(HvdcControlMode rectifierMode,
			HvdcControlMode inverterMode) throws Exception {
		String rawPath = DATA_ROOT + "Kundur_2area_LCC_HVDC.raw";
		SolvedCase sequential = solveForcedTransfer(rawPath, false,
				rectifierMode, inverterMode);
		SolvedCase reduced = solveForcedTransfer(rawPath, true,
				rectifierMode, inverterMode);

		assertEquals(rectifierMode,
				reduced.line.getRectifierControlMode());
		assertEquals(inverterMode,
				reduced.line.getInverterControlMode());
		double maxVoltageDifference = 0.0;
		double maxAngleDifference = 0.0;
		for (AclfBus expectedBus : sequential.network.getBusList()) {
			AclfBus actualBus = reduced.network.getBus(expectedBus.getId());
			maxVoltageDifference = Math.max(maxVoltageDifference,
					Math.abs(expectedBus.getVoltageMag() - actualBus.getVoltageMag()));
			maxAngleDifference = Math.max(maxAngleDifference,
					Math.abs(expectedBus.getVoltageAng() - actualBus.getVoltageAng()));
		}
		assertTrue(maxVoltageDifference < 0.03,
				"forced transfer max voltage difference=" + maxVoltageDifference);
		assertTrue(maxAngleDifference < Math.toRadians(1.0),
				"forced transfer max angle difference rad=" + maxAngleDifference);
		assertEquals(sequential.line.getRectifier().getIdc(),
				reduced.line.getRectifier().getIdc(), 1.0e-6);
		assertEquals(sequential.line.getInverter().getIdc(),
				reduced.line.getInverter().getIdc(), 1.0e-6);
		assertEquals(sequential.line.getRectifier().getDcVoltage(),
				reduced.line.getRectifier().getDcVoltage(), 1.0,
				() -> forcedStateComparison(sequential, reduced));
		assertEquals(sequential.line.getInverter().getDcVoltage(),
				reduced.line.getInverter().getDcVoltage(), 1.0,
				() -> forcedStateComparison(sequential, reduced));
		assertEquals(reduced.admittedRectifierTap,
				reduced.line.getRectifier().getXformerTapSetting(), 1.0e-12);
		assertEquals(reduced.admittedInverterTap,
				reduced.line.getInverter().getXformerTapSetting(), 1.0e-12);
		LccCoupledInjectionModel model = reduced.solver.getLccCoupledModels().get(0);
		assertEquals(LccCoupledInjectionModel.Status.ELIGIBLE, model.getStatus(),
				model.getDiagnostic());
		assertTrue(model.getMaxScaledResidual() < 1.0e-10,
				"LCC residual=" + model.getMaxScaledResidual());
		assertEquals(reduced.line.getRectifier().powerIntoConverter().getReal(),
				model.getRectifierPower().getReal(), 1.0e-9);
		assertEquals(reduced.line.getRectifier().powerIntoConverter().getImaginary(),
				model.getRectifierPower().getImaginary(), 1.0e-9);
		assertEquals(reduced.line.getInverter().powerIntoConverter().getReal(),
				model.getInverterPower().getReal(), 1.0e-9);
		assertEquals(reduced.line.getInverter().powerIntoConverter().getImaginary(),
				model.getInverterPower().getImaginary(), 1.0e-9);
	}

	private static String forcedStateComparison(SolvedCase sequential,
			SolvedCase reduced) {
		return "sequential=" + lccState(sequential.line)
				+ ", reduced=" + lccState(reduced.line);
	}

	private static String lccState(HvdcLine2TLCC<AclfBus> line) {
		return "[mode=" + line.getRectifierControlMode() + "/"
				+ line.getInverterControlMode() + ", Vr="
				+ line.getRectifier().getBus().getVoltageMag() + ", Vi="
				+ line.getInverter().getBus().getVoltageMag() + ", Id="
				+ line.getRectifier().getIdc() + ", Vdr="
				+ line.getRectifier().getDcVoltage() + ", Vdi="
				+ line.getInverter().getDcVoltage() + ", alpha="
				+ line.getRectifier().getFiringAng() + ", gamma="
				+ line.getInverter().getFiringAng() + ", tap="
				+ line.getRectifier().getXformerTapSetting() + "/"
				+ line.getInverter().getXformerTapSetting() + ", S="
				+ line.getRectifier().powerIntoConverter() + "/"
				+ line.getInverter().powerIntoConverter() + "]";
	}

	private static void assertKundurDerivativeAudit(
			LccCoupledInjectionModel model, double rectifierVoltage,
			double inverterVoltage, String operatingPoint) {
		assertTrue(model.evaluate(rectifierVoltage, inverterVoltage),
				operatingPoint + ": " + model.getDiagnostic());
		assertTrue(Double.isFinite(model.getScaledLocalCondition())
				&& model.getScaledLocalCondition() < 1.0e12,
				operatingPoint + " condition=" + model.getScaledLocalCondition());
		double[][] analytical = terminalDerivatives(model);
		double[][] numerical = centeredFiniteDifferences(model,
				rectifierVoltage, inverterVoltage, 3.0e-5);
		String[] equations = {"rectifier-P", "rectifier-Q", "inverter-P",
				"inverter-Q"};
		String[] variables = {"ln(Vr)", "ln(Vi)"};
		for (int row = 0; row < analytical.length; row++) {
			for (int column = 0; column < analytical[row].length; column++) {
				double tolerance = Math.max(5.0e-7,
						5.0e-5 * Math.abs(numerical[row][column]));
				double error = analytical[row][column]
						- numerical[row][column];
				assertEquals(numerical[row][column], analytical[row][column],
						tolerance, "link=" + model.getLine().getId() + " point="
								+ operatingPoint + " mode="
								+ model.getLine().getRectifierControlMode() + "/"
								+ model.getLine().getInverterControlMode()
								+ " equation=" + equations[row] + " variable="
								+ variables[column] + " expected="
								+ numerical[row][column] + " actual="
								+ analytical[row][column] + " error=" + error
								+ " condition="
								+ model.getScaledLocalCondition());
			}
		}
	}

	private static double[][] terminalDerivatives(
			LccCoupledInjectionModel model) {
		double[][] result = new double[4][2];
		for (int row = 0; row < result.length; row++) {
			for (int column = 0; column < result[row].length; column++) {
				result[row][column] = model.getTerminalDerivative(row, column);
			}
		}
		return result;
	}

	private static double[][] centeredFiniteDifferences(
			LccCoupledInjectionModel model, double rectifierVoltage,
			double inverterVoltage, double step) {
		double[][] result = new double[4][2];
		for (int column = 0; column < 2; column++) {
			double plusRectifier = rectifierVoltage
					* (column == 0 ? Math.exp(step) : 1.0);
			double plusInverter = inverterVoltage
					* (column == 1 ? Math.exp(step) : 1.0);
			assertTrue(model.evaluate(plusRectifier, plusInverter),
					model.getDiagnostic());
			double[] plus = terminalPowers(model);

			double minusRectifier = rectifierVoltage
					* (column == 0 ? Math.exp(-step) : 1.0);
			double minusInverter = inverterVoltage
					* (column == 1 ? Math.exp(-step) : 1.0);
			assertTrue(model.evaluate(minusRectifier, minusInverter),
					model.getDiagnostic());
			double[] minus = terminalPowers(model);
			for (int row = 0; row < result.length; row++) {
				result[row][column] = (plus[row] - minus[row])
						/ (2.0 * step);
			}
		}
		return result;
	}

	private static double[] terminalPowers(LccCoupledInjectionModel model) {
		return new double[] {
				model.getRectifierPower().getReal(),
				model.getRectifierPower().getImaginary(),
				model.getInverterPower().getReal(),
				model.getInverterPower().getImaginary()};
	}

	private static void assertReducedParity(String rawPath, boolean applyAdjustments)
			throws Exception {
		SolvedCase sequential = solve(rawPath, applyAdjustments, false);
		SolvedCase reduced = solve(rawPath, applyAdjustments, true);

		for (AclfBus expectedBus : sequential.network.getBusList()) {
			AclfBus actualBus = reduced.network.getBus(expectedBus.getId());
			assertEquals(expectedBus.getVoltageMag(), actualBus.getVoltageMag(), 2.0e-5,
					rawPath + " voltage at " + expectedBus.getId());
			assertEquals(expectedBus.getVoltageAng(), actualBus.getVoltageAng(), 2.0e-5,
					rawPath + " angle at " + expectedBus.getId());
		}

		assertEquals(sequential.line.getRectifierControlMode(),
				reduced.line.getRectifierControlMode(), rawPath);
		assertEquals(sequential.line.getInverterControlMode(),
				reduced.line.getInverterControlMode(), rawPath);
		assertEquals(sequential.line.getRectifier().getXformerTapSetting(),
				reduced.line.getRectifier().getXformerTapSetting(), 1.0e-8, rawPath);
		assertEquals(sequential.line.getInverter().getXformerTapSetting(),
				reduced.line.getInverter().getXformerTapSetting(), 1.0e-8, rawPath);
		assertEquals(sequential.line.getRectifier().powerIntoConverter().getReal(),
				reduced.line.getRectifier().powerIntoConverter().getReal(),
				SOLVE_TOLERANCE_PU, rawPath);
		assertEquals(sequential.line.getRectifier().powerIntoConverter().getImaginary(),
				reduced.line.getRectifier().powerIntoConverter().getImaginary(),
				SOLVE_TOLERANCE_PU,
				rawPath);
		assertEquals(sequential.line.getInverter().powerIntoConverter().getReal(),
				reduced.line.getInverter().powerIntoConverter().getReal(),
				SOLVE_TOLERANCE_PU, rawPath);
		assertEquals(sequential.line.getInverter().powerIntoConverter().getImaginary(),
				reduced.line.getInverter().powerIntoConverter().getImaginary(),
				SOLVE_TOLERANCE_PU,
				rawPath);

		LccCoupledInjectionModel model = reduced.solver.getLccCoupledModels().get(0);
		assertEquals(LccCoupledInjectionModel.Status.ELIGIBLE, model.getStatus(),
				rawPath + ": " + model.getDiagnostic());
	}

	private static SolvedCase solve(String rawPath, boolean applyAdjustments,
			boolean reduced) throws Exception {
		return solve(rawPath, applyAdjustments, reduced, StartMode.SAVED);
	}

	private static SolvedCase solve(String rawPath, boolean applyAdjustments,
			boolean reduced, StartMode startMode) throws Exception {
		AclfNetwork network = new PSSEDirectParser(33).parse(rawPath);
		@SuppressWarnings("unchecked")
		HvdcLine2TLCC<AclfBus> line =
				(HvdcLine2TLCC<AclfBus>) network.getSpecialBranchList().get(0);
		LoadflowAlgorithm algorithm =
				LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network);
		algorithm.setTolerance(SOLVE_TOLERANCE_PU);
		algorithm.getLfAdjAlgo().setApplyAdjustAlgo(applyAdjustments);
		algorithm.getNetAdjAlgo().setAreaInterchangeControlEnabled(false);
		if (startMode == StartMode.FLAT) {
			algorithm.setInitBusVoltage(true);
		}
		else if (startMode == StartMode.PERTURBED) {
			perturbSavedStart(network);
			algorithm.setInitBusVoltage(false);
		}
		algorithm.setMaxIterations(30);
		CoordinatedControlZbrNrSolver solver = null;
		if (reduced) {
			solver = new CoordinatedControlZbrNrSolver(network,
					algorithm.getNrMethodConfig(), algorithm.getTolerance())
						.setReducedLccCouplingEnabled(true);
			algorithm.getLfCalculator().setNrSolver(solver);
			if (startMode != StartMode.SAVED) {
				algorithm.getLfCalculator().setHvdcOuterControlEnabled(false);
			}
		}
		boolean converged = algorithm.loadflow();
		String reducedDiagnostic = solver == null ? ""
				: solver.getLccCoupledModels().stream()
						.map(model -> model.getLine().getId() + "=" + model.getStatus()
								+ "(" + model.getDiagnostic() + ")")
						.reduce((left, right) -> left + ", " + right)
						.orElse("no reduced LCC models");
		assertTrue(converged, rawPath + " reduced=" + reduced
				+ " start=" + startMode + " " + reducedDiagnostic);
		assertTrue(network.isLfConverged(), rawPath + " reduced=" + reduced);
		return new SolvedCase(network, line, solver);
	}

	private static void perturbSavedStart(AclfNetwork network) {
		for (AclfBus bus : network.getBusList()) {
			if (!bus.isActive() || bus.isSwing()) {
				continue;
			}
			double signedOffset = (bus.getSortNumber() % 2 == 0 ? 1.0 : -1.0);
			bus.setVoltageAng(bus.getVoltageAng()
					+ Math.toRadians(1.5 * signedOffset));
			if (!bus.isPV()) {
				bus.setVoltageMag(Math.max(0.90, Math.min(1.10,
						bus.getVoltageMag() + 0.025 * signedOffset)));
			}
		}
	}

	private static SolvedCase solveForcedTransfer(String rawPath, boolean reduced,
			HvdcControlMode rectifierMode, HvdcControlMode inverterMode)
			throws Exception {
		AclfNetwork network = new PSSEDirectParser(33).parse(rawPath);
		@SuppressWarnings("unchecked")
		HvdcLine2TLCC<AclfBus> line =
				(HvdcLine2TLCC<AclfBus>) network.getSpecialBranchList().get(0);
		line.initLoadflow();
		assertTrue(line.calculateLoadflow(null),
				"establish normal LCC state before freezing " + rectifierMode
						+ "/" + inverterMode);
		line.setRectifierControlMode(rectifierMode);
		line.setInverterControlMode(inverterMode);
		if (rectifierMode == HvdcControlMode.DC_CURRENT
				&& inverterMode == HvdcControlMode.FIRING_ANGLE) {
			line.getInverter().setXformerTapSetting(1.10);
		}
		line.calculateLoadflow(null);
		double admittedRectifierTap = line.getRectifier().getXformerTapSetting();
		double admittedInverterTap = line.getInverter().getXformerTapSetting();

		LoadflowAlgorithm algorithm =
				LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network);
		algorithm.setTolerance(SOLVE_TOLERANCE_PU);
		algorithm.getLfAdjAlgo().setApplyAdjustAlgo(false);
		algorithm.getNetAdjAlgo().setAreaInterchangeControlEnabled(false);
		algorithm.setMaxIterations(30);
		algorithm.getLfCalculator().setHvdcOuterControlEnabled(false);
		CoordinatedControlZbrNrSolver solver = null;
		if (reduced) {
			solver = new CoordinatedControlZbrNrSolver(network,
					algorithm.getNrMethodConfig(), algorithm.getTolerance())
						.setReducedLccCouplingEnabled(true);
			algorithm.getLfCalculator().setNrSolver(solver);
			assertTrue(algorithm.loadflow(), "forced transfer reduced solve");
		}
		else {
			Complex previousRectifierPower = Complex.NaN;
			Complex previousInverterPower = Complex.NaN;
			for (int outer = 0; outer < 80; outer++) {
				line.calculateLoadflow(null);
				Complex refreshedRectifierPower =
						line.getRectifier().powerIntoConverter();
				Complex refreshedInverterPower =
						line.getInverter().powerIntoConverter();
				assertTrue(algorithm.loadflow(),
						"forced transfer sequential outer iteration " + outer);
				if (!previousRectifierPower.isNaN()
						&& !previousInverterPower.isNaN()
						&& refreshedRectifierPower.subtract(previousRectifierPower)
								.abs() < 1.0e-9
						&& refreshedInverterPower.subtract(previousInverterPower)
								.abs() < 1.0e-9) {
					break;
				}
				previousRectifierPower = refreshedRectifierPower;
				previousInverterPower = refreshedInverterPower;
			}
			line.calculateLoadflow(null);
		}
		assertTrue(network.isLfConverged());
		return new SolvedCase(network, line, solver, admittedRectifierTap,
				admittedInverterTap);
	}

	private enum StartMode {
		SAVED,
		FLAT,
		PERTURBED
	}

	private static final class SolvedCase {
		private final AclfNetwork network;
		private final HvdcLine2TLCC<AclfBus> line;
		private final CoordinatedControlZbrNrSolver solver;
		private final double admittedRectifierTap;
		private final double admittedInverterTap;

		private SolvedCase(AclfNetwork network, HvdcLine2TLCC<AclfBus> line,
				CoordinatedControlZbrNrSolver solver) {
			this(network, line, solver, line.getRectifier().getXformerTapSetting(),
					line.getInverter().getXformerTapSetting());
		}

		private SolvedCase(AclfNetwork network, HvdcLine2TLCC<AclfBus> line,
				CoordinatedControlZbrNrSolver solver, double admittedRectifierTap,
				double admittedInverterTap) {
			this.network = network;
			this.line = line;
			this.solver = solver;
			this.admittedRectifierTap = admittedRectifierTap;
			this.admittedInverterTap = admittedInverterTap;
		}
	}
}
