package org.interpss.core.dstab;

import static com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType.MachineState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.acsc.AcscModelParser;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.algo.SequenceNetworkBuilder;
import org.interpss.display.AclfOutFunc;
import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.dstab.dynLoad.impl.InductionMotorImpl;
import org.interpss.mapper.odm.ODMAcscParserMapper;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.Number2String;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.sc.ScBusModelType;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.dstab.cache.StateVariableRecorder;
import com.interpss.dstab.cache.StateVariableRecorder.StateRecord;
import com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.devent.DynamicSimuEvent;
import com.interpss.dstab.devent.DynamicSimuEventType;
import com.interpss.dstab.devent.LoadChangeEventType;
import com.interpss.dstab.impl.DStabGenImpl;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class DStab_IEEE9Bus_Test extends DStabTestSetupBase{
	

	@Test
	public void test_IEEE9Bus_Dstab() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_fullModel_v33.dyr"
				//"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
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
		//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(15);

		dstabAlgo.setRefMachine(dsNet.getMachine("Bus2-mach1"));
		
		//Bus fault
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,new Complex(0.0),null,1.0d,0.05),"3phaseFault@Bus5");
        
		//generator tripping event 
		//dsNet.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus1", "1", dsNet, 1),"Bus1_Mach1_trip_1sec");
        
        
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus1"});
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(5);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		//for(int i =1; i<20;i++){
			
			

		if (dstabAlgo.initialization()) {
			//System.out.println(dsNet.getMachineInitCondition());
			
			//System.out.println("Running DStab simulation ...");
			timer.start();
			dstabAlgo.performSimulation();
			
			timer.logStd("total simu time: ");
		}
			//dstabAlgo.performOneStepSimulation();

		//}
		System.out.println("Mach Angles (deg):\n"+sm.toCSVString(sm.getMachAngleTable()));
		
		System.out.println("Mach Pe (pu) :\n"+sm.toCSVString(sm.getMachPeTable()));
		
		System.out.println("Volages (pu):\n"+sm.toCSVString(sm.getBusVoltTable()));
		
		System.out.println("Bus freq (pu):\n"+sm.toCSVString(sm.getBusFreqTable()));
		
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(0).value, 0.71639,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(20).value, 0.71639,1.0E-4));
//		FileUtil.writeText2File("output/ieee9_bus5_machPe_v5_03172015.csv",sm.toCSVString(sm.getMachPeTable()));
//		FileUtil.writeText2File("output/ieee9_bus5_machAngle_v5_03172015.csv",sm.toCSVString(sm.getMachAngleTable()));
//		FileUtil.writeText2File("output/ieee9_bus5_machSpd_v5_03172015.csv",sm.toCSVString(sm.getMachSpeedTable()));
//		FileUtil.writeText2File("output/ieee9_bus5_busVolt_v5_03172015.csv",sm.toCSVString(sm.getBusVoltTable()));
		
		/*
		FileUtil.writeText2File("output/ieee9_bus5_machPe_v4_sat_03042015.csv",sm.toCSVString(sm.getMachPeTable()));
		FileUtil.writeText2File("output/ieee9_bus5_machAngle_v4_sat_03042015.csv",sm.toCSVString(sm.getMachAngleTable()));
		FileUtil.writeText2File("output/ieee9_bus5_machSpd_v4_sat_03042015.csv",sm.toCSVString(sm.getMachSpeedTable()));
		FileUtil.writeText2File("output/ieee9_bus5_busVolt_v3_sat_03042015.csv",sm.toCSVString(sm.getBusVoltTable()));
        */
		/*
		 *  Bus2 Machine Anagle
				0.0000, 57.56288
				0.0010, 57.56288
				0.0020, 57.56288
				0.0030, 57.56288
				0.0040, 57.56288
				0.0050, 57.56288
				0.0060, 57.56288
				0.0070, 57.56288
				0.0080, 57.56288
				0.0090, 57.56288
		 */
		
		/*
		 *  Bus2 Machine PM
			0.0000, 1.6300
			0.0010, 1.6300
			0.0020, 1.6300
			0.0030, 1.6300
			0.0040, 1.6300
			0.0050, 1.6300
			0.0060, 1.6300
			0.0070, 1.6300
			0.0080, 1.6300
			0.0090, 1.6300
		 */
		
		/*
		 *  Bus2 Machine Efd
			0.0000, 1.78898
			0.0010, 1.78898
			0.0020, 1.78898
			0.0030, 1.78898
			0.0040, 1.78898
			0.0050, 1.78898
			0.0060, 1.78898
			0.0070, 1.78898
			0.0080, 1.78898
			0.0090, 1.78898
		 */
	}
	
	
	@Test
	public void test_IEEE9Bus_Dstab_Load_Change_staticLoad() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.WARNING);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_fullModel_v33.dyr"
				//"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
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
	    
	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(15);

		dstabAlgo.setRefMachine(dsNet.getMachine("Bus2-mach1"));
		
		//Bus fault
		//dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,new Complex(0.0),null,1.0d,0.05),"3phaseFault@Bus5");
        
		//generator tripping event 
		//dsNet.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus1", "1", dsNet, 1),"Bus1_Mach1_trip_1sec");
        
		//Load change event
		// load shed
		dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus5", dsNet,LoadChangeEventType.FIXED_TIME,-0.2, 1.0),"LoadReduce20%@Bus5");
        // load increase
		//dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus5", dsNet,LoadChangeEventType.FIXED_TIME, 0.2, 1.0),"LoadReduce20%@Bus5");
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus1"});
		sm.addBranchStdMonitor("Bus5->Bus7(0)");
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(5);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
	    dsNet.setStaticLoadIncludedInYMatrix(false);
		
	    dsNet.setStaticLoadModel(StaticLoadModel.CONST_Z);
	    dsNet.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
		

		if (dstabAlgo.initialization()) {
			//System.out.println(dsNet.getMachineInitCondition());
			
			//System.out.println("Running DStab simulation ...");
			timer.start();
			dstabAlgo.performSimulation();
			
			timer.logStd("total simu time: ");
		}
		
		System.out.println("Mach Pe (pu) :\n"+sm.toCSVString(sm.getMachPeTable()));
