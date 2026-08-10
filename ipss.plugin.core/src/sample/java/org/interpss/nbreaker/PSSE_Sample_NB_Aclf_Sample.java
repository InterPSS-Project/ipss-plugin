package org.interpss.nbreaker;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.psse.PSSEDirectParser;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.funcImpl.AclfNetInfoHelper;
import com.interpss.core.aclf.AclfNetModelType;
import com.interpss.core.aclf.hvdc.HvdcLineMT;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.LoadflowAlgoObjectFactory;

/**
 * Sample: import PSS/E v36 sample_nb RAW (node-breaker overlay) and inspect substations.
 */
public class PSSE_Sample_NB_Aclf_Sample {

	/** Relative to {@code ipss.plugin.core} (launch.json cwd). */
	private static final String CASE = "testData/psse/v36/sample_nb.raw";

	public static void main(String[] args) throws InterpssException {
		AclfNetwork net = new PSSEDirectParser(36).parse(CASE);

		net.setZeroZBranchThreshold(1.0e-3);
		net.setAclfNetModelType(AclfNetModelType.ZBR_DECONSOLIDATED);		

		initCondition(net);

		net.getBusList().forEach(bus -> {
			if (bus.mismatch(AclfMethodType.NR).abs() > 0.1) {
				//System.out.println(bus.getId() + " " + bus.mismatch(AclfMethodType.NR) + " " + bus.getSubstation().getId());
				//AclfNetInfoHelper.outputBusAclfDebugInfo(net, bus.getId(), false);
			}
		});

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
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
