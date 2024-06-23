package org.interpss.core.dstab.mach;

import static org.junit.Assert.assertTrue;

import java.util.Comparator;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.simu.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class SMIB_Gen_Test extends TestSetupBase{
	
	@Test
	public void test_GENROU() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/SMIB/SMIB_v30.raw",
				"testData/adpter/psse/v30/SMIB/SMIB_v30_genrou.dyr"
				
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
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

	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(10);

		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus2-mach1"));
		
		//Bus fault
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",dsNet,SimpleFaultCode.GROUND_3P,new Complex(0.0),null,1.0d,0.05),"3phaseFault@Bus2");
        
		//generator tripping event 
		//dsNet.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus1", "1", dsNet, 1),"Bus1_Mach1_trip_1sec");
        
        
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus2","Bus1"});
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		//for(int i =1; i<20;i++){
			
			

		if (dstabAlgo.initialization()) {
			System.out.println(dsNet.getMachineInitCondition());
			
			//System.out.println("Running DStab simulation ...");
			timer.start();
			dstabAlgo.performSimulation();
			
			timer.logStd("total simu time: ");
			}
			//dstabAlgo.performOneStepSimulation();

		//}
		
		System.out.println("Mach Speed (pu):\n"+sm.toCSVString(sm.getMachSpeedTable()));
		System.out.println("Mach Angles (deg):\n"+sm.toCSVString(sm.getMachAngleTable()));
		
		System.out.println("Mach Pe (pu) :\n"+sm.toCSVString(sm.getMachPeTable()));
		
		System.out.println("Volages (pu):\n"+sm.toCSVString(sm.getBusVoltTable()));
		
//		System.out.println("Bus freq (pu):\n"+sm.toCSVString(sm.getBusFreqTable()));
		
		MonitorRecord maxAngleRec = sm.getMachAngleTable().get("Bus1-mach1").values().stream().max(Comparator.comparing(MonitorRecord:: getValue)).get();
		assertTrue(NumericUtil.equals(maxAngleRec.getValue(),25.338, 1.0e-1));
//		assertTrue(NumericUtil.equals(maxAngleRec.getTime(),1.315, 1.0e-3));
		assertTrue(NumericUtil.equals(sm.getMachAngleTable().get("Bus1-mach1").get(0).value, 22.98926,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachAngleTable().get("Bus1-mach1").get(1000).value, 23.328,1.0E-3));
		
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(0).value, 0.5,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(200).value, 0.5,1.0E-4));
		FileUtil.writeText2File("output/SMIB/GENROU_angle.csv",sm.toCSVString(sm.getMachAngleTable()));
		FileUtil.writeText2File("output/SMIB/GENROU_speed.csv",sm.toCSVString(sm.getMachSpeedTable()));
//		FileUtil.writeText2File("output/ieee9_bus5_busVolt_v5_03172015.csv",sm.toCSVString(sm.getBusVoltTable()));
		
		/*
		FileUtil.writeText2File("output/ieee9_bus5_machPe_v4_sat_03042015.csv",sm.toCSVString(sm.getMachPeTable()));
		FileUtil.writeText2File("output/ieee9_bus5_machAngle_v4_sat_03042015.csv",sm.toCSVString(sm.getMachAngleTable()));
		FileUtil.writeText2File("output/ieee9_bus5_machSpd_v4_sat_03042015.csv",sm.toCSVString(sm.getMachSpeedTable()));
		FileUtil.writeText2File("output/ieee9_bus5_busVolt_v3_sat_03042015.csv",sm.toCSVString(sm.getBusVoltTable()));
        */

	}
	
	@Test
	public void test_GENROU_Saturation() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/SMIB/SMIB_v30.raw",
				"testData/adpter/psse/v30/SMIB/SMIB_v30_genrou_sat.dyr"
				
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
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

	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(2);

		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus2-mach1"));
		
		//Bus fault
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",dsNet,SimpleFaultCode.GROUND_3P,new Complex(0.0),null,1.0d,0.05),"3phaseFault@Bus2");
        
		//generator tripping event 
		//dsNet.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus1", "1", dsNet, 1),"Bus1_Mach1_trip_1sec");
        
        
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus2","Bus1"});
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		//for(int i =1; i<20;i++){
			
			

		if (dstabAlgo.initialization()) {
			System.out.println(dsNet.getMachineInitCondition());
			
			//System.out.println("Running DStab simulation ...");
			timer.start();
			dstabAlgo.performSimulation();
			
			timer.logStd("total simu time: ");
			}
			//dstabAlgo.performOneStepSimulation();

		//}
		
		System.out.println("Mach Speed (pu):\n"+sm.toCSVString(sm.getMachSpeedTable()));
		System.out.println("Mach Angles (deg):\n"+sm.toCSVString(sm.getMachAngleTable()));
		
		System.out.println("Mach Pe (pu) :\n"+sm.toCSVString(sm.getMachPeTable()));
		
		System.out.println("Volages (pu):\n"+sm.toCSVString(sm.getBusVoltTable()));
		
