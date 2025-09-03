package org.interpss.threePhase.dataparser;

import static org.interpss.threePhase.util.ThreePhaseUtilFunction.threePhaseGenAptr;
import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.odm.mapper.ODMDStabParserMapper;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.algo.DynamicEventProcessor3Phase;
import org.interpss.threePhase.dynamic.model.DStabGen3PhaseAdapter;
import org.interpss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.interpss.threePhase.util.ThreePhaseAclfOutFunc;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.Bus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class TestODM3PhaseDstabMapper {

	//@Test
	public void test_IEEE9Bus_3phase_Dstab() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();

		//System.out.println(parser.toXmlDoc());



		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);

		// The only change to the normal data import is the use of ODM3PhaseDStabParserMapper
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}


	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();


	    // build sequence network
//	    SequenceNetworkBuilder seqNetHelper = new SequenceNetworkBuilder(dsNet,true);
//	    seqNetHelper.buildSequenceNetwork(SequenceCode.NEGATIVE);
//	    seqNetHelper.buildSequenceNetwork(SequenceCode.ZERO);
//
//	    System.out.println(dsNet.net2String());

		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));

		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(10);


		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));

		//applied the event
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,new Complex(0.0),null,1.0d,0.05),"3phaseFault@Bus5");


		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus1"});

		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));

		//IpssLogger.getLogger().setLevel(Level.WARNING);

		PerformanceTimer timer = new PerformanceTimer();

        // Must use this dynamic event process to modify the YMatrixABC
		dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());

		if (dstabAlgo.initialization()) {
			System.out.println(ThreePhaseAclfOutFunc.busLfSummary(dsNet));

			System.out.println(dsNet.getMachineInitCondition());

			System.out.println("Running 3Phase DStab simulation ...");
			timer.start();
			//dstabAlgo.performSimulation();

			while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){

				dstabAlgo.solveDEqnStep(true);

				for( Bus b : dsNet.getBusList()) {
					DStab3PBus bus = (DStab3PBus)b;

					if(bus.isActive()){

						Complex3x1 iInject = new Complex3x1();

						if(bus.getContributeGenList().size()>0){
							 for(Object obj: bus.getContributeGenList()){
								 AclfGen gen = (AclfGen)obj;
							      if(gen.isActive() && gen instanceof DStabGen){
							    	  DStabGen dynGen = (DStabGen)gen;
							    	  if( dynGen.getMach()!=null){
							    		  DStabGen3PhaseAdapter gen3P = threePhaseGenAptr.apply(dynGen);
							    		  iInject = iInject.add(gen3P.getIinj2Network3Phase());


							    	 // if(bus.getId().equals("Bus2"))
							    	     // System.out.println("t, bus, Ia, Ib, Ic,"+dstabAlgo.getSimuTime()+","+bus.getId()+","+iInject.a_0.abs()+","+iInject.b_1.abs()+","+iInject.c_2.abs());
							    		//  System.out.println("t, bus, Ia, Ib, Ic,"+dstabAlgo.getSimuTime()+","+bus.getId()+","+iInject.a_0+","+iInject.b_1+","+iInject.c_2);
							    	  }
							       }
							  }
					    }
			}


			}
		}

			timer.logStd("total simu time: ");

		}

//		System.out.println(sm.toCSVString(sm.getBusAngleTable()));
//		System.out.println(sm.toCSVString(sm.getBusVoltTable()));

	   // System.out.println(sm.toCSVString(sm.getMachPeTable()));

//		FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//comprehensive_ch7//ieee9_3Phase_SLG@bus5_machAngle.csv",sm.toCSVString(sm.getMachAngleTable()));
//		FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//comprehensive_ch7//ieee9_3Phase_SLG@bus5_machSpd.csv",sm.toCSVString(sm.getMachSpeedTable()));
//		FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//comprehensive_ch7//ieee9_3Phase_SLG@bus5_busVolt.csv",sm.toCSVString(sm.getBusVoltTable()));
		//		FileUtil.writeText2File("output/ieee9_bus5_machAngle_v5_03172015.csv",sm.toCSVString(sm.getMachAngleTable()));
//		FileUtil.writeText2File("output/ieee9_bus5_machSpd_v5_03172015.csv",sm.toCSVString(sm.getMachSpeedTable()));
//		FileUtil.writeText2File("output/ieee9_bus5_busVolt_v5_03172015.csv",sm.toCSVString(sm.getBusVoltTable()));
//
	}


	@Test
	public void test_IEEE9Bus_posSeq_Dstab() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();

		//System.out.println(parser.toXmlDoc());



		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}


	    BaseDStabNetwork dsNet =simuCtx.getDStabilityNet();


	    // build sequence network
//	    SequenceNetworkBuilder seqNetHelper = new SequenceNetworkBuilder(dsNet,true);
//	    seqNetHelper.buildSequenceNetwork(SequenceCode.NEGATIVE);
//	    seqNetHelper.buildSequenceNetwork(SequenceCode.ZERO);
//
//	    System.out.println(dsNet.net2String());

		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));

		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(10);

		dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,new Complex(0.0),null,1.0d,0.05),"3phaseFault@Bus5");


		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus7"});
		// set the output handler
				dstabAlgo.setSimuOutputHandler(sm);
				dstabAlgo.setOutPutPerSteps(1);

		//IpssLogger.getLogger().setLevel(Level.INFO);

		PerformanceTimer timer = new PerformanceTimer();


		if (dstabAlgo.initialization()) {
			System.out.println(dsNet.getMachineInitCondition());

			System.out.println("Running DStab simulation ...");
			timer.start();
			dstabAlgo.performSimulation();

			timer.logStd("total simu time: ");
		}



		//System.out.println(sm.toCSVString(sm.getMachAngleTable()));

	     System.out.println(sm.toCSVString(sm.getBusVoltTable()));

//			FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//comprehensive_ch7//ieee9_pos_SLG@bus5_machAngle.csv",sm.toCSVString(sm.getMachAngleTable()));
//			FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//comprehensive_ch7//ieee9_pos_SLG@bus5_machSpd.csv",sm.toCSVString(sm.getMachSpeedTable()));
//			FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//comprehensive_ch7//ieee9_pos_SLG@bus5_busVolt.csv",sm.toCSVString(sm.getBusVoltTable()));

//		FileUtil.writeText2File("output/ieee9_bus5_machPe_v5_03172015.csv",sm.toCSVString(sm.getMachPeTable()));
//		FileUtil.writeText2File("output/ieee9_bus5_machAngle_v5_03172015.csv",sm.toCSVString(sm.getMachAngleTable()));
//		FileUtil.writeText2File("output/ieee9_bus5_machSpd_v5_03172015.csv",sm.toCSVString(sm.getMachSpeedTable()));
//		FileUtil.writeText2File("output/ieee9_bus5_busVolt_v5_03172015.csv",sm.toCSVString(sm.getBusVoltTable()));
//
	}

}
