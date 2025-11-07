package org.interpss.plugin.equiv;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.display.impl.AclfOut_PSSE;
import org.interpss.display.impl.AclfOut_PSSE.Format;
import org.interpss.fadapter.IpssFileAdapter;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfGenBusAdapter;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.aclf.hvdc.HvdcLine2T;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.Branch;

public class AclfNetEquivHelperTest {

   @Test
   /** This test case loads a PSSE v30 Kundur 2-area network, performs a load flow analysis,
   * and then creates an equivalent sub-network by using createSubNet with copy method of AclfNetwork and keeping only area 2 which includes the swing bus.
   * It then performs a load flow analysis on the equivalent sub-network and prints the results.
   */
    public void testEquivCopySubNetWithSwingbus() throws InterpssException {
        IpssCorePlugin.init();
        
        AclfNetwork aclfNet = CorePluginFactory
            .getFileAdapter(IpssFileAdapter.FileFormat.PSSE)
            .load("testData/adpter/psse/v30/Kundur_2area/Kundur_2area_v30.raw")
            .getAclfNet();

        LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);
        algo.setLfMethod(AclfMethodType.PQ);
        algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
        algo.loadflow();

        String swingBusId = "Bus3";
          
        AclfBus swingBus = aclfNet.getBus(swingBusId);
        AclfSwingBusAdapter swing = swingBus.toSwingBus();
        System.out.println(swing.getGenResults().getReal());
        System.out.println(swing.getGenResults().getImaginary());


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
        AclfBus swingBusClone = equivNet.getBus(swingBusId);
        AclfSwingBusAdapter swingClone = swingBusClone.toSwingBus();
        System.out.println(swingClone.getGenResults().getReal());
        System.out.println(swingClone.getGenResults().getImaginary());

