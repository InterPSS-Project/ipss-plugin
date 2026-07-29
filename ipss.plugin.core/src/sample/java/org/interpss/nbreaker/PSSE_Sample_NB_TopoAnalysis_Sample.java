package org.interpss.nbreaker;

import org.interpss.fadapter.psse.PSSEDirectParser;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.AclfNetInfoHelper;
import com.interpss.core.funcImpl.topo.SubstationNBreakerHelper;

/**
 * Sample: import PSS/E v36 sample_nb RAW (node-breaker overlay) and inspect substations.
 */
public class PSSE_Sample_NB_TopoAnalysis_Sample {

	/** Relative to {@code ipss.plugin.core} (launch.json cwd). */
	private static final String CASE = "testData/psse/v36/sample_nb.raw";

	public static void main(String[] args) throws InterpssException {
		AclfNetwork net = new PSSEDirectParser(36).parse(CASE);
		
		net.getSubstationMap().forEach((subName, sub) -> {	
			SubstationNBreakerHelper subHelper = new SubstationNBreakerHelper(sub);
			subHelper.topoAnalysis();
		});

		net.getSubstationMap().forEach((subName, sub) -> {	
			//AclfNetInfoHelper.outputSubstationAclfInfo(net, subName, false);

			// at this stage, all active buses in the substation should have intFlag set.
			sub.getBusList().forEach( bus -> {
				if (bus.isActive() && bus.getIntFlag() == 0) {
					System.out.println("Bus " + bus.getId() + " has no intFlag set.");
				}
			});
		});

		// 3W star bus inherits intFlag from the from-bus (Bus3008) topo group.
		AclfBus starBus = net.getBus("3WNDTR_3008_3012_3010_2");
		AclfBus bus3008 = net.getBus("Bus3008");
		System.out.println("Star bus " + starBus.getId() + " intFlag=" + starBus.getIntFlag()
				+ " (Bus3008 intFlag=" + bus3008.getIntFlag() + ")");
	}
}
