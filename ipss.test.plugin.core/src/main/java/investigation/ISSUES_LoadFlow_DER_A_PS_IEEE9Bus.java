package investigation;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.dstab.dynLoad.impl.DER_A_PosSeqImpl;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.datatype.LimitType;
import org.junit.Test;

import com.interpss.common.CoreCommonFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabLoad;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;



public class ISSUES_LoadFlow_DER_A_PS_IEEE9Bus {
	IPSSMsgHub msg = CoreCommonFactory.getIpssMsgHub();
	@Test
	public void test_IEEE9Bus_Dstab() throws InterpssException{
		IpssCorePlugin.init();
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{

				"testData/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen.dyr"


				
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
		BaseDStabNetwork<?,?> dsNet =simuCtx.getDStabilityNet();
	    //System.out.println(dsNet.net2String());
	    DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
	    
	    
	    //--------------------------------------------------------------------------------------------------------------
	    /*
	     * 
	     * The below commented out section is how the DER_A model is created.
	     * The load on bus 5 is increased by 0.5 pu, to match the generation from DER_A 
	     * With the DER_A model commented out, the load flow is as follows:
	     * 
	     * 
     BusID          Code           Volt(pu)   Angle(deg)      Pg(pu)    Qg(pu)    Pl(pu)    Ql(pu)    Bus Name   
  ----------------------------------------------------------------------------------------------------------------
  Bus1         Swing                1.04000        0.00       1.2234    0.3420    0.0000    0.0000   BUS-1        
  Bus2         PV                   1.02500        6.23       1.6300    0.0983    0.0000    0.0000   BUS-2        
  Bus3         PV                   1.02500        2.20       0.8500   -0.0955    0.0000    0.0000   BUS-3        
  Bus4                              1.02353       -3.74       0.0000    0.0000    0.0000    0.0000   BUS-4        
  Bus5                ConstP        0.98772       -7.65       0.0000    0.0000    1.7500    0.5000   BUS-5        
  Bus6                ConstP        1.01127       -5.54       0.0000    0.0000    0.9000    0.3000   BUS-6        
  Bus7                              1.02384        0.66       0.0000    0.0000    0.0000    0.0000   BUS-7        
  Bus8                ConstP        1.01447       -2.09       0.0000    0.0000    1.0000    0.3500   BUS-8        
  Bus9                              1.03160       -0.50       0.0000    0.0000    0.0000    0.0000   BUS-9 
	     * 
	     * 
	     * With the DER_A model, the load flow is as follows:
	     * 
	     *      BusID          Code           Volt(pu)   Angle(deg)      Pg(pu)    Qg(pu)    Pl(pu)    Ql(pu)    Bus Name   
  ----------------------------------------------------------------------------------------------------------------
  Bus1         Swing                1.04000        0.00       1.2234    0.3420    0.0000    0.0000   BUS-1        
  Bus2         PV                   1.02500        6.23       1.6300    0.0983    0.0000    0.0000   BUS-2        
  Bus3         PV                   1.02500        2.20       0.8500   -0.0955    0.0000    0.0000   BUS-3        
  Bus4                              1.02353       -3.74       0.0000    0.0000    0.0000    0.0000   BUS-4        
  Bus5                ConstP        0.98772       -7.65       0.0000    0.0000    1.7500    0.5000   BUS-5        
  Bus6                ConstP        1.01127       -5.54       0.0000    0.0000    0.9000    0.3000   BUS-6        
  Bus7                              1.02384        0.66       0.0000    0.0000    0.0000    0.0000   BUS-7        
  Bus8                ConstP        1.01447       -2.09       0.0000    0.0000    1.0000    0.3500   BUS-8        
  Bus9                              1.03160       -0.50       0.0000    0.0000    0.0000    0.0000   BUS-9 
	     * 
	     * 
	     * 
	     * A seen above, the load flows are the same, with or without the DER_A model.
	     */
	    //--------------------------------------------------------------------------------------------------------------
	    
	    
	    BaseDStabBus<DStabGen, DStabLoad> parentBus_DER_A = dsNet.getDStabBus("Bus3"); // want to put DER at bus 8

	    
	      //parentBus_DER_A.setGenCode(AclfGenCode.GEN_PQ);
		
		  DStabGen gen1 = new DStabObjectFactory().createDStabGen("DER_A"); // create
		  gen1.setParentBus(parentBus_DER_A);
		  gen1.setId("DER_A_"+parentBus_DER_A.getId());
		  
		  gen1.setGen(new Complex(0.5, 0)); //Note 0.5 pu is too much without changing
		  gen1.setMvaBase(100); // need to set sourceZ 
		  gen1.setSourceZ(new Complex(0,0.25)); 
		  gen1.setPosGenZ(new Complex(0,0.25)); 
		  gen1.setNegGenZ(new Complex(0,0.25)); 
		  gen1.setZeroGenZ(new Complex(0,0.75));
		  /* Notes by Qiuhua on Feb 23, 2025, 
		   * Attributes including desiredVoltMag, qLimit, pLimit and vLimit are missing above
		   * as you there is already a generator attached to bus 3  with the limits defined,
		   * newly added generators should be consistent with existing one.
		   * 
		   */
		  gen1.setDesiredVoltMag(1.025);
		  gen1.setPGenLimit(new LimitType(0.7,0));
		  gen1.setQGenLimit(new LimitType(99,-99));
		  
		  parentBus_DER_A.getContributeGenList().add(gen1); 
		  
		
		 DER_A_PosSeqImpl test_DER_A = new
				 DER_A_PosSeqImpl(gen1, new Complex(0.5,0), 100.0,
						 parentBus_DER_A, 0.25); // create DER_A instance
		  
		 
		 //parentBus_DER_A.getDynamicBusDeviceList().add(test_DER_A); // only way I could find to add in the gen device
		 
		 System.out.println(parentBus_DER_A.getContributeGenList()); // verify the DER_A is in the bus gen list
		 test_DER_A.disableVoltVarControl(); 
		 test_DER_A.disablePowerFreqControl();
		 //test_DER_A.initStates(parentBus_DER_A);
		 test_DER_A.setId("DER_A_"+parentBus_DER_A.getId());
		 test_DER_A.setDebugMode(true); // can turn false if you don't want as much text outputted to terminal
		 test_DER_A.setGenToDebug(parentBus_DER_A.getId()); 
		 test_DER_A.setPQFlag(0); // 0 for Q priority, 1 for P priority
		 
		 
//		 
		 dsNet.getDStabBus("Bus5").setLoadP(1.75);
		 dsNet.getDStabBus("Bus5").getContributeLoadList().get(0).setLoadCP(new Complex(1.75,0.5));
		 
		 
	    
	    


	    
	    LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
	    //aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		try {
			assertTrue(aclfAlgo.loadflow());
		} catch (InterpssException e) {
			e.printStackTrace();
		}
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005);
		dstabAlgo.setTotalSimuTimeSec(0.02);
		dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
	    
