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
import com.interpss.dstab.dynLoad.impl.MotorElectronicRelayProtection;
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
		System.out.println("\n gen Pe:\n"+sm.toCSVString(sm.getMachPeTable()));
		System.out.println("\n motor slip:\n"+sm.toCSVString(sm.getMotorSlipTable()));
		System.out.println("\n motor Te:\n"+sm.toCSVString(sm.getMotorTeTable()));
		System.out.println("\n motor Tm:\n"+sm.toCSVString(sm.getMotorTmTable()));
		System.out.println("\n motor Power:\n"+sm.toCSVString(sm.getMotorPTable()));
		
		MonitorRecord pRec0 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(0);
		
		MonitorRecord pRec10 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(10);
		
		assertTrue(Math.abs(pRec0.value-pRec10.value)<1.0E-5);
	}
	
	
	@Test
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
		eleRelay.getTripVoltTimeCurve().getPoints().add(new Point(0.05,0.7)); // x-axis is time, y-axis is voltage
		
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
		System.out.println("\n motor Power:\n"+sm.toCSVString(sm.getMotorPTable()));
		
		MonitorRecord pRec0 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(0);
		
		MonitorRecord pRec10 = sm.getMotorPTable().get("IndMotor_1@Bus1").get(10);
		
		assertTrue(Math.abs(pRec0.value-pRec10.value)<1.0E-5);
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
				createMachine("MachId", "MachName", MachineType.ECONSTANT, net, "Swing", "G1");
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
