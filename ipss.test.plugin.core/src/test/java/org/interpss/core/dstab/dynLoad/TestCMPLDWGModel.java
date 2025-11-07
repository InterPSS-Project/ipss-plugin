package org.interpss.core.dstab.dynLoad;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.core.dstab.mach.TestSetupBase;
import org.interpss.dstab.dynLoad.DynLoadCMPLDWG;
import org.interpss.dstab.dynLoad.impl.DynLoadCMPLDWGImpl;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;

public class TestCMPLDWGModel extends TestSetupBase {

	@Test
	public void test_CMPLDWG_Single_Model() throws InterpssException {
		DStabilityNetwork net = create2BusSystem();


		DStabBus bus1 = (DStabBus) net.getDStabBus("Bus1");
		// total net load is 0.8 + j*0.2
		Complex dgPower = new Complex(0.2, 0.0);

		DynLoadCMPLDWG cmpldwg = new DynLoadCMPLDWGImpl("CMPLDWG_1", bus1, dgPower);

		cmpldwg.setId("CMPLDWG_1");

		cmpldwg.setMvaBase(100);
		
		cmpldwg.getDistEquivalent().setBSubStation(0.04);
		
		cmpldwg.getDistEquivalent().setRFdr(0.04);
		
		cmpldwg.getDistEquivalent().setXFdr(0.04);
		
		cmpldwg.getDistEquivalent().setFB(0.0);
		
		cmpldwg.getDistEquivalent().setXXf(0.06);
		
		cmpldwg.getDistEquivalent().setTFixHS(1);
		
		cmpldwg.getDistEquivalent().setTFixLS(1);
		
		cmpldwg.getDistEquivalent().setLTC(1);
		
		cmpldwg.getDistEquivalent().setTMin(0.9);
		
		cmpldwg.getDistEquivalent().setTMax(1.1);
		
		cmpldwg.getDistEquivalent().setStep(0.00625);
		
		cmpldwg.getDistEquivalent().setVMin(1.0);
		
		cmpldwg.getDistEquivalent().setVMax(1.04);
		
		cmpldwg.getDistEquivalent().setTDelay(30);
		
		cmpldwg.getDistEquivalent().setTTap(5);
		
		cmpldwg.getDistEquivalent().setRComp(0);
		
		cmpldwg.getDistEquivalent().setXComp(0);
		
		// load percentages of the dynamic load component
		
		cmpldwg.setFmA(0.0);
		cmpldwg.setFmB(0.0);
		cmpldwg.setFmC(0.0);
		cmpldwg.setFmD(0.3);
		cmpldwg.setFel(0.0);
		
		// motor types
		cmpldwg.setMotorTypeA(3);
		cmpldwg.setMotorTypeB(3);
		cmpldwg.setMotorTypeC(3);
		cmpldwg.setMotorTypeD(1);
		
		// Electronic loads
		//TODO
		
		
//		// Motor A 
//		//cmpldw.setFmA(cmpldwXml.getFma());
//		cmpldw.getInductionMotorA().setLoadPercent(0.1 * 100);
//		cmpldw.getInductionMotorA().setLoadFactor(0.8);
//		cmpldw.getInductionMotorA().setRa(0.04);  //Stator resistor
//		cmpldw.getInductionMotorA().setXs(3.07); // Synchronous reactance
//		cmpldw.getInductionMotorA().setXp(0.3427); // Transient reactance
//		//cmpldw.getInductionMotorA().setXpp(0.104); // Sub-Transient reactance
//		cmpldw.getInductionMotorA().setTp0(0.875352); // Transient open circuit time constant
//		//cmpldw.getInductionMotorA().setTpp0(0.0021); // Sub-Transient open circuit time constant
//		cmpldw.getInductionMotorA().setH(0.1); // 
//		cmpldw.getInductionMotorA().setA(0.0);
//		cmpldw.getInductionMotorA().setB(0.0);
//		cmpldw.getInductionMotorA().setC(1.0); // assuming Etrq = 2.0; since Tm = (A+B*W+C*W^2)*Tm0
		
//		cmpldw.getInductionMotorA().setXm(3.0);
//		cmpldw.getInductionMotorA().setXl(0.07);
//		cmpldw.getInductionMotorA().setRa(0.04);
//		cmpldw.getInductionMotorA().setXr1(0.3);
//		cmpldw.getInductionMotorA().setRr1(0.01);
//		cmpldw.getInductionMotorA().setMvaBase(0.1 * 100 * mvaRating);
//		cmpldw.getInductionMotorA().setH(0.3);
//		cmpldw.getInductionMotorA().setA(0.0);
//		cmpldw.getInductionMotorA().setB(0.0);
//		cmpldw.getInductionMotorA().setC(1.0);
		
		
		
		// Motor B
		
//		cmpldw.getInductionMotorB().setLoadPercent(0.1 * 100);
//		cmpldw.getInductionMotorB().setLoadFactor(0.8);
//		cmpldw.getInductionMotorB().setRa(0.04);  //Stator resistor
//		cmpldw.getInductionMotorB().setXs(3.07); // Synchronous reactance
//		cmpldw.getInductionMotorB().setXp(0.3427); // Transient reactance
//		//cmpldw.getInductionMotorB().setXpp(0.14); // Sub-Transient reactance
//		cmpldw.getInductionMotorB().setTp0(0.875352); // Transient open circuit time constant
//		//cmpldw.getInductionMotorB().setTpp0(0.0026); // Sub-Transient open circuit time constant
//		cmpldw.getInductionMotorB().setH(0.5); // 
//		cmpldw.getInductionMotorB().setA(0.0);
//		cmpldw.getInductionMotorB().setB(0.0);
//		cmpldw.getInductionMotorB().setC(1.0); // assuming Etrq = 2.0; since Tm = (A+B*W+C*W^2)*Tm0
//		// all the protections are not implemented at this stage.
		
//		cmpldw.getInductionMotorB().setXm(3.0);
//		cmpldw.getInductionMotorB().setXl(0.07);
//		cmpldw.getInductionMotorB().setRa(0.04);
//		cmpldw.getInductionMotorB().setXr1(0.3);
//		cmpldw.getInductionMotorB().setRr1(0.01);
//		cmpldw.getInductionMotorB().setMvaBase(0.1 * 100 * mvaRating);
//		cmpldw.getInductionMotorB().setH(0.3);
//		cmpldw.getInductionMotorB().setA(0.0);
//		cmpldw.getInductionMotorB().setB(0.0);
//		cmpldw.getInductionMotorB().setC(1.0);
		
		
		
		// Motor C
		
		
//		cmpldw.getInductionMotorC().setLoadPercent(0.1 * 100);
//		cmpldw.getInductionMotorC().setLoadFactor(0.8);
//		cmpldw.getInductionMotorC().setRa(0.04);  //Stator resistor
//		cmpldw.getInductionMotorC().setXs(3.07); // Synchronous reactance
//		cmpldw.getInductionMotorC().setXp(0.3427); // Transient reactance
//		//cmpldw.getInductionMotorC().setXpp(0.14); // Sub-Transient reactance
//		cmpldw.getInductionMotorC().setTp0(0.875352); // Transient open circuit time constant
//		//cmpldw.getInductionMotorC().setTpp0(0.0026); // Sub-Transient open circuit time constant
//		cmpldw.getInductionMotorC().setH(0.1); // ppp
//		cmpldw.getInductionMotorC().setA(0.0);
//		cmpldw.getInductionMotorC().setB(0.0);
//		cmpldw.getInductionMotorC().setC(1.0); // assuming Etrq = 2.0; since Tm = (A+B*W+C*W^2)*Tm0
//		// all the protections are not implemented at this stage.
		
//		cmpldw.getInductionMotorC().setXm(3.0);
//		cmpldw.getInductionMotorC().setXl(0.07);
//		cmpldw.getInductionMotorC().setRa(0.04);
//		cmpldw.getInductionMotorC().setXr1(0.3);
//		cmpldw.getInductionMotorC().setRr1(0.01);
//		cmpldw.getInductionMotorC().setMvaBase(0.1 * 100 * mvaRating);
//		cmpldw.getInductionMotorC().setH(0.3);
//		cmpldw.getInductionMotorC().setA(0.0);
//		cmpldw.getInductionMotorC().setB(0.0);
//		cmpldw.getInductionMotorC().setC(1.0);
		
		
		
		// Motor D - single phase induction motor
		cmpldwg.get1PhaseACMotor().setId("1");
		cmpldwg.get1PhaseACMotor().setLoadPercent(0.3 * 100);
		//cmpldw.get1PhaseACMotor().setPowerFactor(0.98);
		cmpldwg.get1PhaseACMotor().setVstall(0.55);
		cmpldwg.get1PhaseACMotor().setRstall(0.1);
		cmpldwg.get1PhaseACMotor().setXstall(0.1);
		cmpldwg.get1PhaseACMotor().setTstall(0.033);
		cmpldwg.get1PhaseACMotor().setFrst(0.2);
		cmpldwg.get1PhaseACMotor().setVrst(0.95);
		cmpldwg.get1PhaseACMotor().setTrst(0.3);
		cmpldwg.get1PhaseACMotor().setFuvr(0.1);
		cmpldwg.get1PhaseACMotor().setUVtr1(0.6);
		cmpldwg.get1PhaseACMotor().setTtr1(0.02);
		cmpldwg.get1PhaseACMotor().setUVtr2(1);
		cmpldwg.get1PhaseACMotor().setTtr2(9999);
		cmpldwg.get1PhaseACMotor().setVc1off(0.5);
		cmpldwg.get1PhaseACMotor().setVc2off(0.4);
		cmpldwg.get1PhaseACMotor().setVc1on(0.6);
		cmpldwg.get1PhaseACMotor().setVc2on(0.5);
		cmpldwg.get1PhaseACMotor().setTth(15);
		cmpldwg.get1PhaseACMotor().setTh1t(0.7);
		cmpldwg.get1PhaseACMotor().setTh2t(1.9);

		//DER_A model

		//net.initDStabNet();

		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		// System.out.println(AclfOutFunc.loadFlowSummary(net));

		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(1);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
		// net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", net, SimpleFaultCode.GROUND_3P, 0.5d, 0.1),
		// 		"3phaseFault@Bus5");

		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[] { "Swing-mach1" });
		sm.addBusStdMonitor(new String[] { "Bus1" });
		// extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getDStabBus().getId();
		sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus1_loadBus");
		sm.addDynDeviceMonitor(DynDeviceType.PVGen, "DER_A_1@Bus1_loadBus");
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(5);

