package org.interpss.threePhase.test.system;

import static org.junit.Assert.assertTrue;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.dstab.dynLoad.impl.InductionMotorImpl;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.model.InductionMotor3PhaseAdapter;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.interpss.threePhase.test.TestBase;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

import com.interpss.DStabObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.net.NetworkType;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

public class Test3PhaseInductionMotor extends TestBase{

	@Test
	public void testIndMotor() throws InterpssException{
		
	       IpssCorePlugin.init();
			
			DStabNetwork3Phase net = create2BusSys();
		    net.setNetworkType(NetworkType.DISTRIBUTION);
		    
//			// initGenLoad-- summarize the effects of contributive Gen/Load to make equivGen/load for power flow calculation	
//			net.initContributeGenLoad();
//				
//			//create a load flow algorithm object
//		  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
//		  	//run load flow using default setting
//		  	
//			// run power flow
//		  	assertTrue(algo.loadflow())	;
//	 
//		  	
//			System.out.println(AclfOutFunc.loadFlowSummary(net));
//			
//			net.initThreePhaseFromLfResult();
			
			DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(net);
			//distPFAlgo.orderDistributionBuses(true);
			
			assertTrue(distPFAlgo.powerflow());
		
			for(BaseAclfBus<?,?> bus:net.getBusList()){
				DStab3PBus bus3P = (DStab3PBus) bus;
				System.out.println("Vabc of bus -"+bus3P.getId()+","+bus3P.get3PhaseVotlages().toString());
			}
			
			System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(net));
			
		    /*
		     *   create the 3phase induction motor model 
		     */
			
			DStab3PBus bus1 = (DStab3PBus) net.getBus("Bus1");
			InductionMotor indMotor= new InductionMotorImpl(bus1, "1");
			indMotor.setDStabBus(bus1);

			indMotor.setXm(3.0);
			indMotor.setXl(0.07);
			indMotor.setRa(0.032);
			indMotor.setXr1(0.3);
			indMotor.setRr1(0.01);
			
	
			indMotor.setMvaBase(50);
			indMotor.setH(1.0);
			
			InductionMotor3PhaseAdapter indMotor3Phase = new InductionMotor3PhaseAdapter(indMotor);
			indMotor3Phase.setLoadPercent(50);
			bus1.getThreePhaseDynLoadList().add(indMotor3Phase);
			
			
	  		
	  		// run dstab to test 1-phase ac model
	       	// initGenLoad-- summarize the effects of contributive Gen/Load to make equivGen/load for power flow calculation	//	net.initContributeGenLoad();
	  			
	  			DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
	  					net, IpssCorePlugin.getMsgHub());
	  				
	  		
	  		  	dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
	  			dstabAlgo.setSimuStepSec(0.005d);
	  			dstabAlgo.setTotalSimuTimeSec(1);
	  			
	  			StateMonitor sm = new StateMonitor();
	  			sm.addGeneratorStdMonitor(new String[]{"Bus3-mach1"});
	  			sm.addBusStdMonitor(new String[]{"Bus3","Bus1"});
	  			
	  			// set the output handler
	  			dstabAlgo.setSimuOutputHandler(sm);
	  			dstabAlgo.setOutPutPerSteps(1);
	  		  	if(dstabAlgo.initialization()){
	  		  		// check induction motor initialization;
	  		  	    //slip
	  		  		
	  		  		
	  		  		//init power
	  		  		
	  		  		//Positive EquivY
	  		  		
	  		  		
	  		  		//Positive sequence Current Inj
	  		  		
	  		  		
	  		  		//3phase equivY
	  		  		
	  		  		
	  		  		System.out.println(indMotor3Phase.getInitLoadPQ3Phase().toString());
	  		  	   
	  		  	   dstabAlgo.performSimulation();
	  		  	    
	  		  	    
	  		  	}
	  		  	
	  		 	System.out.println(sm.toCSVString(sm.getBusAngleTable()));
	  		  	System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	}
}
