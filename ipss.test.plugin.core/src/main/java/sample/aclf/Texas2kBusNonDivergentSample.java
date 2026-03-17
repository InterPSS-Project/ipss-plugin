package sample.aclf;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.util.QAUtil;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfNetInfoHelper;

public class Texas2kBusNonDivergentSample {
	
	public static void main(String args[]) throws InterpssException {
		
		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v36/Texas2k/Texas2k_series24_case1_2016summerPeak_v36.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_36) 
				.load()
				.getImportedObj();
		
		AclfNetwork netCopy = net.jsonCopy();
		
		System.out.println("Buses, Branches: " + net.getNoBus() + ", " + net.getNoBranch());
	  
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		
		aclfAlgo.getNrMethodConfig().setNonDivergent(true);
		
		aclfAlgo.setTolerance(1.0E-4);
		aclfAlgo.setMaxIterations(50);
		
		System.out.println("MaxMismatch (Before): " + net.maxMismatch(AclfMethodType.NR));
		
		aclfAlgo.loadflow();
		
		System.out.println("MaxMismatch (After): " + net.maxMismatch(AclfMethodType.NR));
	}
}