//		System.out.println("Bus freq (pu):\n"+sm.toCSVString(sm.getBusFreqTable()));
		
		MonitorRecord maxAngleRec = sm.getMachAngleTable().get("Bus1-mach1").values().stream().max(Comparator.comparing(MonitorRecord:: getValue)).get();
		assertTrue(NumericUtil.equals(maxAngleRec.getValue(),23.44368, 1.0e-2));
//		assertTrue(NumericUtil.equals(maxAngleRec.getTime(),1.315, 1.0e-3));
		assertTrue(NumericUtil.equals(sm.getMachAngleTable().get("Bus1-mach1").get(0).value, 21.01872,1.0E-4));
		//assertTrue(NumericUtil.equals(sm.getMachAngleTable().get("Bus1-mach1").get(1000).value, 23.328,1.0E-3));
		
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(0).value, 0.5,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(200).value, 0.5,1.0E-4));
		FileUtil.writeText2File("output/SMIB/GENROU_angle.csv",sm.toCSVString(sm.getMachAngleTable()));
		FileUtil.writeText2File("output/SMIB/GENROU_speed.csv",sm.toCSVString(sm.getMachSpeedTable()));
//		FileUtil.writeText2File("output/ieee9_bus5_busVolt_v5_03172015.csv",sm.toCSVString(sm.getBusVoltTable()));
		
		/*
		FileUtil.writeText2File("output/ieee9_bus5_machPe_v4_sat_03042015.csv",sm.toCSVString(sm.getMachPeTable()));
		FileUtil.writeText2File("output/ieee9_bus5_machAngle_v4_sat_03042015.csv",sm.toCSVString(sm.getMachAngleTable()));
		FileUtil.writeText2File("output/ieee9_bus5_machSpd_v4_sat_03042015.csv",sm.toCSVString(sm.getMachSpeedTable()));
		FileUtil.writeText2File("output/ieee9_bus5_busVolt_v3_sat_03042015.csv",sm.toCSVString(sm.getBusVoltTable()));
        */

	}
	
	@Test
	public void test_GENROU_IEEEG1() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/SMIB/SMIB_v30.raw",
				"testData/adpter/psse/v30/SMIB/SMIB_v30_genrou_IEEEG1.dyr"
				
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
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

	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(10);

		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus2-mach1"));
		
		//Bus fault
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",dsNet,SimpleFaultCode.GROUND_3P,new Complex(0.0),null,1.0d,0.05),"3phaseFault@Bus2");
        
		//generator tripping event 
		//dsNet.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus1", "1", dsNet, 1),"Bus1_Mach1_trip_1sec");
        
        
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus2","Bus1"});
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		//for(int i =1; i<20;i++){
			
			

		if (dstabAlgo.initialization()) {
			System.out.println(dsNet.getMachineInitCondition());
			
			//System.out.println("Running DStab simulation ...");
			timer.start();
			dstabAlgo.performSimulation();
			
			timer.logStd("total simu time: ");
			}
			//dstabAlgo.performOneStepSimulation();

		//}
		
		System.out.println("Mach Speed (pu):\n"+sm.toCSVString(sm.getMachSpeedTable()));
		System.out.println("Mach Angles (deg):\n"+sm.toCSVString(sm.getMachAngleTable()));
		
		System.out.println("Mach Pe (pu) :\n"+sm.toCSVString(sm.getMachPeTable()));
		
		System.out.println("Volages (pu):\n"+sm.toCSVString(sm.getBusVoltTable()));
		
		System.out.println("Bus freq (pu):\n"+sm.toCSVString(sm.getBusFreqTable()));

		assertTrue(NumericUtil.equals(sm.getMachAngleTable().get("Bus1-mach1").get(0).value, 22.98926,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachAngleTable().get("Bus1-mach1").get(2000).value, 23.09754,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(0).value, 0.50001,1.0E-5));
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(2000).value, 0.50004,1.0E-5));
		FileUtil.writeText2File("output/SMIB/GENROU_IEEEG1_pm.csv",sm.toCSVString(sm.getMachPmTable()));
		FileUtil.writeText2File("output/SMIB/GENROU_IEEEG1_angle.csv",sm.toCSVString(sm.getMachAngleTable()));
		FileUtil.writeText2File("output/SMIB/GENROU_IEEEG1_speed.csv",sm.toCSVString(sm.getMachSpeedTable()));


	}
	
	@Test
	public void test_GENROU_IEEET1() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/SMIB/SMIB_v30.raw",
				"testData/adpter/psse/v30/SMIB/SMIB_v30_genrou_IEEET1.dyr"
				
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
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

	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(10);

		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus2-mach1"));
		
		//Bus fault
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",dsNet,SimpleFaultCode.GROUND_3P,new Complex(0.0,5.0E-9),null,0.1d,0.05),"3phaseFault@Bus2");
        
		//generator tripping event 
		//dsNet.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus1", "1", dsNet, 1),"Bus1_Mach1_trip_1sec");
        
        
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus2","Bus1"});
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		//for(int i =1; i<20;i++){
			
			

		if (dstabAlgo.initialization()) {
			System.out.println(dsNet.getMachineInitCondition());
			
			//System.out.println("Running DStab simulation ...");
			timer.start();
			while(dstabAlgo.getSimuTime()<dstabAlgo.getTotalSimuTimeSec()) {
				
				//System.out.println("----------------- t= \n\n"+dstabAlgo.getSimuTime());
			   dstabAlgo.solveDEqnStep(true);
			}
			
			timer.logStd("total simu time: ");
			}
			//dstabAlgo.performOneStepSimulation();

		//}
		