		//IpssLogger.getLogger().setLevel(Level.FINE);

		
		if (dstabAlgo.initialization()) {
	
		
			System.out.println("Running DStab simulation ...");
			//System.out.println(dsNet.getMachineInitCondition());
			dstabAlgo.performSimulation();
		

		}
//		System.out.println(sm.toCSVString(sm.getMachPeTable()));
		System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
		System.out.println(sm.toCSVString(sm.getAcMotorQTable()));

		assertTrue(Math.abs(sm.getBusVoltTable().get("Bus1").get(0).value-1.00932)<1.0E-4);
		assertTrue(Math.abs(sm.getBusVoltTable().get("Bus1").get(20).value-1.00932)<1.0E-4);

		assertTrue(Math.abs(sm.getPvGenPTable().get("DER_A_1@Bus1_loadBus").get(0).value-0.2)<1.0E-4);
		assertTrue(Math.abs(sm.getPvGenPTable().get("DER_A_1@Bus1_loadBus").get(20).value-0.2)<1.0E-4);

		assertTrue(Math.abs(sm.getPvGenQTable().get("DER_A_1@Bus1_loadBus").get(0).value-0.0)<1.0E-4);
		assertTrue(Math.abs(sm.getPvGenQTable().get("DER_A_1@Bus1_loadBus").get(20).value-0.0)<1.0E-4);


	}


}
