package org.interpss.core.dstab;

import static com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType.MachineState;
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
import org.interpss.mapper.odm.ODMAcscParserMapper;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.Number2String;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateVariableRecorder;
import com.interpss.dstab.cache.StateVariableRecorder.StateRecord;
import com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.devent.DynamicEvent;
import com.interpss.dstab.devent.DynamicEventType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class DStab_IEEE9Bus_Test extends DStabTestSetupBase{
	
	//@Test
	public void test_IEEE9Bus_Dstab() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
				//"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
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
		dstabAlgo.setTotalSimuTimeSec(20);
		dsNet.setNetEqnIterationNoEvent(1);
		dsNet.setNetEqnIterationWithEvent(1);
		dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,new Complex(0.0),null,1.0d,0.05),"3phaseFault@Bus5");
        
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus1"});
		// set the output handler
				dstabAlgo.setSimuOutputHandler(sm);
				dstabAlgo.setOutPutPerSteps(1);
		
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
		//System.out.println(sm.toCSVString(sm.getMachAngleTable()));
		
		//System.out.println(sm.toCSVString(sm.getMachPeTable()));
		
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
	
	//@Test
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
	//@Test
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
	
	private DynamicEvent create3PhaseFaultEvent(String faultBusId, BaseDStabNetwork net,double startTime, double durationTime){
	       // define an event, set the event id and event type.
			DynamicEvent event1 = DStabObjectFactory.createDEvent("BusFault3P@"+faultBusId, "Bus Fault 3P@"+faultBusId, 
					DynamicEventType.BUS_FAULT, net);
			event1.setStartTimeSec(startTime);
			event1.setDurationSec(durationTime);
			
	      // define a bus fault
			BaseDStabBus faultBus = net.getDStabBus(faultBusId);

			AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus Fault 3P@"+faultBusId, net);
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