        // Assert that the swing bus results in the equivalent network match the original network
        assertEquals(swing.getGenResults().getReal(), swingClone.getGenResults().getReal(), 0.00001);
        assertEquals(swing.getGenResults().getImaginary(), swingClone.getGenResults().getImaginary(), 0.00001);



    }

    @Test
    public void testEquivTurnOffExternalWithSwingbus() throws InterpssException {
        IpssCorePlugin.init();
        
        AclfNetwork aclfNet = CorePluginFactory
            .getFileAdapter(IpssFileAdapter.FileFormat.PSSE)
            .load("testData/adpter/psse/v30/Kundur_2area/Kundur_2area_v30.raw")
            .getAclfNet();

        LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);
        algo.setLfMethod(AclfMethodType.PQ);
        algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
        algo.loadflow();

        String swingBusId = "Bus3";
          
        AclfBus swingBus = aclfNet.getBus(swingBusId);
        AclfSwingBusAdapter swing = swingBus.toSwingBus();
        System.out.println(swing.getGenResults().getReal());
        System.out.println(swing.getGenResults().getImaginary());


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
        AclfBus swingBusClone = equivNet.getBus(swingBusId);
        AclfSwingBusAdapter swingClone = swingBusClone.toSwingBus();
        System.out.println(swingClone.getGenResults().getReal());
        System.out.println(swingClone.getGenResults().getImaginary());

        // Assert that the swing bus results in the equivalent network match the original network
        assertEquals(swing.getGenResults().getReal(), swingClone.getGenResults().getReal(), 0.00001);
        assertEquals(swing.getGenResults().getImaginary(), swingClone.getGenResults().getImaginary(), 0.00001);



    }

    @Test
   /** This test case loads a PSSE v30 Kundur 2-area network, performs a load flow analysis,
   * and then creates an equivalent sub-network by keeping only area 1 which does not include the swing bus in the original network.
   * It then performs a load flow analysis on the equivalent sub-network and prints the results.
   */
    public void testEquivCopySubNetWithoutSwingbus() throws InterpssException {
        IpssCorePlugin.init();
        
        AclfNetwork aclfNet = CorePluginFactory
            .getFileAdapter(IpssFileAdapter.FileFormat.PSSE)
            .load("testData/adpter/psse/v30/Kundur_2area/Kundur_2area_v30.raw")
            .getAclfNet();

        LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);
        algo.setLfMethod(AclfMethodType.NR);
        algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
        algo.loadflow();
        System.out.println(AclfOutFunc.loadFlowSummary(aclfNet));

        String checkBusId = "Bus1";

        AclfBus Bus1 = aclfNet.getBus(checkBusId);
        AclfGenBusAdapter bus1Adapter = Bus1.toGenBus();
        System.out.println(bus1Adapter.getGenResults().getReal());
        System.out.println(bus1Adapter.getGenResults().getImaginary());


        // Create an instance of AclfNetworkEquivHelper
        AclfNetworkEquivHelper equivHelper = new AclfNetworkEquivHelper(aclfNet);

        // Set area 2 to be kept
        List<String> keptAreas = Arrays.asList(new String[]{"1"});
        equivHelper.defineKeptSubNetworkByAreas(keptAreas);
        // Create the equivalent sub-network
        AclfNetwork equivNet = equivHelper.createEquivNetwork(true);
        System.out.println("Equivalent Sub-Network:");
        //System.out.println(equivNet.net2String());

        LoadflowAlgorithm algo2 = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(equivNet);
        algo2.setLfMethod(AclfMethodType.NR);
        algo2.getLfAdjAlgo().setApplyAdjustAlgo(false);
        algo2.loadflow();
        System.out.println(AclfOutFunc.loadFlowSummary(equivNet));

        //System.out.println(equivNet.net2String());
        AclfBus bus1Clone = equivNet.getBus(checkBusId);
        AclfGenBusAdapter bus1CloneAdapter = bus1Clone.toGenBus();
        System.out.println(bus1CloneAdapter.getGenResults().getReal());
        System.out.println(bus1CloneAdapter.getGenResults().getImaginary());

        // Assert that the swing bus results in the equivalent network match the original network
        assertEquals(bus1Adapter.getGenResults().getReal(), bus1CloneAdapter.getGenResults().getReal(), 0.00001);
        assertEquals(bus1Adapter.getGenResults().getImaginary(), bus1CloneAdapter.getGenResults().getImaginary(), 0.00001);



    }


    @Test
   /** This test case loads a PSSE v30 Kundur 2-area network, performs a load flow analysis,
   * and then creates an equivalent sub-network by keeping only area 1 which does not include the swing bus in the original network.
   * It then performs a load flow analysis on the equivalent sub-network and prints the results.
   */
    public void testEquivTurnOffExternalWithoutSwingbus() throws InterpssException {
        IpssCorePlugin.init();
        
        AclfNetwork aclfNet = CorePluginFactory
            .getFileAdapter(IpssFileAdapter.FileFormat.PSSE)
            .load("testData/adpter/psse/v30/Kundur_2area/Kundur_2area_v30.raw")
            .getAclfNet();

        LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);
        algo.setLfMethod(AclfMethodType.NR);
        algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
        algo.loadflow();
        System.out.println(AclfOutFunc.loadFlowSummary(aclfNet));

        String checkBusId = "Bus1";

        AclfBus Bus1 = aclfNet.getBus(checkBusId);
        AclfGenBusAdapter bus1Adapter = Bus1.toGenBus();
        System.out.println(bus1Adapter.getGenResults().getReal());
        System.out.println(bus1Adapter.getGenResults().getImaginary());


        // Create an instance of AclfNetworkEquivHelper
        AclfNetworkEquivHelper equivHelper = new AclfNetworkEquivHelper(aclfNet);

        // Set area 2 to be kept
        List<String> keptAreas = Arrays.asList(new String[]{"1"});
        equivHelper.defineKeptSubNetworkByAreas(keptAreas);
        // Create the equivalent sub-network
        AclfNetwork equivNet = equivHelper.createEquivNetwork(true);
        System.out.println("Equivalent Sub-Network:");
        //System.out.println(equivNet.net2String());

        LoadflowAlgorithm algo2 = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(equivNet);
        algo2.setLfMethod(AclfMethodType.NR);
        algo2.getLfAdjAlgo().setApplyAdjustAlgo(false);
        algo2.loadflow();
        System.out.println(AclfOutFunc.loadFlowSummary(equivNet));

        //System.out.println(equivNet.net2String());
        AclfBus bus1Clone = equivNet.getBus(checkBusId);
        AclfGenBusAdapter bus1CloneAdapter = bus1Clone.toGenBus();
        System.out.println(bus1CloneAdapter.getGenResults().getReal());
        System.out.println(bus1CloneAdapter.getGenResults().getImaginary());

        // Assert that the swing bus results in the equivalent network match the original network
        assertEquals(bus1Adapter.getGenResults().getReal(), bus1CloneAdapter.getGenResults().getReal(), 0.00001);
        assertEquals(bus1Adapter.getGenResults().getImaginary(), bus1CloneAdapter.getGenResults().getImaginary(), 0.00001);



    }

    @Test
   /** This test case loads a PSSE v30 Kundur 2-area network, performs a load flow analysis,
   * and then creates an equivalent sub-network by keeping only area 1 which does not include the swing bus in the original network.
   * It then performs a load flow analysis on the equivalent sub-network and prints the results.
   */
    public void testEquivCopySubNetWithoutSwingbus_LCCHVDC() throws InterpssException {
        IpssCorePlugin.init();
        
        AclfNetwork aclfNet = CorePluginFactory
            .getFileAdapter(IpssFileAdapter.FileFormat.PSSE, IpssFileAdapter.Version.PSSE_33)
            .load("testData/adpter/psse/v33/Kundur_2area_LCC_HVDC.raw")
            .getAclfNet();

        LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);
        algo.setLfMethod(AclfMethodType.NR);
        algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
        algo.loadflow();

        System.out.println(AclfOutFunc.loadFlowSummary(aclfNet));

        for(Branch bra: aclfNet.getSpecialBranchList()){
            if(bra != null && bra.isActive() && bra instanceof HvdcLine2T){
                HvdcLine2T hvdcBranch = (HvdcLine2T) bra;
                Complex power_rec = hvdcBranch.powerIntoConverter(hvdcBranch.getFromBusId());
                Complex power_inv = hvdcBranch.powerIntoConverter(hvdcBranch.getToBusId());
                System.out.println("HVDC Branch Power (From): " + power_rec);
                System.out.println("HVDC Branch Power (To): " + power_inv);

            }
        }

        String checkBusId = "Bus1";

        AclfBus Bus1 = aclfNet.getBus(checkBusId);
        AclfGenBusAdapter bus1Adapter = Bus1.toGenBus();
        System.out.println(bus1Adapter.getGenResults().getReal());
        System.out.println(bus1Adapter.getGenResults().getImaginary());


        // Create an instance of AclfNetworkEquivHelper
        AclfNetworkEquivHelper equivHelper = new AclfNetworkEquivHelper(aclfNet);

        // Set area 2 to be kept
        List<String> keptAreas = Arrays.asList(new String[]{"1"});
        equivHelper.defineKeptSubNetworkByAreas(keptAreas);
        // Create the equivalent sub-network
        AclfNetwork equivNet = equivHelper.createEquivNetwork(true);
        System.out.println("Equivalent Sub-Network:");
        System.out.println(equivNet.net2String());

        LoadflowAlgorithm algo2 = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(equivNet);
        algo2.setLfMethod(AclfMethodType.NR);
        algo2.getLfAdjAlgo().setApplyAdjustAlgo(false);
        algo2.loadflow();
        System.out.println(AclfOutFunc.loadFlowSummary(equivNet));
        System.out.println(AclfOut_PSSE.lfResults(equivNet,Format.GUI));

        //System.out.println(equivNet.net2String());
        AclfBus bus1Clone = equivNet.getBus(checkBusId);
        AclfGenBusAdapter bus1CloneAdapter = bus1Clone.toGenBus();
        System.out.println(bus1CloneAdapter.getGenResults().getReal());
        System.out.println(bus1CloneAdapter.getGenResults().getImaginary());

        // Assert that the swing bus results in the equivalent network match the original network
        assertEquals(bus1Adapter.getGenResults().getReal(), bus1CloneAdapter.getGenResults().getReal(), 0.0001);
        assertEquals(bus1Adapter.getGenResults().getImaginary(), bus1CloneAdapter.getGenResults().getImaginary(), 0.0001);



    }

     @Test
   /** This test case loads a PSSE v30 Kundur 2-area network, performs a load flow analysis,
   * and then creates an equivalent sub-network by keeping only area 1 which does not include the swing bus in the original network.
   * It then performs a load flow analysis on the equivalent sub-network and prints the results.
   */
    public void testEquivTurnoffExternalWithoutSwingbus_LCCHVDC() throws InterpssException {
        IpssCorePlugin.init();
        
        AclfNetwork aclfNet = CorePluginFactory
            .getFileAdapter(IpssFileAdapter.FileFormat.PSSE, IpssFileAdapter.Version.PSSE_33)
            .load("testData/adpter/psse/v33/Kundur_2area_LCC_HVDC.raw")
            .getAclfNet();

        LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);
        algo.setLfMethod(AclfMethodType.NR);
        algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
        algo.loadflow();

        System.out.println(AclfOutFunc.loadFlowSummary(aclfNet));

        for(Branch bra: aclfNet.getSpecialBranchList()){
            if(bra != null && bra.isActive() && bra instanceof HvdcLine2T){
                HvdcLine2T hvdcBranch = (HvdcLine2T) bra;
                Complex power_rec = hvdcBranch.powerIntoConverter(hvdcBranch.getFromBusId());
                Complex power_inv = hvdcBranch.powerIntoConverter(hvdcBranch.getToBusId());
                System.out.println("HVDC Branch Power (From): " + power_rec);
                System.out.println("HVDC Branch Power (To): " + power_inv);

            }
        }

        String checkBusId = "Bus1";

        AclfBus Bus1 = aclfNet.getBus(checkBusId);
        AclfGenBusAdapter bus1Adapter = Bus1.toGenBus();
        System.out.println(bus1Adapter.getGenResults().getReal());
        System.out.println(bus1Adapter.getGenResults().getImaginary());


        // Create an instance of AclfNetworkEquivHelper
        AclfNetworkEquivHelper equivHelper = new AclfNetworkEquivHelper(aclfNet);

        // Set area 2 to be kept
        List<String> keptAreas = Arrays.asList(new String[]{"1"});
        equivHelper.defineKeptSubNetworkByAreas(keptAreas);
        // Create the equivalent sub-network
        AclfNetwork equivNet = equivHelper.createEquivNetwork(false);
        System.out.println("Equivalent Sub-Network:");
        System.out.println(equivNet.net2String());

        LoadflowAlgorithm algo2 = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(equivNet);
        algo2.setLfMethod(AclfMethodType.NR);
        algo2.getLfAdjAlgo().setApplyAdjustAlgo(false);
        algo2.loadflow();
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
