package org.interpss.nbreaker;

import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.numeric.datatype.LimitType;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.topo.SubstationNBreakerHelper;
import com.interpss.core.net.Substation;
import com.interpss.core.net.nb.NBSwitch;

/**
 * Sample: import IEEE14 node-breaker RAW and split substation into groups by bus bars.
 */
public class PSSE_IEEE14_NB_BusSplit_Sample {

	/** Relative to {@code ipss.plugin.core} (launch.json cwd). */
	private static final String CASE =
			"testData/psse/nbreaker/IEEE_14_bus_nodeBreaker_rev35_exported.raw";

	public static void main(String[] args) throws InterpssException {
		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		AclfBus bus3 = net.getBus("Bus3");
		bus3.getContributeGenList().get(0).setQGenLimit(new LimitType(1.0, 0));

		AclfBus bus6 = net.getBus("Bus6");
		bus6.getContributeGenList().get(0).setQGenLimit(new LimitType(0.5, 0));

		Substation sub2 = net.getSubstation("2");

		SubstationNBreakerHelper subHelper = new SubstationNBreakerHelper(sub2);

		subHelper.openSwitch("Sw-BusBars");
		
		subHelper.printSubstationTree();

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.loadflow();
	}
}
