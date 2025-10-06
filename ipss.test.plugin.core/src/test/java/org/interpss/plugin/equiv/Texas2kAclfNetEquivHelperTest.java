package org.interpss.plugin.equiv;

import java.util.Arrays;
import java.util.List;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.display.impl.AclfOut_PSSE;
import org.interpss.display.impl.AclfOut_PSSE.Format;
import org.interpss.fadapter.IpssFileAdapter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfGenBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class Texas2kAclfNetEquivHelperTest {

   

     @Test
   /** This test case loads a PSSE v30 Kundur 2-area network, performs a load flow analysis,
   * and then creates an equivalent sub-network by keeping only area 1 which does not include the swing bus in the original network.
   * It then performs a load flow analysis on the equivalent sub-network and prints the results.
   */
    public void testEquivByCopy() throws InterpssException {
        IpssCorePlugin.init();
        
        AclfNetwork aclfNet = CorePluginFactory
            .getFileAdapter(IpssFileAdapter.FileFormat.PSSE, IpssFileAdapter.Version.PSSE_35)
            .load("testData/psse/v35/Texas2k/Texas2k_series24_case1_2016summerPeak_v35.RAW")
            .getAclfNet();

        LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);
        algo.setLfMethod(AclfMethodType.NR);
        algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
        algo.loadflow();

        //System.out.println(AclfOutFunc.loadFlowSummary(aclfNet));

        String checkBusId = "Bus8033";//"Bus7009";//"Bus6010";//"Bus5035";// "Bus4010"; //"Bus3004"

        AclfBus Bus1 = aclfNet.getBus(checkBusId);
        AclfGenBusAdapter bus1Adapter = Bus1.toGenBus();
        System.out.println(bus1Adapter.getGenResults().getReal());
        System.out.println(bus1Adapter.getGenResults().getImaginary());


        // Create an instance of AclfNetworkEquivHelper
        AclfNetworkEquivHelper equivHelper = new AclfNetworkEquivHelper(aclfNet);

        // Set area 2 to be kept
        List<String> keptAreas = Arrays.asList(new String[]{"8"});
        equivHelper.defineKeptSubNetworkByAreas(keptAreas);
        // Create the equivalent sub-network
        AclfNetwork equivNet = equivHelper.createEquivNetwork(true);
       // System.out.println("Equivalent Sub-Network:");
        //System.out.println(equivNet.net2String());

        LoadflowAlgorithm algo2 = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(equivNet);
        algo2.setLfMethod(AclfMethodType.NR);
        algo2.getLfAdjAlgo().setApplyAdjustAlgo(false);
        algo2.loadflow();
        assertTrue(equivNet.isLfConverged());
        System.out.println(AclfOutFunc.loadFlowSummary(equivNet));
        System.out.println(AclfOut_PSSE.lfResults(equivNet,Format.GUI));

        //System.out.println(equivNet.net2String());
        AclfBus bus1Clone = equivNet.getBus(checkBusId);
        AclfGenBusAdapter bus1CloneAdapter = bus1Clone.toGenBus();
        System.out.println(bus1CloneAdapter.getGenResults().getReal());
        System.out.println(bus1CloneAdapter.getGenResults().getImaginary());

        // Assert that the swing bus results in the equivalent network match the original network
        assertEquals(bus1Adapter.getGenResults().getReal(), bus1CloneAdapter.getGenResults().getReal(), 0.00001);
        assertEquals(bus1Adapter.getGenResults().getImaginary(), bus1CloneAdapter.getGenResults().getImaginary(), 0.00001);
    }

}
