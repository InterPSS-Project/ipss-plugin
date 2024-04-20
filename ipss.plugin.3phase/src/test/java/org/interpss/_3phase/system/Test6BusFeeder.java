package org.interpss._3phase.system;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.algo.DynamicEventProcessor3Phase;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.interpss.threePhase.util.ThreePhaseAclfOutFunc;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.core.acsc.adpter.AcscXformerAdapter;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.net.NetworkType;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineModelType;

public class Test6BusFeeder {
	
	@Test
	public void testFeederPowerflow() throws InterpssException{
	       IpssCorePlugin.init();
		
			DStabNetwork3Phase distNet = createFeeder();
			
			DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
			//distPFAlgo.orderDistributionBuses(true);
			
			assertTrue(distPFAlgo.powerflow());
			
			System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(distNet));
			
			System.out.println("power@source = "+distNet.getBus("Bus1").getContributeGen("Source").getGen().toString());
	}
	
	@Test
	public void test6BusFeederDstabSim() throws InterpssException{
		
        IpssCorePlugin.init();
		
		DStabNetwork3Phase distNet = this.createFeeder();
		
		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		//distPFAlgo.orderDistributionBuses(true);
		
		assertTrue(distPFAlgo.powerflow());
		
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(distNet));
		
		DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
				distNet, IpssCorePlugin.getMsgHub());
			
	
	  	
	  	dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.5);
	    //dstabAlgo.setRefMachine(net.getMachine("Bus3-mach1"));
		distNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", distNet, SimpleFaultCode.GROUND_LG,new Complex(0,0.0),new Complex(0,0.0), 0.1,0.07), "SLG@Bus1");
        
		
		StateMonitor sm = new StateMonitor();
		//sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus2","Bus1"});
		sm.add3PhaseBusStdMonitor(new String[]{"Bus2","Bus1"});
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
				
	  	if(dstabAlgo.initialization()){
	  		System.out.println(ThreePhaseAclfOutFunc.busLfSummary(distNet));
	  		System.out.println(distNet.getMachineInitCondition());
	  	
	  		//dstabAlgo.performSimulation();
	  		while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
				
				dstabAlgo.solveDEqnStep(true);
				
				for(String busId: sm.getBusPhAVoltTable().keySet()){
					
					 sm.addBusPhaseVoltageMonitorRecord( busId,dstabAlgo.getSimuTime(), ((DStab3PBus)distNet.getBus(busId)).get3PhaseVotlages());
				}
				
			}
	  	}
	  	//System.out.println(sm.toCSVString(sm.getBusAngleTable()));
	  	//System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	  	MonitorRecord rec1 = sm.getBusVoltTable().get("Bus2").get(1);
	  	MonitorRecord rec20 = sm.getBusVoltTable().get("Bus2").get(20);
	  	assertTrue(Math.abs(rec1.getValue()-rec20.getValue())<1.0E-4);
	  	
	  	
	  	System.out.println(sm.toCSVString(sm.getBusPhAVoltTable()));
	  	System.out.println(sm.toCSVString(sm.getBusPhBVoltTable()));
	  	System.out.println(sm.toCSVString(sm.getBusPhCVoltTable()));
	}
	
	
	public DStabNetwork3Phase createFeeder() throws InterpssException{
		
	    DStabNetwork3Phase net = ThreePhaseObjectFactory.create3PhaseDStabNetwork();
		
		
		// identify this is a distribution network
		net.setNetworkType(NetworkType.DISTRIBUTION);
		
		DStab3PBus bus1 = ThreePhaseObjectFactory.create3PDStabBus("Bus1", net);
		bus1.setAttributes("69 kV feeder source", "");
		bus1.setBaseVoltage(69000.0);
		// set the bus to a non-generator bus
		bus1.setGenCode(AclfGenCode.SWING);
		// set the bus to a constant power load bus
		bus1.setLoadCode(AclfLoadCode.NON_LOAD);
		bus1.setVoltage(new Complex(1.01,0));
		
		DStab3PGen constantGen = ThreePhaseObjectFactory.create3PGenerator("Source");
		constantGen.setMvaBase(100);
		constantGen.setPosGenZ(new Complex(0.0,0.05));
		constantGen.setNegGenZ(new Complex(0.0,0.05));
		constantGen.setZeroGenZ(new Complex(0.0,0.05));
		bus1.getContributeGenList().add(constantGen);
		
		
		EConstMachine mach = (EConstMachine)DStabObjectFactory.
				createMachine("MachId", "MachName", MachineModelType.ECONSTANT, net, "Bus1", "Source");
	
		mach.setRating(100, UnitType.mVA, net.getBaseKva());
		mach.setRatedVoltage(69000.0);
		mach.setH(50000.0);
		mach.setXd1(0.05);

		
		DStab3PBus bus2 = ThreePhaseObjectFactory.create3PDStabBus("Bus2", net);
		bus2.setAttributes("feeder bus 2", "");
		bus2.setBaseVoltage(12500.0);
		// set the bus to a non-generator bus
		bus2.setGenCode(AclfGenCode.NON_GEN);
		// set the bus to a constant power load bus
		bus2.setLoadCode(AclfLoadCode.CONST_P);
		
		
	DStab3PBranch xfr1_2 = ThreePhaseObjectFactory.create3PBranch("Bus1", "Bus2", "0", net);
		xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
		xfr1_2.setToTurnRatio(1.02);
		xfr1_2.setZ( new Complex( 0.0, 0.04 ));
		//xfr1_2.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex( 0.0, 0.04 )));
		//xfr1_2.setZ0( new Complex(0.0, 0.4 ));
	
	
	AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr1_2);
		xfr0.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
		xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);

		
		
		DStab3PBus bus3 = ThreePhaseObjectFactory.create3PDStabBus("Bus3", net);
		bus3.setAttributes("feeder bus 3", "");
		bus3.setBaseVoltage(12500.0);
		// set the bus to a non-generator bus
		bus3.setGenCode(AclfGenCode.GEN_PQ);
		// set the bus to a constant power load bus
		bus3.setLoadCode(AclfLoadCode.CONST_P);
		
		
