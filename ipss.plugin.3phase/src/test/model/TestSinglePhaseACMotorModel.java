package test.model;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.algo.DynamicEventProcessor3Phase;
import org.interpss.threePhase.dynamic.impl.DStabNetwork3phaseImpl;
import org.interpss.threePhase.dynamic.model.impl.SinglePhaseACMotor;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.net.NetworkType;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.dstab.devent.LoadChangeEventType;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineModelType;

public class TestSinglePhaseACMotorModel {
	
	//@Test
	public void test_dstab_1PAC() throws InterpssException{
		IpssCorePlugin.init();
		
		DStabNetwork3Phase net = create2BusSys();
	
		net.setNetworkType(NetworkType.DISTRIBUTION);
		
		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(net);
		//distPFAlgo.orderDistributionBuses(true);
		
		assertTrue(distPFAlgo.powerflow());
		
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(net));

		
	    /*
	     *   create the 1-phase AC model 
	     */
		
		DStab3PBus bus1 = (DStab3PBus) net.getBus("Bus1");
		
	    SinglePhaseACMotor ac1 = new SinglePhaseACMotor(bus1,"1");
  		ac1.setLoadPercent(25);
  		ac1.setPhase(PhaseCode.A);
  		ac1.setMvaBase(25);
  		bus1.getPhaseADynLoadList().add(ac1);
  		
  		
  		
  		SinglePhaseACMotor ac2 = new SinglePhaseACMotor(bus1,"2");
  		ac2.setLoadPercent(30);
  		ac2.setPhase(PhaseCode.B);
  		ac2.setMvaBase(30);
  		bus1.getPhaseBDynLoadList().add(ac2);
  		

  		
  		SinglePhaseACMotor ac3 = new SinglePhaseACMotor(bus1,"3");
  		ac3.setLoadPercent(20);
  		ac3.setPhase(PhaseCode.C);
  		ac3.setMvaBase(20);
  		bus1.getPhaseCDynLoadList().add(ac3);
  		
  		// run dstab to test 1-phase ac model
       	// initGenLoad-- summarize the effects of contributive Gen/Load to make equivGen/load for power flow calculation	//	net.initContributeGenLoad();
  			
  			DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
  					net, IpssCorePlugin.getMsgHub());
  				
  		
  		  	dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
  			dstabAlgo.setSimuStepSec(0.005d);
  			dstabAlgo.setTotalSimuTimeSec(.2);
  			
  			StateMonitor sm = new StateMonitor();
  			sm.addGeneratorStdMonitor(new String[]{"Bus3-mach1"});
  			sm.addBusStdMonitor(new String[]{"Bus3","Bus1"});
  			sm.add3PhaseBusStdMonitor("Bus1");
  			
  			// AC MOTOR extended Id
  			//"ACMotor_"+this.getId()+"@"+this.getParentBus().getId()+"_phase"+this.getPhase();
  			sm.addMultiDynDeviceMonitor(DynDeviceType.ACMotor, new String[]{"ACMotor_1@Bus1_phaseA","ACMotor_2@Bus1_phaseB"});
  			
  			
  			// set the output handler
  			dstabAlgo.setSimuOutputHandler(sm);
  			dstabAlgo.setOutPutPerSteps(1);
  			
  			net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", net, SimpleFaultCode.GROUND_3P,new Complex(0,0.1),null, 0.1,0.06), "SLG@Bus1");
  			dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
  			
  		  	if(dstabAlgo.initialization()){
  		  	    System.out.print(net.getYMatrixABC().getSparseEqnComplex().toString());
  		  	    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
  		  	          System.out.print("\n\n time = "+dstabAlgo.getSimuTime()+"\n");
  		  		      dstabAlgo.solveDEqnStep(true);
  		  		      sm.addBusPhaseVoltageMonitorRecord("Bus1", dstabAlgo.getSimuTime(), bus1.get3PhaseVotlages());
  		  	    }
  		  	}
  		  	
  		
  		  	//System.out.println(sm.toCSVString(sm.getBusAngleTable()));
