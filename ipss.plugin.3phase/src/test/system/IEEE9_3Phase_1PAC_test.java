package test.system;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;
import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.algo.DynamicEventProcessor3Phase;
import org.interpss.threePhase.dynamic.model.impl.SinglePhaseACMotor;
import org.interpss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.interpss.threePhase.util.ThreePhaseAclfOutFunc;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.adpter.AcscXformerAdapter;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class IEEE9_3Phase_1PAC_test {
	
	
	//@Test
	public void test_IEEE9_1pac_Network_solution() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
				"testData/IEEE9Bus/ieee9_dyn.dyr"
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
	    
	    
	    /*
	     *   create the 1-phase AC model 
	     */
		
		DStab3PBus bus5 = (DStab3PBus) dsNet.getBus("Bus5");
		
	    SinglePhaseACMotor ac1 = new SinglePhaseACMotor(bus5,"1");
  		ac1.setLoadPercent(50);
  		ac1.setPhase(PhaseCode.A);
  		ac1.setMvaBase(25);
  		bus5.getPhaseADynLoadList().add(ac1);
  		
  		
  		
  		SinglePhaseACMotor ac2 = new SinglePhaseACMotor(bus5,"2");
  		ac2.setLoadPercent(50);
  		ac2.setPhase(PhaseCode.B);
  		ac2.setMvaBase(25);
  		bus5.getPhaseBDynLoadList().add(ac2);
  		

  		
  		SinglePhaseACMotor ac3 = new SinglePhaseACMotor(bus5,"3");
  		ac3.setLoadPercent(50);
  		ac3.setPhase(PhaseCode.C);
  		ac3.setMvaBase(25);
  		bus5.getPhaseCDynLoadList().add(ac3);
	    
	    
	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.2);
		

		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		
		//applied the event
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus9",dsNet,SimpleFaultCode.GROUND_3P,new Complex(0.0),null,0.01d,0.05),"3phaseFault@Bus5");
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus1"});
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(5);
		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		
		IpssLogger.getLogger().setLevel(Level.WARNING);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
        // Must use this dynamic event process to modify the YMatrixABC
		dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
		
		if (dstabAlgo.initialization()) {
			System.out.println(ThreePhaseAclfOutFunc.busLfSummary(dsNet));
			dsNet.solveNetEqn(); 
			System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		}
	}
	@Test
	public void test_IEEE9_1pac_Dstab() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
				"testData/IEEE9Bus/ieee9_dyn.dyr"
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
	    
	    
	    /*
	     *   create the 1-phase AC model 
	     */
	   
		
	    
	    
		DStab3PBus bus5 = (DStab3PBus) dsNet.getBus("Bus5");
		
