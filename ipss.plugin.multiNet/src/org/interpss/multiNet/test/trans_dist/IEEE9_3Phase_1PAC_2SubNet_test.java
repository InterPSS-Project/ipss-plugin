package org.interpss.multiNet.test.trans_dist;

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
import org.interpss.dstab.dynLoad.LD1PAC;
import org.interpss.dstab.dynLoad.impl.LD1PACImpl;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.multiNet.algo.MultiNet3Ph3SeqDStabSimuHelper;
import org.interpss.multiNet.algo.MultiNet3Ph3SeqDStabSolverImpl;
import org.interpss.multiNet.algo.MultiNet3Ph3SeqDynEventProcessor;
import org.interpss.multiNet.algo.SubNetworkProcessor;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.model.impl.SinglePhaseACMotor;
import org.interpss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.core.acsc.adpter.AcscXformerAdapter;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class IEEE9_3Phase_1PAC_2SubNet_test {
	
	
	//@Test
	public void test_IEEE9_addFeeder_1pac_Dstab() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE9Bus/ieee9_dyn_Exciter.dyr"
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
	    
	    
	    addDistSys(dsNet);
	    
	    /////////////////////////////////////////////////////////////////////////////////
  		
  		
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		
		System.out.println("bus 5 total load:"+dsNet.getBranch("Bus5", "Bus10", "0").powerFrom2To());
		
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.2);
		
		
		///////////////////////////////////////////////////////////////////////////////
		
		// process subnetworks
		SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
	
		proc.addSubNetInterfaceBranch("Bus4->Bus5(0)",false);
	    proc.addSubNetInterfaceBranch("Bus5->Bus7(0)",true);
	
	    
	    proc.splitFullSystemIntoSubsystems(true);
	    
	    //TODO now one needs to set the three-phase modeling subnetwork by one of the bus the subnetwork contains
	    proc.set3PhaseSubNetByBusId("Bus10");
	    
	    MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
	  
		
		IpssLogger.getLogger().setLevel(Level.INFO);
		

        // TODO a special 3-phase 3seq dstab algorithm object, with the following two setting as default
		dstabAlgo.setSolver( new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
		dstabAlgo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(mNetHelper));
		
		
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus10",proc.getSubNetworkByBusId("Bus10"),SimpleFaultCode.GROUND_LG,new Complex(0,0),null,0.0d,0.07),"3phaseFault@Bus5");
		

		  //dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		
		  //applied the event                                                                                           Rfault = 3 ohm -> 0.063 pu
