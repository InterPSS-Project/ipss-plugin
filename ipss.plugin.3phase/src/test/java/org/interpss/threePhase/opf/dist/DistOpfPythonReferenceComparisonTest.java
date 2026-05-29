package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Paths;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.opf.dist.model.DistOpfCsvModelDataImporter;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.LinDistFlowModelBuilder;
import org.interpss.threePhase.opf.dist.solver.DistOpfSolverResult;
import org.interpss.threePhase.opf.dist.solver.OjAlgoDistOpfSolver;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;

public class DistOpfPythonReferenceComparisonTest {

	@Test
	public void matchesPythonDistopfTwoBusReference() throws InterpssException {
		DistOpfResult result = ThreePhaseObjectFactory.createDistOpfAlgorithm(createTwoBusFeeder()).solve();

		// Reference generated with GRIDAPPSD/distopf 0.4.0 matrix wrapper on the same two-bus CSV case:
		// load voltage magnitude = 0.998198 pu, branch P = 0.1 pu, branch Q = 0.02 pu.
		assertEquals(0.9964, result.getBusVoltageSquared("load", "A"), 5.0e-6);
		assertEquals(0.998198, Math.sqrt(result.getBusVoltageSquared("load", "A")), 5.0e-6);
		assertEquals(0.1, result.getBranchActivePower().values().iterator().next(), 1.0e-7);
		assertEquals(0.02, result.getBranchReactivePower().values().iterator().next(), 1.0e-7);
	}

	@Test
	public void matchesPythonDistopfIeee13CsvReference() {
		DistOpfModelData data = new DistOpfCsvModelDataImporter().importModel(
				Paths.get("src/test/resources/distopf/ieee13"), false);
		DistOpfOptions options = new DistOpfOptions().setMinVoltagePu(0.0).setMaxVoltagePu(2.0);
		DistOpfModel model = new LinDistFlowModelBuilder().build(data, options);

		DistOpfSolverResult result = new OjAlgoDistOpfSolver().solve(model, options);

		// Reference generated with Python distopf 0.2.0:
		// DistOPFCase(data_path=CASES_DIR / "csv" / "ieee13").run_pf()
		assertEquals(DistOpfStatus.OPTIMAL, result.getStatus());
		assertEquals(16, data.getBuses().size());
		assertEquals(15, data.getBranches().size());
		// Python distopf 0.2.0 uses a voltage-magnitude LinDistFlow variant for this CSV case,
		// while Java InterPSS uses squared-voltage LinDistFlow. Compare topology and source
		// P/Q directly, and keep a near-source voltage check where the formulations remain close.
		assertEquals(1.037604, Math.sqrt(result.getPrimalVariables()[
				model.getVariableIndex().busV2("3", PhaseCode.B)]), 7.0e-3);
		assertEquals(1.212062, result.getPrimalVariables()[
				model.getVariableIndex().branchP("sub", PhaseCode.A)], 2.0e-5);
		assertEquals(0.527291, result.getPrimalVariables()[
				model.getVariableIndex().branchQ("sub", PhaseCode.A)], 7.0e-3);
	}

	private static DStabNetwork3Phase createTwoBusFeeder() throws InterpssException {
		DStabNetwork3Phase net = ThreePhaseObjectFactory.create3PhaseDStabNetwork();
		net.setBaseKva(1000.0);

		DStab3PBus source = ThreePhaseObjectFactory.create3PDStabBus("source", net);
		source.setBaseVoltage(12470.0);
		source.setGenCode(AclfGenCode.SWING);
		source.setLoadCode(AclfLoadCode.NON_LOAD);
		source.setVoltage(new Complex(1.0, 0.0));

		DStab3PBus loadBus = ThreePhaseObjectFactory.create3PDStabBus("load", net);
		loadBus.setBaseVoltage(12470.0);
		loadBus.setGenCode(AclfGenCode.NON_GEN);
		loadBus.setLoadCode(AclfLoadCode.CONST_P);
		DStab3PLoad load = ThreePhaseObjectFactory.create3PLoad("load-1");
		load.set3PhaseLoad(new Complex3x1(new Complex(0.1, 0.02),
				new Complex(0.1, 0.02), new Complex(0.1, 0.02)));
		loadBus.getThreePhaseLoadList().add(load);

		DStab3PBranch line = ThreePhaseObjectFactory.create3PBranch("source", "load", "0", net);
		line.setBranchCode(AclfBranchCode.LINE);
		line.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex(0.01, 0.04)));
		return net;
	}
}
