package org.interpss.core.dstab.dynLoad;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.interpss.core.dstab.mach.TestSetupBase;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.Point;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.dstab.dynLoad.InductionMotor;
import com.interpss.dstab.dynLoad.impl.MotorContactorControl;
import com.interpss.dstab.dynLoad.impl.MotorEMSControl;
import com.interpss.dstab.dynLoad.impl.MotorElectronicRelayProtection;
import com.interpss.dstab.dynLoad.impl.MotorOverLoadProtection;
import com.interpss.dstab.dynLoad.impl.MotorThermalProtection;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineType;

public class TestMotorProtectionModel  extends TestSetupBase {
	
	//@Test
	public void test_induction_Motor_dynModel_single_cage_protection()  throws InterpssException {
		DStabilityNetwork net = create2BusSystem();
		assertTrue(net.isLfConverged());
		
		DStabBus bus1 = net.getDStabBus("Bus1");
		
		InductionMotor indMotor= DStabObjectFactory.createInductionMotor(bus1,"1");
		

		indMotor.setXm(3.0);
		indMotor.setXl(0.07);
		indMotor.setRa(0.032);
		indMotor.setXr1(0.2);
		indMotor.setRr1(0.01);
		indMotor.setXr2(0.0);
		indMotor.setRr2(0.0);
		
		indMotor.setLoadPercent(50);
		indMotor.setMVABase(50);
		indMotor.setH(1.0);
		indMotor.setVtr1(0.7);
		indMotor.setTtr1(0.02);
		indMotor.setFtr1(0.2);
		indMotor.setVrc1(0.90);
		indMotor.setTrc1(0.05);
		
		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(net));
		
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
		
		System.out.println("slip ="+indMotor.getSlip());
		System.out.println("motor power ="+indMotor.getLoadPQ());
		
		System.out.println("\n bus volt:\n"+sm.toCSVString(sm.getBusVoltTable()));
		System.out.println("\n gen Q:\n"+sm.toCSVString(sm.getMachQgenTable()));
		System.out.println("\n gen Pe:\n"+sm.toCSVString(sm.getMachPeTable()));
		System.out.println("\n motor slip:\n"+sm.toCSVString(sm.getMotorSlipTable()));
		System.out.println("\n motor Te:\n"+sm.toCSVString(sm.getMotorTeTable()));
		System.out.println("\n motor Tm:\n"+sm.toCSVString(sm.getMotorTmTable()));
		System.out.println("\n motor Real Power:\n"+sm.toCSVString(sm.getMotorPTable()));
		System.out.println("\n motor Reactive Power:\n"+sm.toCSVString(sm.getMotorQTable()));
		
		MonitorRecord pRec0 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(0);
		
		MonitorRecord pRec10 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(10);
		
