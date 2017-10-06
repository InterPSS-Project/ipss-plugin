package org.ipss.multiNet.test;

import static org.ipss.threePhase.util.ThreePhaseUtilFunction.threePhaseXfrAptr;
import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.PerformanceTimer;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDStabSimuHelper;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDStabSolverImpl;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDynEventProcessor;
import org.ipss.multiNet.algo.MultiNet3PhPosSeqDynEventProcessor;
import org.ipss.multiNet.algo.SubNetworkProcessor;
import org.ipss.multiNet.algo.TposseqD3phaseMultiNetDStabSolverImpl;
import org.ipss.multiNet.algo.powerflow.TposSeqD3PhaseMultiNetPowerflowAlgorithm;
import org.ipss.threePhase.basic.Branch3Phase;
import org.ipss.threePhase.basic.Bus3Phase;
import org.ipss.threePhase.basic.Gen3Phase;
import org.ipss.threePhase.basic.Load3Phase;
import org.ipss.threePhase.basic.Transformer3Phase;
import org.ipss.threePhase.basic.impl.Load3PhaseImpl;
import org.ipss.threePhase.dynamic.DStabNetwork3Phase;
import org.ipss.threePhase.dynamic.algo.DynamicEventProcessor3Phase;
import org.ipss.threePhase.dynamic.impl.DStabNetwork3phaseImpl;
import org.ipss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.ipss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.ipss.threePhase.util.ThreePhaseAclfOutFunc;
import org.ipss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.dstab.devent.DynamicEvent;
import com.interpss.dstab.devent.DynamicEventType;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineType;
import com.interpss.dstab.mach.RoundRotorMachine;

public class TestTposD3phaseDStab {
	
	//@Test
	public void testTD_3Phase_oneNetwork() throws InterpssException{
		IpssCorePlugin.init();
		 
		DStabNetwork3Phase dsNet = create3BusSys();
		
		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(dsNet);
		
		
		assertTrue(distPFAlgo.powerflow());
		
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(dsNet));
		dsNet.setLfConverged(true);
		
		/*
		 * Bus results: 
		Bus1,0.9829882327809214,-0.15267971911076395,0.9829882327809214,4.036110485675627,0.9829882327809212,1.9417153832824312,0.97155 + j-0.1495  -0.61525 + j-0.76664  -0.35631 + j0.91614
		Bus2,0.9893831503622458,-0.10124591156889767,0.9893831503622457,4.087544293217493,0.9893831503622456,1.9931491908242975,0.98432 + j-0.1000  -0.57876 + j-0.80244  -0.40556 + j0.90244
		Bus3,1.0,0.0,1.0,4.188790204786391,1.0,2.0943951023931953,1.0000 + j0.0000  -0.5000 + j-0.86603  -0.5000 + j0.86603
		 */
		
		DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
				dsNet, IpssCorePlugin.getMsgHub());
			
	
	  	
	  	dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.5);
		
	    //dstabAlgo.setRefMachine(net.getMachine("Bus3-mach1"));
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",dsNet, SimpleFaultCode.GROUND_3P, new Complex(0,0), null, 0.2, 0.05),"fault@bus2");
        
		
		StateMonitor sm = new StateMonitor();
		//sm.addGeneratorStdMonitor(new String[]{"Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus2","Bus1"});
	
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
				
	  	if(dstabAlgo.initialization()){
//	  		System.out.println(ThreePhaseAclfOutFunc.busLfSummary(dsNet));
	  		System.out.println(dsNet.getMachineInitCondition());
	  	
	  		dstabAlgo.performSimulation();
	  	}
	  	
	  	System.out.println(sm.toCSVString(sm.getBusAngleTable()));
	  	
	  	System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	  	
	  	MonitorRecord rec1 = sm.getBusVoltTable().get("Bus2").get(1);
	  	MonitorRecord rec20 = sm.getBusVoltTable().get("Bus2").get(20);
	  	assertTrue(Math.abs(rec1.getValue()-rec20.getValue())<1.0E-4);

	}
	
	// test the basic (key) procedures first
	@Test
	public void testTransBusVoltDistTotalPower() throws InterpssException{
		IpssCorePlugin.init();
		 
		DStabNetwork3Phase dsNet = create3BusSys();
		
//		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(dsNet);
//		//distPFAlgo.orderDistributionBuses(true);
//		
//		assertTrue(distPFAlgo.powerflow());
//		
//		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(dsNet));
//		
		/*
		 * Bus results: 
		Bus1,0.9829882327809214,-0.15267971911076395,0.9829882327809214,4.036110485675627,0.9829882327809212,1.9417153832824312,0.97155 + j-0.1495  -0.61525 + j-0.76664  -0.35631 + j0.91614
		Bus2,0.9893831503622458,-0.10124591156889767,0.9893831503622457,4.087544293217493,0.9893831503622456,1.9931491908242975,0.98432 + j-0.1000  -0.57876 + j-0.80244  -0.40556 + j0.90244
		Bus3,1.0,0.0,1.0,4.188790204786391,1.0,2.0943951023931953,1.0000 + j0.0000  -0.5000 + j-0.86603  -0.5000 + j0.86603
		 */
		 
		// TODO caused a bug if the power flow is run first, as BooleanFlag is changed by the power flow solution
		 SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		 proc.addSubNetInterfaceBranch("Bus3->Bus2(0)",false);
		 proc.splitFullSystemIntoSubsystems(true);
		 
		 proc.set3PhaseSubNetByBusId("Bus1");
		 
		 System.out.println("external boundary bus: "+proc.getExternalSubNetBoundaryBusIdList());
		 
		 System.out.println("internal boundary bus: "+proc.getInternalSubNetBoundaryBusIdList());
		 
	    
	    
	    //TODO create TDMultiNetPowerflowAlgo
	    
		 TposSeqD3PhaseMultiNetPowerflowAlgorithm tdAlgo = new TposSeqD3PhaseMultiNetPowerflowAlgorithm(dsNet,proc);
		 
	    
		 assertTrue(tdAlgo.powerflow()); 
		 
		 //System.out.println(tdAlgo.getTransmissionNetwork().net2String());
		 
		 
		 System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
		 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
		 
		 //
		 //=============dynamic simulation ===============================
		 //
		  DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
		    
		  
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(0.2);
			

			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			
			//applied the event
		   dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2Dummy",proc.getSubNetworkByBusId("Bus2Dummy"),SimpleFaultCode.GROUND_3P,new Complex(0.0),null,0.01d,0.07),"3phaseFault@Bus5");
	        
			
			StateMonitor sm = new StateMonitor();
			sm.addGeneratorStdMonitor(new String[]{"Bus3-mach1"});
			sm.addBusStdMonitor(new String[]{"Bus3","Bus2Dummy","Bus2","Bus1"});
			
			sm.add3PhaseBusStdMonitor(new String[]{"Bus2","Bus1"});
//			 String idPrefix = "Bus2_feeder_1_";
//			   DStabNetwork3Phase distNet_1 = (DStabNetwork3Phase) proc.getSubNetworkByBusId(idPrefix+"BusRG60");
//			   
//			  
//			   
//			 String[] feederIdAry = new String[13];
//			 
//			  int k = 0;
//			   for(DStabBus dsBus:distNet_1.getBusList()){
//				   if(dsBus.getId().startsWith(idPrefix)){
//					   if(k<13){
//						   feederIdAry[k] = dsBus.getId();
//						   k++;
//					   }
//				   }
//			   }
//			   sm.add3PhaseBusStdMonitor(feederIdAry);
			//1Phase AC motor extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getParentBus().getId()+"_phase"+this.getPhase();
			
					
			
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(1);
			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			
			IpssLogger.getLogger().setLevel(Level.WARNING);
			
			PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
			
	 
			
			MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
			
			TposseqD3phaseMultiNetDStabSolverImpl sol = new TposseqD3phaseMultiNetDStabSolverImpl(dstabAlgo, mNetHelper);
			sol.setTheveninEquivFlag(false);
			
			dstabAlgo.setSolver( sol);
			
			dstabAlgo.setDynamicEventHandler(new MultiNet3PhPosSeqDynEventProcessor(mNetHelper));
			
			if (dstabAlgo.initialization()) {
				//System.out.println(ThreePhaseAclfOutFunc.busLfSummary(dsNet));
				
				//System.out.println(dsNet.getMachineInitCondition());
				
				//System.out.println("Running 3Phase/3sequence DStab co-simulation ...");
				timer.start();
				//dstabAlgo.performSimulation();
				
				while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
					
					for(String busId: sm.getBusPhAVoltTable().keySet()){
						
						 sm.addBusPhaseVoltageMonitorRecord( busId,dstabAlgo.getSimuTime(), ((Bus3Phase)proc.getSubNetworkByBusId(busId).getBus(busId)).get3PhaseVotlages());
					}
					
					dstabAlgo.solveDEqnStep(true);
				
					
				}
			}
			System.out.println(sm.toCSVString(sm.getBusVoltTable()));
			System.out.println(sm.toCSVString(sm.getBusAngleTable()));
			System.out.println(sm.toCSVString(sm.getMachAngleTable()));
			System.out.println(sm.toCSVString(sm.getMachSpeedTable()));
			System.out.println(sm.toCSVString(sm.getBusPhAVoltTable()));
		
		
	}
	
