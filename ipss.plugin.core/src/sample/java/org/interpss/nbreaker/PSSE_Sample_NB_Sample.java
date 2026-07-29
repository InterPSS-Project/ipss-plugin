package org.interpss.nbreaker;

import org.interpss.fadapter.psse.PSSEDirectParser;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfNetInfoHelper;
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

		/* 
		Substation sub5 = net.getSubstation("5");
		Substation sub9 = net.getSubstation("9");

		SubstationNBreakerHelper subHelper5 = new SubstationNBreakerHelper(sub5);
		subHelper5.topoAnalysis();
		//subHelper5.printSubstationTree();

		SubstationNBreakerHelper subHelper9 = new SubstationNBreakerHelper(sub9);
		subHelper9.topoAnalysis();
		//subHelper9.printSubstationTree();
		//subHelper9.printTopoFlags();

		/* 
		net.getBus("Bus3010").setStatus(false);
		net.getBus("Bus215").setStatus(false);
		net.getBus("Bus401").setStatus(false);
		net.getBus("Bus402").setStatus(false);
		*/

		AclfBus bus301 = net.getBus("Bus301");
		// Expected null substation: sample_nb.raw has no SUBSTATION NODE for bus 301
		// (also 401, 402). Importer assigns via NB node bus I only —
		// see testData/psse/v36/sample-nb-substation-nbModel.md.

		/* 
		AclfBranch bus3010Leg = net.getBranch("3WNDTR_3008_3012_3010_2->Bus3010(2)");
		bus3010Leg.setStatus(true);
		AclfNetInfoHelper.outputBusAclfDebugInfo(net, "Bus3010", false);
		*/
		
		net.getSubstationMap().forEach((subName, sub) -> {	
			SubstationNBreakerHelper subHelper = new SubstationNBreakerHelper(sub);
			subHelper.topoAnalysis();

			AclfNetInfoHelper.outputSubstationAclfInfo(net, subName, false);
		});

		//AclfNetInfoHelper.outputSubstationAclfInfo(net, "9", false);

		/* 
		AclfBranch bus215Leg = net.getBranch("3WNDTR_205_215_208_3->Bus215(3)");
		bus215Leg.setStatus(true);
		Aclf3WBranch bus215Xfr = net.get3WXfr("Bus205", "Bus215", "Bus208", "3");
		bus215Xfr.setStatus(true);
		AclfNetInfoHelper.outputBusAclfDebugInfo(net, "Bus215", false);
		*/

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.loadflow();
	}
}