//		System.out.println("Mach Speed (pu):\n"+sm.toCSVString(sm.getMachSpeedTable()));
//		System.out.println("Mach Angles (deg):\n"+sm.toCSVString(sm.getMachAngleTable()));
//		
//		System.out.println("Mach Pe (pu) :\n"+sm.toCSVString(sm.getMachPeTable()));
//		
		System.out.println("Volages (pu):\n"+sm.toCSVString(sm.getBusVoltTable()));
//		
//		System.out.println("Bus freq (pu):\n"+sm.toCSVString(sm.getBusFreqTable()));
		
		assertTrue(NumericUtil.equals(sm.getMachEfdTable().get("Bus1-mach1").get(0).value, 1.105872869,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachEfdTable().get("Bus1-mach1").get(83).t, 0.40,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachEfdTable().get("Bus1-mach1").get(83).value, 1.73807,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachEfdTable().get("Bus1-mach1").get(2000).value, 1.10455,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachEfdTable().get("Bus1-mach1").get(2000).value, 1.10455,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(0).value, 0.50001,1.0E-5));

		FileUtil.writeText2File("output/SMIB/GENROU_IEEET1_Efd.csv",sm.toCSVString(sm.getMachEfdTable()));
		FileUtil.writeText2File("output/SMIB/GENROU_IEEET1_angle.csv",sm.toCSVString(sm.getMachAngleTable()));
		FileUtil.writeText2File("output/SMIB/GENROU_IEEET1_speed.csv",sm.toCSVString(sm.getMachSpeedTable()));
		FileUtil.writeText2File("output/SMIB/GENROU_IEEET1_Vt.csv",sm.toCSVString(sm.getBusVoltTable()));
		
		/*
		FileUtil.writeText2File("output/ieee9_bus5_machPe_v4_sat_03042015.csv",sm.toCSVString(sm.getMachPeTable()));
		FileUtil.writeText2File("output/ieee9_bus5_machAngle_v4_sat_03042015.csv",sm.toCSVString(sm.getMachAngleTable()));
		FileUtil.writeText2File("output/ieee9_bus5_machSpd_v4_sat_03042015.csv",sm.toCSVString(sm.getMachSpeedTable()));
		FileUtil.writeText2File("output/ieee9_bus5_busVolt_v3_sat_03042015.csv",sm.toCSVString(sm.getBusVoltTable()));
        */

	}
	
	@Test
	public void test_GENSAL() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/SMIB/SMIB_v30.raw",
				"testData/adpter/psse/v30/SMIB/SMIB_v30_gensal.dyr"
				
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
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

	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(10);

		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus2-mach1"));
		
		//Bus fault
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",dsNet,SimpleFaultCode.GROUND_3P,new Complex(0.0),null,1.0d,0.05),"3phaseFault@Bus2");
        
		//generator tripping event 
		//dsNet.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus1", "1", dsNet, 1),"Bus1_Mach1_trip_1sec");
        
        
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus2","Bus1"});
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		//for(int i =1; i<20;i++){
			
			

		if (dstabAlgo.initialization()) {
			System.out.println(dsNet.getMachineInitCondition());

			
			//System.out.println("Running DStab simulation ...");
			timer.start();
			dstabAlgo.performSimulation();
			
			timer.logStd("total simu time: ");
			}
			//dstabAlgo.performOneStepSimulation();

		//}
		
		System.out.println("Mach Speed (pu):\n"+sm.toCSVString(sm.getMachSpeedTable()));
		System.out.println("Mach Angles (deg):\n"+sm.toCSVString(sm.getMachAngleTable()));
		
		System.out.println("Mach Pe (pu) :\n"+sm.toCSVString(sm.getMachPeTable()));
		
