package sample.ei;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfNetObjectComparator;
import com.interpss.state.aclf.AclfNetworkState;

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
	  
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		
		//aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

		//aclfAlgo.getLfAdjAlgo().setPowerAdjAppType(AdjustApplyType.POST_ITERATION);
		// not need to turn off the power adjustment, since the turn of Adjust will turn off the power adjustmentå
		//aclfAlgo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(false);
		aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		aclfAlgo.setTolerance(0.0001);
		
		aclfAlgo.loadflow();
	  	
		System.out.println("After MaxMismatch: " + net.maxMismatch(AclfMethodType.NR));
		/*
        long loadStartTime = System.currentTimeMillis();
        AclfNetworkState clonedNetBean = new AclfNetworkState(net);
        for (int i = 0; i < 10; i++) {
        	AclfNetwork copyNet = AclfNetworkState.create(clonedNetBean);
        }
        long loadEndTime = System.currentTimeMillis();
        System.out.println("Network json copy(1) " + (loadEndTime - loadStartTime)*0.001 + " s");
        
        loadStartTime = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
        	AclfNetwork copyNet = net.jsonCopy();
        }
        loadEndTime = System.currentTimeMillis();
        System.out.println("Network json copy(2) " + (loadEndTime - loadStartTime)*0.001 + " s");
        
        AclfNetwork copyNet = net.jsonCopy();
        loadStartTime = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
        	new AclfNetObjectComparator(net, copyNet).compareNetwork();
        }
        loadEndTime = System.currentTimeMillis();
        System.out.println("Network json copy(3) " + (loadEndTime - loadStartTime)*0.001 + " s");
        */
	}
}
