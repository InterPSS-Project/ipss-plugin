package org.interpss.core.dstab.dynLoad;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.interpss.core.dstab.mach.TestSetupBase;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
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
import com.interpss.dstab.dynLoad.LD1PAC;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineModelType;

public class TestLd1pacModel extends TestSetupBase {
	
	//@Test
	public void test_DStab_Ld1pac()  throws InterpssException {
		DStabilityNetwork net = create2BusSystem();
		assertTrue(net.isLfConverged());
		
		DStabBus bus1 = (DStabBus) net.getDStabBus("Bus1");
		
		LD1PAC acLoad= DStabObjectFactory.createLD1PAC(bus1,"1");
		
		acLoad.setLoadPercent(50.0);
		acLoad.setMVABase(50);
		
		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(1);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
		//net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,0.0d,0.05),"3phaseFault@Bus5");
        
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus1"});
		//extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getDStabBus().getId();
		sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus1");
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(5);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		
		if (dstabAlgo.initialization()) {
			System.out.println(net.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
		    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec())
			     dstabAlgo.solveDEqnStep(true);
			
			}
		

		//}
		//System.out.println(sm.toCSVString(sm.getMachAngleTable()));
		System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		System.out.println(sm.toCSVString(sm.getMachPeTable()));
		System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
		System.out.println(sm.toCSVString(sm.getAcMotorQTable()));
		// Total load = 0.8 pu on system base, AC motor 50% ->0.4 pu
		assertTrue(Math.abs(sm.getAcMotorPTable().get("ACMotor_1@Bus1").get(20).value-0.400)<1.0E-4);
		assertTrue(Math.abs(sm.getAcMotorQTable().get("ACMotor_1@Bus1").get(20).value-0.10025)<1.0E-4);
	}
	
	@Test
	public void compare_DStab_Ld1pac_with_PSCAD_Model()  throws InterpssException {
		DStabilityNetwork net = create2BusSystem();
		assertTrue(net.isLfConverged());
		
		DStabBus bus1 = (DStabBus) net.getDStabBus("Bus1");
		
		LD1PAC acLoad= DStabObjectFactory.createLD1PAC(bus1,"1");
		
		acLoad.setLoadPercent(100.0);
		acLoad.setMVABase(100);
		acLoad.setPowerFactor(0.94);
		
		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(2);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
		//net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,0.0d,0.05),"3phaseFault@Bus5");
        
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus1"});
		//extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getDStabBus().getId();
		sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus1");
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(5);
		//faultBusId, net, code, zlg, zll, startTime, durationTime)
		net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Swing",net,SimpleFaultCode.GROUND_3P,new Complex(0.01,0), new Complex(0), 1.0d,0.08),"3phaseFault@Bus1");
        
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		
		if (dstabAlgo.initialization()) {
			System.out.println(net.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
		    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec())
			     dstabAlgo.solveDEqnStep(true);
			
			}
		

		//}
		//System.out.println(sm.toCSVString(sm.getMachAngleTable()));
		//System.out.println("\nVac =\n"+sm.toCSVString(sm.getBusVoltTable()));
		//System.out.println(sm.toCSVString(sm.getMachPeTable()));
		//System.out.println("\nPac =\n"+sm.toCSVString(sm.getAcMotorPTable()));
		//System.out.println("\nQac =\n"+sm.toCSVString(sm.getAcMotorQTable()));
		// Total load = 0.8 pu on system base, AC motor 50% ->0.4 pu
		//assertTrue(Math.abs(sm.getAcMotorPTable().get("ACMotor_1@Bus1").get(20).value-0.400)<1.0E-4);
		//assertTrue(Math.abs(sm.getAcMotorQTable().get("ACMotor_1@Bus1").get(20).value-0.10025)<1.0E-4);
	}
	
	//@Test
	public void test_DStab_Ld1pac_loadChange()  throws InterpssException {
		DStabilityNetwork net = create2BusSystem();
		assertTrue(net.isLfConverged());
		
		DStabBus bus1 = (DStabBus) net.getDStabBus("Bus1");
		
		LD1PAC acLoad= DStabObjectFactory.createLD1PAC(bus1,"1");
		
		acLoad.setLoadPercent(50);
		acLoad.setMVABase(50);
		
		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(1);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
		//net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,0.0d,0.05),"3phaseFault@Bus5");
        
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus1"});
		//extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getDStabBus().getId();
		sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus1");
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
			    	acLoad.changeLoad(-0.1);
			     }
			     