//		System.out.println("Volages (pu):\n"+sm.toCSVString(sm.getBusVoltTable()));
//		
//		System.out.println("Bus freq (pu):\n"+sm.toCSVString(sm.getBusFreqTable()));
		
		MonitorRecord maxAngleRec = sm.getMachAngleTable().get("Bus1-mach1").values().stream().max(Comparator.comparing(MonitorRecord:: getValue)).get();
		assertTrue(NumericUtil.equals(maxAngleRec.getValue(),25.94, 1.0e-1));
//		assertTrue(NumericUtil.equals(maxAngleRec.getTime(),1.315, 1.0e-3));
		assertTrue(NumericUtil.equals(sm.getMachAngleTable().get("Bus1-mach1").get(0).value, 22.98926,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachAngleTable().get("Bus1-mach1").get(2000).value, 23.09,1.0E-2));
		
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(0).value, 0.5,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(200).value, 0.5,1.0E-4));
//		FileUtil.writeText2File("output/ieee9_bus5_machPe_v5_03172015.csv",sm.toCSVString(sm.getMachPeTable()));
		FileUtil.writeText2File("output/SMIB/GENSAL_angle.csv",sm.toCSVString(sm.getMachAngleTable()));
		FileUtil.writeText2File("output/SMIB/GENSAL_speed.csv",sm.toCSVString(sm.getMachSpeedTable()));



	}
	
	
	

}
