package org.interpss.multiNet.unit;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.multiNet.algo.MultiNet3Ph3SeqDStabSimuHelper;
import org.interpss.multiNet.algo.MultiNet3Ph3SeqDStabSolverImpl;
import org.interpss.multiNet.algo.MultiNet3Ph3SeqDynEventProcessor;
import org.interpss.multiNet.algo.MultiNetDStabSimuHelper;
import org.interpss.multiNet.algo.MultiNetDStabSolverImpl;
import org.interpss.multiNet.algo.MultiNetDynamicEventProcessor;
import org.interpss.multiNet.algo.SubNetworkProcessor;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.odm.mapper.ODMDStabParserMapper;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class TestIEEE39_MultiNet3ph3seqDstab {
	
	/**
	 * test 3ph/3-seq co-simulation
	 * 
	 * NOTE: 04/27/2016 This is an unidentified error in this test case
	 * 
	 */
	@Test
	public void test_3phase3SeqMultiSubNetTS_IEEE39Bus() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE39Bus/IEEE39bus_v30.raw",
				"testData/IEEE39Bus/IEEE39bus_v30.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE39Bus/IEEE39bus_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));

		
		 SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		 
		 
		 /*
		  * Divided into 3 areas. 
		  * 15-16
		  * 16-17
		  * 3-4
		  * 9-39
		  */
		 /*
		    proc.addSubNetInterfaceBranch("Bus3->Bus4(1)");
		    proc.addSubNetInterfaceBranch("Bus9->Bus39(1)");
		    proc.addSubNetInterfaceBranch("Bus15->Bus16(1)");
		    proc.addSubNetInterfaceBranch("Bus16->Bus17(1)");
		  */
		 
		 /*
		  * Divided into 2 areas. 
		  */
		    proc.addSubNetInterfaceBranch("Bus15->Bus16(1)",false);
		    proc.addSubNetInterfaceBranch("Bus16->Bus17(1)",true);
		 
		    proc.splitFullSystemIntoSubsystems(true);
		    
		    //TODO now one needs to set the three-phase modeling subnetwork by one of the bus the subnetwork contains
		  proc.set3PhaseSubNetByBusId("Bus17");
		    
		  MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
		  
		  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
		  DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
		    
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(1d);
			
			StateMonitor sm = new StateMonitor();
			sm.addBusStdMonitor(new String[]{"Bus17","Bus18","Bus15","Bus16","Bus28"});
			sm.addGeneratorStdMonitor(new String[]{"Bus30-mach1","Bus31-mach1","Bus34-mach1","Bus39-mach1"});
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(1);
			
			//IpssLogger.getLogger().setLevel(Level.INFO);
			
			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus17",proc.getSubNetworkByBusId("Bus17"),SimpleFaultCode.GROUND_LG,new Complex(0,0),null,0.5d,0.05),"3phaseFault@Bus5");
			
	        // TODO a special 3-phase 3seq dstab algorithm object, with the following two setting as default
			dstabAlgo.setSolver( new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
			dstabAlgo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(mNetHelper));
		  
			 if(dstabAlgo.initialization()){
				 dstabAlgo.performSimulation();
			 }
		   
			 System.out.println(sm.toCSVString(sm.getMachPeTable()));
				
		     System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		     
		     assertTrue(Math.abs(sm.getMachPeTable().get("Bus30-mach1").get(1).getValue()-
						sm.getMachPeTable().get("Bus30-mach1").get(10).getValue())<1.0E-3);
		     assertTrue(Math.abs(sm.getBusAngleTable().get("Bus15").get(1).getValue()-
						sm.getBusAngleTable().get("Bus15").get(10).getValue())<1.0E-1);
			assertTrue(Math.abs(sm.getBusVoltTable().get("Bus15").get(1).getValue()-
						sm.getBusVoltTable().get("Bus15").get(10).getValue())<1.0E-4);
		  
		
	}
	
	
	/**
	 * test 3ph/3-seq co-simulation
	 * 
	 */
	//@Test
	public void test_IEEE39Bus_pos_SeqMultiSubNetTS() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE39Bus/IEEE39bus_v30.raw",
				"testData/IEEE39Bus/IEEE39bus_v30.seq",
				"testData/IEEE39Bus/IEEE39bus_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}

	    DStabilityNetwork dsNet = (DStabilityNetwork) simuCtx.getDStabilityNet();
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));

		
		 SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		 /*
		  * Divided into 3 areas. 
		  * 15-16
		  * 16-17
		  * 3-4
		  * 9-39
		  */
		    proc.addSubNetInterfaceBranch("Bus3->Bus4(1)");
		    proc.addSubNetInterfaceBranch("Bus9->Bus39(1)");
		    proc.addSubNetInterfaceBranch("Bus15->Bus16(1)");
		    proc.addSubNetInterfaceBranch("Bus16->Bus17(1)");
		    
		    proc.splitFullSystemIntoSubsystems(false);
		    
		    //TODO now one needs to set the three-phase modeling subnetwork by one of the bus the subnetwork contains
		   // proc.set3PhaseSubNetByBusId("Bus5");
		    
		  MultiNetDStabSimuHelper  mNetHelper = new MultiNetDStabSimuHelper(dsNet,proc);
		  
		  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
		  DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
		    
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(1d);
			
			StateMonitor sm = new StateMonitor();
			sm.addBusStdMonitor(new String[]{"Bus17","Bus18","Bus15","Bus16","Bus28"});
			sm.addGeneratorStdMonitor(new String[]{"Bus30-mach1","Bus31-mach1","Bus34-mach1","Bus39-mach1"});
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(5);
			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus39-mach1"));
			
			//IpssLogger.getLogger().setLevel(Level.INFO);
			
			//dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus17",proc.getSubNetworkByBusId("Bus17"),SimpleFaultCode.GROUND_3P,new Complex(0,0),null,1.0d,0.05),"3phaseFault@Bus17");
			
	       
			dstabAlgo.setSolver(new MultiNetDStabSolverImpl(dstabAlgo, mNetHelper));
			
			dstabAlgo.setDynamicEventHandler(new MultiNetDynamicEventProcessor(mNetHelper));
			
			PerformanceTimer timer = new PerformanceTimer();
		    timer.start();
			 if(dstabAlgo.initialization()){
				 dstabAlgo.performSimulation();
			 }
		     
			 timer.end();
			 System.out.println("used time ="+ timer.getDuration());
			 System.out.println(sm.toCSVString(sm.getMachPeTable()));
			 System.out.println(sm.toCSVString(sm.getBusAngleTable()));	
		     System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		     
		     assertTrue(Math.abs(sm.getMachPeTable().get("Bus30-mach1").get(1).getValue()-
						sm.getMachPeTable().get("Bus30-mach1").get(10).getValue())<1.0E-3);
		     assertTrue(Math.abs(sm.getBusAngleTable().get("Bus15").get(1).getValue()-
						sm.getBusAngleTable().get("Bus15").get(10).getValue())<1.0E-1);
			assertTrue(Math.abs(sm.getBusVoltTable().get("Bus15").get(1).getValue()-
						sm.getBusVoltTable().get("Bus15").get(10).getValue())<1.0E-3);
//		     FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//comprehensive_ch7/ieee39_mnet_pos_3p@bus17_genAngle.csv",sm.toCSVString(sm.getMachAngleTable()));
//		     FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//comprehensive_ch7/ieee39_mnet_pos_3p@bus17_busVolt.csv",sm.toCSVString(sm.getBusVoltTable()));
		
		
	}

}
