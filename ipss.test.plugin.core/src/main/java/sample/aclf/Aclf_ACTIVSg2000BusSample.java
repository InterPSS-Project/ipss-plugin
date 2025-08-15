package sample.aclf;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.interpss.IpssCorePlugin;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class Aclf_ACTIVSg2000BusSample {
	
	public static void main(String args[]) throws InterpssException {
		IpssCorePlugin.init();
		
		IpssLogger.getLogger().setLevel(Level.INFO);
		
		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v36/Texas2k/Texas2k_series24_case1_2016summerPeak_v36.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_36) 
				.load()
				.getImportedObj();
		
		System.out.println("Buses, Branches: " + net.getNoBus() + ", " + net.getNoBranch());
	  
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(net);
		
		//aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

		//aclfAlgo.getLfAdjAlgo().setPowerAdjAppType(AdjustApplyType.POST_ITERATION);
		aclfAlgo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(false);
		aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		aclfAlgo.setTolerance(1.0E-6);
		
		System.out.println("MaxMismatch: " + net.maxMismatch(AclfMethodType.NR));
	}
}
