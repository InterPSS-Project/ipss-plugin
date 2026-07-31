package org.interpss.nbreaker;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.psse.PSSEDirectParser;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfNetInfoHelper;
import com.interpss.core.funcImpl.topo.SubstationNBreakerHelper;
import com.interpss.core.net.Substation;

/**
 * Sample: import PSS/E v36 sample_nb RAW (node-breaker overlay) and inspect substations.
 */
public class PSSE_Sample_NB_Aclf_Sample {

	/** Relative to {@code ipss.plugin.core} (launch.json cwd). */
	private static final String CASE = "testData/psse/v36/sample_nb.raw";

	public static void main(String[] args) throws InterpssException {
		AclfNetwork net = new PSSEDirectParser(36).parse(CASE);

		net.getBus("Bus301").setStatus(false);
		net.getBus("Bus401").setStatus(false);
		net.getBus("Bus402").setStatus(false);

		// 3021 / 3022  2T Hvdc   
		net.getBus("Bus3021").setExternalPowerIntoNet(new Complex(-14.298913320926543, 5.616601769590946));
		net.getBus("Bus3022").setExternalPowerIntoNet(new Complex(-14.359068332519264, 6.156034189594078));

		// Bus 212       Bus 213  Multi-terminal Hvdc
		net.getBus("Bus212").setExternalPowerIntoNet(new Complex(-2.963798116185913, 1.4096459351954498));
		net.getBus("Bus213").setExternalPowerIntoNet(new Complex(-3.037951816979418, 1.5965591185481436));

		net.getBusList().forEach(bus -> {
			if (bus.mismatch(AclfMethodType.NR).abs() > 0.1) 
				System.out.println(bus.getId() + " " + bus.mismatch(AclfMethodType.NR) + " " + bus.getSubstation().getId());
		});

		//LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		//algo.loadflow();
	}
}