//  		  	System.out.println(sm.toCSVString(sm.getBusVoltTable()));
  			System.out.println(sm.toCSVString(sm.getBusPhAVoltTable()));
  			System.out.println(sm.toCSVString(sm.getBusPhBVoltTable()));
  			System.out.println(sm.toCSVString(sm.getBusPhCVoltTable()));
  		    System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
  		    System.out.println(sm.toCSVString( sm.getAcMotorRemainFractionTable()));
  		    
  		  
  		  
	      assertTrue(Math.abs(sm.getBusAngleTable().get("Bus1").get(1).getValue()-
					sm.getBusAngleTable().get("Bus1").get(10).getValue())<1.0E-1);
		  assertTrue(Math.abs(sm.getBusVoltTable().get("Bus1").get(1).getValue()-
					sm.getBusVoltTable().get("Bus1").get(10).getValue())<1.0E-3);
		  assertTrue(Math.abs(sm.getAcMotorPTable().get("ACMotor_1@Bus1_phaseA").get(1).getValue()-
				  sm.getAcMotorPTable().get("ACMotor_1@Bus1_phaseA").get(10).getValue())<1.0E-3);
		  
		  assertTrue(Math.abs(sm.getBusPhAVoltTable().get("Bus1").get(1).getValue()-
					sm.getBusPhAVoltTable().get("Bus1").get(10).getValue())<1.0E-3);
	}
	
	
	@Test
	public void test_dstab_1PAC_load_change() throws InterpssException{
		IpssCorePlugin.init();
		
		DStabNetwork3Phase net = create2BusSys();
	
		net.setNetworkType(NetworkType.DISTRIBUTION);
		
		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(net);
		//distPFAlgo.orderDistributionBuses(true);
		
		assertTrue(distPFAlgo.powerflow());
		
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(net));

		
	    /*
	     *   create the 1-phase AC model 
	     */
		
		DStab3PBus bus1 = (DStab3PBus) net.getBus("Bus1");
		
	    SinglePhaseACMotor ac1 = new SinglePhaseACMotor(bus1,"1");
  		ac1.setLoadPercent(25);
  		ac1.setPhase(PhaseCode.A);
  		ac1.setMvaBase(25);
  		bus1.getPhaseADynLoadList().add(ac1);
  		
  		
  		SinglePhaseACMotor ac2 = new SinglePhaseACMotor(bus1,"2");
  		ac2.setLoadPercent(30);
  		ac2.setPhase(PhaseCode.B);
  		ac2.setMvaBase(30);
  		bus1.getPhaseBDynLoadList().add(ac2);

  		
  		SinglePhaseACMotor ac3 = new SinglePhaseACMotor(bus1,"3");
  		ac3.setLoadPercent(20);
  		ac3.setPhase(PhaseCode.C);
  		ac3.setMvaBase(20);
  		bus1.getPhaseCDynLoadList().add(ac3);
  		
  		// run dstab to test 1-phase ac model
       	// initGenLoad-- summarize the effects of contributive Gen/Load to make equivGen/load for power flow calculation	//	net.initContributeGenLoad();
  			
		DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
				net, IpssCorePlugin.getMsgHub());
			
	
	  	dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.5);
		

		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus3","Bus1"});
		sm.add3PhaseBusStdMonitor("Bus1");
		
		// AC MOTOR extended Id
		//"ACMotor_"+this.getId()+"@"+this.getParentBus().getId()+"_phase"+this.getPhase();
		sm.addMultiDynDeviceMonitor(DynDeviceType.ACMotor, new String[]{"ACMotor_1@Bus1_phaseA","ACMotor_2@Bus1_phaseB"});
		
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		// bus fault event
		//net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", net, SimpleFaultCode.GROUND_3P,new Complex(0,0.1),null, 0.1,0.06), "SLG@Bus1");
		//Load change event
		net.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus1", net,LoadChangeEventType.FIXED_TIME,-0.2, 0.2),"LoadReduce20%@Bus1");
        
		
		dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
		
	  	if(dstabAlgo.initialization()){
	  	    //System.out.print(net.getYMatrixABC().getSparseEqnComplex().toString());
	  	    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
	  	          //System.out.print("\n\n time = "+dstabAlgo.getSimuTime()+"\n");
	  		      dstabAlgo.solveDEqnStep(true);
	  		      sm.addBusPhaseVoltageMonitorRecord("Bus1", dstabAlgo.getSimuTime(), bus1.get3PhaseVotlages());
	  	    }
	  	}
	  	
	
	  	//System.out.println(sm.toCSVString(sm.getBusAngleTable()));
