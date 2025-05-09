package org.interpss.core.dstab.dynLoad;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.interpss.core.dstab.mach.TestSetupBase;
import org.interpss.display.AclfOutFunc;
import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.dstab.dynLoad.impl.InductionMotorImpl;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.sc.ScBusModelType;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineModelType;

public class TestInductionMotorModel extends TestSetupBase {
	
	@Test
	public void test_induction_Motor_dynModel_single_cage()  throws InterpssException {
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
		
		//for testing on system MVA base
		boolean isSysMVABase = true;
		indMotor.setOutputPowerMVABase(isSysMVABase);
		
		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.1);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
		//net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,0.0d,0.05),"3phaseFault@Bus5");
        
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus1"});
		sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus1");
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		
		if (dstabAlgo.initialization()) {
			System.out.println(net.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
		    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec())
			     dstabAlgo.solveDEqnStep(true);
			
		}
		else{
			System.out.println("Initialization error!");
		}
		
		System.out.println("slip ="+indMotor.getSlip());
		System.out.println("motor power ="+indMotor.getLoadPQ());
		/*
		 * slip =0.00881671588619575
           motor power =(0.7999996196862666, 0.4981598698987694)
		 */
		assertTrue(Math.abs(indMotor.getSlip()-0.00881671588619575)<1.0E-5);
		
		assertTrue(Math.abs(indMotor.getLoadPQ().getReal()-0.8)<1.0E-5);
		
		assertTrue( Math.abs(bus1.getVoltageMag()-1.00932)<1.0E-5);
		
		
		//}
		//System.out.println(sm.toCSVString(sm.getMachAngleTable()));
		//System.out.println("\n bus volt:\n"+sm.toCSVString(sm.getBusVoltTable()));
		//System.out.println("\n gen Pe:\n"+sm.toCSVString(sm.getMachPeTable()));
		//System.out.println("\n motor slip:\n"+sm.toCSVString(sm.getMotorSlipTable()));
		//System.out.println("\n motor Te:\n"+sm.toCSVString(sm.getMotorTeTable()));
		//System.out.println("\n motor Tm:\n"+sm.toCSVString(sm.getMotorTmTable()));
		System.out.println("\n motor Power:\n"+sm.toCSVString(sm.getMotorPTable()));
		
		MonitorRecord pRec0 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(0);
		
		MonitorRecord pRec10 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(10);
		
		assertTrue(Math.abs(pRec0.value-pRec10.value)<1.0E-5);
		
		// bus1.setLoadP(0.8);
		// bus1.setLoadQ(0.2); 
		// motor loadpercentage = 50% ==> motor power = 40MW or 0.4 pu on system base
		