		assertTrue(Math.abs(pRec0.value-pRec10.value)<1.0E-5);
	}
	
	
	//@Test
	public void test_single_induction_Motor_electronic_relay_protection()  throws InterpssException {
		DStabilityNetwork net = create2BusSystem();
		assertTrue(net.isLfConverged());
		
		DStabBus bus1 = net.getDStabBus("Bus1");
		
		InductionMotor indMotor= DStabObjectFactory.createInductionMotor(bus1,"1");
		

		indMotor.setXm(3.0);
		indMotor.setXl(0.07);
		indMotor.setRa(0.032);
		indMotor.setXr1(0.2);
		indMotor.setRr1(0.01);
		indMotor.setXr2(0.0);
		indMotor.setRr2(0.0);
		
		indMotor.setLoadPercent(50);
		indMotor.setMVABase(50);
		indMotor.setH(0.3);
		
		//create electronic relay and add it to the list
		MotorElectronicRelayProtection eleRelay = new MotorElectronicRelayProtection(indMotor);
		eleRelay.getTripVoltTimeCurve().getPoints().add(new Point(0.05,0.7));       // x-axis is time, y-axis is voltage
		
		eleRelay.getReconnectVoltTimeCurve().getPoints().add(new Point(0.016,0.9)); // x-axis is time, y-axis is voltage
		indMotor.getProtectionControlList().add(eleRelay);

		
		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(1.0);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
		net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,0.1d,0.08),"3phaseFault@Bus1");
        
        
		
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
		
		System.out.println("slip ="+indMotor.getSlip());
		System.out.println("motor power ="+indMotor.getLoadPQ());
		
		System.out.println("\n bus volt:\n"+sm.toCSVString(sm.getBusVoltTable()));
		System.out.println("\n gen Pe:\n"+sm.toCSVString(sm.getMachPeTable()));
		System.out.println("\n motor slip:\n"+sm.toCSVString(sm.getMotorSlipTable()));
		System.out.println("\n motor Te:\n"+sm.toCSVString(sm.getMotorTeTable()));
		System.out.println("\n motor Tm:\n"+sm.toCSVString(sm.getMotorTmTable()));
		System.out.println("\n motor Real Power:\n"+sm.toCSVString(sm.getMotorPTable()));
		System.out.println("\n motor Reactive Power:\n"+sm.toCSVString(sm.getMotorQTable()));
		
		
		assertTrue(eleRelay.getReconnectStatus());
		
		MonitorRecord pRec0 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(0);
		
		MonitorRecord pRec10 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(10);
		
		assertTrue(Math.abs(pRec0.value-pRec10.value)<1.0E-5);
		
		// at 0.180 second, the motor has been tripped, pRec39 = 0.0
		MonitorRecord pRec39 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(39);
		assertTrue(Math.abs(pRec39.value)<1.0E-9);
		
		//the fault is cleared at 0.180, after 0.016 (1 cycle) delay, the motor is reconnected, at 0.20 second, the motor P should be > 0.0
		MonitorRecord pRec43 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(43);
		// 0.2050,    0.41316,
		assertTrue(pRec43.value>0.41);
		
	}
	
	
	//@Test
	public void test_single_induction_Motor_overload_relay_protection()  throws InterpssException {
		DStabilityNetwork net = create2BusSystem();
		assertTrue(net.isLfConverged());
		
		
		EConstMachine mach =(EConstMachine) net.getDStabBus("Swing").getMachine("Mach1");
		DStabBus bus1 = net.getDStabBus("Bus1");
		
		InductionMotor indMotor= DStabObjectFactory.createInductionMotor(bus1,"1");
		

		indMotor.setXm(3.0);
		indMotor.setXl(0.07);
		indMotor.setRa(0.032);
		indMotor.setXr1(0.2);
		indMotor.setRr1(0.01);
		indMotor.setXr2(0.0);
		indMotor.setRr2(0.0);
		
		indMotor.setLoadPercent(50);
		indMotor.setMVABase(50);
		indMotor.setH(0.3);
		
		//create electronic relay and add it to the list
//		MotorElectronicRelayProtection eleRelay = new MotorElectronicRelayProtection(indMotor);
//		eleRelay.getTripVoltTimeCurve().getPoints().add(new Point(0.05,0.7)); // x-axis is time, y-axis is voltage
//		
//		eleRelay.getReconnectVoltTimeCurve().getPoints().add(new Point(0.016,1.0)); // x-axis is time, y-axis is voltage
//		
//		indMotor.getProtectionControlList().add(eleRelay);
		
		MotorOverLoadProtection olProtection = new MotorOverLoadProtection(indMotor);
		olProtection.getTripVoltTimeCurve().getPoints().add(new Point(0.33,0.6)); // x-axis is time, y-axis is voltage
		olProtection.getTripVoltTimeCurve().getPoints().add(new Point(0.5, 0.7)); // x-axis is time, y-axis is voltage
		olProtection.getTripVoltTimeCurve().getPoints().add(new Point(1.0, 0.8)); // x-axis is time, y-axis is voltage
		//olProtection.getReconnectVoltTimeCurve().getPoints().add(new Point(0.016,1.0)); // x-axis is time, y-axis is voltage
		
		indMotor.getProtectionControlList().add(olProtection);

		
		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(1.0);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
		//net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,0.1d,0.08),"3phaseFault@Bus1");
        
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus1","Swing"});
		sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus1");
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		double vsag = 0.7;
		if (dstabAlgo.initialization()) {
			System.out.println(net.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
		    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
		    	System.out.println("\n time = "+dstabAlgo.getSimuTime());
			     dstabAlgo.solveDEqnStep(true);
			     
			     if(dstabAlgo.getSimuTime()>0.1 && dstabAlgo.getSimuTime()<0.7){
						mach.setE(vsag);
					}
					else if (dstabAlgo.getSimuTime()>=0.7){
						mach.setE(1.02);
					}
		    }
			
		}
		else{
			System.out.println("Initialization error!");
		}
		/*
		 * slip =0.009072990571330868
           motor power =(0.7999999997203182, 0.49512907648490423)
		 */
		
		System.out.println("slip ="+indMotor.getSlip());
		System.out.println("motor power ="+indMotor.getLoadPQ());
		
		System.out.println("\n bus volt:\n"+sm.toCSVString(sm.getBusVoltTable()));
		System.out.println("\n gen Pe:\n"+sm.toCSVString(sm.getMachPeTable()));
		System.out.println("\n motor slip:\n"+sm.toCSVString(sm.getMotorSlipTable()));
		System.out.println("\n motor Te:\n"+sm.toCSVString(sm.getMotorTeTable()));
		System.out.println("\n motor Tm:\n"+sm.toCSVString(sm.getMotorTmTable()));
		System.out.println("\n motor Power:\n"+sm.toCSVString(sm.getMotorPTable()));
		
		MonitorRecord pRec0 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(0);
		
		MonitorRecord pRec10 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(10);
		MonitorRecord pRec129 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(129);
		MonitorRecord pRec130 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(130);
		
		assertTrue(Math.abs(pRec0.value-pRec10.value)<1.0E-5);
		/*  time, Motor P
		 *  0.6450,    0.84543,
            0.6500,    0.00000,

		 */
		assertTrue(pRec129.value-0.845 >0.0);
		assertTrue(pRec130.value==0.0);
	}
	
	//@Test
	public void test_single_induction_Motor_thermal_relay_protection()  throws InterpssException {
		DStabilityNetwork net = create2BusSystem();
		assertTrue(net.isLfConverged());
		
		
		EConstMachine mach =(EConstMachine) net.getDStabBus("Swing").getMachine("Mach1");
		DStabBus bus1 = net.getDStabBus("Bus1");
		
		InductionMotor indMotor= DStabObjectFactory.createInductionMotor(bus1,"1");
		

		indMotor.setXm(3.0);
		indMotor.setXl(0.07);
		indMotor.setRa(0.032);
		indMotor.setXr1(0.2);
		indMotor.setRr1(0.01);
		indMotor.setXr2(0.0);
		indMotor.setRr2(0.0);
		
		indMotor.setLoadPercent(50);
		indMotor.setMVABase(50);
		indMotor.setH(0.3);
		
		//create electronic relay and add it to the list
//		MotorElectronicRelayProtection eleRelay = new MotorElectronicRelayProtection(indMotor);
//		eleRelay.getTripVoltTimeCurve().getPoints().add(new Point(0.05,0.7)); // x-axis is time, y-axis is voltage
//		
//		eleRelay.getReconnectVoltTimeCurve().getPoints().add(new Point(0.016,1.0)); // x-axis is time, y-axis is voltage
//		
//		indMotor.getProtectionControlList().add(eleRelay);
		
		MotorThermalProtection thermalProtection = new MotorThermalProtection(indMotor);
		thermalProtection.getTripVoltTimeCurve().getPoints().add(new Point(0.5,0.55)); // x-axis is time, y-axis is voltage
		thermalProtection.getTripVoltTimeCurve().getPoints().add(new Point(1.0, 0.6)); // x-axis is time, y-axis is voltage
		thermalProtection.getTripVoltTimeCurve().getPoints().add(new Point(2.0, 0.65)); // x-axis is time, y-axis is voltage
		thermalProtection.getTripVoltTimeCurve().getPoints().add(new Point(4.0, 0.7)); // x-axis is time, y-axis is voltage
		thermalProtection.getTripVoltTimeCurve().getPoints().add(new Point(8.0, 0.75)); // x-axis is time, y-axis is voltage
		thermalProtection.getTripVoltTimeCurve().getPoints().add(new Point(10.0, 0.8)); // x-axis is time, y-axis is voltage
		//olProtection.getReconnectVoltTimeCurve().getPoints().add(new Point(0.016,1.0)); // x-axis is time, y-axis is voltage
		
		indMotor.getProtectionControlList().add(thermalProtection);

		
		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(2.5);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
		//net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,0.1d,0.08),"3phaseFault@Bus1");
        
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus1","Swing"});
		sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus1");
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		double vsag = 0.6;
		double startTime = 0.1;
		double duration = 1.1;
		double endTime = startTime+duration;
		if (dstabAlgo.initialization()) {
			System.out.println(net.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
		    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
		    	System.out.println("\n time = "+dstabAlgo.getSimuTime());
			     dstabAlgo.solveDEqnStep(true);
			     
			     if(dstabAlgo.getSimuTime()>=startTime && dstabAlgo.getSimuTime()<endTime){
						mach.setE(vsag);
					}
					else if (dstabAlgo.getSimuTime()>=endTime ){
						mach.setE(1.02);
					}
		    }
			
		}
		else{
			System.out.println("Initialization error!");
		}
		/*
		 * slip =0.009072990571330868
           motor power =(0.7999999997203182, 0.49512907648490423)
		 */
		
		System.out.println("slip ="+indMotor.getSlip());
		System.out.println("motor power ="+indMotor.getLoadPQ());
		
		System.out.println("\n bus volt:\n"+sm.toCSVString(sm.getBusVoltTable()));
		System.out.println("\n gen Pe:\n"+sm.toCSVString(sm.getMachPeTable()));
		System.out.println("\n motor slip:\n"+sm.toCSVString(sm.getMotorSlipTable()));
		System.out.println("\n motor Te:\n"+sm.toCSVString(sm.getMotorTeTable()));
		//System.out.println("\n motor Tm:\n"+sm.toCSVString(sm.getMotorTmTable()));
		System.out.println("\n motor Power:\n"+sm.toCSVString(sm.getMotorPTable()));
		
		MonitorRecord pRec0 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(0);
		
		MonitorRecord pRec10 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(10);
		MonitorRecord pRec225 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(225);
		MonitorRecord pRec226 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(226);
		
		assertTrue(Math.abs(pRec0.value-pRec10.value)<1.0E-5);
		
		/*
		 *  1.12	0.18555
			1.125	0.18526

		 */
		assertTrue(pRec225.value-0.18 >0.0);
		assertTrue(pRec226.value==0.0);
	}
	
	
	//@Test
	public void test_single_induction_Motor_contactor_relay_protection()  throws InterpssException {
		DStabilityNetwork net = create2BusSystem();
		assertTrue(net.isLfConverged());
		
		
		EConstMachine mach =(EConstMachine) net.getDStabBus("Swing").getMachine("Mach1");
		DStabBus bus1 = net.getDStabBus("Bus1");
		
		InductionMotor indMotor= DStabObjectFactory.createInductionMotor(bus1,"1");
		

		indMotor.setXm(3.0);
		indMotor.setXl(0.07);
		indMotor.setRa(0.032);
		indMotor.setXr1(0.2);
		indMotor.setRr1(0.01);
		indMotor.setXr2(0.0);
		indMotor.setRr2(0.0);
		
		indMotor.setLoadPercent(50);
		indMotor.setMVABase(50);
		indMotor.setH(0.3);
		
		//create electronic relay and add it to the list
//		MotorElectronicRelayProtection eleRelay = new MotorElectronicRelayProtection(indMotor);
//		eleRelay.getTripVoltTimeCurve().getPoints().add(new Point(0.05,0.7)); // x-axis is time, y-axis is voltage
//		
//		eleRelay.getReconnectVoltTimeCurve().getPoints().add(new Point(0.016,1.0)); // x-axis is time, y-axis is voltage
//		
//		indMotor.getProtectionControlList().add(eleRelay);
		
		MotorContactorControl contactor = new MotorContactorControl(indMotor);
		
		contactor.getTripVoltTimeCurve().getPoints().add(new Point(0.083,0.55));  // x-axis is time, y-axis is voltage


		contactor.getReconnectVoltTimeCurve().getPoints().add(new Point(0.1,0.7)); // x-axis is time, y-axis is voltage
		
		indMotor.getProtectionControlList().add(contactor);

		
		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.505);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
		//net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,0.1d,0.08),"3phaseFault@Bus1");
        
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus1","Swing"});
		sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus1");
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		double vsag = 0.54;
		double startTime = 0.1;
		double duration = 0.1;
		double endTime = startTime+duration;
		if (dstabAlgo.initialization()) {
			System.out.println(net.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
		    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
		    	System.out.println("\n time = "+dstabAlgo.getSimuTime());
			     dstabAlgo.solveDEqnStep(true);
			     
			     
			     if(dstabAlgo.getSimuTime()>=startTime && dstabAlgo.getSimuTime()<endTime){
						mach.setE(vsag);
					}
				 else if (dstabAlgo.getSimuTime()>=endTime && dstabAlgo.getSimuTime()<endTime+0.1){
					mach.setE(1.02);
				 }
				 else if(dstabAlgo.getSimuTime()>0.3 && dstabAlgo.getSimuTime()<0.4){
					 mach.setE(0.5);
				 }
				 else if(dstabAlgo.getSimuTime()>=0.4){
					 mach.setE(1.02);
				 }
			     
			     
		    }
			
		}
		else{
			System.out.println("Initialization error!");
		}
		/*
		 * slip =0.009072990571330868
           motor power =(0.7999999997203182, 0.49512907648490423)
		 */
		
		System.out.println("slip ="+indMotor.getSlip());
		System.out.println("motor power ="+indMotor.getLoadPQ());
		
		System.out.println("\n bus volt:\n"+sm.toCSVString(sm.getBusVoltTable()));
		System.out.println("\n gen Pe:\n"+sm.toCSVString(sm.getMachPeTable()));
		System.out.println("\n motor slip:\n"+sm.toCSVString(sm.getMotorSlipTable()));
		System.out.println("\n motor Te:\n"+sm.toCSVString(sm.getMotorTeTable()));
		//System.out.println("\n motor Tm:\n"+sm.toCSVString(sm.getMotorTmTable()));
		System.out.println("\n motor Power:\n"+sm.toCSVString(sm.getMotorPTable()));
		
		MonitorRecord pRec0 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(0);
		
		MonitorRecord pRec10 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(10);
		MonitorRecord pRec76 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(76);
		MonitorRecord pRec77 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(77);
		
		assertTrue(Math.abs(pRec0.value-pRec10.value)<1.0E-5);
		
		
		/* withstand the first dip but tripped for the second dip
		 * time, power
		 * 0.38	0.70154
			0.385	0

		 */
		assertTrue(pRec76.value-0.7 >0.0);
		assertTrue(pRec77.value==0.0);
		
		assertTrue(contactor.getReconnectStatus());
		
		
	}
	
	//@Test
	public void test_single_induction_Motor_EMS_relay_protection()  throws InterpssException {
		DStabilityNetwork net = create2BusSystem();
		assertTrue(net.isLfConverged());
		
		
		EConstMachine mach =(EConstMachine) net.getDStabBus("Swing").getMachine("Mach1");
		DStabBus bus1 = net.getDStabBus("Bus1");
		
		InductionMotor indMotor= DStabObjectFactory.createInductionMotor(bus1,"1");
		

		indMotor.setXm(3.0);
		indMotor.setXl(0.07);
		indMotor.setRa(0.032);
		indMotor.setXr1(0.2);
		indMotor.setRr1(0.01);
		indMotor.setXr2(0.0);
		indMotor.setRr2(0.0);
		
		indMotor.setLoadPercent(50);
		indMotor.setMVABase(50);
		indMotor.setH(0.3);
		
		//create electronic relay and add it to the list
//		MotorElectronicRelayProtection eleRelay = new MotorElectronicRelayProtection(indMotor);
//		eleRelay.getTripVoltTimeCurve().getPoints().add(new Point(0.05,0.7)); // x-axis is time, y-axis is voltage
//		
//		eleRelay.getReconnectVoltTimeCurve().getPoints().add(new Point(0.016,1.0)); // x-axis is time, y-axis is voltage
//		
//		indMotor.getProtectionControlList().add(eleRelay);
		
		MotorEMSControl ems = new MotorEMSControl(indMotor);
		
		ems.getTripVoltTimeCurve().getPoints().add(new Point(0.083,0.0));  // x-axis is time, y-axis is voltage
		ems.getTripVoltTimeCurve().getPoints().add(new Point(0.1,0.4)); // x-axis is time, y-axis is voltage
		ems.getTripVoltTimeCurve().getPoints().add(new Point(0.166, 0.5)); // x-axis is time, y-axis is voltage
		ems.getTripVoltTimeCurve().getPoints().add(new Point(0.25, 0.6)); // x-axis is time, y-axis is voltage
		ems.getTripVoltTimeCurve().getPoints().add(new Point(0.5, 0.7)); // x-axis is time, y-axis is voltage
	


		ems.getReconnectVoltTimeCurve().getPoints().add(new Point(2.0,1.0)); // x-axis is time, y-axis is voltage
		
		indMotor.getProtectionControlList().add(ems);

		
		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(2.3);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
		//net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,0.1d,0.08),"3phaseFault@Bus1");
        
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus1","Swing"});
		sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus1");
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		double vsag = 0.35;
		double startTime = 0.1;
		double duration = 0.11;
		double endTime = startTime+duration;
		if (dstabAlgo.initialization()) {
			System.out.println(net.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
		    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
		    	System.out.println("\n time = "+dstabAlgo.getSimuTime());
			     dstabAlgo.solveDEqnStep(true);
			     
			     
			     if(dstabAlgo.getSimuTime()>=startTime && dstabAlgo.getSimuTime()<endTime){
						mach.setE(vsag);
					}
				 else if (dstabAlgo.getSimuTime()>=endTime && dstabAlgo.getSimuTime()<endTime+0.1){
					mach.setE(1.02);
				 }
//				 else if(dstabAlgo.getSimuTime()>0.3 && dstabAlgo.getSimuTime()<0.4){
//					 mach.setE(0.5);
//				 }
//				 else if(dstabAlgo.getSimuTime()>=0.4){
//					 mach.setE(0.5);
//				 }
			     
			     
		    }
			
		}
		else{
			System.out.println("Initialization error!");
		}
		/*
		 * slip =0.009072990571330868
           motor power =(0.7999999997203182, 0.49512907648490423)
		 */
		
		System.out.println("slip ="+indMotor.getSlip());
		System.out.println("motor power ="+indMotor.getLoadPQ());
		
		System.out.println("\n bus volt:\n"+sm.toCSVString(sm.getBusVoltTable()));
		System.out.println("\n gen Pe:\n"+sm.toCSVString(sm.getMachPeTable()));
		System.out.println("\n motor slip:\n"+sm.toCSVString(sm.getMotorSlipTable()));
		System.out.println("\n motor Te:\n"+sm.toCSVString(sm.getMotorTeTable()));
		//System.out.println("\n motor Tm:\n"+sm.toCSVString(sm.getMotorTmTable()));
		System.out.println("\n motor Power:\n"+sm.toCSVString(sm.getMotorPTable()));
		
		MonitorRecord pRec0 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(0);
		
		MonitorRecord pRec10 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(10);
		MonitorRecord pRec39 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(39);
		MonitorRecord pRec40 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(40);
		MonitorRecord pRec443 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(443);
		MonitorRecord pRec444 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(444);
		
		assertTrue(Math.abs(pRec0.value-pRec10.value)<1.0E-5);
		
		assertTrue(ems.getReconnectStatus());
		/* withstand the first dip but tripped for the second dip
		 * time, power
		 * 0.195	0.47014
         *  0.2	    0
		 */
		assertTrue(pRec39.value-0.47 >0.0);
		assertTrue(pRec40.value==0.0);
		
		/*time, power
		 * 2.215	0.00002
          2.22	0.41312

		 */
		assertTrue(pRec443.value<0.0001);
		assertTrue(pRec444.value-0.4>0.0);
		
		
	}
	
	//@Test
	public void test_single_induction_Motor_OverLoad_Contractor_EMS_relay_protection()  throws InterpssException {
		DStabilityNetwork net = create2BusSystem();
		assertTrue(net.isLfConverged());
		
		
		EConstMachine mach =(EConstMachine) net.getDStabBus("Swing").getMachine("Mach1");
		DStabBus bus1 = net.getDStabBus("Bus1");
		
		InductionMotor indMotor= DStabObjectFactory.createInductionMotor(bus1,"1");
		

		indMotor.setXm(3.0);
		indMotor.setXl(0.07);
		indMotor.setRa(0.032);
		indMotor.setXr1(0.2);
		indMotor.setRr1(0.01);
		indMotor.setXr2(0.0);
		indMotor.setRr2(0.0);
		
		indMotor.setLoadPercent(50);
		indMotor.setMVABase(50);
		indMotor.setH(0.3);
		

		
		MotorContactorControl contactor = new MotorContactorControl(indMotor);
		
		contactor.getTripVoltTimeCurve().getPoints().add(new Point(0.083,0.55));  // x-axis is time, y-axis is voltage

		contactor.getReconnectVoltTimeCurve().getPoints().add(new Point(0.1,0.7)); // x-axis is time, y-axis is voltage
		
		indMotor.getProtectionControlList().add(contactor);
		
		///
		MotorOverLoadProtection olProtection = new MotorOverLoadProtection(indMotor);
		olProtection.getTripVoltTimeCurve().getPoints().add(new Point(0.33,0.6)); // x-axis is time, y-axis is voltage
		olProtection.getTripVoltTimeCurve().getPoints().add(new Point(0.5, 0.7)); // x-axis is time, y-axis is voltage
		olProtection.getTripVoltTimeCurve().getPoints().add(new Point(1.0, 0.8)); // x-axis is time, y-axis is voltage
		//olProtection.getReconnectVoltTimeCurve().getPoints().add(new Point(0.016,1.0)); // x-axis is time, y-axis is voltage
		
		indMotor.getProtectionControlList().add(olProtection);
		
		/////
		MotorEMSControl ems = new MotorEMSControl(indMotor);
		
		ems.getTripVoltTimeCurve().getPoints().add(new Point(0.083,0.0));  // x-axis is time, y-axis is voltage
		ems.getTripVoltTimeCurve().getPoints().add(new Point(0.1,0.4)); // x-axis is time, y-axis is voltage
		ems.getTripVoltTimeCurve().getPoints().add(new Point(0.166, 0.5)); // x-axis is time, y-axis is voltage
		ems.getTripVoltTimeCurve().getPoints().add(new Point(0.25, 0.6)); // x-axis is time, y-axis is voltage
		ems.getTripVoltTimeCurve().getPoints().add(new Point(0.5, 0.7)); // x-axis is time, y-axis is voltage
	


		ems.getReconnectVoltTimeCurve().getPoints().add(new Point(2.0,1.0)); // x-axis is time, y-axis is voltage
		
		indMotor.getProtectionControlList().add(ems);

		
		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(2.3);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
		//net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,0.1d,0.08),"3phaseFault@Bus1");
        
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus1","Swing"});
		sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus1");
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		double vsag = 0.5;
		double startTime = 0.1;
		double duration = 0.11;
		double endTime = startTime+duration;
		if (dstabAlgo.initialization()) {
			System.out.println(net.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
		    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
		    	System.out.println("\n time = "+dstabAlgo.getSimuTime());
			     dstabAlgo.solveDEqnStep(true);
			     
			     
			     if(dstabAlgo.getSimuTime()>=startTime && dstabAlgo.getSimuTime()<endTime){
						mach.setE(vsag);
					}
				 else if (dstabAlgo.getSimuTime()>=endTime && dstabAlgo.getSimuTime()<endTime+0.1){
					mach.setE(1.02);
				 }
//				 else if(dstabAlgo.getSimuTime()>0.3 && dstabAlgo.getSimuTime()<0.4){
//					 mach.setE(0.5);
//				 }
//				 else if(dstabAlgo.getSimuTime()>=0.4){
//					 mach.setE(0.5);
//				 }
			     
			     
		    }
			
		}
		else{
			System.out.println("Initialization error!");
		}
		/*
		 * slip =0.009072990571330868
           motor power =(0.7999999997203182, 0.49512907648490423)
		 */
		
		System.out.println("slip ="+indMotor.getSlip());
		System.out.println("motor power ="+indMotor.getLoadPQ());
		
		System.out.println("\n bus volt:\n"+sm.toCSVString(sm.getBusVoltTable()));
		System.out.println("\n gen Pe:\n"+sm.toCSVString(sm.getMachPeTable()));
		System.out.println("\n motor slip:\n"+sm.toCSVString(sm.getMotorSlipTable()));
		System.out.println("\n motor Te:\n"+sm.toCSVString(sm.getMotorTeTable()));
		//System.out.println("\n motor Tm:\n"+sm.toCSVString(sm.getMotorTmTable()));
		System.out.println("\n motor Power:\n"+sm.toCSVString(sm.getMotorPTable()));
		
		MonitorRecord pRec0 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(0);
		
		MonitorRecord pRec10 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(10);
		MonitorRecord pRec36 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(36);
		MonitorRecord pRec37 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(37);
		
//		MonitorRecord pRec443 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(443);
//		MonitorRecord pRec444 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(444);
		
		assertTrue(Math.abs(pRec0.value-pRec10.value)<1.0E-5);
		
		assertTrue(contactor.getReconnectStatus());
		assertTrue(!ems.getReconnectStatus());
		assertTrue(!olProtection.getReconnectStatus());
		
		/* withstand the first dip but tripped for the second dip
		 * time, power
		 *  0.1800,    0.81210,
            0.1850,    0.00000,
         *  
         *   0.3100,    0.00000,
             0.3150,    0.41312,
		 */
		assertTrue(pRec36.value-0.81 >0.0);
		assertTrue(pRec37.value==0.0);
		

		
		
	}
	
	/*
	 *  protections 1,2,4,5
	 */
	@Test
	public void test_single_induction_Motor_Electronic_OverLoad_Contractor_EMS_relay_protection()  throws InterpssException {
		DStabilityNetwork net = create2BusSystem();
		assertTrue(net.isLfConverged());
		
		
		EConstMachine mach =(EConstMachine) net.getDStabBus("Swing").getMachine("Mach1");
		DStabBus bus1 = net.getDStabBus("Bus1");
		
		InductionMotor indMotor= DStabObjectFactory.createInductionMotor(bus1,"1");
		

		indMotor.setXm(3.0);
		indMotor.setXl(0.07);
		indMotor.setRa(0.032);
		indMotor.setXr1(0.2);
		indMotor.setRr1(0.01);
		indMotor.setXr2(0.0);
		indMotor.setRr2(0.0);
		
		indMotor.setLoadPercent(50);
		indMotor.setMVABase(50);
		indMotor.setH(0.3);
		
		// 
		MotorElectronicRelayProtection eleRelay = new MotorElectronicRelayProtection(indMotor);
		eleRelay.getTripVoltTimeCurve().getPoints().add(new Point(0.05,0.7));       // x-axis is time, y-axis is voltage
		
		eleRelay.getReconnectVoltTimeCurve().getPoints().add(new Point(0.016,0.95)); // x-axis is time, y-axis is voltage
		indMotor.getProtectionControlList().add(eleRelay);

		// 
		MotorContactorControl contactor = new MotorContactorControl(indMotor);
		
		contactor.getTripVoltTimeCurve().getPoints().add(new Point(0.083,0.55));  // x-axis is time, y-axis is voltage

		contactor.getReconnectVoltTimeCurve().getPoints().add(new Point(0.1,0.7)); // x-axis is time, y-axis is voltage
		
		indMotor.getProtectionControlList().add(contactor);
		
		///
		MotorOverLoadProtection olProtection = new MotorOverLoadProtection(indMotor);
		olProtection.getTripVoltTimeCurve().getPoints().add(new Point(0.33,0.6)); // x-axis is time, y-axis is voltage
		olProtection.getTripVoltTimeCurve().getPoints().add(new Point(0.5, 0.7)); // x-axis is time, y-axis is voltage
		olProtection.getTripVoltTimeCurve().getPoints().add(new Point(1.0, 0.8)); // x-axis is time, y-axis is voltage
		//olProtection.getReconnectVoltTimeCurve().getPoints().add(new Point(0.016,1.0)); // x-axis is time, y-axis is voltage
		
		indMotor.getProtectionControlList().add(olProtection);
		
		/////
		MotorEMSControl ems = new MotorEMSControl(indMotor);
		
		ems.getTripVoltTimeCurve().getPoints().add(new Point(0.083,0.0));  // x-axis is time, y-axis is voltage
		ems.getTripVoltTimeCurve().getPoints().add(new Point(0.1,0.4)); // x-axis is time, y-axis is voltage
		ems.getTripVoltTimeCurve().getPoints().add(new Point(0.166, 0.5)); // x-axis is time, y-axis is voltage
		ems.getTripVoltTimeCurve().getPoints().add(new Point(0.25, 0.6)); // x-axis is time, y-axis is voltage
		ems.getTripVoltTimeCurve().getPoints().add(new Point(0.5, 0.7)); // x-axis is time, y-axis is voltage
	


		ems.getReconnectVoltTimeCurve().getPoints().add(new Point(2.0,1.0)); // x-axis is time, y-axis is voltage
		
		indMotor.getProtectionControlList().add(ems);

		
		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(2.3);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
		//net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,0.1d,0.08),"3phaseFault@Bus1");
        
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus1","Swing"});
		sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus1");
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		double vsag = 0.5;
		double startTime = 0.1;
		double duration = 0.08;
		double endTime = startTime+duration;
		if (dstabAlgo.initialization()) {
			System.out.println(net.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
		    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
		    	System.out.println("\n time = "+dstabAlgo.getSimuTime());
			     dstabAlgo.solveDEqnStep(true);
			     
			     
			     if(dstabAlgo.getSimuTime()>=startTime && dstabAlgo.getSimuTime()<endTime){
						mach.setE(vsag);
					}
				 else if (dstabAlgo.getSimuTime()>=endTime && dstabAlgo.getSimuTime()<endTime+0.1){
					mach.setE(1.02);
				 }
//				 else if(dstabAlgo.getSimuTime()>0.3 && dstabAlgo.getSimuTime()<0.4){
//					 mach.setE(0.5);
//				 }
//				 else if(dstabAlgo.getSimuTime()>=0.4){
//					 mach.setE(0.5);
//				 }
			     
			     
		    }
			
		}
		else{
			System.out.println("Initialization error!");
		}
		/*
		 * slip =0.009072990571330868
           motor power =(0.7999999997203182, 0.49512907648490423)
		 */
		
		System.out.println("slip ="+indMotor.getSlip());
		System.out.println("motor power ="+indMotor.getLoadPQ());
		
		System.out.println("\n bus volt:\n"+sm.toCSVString(sm.getBusVoltTable()));
		System.out.println("\n gen Pe:\n"+sm.toCSVString(sm.getMachPeTable()));
		System.out.println("\n motor slip:\n"+sm.toCSVString(sm.getMotorSlipTable()));
		System.out.println("\n motor Te:\n"+sm.toCSVString(sm.getMotorTeTable()));
		//System.out.println("\n motor Tm:\n"+sm.toCSVString(sm.getMotorTmTable()));
		System.out.println("\n motor Power:\n"+sm.toCSVString(sm.getMotorPTable()));
		
		MonitorRecord pRec0 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(0);
		
		MonitorRecord pRec10 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(10);
		MonitorRecord pRec36 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(36);
		MonitorRecord pRec37 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(37);
		
//		MonitorRecord pRec443 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(443);
//		MonitorRecord pRec444 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(444);
		
		assertTrue(Math.abs(pRec0.value-pRec10.value)<1.0E-5);
		
		assertTrue(eleRelay.getReconnectStatus());
		assertTrue(!contactor.getReconnectStatus());
		assertTrue(!ems.getReconnectStatus());
		assertTrue(!olProtection.getReconnectStatus());
		
		/* withstand the first dip but tripped for the second dip
		 * time, power
		 *  0.1800,    0.81210,
            0.1850,    0.00000,
         *  
         *   0.3100,    0.00000,
             0.3150,    0.41312,
		 */
//		assertTrue(pRec36.value-0.81 >0.0);
//		assertTrue(pRec37.value==0.0);
		

		
		
	}
	
	
	
