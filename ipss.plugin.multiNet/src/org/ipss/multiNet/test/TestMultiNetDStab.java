package org.ipss.multiNet.test;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.algo.SubNetworkProcessor;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.util.PerformanceTimer;
import org.ipss.multiNet.algo.MultiNetDStabSimuHelper;
import org.ipss.multiNet.algo.MultiNetDStabSolverImpl;
import org.ipss.multiNet.algo.MultiNetDynamicEventProcessor;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.devent.DynamicEvent;
import com.interpss.dstab.devent.DynamicEventType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestMultiNetDStab {
	
	@Test
	public void test_IEEE9Bus_subNet_Dstab() throws InterpssException{
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
		
		
	    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
	    
		
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
	    
	    /*
	     * Step-1  split the full network into two sub-network
	     */
	    
	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
	    proc.addSubNetInterfaceBranch("Bus5->Bus7(0)");
	    proc.addSubNetInterfaceBranch("Bus7->Bus8(0)");
	    
	    proc.createSubNetworks();
	    
	    /*
	     * Step-2  use MultiNetDStabSimuHelper to preProcess the multiNetwork
	     */
	    MultiNetDStabSimuHelper multiNetHelper = new  MultiNetDStabSimuHelper(dsNet,proc);
	    multiNetHelper.processInterfaceBranchEquiv();
	    
	    // the first subnetwork 
	    DStabilityNetwork subNet = proc.getSubNetworkList().get(0);

	    
	    DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(subNet, IpssCorePlugin.getMsgHub());
	    
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(1.0d);
		dsNet.setNetEqnIterationNoEvent(1);
		dsNet.setNetEqnIterationWithEvent(1);
		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		subNet.addDynamicEvent(create3PhaseFaultEvent("Bus6", subNet,1.01d,0.05),"3phaseFault@Bus6");
		//dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,1.0d,0.05),"3phaseFault@Bus5");
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus8","Bus5","Bus1"});
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.INFO);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		
		if (dstabAlgo.initialization()) {
			
			
			System.out.println(subNet.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
			timer.start();
			dstabAlgo.performSimulation();
			
			timer.logStd("total simu time: ");
		 }
		
		 System.out.println(sm.toCSVString(sm.getMachPeTable()));
		
	     System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	}
	
	//@Test
	public void test_IEEE9Bus_MultiSubNet_Dstab() throws InterpssException{
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
		
		
	    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
	    
		
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
	    
	    /*
	     * Step-1  split the full network into two sub-network
	     */
	    
	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
	    proc.addSubNetInterfaceBranch("Bus5->Bus7(0)");
	    proc.addSubNetInterfaceBranch("Bus7->Bus8(0)");
	    
	    proc.createSubNetworks();
	    
	    /*
	     * Step-2  use MultiNetDStabSimuHelper to preProcess the multiNetwork
	     */
	    MultiNetDStabSimuHelper multiNetHelper = new  MultiNetDStabSimuHelper(dsNet,proc);
	    multiNetHelper.processInterfaceBranchEquiv();
	    
	    
	    // DStabilityNetwork subNet = (DStabilityNetwork) dsNet.getChildNetList().get(1).getNetwork();
	    //This is a must, after splitting the network, it cannot run the load flow again.
	    //subNet.setLfConverged(true);
	    
	    
	    
	    DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
	    
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.2);
		for (DStabilityNetwork subNet:proc.getSubNetworkList()){
			subNet.setNetEqnIterationNoEvent(1);
			subNet.setNetEqnIterationWithEvent(1);
		
		  // dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		}
		dsNet.addDynamicEvent(create3PhaseFaultEvent("Bus6", proc.getSubNetworkList().get(0),0.1d,0.05),"3phaseFault@Bus6");
        

		dstabAlgo.setSolver(new MultiNetDStabSolverImpl(dstabAlgo , IpssCorePlugin.getMsgHub(),multiNetHelper));
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus1","Bus2","Bus3","Bus7"});
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.INFO);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		
		// multiNetDynamic Event handler
		dstabAlgo.setDynamicEventHandler(new MultiNetDynamicEventProcessor());
		
		
		if (dstabAlgo.initialization()) {
			
			System.out.println("preFault Ymatrix: "+proc.getSubNetworkList().get(0).getYMatrix());
			System.out.println(dsNet.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
			//timer.start();
			while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
				  System.out.println("---------------------------------------- \n t="+dstabAlgo.getSimuTime());
			     dstabAlgo.solveDEqnStep(true);
			}
			//timer.logStd("total simu time: ");
		 }
		
		 System.out.println(sm.toCSVString(sm.getMachPeTable()));
		
	     System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	}
	
	private DynamicEvent create3PhaseFaultEvent(String faultBusId, DStabilityNetwork net,double startTime, double durationTime){
	    // define an event, set the event id and event type.
			DynamicEvent event1 = DStabObjectFactory.createDEvent("BusFault3P@"+faultBusId, "Bus Fault 3P@"+faultBusId, 
					DynamicEventType.BUS_FAULT, net);
			event1.setStartTimeSec(startTime);
			event1.setDurationSec(durationTime);
			
	   // define a bus fault
			DStabBus faultBus = net.getDStabBus(faultBusId);
			AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus Fault 3P@"+faultBusId, net);
			fault.setBus(faultBus);
			fault.setFaultCode(SimpleFaultCode.GROUND_3P);
			fault.setZLGFault(NumericConstant.SmallScZ);
	
	   // add this fault to the event, must be consist with event type definition before.
			event1.setBusFault(fault); 
			return event1;
	}
	
	//@Test
	public void test_IEEE9Bus_Dstab() throws InterpssException{
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
		
		
	    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
	    
		
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
	    
	

	    
	    DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
	    
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(.5d);
		dsNet.setNetEqnIterationNoEvent(1);
		dsNet.setNetEqnIterationWithEvent(1);
		dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		dsNet.addDynamicEvent(create3PhaseFaultEvent("Bus6", dsNet,0.1d,0.05),"3phaseFault@Bus6");
		//dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,1.0d,0.05),"3phaseFault@Bus5");
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus8","Bus5","Bus1"});
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.INFO);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		dstabAlgo.setDynamicEventHandler(new MultiNetDynamicEventProcessor());
		if (dstabAlgo.initialization()) {
			
			
			System.out.println(dsNet.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
			timer.start();
			dstabAlgo.performSimulation();
			
			timer.logStd("total simu time: ");
		 }
		
		 System.out.println(sm.toCSVString(sm.getMachPeTable()));
		
	     System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	}

}
