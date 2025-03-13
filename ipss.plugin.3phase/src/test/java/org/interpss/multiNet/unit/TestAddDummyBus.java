package org.interpss.multiNet.unit;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.multiNet.algo.MultiNetDStabSimuHelper;
import org.interpss.multiNet.algo.MultiNetDStabSolverImpl;
import org.interpss.multiNet.algo.MultiNetDynamicEventProcessor;
import org.interpss.multiNet.algo.SubNetworkProcessor;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.odm.mapper.ODMDStabParserMapper;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.Branch;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.devent.DynamicSimuEvent;
import com.interpss.dstab.devent.DynamicSimuEventType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class TestAddDummyBus {
	
	
	@Test
	public void test_addDummyBus() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
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
	    
//	    LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
//	    
//		assertTrue(aclfAlgo.loadflow());
//		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
	    
	    // add the dummy buses
	    //dsNet.bookmark(true);
	    
	    BaseDStabBus<?,?> bus7 = dsNet.getBus("Bus7");
	    
	    DStabBus bus7Dummy = (DStabBus) DStabObjectFactory.createDStabBus("Bus7Dummy", dsNet).get();
	    //basic copy
	    bus7Dummy.setBaseVoltage(bus7.getBaseVoltage());
	    bus7Dummy.setVoltage(bus7.getVoltage());
	    
	    
	    
	    for(Branch bra:dsNet.getBranchList()){
	    	if(bra.getFromBus().getId().equals("Bus7"))
	    	   bra.setFromBus(bus7Dummy);
	    	else if(bra.getToBus().getId().equals("Bus7")){
	    		bra.setToBus(bus7Dummy);
	    	}
	    }
	  
	    // add the zero-impedance line to connect the dummy bus to the original bus
	    DStabBranch dummyBranch = DStabObjectFactory.createDStabBranch("Bus7Dummy", "Bus7", dsNet);
	    dummyBranch.setZ(new Complex(0,1.0E-5));
	    
	    /*
	     * 
     BusID          Code           Volt(pu)   Angle(deg)     P(pu)     Q(pu)      Bus Name   
  -------------------------------------------------------------------------------------------
  Bus1         Swing                1.04000        0.00       0.7164    0.2710   BUS-1        
  Bus2         PV                   1.02500        9.32       1.6300    0.0659   BUS-2        
  Bus3         PV                   1.02500        4.70       0.8500   -0.1092   BUS-3        
  Bus4                              1.02597       -2.18       0.0000    0.0000   BUS-4        
  Bus5                ConstP        0.99577       -3.95      -1.2500   -0.5000   BUS-5        
  Bus6                ConstP        1.01279       -3.65      -0.9000   -0.3000   BUS-6        
  Bus7                              1.02581        3.76       0.0000    0.0000   BUS-7        
  Bus8                ConstP        1.01592        0.76      -1.0000   -0.3500   BUS-8        
  Bus9                              1.03239        2.00       0.0000    0.0000   BUS-9     
	     */
	    System.out.println("after adding the dummy buses");
	    LoadflowAlgorithm aclfAlgo2 = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo2.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		assertTrue(Math.abs(bus7.getVoltageMag()-1.02581)<1.0E-4);
		assertTrue(Math.abs(bus7Dummy.getVoltageMag()-1.02581)<1.0E-4);
		
		assertTrue(dsNet.getBusList().size()==10);
	}
	
	
	@Test
	public void test_Split_dummyBus() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
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
	    
