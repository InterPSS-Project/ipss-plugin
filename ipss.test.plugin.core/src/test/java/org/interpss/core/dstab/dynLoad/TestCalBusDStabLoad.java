package org.interpss.core.dstab.dynLoad;

import static org.junit.Assert.assertTrue;

import org.interpss.core.dstab.mach.TestSetupBase;
import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.dstab.dynLoad.impl.InductionMotorImpl;
import org.interpss.numeric.util.NumericUtil;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.sc.ScBusModelType;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;

public class TestCalBusDStabLoad extends TestSetupBase {
	
	@Test
	public void test_cal_half_indMotor()  throws InterpssException {
		DStabilityNetwork net = create2BusSystem();
		net.initialization(ScBusModelType.DSTAB_SIMU);
		
		assertTrue(net.isLfConverged());
		
		DStabBus bus1 = (DStabBus) net.getDStabBus("Bus1");
		
		InductionMotor indMotor= new InductionMotorImpl(bus1,"1");
		

		indMotor.setXm(3.0);
		indMotor.setXl(0.07);
		indMotor.setRa(0.032);
		indMotor.setXr1(0.2);
		indMotor.setRr1(0.01);
		indMotor.setXr2(0.0);
		indMotor.setRr2(0.0);
		
		indMotor.setLoadPercent(50);
		indMotor.setMvaBase(50);
		indMotor.setH(1.0);
		
		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.1);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
		//net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,0.0d,0.05),"3phaseFault@Bus5");
        
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus1"});
		sm.addBusLoadMonitor(new String[]{"Bus1"});
		sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus1");
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		//IpssLogger.getLogger().setLevel(Level.FINE);
		
		
		if (dstabAlgo.initialization()) {
			System.out.println(net.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
		    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec())
			     dstabAlgo.solveDEqnStep(true);
			
		}
		else{
			System.out.println("Initialization error!");
		}
		
		System.out.println("\n bus total Load P:\n"+sm.toCSVString(sm.getBusTotalLoadPTable()));
		
		System.out.println("\n bus total Load Q:\n"+sm.toCSVString(sm.getBusTotalLoadQTable()));
		
		System.out.println(sm.getBusTotalLoadQTable().get("Bus1").get(1).getValue());
		assertTrue(NumericUtil.equals(sm.getBusTotalLoadPTable().get("Bus1").get(1).getValue(), 0.8,1.0E-6));
		assertTrue(NumericUtil.equals(sm.getBusTotalLoadQTable().get("Bus1").get(1).getValue(), 0.2,1.0E-5));
		
	}
	

}
