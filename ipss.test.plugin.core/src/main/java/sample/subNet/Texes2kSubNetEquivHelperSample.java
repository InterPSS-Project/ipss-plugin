package sample.subNet;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.display.impl.AclfOut_BusStyle;
import org.interpss.display.impl.AclfOut_PSSE;
import org.interpss.display.impl.AclfOut_PSSE.Format;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.equiv.AclfNetworkEquivHelper;
import org.interpss.util.FileUtil;

import com.interpss.common.util.IpssLogger;


import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class Texes2kSubNetEquivHelperSample {

    public static void main(String[] args) throws InterpssException {
		IpssCorePlugin.init();
    IpssLogger.getLogger().setLevel(Level.INFO);

		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.PSSE,IpssFileAdapter.Version.PSSE_33)
				.load("ipss-plugin/ipss.test.plugin.core/testData/adpter/psse/v33/ACTIVSg2000/ACTIVSg2000.RAW")
				.getAclfNet();

    LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(aclfNet);
	  algo.setLfMethod(AclfMethodType.NR);
    algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  algo.loadflow();

    //System.out.println(AclfOutFunc.loadFlowSummary(aclfNet));
    //save the loadflow results to a file by creating a file and save the string to it
    //FileUtil.writeText2File("ipss-plugin/ipss.test.plugin.core/output/Texes2kBasecase_LF_Results.txt", AclfOutFunc.loadFlowSummary(aclfNet));
    FileUtil.writeText2File("ipss-plugin/ipss.test.plugin.core/output/Texes2kBasecase_LF_Results_PSSE_GUI.txt", AclfOut_PSSE.lfResults(aclfNet, Format.GUI));

    // Create an instance of AclfNetworkEquivHelper
    AclfNetworkEquivHelper equivHelper = new AclfNetworkEquivHelper(aclfNet);

    // Set area 1 to be kept
    List<String> keptAreas = Arrays.asList(new String[]{"8"}); // single areas 1 and 8 are okay
    equivHelper.defineKeptSubNetworkByAreas(keptAreas);

    System.out.println("boundary buses: " + equivHelper.getBoundaryBuses());
      // Create the equivalent sub-network
    AclfNetwork equivNet = equivHelper.createEquivNetwork(false);
    System.out.println("Equivalent Sub-Network:");
    //System.out.println(equivNet.net2String());
    FileUtil.writeText2File("ipss-plugin/ipss.test.plugin.core/output/Texes2k_equivNet.txt", equivNet.net2String());
    for (AclfBus bus : equivNet.getBusList()) {
        assert(bus.mismatch(AclfMethodType.NR).abs() < 0.0001);
    }

    LoadflowAlgorithm algo2 = CoreObjectFactory.createLoadflowAlgorithm(equivNet);
    algo2.setLfMethod(AclfMethodType.NR);
    algo2.getLfAdjAlgo().setApplyAdjustAlgo(false);
    //algo2.setNonDivergent(true); // set to true to avoid divergence due to large swing bus gen
    algo2.loadflow();

    System.out.println(AclfOutFunc.loadFlowSummary(equivNet));
    //System.out.println(AclfOutFunc.busSummaryCommaDelimited(equivNet,true,true));
    //System.out.println(AclfOut_PSSE.lfResults(equivNet, Format.GUI));





    }

}