			     if(dstabAlgo.getSimuTime()>=0.149 && dstabAlgo.getSimuTime()<0.151){
			    	 acLoad.changeLoad(0.2);
			     }
			
			}
		

		}
		
		//System.out.println(sm.toCSVString(sm.getMachAngleTable()));
		//System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		System.out.println("\n motor Real Power:\n"+sm.toCSVString(sm.getMachPeTable()));
		System.out.println("\n motor Real Power:\n"+sm.toCSVString(sm.getAcMotorPTable()));
		System.out.println("\n motor Reactive Power:\n"+sm.toCSVString(sm.getAcMotorQTable()));
		
	}
	//@Test
	public void test_Ld1pac()  throws InterpssException {
		// create a machine in a two-bus network. The loadflow already converged
		DStabilityNetwork net = create2BusSystem();
		assertTrue(net.isLfConverged());
		
		DStabBus bus1 = (DStabBus) net.getDStabBus("Bus1");
		
		LD1PAC acLoad= DStabObjectFactory.createLD1PAC(bus1,"1");
		
		acLoad.setLoadPercent(50);
		acLoad.setMVABase(50);
		
		
		net.initDStabNet();
		
		// check init load
		Complex loadPQ = new Complex(0.4,0.4*Math.tan(Math.acos(0.97)));
		assertTrue( NumericUtil.equals(acLoad.getInitLoadPQ(),loadPQ,1.0E-5));
		
		//check only 50% load left as static load
		Complex remainLoadPQ = new Complex(0.8,0.6).subtract(loadPQ);
		assertTrue( NumericUtil.equals(bus1.getNetLoadResults(),remainLoadPQ,1.0E-5));
		
		// correct equivY = 1/(0.124+j0.114)
		Complex y = new Complex(0.5,0).divide(new Complex(0.124,0.114));
		assertTrue( NumericUtil.equals(acLoad.getPosSeqEquivY(),y,1.0E-5));
		
		// check the calculated loadPQ
		System.out.println("ac pq ="+acLoad.getLoadPQ().toString());
		assertTrue(NumericUtil.equals(acLoad.getLoadPQ(),loadPQ,1.0E-5));
		
		 double v = 0.599;
		 bus1.setVoltageMag(0.599);
		 //Tstall = 0.033;
		 acLoad.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER);
		 acLoad.updateAttributes(false);
		 acLoad.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER);
		 acLoad.updateAttributes(false);
		 acLoad.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER);
		 acLoad.updateAttributes(false);
		 acLoad.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER);
		 acLoad.updateAttributes(false);
		 acLoad.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER);
		 acLoad.updateAttributes(false);
		 acLoad.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER);
		 acLoad.updateAttributes(false);

		 // check the power before stalling
		 System.out.println("before stalling ac pq ="+acLoad.getLoadPQ());
		 
		 // check the current injection compesation under normal running condition
		 Complex Iinj = acLoad.getLoadPQ().subtract(acLoad.getPosSeqEquivY().multiply(v*v).conjugate());
		 Iinj = Iinj.divide(bus1.getVoltage()).conjugate().multiply(-1.0);
		 assertTrue(acLoad.getNortonCurInj().subtract(Iinj).abs()<1.0E-6);
		 
		 
		 acLoad.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER);
		 acLoad.updateAttributes(false);
		 // check the stall status
		 assertTrue(acLoad.getStage() ==0);
		 
		 System.out.println("after stalling ac pq ="+acLoad.getLoadPQ());
		 
		 // check the current injection compesation under stalling condition
		 System.out.println("current inject after stall ="+acLoad.getNortonCurInj());
		 assertTrue(acLoad.getNortonCurInj().abs()<1.0E-8);
		
	}
	
	
	private DStabilityNetwork create2BusSystem() throws InterpssException{
		
		DStabilityNetwork net = DStabObjectFactory.createDStabilityNetwork();
		net.setFrequency(60.0);
		
		// First bus is PQ Gen bus
		DStabBus bus1 = (DStabBus) DStabObjectFactory.createDStabBus("Bus1", net);
		bus1.setName("Gen Bus");
		bus1.setBaseVoltage(1000);
		//bus1.setGenCode(AclfGenCode.GEN_PQ);
		bus1.setLoadCode(AclfLoadCode.CONST_P);

		bus1.setLoadPQ( new Complex(0.8,0.6));
		
		// Second bus is a Swing bus
		DStabBus bus2 = (DStabBus) DStabObjectFactory.createDStabBus("Swing", net);
		bus2.setName("Swing Bus");
		bus2.setBaseVoltage(1000);
		bus2.setGenCode(AclfGenCode.SWING);
		AclfSwingBus swing = bus2.toSwingBus();
		//swing.setDesiredVoltMag(1.06, UnitType.PU);
		//swing.setDesiredVoltAng(0, UnitType.Deg);
		
		DStabGen gen = DStabObjectFactory.createDStabGen("G1");
		//gen.setGen(new Complex(0.8,0.6));
		gen.setSourceZ(new Complex(0,0.1));
		bus2.getContributeGenList().add(gen);
		gen.setDesiredVoltMag(1.0285);
		
		EConstMachine mach = (EConstMachine)DStabObjectFactory.
				createMachine("MachId", "MachName", MachineModelType.ECONSTANT, net, "Swing", "G1");
		//DStabBus bus = net.getDStabBus("Gen");
		mach.setRating(1000, UnitType.mVA, net.getBaseKva());
		mach.setRatedVoltage(1000.0);
		mach.calMultiFactors();
		mach.setH(5.0);
		mach.setD(0.01);
		mach.setXd1(0.1);

		// a line branch connect the two buses
		DStabBranch branch = DStabObjectFactory.createDStabBranch("Bus1", "Swing", net);
		branch.setBranchCode(AclfBranchCode.LINE);
		branch.setZ(new Complex(0.0, 0.002));
		
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