//		Load3Phase load1 = new Load3PhaseImpl();
//		load1.set3PhaseLoad(new Complex3x1(new Complex(1.25,0.5),new Complex(1.25,0.5),new Complex(1.25,0.5)));
//		bus5.getThreePhaseLoadList().add(load1);
//		
//	    SinglePhaseACMotor ac1 = new SinglePhaseACMotor(bus5,"1");
//  		ac1.setLoadPercent(50);
//  		ac1.setPhase(Phase.A);
//  		ac1.setMVABase(25);
//  		bus5.getPhaseADynLoadList().add(ac1);
//  		
//  		
//  		
//  		SinglePhaseACMotor ac2 = new SinglePhaseACMotor(bus5,"2");
//  		ac2.setLoadPercent(50);
//  		ac2.setPhase(Phase.B);
//  		ac2.setMVABase(25);
//  		bus5.getPhaseBDynLoadList().add(ac2);
//  		
//
//  		
//  		SinglePhaseACMotor ac3 = new SinglePhaseACMotor(bus5,"3");
//  		ac3.setLoadPercent(50);
//  		ac3.setPhase(Phase.C);
//  		ac3.setMVABase(25);
//  		bus5.getPhaseCDynLoadList().add(ac3);
	    
	    
	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.2);
		

		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		
		//applied the event
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus9",dsNet,SimpleFaultCode.GROUND_3P,new Complex(0.0),null,0.1d,0.05),"3phaseFault@Bus5");
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus1"});
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(5);
		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		
		IpssLogger.getLogger().setLevel(Level.WARNING);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
        // Must use this dynamic event process to modify the YMatrixABC
		dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
		
		if (dstabAlgo.initialization()) {
			System.out.println(ThreePhaseAclfOutFunc.busLfSummary(dsNet));
			System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			System.out.println(dsNet.getMachineInitCondition());
			
			System.out.println("Running 3Phase DStab simulation ...");
			timer.start();
			//dstabAlgo.performSimulation();
			
			while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
				
				dstabAlgo.solveDEqnStep(true);}
		}
		
		System.out.println(sm.toCSVString(sm.getBusAngleTable()));
		System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		
	}
	
	
	//@Test
	public void test_IEEE9_addFeeder_1pac_Dstab() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
				"testData/IEEE9Bus/ieee9_dyn.dyr"
				//"testData/IEEE9Bus/ieee9_dyn_fullModel.dyr"
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
	    
	    // remove the load from bus5
	    DStab3PBus bus5 = (DStab3PBus) dsNet.getBus("Bus5");
	    
	    bus5.setLoadCode(AclfLoadCode.NON_LOAD);
	    bus5.getContributeLoadList().remove(0);
	    
	    // add 69 kV and below distribution system
	    
		DStab3PBus bus10 = ThreePhaseObjectFactory.create3PDStabBus("Bus10", dsNet);
  		bus10.setAttributes("69kV sub", "");
  		bus10.setBaseVoltage(69000.0);
  		// set the bus to a non-generator bus
  		bus10.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus10.setLoadCode(AclfLoadCode.NON_LOAD);
  		
  		
		DStab3PBus bus11 = ThreePhaseObjectFactory.create3PDStabBus("Bus11", dsNet);
  		bus11.setAttributes("12.47 kV feeder", "");
  		bus11.setBaseVoltage(12470.0);
  		// set the bus to a non-generator bus
  		bus11.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus11.setLoadCode(AclfLoadCode.CONST_P);
  		
  		bus11.setLoadP(0.625);
  		bus11.setLoadQ(-0.05);
  		
		DStab3PBus bus12 = ThreePhaseObjectFactory.create3PDStabBus("Bus12", dsNet);
  		bus12.setAttributes("208 V feeder", "");
  		bus12.setBaseVoltage(208.0);
  		// set the bus to a non-generator bus
  		bus12.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus12.setLoadCode(AclfLoadCode.CONST_P);
  		
  		bus12.setLoadCode(AclfLoadCode.CONST_P);
  		
  		bus12.setLoadP(0.6);
  		bus12.setLoadQ(0.1);
  		
  		
		//////////////////transformers///////////////////////////////////////////
  		
		DStab3PBranch xfr5_10 = ThreePhaseObjectFactory.create3PBranch("Bus5", "Bus10", "0", dsNet);
		xfr5_10.setBranchCode(AclfBranchCode.XFORMER);
		xfr5_10.setZ( new Complex( 0.0, 0.08 ));
		xfr5_10.setZ0( new Complex(0.0, 0.08 ));
		
		
		AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr5_10);
		xfr0.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
		xfr0.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
  		
  		
		DStab3PBranch xfr10_11 = ThreePhaseObjectFactory.create3PBranch("Bus10", "Bus11", "0", dsNet);
		xfr10_11.setBranchCode(AclfBranchCode.XFORMER);
		xfr10_11.setZ( new Complex( 0.0, 0.06 ));
		xfr10_11.setZ0( new Complex(0.0, 0.06 ));
		
		AcscXformerAdapter xfr1 = acscXfrAptr.apply(xfr10_11);
		xfr1.setFromConnectGroundZ(XfrConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
		xfr1.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
		
	    
		
		DStab3PBranch xfr11_12 = ThreePhaseObjectFactory.create3PBranch("Bus11", "Bus12", "0", dsNet);
		xfr11_12.setBranchCode(AclfBranchCode.XFORMER);
		xfr11_12.setZ( new Complex( 0.0, 0.025 ));
		xfr11_12.setZ0( new Complex(0.0, 0.025 ));
		xfr11_12.setToTurnRatio(1.01);
		AcscXformerAdapter xfr2 = acscXfrAptr.apply(xfr11_12);
		xfr2.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
		xfr2.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
		
	    
	    /*
	     *   create the 1-phase AC model 
	     */
		

		
	    SinglePhaseACMotor ac1 = new SinglePhaseACMotor(bus12,"1");
  		ac1.setLoadPercent(100);
  		ac1.setPhase(PhaseCode.A);
  		ac1.setMvaBase(25);
  		bus12.getPhaseADynLoadList().add(ac1);
  		ac1.setVstall(0.65);
  		ac1.setTstall(0.05);
  		
  		SinglePhaseACMotor ac2 = new SinglePhaseACMotor(bus12,"2");
  		ac2.setLoadPercent(100);
  		ac2.setPhase(PhaseCode.B);
  		ac2.setMvaBase(25);
  		bus12.getPhaseBDynLoadList().add(ac2);
  		ac2.setVstall(0.65);
  		ac2.setTstall(0.05);

  		
  		SinglePhaseACMotor ac3 = new SinglePhaseACMotor(bus12,"3");
  		ac3.setLoadPercent(100);
  		ac3.setPhase(PhaseCode.C);
  		ac3.setMvaBase(25);
  		bus12.getPhaseCDynLoadList().add(ac3);
  		ac3.setVstall(0.65);
  		ac3.setTstall(0.05);
	    
	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(2);
		
	

		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		
		//applied the event
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus10",dsNet,SimpleFaultCode.GROUND_LG,new Complex(0.0),null,1d,0.07),"bus fault");
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus10","Bus11","Bus12","Bus5","Bus1"});
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		
		IpssLogger.getLogger().setLevel(Level.WARNING);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
        // Must use this dynamic event process to modify the YMatrixABC
		dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
		
		StringBuffer sb = new StringBuffer();
		
		if (dstabAlgo.initialization()) {
			System.out.println(ThreePhaseAclfOutFunc.busLfSummary(dsNet));
			
			System.out.println(dsNet.getMachineInitCondition());
			
			System.out.println("Running 3Phase DStab simulation ...");
			timer.start();
			//dstabAlgo.performSimulation();
			
			
			while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
				//System.out.println("\n\n simu Time = "+dstabAlgo.getSimuTime()+"\n");
				// access the terminal voltage, stage and power of the AC unit
				Complex3x1 vabc = bus12.get3PhaseVotlages();
				sb.append("t,AC Stage, voltage, power,"+dstabAlgo.getSimuTime()+","+ac1.getStage()+","+ac2.getStage()+","+ac3.getStage()+","
				+vabc.a_0.abs()+","+vabc.b_1.abs()+","+vabc.c_2.abs()+","
				+ac1.getLoadPQ().toString()+","+ac2.getLoadPQ().toString()+","+ac3.getLoadPQ().toString()+"\n");
				
				dstabAlgo.solveDEqnStep(true);
			}
		}
		
//		System.out.println(sm.toCSVString(sm.getBusAngleTable()));
		System.out.println(sm.toCSVString(sm.getBusVoltTable()));
//		FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//comprehensive_ch7//ieee9_dist_3p_SLG@Bus10_GenSpd.csv", sm.toCSVString(sm.getMachSpeedTable()));
//		FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//comprehensive_ch7//ieee9_dist_3p_SLG@Bus10_GenAngle.csv", sm.toCSVString(sm.getMachAngleTable()));
//		FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//comprehensive_ch7//ieee9_dist_3p_SLG@Bus10_busVolt.csv", sm.toCSVString(sm.getBusVoltTable()));
//		FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//comprehensive_ch7//ieee9_dist_3p_SLG@Bus10_ac_Results.txt",sb.toString());
	}
	
	

}