		String[] busIdNames = new String[dsNet.getNoBus()];
		for(int numBus = 0; numBus < dsNet.getNoBus(); numBus++) {
			busIdNames[numBus] =  dsNet.getBusList().get(numBus).getId();
		}

		
		//for(int num_faultBus = 0; num_faultBus < 1; num_faultBus++) {
		String fault_bus = "Bus4";
		dsNet.initBusVoltage();
		aclfAlgo.setLfMethod(AclfMethodType.NR);
		aclfAlgo.setNonDivergent(true);
		try {
			assertTrue(aclfAlgo.loadflow());
		} catch (InterpssException e) {
			e.printStackTrace();
		}
		
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
//		//String fault_bus = busIdNames[num_faultBus];
//		
//		System.out.print("--->Processing Bus: "+fault_bus + "\n");
//		
//		//dsNet.removeAllDEvent();
//		
//		//create a three phase fault @Bus4, start at 1.0s, last for 0.08s
//		
//		//dsNet.addDynamicEvent(create3PhaseFaultEvent(fault_bus,dsNet,0.2,0.08),"3phaseFault@"+fault_bus);
//		
//		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(fault_bus, dsNet,
//				SimpleFaultCode.GROUND_3P, new Complex(0.0), new Complex(0.0), 100.5, 0.07),
//		"3phaseFault@"+fault_bus);
//		
//		StateMonitor sm = new StateMonitor();
//		
//		sm.addBusStdMonitor(busIdNames);
//		//sm.addGeneratorStdMonitor(new String[]{"Bus30-mach1", "Bus31-mach1"});
//		
//		// set the output handler of DStabAlgorithm
//		dstabAlgo.setSimuOutputHandler(sm);
//
//		// set output frequency, measured by steps
//		dstabAlgo.setOutPutPerSteps(1);
//
//		IpssLogger.getLogger().setLevel(Level.WARNING);
//		
//		if (dstabAlgo.initialization()) {
//
//			
//			System.out.println("Running DStab simulation ...");
//			dstabAlgo.performSimulation();
//			
//		}
//		
//		
//		
//		System.out.println(sm.toCSVString(sm.getBusVoltTable()));
//		System.out.println("Simulation completed");
//		
//		FileUtil.writeText2File("/Users/ocornmesser/Desktop/Research/Graduate/Thesis/results_raw/DER_A_test_busVolt9bus_Fault"+fault_bus+".csv",
//				 sm.toCSVString(sm.getBusVoltTable()));
//		
		
	}

	
}