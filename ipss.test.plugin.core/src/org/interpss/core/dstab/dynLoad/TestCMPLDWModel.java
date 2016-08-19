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

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.spring.CoreCommonSpringFactory;

public class TestCMPLDWModel {
	
	//@Test
	public void testPTIExample() throws InterpssException{
		IpssCorePlugin.init();
		IPSSMsgHub msg = CoreCommonSpringFactory.getIpssMsgHub();
		IpssLogger.getLogger().setLevel(Level.WARNING);
		
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/threeBus_cmpldw.raw",
				"testData/adpter/psse/v30/threeBus_cmpldw.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
	    
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
	  	algo.setLfMethod(AclfMethod.PQ);
	  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  	
  		assertTrue( dsNet.isLfConverged());
  		
  		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
  		
  		
	    
	}
	
	
	@Test
	public void testCMPLDWPSLFData() throws InterpssException{
		
		IpssCorePlugin.init();
		IPSSMsgHub msg = CoreCommonSpringFactory.getIpssMsgHub();
		IpssLogger.getLogger().setLevel(Level.WARNING);
    		
          GenericODMAdapter adapter = new GenericODMAdapter(ODMFileFormatEnum.PsseV30,ODMFileFormatEnum.GePSLF);
		  
		  adapter.parseInputFile(NetType.DStabNet, new String[]{
				  "testData/adpter/psse/v30/threeBus_cmpldw.raw",
				  "testData/adpter/psse/v30/threeBus_cmpldw.dyd"
			//"testData/ge/ieee9_onlyGen_GE.dyd"
	        });
		  

		   DStabModelParser parser =(DStabModelParser) adapter.getModel();
		   
		   System.out.println(parser.toXmlDoc());
			
			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabParserMapper(msg)
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			
		    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
		    dsNet.setFrequency(60.0);
		  
	  		/*
	  		 *  load 
	  		 */
	  		
	  		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, msg);
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			assertTrue(aclfAlgo.loadflow());
			System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(1);

			dstabAlgo.setRefMachine(dsNet.getMachine("Swing-mach1"));
			//net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,0.0d,0.05),"3phaseFault@Bus5");
	        
	        
			
			StateMonitor sm = new StateMonitor();
			sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
			sm.addBusStdMonitor(new String[]{"Bus1"});
			//extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getDStabBus().getId();
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus1");
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
		  
		  
	}

}
