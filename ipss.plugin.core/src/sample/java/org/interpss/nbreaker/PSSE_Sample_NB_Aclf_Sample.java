package org.interpss.nbreaker;

import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.numeric.exp.IpssNumericException;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetModelType;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcLineMT;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.zeroz.AclfNetZeroZBranchHelper;
import com.interpss.core.funcImpl.topo.AclfNetTopoHelper;

/**
 * Sample: import PSS/E v36 sample_nb RAW (node-breaker overlay) and inspect substations.
 */
public class PSSE_Sample_NB_Aclf_Sample {

	/** Relative to {@code ipss.plugin.core} (launch.json cwd). */
	private static final String CASE = "testData/private/sample_nb.raw";

	public static void main(String[] args) throws InterpssException, IpssNumericException {
		case0();
		case1();
		case2();
		case3();
	}

	// check the initial condition
	private static void case0() throws InterpssException, IpssNumericException {
		System.out.println("Case 0: init condition");

		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		net.setZeroZBranchThreshold(1.0e-3);
		net.setAclfNetModelType(AclfNetModelType.ZBR_MODEL);

		initCondition(net);
	}

	// activated buses and branches relavent to the open switches
	private static void case1() throws InterpssException, IpssNumericException {
		System.out.println("Case 1: activated network");

		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		net.setZeroZBranchThreshold(1.0e-3);
		net.setAclfNetModelType(AclfNetModelType.ZBR_MODEL);

		PSSE_Sample_NB_TopoAnalysis_Sample.activateBusBranch(net);

		// Merge ZBR-connected buses (e.g. Bus151↔Bus201 SF6) onto retained buses before LF
		new AclfNetZeroZBranchHelper(net).consolidate();

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		configureSampleSolver(algo);
		//algo.setTolerance(0.001);
		algo.loadflow();

		printInfo(net);
	}

	// maintenance network due the open switches
	private static void case2() throws InterpssException, IpssNumericException {
		System.out.println("Case 2: maintenance network");

		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		net.setZeroZBranchThreshold(1.0e-3);
		net.setAclfNetModelType(AclfNetModelType.ZBR_MODEL);

		// Merge ZBR-connected buses (e.g. Bus151↔Bus201 SF6) onto retained buses before LF
		new AclfNetZeroZBranchHelper(net).consolidate();

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		configureSampleSolver(algo);
		//algo.setTolerance(0.001);
		algo.loadflow();

		printInfo(net);
	}

	// activate the network first and then do the topo processing to create the maintenance network
	private static void case3() throws InterpssException, IpssNumericException {
		System.out.println("Case 3: activated the topo processing network");

		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		net.setZeroZBranchThreshold(1.0e-3);
		net.setAclfNetModelType(AclfNetModelType.ZBR_MODEL);

		PSSE_Sample_NB_TopoAnalysis_Sample.activateBusBranch(net);

		new AclfNetTopoHelper(net).topoProcessing();

		// Merge ZBR-connected buses (e.g. Bus151↔Bus201 SF6) onto retained buses before LF
		new AclfNetZeroZBranchHelper(net).consolidate();

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		configureSampleSolver(algo);
		//algo.setTolerance(0.001);
		algo.loadflow();

		printInfo(net);
	}

	private static void configureSampleSolver(LoadflowAlgorithm algo) {
		algo.getLfAdjAlgo().getVoltAdjConfig().setDQ_dVThreshold(0.4);
		algo.getLfAdjAlgo().getVoltAdjConfig().setAdjTolerance(0.05);
		// sample_nb.raw SOLVER options: ACTAPS=0, AREAIN=0, PHSHFT=0,
		// DCTAPS=1, SWSHNT=1.
		algo.getLfAdjAlgo().getVoltAdjConfig().setXfrTapControl(false);
		algo.getLfAdjAlgo().getVoltAdjConfig().setHvdcTapControl(true);
		algo.getLfAdjAlgo().getPowerAdjConfig().setPsXfrPControl(false);
		algo.getNetAdjAlgo().setAreaInterchangeControlEnabled(false);
	}

	private static void initCondition(AclfNetwork aclfNet) {
		System.out.println("MTDC lines: " + aclfNet.getHvdcLineMTList().size());
		for (HvdcLineMT mt : aclfNet.getHvdcLineMTList()) {
			mt.initLoadflow();
		}

		aclfNet.getSpecialBranchList().forEach(branch -> {
			if (branch instanceof HvdcLine2TLCC) {
				((HvdcLine2TLCC<?>) branch).initLoadflow();
			}
		});

		aclfNet.calExternalPowerIntoNet();

		System.out.println("Buses with |mismatch| > 0.1:");
		for (AclfBus bus : aclfNet.getBusList()) {
			if (bus.mismatch(AclfMethodType.NR).abs() > 1e-1)
				System.out.println(bus.getId() + ", " + bus.mismatch(AclfMethodType.NR));
		}
	}

	private static void printInfo(AclfNetwork net) {
		// print number of active Buses and Branches
		System.out.println("Number of active Buses: " + net.getBusList().stream().filter(bus -> bus.isActive()).count());
		System.out.println("Number of active Branches: " + net.getBranchList().stream().filter(branch -> branch.isActive()).count());

		// print swing bus P and Q
		for (AclfBus bus : net.getBusList()) {
			if (bus.isSwing()) {
				System.out.println("Swing bus " + bus.getId() + " P,Q =" + bus.powerIntoNet());
			}
		}
	}

}
