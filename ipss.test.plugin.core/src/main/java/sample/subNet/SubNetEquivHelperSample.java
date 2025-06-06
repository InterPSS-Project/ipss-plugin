package sample.subNet;

import java.util.Arrays;
import java.util.List;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.equiv.AclfNetworkEquivHelper;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class SubNetEquivHelperSample {

    public static void main(String[] args) throws InterpssException {
		IpssCorePlugin.init();
		
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.PSSE)
				.load("ipss-plugin/ipss.test.plugin.core/testData/adpter/psse/v30/Kundur_2area/Kundur_2area_v30.raw")
				.getAclfNet();

        LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(aclfNet);
	  	algo.setLfMethod(AclfMethodType.PQ);
        algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();

        System.out.println(aclfNet.net2String());

        // Create an instance of AclfNetworkEquivHelper
        AclfNetworkEquivHelper equivHelper = new AclfNetworkEquivHelper(aclfNet);

        // Set area 2 to be kept
        List<String> keptAreas = Arrays.asList(new String[]{"2"});
        equivHelper.defineKeptSubNetworkByAreas(keptAreas);
        // Create the equivalent sub-network
        AclfNetwork equivNet = equivHelper.createEquivNetwork(true);
        System.out.println("Equivalent Sub-Network:");
        //System.out.println(equivNet.net2String());

        LoadflowAlgorithm algo2 = CoreObjectFactory.createLoadflowAlgorithm(equivNet);
	  	algo2.setLfMethod(AclfMethodType.PQ);
        algo2.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo2.loadflow();

        System.out.println(equivNet.net2String());


    }

}