//		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus10",dsNet,SimpleFaultCode.GROUND_LG,new Complex(0.0,0),null,.5d,0.07),"3phaseFault@Bus5");
//		 // Must use this dynamic event process to modify the YMatrixABC
//		dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
		
		
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus10","Bus11","Bus12","Bus5","Bus1"});
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		
		IpssLogger.getLogger().setLevel(Level.WARNING);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
        StringBuffer sb = new StringBuffer();
        
        DStab3PBus bus12 = ((DStab3PBus)dsNet.getBus("Bus12"));
        SinglePhaseACMotor ac1 = (SinglePhaseACMotor) bus12.getPhaseADynLoadList().get(0);
        SinglePhaseACMotor ac2 = (SinglePhaseACMotor) bus12.getPhaseBDynLoadList().get(0);
        SinglePhaseACMotor ac3 = (SinglePhaseACMotor) bus12.getPhaseCDynLoadList().get(0);
		
		if (dstabAlgo.initialization()) {
			//System.out.println(ThreePhaseAclfOutFunc.busLfSummary(dsNet));
			
			//System.out.println(dsNet.getMachineInitCondition());
			
			System.out.println("Running 3Phase DStab simulation ...");
			
			//dstabAlgo.performSimulation();
			timer.start();
			
			while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
				
				Complex3x1 vabc = bus12.get3PhaseVotlages();
				sb.append("t,AC Stage, voltage, power,"+dstabAlgo.getSimuTime()+","+ac1.getStage()+","+ac2.getStage()+","+ac3.getStage()+","
				+vabc.a_0.abs()+","+vabc.b_1.abs()+","+vabc.c_2.abs()+","
				+ac1.getLoadPQ().toString()+","+ac2.getLoadPQ().toString()+","+ac3.getLoadPQ().toString()+"\n");
				
				dstabAlgo.solveDEqnStep(true);
			}
			timer.end();
			System.out.println("time = "+timer.getDuration());
		}
		
		//System.out.println(sm.toCSVString(sm.getBusAngleTable()));
		System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		System.out.println(sb.toString());
		
		//FileUtil.writeText2File("/testData/output/full_net_3P@Bus10_busVolts.csv",sm.toCSVString(sm.getBusVoltTable()));
		
		//FileUtil.writeText2File("/testData/output/ac_Results_0.6_0.05.txt",sb.toString());

	}
	
	
	
	private void addDistSys(DStabNetwork3Phase dsNet ) throws InterpssException{
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
  		
  		
//		Bus3Phase bus10a = ThreePhaseObjectFactory.create3PBus("Bus10a", dsNet);
//		bus10a.setAttributes("69 kV xfr HV", "");
//		bus10a.setBaseVoltage(69000.0);
//  		// set the bus to a non-generator bus
//		bus10a.setGenCode(AclfGenCode.NON_GEN);
//  		// set the bus to a constant power load bus
//		bus10a.setLoadCode(AclfLoadCode.NON_LOAD);
  		
  		
  		
		DStab3PBus bus11 = ThreePhaseObjectFactory.create3PDStabBus("Bus11", dsNet);
  		bus11.setAttributes("13.8 kV feeder starting end", "");
  		bus11.setBaseVoltage(13800.0);
  		// set the bus to a non-generator bus
  		bus11.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus11.setLoadCode(AclfLoadCode.NON_LOAD);
  		
  		
  		
		DStab3PBus bus11a = ThreePhaseObjectFactory.create3PDStabBus("Bus11a", dsNet);
  		bus11a.setAttributes("13.8 kV feeder terminal-end", "");
  		bus11a.setBaseVoltage(13800.0);
  		// set the bus to a non-generator bus
  		bus11a.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus11a.setLoadCode(AclfLoadCode.CONST_P);
  		bus11a.setLoadP(0.61);
  		bus11a.setLoadQ(0.1);
  		
		DStab3PBus bus12 = ThreePhaseObjectFactory.create3PDStabBus("Bus12", dsNet);
  		bus12.setAttributes("230 V feeder", "");
  		bus12.setBaseVoltage(230.0);
  		// set the bus to a non-generator bus
  		bus12.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus12.setLoadCode(AclfLoadCode.CONST_P);
  		
  		bus12.setLoadCode(AclfLoadCode.CONST_P);
  		
  		bus12.setLoadP(0.61);
  		bus12.setLoadQ(0.1);
  		
  		

  		
  		
		//////////////////transformers///////////////////////////////////////////
  		
		DStab3PBranch xfr5_10 = ThreePhaseObjectFactory.create3PBranch("Bus5", "Bus10", "0", dsNet);
		xfr5_10.setBranchCode(AclfBranchCode.XFORMER);
		xfr5_10.setZ( new Complex( 0.0, 0.0667 )); // 150 MVA ,X = 0.1
		xfr5_10.setZ0( new Complex(0.0, 0.0667 ));
		xfr5_10.setToTurnRatio(1.02);
		
		AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr5_10);
		xfr0.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
		xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
  		
		
