package org.interpss.core.dstab.dynLoad;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.ieee.odm.ODMFileFormatEnum;
import org.ieee.odm.adapter.GenericODMAdapter;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.junit.Test;

import com.interpss.CoreCommonFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestCMPLDWModel {
	
	//@Test
	public void testCMPLDWInit() throws InterpssException{
		IpssCorePlugin.init();
		IPSSMsgHub msg = CoreCommonFactory.getIpssMsgHub();
		IpssLogger.getLogger().setLevel(Level.WARNING);
		
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/threeBus_cmpldw.raw",
				"testData/adpter/psse/v30/threeBus_cmpldw.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabilityNetwork dsNet =(DStabilityNetwork) simuCtx.getDStabilityNet();
	    
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
	  	algo.setLfMethod(AclfMethod.PQ);
	  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  	
  		assertTrue( dsNet.isLfConverged());
  		
  		//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
  		
  		
	    
	}
	
	
	@Test
	public void testCMPLDWPSLFData() throws InterpssException{
		
		IpssCorePlugin.init();
		IPSSMsgHub msg = CoreCommonFactory.getIpssMsgHub();
		IpssLogger.getLogger().setLevel(Level.WARNING);
    		
          GenericODMAdapter adapter = new GenericODMAdapter(ODMFileFormatEnum.PsseV30,ODMFileFormatEnum.GePSLF);
		  
		  adapter.parseInputFile(NetType.DStabNet, new String[]{
				  "testData/adpter/psse/v30/threeBus_cmpldw.raw",
				  "testData/adpter/psse/v30/threeBus_cmpldw.dyd"
			//"testData/ge/ieee9_onlyGen_GE.dyd"
	        });
		  

		   DStabModelParser parser =(DStabModelParser) adapter.getModel();
		   
		   //System.out.println(parser.toXmlDoc());
			
			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabParserMapper(msg)
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			
		    DStabilityNetwork dsNet =(DStabilityNetwork) simuCtx.getDStabilityNet();
		    dsNet.setFrequency(60.0);
		  
	  		/*
	  		 *  load 
	  		 */
	  		
	  		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, msg);
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			assertTrue(aclfAlgo.loadflow());
			//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(1);

			//dstabAlgo.setRefMachine(dsNet.getMachine("Swing-mach1"));
			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus3",dsNet,SimpleFaultCode.GROUND_3P,0.5d,0.05),"3phaseFault@Bus5");
	        
	        
			
			StateMonitor sm = new StateMonitor();
			//sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
			sm.addBusStdMonitor(new String[]{"Bus1","Bus3","Bus3_lowBus","Bus3_loadBus"});
			//extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getDStabBus().getId();
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus3_loadBus");
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(5);
			
			IpssLogger.getLogger().setLevel(Level.FINE);
			
			
			if (dstabAlgo.initialization()) {
				System.out.println(dsNet.getMachineInitCondition());
				
				System.out.println("Running DStab simulation ...");
			    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
				     dstabAlgo.solveDEqnStep(true);
				
				}
		
			}
			//System.out.println(sm.toCSVString(sm.getMachAngleTable()));
			System.out.println(sm.toCSVString(sm.getBusVoltTable()));
			System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
			
			/*
			 * time,Bus3_loadBus, Bus3_lowBus, Bus3, Bus1
				 0.0000,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.0200,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.0450,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.0700,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.0950,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.1200,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.1450,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.1700,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.1950,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.2200,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.2450,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.2700,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.2950,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.3200,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.3450,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.3700,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.3950,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.4200,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.4450,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.4700,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.4950,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.5000,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.5200,    0.01738,    0.00998,    0.00000,    0.81895,
				 0.5450,    0.00403,    0.00231,    0.00000,    0.81895,
				 0.5500,    0.00403,    0.00231,    0.00000,    0.81895,
				 0.5700,    0.95547,    0.99303,    0.98655,    1.00754,
				 0.5950,    0.97060,    1.00337,    0.99135,    1.00843,
				 0.6200,    0.98671,    1.01359,    0.99632,    1.00930,
				 0.6450,    0.99499,    1.01892,    0.99888,    1.00975,
				 0.6700,    0.99620,    1.02029,    0.99937,    1.00988,
				 0.6950,    0.99909,    1.02156,    1.00015,    1.00997,
				 0.7200,    1.00001,    1.02234,    1.00047,    1.01004,
				 0.7450,    0.99914,    1.02199,    1.00024,    1.01002,
				 0.7700,    1.00001,    1.02228,    1.00046,    1.01004,
				 0.7950,    0.99966,    1.02227,    1.00039,    1.01004,
				 0.8200,    0.99945,    1.02210,    1.00032,    1.01002,
				 0.8450,    0.99991,    1.02229,    1.00044,    1.01004,
				 0.8700,    0.99958,    1.02220,    1.00037,    1.01003,
				 0.8950,    0.99967,    1.02219,    1.00038,    1.01003,
				 0.9200,    0.99980,    1.02227,    1.00042,    1.01004,
				 0.9450,    0.99960,    1.02219,    1.00037,    1.01003,
				 0.9700,    0.99973,    1.02222,    1.00040,    1.01003,
				 0.9950,    0.99972,    1.02224,    1.00040,    1.01004,

			 */
		  
	}

	@Test
	public void test_CMPLDW_init_methods() throws InterpssException{
		
		IpssCorePlugin.init();
		IPSSMsgHub msg = CoreCommonFactory.getIpssMsgHub();
		IpssLogger.getLogger().setLevel(Level.WARNING);
    		
          GenericODMAdapter adapter = new GenericODMAdapter(ODMFileFormatEnum.PsseV30,ODMFileFormatEnum.GePSLF);
		  
		  adapter.parseInputFile(NetType.DStabNet, new String[]{
				  "testData/adpter/psse/v30/twoBus_cmpldw.raw",
				  "testData/adpter/psse/v30/threeBus_cmpldw_vloadbusmin.dyd"
			//"testData/ge/ieee9_onlyGen_GE.dyd"
	        });
		  

		   DStabModelParser parser =(DStabModelParser) adapter.getModel();
		   
		   //System.out.println(parser.toXmlDoc());
			
			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabParserMapper(msg)
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			
		    DStabilityNetwork dsNet =(DStabilityNetwork) simuCtx.getDStabilityNet();
		    dsNet.setFrequency(60.0);
		    
		    //dsNet.getBus("Bus1").setVoltageMag(0.99);
		  
	  		/*
	  		 *  load 
	  		 */
	  		
	  		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, msg);
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			assertTrue(aclfAlgo.loadflow());
			//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(20);

			//dstabAlgo.setRefMachine(dsNet.getMachine("Swing-mach1"));
			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus3",dsNet,SimpleFaultCode.GROUND_3P,0.5d,0.05),"3phaseFault@Bus5");
	        
	        
			
			StateMonitor sm = new StateMonitor();
			//sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
			sm.addBusStdMonitor(new String[]{"Bus1","Bus3","Bus3_lowBus","Bus3_loadBus"});
			//extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getDStabBus().getId();
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus3_loadBus");
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(5);
			
			IpssLogger.getLogger().setLevel(Level.FINE);
			
			
			if (dstabAlgo.initialization()) {
				System.out.println(dsNet.getMachineInitCondition());
				
				System.out.println("Running DStab simulation ...");
			    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
				     dstabAlgo.solveDEqnStep(true);
				
				}
		
			}
			//System.out.println(sm.toCSVString(sm.getMachAngleTable()));
			System.out.println(sm.toCSVString(sm.getBusVoltTable()));
			//System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
		  
	}
	
}
