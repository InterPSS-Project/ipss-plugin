package sample.ei;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class EasternInterconnctionSample {
	
	public static void main(String args[]) throws InterpssException {
		
		//IpssLogger.getLogger().setLevel(Level.INFO);
		
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v33/Base_Eastern_Interconnect_515GW.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33) 
				.load()
				.getImportedObj();
		
		System.out.println("Buses, Branches: " + net.getNoBus() + ", " + net.getNoBranch());
		System.out.println("Before MaxMismatch: " + net.maxMismatch(AclfMethodType.NR));
	  
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(net);
		
		//aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

		//aclfAlgo.getLfAdjAlgo().setPowerAdjAppType(AdjustApplyType.POST_ITERATION);
		aclfAlgo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(false);
		aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		aclfAlgo.setTolerance(0.0001);
		
		aclfAlgo.loadflow();
	  	
		System.out.println("After MaxMismatch: " + net.maxMismatch(AclfMethodType.NR));
	}
}
