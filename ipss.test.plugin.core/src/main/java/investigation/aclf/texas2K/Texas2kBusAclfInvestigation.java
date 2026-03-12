package investigation.aclf.texas2K;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.util.QAUtil;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfNetInfoHelper;

public class Texas2kBusAclfInvestigation {
	
	public static void main(String args[]) throws InterpssException {
		
		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v36/Texas2k/Texas2k_series24_case1_2016summerPeak_v36.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_36) 
				.load()
				.getImportedObj();
		
		AclfNetwork netPsse = net.jsonCopy();
		
		System.out.println("Buses, Branches: " + net.getNoBus() + ", " + net.getNoBranch());
	  
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		
		aclfAlgo.getNrMethodConfig().setNonDivergent(true);
		
		aclfAlgo.setTolerance(1.0E-4);
		aclfAlgo.setMaxIterations(50);
		
		System.out.println("MaxMismatch (Before): " + net.maxMismatch(AclfMethodType.NR));
		
		aclfAlgo.loadflow();
		
		System.out.println("MaxMismatch (After): " + net.maxMismatch(AclfMethodType.NR));
		
		QAUtil.getMaxBusVoltageDiff(net, netPsse);
		
		QAUtil.getMaxGenPOutputDiff(net, netPsse);
		
		QAUtil.getMaxBranchFlowDiff(net, netPsse, 0.00001);
		
		System.out.println("\n\n===========IpssNet ===========");
		AclfNetInfoHelper.outputBranchAclfDebugInfo(net, "Bus7366->Bus7400(1)", false);
		
		System.out.println("\n\n===========PsseNet ===========");
		AclfNetInfoHelper.outputBranchAclfDebugInfo(netPsse, "Bus7366->Bus7400(1)", false);
		
/*
 *      PSSE Result
 *      
 *      Bus7400: genP: 8.784, genQ: -16.74503, qGenLimit: ( 4.4712, -0.9749 ), voltSpec: 1.0		
 *      
		gen: (0.72, -1.37254), desiredVoltMag: 1.0, qGenLimit: ( 0.3665, -0.0799 ), 
		gen: (0.72, -1.37254), desiredVoltMag: 1.0, qGenLimit: ( 0.3665, -0.0799 )
		gen: (0.72, -1.37254), desiredVoltMag: 1.0, qGenLimit: ( 0.3665, -0.0799 )
		gen: (0.72, -1.37254), desiredVoltMag: 1.0, qGenLimit: ( 0.3665, -0.0799 ), 
		gen: (1.44, -2.74509), desiredVoltMag: 1.0, qGenLimit: ( 0.733, -0.1598 ), 
		gen: (4.464, -8.50978), desiredVoltMag: 1.0, qGenLimit: ( 2.2722, -0.4955 ), 
		
		Observation:
		
		1) PSSE format results at Bus7400 are not correct, the genP is 8.784, but the genQ is -16.74503, 
		which is much larger than the qGenLimit.min of -0.0799.
		2) The genQ is -16.74503, which is much larger than the qGenLimit.min of -0.0799, 
		but the voltage spec is set to 1.0, which is too low for the available gen Q limit.  
*/
	}
}