//		
//		System.out.println("Volages Mag (pu):\n"+sm.toCSVString(sm.getBusVoltTable()));
		
//		System.out.println("Volages Angle (Deg):\n"+sm.toCSVString(sm.getBusAngleTable()));
		
		System.out.println("Bus freq (pu):\n"+sm.toCSVString(sm.getBusFreqTable()));
		System.out.println("Branch flow P (pu):\n"+sm.toCSVString(sm.getBranchFlowPTable()));
		
		Complex load5 = ((BaseDStabBus)dsNet.getBus("Bus5")).calStaticLoad();
		
	    System.out.println("after being tripped static load at bus 5 = "+load5.toString());
	    
	    assertTrue(NumericUtil.equals(load5, new Complex(1.0298252490657749, 0.41193009962631), 1.0E-6)); 
		

		
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(0).value, 0.71639,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(40).value, 0.71639,1.0E-4));
		//assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(60).value, 0.62507,1.0E-5));
		//assertTrue(!dsNet.getMachine("Bus1-mach1").isActive());
		
		/*
		 * Mach Pe (pu) :
 time,Bus3-mach1, Bus1-mach1, Bus2-mach1
 0.0000,    0.85000,    0.71639,    1.63000,
 0.0200,    0.85000,    0.71640,    1.63000,
 0.0450,    0.85000,    0.71640,    1.63000,
 0.0700,    0.85000,    0.71640,    1.63000,
 0.0950,    0.85000,    0.71640,    1.63000,
 0.1200,    0.85000,    0.71640,    1.63000,
 0.1450,    0.85000,    0.71640,    1.63000,
 0.1700,    0.85000,    0.71639,    1.63000,
 0.1950,    0.85000,    0.71639,    1.63000,
 0.2200,    0.85000,    0.71639,    1.63000,
 0.2450,    0.85000,    0.71639,    1.63000,
 0.2700,    0.85000,    0.71639,    1.63000,
 0.2950,    0.85000,    0.71639,    1.63000,
 0.3200,    0.85000,    0.71639,    1.63000,
 0.3450,    0.85000,    0.71639,    1.63000,
 0.3700,    0.85000,    0.71639,    1.63000,
 0.3950,    0.85000,    0.71639,    1.63000,
 0.4200,    0.85000,    0.71639,    1.63000,
 0.4450,    0.85000,    0.71639,    1.63000,
 0.4700,    0.85000,    0.71639,    1.63000,
 0.4950,    0.85000,    0.71639,    1.63000,
 0.5200,    0.85000,    0.71639,    1.63000,
 0.5450,    0.85000,    0.71639,    1.63000,
 0.5700,    0.85000,    0.71639,    1.63000,
 0.5950,    0.85000,    0.71639,    1.63000,
 0.6200,    0.85000,    0.71639,    1.63000,
 0.6450,    0.85000,    0.71639,    1.63000,
 0.6700,    0.85000,    0.71639,    1.63000,
 0.6950,    0.85000,    0.71640,    1.63000,
 0.7200,    0.85000,    0.71640,    1.63000,
 0.7450,    0.85000,    0.71640,    1.63000,
 0.7700,    0.85000,    0.71640,    1.63000,
 0.7950,    0.85000,    0.71640,    1.63000,
 0.8200,    0.85000,    0.71640,    1.63000,
 0.8450,    0.85000,    0.71640,    1.63000,
 0.8700,    0.85000,    0.71640,    1.63000,
 0.8950,    0.85000,    0.71640,    1.63000,
 0.9200,    0.85000,    0.71640,    1.63000,
 0.9450,    0.85000,    0.71640,    1.63000,
 0.9700,    0.85000,    0.71640,    1.63000,
 0.9950,    0.85000,    0.71640,    1.63000,
 1.0000,    0.85000,    0.71640,    1.63000,
 1.0200,    0.82160,    0.61290,    1.58322,
 1.0450,    0.82396,    0.61376,    1.58432,
 1.0700,    0.82704,    0.61364,    1.58610,
 1.0950,    0.83067,    0.61270,    1.58849,
 1.1200,    0.83439,    0.61094,    1.59123,
 1.1450,    0.83797,    0.60880,    1.59434,
 1.1700,    0.84114,    0.60653,    1.59771,
 1.1950,    0.84370,    0.60441,    1.60127,
 1.2200,    0.84553,    0.60266,    1.60492,
 1.2450,    0.84657,    0.60148,    1.60855,
 1.2700,    0.84685,    0.60101,    1.61206,
 1.2950,    0.84644,    0.60134,    1.61530,
 1.3200,    0.84549,    0.60251,    1.61815,
 1.3450,    0.84415,    0.60453,    1.62047,
 1.3700,    0.84260,    0.60736,    1.62217,
 1.3950,    0.84102,    0.61094,    1.62316,
 1.4200,    0.83957,    0.61515,    1.62339,
 1.4450,    0.83839,    0.61991,    1.62286,
 1.4700,    0.83755,    0.62507,    1.62160,
 1.4950,    0.83710,    0.63051,    1.61969,
 1.5200,    0.83706,    0.63609,    1.61725,
 1.5450,    0.83738,    0.64170,    1.61443,
 1.5700,    0.83799,    0.64720,    1.61140,
 1.5950,    0.83880,    0.65250,    1.60834,
 1.6200,    0.83970,    0.65749,    1.60544,
 1.6450,    0.84060,    0.66209,    1.60287,
 1.6700,    0.84140,    0.66623,    1.60079,
 1.6950,    0.84203,    0.66985,    1.59930,
 1.7200,    0.84244,    0.67290,    1.59850,
 1.7450,    0.84260,    0.67536,    1.59841,
 1.7700,    0.84254,    0.67722,    1.59905,
 1.7950,    0.84230,    0.67847,    1.60035,
 1.8200,    0.84193,    0.67914,    1.60225,
 1.8450,    0.84150,    0.67925,    1.60463,
 1.8700,    0.84110,    0.67885,    1.60738,
 1.8950,    0.84080,    0.67802,    1.61033,
 1.9200,    0.84067,    0.67681,    1.61336,
 1.9450,    0.84076,    0.67532,    1.61634,
 1.9700,    0.84110,    0.67365,    1.61913,
 1.9950,    0.84168,    0.67189,    1.62164,
 2.0200,    0.84249,    0.67016,    1.62380,
 2.0450,    0.84348,    0.66856,    1.62555,
 2.0700,    0.84459,    0.66718,    1.62687,
 2.0950,    0.84574,    0.66611,    1.62776,
 2.1200,    0.84686,    0.66543,    1.62823,
 2.1450,    0.84787,    0.66519,    1.62833,
 2.1700,    0.84869,    0.66543,    1.62810,
 2.1950,    0.84928,    0.66616,    1.62759,
 2.2200,    0.84960,    0.66739,    1.62687,
 2.2450,    0.84963,    0.66907,    1.62598,
 2.2700,    0.84938,    0.67117,    1.62499,
 2.2950,    0.84887,    0.67362,    1.62393,
 2.3200,    0.84817,    0.67633,    1.62286,
 2.3450,    0.84731,    0.67922,    1.62179,
 2.3700,    0.84639,    0.68220,    1.62076,
 2.3950,    0.84545,    0.68516,    1.61980,
 2.4200,    0.84458,    0.68801,    1.61891,
 2.4450,    0.84384,    0.69067,    1.61812,
 2.4700,    0.84326,    0.69306,    1.61743,
 2.4950,    0.84289,    0.69511,    1.61687,
 2.5200,    0.84274,    0.69679,    1.61646,
 2.5450,    0.84281,    0.69807,    1.61620,
 2.5700,    0.84309,    0.69893,    1.61611,
 2.5950,    0.84353,    0.69938,    1.61620,
 2.6200,    0.84410,    0.69944,    1.61648,
 2.6450,    0.84476,    0.69916,    1.61696,
 2.6700,    0.84546,    0.69857,    1.61764,
 2.6950,    0.84615,    0.69773,    1.61849,
 2.7200,    0.84678,    0.69670,    1.61950,
 2.7450,    0.84734,    0.69556,    1.62065,
 2.7700,    0.84779,    0.69435,    1.62189,
 2.7950,    0.84813,    0.69315,    1.62319,
 2.8200,    0.84835,    0.69200,    1.62449,
 2.8450,    0.84846,    0.69097,    1.62575,
 2.8700,    0.84849,    0.69009,    1.62693,
 2.8950,    0.84844,    0.68941,    1.62796,
 2.9200,    0.84835,    0.68894,    1.62881,
 2.9450,    0.84823,    0.68870,    1.62945,
 2.9700,    0.84811,    0.68871,    1.62986,
 2.9950,    0.84801,    0.68896,    1.63002,
 3.0200,    0.84793,    0.68945,    1.62994,
		 */
	

	}
	
	@Test
	public void test_IEEE9Bus_Dstab_Load_Change_dynamicLoad() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.WARNING);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_fullModel_v33.dyr"
				//"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
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
	    
	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		
        BaseDStabBus bus5 =  dsNet.getDStabBus("Bus5");
		
		InductionMotor indMotor= new InductionMotorImpl(bus5,"1");
		

		indMotor.setXm(3.0);
		indMotor.setXl(0.07);
		indMotor.setRa(0.032);
		indMotor.setXr1(0.2);
		indMotor.setRr1(0.01);
		indMotor.setXr2(0.0);
		indMotor.setRr2(0.0);
		
		indMotor.setLoadPercent(50);
		//indMotor.setMvaBase(50);
		indMotor.setH(1.0);
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(15);

		dstabAlgo.setRefMachine(dsNet.getMachine("Bus2-mach1"));
		
		//Bus fault
		//dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,new Complex(0.0),null,1.0d,0.05),"3phaseFault@Bus5");
        
		//generator tripping event 
		//dsNet.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus1", "1", dsNet, 1),"Bus1_Mach1_trip_1sec");
        
		//Load change event
		double loadChangeFraction = 0.2;
		dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus5", dsNet,LoadChangeEventType.FIXED_TIME,loadChangeFraction, 1.0),"LoadReduce20%@Bus5");
        
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus1"});
		sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus5");
		sm.addBranchStdMonitor("Bus5->Bus7(0)");
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(5);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
	    dsNet.setStaticLoadIncludedInYMatrix(false);
		
	    dsNet.setStaticLoadModel(StaticLoadModel.CONST_Z);
	    dsNet.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
		

		if (dstabAlgo.initialization()) {
			//System.out.println(dsNet.getMachineInitCondition());
			
			//System.out.println("Running DStab simulation ...");
			timer.start();
			dstabAlgo.performSimulation();
			
			timer.logStd("total simu time: ");
		}
		
		Complex load5 = ((BaseDStabBus)dsNet.getBus("Bus5")).calTotalLoad();
		
	    System.out.println("after being tripped total load at bus 5 = "+load5.toString());
	    