//		Branch3Phase bra10_10a = ThreePhaseObjectFactory.create3PBranch("Bus10", "Bus10a", "1", dsNet);
//		 bra10_10a.setBranchCode(AclfBranchCode.LINE);
//		 bra10_10a.setZ( new Complex( 0.001, 0.01 )); // 100 MVA ,X = 0.1
//		 bra10_10a.setZ0( new Complex(0.0025, 0.025 ));
//		 bra10_10a.setHShuntY(new Complex(0,0.1));
//		 bra10_10a.setHB0(0.1);
		
  		
		DStab3PBranch xfr10_11 = ThreePhaseObjectFactory.create3PBranch("Bus10", "Bus11", "0", dsNet);
		xfr10_11.setBranchCode(AclfBranchCode.XFORMER);
		xfr10_11.setZ( new Complex( 0.0, 0.0532 )); // 150 MVA ,X = 0.08
		xfr10_11.setZ0( new Complex(0.0, 0.0532 ));
		xfr10_11.setToTurnRatio(1.02);
		AcscXformerAdapter xfr1 = acscXfrAptr.apply(xfr10_11);
		xfr1.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0.0,0.0), UnitType.PU);
		xfr1.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
		
	    
		
		DStab3PBranch xfr11_12 = ThreePhaseObjectFactory.create3PBranch("Bus11a", "Bus12", "0", dsNet);
		xfr11_12.setBranchCode(AclfBranchCode.XFORMER);
		xfr11_12.setZ( new Complex( 0.0, 0.025 ));
		xfr11_12.setZ0( new Complex(0.0, 0.025 ));
		xfr11_12.setToTurnRatio(1.01);
		AcscXformerAdapter xfr2 = acscXfrAptr.apply(xfr11_12);
		xfr2.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
		xfr2.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
		
		
		DStab3PBranch feeder = ThreePhaseObjectFactory.create3PBranch("Bus11", "Bus11a", "1", dsNet);
		feeder.setBranchCode(AclfBranchCode.LINE);
		feeder.setZ( new Complex( 0.01, 0.02 )); // 100 MVA ,X = 0.1
		feeder.setZ0( new Complex(0.025, 0.05 ));
		
		
		
	    
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
	}
	
	
	@Test
	public void test_IEEE9_addFeeder_1pac_Positive_Seq_Dstab() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE9Bus/ieee9_dyn_Exciter.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		
		// The only change to the normal data import is the use of ODM3PhaseDStabParserMapper
		if (!new ODMDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabilityNetwork dsNet =(DStabilityNetwork) simuCtx.getDStabilityNet();
	    
	    
	    addSinglePhaseDistSys(dsNet);
	    
	    /////////////////////////////////////////////////////////////////////////////////
  		
  		
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		
		System.out.println("bus 5 total load:"+dsNet.getBranch("Bus5", "Bus10", "0").powerFrom2To());
		
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(10.0);
		
		
//		///////////////////////////////////////////////////////////////////////////////
//		
//		// process subnetworks
//		SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
//	
//		proc.addSubNetInterfaceBranch("Bus4->Bus5(0)",false);
//	    proc.addSubNetInterfaceBranch("Bus5->Bus7(0)",true);
//	
//	    
//	    proc.splitFullSystemIntoSubsystems(true);
//	    
//	    //TODO now one needs to set the three-phase modeling subnetwork by one of the bus the subnetwork contains
//	    proc.set3PhaseSubNetByBusId("Bus10");
//	    
//	    MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
//	  
//		
//		IpssLogger.getLogger().setLevel(Level.INFO);
//		
//
//        // TODO a special 3-phase 3seq dstab algorithm object, with the following two setting as default
//		dstabAlgo.setSolver( new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
//		dstabAlgo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(mNetHelper));
//		
		
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus10",dsNet,SimpleFaultCode.GROUND_LG,new Complex(0,0),null,0.5d,0.07),"3phaseFault@Bus5");
		

		  //dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		
		  //applied the event                                                                                           Rfault = 3 ohm -> 0.063 pu
//		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus10",dsNet,SimpleFaultCode.GROUND_LG,new Complex(0.0,0),null,.5d,0.07),"3phaseFault@Bus5");
//		 // Must use this dynamic event process to modify the YMatrixABC
//		dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
		
		
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus10","Bus11","Bus12","Bus5","Bus1"});
		sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus12");
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		
		IpssLogger.getLogger().setLevel(Level.WARNING);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
        StringBuffer sb = new StringBuffer();
        

		
		if (dstabAlgo.initialization()) {
			//System.out.println(ThreePhaseAclfOutFunc.busLfSummary(dsNet));
			
			//System.out.println(dsNet.getMachineInitCondition());
			
			System.out.println("Running 3Phase DStab simulation ...");
			
			//dstabAlgo.performSimulation();
			timer.start();
			
			while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
				
				dstabAlgo.solveDEqnStep(true);
			}
			timer.end();
			System.out.println("time = "+timer.getDuration());
		}
		
		//System.out.println(sm.toCSVString(sm.getBusAngleTable()));
		System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		System.out.println(sb.toString());
		
		System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
		System.out.println(sm.toCSVString(sm.getAcMotorQTable()));
		FileUtil.writeText2File("output//ieee9_equivFeeder_1AC_BusVolt.csv",sm.toCSVString(sm.getBusVoltTable()));
		//FileUtil.writeText2File("/testData/output/full_net_3P@Bus10_busVolts.csv",sm.toCSVString(sm.getBusVoltTable()));
		
		//FileUtil.writeText2File("/testData/output/ac_Results_0.6_0.05.txt",sb.toString());
	}
	
	private void addSinglePhaseDistSys(DStabilityNetwork dsNet ) throws InterpssException{
		 // remove the load from bus5
		DStabBus bus5 = dsNet.getBus("Bus5");
	    
	    bus5.setLoadCode(AclfLoadCode.NON_LOAD);
	    bus5.getContributeLoadList().remove(0);
	    
	    // add 69 kV and below distribution system
	    
		DStabBus bus10 = (DStabBus) DStabObjectFactory.createDStabBus("Bus10", dsNet).get();
 		bus10.setAttributes("69kV sub", "");
 		bus10.setBaseVoltage(69000.0);
 		// set the bus to a non-generator bus
 		bus10.setGenCode(AclfGenCode.NON_GEN);
 		// set the bus to a constant power load bus
 		bus10.setLoadCode(AclfLoadCode.NON_LOAD);
 		
 		
//		Bus3Phase bus10a = ThreePhaseObjectFactory.create3PBus("Bus10a", dsNet);
//		bus10a.setAttributes("69 kV xfr HV", "");
//		bus10a.setBaseVoltage(69000.0);
// 		// set the bus to a non-generator bus
//		bus10a.setGenCode(AclfGenCode.NON_GEN);
// 		// set the bus to a constant power load bus
//		bus10a.setLoadCode(AclfLoadCode.NON_LOAD);
 		
 		
 		
 		DStabBus bus11 = (DStabBus) DStabObjectFactory.createDStabBus("Bus11", dsNet).get();
 		bus11.setAttributes("13.8 kV feeder starting end", "");
 		bus11.setBaseVoltage(13800.0);
 		// set the bus to a non-generator bus
 		bus11.setGenCode(AclfGenCode.NON_GEN);
 		// set the bus to a constant power load bus
 		bus11.setLoadCode(AclfLoadCode.NON_LOAD);
 		
 		
 		
 		DStabBus bus11a = (DStabBus) DStabObjectFactory.createDStabBus("Bus11a", dsNet).get();
 		bus11a.setAttributes("13.8 kV feeder terminal-end", "");
 		bus11a.setBaseVoltage(13800.0);
 		// set the bus to a non-generator bus
 		bus11a.setGenCode(AclfGenCode.NON_GEN);
 		// set the bus to a constant power load bus
 		bus11a.setLoadCode(AclfLoadCode.CONST_P);
 		bus11a.setLoadP(0.61);
 		bus11a.setLoadQ(0.1);
 		
 		
 		DStabBus bus12 = (DStabBus) DStabObjectFactory.createDStabBus("Bus12", dsNet).get();
 		bus12.setAttributes("230 V feeder", "");
 		bus12.setBaseVoltage(230.0);
 		// set the bus to a non-generator bus
 		bus12.setGenCode(AclfGenCode.NON_GEN);
 		// set the bus to a constant power load bus
 		bus12.setLoadCode(AclfLoadCode.CONST_P);
 		
 		bus12.setLoadCode(AclfLoadCode.CONST_P);
 		
 		bus12.setLoadP(0.61);
 		bus12.setLoadQ(0.1);
 		
 		

 		
 		
		//////////////////transformers///////////////////////////////////////////
 		
		DStab3PBranch xfr5_10 = ThreePhaseObjectFactory.create3PBranch("Bus5", "Bus10", "0", dsNet);
		xfr5_10.setBranchCode(AclfBranchCode.XFORMER);
		xfr5_10.setZ( new Complex( 0.0, 0.0667 )); // 150 MVA ,X = 0.1
		xfr5_10.setZ0( new Complex(0.0, 0.0667 ));
		xfr5_10.setToTurnRatio(1.02);
		
		AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr5_10);
		xfr0.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
		xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
 		
		
