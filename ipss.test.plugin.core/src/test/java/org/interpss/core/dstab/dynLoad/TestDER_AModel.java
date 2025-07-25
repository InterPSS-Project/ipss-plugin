package org.interpss.core.dstab.dynLoad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.interpss.core.dstab.mach.TestSetupBase;
import org.interpss.display.AclfOutFunc;
import org.interpss.dstab.dynLoad.DER_A_PosSeq;
import org.interpss.dstab.dynLoad.DynLoadCMPLDW;
import org.interpss.dstab.dynLoad.DynLoadCMPLDWG;
import org.interpss.dstab.dynLoad.impl.DER_A_PosSeqImpl;
import org.interpss.dstab.dynLoad.impl.DynLoadCMPLDWGImpl;
import org.interpss.dstab.dynLoad.impl.DynLoadCMPLDWImpl;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.sc.ScBusModelType;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;

public class TestDER_AModel extends TestSetupBase {

	@Test
	public void test_DER_A_2Bus() throws InterpssException {
		DStabilityNetwork net = create2BusSystem();

		DStabBus bus1 = (DStabBus) net.getDStabBus("Bus1");
		// total net load is 0.8 + j*0.2
		Complex dgPower = new Complex(0.2, 0.0);

		//DER_A model

		DStabGen gen1 = new DStabObjectFactory().createDStabGen("DER_A"); // create generator instance
		gen1.setParentBus(bus1);
		gen1.setId("DER_A_"+bus1.getId());
		gen1.setGen(dgPower);
		gen1.setMvaBase(dgPower.getReal()* 100.0*1.1); // 10% extra
		gen1.setSourceZ(new Complex(0,0.25));
		gen1.setPosGenZ(new Complex(0,0.25));
		gen1.setNegGenZ(new Complex(0,0.25));
		gen1.setZeroGenZ(new Complex(0,0.25));
		bus1.getContributeGenList().add(gen1);
		bus1.setGenCode(AclfGenCode.GEN_PQ);

		DER_A_PosSeq der = new DER_A_PosSeqImpl(gen1,bus1, "1");
		der.setDebugMode(true);
		der.outputInternalStatesDuringSim(true);
	
		//net.initDStabNet();

		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(net));

		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.1);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
		// net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", net, SimpleFaultCode.GROUND_3P, 0.5d, 0.1),
		// 		"3phaseFault@Bus5");

		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[] { "Swing-mach1" });
		sm.addBusStdMonitor(new String[] { "Bus1" });
		// extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getDStabBus().getId();
		sm.addDynDeviceMonitor(DynDeviceType.PVGen, "DER_A_1@Bus1");
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutputPerSteps(1);

		IpssLogger.getLogger().setLevel(Level.FINE);

		
		if (dstabAlgo.initialization()) {
	
		
			System.out.println("Running DStab simulation ...");
			//System.out.println(dsNet.getMachineInitCondition());
			dstabAlgo.performSimulation();
		

		}
//		System.out.println(sm.toCSVString(sm.getMachPeTable()));
		System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		System.out.println(sm.toCSVString(sm.getPvGenPTable()));
		System.out.println(sm.toCSVString(sm.getPvGenQTable()));

		assertTrue(Math.abs(sm.getBusVoltTable().get("Bus1").get(0).value-1.00966)<1.0E-4);
		assertTrue(Math.abs(sm.getBusVoltTable().get("Bus1").get(20).value-1.00966)<1.0E-4);

		assertTrue(Math.abs(sm.getPvGenPTable().get("DER_A_1@Bus1").get(0).value-0.2)<1.0E-4);
		assertTrue(Math.abs(sm.getPvGenPTable().get("DER_A_1@Bus1").get(20).value-0.2)<1.0E-4);

		assertTrue(Math.abs(sm.getPvGenQTable().get("DER_A_1@Bus1").get(0).value-0.0)<1.0E-4);
		assertTrue(Math.abs(sm.getPvGenQTable().get("DER_A_1@Bus1").get(20).value-0.0)<1.0E-4);

	}

	@Test
	public void test_DER_A() throws InterpssException{

		DStabilityNetwork net = create2BusSystem();

		DStabBus bus1 = (DStabBus) net.getDStabBus("Bus1");
		// total net load is 0.8 + j*0.2
		Complex dgPower = new Complex(0.2, 0.0);

		//DER_A model

		DStabGen gen1 = new DStabObjectFactory().createDStabGen("DER_A"); // create generator instance
		gen1.setParentBus(bus1);
		gen1.setId("DER_A_"+bus1.getId());
		gen1.setGen(dgPower);
		gen1.setMvaBase(dgPower.getReal()* 100.0*1.1); // 10% extra
		gen1.setSourceZ(new Complex(0,0.25));
		gen1.setPosGenZ(new Complex(0,0.25));
		gen1.setNegGenZ(new Complex(0,0.25));
		gen1.setZeroGenZ(new Complex(0,0.25));
		bus1.getContributeGenList().add(gen1);
		bus1.setGenCode(AclfGenCode.GEN_PQ);

		DER_A_PosSeq der = new DER_A_PosSeqImpl(gen1,bus1, "1");


		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());

		System.out.println(AclfOutFunc.loadFlowSummary(net));

		net.initDStabNet();


		der.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER,0);
		der.getOutputObject();
		der.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER,1);
		der.getOutputObject();
		der.afterStep(0.005);
		der.updateAttributes(false);

		
		
		der.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER,0);
		der.getOutputObject();
		der.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER,1);
		der.getOutputObject();
		der.afterStep(0.005);
		der.updateAttributes(false);
	
		
		der.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, 0);
		der.getOutputObject();
		der.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, 1);
		der.getOutputObject();
		der.afterStep(0.005);
		der.updateAttributes(false);
		der.getOutputObject();
		
		der.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER,0);
		der.getOutputObject();
		der.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER,1);
		der.getOutputObject();
		der.afterStep(0.005);
		der.updateAttributes(false);
		der.getOutputObject();
		
		der.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER,0);
		der.getOutputObject();
		der.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER,1);
		der.getOutputObject();
		der.afterStep(0.005);
		der.updateAttributes(false);
		der.getOutputObject();
		
		der.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER,0);
		der.getOutputObject();
		der.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER,1);
		der.getOutputObject();
		der.afterStep(0.005);
		der.updateAttributes(false);

		assertEquals(0.2, der.getPosSeqGenPQ().getReal(), 0.0001);
	}


}
