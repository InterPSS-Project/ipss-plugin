package org.interpss.multiNet.test;

import static org.junit.Assert.assertTrue;

import java.util.Hashtable;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.FieldMatrix;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.multiNet.algo.MultiNetDStabSimuHelper;
import org.interpss.multiNet.algo.SubNetworkProcessor;
import org.interpss.multiNet.equivalent.NetworkEquivalent;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabLoad;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestSubNetEquiv {
	
		
		@Test
		public void test_SubNetEquivMatrix_IEEE9Bus() throws InterpssException{
			IpssCorePlugin.init();
			IpssCorePlugin.setLoggerLevel(Level.INFO);
			PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
			assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
					"testData/IEEE9Bus/ieee9.raw",
					"testData/IEEE9Bus/ieee9.seq",
					//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
					"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
			}));
			DStabModelParser parser =(DStabModelParser) adapter.getModel();
			
			//System.out.println(parser.toXmlDoc());

			
			
			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabParserMapper(IpssCorePlugin.getMsgHub())
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			
		    DStabilityNetwork dsNet =(DStabilityNetwork) simuCtx.getDStabilityNet();
		    
			
			LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
			assertTrue(aclfAlgo.loadflow());
			System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			
			
	         //Calculated tie-line current

            Complex vdiff_5_7 = dsNet.getBus("Bus5").getVoltage().subtract(dsNet.getBus("Bus7").getVoltage());
            Complex curr_5_7 = vdiff_5_7.divide(dsNet.getBranch("Bus5->Bus7(0)").getZ());
            
            
            Complex vdiff_7_8 = dsNet.getBus("Bus7").getVoltage().subtract(dsNet.getBus("Bus8").getVoltage());
            Complex curr_7_8 = vdiff_7_8.divide(dsNet.getBranch("Bus7->Bus8(0)").getZ());
            
            System.out.println("Calculated current 5->7 = "+curr_5_7 );
		    
		    /*
		     * Step-1  split the full network into two sub-network
		     */
		    
		    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		    proc.addSubNetInterfaceBranch("Bus5->Bus7(0)");
		    proc.addSubNetInterfaceBranch("Bus7->Bus8(0)");
		    
		    proc.splitFullSystemIntoSubsystems(false);
		    
		    // need to initialize the dstab network to perform the subnetwork solution
		    

		    
		    
		    /*
		     * Step-2  use MultiNetDStabSimuHelper to preProcess the multiNetwork
		     */
		    MultiNetDStabSimuHelper multiNetHelper = new  MultiNetDStabSimuHelper(dsNet,proc);
		   // multiNetHelper.processInterfaceBranchEquiv();
		    
		    for(BaseDStabNetwork<?, ?> subNet: proc.getSubNetworkList()){
		    	subNet.initDStabNet();
		    }
		    
		    
		    //
		    //test-1 the interface tie-line to the subsystem boundary bus incidence matrix
		    //
            Hashtable<String,FieldMatrix<Complex>> incidMatrix = multiNetHelper.getSubNetIncidenceMatrixTable();
		 
            /*
             * subNetwork Bus2 and Bus7, with Bus7 as the boundary bus
             * 
             * Tie-lines:
             *
             * "Bus5->Bus7(0)"
             * "Bus7->Bus8(0)"
             * 
             * Thus the incidence matrix Pk_T is [ -1,1]^T
             * 
             */
            
            FieldMatrix<Complex> P2_T = incidMatrix.get("SubNet-2");
            assertTrue( NumericUtil.equals(P2_T.getEntry(0, 0),new Complex(-1.0, 0.0)));
            assertTrue( NumericUtil.equals(P2_T.getEntry(1, 0),new Complex(1.0, 0.0)));
             System.out.println ( P2_T);
             
             
             
            // 
            // test-2  the subsystem equivalent matrix: Zth
		    //
            
            multiNetHelper.solvSubNetAndUpdateEquivSource();
             
             Hashtable<String, NetworkEquivalent> equivTable= multiNetHelper.getSubNetEquivTable();
             NetworkEquivalent equiv2=equivTable.get("SubNet-2");
            
             System.out.println("Zii at bus7 ="+equiv2.getComplexEqn().getAij(0,0));
             assertTrue(NumericUtil.equals(equiv2.getComplexEqn().getAij(0,0),new Complex(0.0, 0.15690803143610757),1.0E-6));
             
            // 
            // test-3 the subsystem equivalent Thevenin equivalent voltage Source
            //
             
             Complex[] Vth = equiv2.getComplexEqn().getB();
             
           //  
           // test-4 boundary subsystem solution: solve Zl*Il = Eth to obtain currents of interface tie-lines 
           //
             
             multiNetHelper.prepareBoundarySubSystemMatrix();
             
             multiNetHelper.solveBoundarySubSystem();
             
             Hashtable<String, Hashtable<String,Complex>> subNetCurrTable = multiNetHelper.getSubNetCurrInjTable();
             
             Hashtable<String,Complex>  currTable2 = subNetCurrTable.get("SubNet-2"); 
             Hashtable<String,Complex>  currTable1 = subNetCurrTable.get("SubNet-1");
             
             
    
             //Calculated current 5->7 = (-0.8474955142071343, 0.019155755845009444)
             // Boundary subsystem solution result: 
             //   --- boundary bus current injection
             //Bus5=(0.8474961882206447, -0.019156012115090406)}
             //Bus8=(0.747465838325234, -0.019732069636158034), 
             
             System.out.println(currTable1);
             assertTrue(NumericUtil.equals(currTable1.get("Bus5"),curr_5_7.multiply(-1.0),5.0E-5));
             assertTrue(NumericUtil.equals(currTable1.get("Bus8"),curr_7_8,5.0E-5));
             
             //{Bus7=(-1.5949620265458786, 0.03888808175124844)}
             System.out.println(currTable2);
             
             
             //
             // test-5 use the superposition approach to solve the system
             // 
             
             multiNetHelper.solveSubNetWithBoundaryCurrInjection();
             /*
              * Bus, Vm, Va =Bus5  ,0.995769405872312  ,-3.9527924201734583
				Bus, Vm, Va =Bus4  ,1.025968118314481  ,-2.181732949891952
				Bus, Vm, Va =Bus1  ,1.0399992062183945  ,-1.2174846925460595E-5
				Bus, Vm, Va =Bus6  ,1.0127894205516264  ,-3.65144100823566
				Bus, Vm, Va =Bus9  ,1.0323881845555523  ,2.002884954630945
				Bus, Vm, Va =Bus8  ,1.0159207319744723  ,0.7637165498424902
				Bus, Vm, Va =Bus3  ,1.0249992157810297  ,4.700828451009765
				Bus, Vm, Va =Bus7  ,1.0258075418988797  ,3.7555960457865005
				Bus, Vm, Va =Bus2  ,1.024999053994286  ,9.31568983146796
              */
             for(BaseDStabNetwork<?, ?> subNet: proc.getSubNetworkList()){
 		    	 for(BaseDStabBus<? extends DStabGen, ? extends DStabLoad> b:subNet.getBusList()){
 		    		 System.out.println("Bus, Vm, Va ="+b.getId()+"  ,"+b.getVoltageMag()+"  ,"+b.getVoltageAng(UnitType.Deg));
 		    		  if(b.getId().equals("Bus5")){
 		    			 assertTrue(NumericUtil.equals(b.getVoltageMag(),0.99577,1.0E-4));
 		    		  }
 		    		  if(b.getId().equals("Bus7")){
 		    			 assertTrue(NumericUtil.equals(b.getVoltageMag(),1.02581,1.0E-4));
 		    		  }
 		    		  if(b.getId().equals("Bus8")){
		    			 assertTrue(NumericUtil.equals(b.getVoltageMag(),1.01592,1.0E-4));
		    		  }
 		    	 }
 		    	 
 		    	 
 		    }
             
             
            
            
	}
	
	

}
