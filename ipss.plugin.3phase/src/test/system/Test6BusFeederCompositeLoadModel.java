package test.system;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.dstab.dynLoad.impl.InductionMotorImpl;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.impl.DStab3PGenImpl;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.algo.DynamicEventProcessor3Phase;
import org.interpss.threePhase.dynamic.model.InductionMotor3PhaseAdapter;
import org.interpss.threePhase.dynamic.model.PVDistGen3Phase;
import org.interpss.threePhase.dynamic.model.impl.SinglePhaseACMotor;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.interpss.threePhase.util.ThreePhaseAclfOutFunc;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.net.NetworkType;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;

import test.TestBase;

/**
 * This test case serves to test more detailed load modeling at the feeder level
 * @author Qiuhua
 *
 */
public class Test6BusFeederCompositeLoadModel extends TestBase {
	
	@Test
	public void testFeederCompositeLoad() throws InterpssException{
        IpssCorePlugin.init();
		
		DStabNetwork3Phase distNet = this.create6BusFeeder();
		distNet.setNetworkType(NetworkType.DISTRIBUTION);
		
		
		//Add composite load component to each load bus
		
		for(int i =2;i<=6;i++){
			DStab3PBus loadBus = (DStab3PBus) distNet.getBus("Bus"+i);
			/*
			Load3Phase load1 = new Load3PhaseImpl();
			load1.set3PhaseLoad(new Complex3x1(new Complex(0.3,0.05),new Complex(0.3,0.05),new Complex(0.3,0.05)));
			loadBus.getThreePhaseLoadList().add(load1);
			*/
			
		
			
			
			// AC motor, 50%
			
			 SinglePhaseACMotor ac1 = new SinglePhaseACMotor(loadBus,"1");
		  		ac1.setLoadPercent(50);
		  		ac1.setPhase(PhaseCode.A);
		  		ac1.setMvaBase(5);
		  		ac1.setTstall(99); // disable ac stalling
		  		loadBus.getPhaseADynLoadList().add(ac1);
		  		
		  		
		  		
		  	SinglePhaseACMotor ac2 = new SinglePhaseACMotor(loadBus,"2");
		  		ac2.setLoadPercent(50);
		  		ac2.setPhase(PhaseCode.B);
		  		ac2.setMvaBase(5);
		  		ac2.setTstall(99); // disable ac stalling
		  		loadBus.getPhaseBDynLoadList().add(ac2);
		  		

		  		
		  	SinglePhaseACMotor ac3 = new SinglePhaseACMotor(loadBus,"3");
		  		ac3.setLoadPercent(50);
		  		ac3.setPhase(PhaseCode.C);
		  		ac3.setMvaBase(5);
		  		ac3.setTstall(99); // disable ac stalling
		  		loadBus.getPhaseCDynLoadList().add(ac3);
			
			
			// 3 phase motor, 20%
		  		
		  		//InductionMotor indMotor= DStabObjectFactory.createInductionMotor("1");
				//indMotor.setDStabBus(loadBus);
				InductionMotor indMotor= new InductionMotorImpl(loadBus,"1");

				indMotor.setXm(3.0);
				indMotor.setXl(0.07);
				indMotor.setRa(0.032);
				indMotor.setXr1(0.3);
				indMotor.setRr1(0.01);
				
		
				indMotor.setMvaBase(8);
				indMotor.setH(1.0);
				
				InductionMotor3PhaseAdapter indMotor3Phase = new InductionMotor3PhaseAdapter(indMotor);
				indMotor3Phase.setLoadPercent(20); //0.06 MW
				loadBus.getThreePhaseDynLoadList().add(indMotor3Phase);	
			
			
			// PV generation
			
				DStab3PGen gen1 = new DStab3PGenImpl();
				gen1.setParentBus(loadBus);
				gen1.setId("PVGen");
				gen1.setGen(new Complex(0.05,0));  // total gen power, system mva based
				
				loadBus.getThreePhaseGenList().add(gen1);
				
				gen1.setMvaBase(6); // for dynamic simulation only
				gen1.setPosGenZ(new Complex(0,1.0E-1));   // assuming open-circuit
				gen1.setNegGenZ(new Complex(0,1.0E-1));
				gen1.setZeroGenZ(new Complex(0,1.0E-1));
				//create the PV Distributed gen model
				PVDistGen3Phase pv = new PVDistGen3Phase(gen1);
				pv.setUnderVoltTripAll(0.4);
				pv.setUnderVoltTripStart(0.8);
			
			
		}
		
		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		//distPFAlgo.orderDistributionBuses(true);
		
		assertTrue(distPFAlgo.powerflow());
		
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(distNet));
		
		DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
				distNet, IpssCorePlugin.getMsgHub());
			
	
	  	
	  	dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(1);
	    //dstabAlgo.setRefMachine(net.getMachine("Bus3-mach1"));
		distNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus6",distNet,SimpleFaultCode.GROUND_3P,new Complex(0.001,0), null, 0.2,0.05),"3phaseFault@Bus2");
        
		
		StateMonitor sm = new StateMonitor();
		//sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus6","Bus2","Bus1"});
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
				
	  	if(dstabAlgo.initialization()){
	  		System.out.println(ThreePhaseAclfOutFunc.busLfSummary(distNet));
	  		System.out.println(distNet.getMachineInitCondition());
	  	
	  		dstabAlgo.performSimulation();
	  	}
	  	System.out.println(sm.toCSVString(sm.getBusAngleTable()));
	  	System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	  	MonitorRecord rec1 = sm.getBusVoltTable().get("Bus2").get(1);
	  	MonitorRecord rec20 = sm.getBusVoltTable().get("Bus2").get(20);
	  	assertTrue(Math.abs(rec1.getValue()-rec20.getValue())<1.0E-3);
	}

}
