package org.interpss.nbreaker;

import org.interpss.fadapter.psse.PSSEDirectParser;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.topo.SubstationNBreakerHelper;
import com.interpss.core.net.Substation;

/**
 * Sample: import PSS/E v36 sample_nb RAW (node-breaker overlay) and inspect substations.
 */
public class PSSE_Sample_NB_Sample {

	/** Relative to {@code ipss.plugin.core} (launch.json cwd). */
	private static final String CASE = "testData/psse/v36/sample_nb.raw";

	public static void main(String[] args) throws InterpssException {
		AclfNetwork net = new PSSEDirectParser(36).parse(CASE);

		Substation sub5 = net.getSubstation("5");
		Substation sub9 = net.getSubstation("9");

		AclfBus bus215 = net.getBus("Bus215");
		Substation sub = bus215.getSubstation();
		SubstationNBreakerHelper subHelper = new SubstationNBreakerHelper(sub);
		subHelper.printSubstationTree();

		System.out.println("Substations: " + net.getSubstationMap().size()
				+ ", sub5=" + (sub5 != null ? sub5.getName() : "null")
				+ ", sub9=" + (sub9 != null ? sub9.getName() : "null"));

		//LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		//algo.loadflow();
	}
}
