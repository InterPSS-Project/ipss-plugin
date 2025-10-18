package sample.ei;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.compare.AclfNetObjectComparator;
import com.interpss.core.funcImpl.compare.AclfNetObjectUpdater;
import com.interpss.state.aclf.AclfNetworkState;

public class EasternInterconnctionUpdateSample {
	
	public static void main(String args[]) throws InterpssException {
		
		//IpssLogger.getLogger().setLevel(Level.INFO);
		
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v33/Base_Eastern_Interconnect_515GW.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33) 
				.load()
				.getImportedObj();
		
		System.out.println("Buses, Branches: " + net.getNoBus() + ", " + net.getNoBranch());
		System.out.println("Before MaxMismatch: " + net.maxMismatch(AclfMethodType.NR));
	  
		AclfNetwork originalNet = net.jsonCopy();
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		
		aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(true);
		aclfAlgo.setTolerance(0.0001);
		
		aclfAlgo.loadflow();
	  	
		System.out.println("After MaxMismatch: " + net.maxMismatch(AclfMethodType.NR));

        long loadStartTime = System.currentTimeMillis();
        System.out.println("Updateing the modified network back to the original network...");
  		AclfNetObjectUpdater updater = new AclfNetObjectUpdater(originalNet, net);
  		updater.update();
        long loadEndTime = System.currentTimeMillis();
        System.out.println("Network Update: " + (loadEndTime - loadStartTime)*0.001 + " s");
  		
        loadStartTime = System.currentTimeMillis();
        AclfNetObjectComparator comparator = new AclfNetObjectComparator(originalNet, net);
        comparator.compareNetwork();
        loadEndTime = System.currentTimeMillis();
        System.out.println("Network Compare: " + (loadEndTime - loadStartTime)*0.001 + " s");
        System.out.println(comparator.getDiffMsgList());
        
        loadStartTime = System.currentTimeMillis();
        System.out.println("Updateing the modified network back to the original network...");
  		for (int i = 0; i < 100; i++) {
  	  		updater.update();
  		}
        loadEndTime = System.currentTimeMillis();
        System.out.println("Network Update(100): " + (loadEndTime - loadStartTime)*0.001 + " s");
	}
}