private DStabilityNetwork create2BusSystem() throws InterpssException{
		
		DStabilityNetwork net = DStabObjectFactory.createDStabilityNetwork();
		net.setFrequency(60.0);
		
		// First bus is PQ Gen bus
		DStabBus bus1 = DStabObjectFactory.createDStabBus("Bus1", net);
		bus1.setName("Gen Bus");
		bus1.setBaseVoltage(1000);
		//bus1.setGenCode(AclfGenCode.GEN_PQ);
		bus1.setLoadCode(AclfLoadCode.CONST_P);

		bus1.setLoadPQ( new Complex(0.8,0.2));
		
		// Second bus is a Swing bus
		DStabBus bus2 = DStabObjectFactory.createDStabBus("Swing", net);
		bus2.setName("Swing Bus");
		bus2.setBaseVoltage(1000);
		bus2.setGenCode(AclfGenCode.SWING);
		AclfSwingBus swing = bus2.toSwingBus();
		//swing.setDesiredVoltMag(1.06, UnitType.PU);
		//swing.setDesiredVoltAng(0, UnitType.Deg);
		
		DStabGen gen = DStabObjectFactory.createDStabGen("G1");
		//gen.setGen(new Complex(0.8,0.6));
		gen.setSourceZ(new Complex(0,0.2));
		bus2.getContributeGenList().add(gen);
		gen.setDesiredVoltMag(1.02);
		
		EConstMachine mach = (EConstMachine)DStabObjectFactory.
				createMachine("Mach1", "Mach1", MachineType.ECONSTANT, net, "Swing", "G1");
		//DStabBus bus = net.getDStabBus("Gen");
		mach.setRating(100000, UnitType.mVA, net.getBaseKva());
		mach.setRatedVoltage(1000.0);
		mach.calMultiFactors();
		mach.setH(5.0);
		mach.setD(0.01);
		mach.setXd1(0.2);

		// a line branch connect the two buses
		DStabBranch branch = DStabObjectFactory.createDStabBranch("Bus1", "Swing", net);
		branch.setBranchCode(AclfBranchCode.LINE);
		branch.setZ(new Complex(0.0, 0.05));
		
		//set positive info only
		net.setPositiveSeqDataOnly(true);

	  	net.initContributeGenLoad();

		// run load flow
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethod.NR);
	  	algo.loadflow();
	  	
	  	// uncommet this line to see the net object states
  		//System.out.println(net.net2String());
	  	
		return net;
	}

}