//  		  	System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		System.out.println("volt phase A:"+sm.toCSVString(sm.getBusPhAVoltTable()));
		System.out.println("volt phase B:"+sm.toCSVString(sm.getBusPhBVoltTable()));
		System.out.println("volt phase C:"+sm.toCSVString(sm.getBusPhCVoltTable()));
	    System.out.println("AC motor Pe:"+sm.toCSVString(sm.getAcMotorPTable()));
	    System.out.println("Motor remain fraction:"+sm.toCSVString( sm.getAcMotorRemainFractionTable()));
  		    
  		  
  		  
	      assertTrue(Math.abs(sm.getBusAngleTable().get("Bus1").get(1).getValue()-
					sm.getBusAngleTable().get("Bus1").get(10).getValue())<1.0E-1);
		  assertTrue(Math.abs(sm.getBusVoltTable().get("Bus1").get(1).getValue()-
					sm.getBusVoltTable().get("Bus1").get(10).getValue())<1.0E-3);
		  assertTrue(Math.abs(sm.getAcMotorPTable().get("ACMotor_1@Bus1_phaseA").get(1).getValue()-
				  sm.getAcMotorPTable().get("ACMotor_1@Bus1_phaseA").get(10).getValue())<1.0E-3);
		  
		  assertTrue(Math.abs(sm.getBusPhAVoltTable().get("Bus1").get(1).getValue()-
					sm.getBusPhAVoltTable().get("Bus1").get(10).getValue())<1.0E-3);
		  
		 
		  assertTrue(Math.abs(sm.getAcMotorPTable().get("ACMotor_1@Bus1_phaseA").get(0).getValue()-
				  sm.getAcMotorPTable().get("ACMotor_1@Bus1_phaseA").get(41).getValue())<1.0E-4);
		  
		  assertTrue(Math.abs(sm.getAcMotorPTable().get("ACMotor_1@Bus1_phaseA").get(0).getValue()*0.8-
				  sm.getAcMotorPTable().get("ACMotor_1@Bus1_phaseA").get(42).getValue())<1.0E-4);
		  
		  /*
AC motor Pe: time,ACMotor_2@Bus1_phaseB, ACMotor_1@Bus1_phaseA
 0.0000,    0.30000,    0.25000,
 0.0000,    0.30000,    0.25000,
 0.0050,    0.30000,    0.25000,
 0.0100,    0.30000,    0.25000,
 0.0150,    0.30000,    0.25000,
 0.0200,    0.30000,    0.25000,
 0.0250,    0.30000,    0.25000,
 0.0300,    0.30000,    0.25000,
 0.0350,    0.30000,    0.25000,
 0.0400,    0.30000,    0.25000,
 0.0450,    0.30000,    0.25000,
 0.0500,    0.30000,    0.25000,
 0.0550,    0.30000,    0.25000,
 0.0600,    0.30000,    0.25000,
 0.0650,    0.30000,    0.25000,
 0.0700,    0.30000,    0.25000,
 0.0750,    0.30000,    0.25000,
 0.0800,    0.30000,    0.25000,
 0.0850,    0.30000,    0.25000,
 0.0900,    0.30000,    0.25000,
 0.0950,    0.30000,    0.25000,
 0.1000,    0.30000,    0.25000,
 0.1050,    0.30000,    0.25000,
 0.1100,    0.30000,    0.25000,
 0.1150,    0.30000,    0.25000,
 0.1200,    0.30000,    0.25000,
 0.1250,    0.30000,    0.25000,
 0.1300,    0.30000,    0.25000,
 0.1350,    0.30000,    0.25000,
 0.1400,    0.30000,    0.25000,
 0.1450,    0.30000,    0.25000,
 0.1500,    0.30000,    0.25000,
 0.1550,    0.30000,    0.25000,
 0.1600,    0.30000,    0.25000,
 0.1650,    0.30000,    0.25000,
 0.1700,    0.30000,    0.25000,
 0.1750,    0.30000,    0.25000,
 0.1800,    0.30000,    0.25000,
 0.1850,    0.30000,    0.25000,
 0.1900,    0.30000,    0.25000,
 0.1950,    0.30000,    0.25000,
 0.2000,    0.30000,    0.25000,
 0.2000,    0.24000,    0.20000,
 0.2050,    0.24020,    0.20017,
 0.2100,    0.24018,    0.20015,
 0.2150,    0.24016,    0.20014,
 0.2200,    0.24015,    0.20012,
 0.2250,    0.24013,    0.20011,
 0.2300,    0.24012,    0.20010,
 0.2350,    0.24011,    0.20009,
 0.2400,    0.24010,    0.20008,
 0.2450,    0.24009,    0.20008,
 0.2500,    0.24008,    0.20007,
 0.2550,    0.24007,    0.20006,
 0.2600,    0.24007,    0.20006,
 0.2650,    0.24006,    0.20005,
 0.2700,    0.24006,    0.20005,
 0.2750,    0.24005,    0.20004,
 0.2800,    0.24005,    0.20004,
 0.2850,    0.24004,    0.20003,
 0.2900,    0.24004,    0.20003,
 0.2950,    0.24003,    0.20003,
 0.3000,    0.24003,    0.20003,
 0.3050,    0.24003,    0.20002,
 0.3100,    0.24002,    0.20002,
 0.3150,    0.24002,    0.20002,
 0.3200,    0.24002,    0.20002,
 0.3250,    0.24002,    0.20002,
 0.3300,    0.24002,    0.20001,
 0.3350,    0.24002,    0.20001,
 0.3400,    0.24001,    0.20001,
 0.3450,    0.24001,    0.20001,
 0.3500,    0.24001,    0.20001,
 0.3550,    0.24001,    0.20001,
 0.3600,    0.24001,    0.20001,
 0.3650,    0.24001,    0.20001,
 0.3700,    0.24001,    0.20001,
 0.3750,    0.24001,    0.20001,
 0.3800,    0.24001,    0.20001,
 0.3850,    0.24001,    0.20000,
 0.3900,    0.24001,    0.20000,
 0.3950,    0.24000,    0.20000,
 0.4000,    0.24000,    0.20000,
 0.4050,    0.24000,    0.20000,
 0.4100,    0.24000,    0.20000,
 0.4150,    0.24000,    0.20000,
 0.4200,    0.24000,    0.20000,
 0.4250,    0.24000,    0.20000,
 0.4300,    0.24000,    0.20000,
 0.4350,    0.24000,    0.20000,
 0.4400,    0.24000,    0.20000,
 0.4450,    0.24000,    0.20000,
 0.4500,    0.24000,    0.20000,
 0.4550,    0.24000,    0.20000,
 0.4600,    0.24000,    0.20000,
 0.4650,    0.24000,    0.20000,
 0.4700,    0.24000,    0.20000,
 0.4750,    0.24000,    0.20000,
 0.4800,    0.24000,    0.20000,
 0.4850,    0.24000,    0.20000,
 0.4900,    0.24000,    0.20000,
 0.4950,    0.24000,    0.20000,
		   */
	}
	
	
	//@Test
	public void test1PAC() throws InterpssException{
		
       IpssCorePlugin.init();
		
		DStabNetwork3Phase net = create2BusSys();
		net.setNetworkType(NetworkType.DISTRIBUTION);
	
		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(net);
		//distPFAlgo.orderDistributionBuses(true);
		
		assertTrue(distPFAlgo.powerflow());
		
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(net));
		
	    /*
	     *   create the 1-phase AC model 
	     */
		
		DStab3PBus bus1 = (DStab3PBus) net.getBus("Bus1");
		
	    SinglePhaseACMotor ac1 = new SinglePhaseACMotor(bus1,"1");
  		ac1.setLoadPercent(50);
  		ac1.setPhase(PhaseCode.A);
  		ac1.setMvaBase(50);
  		bus1.getPhaseADynLoadList().add(ac1);
  		
  		
  		
  		SinglePhaseACMotor ac2 = new SinglePhaseACMotor(bus1,"2");
  		ac2.setLoadPercent(50);
  		ac2.setPhase(PhaseCode.B);
  		ac2.setMvaBase(50);
  		bus1.getPhaseBDynLoadList().add(ac2);
  		

  		
  		SinglePhaseACMotor ac3 = new SinglePhaseACMotor(bus1,"3");
  		ac3.setLoadPercent(50);
  		ac3.setPhase(PhaseCode.C);
  		ac3.setMvaBase(50);
  		bus1.getPhaseCDynLoadList().add(ac3);
  		
  		// run dstab to test 1-phase ac model
       	// initGenLoad-- summarize the effects of contributive Gen/Load to make equivGen/load for power flow calculation	//	net.initContributeGenLoad();
  			
  			DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
  					net, IpssCorePlugin.getMsgHub());
  				
  		
  		  	dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
  			dstabAlgo.setSimuStepSec(0.005d);
  			dstabAlgo.setTotalSimuTimeSec(0.01);
  			
  			StateMonitor sm = new StateMonitor();
  			sm.addGeneratorStdMonitor(new String[]{"Bus3-mach1"});
  			sm.addBusStdMonitor(new String[]{"Bus3","Bus1"});
  			
  			// set the output handler
  			dstabAlgo.setSimuOutputHandler(sm);
  			dstabAlgo.setOutPutPerSteps(1);
  		  	if(dstabAlgo.initialization()){
		
		       // check init load
				Complex loadPQ = new Complex(0.5,0.5*Math.tan(Math.acos(0.97)));
				assertTrue( NumericUtil.equals(ac1.getInitLoadPQ(),loadPQ,1.0E-5));
				
				// check the calculated loadPQ
				assertTrue(NumericUtil.equals(ac1.getLoadPQ(),loadPQ,1.0E-5));
				
				//check only 50% load left as static load
				Complex remainLoadPQ = new Complex(1,0.2).subtract(loadPQ);
				assertTrue( NumericUtil.equals(bus1.get3PhaseNetLoadResults().a_0,remainLoadPQ,1.0E-5));
				
				// correct equivY = 1/(0.124+j0.114)
				Complex y = new Complex(1.5,0).divide(new Complex(ac1.getRstall(),ac1.getXstall()));
				assertTrue( NumericUtil.equals(ac1.getEquivY(),y,1.0E-5));
				
				bus1.getFreq();
				//bus1.setFreq(arg0);

				
				 double v = 0.599;
				 bus1.get3PhaseVotlages().a_0 = new Complex(v,0.0);
				 //Tstall = 0.033;
				 ac1.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER);
				 ac1.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER);
			
				 ac1.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER);
				 ac1.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER);
				 ac1.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER);
				 ac1.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER);
				 ac1.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER);
				 
				 // check the power before stalling
				 System.out.println(ac1.getLoadPQ());
				 
				 // check the current injection compensation under normal running condition
				 Complex Iinj = ac1.getLoadPQ().subtract(ac1.getEquivY().multiply(v*v).conjugate());
				 Iinj = Iinj.divide( bus1.get3PhaseVotlages().a_0).conjugate().multiply(-1.0);
				 assertTrue(ac1.getNortonCurInj().subtract(Iinj).abs()<1.0E-6);
				 
				 
				 ac1.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER);
				 // check the stall status
				 assertTrue(ac1.getStage() ==1);
				 
				 System.out.println(ac1.getLoadPQ());
				 
				 // check the current injection compesation under stalling condition
				 assertTrue(ac1.getNortonCurInj().abs()==0.0);
  		  	}
	}
	
	
  private DStabNetwork3Phase create2BusSys() throws InterpssException{
		
		DStabNetwork3Phase net = new DStabNetwork3phaseImpl();

		double baseKva = 100000.0;
		
		// set system basekva for loadflow calculation
		net.setBaseKva(baseKva);
	  
	   //Bus 1
		DStab3PBus bus1 = ThreePhaseObjectFactory.create3PDStabBus("Bus1", net);
  		// set bus name and description attributes
  		bus1.setAttributes("Bus 1", "");
  		// set bus base voltage 
  		bus1.setBaseVoltage(230000.0);
  		// set bus to be a swing bus
  		bus1.setGenCode(AclfGenCode.NON_GEN);
  		// adapt the bus object to a swing bus object
  		bus1.setLoadCode(AclfLoadCode.CONST_P);
  		
  		//bus1.setLoadPQ(new Complex(1.0,0.2));
  		DStab3PLoad load1 = new DStab3PLoadImpl();
		load1.set3PhaseLoad(new Complex3x1(new Complex(1.0,0.2),new Complex(1.0,0.2),new Complex(1.0,0.2)));
		bus1.getThreePhaseLoadList().add(load1);
  		//bus1.setScLoadShuntY2( new Complex (1.0,0.2));
  		//bus1.setScLoadShuntY0( new Complex (1.0,0.2));
  		

  	  	// Bus 3
		DStab3PBus bus3 = ThreePhaseObjectFactory.create3PDStabBus("Bus3", net);
  		// set bus name and description attributes
  		bus3.setAttributes("Bus 3", "");
  		// set bus base voltage 
  		bus3.setBaseVoltage(230000.0);
  		// set bus to be a swing bus
  		bus3.setGenCode(AclfGenCode.SWING);
  		
  		bus3.setSortNumber(1);
  		
  		DStab3PGen gen2 = ThreePhaseObjectFactory.create3PGenerator("Gen2");
  		gen2.setMvaBase(100.0);
  		gen2.setDesiredVoltMag(1.025);
  		//gen2.setGen(new Complex(0.7164,0.2710));
  		gen2.setPosGenZ(new Complex(0.002,0.02));
  		gen2.setNegGenZ(new Complex(0.002,0.02));
  		gen2.setZeroGenZ(new Complex(0.000,1.0E9));
  		
  		//add to contributed gen list
  		bus3.getContributeGenList().add(gen2);
  		
  		EConstMachine mach2 = (EConstMachine)DStabObjectFactory.
				createMachine("1", "Mach-1", MachineModelType.ECONSTANT, net, "Bus3", "Gen2");
  		
  		mach2.setRating(100, UnitType.mVA, net.getBaseKva());
		mach2.setRatedVoltage(230000.0);
		mach2.calMultiFactors();
		mach2.setH(5.0E6);
		mach2.setD(0.01);
		mach2.setRa(0.0020);
		mach2.setXd1(0.020);
  				
  		
  		DStab3PBranch bra = ThreePhaseObjectFactory.create3PBranch("Bus1", "Bus3", "0", net);
		bra.setBranchCode(AclfBranchCode.LINE);
		bra.setZ( new Complex(0.000,   0.0500));
		bra.setHShuntY(new Complex(0, 0.200/2));
		bra.setZ0( new Complex(0.0,	  0.15));
		bra.setHB0(0.200/2);
      
	
		//net.setBusNumberArranged(true);
  		return net;
		
	}

}
