package org.interpss.core.dstab.dynLoad;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.interpss.core.dstab.mach.TestSetupBase;
import org.interpss.display.AclfOutFunc;
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
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.dynLoad.InductionMotor;
import com.interpss.dstab.dynLoad.impl.InductionMotorImpl;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineType;

public class TestInductionMotorModel extends TestSetupBase {
	
	@Test
	public void test_induction_Motor_dynModel()  throws InterpssException {
		DStabilityNetwork net = create2BusSystem();
		assertTrue(net.isLfConverged());
		
		DStabBus bus1 = net.getDStabBus("Bus1");
		
		InductionMotor indMotor= DStabObjectFactory.createInductionMotor(bus1,"1");
		

		indMotor.setXm(3.0);
		indMotor.setXl(0.07);
		indMotor.setRa(0.032);
		indMotor.setXr1(0.3);
		indMotor.setRr1(0.01);
//		indMotor.setXr2(0.3);
//		indMotor.setRr2(0.175);
		
		indMotor.setLoadPercent(50);
		indMotor.setMVABase(50);
		indMotor.setH(1.0);
		
		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.02);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
		//net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,0.0d,0.05),"3phaseFault@Bus5");
        
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus1"});
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
		

		//}
		//System.out.println(sm.toCSVString(sm.getMachAngleTable()));
		System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		System.out.println(sm.toCSVString(sm.getMachPeTable()));
		
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
		mach.setRating(100, UnitType.mVA, net.getBaseKva());
		mach.setRatedVoltage(1000.0);
		mach.calMultiFactors();
		mach.setH(5.0);
		mach.setD(0.01);
		mach.setXd1(0.2);

		// a line branch connect the two buses
		DStabBranch branch = DStabObjectFactory.createDStabBranch("Bus1", "Swing", net);
		branch.setBranchCode(AclfBranchCode.LINE);
		branch.setZ(new Complex(0.0, 0.1));
		
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