//	    assertTrue(NumericUtil.equals(load5, new Complex(1.0143804864153798, 0.4040793069573881), 1.0E-6)); 
		
		System.out.println("Mach Pe (pu) :\n"+sm.toCSVString(sm.getMachPeTable()));
//		
		System.out.println("Volages Mag (pu):\n"+sm.toCSVString(sm.getBusVoltTable()));
		
		System.out.println("Motor Pe:\n"+sm.toCSVString(sm.getMotorPTable()));
		
		//System.out.println("Bus freq (pu):\n"+sm.toCSVString(sm.getBusFreqTable()));
		System.out.println("Branch flow P (pu):\n"+sm.toCSVString(sm.getBranchFlowPTable()));
		
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(0).value, 0.71639,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(40).value, 0.71639,1.0E-4));
		//assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(60).value, 0.62507,1.0E-5));
		assertEquals(sm.getMotorPTable().get("IndMotor_1@Bus5").get(0).value*(1+loadChangeFraction), sm.getMotorPTable().get("IndMotor_1@Bus5").get(40*14).value,5.0E-4);
		


	}
	
	@Test
	public void test_IEEE9Bus_Dstab_Generator_Trip() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_fullModel_v33.dyr"
				//"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
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
//	    System.out.println(dsNet.net2String());
	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(15);

		dstabAlgo.setRefMachine(dsNet.getMachine("Bus2-mach1"));
		
		//Bus fault
		//dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,new Complex(0.0),null,1.0d,0.05),"3phaseFault@Bus5");
        
		//generator tripping event 
		dsNet.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus1", "1", dsNet, 1),"Bus1_Mach1_trip_1sec");
        
        
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus1"});
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(5);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		//for(int i =1; i<20;i++){
			
			

		if (dstabAlgo.initialization()) {
			//System.out.println(dsNet.getMachineInitCondition());
			
			//System.out.println("Running DStab simulation ...");
			timer.start();
			dstabAlgo.performSimulation();
			
			timer.logStd("total simu time: ");
			}
			//dstabAlgo.performOneStepSimulation();

		//}
		System.out.println("Mach Angles (deg):\n"+sm.toCSVString(sm.getMachAngleTable()));
		
