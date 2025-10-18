package sample.subNet;

import java.util.Arrays;
import java.util.List;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.equiv.AclfNetworkEquivHelper;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.compare.AclfNetObjectComparator;

public class SubNetEquivHelperSample {

    public static void main(String[] args) throws InterpssException {
		IpssCorePlugin.init();
		
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.PSSE)
				.load("testData/adpter/psse/v30/Kundur_2area/Kundur_2area_v30.raw")
				.getAclfNet();

        LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);
	  	algo.setLfMethod(AclfMethodType.PQ);
        algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();

        //System.out.println(aclfNet.net2String());

        // Create an instance of AclfNetworkEquivHelper
        AclfNetworkEquivHelper equivHelper = new AclfNetworkEquivHelper(aclfNet);

        // Set area 2 to be kept
        List<String> keptAreas = Arrays.asList(new String[]{"2"});
        equivHelper.defineKeptSubNetworkByAreas(keptAreas);
        // Create the equivalent sub-network
        AclfNetwork equivNet = equivHelper.createEquivNetwork(true);
        System.out.println("Equivalent Sub-Network:");
        //System.out.println(equivNet.net2String());

        LoadflowAlgorithm algo2 = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(equivNet);
	  	algo2.setLfMethod(AclfMethodType.PQ);
        algo2.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo2.loadflow();

        //System.out.println(equivNet.net2String());

	  	AclfNetObjectComparator comparator = new AclfNetObjectComparator(aclfNet, equivNet);
	  	
        String busId = "Bus9";
        comparator.compareBus(busId);
        
        String braId = "Bus3->Bus11(1)";
        //String braId = "Bus4->Bus10(1)";
        comparator.compareBranch(braId);  
        
        System.out.println("Comparison results " + comparator.getDiffMsgList());
    }
}