//		Branch3Phase bra10_10a = ThreePhaseObjectFactory.create3PBranch("Bus10", "Bus10a", "1", dsNet);
//		 bra10_10a.setBranchCode(AclfBranchCode.LINE);
//		 bra10_10a.setZ( new Complex( 0.001, 0.01 )); // 100 MVA ,X = 0.1
//		 bra10_10a.setZ0( new Complex(0.0025, 0.025 ));
//		 bra10_10a.setHShuntY(new Complex(0,0.1));
//		 bra10_10a.setHB0(0.1);
		
 		
		DStab3PBranch xfr10_11 = ThreePhaseObjectFactory.create3PBranch("Bus10", "Bus11", "0", dsNet);
		xfr10_11.setBranchCode(AclfBranchCode.XFORMER);
		xfr10_11.setZ( new Complex( 0.0, 0.0532 )); // 150 MVA ,X = 0.08
		xfr10_11.setZ0( new Complex(0.0, 0.0532 ));
		xfr10_11.setToTurnRatio(1.02);
		AcscXformerAdapter xfr1 = acscXfrAptr.apply(xfr10_11);
		xfr1.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0.0,0.0), UnitType.PU);
		xfr1.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
		
	    
		
		DStab3PBranch xfr11_12 = ThreePhaseObjectFactory.create3PBranch("Bus11a", "Bus12", "0", dsNet);
		xfr11_12.setBranchCode(AclfBranchCode.XFORMER);
		xfr11_12.setZ( new Complex( 0.0, 0.025 ));
		xfr11_12.setZ0( new Complex(0.0, 0.025 ));
		xfr11_12.setToTurnRatio(1.01);
		AcscXformerAdapter xfr2 = acscXfrAptr.apply(xfr11_12);
		xfr2.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
		xfr2.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
		
		
		DStab3PBranch feeder = ThreePhaseObjectFactory.create3PBranch("Bus11", "Bus11a", "1", dsNet);
		feeder.setBranchCode(AclfBranchCode.LINE);
		feeder.setZ( new Complex( 0.01, 0.02 )); // 100 MVA ,X = 0.1
		feeder.setZ0( new Complex(0.025, 0.05 ));
		
		
		
	    
	    /*
	     *   create the 1-phase AC model 
	     *   
	     *   	    SinglePhaseACMotor ac1 = new SinglePhaseACMotor(bus12,"1");
			  		ac1.setLoadPercent(100);
			  		ac1.setPhase(Phase.A);
			  		ac1.setMVABase(25);
			  		bus12.getPhaseADynLoadList().add(ac1);
			  		ac1.setVstall(0.65);
			  		ac1.setTstall(0.05);
	     */
		LD1PAC acLoad= new LD1PACImpl(bus12,"1");
		
		acLoad.setLoadPercent(100);
		acLoad.setMvaBase(75);
		acLoad.setVstall(0.65);
		acLoad.setTstall(0.05);
		
	
	}

}