//		System.out.println("Mach Pe (pu) :\n"+sm.toCSVString(sm.getMachPeTable()));
		System.out.println("Mach Pm (pu) :\n"+sm.toCSVString(sm.getMachPmTable()));
//		
//		System.out.println("Volages Mag (pu):\n"+sm.toCSVString(sm.getBusVoltTable()));
		
		System.out.println("Volages Angle (Deg):\n"+sm.toCSVString(sm.getBusAngleTable()));
		
		System.out.println("Bus freq (pu):\n"+sm.toCSVString(sm.getBusFreqTable()));
		
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(0).value, 0.71639,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(15).value, 0.71639,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(42).value, 0.0,1.0E-4));
		assertTrue(!dsNet.getMachine("Bus1-mach1").isActive());
		
	
		/*
		FileUtil.writeText2File("output/ieee9_bus5_machPe_v4_sat_03042015.csv",sm.toCSVString(sm.getMachPeTable()));
		FileUtil.writeText2File("output/ieee9_bus5_machAngle_v4_sat_03042015.csv",sm.toCSVString(sm.getMachAngleTable()));
		FileUtil.writeText2File("output/ieee9_bus5_machSpd_v4_sat_03042015.csv",sm.toCSVString(sm.getMachSpeedTable()));
		FileUtil.writeText2File("output/ieee9_bus5_busVolt_v3_sat_03042015.csv",sm.toCSVString(sm.getBusVoltTable()));
        */

	}
	
	@Test
	public void test_IEEE9Bus_Dstab_Generator_Energization() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_multiGen_v2.raw",
                //"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
                "testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_multiGen.dyr"
			
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
        dsNet.initialization(ScBusModelType.DSTAB_SIMU);
	    
	    // build sequence network
