package org.interpss.nbreaker;

import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.numeric.exp.IpssNumericException;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetModelType;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.PQBusLimit;
import com.interpss.core.aclf.adj.PVBusLimit;
import com.interpss.core.aclf.adj.RemoteQBus;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.adj.TapControl;
import com.interpss.core.aclf.facts.StaticVarCompensator;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcLineMT;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.config.LimitControlConfig;
import com.interpss.core.algo.config.PowerAdjControlConfig;
import com.interpss.core.algo.config.VoltageAdjControlConfig;
import com.interpss.core.funcImpl.zeroz.AclfNetZeroZBranchHelper;

/**
 * Sample: import PSS/E v36 sample_nb RAW (node-breaker overlay) and inspect substations.
 */
public class PSSE_Sample_NB_Aclf_Sample {

	/** Relative to {@code ipss.plugin.core} (launch.json cwd). */
	private static final String CASE = "testData/psse/v36/sample_nb.raw";

	public static void main(String[] args) throws InterpssException, IpssNumericException {
		case0();
		case1();
		case2();
	}

	private static void case0() throws InterpssException, IpssNumericException {
		System.out.println("Case 0: init condition");

		AclfNetwork net = new PSSEDirectParser(36).parse(CASE);

		net.setZeroZBranchThreshold(1.0e-3);
		net.setAclfNetModelType(AclfNetModelType.ZBR_DECONSOLIDATED);

		initCondition(net);
	}

	private static void case1() throws InterpssException, IpssNumericException {
		System.out.println("Case 1: fully connected network");

		AclfNetwork net = new PSSEDirectParser(36).parse(CASE);

		net.setZeroZBranchThreshold(1.0e-3);
		net.setAclfNetModelType(AclfNetModelType.ZBR_DECONSOLIDATED);

		net.getBusList().forEach(bus -> {
			if (!bus.isActive()) {
				System.out.println(bus.getId() + " set active");
				bus.setStatus(true);
			}
		});

		net.getBranchList().forEach(branch -> {
			if (!branch.isActive()) {
				System.out.println(branch.getId() + " set active");
				branch.setStatus(true);
			}
		});

		// Merge ZBR-connected buses (e.g. Bus151↔Bus201 SF6) onto retained buses before LF
		new AclfNetZeroZBranchHelper(net).consolidate();

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.getLfAdjAlgo().getVoltAdjConfig().setDQ_dVThreshold(0.4);
		algo.getLfAdjAlgo().getVoltAdjConfig().setAdjTolerance(0.05);
		algo.setTolerance(0.01);
		algo.loadflow();
	}

	private static void case2() throws InterpssException, IpssNumericException {
		System.out.println("Case 2: maintenance network");

		AclfNetwork net = new PSSEDirectParser(36).parse(CASE);

		net.setZeroZBranchThreshold(1.0e-3);
		net.setAclfNetModelType(AclfNetModelType.ZBR_DECONSOLIDATED);

		// Merge ZBR-connected buses (e.g. Bus151↔Bus201 SF6) onto retained buses before LF
		new AclfNetZeroZBranchHelper(net).consolidate();

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.getLfAdjAlgo().getVoltAdjConfig().setDQ_dVThreshold(0.4);
		algo.getLfAdjAlgo().getVoltAdjConfig().setAdjTolerance(0.05);
		algo.setTolerance(0.01);
		algo.loadflow();
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

}
