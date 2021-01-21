package org.interpss.multiNet.test.unit;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.multiNet.algo.MultiNetDStabSimuHelper;
import org.interpss.multiNet.algo.MultiNetDStabSolverImpl;
import org.interpss.multiNet.algo.MultiNetDynamicEventProcessor;
import org.interpss.multiNet.algo.SubNetworkProcessor;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.util.PerformanceTimer;
import org.junit.Test;

import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.devent.DynamicSimuEvent;
import com.interpss.dstab.devent.DynamicSimuEventType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestMultiNetDStab {
	
	//@Test
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
		
		
	    DStabilityNetwork dsNet =(DStabilityNetwork) simuCtx.getDStabilityNet();
		
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
	    
	    /*
	     * Step-1  split the full network into two sub-network
	     */
	    
	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
	    proc.addSubNetInterfaceBranch("Bus5->Bus7(0)");
	    proc.addSubNetInterfaceBranch("Bus7->Bus8(0)");
	    
	    proc.splitFullSystemIntoSubsystems(false);
	    
	    /*
	     * Step-2  use MultiNetDStabSimuHelper to preProcess the multiNetwork
	     */
	    MultiNetDStabSimuHelper multiNetHelper = new  MultiNetDStabSimuHelper(dsNet,proc);
	    multiNetHelper.processInterfaceBranchEquiv();
	    
	    // the first subnetwork 
	    BaseDStabNetwork<?, ?> subNet = proc.getSubNetworkList().get(0);
	    
	    DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(subNet, IpssCorePlugin.getMsgHub());
	    
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(1.0d);
	
		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		subNet.addDynamicEvent(create3PhaseFaultEvent("Bus6", (DStabilityNetwork) subNet,1.01d,0.05),"3phaseFault@Bus6");
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
	public void test_IEEE9Bus_MultiSubNet_Dstab_1port() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_GENCLS.dyr"
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
	    
	    /*
	     * Step-1  split the full network into two sub-network
	     */
	    
	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
	    proc.addSubNetInterfaceBranch("Bus5->Bus7(0)");
	    proc.addSubNetInterfaceBranch("Bus7->Bus8(0)");
	    
	    proc.splitFullSystemIntoSubsystems(false);
	    
	    /*
	     * Step-2  use MultiNetDStabSimuHelper to preProcess the multiNetwork
	     *  
	     */
	    // in the MultiNetDStabSimuHelper constructor, subnetwork Y matrices are built, the Thevenin equivalent Zth 
	    // matrices are prepared, the interface tie-line to boundary bus incidence matrix is formed.
	    MultiNetDStabSimuHelper multiNetHelper = new  MultiNetDStabSimuHelper(dsNet,proc);
	    
	    
	    DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
	    
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.5d);

		dsNet.addDynamicEvent(create3PhaseFaultEvent("Bus6", (DStabilityNetwork) proc.getSubNetworkList().get(0),0.1d,0.05),"3phaseFault@Bus6");
        
        // use the multi subnetwork solver
		dstabAlgo.setSolver(new MultiNetDStabSolverImpl(dstabAlgo ,multiNetHelper));
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus1","Bus2","Bus3","Bus5","Bus7","Bus8"});
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.INFO);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		
		// multiNetDynamic Event handler
		dstabAlgo.setDynamicEventHandler(new MultiNetDynamicEventProcessor(multiNetHelper));
		
		
		if (dstabAlgo.initialization()) {
			
			System.out.println("preFault Ymatrix: "+proc.getSubNetworkList().get(0).getYMatrix());
			System.out.println(proc.getSubNetworkList().get(0).getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
			//timer.start();
			while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
				//  System.out.println("t = "+dstabAlgo.getSimuTime());
			     dstabAlgo.solveDEqnStep(true);
			}
			//timer.logStd("total simu time: ");
		 }
		
		System.out.println(sm.toCSVString(sm.getMachAngleTable()));
		System.out.println(sm.toCSVString(sm.getMachPeTable()));
	    System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	}
	
	
	/**
	 * Bus 2,5,7 as a subsystem, with buses 5 and 7 as boundary buses
	 * @throws InterpssException
	 */
	@Test
	public void test_IEEE9Bus_MultiSubNet_Dstab_2port() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_GENCLS.dyr"
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
	    
	    /*
	     * Step-1  split the full network into two sub-network
	     */
	    
	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
	    proc.addSubNetInterfaceBranch("Bus4->Bus5(0)");
	    proc.addSubNetInterfaceBranch("Bus7->Bus8(0)");
	    
	    proc.splitFullSystemIntoSubsystems(false);
	    
	    /*
	     * Step-2  use MultiNetDStabSimuHelper to preProcess the multiNetwork
	     *  
	     */
	    // in the MultiNetDStabSimuHelper constructor, subnetwork Y matrices are built, the Thevenin equivalent Zth 
	    // matrices are prepared, the interface tie-line to boundary bus incidence matrix is formed.
	    MultiNetDStabSimuHelper multiNetHelper = new  MultiNetDStabSimuHelper(dsNet,proc);
	    
	    
	    DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
	    
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.5d);

		dsNet.addDynamicEvent(create3PhaseFaultEvent("Bus6", (DStabilityNetwork) proc.getSubNetworkList().get(0),0.1d,0.05),"3phaseFault@Bus6");
        
        // use the multi subnetwork solver
		dstabAlgo.setSolver(new MultiNetDStabSolverImpl(dstabAlgo ,multiNetHelper));
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus1","Bus2","Bus3","Bus5","Bus7","Bus8"});
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.INFO);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		
		// multiNetDynamic Event handler
		dstabAlgo.setDynamicEventHandler(new MultiNetDynamicEventProcessor(multiNetHelper));
		
		
		if (dstabAlgo.initialization()) {
			
			System.out.println("preFault Ymatrix: "+proc.getSubNetworkList().get(0).getYMatrix());
			System.out.println(proc.getSubNetworkList().get(0).getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
			//timer.start();
			while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
				//  System.out.println("t = "+dstabAlgo.getSimuTime());
			     dstabAlgo.solveDEqnStep(true);
			}
			//timer.logStd("total simu time: ");
		 }
		
		System.out.println(sm.toCSVString(sm.getMachAngleTable()));
		System.out.println(sm.toCSVString(sm.getMachPeTable()));
	    System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	}
	
	private DynamicSimuEvent create3PhaseFaultEvent(String faultBusId, BaseDStabNetwork<?,?> net,double startTime, double durationTime){
	    // define an event, set the event id and event type.
			DynamicSimuEvent event1 = DStabObjectFactory.createDEvent("BusFault3P@"+faultBusId, "Bus Fault 3P@"+faultBusId, 
					DynamicSimuEventType.BUS_FAULT, net);
			event1.setStartTimeSec(startTime);
			event1.setDurationSec(durationTime);
			
	   // define a bus fault

			DStabBus faultBus = (DStabBus) net.getDStabBus(faultBusId);

			AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus Fault 3P@"+faultBusId, net);
			fault.setBus(faultBus);
			fault.setFaultCode(SimpleFaultCode.GROUND_3P);
			fault.setZLGFault(NumericConstant.SmallScZ);
	
	   // add this fault to the event, must be consist with event type definition before.
			event1.setBusFault(fault); 
			return event1;
	}
	
	@Test
	public void test_IEEE9Bus_Dstab() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_GENCLS.dyr"
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
	    
	

	    
	    DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
	    
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.5d);
	
		dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		dsNet.addDynamicEvent(create3PhaseFaultEvent("Bus6", dsNet,0.1d,0.05),"3phaseFault@Bus6");
		//dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,1.0d,0.05),"3phaseFault@Bus5");
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus3-mach1","Bus2-mach1","Bus1-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus8","Bus7","Bus5","Bus3","Bus2","Bus1"});
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		if (dstabAlgo.initialization()) {
			
			
			System.out.println(dsNet.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
			timer.start();
			dstabAlgo.performSimulation();
			
			timer.logStd("total simu time: ");
		 }
		
		//System.out.println(sm.toCSVString(sm.getMachPeTable()));
		//System.out.println(sm.toCSVString(sm.getMachAngleTable()));
	    System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	}
	
	
	

}
