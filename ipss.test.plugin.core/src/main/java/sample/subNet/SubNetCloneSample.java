package sample.subNet;

import java.util.Set;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.IpssFileAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfNetHelper;

public class SubNetCloneSample {
	public static void main(String[] args) throws InterpssException {
		IpssCorePlugin.init();
		
		/*
		 * Basecase Aclf
		 */
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
				.load("ipss-plugin/ipss.test.plugin.core/testData/ipssdata/BUS1824.ipssdat")
				.getAclfNet();	
	
		//System.out.println(net.net2String());

	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.NR);
	  	algo.loadflow();
	  	
	  	String swingBusId = "1a";
	  	
  		AclfBus swingBus = (AclfBus)net.getBus(swingBusId);
  		AclfSwingBusAdapter swing = swingBus.toSwingBus();
		System.out.println(swing.getGenResults().getReal());
		System.out.println(swing.getGenResults().getImaginary());
  		
		/*
		 * SubNet Aclf
		 */
		// calculate subNet bus id set
		Set<String> busIdSet = new AclfNetHelper(net).calConnectedSubArea(swingBusId);
		System.out.println("BusIdSet size: " + busIdSet.size());
  
		// create a cloned subNet
		AclfNetwork cloneNet = net.createSubNet(busIdSet, false, false /*equivHvdc*/);
		
		LoadflowAlgorithm algoClone = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(cloneNet);
		algoClone.setLfMethod(AclfMethodType.NR);
		algoClone.loadflow();
	  	
		AclfBus swingBusClone = (AclfBus)cloneNet.getBus(swingBusId);
  		AclfSwingBusAdapter swingClone = swingBusClone.toSwingBus();
		System.out.println(swingClone.getGenResults().getReal());
		System.out.println(swingClone.getGenResults().getImaginary());
	}
}