		if (isSysMVABase)
			assertTrue(pRec0.value==0.4);
		else {
			assertTrue(pRec0.value==0.8);
		}
	}
	
	@Test
	public void test_induction_Motor_dynModel_single_cage_loadchange()  throws InterpssException {
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
			dstabAlgo.setTotalSimuTimeSec(1.0);

			dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
			//net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,0.0d,0.05),"3phaseFault@Bus5");
	        
	        
			
			StateMonitor sm = new StateMonitor();
			sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
			sm.addBusStdMonitor(new String[]{"Bus1"});
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus1");
			
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(1);
			
			IpssLogger.getLogger().setLevel(Level.FINE);
			
			
			if (dstabAlgo.initialization()) {
				System.out.println(net.getMachineInitCondition());
				
				System.out.println("Running DStab simulation ...");
			    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
				     dstabAlgo.solveDEqnStep(true);
				     if(dstabAlgo.getSimuTime()>=0.101 && dstabAlgo.getSimuTime()<0.11){
				    	 indMotor.changeLoad(-0.1);
				     }
				     
				     if(dstabAlgo.getSimuTime()>=0.149 && dstabAlgo.getSimuTime()<0.151){
				    	 indMotor.changeLoad(0.2);
				     }
			    }
				
			}
			else{
				System.out.println("Initialization error!");
			}

			
			//System.out.println("slip ="+indMotor.getSlip());
			//System.out.println("motor power ="+indMotor.getLoadPQ());


			//System.out.println("\n bus volt:\n"+sm.toCSVString(sm.getBusVoltTable()));
			//System.out.println("\n gen Pe:\n"+sm.toCSVString(sm.getMachPeTable()));
			//System.out.println("\n motor slip:\n"+sm.toCSVString(sm.getMotorSlipTable()));
			//System.out.println("\n motor Te:\n"+sm.toCSVString(sm.getMotorTeTable()));
			//System.out.println("\n motor Tm:\n"+sm.toCSVString(sm.getMotorTmTable()));
			//System.out.println("\n motor Power:\n"+sm.toCSVString(sm.getMotorPTable()));
			
	
			
		}
	
	//@Test
	public void test_induction_Motor_dynModel_single_cage_protection()  throws InterpssException {
		DStabilityNetwork net = create2BusSystem();
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
		indMotor.setVtr1(0.7);
		indMotor.setTtr1(0.02);
		indMotor.setFtr1(0.2);
		indMotor.setVrc1(0.90);
		indMotor.setTrc1(0.05);
		
		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.5);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
		net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,0.1d,0.05),"3phaseFault@Bus1");
        
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus1","Swing"});
		sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus1");
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		
		if (dstabAlgo.initialization()) {
			System.out.println(net.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
		    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
		    	System.out.println("\n time = "+dstabAlgo.getSimuTime());
			     dstabAlgo.solveDEqnStep(true);
		    }
			
		}
		else{
			System.out.println("Initialization error!");
		}
		/*
		 * slip =0.009072990571330868
           motor power =(0.7999999997203182, 0.49512907648490423)
		 */
		
		//System.out.println("slip ="+indMotor.getSlip());
		//System.out.println("motor power ="+indMotor.getLoadPQ());
		
		//System.out.println("\n bus volt:\n"+sm.toCSVString(sm.getBusVoltTable()));
		//System.out.println("\n gen Pe:\n"+sm.toCSVString(sm.getMachPeTable()));
		//System.out.println("\n motor slip:\n"+sm.toCSVString(sm.getMotorSlipTable()));
		//System.out.println("\n motor Te:\n"+sm.toCSVString(sm.getMotorTeTable()));
		//System.out.println("\n motor Tm:\n"+sm.toCSVString(sm.getMotorTmTable()));
		//System.out.println("\n motor Power:\n"+sm.toCSVString(sm.getMotorPTable()));
		
		MonitorRecord pRec0 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(0);
		
		MonitorRecord pRec10 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(10);
		
		assertTrue(Math.abs(pRec0.value-pRec10.value)<1.0E-5);
	}
	
	//@Test
	public void test_induction_Motor_dynModel_double_cage()  throws InterpssException {
		DStabilityNetwork net = create2BusSystem();
		assertTrue(net.isLfConverged());
		
		DStabBus bus1 = (DStabBus) net.getDStabBus("Bus1");
		
		InductionMotor indMotor= new InductionMotorImpl(bus1,"1");
		

		indMotor.setXm(3.0);
		indMotor.setXl(0.07);
		indMotor.setRa(0.032);
		indMotor.setXr1(0.3);
		indMotor.setRr1(0.05);
		indMotor.setXr2(0.3);
		indMotor.setRr2(0.02);
		
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
		sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus1");
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		
		if (dstabAlgo.initialization()) {
			System.out.println(net.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
		    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec())
			     dstabAlgo.solveDEqnStep(true);
			
		}
		else{
			System.out.println("Initialization error!");
		}
		/*
		 * slip =0.009598914568821991
           motor power =(0.7999999997171088, 0.5790774480423598)
		 */
//		assertTrue(Math.abs(indMotor.getSlip()-0.00959891)<1.0E-5);
//		
//		assertTrue(Math.abs(indMotor.getLoadPQ().getReal()-0.8)<1.0E-5);
//		
//		assertTrue( Math.abs(bus1.getVoltageMag()-0.99677)<1.0E-5);
		
		//System.out.println("slip ="+indMotor.getSlip());
		//System.out.println("motor power ="+indMotor.getLoadPQ());
		//}
		//System.out.println(sm.toCSVString(sm.getMachAngleTable()));
		//System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		//System.out.println(sm.toCSVString(sm.getMachPeTable()));
		//System.out.println(sm.toCSVString(sm.getMotorSlipTable()));
		//System.out.println(sm.toCSVString(sm.getMotorTeTable()));
		//System.out.println(sm.toCSVString(sm.getMotorTmTable()));
		//System.out.println(sm.toCSVString(sm.getMotorPTable()));
		
		
	}

}