//		Gen3Phase gen1 = new Gen3PhaseImpl();
//		gen1.setParentBus(bus3);
//		gen1.setId("PVGen");
//		gen1.setGen(new Complex(0.5,0));  // total gen power, system mva based
//		
//		bus3.getThreePhaseGenList().add(gen1);
		
		
		DStab3PBus bus4 = ThreePhaseObjectFactory.create3PDStabBus("Bus4", net);
		bus4.setAttributes("feeder bus 4", "");
		bus4.setBaseVoltage(12500.0);
		// set the bus to a non-generator bus
		bus4.setGenCode(AclfGenCode.GEN_PQ);
		// set the bus to a constant power load bus
		bus4.setLoadCode(AclfLoadCode.CONST_P);
		
		
		DStab3PBus bus5 = ThreePhaseObjectFactory.create3PDStabBus("Bus5", net);
		bus5.setAttributes("feeder bus 5", "");
		bus5.setBaseVoltage(12500.0);
		// set the bus to a non-generator bus
		bus5.setGenCode(AclfGenCode.GEN_PQ);
		// set the bus to a constant power load bus
		bus5.setLoadCode(AclfLoadCode.CONST_P);
		
		
		DStab3PBus bus6 = ThreePhaseObjectFactory.create3PDStabBus("Bus6", net);
		bus6.setAttributes("feeder bus 6", "");
		bus6.setBaseVoltage(12500.0);
		// set the bus to a non-generator bus
		bus6.setGenCode(AclfGenCode.GEN_PQ);
		// set the bus to a constant power load bus
		bus6.setLoadCode(AclfLoadCode.CONST_P);
		
		
		for(int i =2;i<=6;i++){
			DStab3PBus loadBus = (DStab3PBus) net.getBus("Bus"+i);
			DStab3PLoad load1 = new DStab3PLoadImpl();
			load1.set3PhaseLoad(new Complex3x1(new Complex(0.01,0.001),new Complex(0.01,0.001),new Complex(0.01,0.001)));
			loadBus.getThreePhaseLoadList().add(load1);
			
		}
		
		for(int i =2;i<6;i++){
			DStab3PBranch Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus"+i, "Bus"+(i+1), "0", net);
			Line2_3.setBranchCode(AclfBranchCode.LINE);
			Complex3x3 zabcActual = this.getFeederZabc601().multiply(5.28);
			Double zbase = net.getBus("Bus"+i).getBaseVoltage()*net.getBus("Bus"+i).getBaseVoltage()/net.getBaseMva()/1.0E6;
			Line2_3.setZabc(zabcActual.multiply(1/zbase));
			
		}
		
		
		
		return net; 
		
		
		
	}
	
	//TODO  1 Mile = 5280 feets
	//ohms per 1000ft
	/*
	 * New linecode.601 nphases=3 BaseFreq=60
		~ rmatrix = [0.065625    | 0.029545455  0.063920455  | 0.029924242  0.02907197  0.064659091]
		~ xmatrix = [0.192784091 | 0.095018939  0.19844697   | 0.080227273  0.072897727  0.195984848]
		~ cmatrix = [3.164838036 | -1.002632425  2.993981593 | -0.632736516  -0.372608713  2.832670203]
		
		unit: per 1000 ft
	 */
	private Complex3x3 getFeederZabc601(){
		  Complex3x3 zabc= new Complex3x3();
		  zabc.aa = new Complex(0.065625,0.192784091);
		  zabc.ab = new Complex( 0.029545455,0.095018939);
		  zabc.ac = new Complex(0.029924242,0.080227273);
		  zabc.ba =  zabc.ab;
		  zabc.bb =  new Complex(0.063920455, 0.19844697);
		  zabc.bc =  new Complex(0.02907197,0.072897727);
		  zabc.ca =  zabc.ac;
		  zabc.cb =  zabc.bc;
		  zabc.cc =  new Complex(0.064659091,0.195984848);
		  
		  return zabc;
		  
	}
	
    private Complex3x3 getFeederYabc601(){
		  return new Complex3x3();
	}

}