//	    LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
//	    
//		assertTrue(aclfAlgo.loadflow());
//		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
	    
	    // add the dummy buses
	    
	    BaseDStabBus<?,?> bus7 = dsNet.getBus("Bus7");
	    
	    DStabBus bus7Dummy = (DStabBus) DStabObjectFactory.createDStabBus("Bus7Dummy", dsNet).get();
	    //basic copy
	    bus7Dummy.setBaseVoltage(bus7.getBaseVoltage());
	    bus7Dummy.setVoltage(bus7.getVoltage());
	    
	    /*
	     *   proc.addSubNetInterfaceBranch("Bus5->Bus7(0)");
	    proc.addSubNetInterfaceBranch("Bus7->Bus8(0)");
	     */
	    
	    // cannot replace those belonging to the internal system
	    
	    for(Branch bra:dsNet.getBranchList()){
	    	if(bra.getId().equals("Bus7->Bus8(0)")){
	    	   bra.setFromBus(bus7Dummy);
	    	   String newId =bra.getId().replaceAll("Bus7", "Bus7Dummy");
	    	   bra.setId(newId);
	    	}else if(bra.getId().equals("Bus5->Bus7(0)")){
	    		bra.setToBus(bus7Dummy);
	    		String newId =bra.getId().replaceAll("Bus7", "Bus7Dummy");
	    		bra.setId(newId);
	    	}
	    }
	  
	    // add the zero-impedance line to connect the dummy bus to the original bus
	    DStabBranch dummyBranch = DStabObjectFactory.createDStabBranch("Bus7Dummy", "Bus7", dsNet);
	    dummyBranch.setZ(new Complex(0,1.0E-5));
	    
	    /*
	     * 
     BusID          Code           Volt(pu)   Angle(deg)     P(pu)     Q(pu)      Bus Name   
  -------------------------------------------------------------------------------------------
  Bus1         Swing                1.04000        0.00       0.7164    0.2710   BUS-1        
  Bus2         PV                   1.02500        9.32       1.6300    0.0659   BUS-2        
  Bus3         PV                   1.02500        4.70       0.8500   -0.1092   BUS-3        
  Bus4                              1.02597       -2.18       0.0000    0.0000   BUS-4        
  Bus5                ConstP        0.99577       -3.95      -1.2500   -0.5000   BUS-5        
  Bus6                ConstP        1.01279       -3.65      -0.9000   -0.3000   BUS-6        
  Bus7                              1.02581        3.76       0.0000    0.0000   BUS-7        
  Bus8                ConstP        1.01592        0.76      -1.0000   -0.3500   BUS-8        
  Bus9                              1.03239        2.00       0.0000    0.0000   BUS-9     
	     */
	    System.out.println("after adding the dummy buses");
	    LoadflowAlgorithm aclfAlgo2 = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo2.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		assertTrue(Math.abs(bus7.getVoltageMag()-1.02581)<1.0E-4);
		assertTrue(Math.abs(bus7Dummy.getVoltageMag()-1.02581)<1.0E-4);
		
		
		
		 /*
	     * Step-1  split the full network into two sub-network
	     */
	    
	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
	    proc.addSubNetInterfaceBranch(dummyBranch.getId());

	    
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
        

		dstabAlgo.setSolver(new MultiNetDStabSolverImpl(dstabAlgo,multiNetHelper));
		
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
			
			//System.out.println("preFault Ymatrix: "+proc.getSubNetworkList().get(0).getYMatrix());
			System.out.println(proc.getSubNetworkList().get(0).getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
			//timer.start();
			while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
				  System.out.println("t = "+dstabAlgo.getSimuTime());
			     dstabAlgo.solveDEqnStep(true);
			}
			//timer.logStd("total simu time: ");
		 }
		
		//System.out.println(sm.toCSVString(sm.getMachAngleTable()));
		//System.out.println(sm.toCSVString(sm.getMachPeTable()));
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

			AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus Fault 3P@"+faultBusId, net, true /* cacheBusScVolt */);
			fault.setBus(faultBus);
			fault.setFaultCode(SimpleFaultCode.GROUND_3P);
			fault.setZLGFault(NumericConstant.SmallScZ);
	
	   // add this fault to the event, must be consist with event type definition before.
			event1.setBusFault(fault); 
			return event1;
	}
	
	

}