private DStabNetwork3Phase create3BusSys() throws InterpssException{
		
		DStabNetwork3Phase net = new DStabNetwork3phaseImpl();

		double baseKva = 100000.0;
		
		// set system basekva for loadflow calculation
		net.setBaseKva(baseKva);
	  
	    //Bus 1
  		Bus3Phase bus1 = ThreePhaseObjectFactory.create3PDStabBus("Bus1", net);
  		// set bus name and description attributes
  		bus1.setAttributes("Bus 1", "");
  		// set bus base voltage 
  		bus1.setBaseVoltage(16500.0);
  		// set bus to be a swing bus
  		bus1.setGenCode(AclfGenCode.NON_GEN);
  		bus1.setLoadCode(AclfLoadCode.CONST_P);
  		Load3Phase load1 = new Load3PhaseImpl();
		load1.set3PhaseLoad(new Complex3x1(new Complex(1,0.1),new Complex(1,0.1),new Complex(1.0,0.1)));
	
		bus1.getThreePhaseLoadList().add(load1);
  		
  		// adapt the bus object to a swing bus object
  		
  		// create contribute generator
  		// MVABase, power, sourceZ1/2/0
  		
//  		Gen3Phase gen1 = ThreePhaseObjectFactory.create3PGenerator("Gen1");
//  		gen1.setMvaBase(100.0);
//  		gen1.setDesiredVoltMag(1.04);
//  		gen1.setGen(new Complex(0.7164,0.2710));
//  		gen1.setPosGenZ(new Complex(0.003,0.2));
//  		gen1.setNegGenZ(new Complex(0.003,0.2));
//  		gen1.setZeroGenZ(new Complex(0,1.0E9));
//  		
//  		//add to contributed gen list
//  		bus1.getContributeGenList().add(gen1);
//  		bus1.setSortNumber(0);
//  		
//  		RoundRotorMachine mach = (RoundRotorMachine)DStabObjectFactory.
//				createMachine("1", "Mach-1", MachineType.EQ11_ED11_ROUND_ROTOR, net, "Bus1", "Gen1");
//  		
//  		mach.setRating(100, UnitType.mVA, net.getBaseKva());
//		mach.setRatedVoltage(16500.0);
//		mach.calMultiFactors();
//		mach.setH(5.0);
//		mach.setD(0.00);
//		mach.setRa(0.003);
//		mach.setXl(0.14);
//		mach.setXd(1.1);
//		mach.setXq(1.08);
//		mach.setXd1(0.23);
//		mach.setTd01(5.6);
//		mach.setXq1(0.23);
//		mach.setTq01(1.5);
//		mach.setXd11(0.2);
//		mach.setTq011(0.05);
//		mach.setXq11(0.2);
//		mach.setTd011(0.03);
//		mach.setSliner(2.0);  // no saturation
//		mach.setSe100(0.0);   // no saturation
//		mach.setSe120(0.0);		
  		
  		
  		
  	// Bus 2
  		Bus3Phase bus2 = ThreePhaseObjectFactory.create3PDStabBus("Bus2", net);
  		// set bus name and description attributes
  		bus2.setAttributes("Bus 2", "");
  		// set bus base voltage 
  		bus2.setBaseVoltage(230000.0);
  		// set bus to be a swing bus
  		bus2.setGenCode(AclfGenCode.NON_GEN);
  		bus2.setLoadCode(AclfLoadCode.NON_LOAD);
  		

  		
  	  	// Bus 3
  		Bus3Phase bus3 = ThreePhaseObjectFactory.create3PDStabBus("Bus3", net);
  		// set bus name and description attributes
  		bus3.setAttributes("Bus 3", "");
  		// set bus base voltage 
  		bus3.setBaseVoltage(230000.0);
  		// set bus to be a swing bus
  		bus3.setGenCode(AclfGenCode.SWING);
  		
  		Gen3Phase gen2 = ThreePhaseObjectFactory.create3PGenerator("Gen2");
  		gen2.setMvaBase(100.0);
  		gen2.setDesiredVoltMag(1.025);
  		//gen2.setGen(new Complex(0.7164,0.2710));
  		gen2.setPosGenZ(new Complex(0.02,0.2));
  		gen2.setNegGenZ(new Complex(0.02,0.2));
  		gen2.setZeroGenZ(new Complex(0,1.0E9));
  		
  		//add to contributed gen list
  		bus3.getContributeGenList().add(gen2);
  		
  		EConstMachine mach2 = (EConstMachine)DStabObjectFactory.
				createMachine("1", "Mach-1", MachineType.ECONSTANT, net, "Bus3", "Gen2");
  		
  		mach2.setRating(100, UnitType.mVA, net.getBaseKva());
		mach2.setRatedVoltage(230000.0);
		mach2.calMultiFactors();
		mach2.setH(5.0E6);
		mach2.setD(0.01);
		mach2.setRa(0.02);
		mach2.setXd1(0.20);

  
		//////////////////transformers///////////////////////////////////////////
		Branch3Phase xfr12 = ThreePhaseObjectFactory.create3PBranch("Bus2", "Bus1", "0", net);
		xfr12.setBranchCode(AclfBranchCode.XFORMER);
		xfr12.setZ( new Complex( 0.0, 0.05 ));
		xfr12.setZ0( new Complex(0.0, 0.05 ));
		Transformer3Phase xfr = threePhaseXfrAptr.apply(xfr12);
		//TODO change for testing
		xfr.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
		xfr.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
  		
		
  		Branch3Phase bra23 = ThreePhaseObjectFactory.create3PBranch("Bus3", "Bus2", "0", net);
		bra23.setBranchCode(AclfBranchCode.LINE);
		bra23.setZ( new Complex(0.000,   0.100));
		bra23.setHShuntY(new Complex(0, 0.200/2));
		bra23.setZ0( new Complex(0.0,	  0.3));
		bra23.setHB0(0.200/2);
		
		
		//net.setBusNumberArranged(true);
  		return net;
		
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

}