//	    SequenceNetworkBuilder seqNetHelper = new SequenceNetworkBuilder(dsNet,true);
//	    seqNetHelper.buildSequenceNetwork(SequenceCode.NEGATIVE);
//	    seqNetHelper.buildSequenceNetwork(SequenceCode.ZERO);
//	    
//	    System.out.println(dsNet.net2String());
	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(15);

		dstabAlgo.setRefMachine(dsNet.getMachine("Bus2-mach1"));
		
		//Bus fault
		//dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,new Complex(0.0),null,1.0d,0.05),"3phaseFault@Bus5");
        
		
        
 	    dsNet.addDynamicEvent(DStabObjectFactory.createGeneratorEnergizationEvent("Bus3", "2", dsNet, 1),"Bus3_Mach2_connect_1.1sec");
        
        
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1","Bus3-mach2"});
		sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus1"});
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(5);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		//for(int i =1; i<20;i++){
			
		assertTrue(!dsNet.getMachine("Bus3-mach2").isActive());

		if (dstabAlgo.initialization()) {
			//System.out.println(dsNet.getMachineInitCondition());
			
			//System.out.println("Running DStab simulation ...");
			timer.start();
			dstabAlgo.performSimulation();
			
			timer.logStd("total simu time: ");
			}
			//dstabAlgo.performOneStepSimulation();

		//}
		System.out.println("Mach Angles (deg):\n"+sm.toCSVString(sm.getMachAngleTable()));
		
		System.out.println("Mach Pe (pu) :\n"+sm.toCSVString(sm.getMachPeTable()));
