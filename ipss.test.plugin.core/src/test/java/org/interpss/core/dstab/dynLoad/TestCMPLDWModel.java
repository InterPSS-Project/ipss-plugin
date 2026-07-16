package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.mach.TestSetupBase;
import org.interpss.dstab.dynLoad.DynLoadCMPLDW;
import org.interpss.dstab.dynLoad.impl.DynLoadCMPLDWImpl;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.simu.SimuContext;

public class TestCMPLDWModel extends TestSetupBase {


	//@Test
	public void test_CMPLDW_Single_Model() throws InterpssException {
		// create a machine in a two-bus network. The loadflow already converged
		DStabilityNetwork net = create2BusSystem();
		assertTrue(net.isLfConverged());

		DStabBus bus1 = (DStabBus) net.getDStabBus("Bus1");

		DynLoadCMPLDW cmpldw = new DynLoadCMPLDWImpl("CMPLDW_1", bus1);

		cmpldw.setId("CMPLDW_1");

		cmpldw.setMvaBase(100);
		
		cmpldw.getDistEquivalent().setBSubStation(0.04);
		
		cmpldw.getDistEquivalent().setRFdr(0.04);
		
		cmpldw.getDistEquivalent().setXFdr(0.04);
		
		cmpldw.getDistEquivalent().setFB(0.0);
		
		cmpldw.getDistEquivalent().setXXf(0.06);
		
		cmpldw.getDistEquivalent().setTFixHS(1);
		
		cmpldw.getDistEquivalent().setTFixLS(1);
		
		cmpldw.getDistEquivalent().setLTC(1);
		
		cmpldw.getDistEquivalent().setTMin(0.9);
		
		cmpldw.getDistEquivalent().setTMax(1.1);
		
		cmpldw.getDistEquivalent().setStep(0.00625);
		
		cmpldw.getDistEquivalent().setVMin(1.0);
		
		cmpldw.getDistEquivalent().setVMax(1.04);
		
		cmpldw.getDistEquivalent().setTDelay(30);
		
		cmpldw.getDistEquivalent().setTTap(5);
		
		cmpldw.getDistEquivalent().setRComp(0);
		
		cmpldw.getDistEquivalent().setXComp(0);
		
		// load percentages of the dynamic load component
		
		cmpldw.setFmA(0.0);
		cmpldw.setFmB(0.0);
		cmpldw.setFmC(0.0);
		cmpldw.setFmD(0.3);
		cmpldw.setFel(0.0);
		
		// motor types
		cmpldw.setMotorTypeA(3);
		cmpldw.setMotorTypeB(3);
		cmpldw.setMotorTypeC(3);
		cmpldw.setMotorTypeD(1);
		
		// Motor D - single phase induction motor
		cmpldw.get1PhaseACMotor().setLoadPercent(0.3 * 100);
		cmpldw.get1PhaseACMotor().setVstall(0.55);
		cmpldw.get1PhaseACMotor().setRstall(0.1);
		cmpldw.get1PhaseACMotor().setXstall(0.1);
		cmpldw.get1PhaseACMotor().setTstall(0.033);
		cmpldw.get1PhaseACMotor().setFrst(0.2);
		cmpldw.get1PhaseACMotor().setVrst(0.95);
		cmpldw.get1PhaseACMotor().setTrst(0.3);
		cmpldw.get1PhaseACMotor().setFuvr(0.1);
		cmpldw.get1PhaseACMotor().setUVtr1(0.6);
		cmpldw.get1PhaseACMotor().setTtr1(0.02);
		cmpldw.get1PhaseACMotor().setUVtr2(1);
		cmpldw.get1PhaseACMotor().setTtr2(9999);
		cmpldw.get1PhaseACMotor().setVc1off(0.5);
		cmpldw.get1PhaseACMotor().setVc2off(0.4);
		cmpldw.get1PhaseACMotor().setVc1on(0.6);
		cmpldw.get1PhaseACMotor().setVc2on(0.5);
		cmpldw.get1PhaseACMotor().setTth(15);
		cmpldw.get1PhaseACMotor().setTh1t(0.7);
		cmpldw.get1PhaseACMotor().setTh2t(1.9);

		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());

		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(1);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));

		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[] { "Swing-mach1" });
		sm.addBusStdMonitor(new String[] { "Bus1" });
		sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_CMPLDW_1@Bus1_loadBus");
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(5);

		
		if (dstabAlgo.initialization()) {
	
		
			System.out.println("Running DStab simulation ...");
			dstabAlgo.performSimulation();
		

		}
		System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
		System.out.println(sm.toCSVString(sm.getAcMotorQTable()));

		assertTrue(Math.abs(sm.getBusVoltTable().get("Bus1").get(0).value-1.00932)<1.0E-4);
		assertTrue(Math.abs(sm.getBusVoltTable().get("Bus1").get(20).value-1.00932)<1.0E-4);

		assertTrue(Math.abs(sm.getAcMotorPTable().get("ACMotor_CMPLDW_1@Bus1_loadBus").get(0).value-0.23211)<1.0E-4);
		assertTrue(Math.abs(sm.getAcMotorPTable().get("ACMotor_CMPLDW_1@Bus1_loadBus").get(20).value-0.23211)<1.0E-4);

		assertTrue(Math.abs(sm.getAcMotorQTable().get("ACMotor_CMPLDW_1@Bus1_loadBus").get(0).value-0.05817)<1.0E-4);
		assertTrue(Math.abs(sm.getAcMotorQTable().get("ACMotor_CMPLDW_1@Bus1_loadBus").get(20).value-0.05817)<1.0E-4);


	}
	
	//@Test
	public void testCMPLDWInit() throws InterpssException{
		IpssCorePlugin.init();
		SimuContext simuCtx = new PSSEMultiFileLoader(30).loadDStab(
				"testData/adpter/psse/v30/threeBus_cmpldw.raw",
				"testData/adpter/psse/v30/threeBus_cmpldw.dyr");
		
		
	    DStabilityNetwork dsNet =(DStabilityNetwork) simuCtx.getDStabilityNet();
	    
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(dsNet);
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  	
  		assertTrue( dsNet.isLfConverged());
	    
	}
	
	// NOTE: testCMPLDWPSLFData and test_CMPLDW_init_methods removed -
	// they depend on ODM DStab pipeline (GenericODMAdapter + DStabModelParser + ODMDStabParserMapper)
	// which are being removed as part of the ODM dependency migration.

	
}