//		
//		System.out.println("Volages Mag (pu):\n"+sm.toCSVString(sm.getBusVoltTable()));
		
//		System.out.println("Volages Angle (Deg):\n"+sm.toCSVString(sm.getBusAngleTable()));
//		
		System.out.println("Bus freq (pu):\n"+sm.toCSVString(sm.getBusFreqTable()));
		
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(0).value, 1.13090,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus1-mach1").get(40).value, 1.13090,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus3-mach2").get(0).value, 0.0,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus3-mach2").get(40).value, 0.0,1.0E-4));
		assertTrue(NumericUtil.equals(sm.getMachPeTable().get("Bus3-mach2").get(42).value, 0.60418,1.0E-4));
		
	
		
	
		/*
		FileUtil.writeText2File("output/ieee9_bus5_machPe_v4_sat_03042015.csv",sm.toCSVString(sm.getMachPeTable()));
		FileUtil.writeText2File("output/ieee9_bus5_machAngle_v4_sat_03042015.csv",sm.toCSVString(sm.getMachAngleTable()));
		FileUtil.writeText2File("output/ieee9_bus5_machSpd_v4_sat_03042015.csv",sm.toCSVString(sm.getMachSpeedTable()));
		FileUtil.writeText2File("output/ieee9_bus5_busVolt_v3_sat_03042015.csv",sm.toCSVString(sm.getBusVoltTable()));
        */

	}
	
	@Test
	public void IEEE9_Dstab_benchMark() throws InterpssException{
		IpssCorePlugin.init();
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
	    BaseDStabNetwork dsNet =simuCtx.getDStabilityNet();
	    
	   // System.out.println(dsNet.net2String());
   
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		
		//dsNet.setNetEqnIterationNoEvent(20);
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005);
		dstabAlgo.setTotalSimuTimeSec(20);
		dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));


		double[] timePoints   = {0.0,    0.4,    0.7,    0.9},
	      			 machPmPoints = {1.6300, 1.6300,   1.6300,   1.6300},
	      			 machAngPoints  = {57.56288, 57.56288,  57.56288,   57.56288},
	      			 machEfdPoints  = {1.78898, 1.78898,   1.78898,   1.78898};
		
		StateVariableRecorder stateTestRecorder = new StateVariableRecorder(0.0001);
		stateTestRecorder.addTestRecords("Bus2-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_PM, timePoints, machPmPoints);
		stateTestRecorder.addTestRecords("Bus2-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_ANG, timePoints, machAngPoints);
		stateTestRecorder.addTestRecords("Bus2-mach1", MachineState, 
				DStabOutSymbol.OUT_SYMBOL_MACH_Efd, timePoints, machEfdPoints);
		dstabAlgo.setSimuOutputHandler(stateTestRecorder);
			

		if (dstabAlgo.initialization()) {
			//System.out.println(simuCtx.getDStabilityNet().net2String());

			System.out.println("Running DStab simulation ...");
			assertTrue(dstabAlgo.performSimulation());
		}
		timer.logStd("total simu time: ");
			
		assertTrue(stateTestRecorder.diffTotal("Bus2-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_PM) < 0.00001);
		assertTrue(stateTestRecorder.diffTotal("Bus2-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_ANG) < 0.001);
		assertTrue(stateTestRecorder.diffTotal("Bus2-mach1", MachineState, 
				DStabOutSymbol.OUT_SYMBOL_MACH_Efd) < 0.0001);
	}
	
	@Test
	public void IEEE9_Dstab_GenWithoutMach() throws InterpssException{
		IpssCorePlugin.init();
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_Gen3NoMach.dyr"
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

	    //System.out.println(dsNet.net2String());
	    
	    //TODO Set allow gen without machine
	    dsNet.setAllowGenWithoutMach(true);
   
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.001);
		dstabAlgo.setTotalSimuTimeSec(0.01);
		dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		
		double[] timePoints   = {0.0,    0.004,    0.007,    0.009},
	      			 machPmPoints = {1.6300, 1.6300,   1.6300,   1.6300},
	      			 machAngPoints  = {57.56288, 57.56288,  57.56288,   57.56288},
	      			 machEfdPoints  = {1.78898, 1.78898,   1.78898,   1.78898};
		
		StateVariableRecorder stateTestRecorder = new StateVariableRecorder(0.0001);
		stateTestRecorder.addTestRecords("Bus2-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_PM, timePoints, machPmPoints);
		stateTestRecorder.addTestRecords("Bus2-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_ANG, timePoints, machAngPoints);
		stateTestRecorder.addTestRecords("Bus2-mach1", MachineState, 
				DStabOutSymbol.OUT_SYMBOL_MACH_Efd, timePoints, machEfdPoints);
		dstabAlgo.setSimuOutputHandler(stateTestRecorder);
			

		if (dstabAlgo.initialization()) {
			//System.out.println(simuCtx.getDStabilityNet().net2String());

			System.out.println("Running DStab simulation ...");
			assertTrue(dstabAlgo.performSimulation());
		}
		
		
			
		assertTrue(stateTestRecorder.diffTotal("Bus2-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_PM) < 0.00001);
		assertTrue(stateTestRecorder.diffTotal("Bus2-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_ANG) < 0.00001);
		assertTrue(stateTestRecorder.diffTotal("Bus2-mach1", MachineState, 
				DStabOutSymbol.OUT_SYMBOL_MACH_Efd) < 0.00001);
	}
	@Test
    public void IEEE9_Dstab_multiGen_Test() throws InterpssException{
            IpssCorePlugin.init();
            IpssLogger.getLogger().setLevel(Level.INFO);
            PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
            assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
                            "testData/adpter/psse/v30/IEEE9Bus/ieee9_multiGen.raw",
                            //"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
                            "testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_multiGen.dyr"
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
            //System.out.println(dsNet.net2String());
            dsNet.initialization(ScBusModelType.DSTAB_SIMU);

            DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
            LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
            aclfAlgo.setTolerance(1.0E-6);
            assertTrue(aclfAlgo.loadflow());
            System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
            
            dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
            dstabAlgo.setSimuStepSec(0.001);
            dstabAlgo.setTotalSimuTimeSec(1);
            dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
            
            double[] timePoints   = {0.0,    0.004,    0.7,    0.9},
                                   machPmPoints = {1.6300, 1.6300,   1.6300,   1.6300},
                                   machAngPoints  = {57.56288, 57.56288,  57.56288,   57.56288},
                                   machEfdPoints  = {1.78898, 1.78898,   1.78898,   1.78898};
            
            StateVariableRecorder stateTestRecorder = new StateVariableRecorder(0.0001);
            stateTestRecorder.addTestRecords("Bus2-mach1", MachineState, 
                                    DStabOutSymbol.OUT_SYMBOL_MACH_PM, timePoints, machPmPoints);
            stateTestRecorder.addTestRecords("Bus2-mach1", MachineState, 
                                    DStabOutSymbol.OUT_SYMBOL_MACH_ANG, timePoints, machAngPoints);
            stateTestRecorder.addTestRecords("Bus2-mach1", MachineState, 
                            DStabOutSymbol.OUT_SYMBOL_MACH_Efd, timePoints, machEfdPoints);
            dstabAlgo.setSimuOutputHandler(stateTestRecorder);
                    
            dsNet.setNetEqnIterationNoEvent(1);
            if (dstabAlgo.initialization()) {
                    //System.out.println(simuCtx.getDStabilityNet().net2String());

                    System.out.println("Running DStab simulation ...");
                    assertTrue(dstabAlgo.performSimulation());
            }
                    
            assertTrue(stateTestRecorder.diffTotal("Bus2-mach1", MachineState, 
                                    DStabOutSymbol.OUT_SYMBOL_MACH_PM) < 0.0001);
            assertTrue(stateTestRecorder.diffTotal("Bus2-mach1", MachineState, 
                                    DStabOutSymbol.OUT_SYMBOL_MACH_ANG) < 0.001);
            assertTrue(stateTestRecorder.diffTotal("Bus2-mach1", MachineState, 
                            DStabOutSymbol.OUT_SYMBOL_MACH_Efd) < 0.0001);
    }
	
	@Test
	public void IEEE9_Dstab_gen_load_status_change() throws InterpssException{
		IpssCorePlugin.init();
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
	    BaseDStabNetwork<?, ?> dsNet =simuCtx.getDStabilityNet();
	    
	    DStabGenImpl gen1 = (DStabGenImpl) dsNet.getBus("Bus1").getContributeGenList().get(0);
		DStabGenImpl gen2 = (DStabGenImpl) dsNet.getBus("Bus2").getContributeGenList().get(0);
		DStabGenImpl gen3 = (DStabGenImpl) dsNet.getBus("Bus3").getContributeGenList().get(0);
		//gen3.setGen(new Complex(0.2, 0.0665));
		//gen3.getParentBus().initContributeGen();
		
		gen2.setStatus(false);
	
		gen2.getParentBus().initContributeGen();
	    
	   // System.out.println(dsNet.net2String());
   
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		//dsNet.setNetEqnIterationNoEvent(20);
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005);
		dstabAlgo.setTotalSimuTimeSec(2);
		dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));


		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus1"});
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(5);
		
		if (dstabAlgo.initialization()) {
			//System.out.println(simuCtx.getDStabilityNet().net2String());

			System.out.println("Running DStab simulation ...");
			assertTrue(dstabAlgo.performSimulation());
		}
		timer.logStd("total simu time: ");
		
		System.out.println("Mach Pm (pu) :\n"+sm.toCSVString(sm.getMachPmTable()));
		
		System.out.println("Volages Angle (Deg):\n"+sm.toCSVString(sm.getBusAngleTable()));
		
		System.out.println("Bus freq (pu):\n"+sm.toCSVString(sm.getBusFreqTable()));
			
	
	}
	
	private DynamicSimuEvent create3PhaseFaultEvent(String faultBusId, BaseDStabNetwork net,double startTime, double durationTime){
	       // define an event, set the event id and event type.
			DynamicSimuEvent event1 = DStabObjectFactory.createDEvent("BusFault3P@"+faultBusId, "Bus Fault 3P@"+faultBusId, 
					DynamicSimuEventType.BUS_FAULT, net);
			event1.setStartTimeSec(startTime);
			event1.setDurationSec(durationTime);
			
	      // define a bus fault
			BaseDStabBus faultBus = net.getDStabBus(faultBusId);

			AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus Fault 3P@"+faultBusId, net, true /* cacheBusScVolt */);
	  		fault.setBus(faultBus);
			fault.setFaultCode(SimpleFaultCode.GROUND_3P);
			fault.setZLGFault(NumericConstant.SmallScZ);

	      // add this fault to the event, must be consist with event type definition before.
			event1.setBusFault(fault); 
			return event1;
	}
	
	
	//@Test
	public void test_ieee_1981_exciter() throws InterpssException{
		IpssCorePlugin.init();
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_Model_1981Exc.dyr"
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

	    //System.out.println(dsNet.net2String());
	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.00416);
		dstabAlgo.setTotalSimuTimeSec(0.6);
		dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		dsNet.addDynamicEvent(create3PhaseFaultEvent("Bus4",dsNet,0.2,0.05),"3phaseFault@Bus4");
		
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		
		// set the output handler
				dstabAlgo.setSimuOutputHandler(sm);
				dstabAlgo.setOutPutPerSteps(1);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		assertTrue(dstabAlgo.initialization());
		if (dstabAlgo.initialization()) {
			System.out.println(dsNet.getMachineInitCondition());
			System.out.println("Running DStab simulation ...");
			dstabAlgo.performSimulation();
			//dstabAlgo.performOneStepSimulation();
		}
		
		//System.out.println(sm.toCSVString(sm.getMachEfdTable()));
		//System.out.println(sm.toCSVString(sm.getMachQgenTable()));
		/*
		FileUtil.writeText2File("output/ieee9_1981_machEfd_0320_v1.csv",sm.toCSVString(sm.getMachEfdTable()));
		FileUtil.writeText2File("output/ieee9_1981_machQ_0321.csv",sm.toCSVString(sm.getMachQgenTable()));
		*/

	}
	
	//@Test
	public void test_ieee_2005_exciter() throws InterpssException{
		IpssCorePlugin.init();
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_Model_2005Exc.dyr"
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

	   // System.out.println(dsNet.net2String());
	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.00416);
		dstabAlgo.setTotalSimuTimeSec(6);
		dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		dsNet.addDynamicEvent(create3PhaseFaultEvent("Bus4",dsNet,0.20,0.05),"3phaseFault@Bus4");

		
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		
		// set the output handler
				dstabAlgo.setSimuOutputHandler(sm);
				dstabAlgo.setOutPutPerSteps(2);
		
		IpssLogger.getLogger().setLevel(Level.ALL);
		assertTrue(dstabAlgo.initialization());
		if (dstabAlgo.initialization()) {
			System.out.println(dsNet.getMachineInitCondition());
			System.out.println("Running DStab simulation ...");
			dstabAlgo.performSimulation();
			//dstabAlgo.performOneStepSimulation();

		}
		
		//System.out.println(sm.toCSVString(sm.getMachEfdTable()));
		//System.out.println(sm.toCSVString(sm.getMachAngleTable()));
	}

}
