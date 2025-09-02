package org.interpss.multiNet.trans_dist;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;
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
import org.interpss.dstab.dynLoad.impl.InductionMotorImpl;
import org.interpss.multiNet.algo.MultiNet3Ph3SeqDStabSimuHelper;
import org.interpss.multiNet.algo.MultiNet3Ph3SeqDStabSolverImpl;
import org.interpss.multiNet.algo.MultiNet3Ph3SeqDynEventProcessor;
import org.interpss.multiNet.algo.SubNetworkProcessor;
import org.interpss.multiNet.algo.powerflow.TDMultiNetPowerflowAlgorithm;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.threePhase.basic.IEEEFeederLineCode;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.model.InductionMotor3PhaseAdapter;
import org.interpss.threePhase.dynamic.model.impl.SinglePhaseACMotor;
import org.interpss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.interpss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.core.acsc.adpter.AcscXformerAdapter;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class TestTnD_IEEE9_8BusFeeder {
	
	//@Test
	public void test_IEEE9_8Busfeeder_powerflow() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.WARNING);
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
				"testData/IEEE9Bus/ieee9_dyn.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();


		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		
		// The only change to the normal data import is the use of ODM3PhaseDStabParserMapper
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
	    double PVPenetrationLevel = .00;
	    double PVIncrement = PVPenetrationLevel/(1-PVPenetrationLevel) ;
	    double ACMotorPercent = 40;
	    double IndMotorPercent = 5;
	    double ACPhaseUnbalance = 5.0;
	   
	    
	
	    
	   
	    double baseVolt = 12470;
		int feederBusNum = 9;
		
		double loadPF = 0.95;
		double loadUnbalanceFactor = 0.00; //at the scale of 0 to 1
		
		double [] loadDistribution = new double[]{0.25,0.20,0.15,0.15,0.1,0.1,0.05};
		double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.0,1.5,2,2}; // unit in mile
		//double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.5,0.5,0.5, 1}; // unit in mile
		
		/**
		 * --------------------- Feeders below Bus 5---------------------------- 
		 */
		
	    dsNet.getBus("Bus5").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus5").setLoadCode(AclfLoadCode.NON_LOAD);
	    
		 double netTotalLoad = 120;
		 double totalLoad = netTotalLoad*(1+PVIncrement);
		 double XfrMVA = 150;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus5"), 10, baseVolt,feederBusNum,totalLoad,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
	
		/**
		 * --------------------- Feeders below Bus 6---------------------------- 
		 */
		
		   dsNet.getBus("Bus6").getContributeLoadList().remove(0);
		    dsNet.getBus("Bus6").setLoadCode(AclfLoadCode.NON_LOAD);
		
		 double netTotalLoadBus6 = 90;
		 double totalLoadBus6 = netTotalLoadBus6*(1+PVIncrement);
		 XfrMVA = 120;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus6"), 20, baseVolt,feederBusNum,totalLoadBus6,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		
		/**
		 * --------------------- Feeders below Bus 8---------------------------- 
		 */
		 dsNet.getBus("Bus8").getContributeLoadList().remove(0);
		 dsNet.getBus("Bus8").setLoadCode(AclfLoadCode.NON_LOAD);
		 double netTotalLoadBus8 = 100;
		 double totalLoadBus8 = netTotalLoadBus8*(1+PVIncrement);
		 XfrMVA = 150;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus8"), 30, baseVolt,feederBusNum,totalLoadBus8,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		
		
		  
	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		 proc.addSubNetInterfaceBranch("Bus5->Bus10(0)",false);
		 proc.addSubNetInterfaceBranch("Bus6->Bus20(0)",false);
		 proc.addSubNetInterfaceBranch("Bus8->Bus30(0)",false);
		 
		 proc.splitFullSystemIntoSubsystems(true);
		 
		 // currently, if a fault at transmission system is to be considered, then it should be set to 3phase
		 proc.set3PhaseSubNetByBusId("Bus5");
		//TODO this has to be manually identified
		 proc.set3PhaseSubNetByBusId("Bus11");
		 proc.set3PhaseSubNetByBusId("Bus21");
		 proc.set3PhaseSubNetByBusId("Bus31");
		 
		 
		 System.out.println("external boundary bus: "+proc.getExternalSubNetBoundaryBusIdList());
		 
		 System.out.println("internal boundary bus: "+proc.getInternalSubNetBoundaryBusIdList());
		 
	    
	    
	    //TODO create TDMultiNetPowerflowAlgo
	    
		 TDMultiNetPowerflowAlgorithm tdAlgo = new TDMultiNetPowerflowAlgorithm((BaseAclfNetwork<? extends BaseAclfBus<?,?>, ? extends AclfBranch>) dsNet,proc);
		 
		 System.out.println(tdAlgo.getTransmissionNetwork().net2String());
	     
		 assertTrue(tdAlgo.powerflow()); 
		 
		 
		 
		 System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
		 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
		 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(1)));
		 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(2)));
	
	
	}
	
	
	
	@Test
	public void test_IEEE9_8Busfeeder_dynSim() throws InterpssException{
		IpssCorePlugin.init();
		
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		
		
		/**
		 * -----------------------------------------------------------
		 *  import the transmission network data
		 *  ----------------------------------------------------------
		 */
		
		
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
				//"testData/IEEE9Bus/ieee9_dyn.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		
		// The only change to the normal data import is the use of ODM3PhaseDStabParserMapper
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
	    double PVPenetrationLevel = .00;
	    double PVIncrement = PVPenetrationLevel/(1-PVPenetrationLevel) ;
	    double ACMotorPercent = 10;
	    double IndMotorPercent = 0;
	    double ACPhaseUnbalance = 0.0;
	   
	   
	    double baseVolt = 12470;
		int feederBusNum = 9;
		
		double loadPF = 0.95;
		double loadUnbalanceFactor = 0.00;
		
		double [] loadDistribution = new double[]{0.25,0.20,0.15,0.15,0.1,0.1,0.05};
		double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.0,1.5,2,2}; // unit in mile
		//double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.5,0.5,0.5, 1}; // unit in mile
		
			
		
		/**
		 * -----------------------------------------------------------
		 *  create the distribution systems to replace the original loads at the transmission buses
		 *  ----------------------------------------------------------
		 */
		
		
		/**
		 * --------------------- Feeders below Bus 5---------------------------- 
		 */
		
	    dsNet.getBus("Bus5").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus5").setLoadCode(AclfLoadCode.NON_LOAD);
	    
		 double netTotalLoad = 120;
		 double totalLoad = netTotalLoad*(1+PVIncrement);
		 double XfrMVA = 150;
		int startBusIndex = 10;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus5"), startBusIndex, baseVolt,feederBusNum,totalLoad,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoad,loadDistribution);
		
	
		/**
		 * --------------------- Feeders below Bus 6---------------------------- 
		 */
		
		   dsNet.getBus("Bus6").getContributeLoadList().remove(0);
		    dsNet.getBus("Bus6").setLoadCode(AclfLoadCode.NON_LOAD);
		
		 double netTotalLoadBus6 = 90;
		 double totalLoadBus6 = netTotalLoadBus6*(1+PVIncrement);
		 XfrMVA = 120;
		 
		 startBusIndex = 20;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus6"), startBusIndex, baseVolt,feederBusNum,totalLoadBus6,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus6,loadDistribution);
		
		
		
		/**
		 * --------------------- Feeders below Bus 8---------------------------- 
		 */
		 dsNet.getBus("Bus8").getContributeLoadList().remove(0);
		 dsNet.getBus("Bus8").setLoadCode(AclfLoadCode.NON_LOAD);
		 
		 double netTotalLoadBus8 = 100;
		 double totalLoadBus8 = netTotalLoadBus8*(1+PVIncrement);
		 XfrMVA = 150;
		 
		 startBusIndex = 30;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus8"), startBusIndex, baseVolt,feederBusNum,totalLoadBus8,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus8,loadDistribution);
		
		
		/**
		 * -----------------------------------------------------------
		 *  split the T&D network into 4 subnetworks
		 *  ----------------------------------------------------------
		 */
		  
	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		 proc.addSubNetInterfaceBranch("Bus5->Bus10(0)",false);
		 
//	     proc.addSubNetInterfaceBranch("Bus4->Bus5(0)",false);
//	     proc.addSubNetInterfaceBranch("Bus5->Bus7(0)",true);
	    
		 proc.addSubNetInterfaceBranch("Bus6->Bus20(0)",false);
		 proc.addSubNetInterfaceBranch("Bus8->Bus30(0)",false);
		 
		 proc.splitFullSystemIntoSubsystems(true);
		 
		 // currently, if a fault at transmission system is to be considered, then it should be set to 3phase
		 //proc.set3PhaseSubNetByBusId("Bus4");
		//TODO this has to be manually identified
		 proc.set3PhaseSubNetByBusId("Bus11");
		 proc.set3PhaseSubNetByBusId("Bus21");
		 proc.set3PhaseSubNetByBusId("Bus31");
		 
		 
		 System.out.println("external boundary bus: "+proc.getExternalSubNetBoundaryBusIdList());
		 
		 System.out.println("internal boundary bus: "+proc.getInternalSubNetBoundaryBusIdList());
		 
	    
	    
	    //TODO create TDMultiNetPowerflowAlgo
	    
		 TDMultiNetPowerflowAlgorithm tdAlgo = new TDMultiNetPowerflowAlgorithm((BaseAclfNetwork<? extends BaseAclfBus<?,?>, ? extends AclfBranch>) dsNet,proc);
		
		 //System.out.println(tdAlgo.getTransmissionNetwork().net2String());
	    
		 assertTrue(tdAlgo.powerflow()); 
		 
		 
		 
		 System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
		 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
		 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(1)));
		 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(2)));
		 
		 
		 

		  MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
		  
		  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
		  DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
		    
		  
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(1.5);
			

			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			
			//applied the event
//			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus10",proc.getSubNetworkByBusId("Bus10"),SimpleFaultCode.GROUND_LG,new Complex(0.0),new Complex(0.0),1.0d,0.07),"3phaseFault@Bus5");
	        
			dsNet.addDynamicEvent(
					DStabObjectFactory.createBusFaultEvent("Bus10", proc.getSubNetworkByBusId("Bus10"),
							SimpleFaultCode.GROUND_3P, new Complex(0.0), new Complex(0.0), 0.5, 0.1),
					"3phaseFault@Bus10");
			
			StateMonitor sm = new StateMonitor();
			sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
			sm.addBusStdMonitor(new String[]{"Bus38","Bus32","Bus28","Bus24","Bus22","Bus18","Bus14","Bus12","Bus8","Bus6", "Bus5","Bus4","Bus1"});
			sm.add3PhaseBusStdMonitor(new String[]{"Bus38","Bus34","Bus32","Bus28","Bus24","Bus22","Bus18","Bus15","Bus14","Bus12","Bus11","Bus10"});
			//String[] seqVotBusAry = new String[]{"Bus5","Bus4","Bus7"};
			//sm.add3PhaseBusStdMonitor(seqVotBusAry);
			
			sm.addBranchStdMonitor("Bus5->Bus10Dummy(1)");
			sm.addBranchStdMonitor("Bus5->Bus7(1)");
			
			//1Phase AC motor extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getParentBus().getId()+"_phase"+this.getPhase();
			
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus12_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus12_phaseB");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus12_phaseC");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus14_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus14_phaseB");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus14_phaseC");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus18_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus22_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus24_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus24_phaseB");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus24_phaseC");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus28_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus32_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus32_phaseB");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus32_phaseC");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus34_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus38_phaseA");
			
			//3phase induction motor extended_device_Id = "IndMotor_"+this.getId()+"@"+this.getDStabBus().getId();
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus12");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus14");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus18");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus22");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus24");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus28");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus32");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus34");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus38");
			
			// PV gen
			// extended_device_Id = "PVGen3Phase_"+this.getId()+"@"+this.getParentGen().getParentBus().getId();
//			
//			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus12");
//			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus18");
//			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus22");
//			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus28");
//			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus32");
//			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus38");
			
			
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(5);
			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			
			//IpssLogger.getLogger().setLevel(Level.WARNING);
			
			PerformanceTimer timer = new PerformanceTimer();
			timer.start();
	        // Must use this dynamic event process to modify the YMatrixABC
//			dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
			
			dstabAlgo.setSolver( new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
			dstabAlgo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(mNetHelper));
			
			if (dstabAlgo.initialization()) {
				//System.out.println(ThreePhaseAclfOutFunc.busLfSummary(dsNet));
				
				//System.out.println(dsNet.getMachineInitCondition());
				
				System.out.println("Running 3Phase/3sequence DStab co-simulation ...");
				
				//dstabAlgo.performSimulation();
				
				while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
					
					dstabAlgo.solveDEqnStep(true);
					
					for(String busId: sm.getBusPhAVoltTable().keySet()){
						
						 sm.addBusPhaseVoltageMonitorRecord( busId,dstabAlgo.getSimuTime(), ((DStab3PBus)proc.getSubNetworkByBusId(busId).getBus(busId)).get3PhaseVotlages());
					}
					
				}
			}
			timer.end();
			System.out.println("total sim time = "+timer.getDuration());
//			System.out.println(sm.toCSVString(sm.getBusVoltTable()));
			System.out.println(sm.toCSVString(sm.getBusPhAVoltTable()));
			//System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
			System.out.println(sm.toCSVString(sm.getAcMotorStateTable()));
			//System.out.println("Branch flow p ="+sm.toCSVString(sm.getBranchFlowPTable()));
			
			
			MonitorRecord volt_rec1 = sm.getBusVoltTable().get("Bus38").get(0);
		  	MonitorRecord volt_rec51 = sm.getBusVoltTable().get("Bus38").get(50);
		  	assertTrue(Math.abs(volt_rec1.getValue()-volt_rec51.getValue())<1.0E-3);
		  	
		  	
			MonitorRecord angle_rec1 = sm.getBusAngleTable().get("Bus32").get(0);
		  	MonitorRecord angle_rec51 = sm.getBusAngleTable().get("Bus32").get(50);
		  	assertTrue(Math.abs(angle_rec1.getValue()-angle_rec51.getValue())<1.0E-1);
			
			
			/*
			FileUtil.writeText2File("output/busVoltage.csv", sm.toCSVString(sm.getBusVoltTable()));
			*/

	
	}
	
	//@Test
	public void test_IEEE9_8Busfeeder_constZload_dynSim() throws InterpssException{
		IpssCorePlugin.init();
		
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		
		
		/**
		 * -----------------------------------------------------------
		 *  import the transmission network data
		 *  ----------------------------------------------------------
		 */
		
		
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
				//"testData/IEEE9Bus/ieee9_dyn.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		
		// The only change to the normal data import is the use of ODM3PhaseDStabParserMapper
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
	    double PVPenetrationLevel = .00;
	    double PVIncrement = PVPenetrationLevel/(1-PVPenetrationLevel) ;
	    double ACMotorPercent = 50;
	    double IndMotorPercent = 0;
	    double ACPhaseUnbalance = 0.0;
	   
	   
	    double baseVolt = 12470;
		int feederBusNum = 9;
		
		double loadPF = 0.95;
		double loadUnbalanceFactor = 0.00;
		
		double [] loadDistribution = new double[]{0.25,0.20,0.15,0.15,0.1,0.1,0.05};
		double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.0,1.5,2,2}; // unit in mile
		//double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.5,0.5,0.5, 1}; // unit in mile
		
			
		
		/**
		 * -----------------------------------------------------------
		 *  create the distribution systems to replace the original loads at the transmission buses
		 *  ----------------------------------------------------------
		 */
		
		
		/**
		 * --------------------- Feeders below Bus 5---------------------------- 
		 */
		
	    dsNet.getBus("Bus5").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus5").setLoadCode(AclfLoadCode.NON_LOAD);
	    
		 double netTotalLoad = 120;
		 double totalLoad = netTotalLoad*(1+PVIncrement);
		 double XfrMVA = 150;
		int startBusIndex = 10;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus5"), startBusIndex, baseVolt,feederBusNum,totalLoad,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		//buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoad,loadDistribution);
		
	
		/**
		 * --------------------- Feeders below Bus 6---------------------------- 
		 */
		
		   dsNet.getBus("Bus6").getContributeLoadList().remove(0);
		    dsNet.getBus("Bus6").setLoadCode(AclfLoadCode.NON_LOAD);
		
		 double netTotalLoadBus6 = 90;
		 double totalLoadBus6 = netTotalLoadBus6*(1+PVIncrement);
		 XfrMVA = 120;
		 
		 startBusIndex = 20;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus6"), startBusIndex, baseVolt,feederBusNum,totalLoadBus6,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		//buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus6,loadDistribution);
		
		
		
		/**
		 * --------------------- Feeders below Bus 8---------------------------- 
		 */
		 dsNet.getBus("Bus8").getContributeLoadList().remove(0);
		 dsNet.getBus("Bus8").setLoadCode(AclfLoadCode.NON_LOAD);
		 
		 double netTotalLoadBus8 = 100;
		 double totalLoadBus8 = netTotalLoadBus8*(1+PVIncrement);
		 XfrMVA = 150;
		 
		 startBusIndex = 30;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus8"), startBusIndex, baseVolt,feederBusNum,totalLoadBus8,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		//buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus8,loadDistribution);
		
		
		/**
		 * -----------------------------------------------------------
		 *  split the T&D network into 4 subnetworks
		 *  ----------------------------------------------------------
		 */
		  
	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		// proc.addSubNetInterfaceBranch("Bus5->Bus10(0)",false);
		 
	     proc.addSubNetInterfaceBranch("Bus4->Bus5(0)",false);
	     proc.addSubNetInterfaceBranch("Bus5->Bus7(0)",true);
	    
		 proc.addSubNetInterfaceBranch("Bus6->Bus20(0)",false);
		 proc.addSubNetInterfaceBranch("Bus8->Bus30(0)",false);
		 
		 proc.splitFullSystemIntoSubsystems(true);
		 
		 // currently, if a fault at transmission system is to be considered, then it should be set to 3phase
		 proc.set3PhaseSubNetByBusId("Bus4");
		//TODO this has to be manually identified
		 proc.set3PhaseSubNetByBusId("Bus11");
		 proc.set3PhaseSubNetByBusId("Bus21");
		 proc.set3PhaseSubNetByBusId("Bus31");
		 
		 
		 System.out.println("external boundary bus: "+proc.getExternalSubNetBoundaryBusIdList());
		 
		 System.out.println("internal boundary bus: "+proc.getInternalSubNetBoundaryBusIdList());
		 
	    
	    
	    //TODO create TDMultiNetPowerflowAlgo
	    
		 TDMultiNetPowerflowAlgorithm tdAlgo = new TDMultiNetPowerflowAlgorithm((BaseAclfNetwork<? extends BaseAclfBus<?,?>, ? extends AclfBranch>) dsNet,proc);
		
		 System.out.println(tdAlgo.getTransmissionNetwork().net2String());
	    
		 assertTrue(tdAlgo.powerflow()); 
		 
		 
		 
		 System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
		 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
		 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(1)));
		 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(2)));
		 
		 
		 

		  MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
		  
		  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
		  DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
		    
		  
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(5.00);
			

			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			
			//applied the event
			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus10",proc.getSubNetworkByBusId("Bus10"),SimpleFaultCode.GROUND_LG,new Complex(0.0),new Complex(0.0),1.0d,0.07),"3phaseFault@Bus5");
	        
			//dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",proc.getSubNetworkByBusId("Bus5"),SimpleFaultCode.GROUND_3P,new Complex(0.0),new Complex(0.0),0.5d,0.07),"3phaseFault@Bus5");
	        
			
			StateMonitor sm = new StateMonitor();
			sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
			sm.addBusStdMonitor(new String[]{"Bus38","Bus32","Bus28","Bus24","Bus22","Bus18","Bus14","Bus12","Bus8","Bus6", "Bus5","Bus4","Bus1"});
			sm.add3PhaseBusStdMonitor(new String[]{"Bus2","Bus10","Bus5"});
			
			
			// PV gen
			// extended_device_Id = "PVGen3Phase_"+this.getId()+"@"+this.getParentGen().getParentBus().getId();
//			
//			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus12");
//			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus18");
//			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus22");
//			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus28");
//			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus32");
//			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus38");
			
			
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(1);
			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			
			//IpssLogger.getLogger().setLevel(Level.WARNING);
			
			PerformanceTimer timer = new PerformanceTimer();
			timer.start();
	        // Must use this dynamic event process to modify the YMatrixABC
//			dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
			
			dstabAlgo.setSolver( new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
			dstabAlgo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(mNetHelper));
			
			if (dstabAlgo.initialization()) {
				//System.out.println(ThreePhaseAclfOutFunc.busLfSummary(dsNet));
				
				//System.out.println(dsNet.getMachineInitCondition());
				
				System.out.println("Running 3Phase/3sequence DStab co-simulation ...");
				
				//dstabAlgo.performSimulation();
				
				while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
					
					dstabAlgo.solveDEqnStep(true);
					
					for(String busId: sm.getBusPhAVoltTable().keySet()){
						
						 sm.addBusPhaseVoltageMonitorRecord( busId,dstabAlgo.getSimuTime(), ((DStab3PBus)proc.getSubNetworkByBusId(busId).getBus(busId)).get3PhaseVotlages());
					}
					
				}
			}
			timer.end();
			System.out.println("total sim time = "+timer.getDuration());
			//System.out.println(sm.toCSVString(sm.getBusVoltTable()));
			//System.out.println(sm.toCSVString(sm.getBusAngleTable()));
			//System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
			//System.out.println(sm.toCSVString(sm.getAcMotorStateTable()));
			
			MonitorRecord volt_rec1 = sm.getBusVoltTable().get("Bus38").get(0);
		  	MonitorRecord volt_rec51 = sm.getBusVoltTable().get("Bus38").get(50);
		  	assertTrue(Math.abs(volt_rec1.getValue()-volt_rec51.getValue())<1.0E-3);
		  	
		  	
			MonitorRecord angle_rec1 = sm.getBusAngleTable().get("Bus32").get(0);
		  	MonitorRecord angle_rec51 = sm.getBusAngleTable().get("Bus32").get(50);
		  	assertTrue(Math.abs(angle_rec1.getValue()-angle_rec51.getValue())<1.0E-1);

	
	}
	
	
	
	/**
	 * The first bus is feeder sending end, no load is connected; all the loads are connected at bus [2,...BusNum];
	 * 
	 * The base case of the feeder is assumed to serve 8 MW load, feeder impedances are re-scaled based on the totalMW
	 * @param totalMW
	 * @return
	 * @throws InterpssException
	 */
public DStabNetwork3Phase createFeeder(DStabNetwork3Phase net,DStab3PBus sourceBus, int startBusNum, double baseVolt, int BusNum, double totalMW, double XfrMVA, double loadPF, double[] loadPercentAry, double loadUnbalanceFactor, double[] sectionLength) throws InterpssException{
		
	    double scaleFactor = totalMW;
	    double zscaleFactor =  totalMW/8.0; 
	    double q2pfactor = Math.tan(Math.acos(loadPF));
	
		
		int loadIdx = 0;
		for(int i =startBusNum;i<startBusNum+BusNum;i++){
			DStab3PBus bus = ThreePhaseObjectFactory.create3PDStabBus("Bus"+i, net);
			bus.setAttributes("feeder bus "+i, "");
			bus.setBaseVoltage(baseVolt);
			
			
			// set the bus to a non-generator bus
			bus.setGenCode(AclfGenCode.GEN_PQ);
			// set the bus to a constant power load bus
			bus.setLoadCode(AclfLoadCode.CONST_P);
			
			if(i ==startBusNum){
				bus.setBaseVoltage(69000.0);
				bus.setGenCode(AclfGenCode.NON_GEN);
				// set the bus to a constant power load bus
				bus.setLoadCode(AclfLoadCode.NON_LOAD);
			}
			
			if(i>=startBusNum+2){
				DStab3PLoad load1 = new DStab3PLoadImpl();
				Complex3x1 load3Phase = new Complex3x1(new Complex(0.01,0.01*q2pfactor),new Complex(0.01,0.01*q2pfactor).multiply(1-loadUnbalanceFactor),new Complex(0.01,0.01*q2pfactor).multiply(1+loadUnbalanceFactor));
				load1.set3PhaseLoad(load3Phase.multiply(scaleFactor*loadPercentAry[loadIdx++]));
				bus.getThreePhaseLoadList().add(load1);
			}
			// shunt compensation
//			if(i ==startBusNum+3 || i == startBusNum+5 || i==startBusNum+7){
//				Load3Phase Shuntload = new Load3PhaseImpl();
//				Shuntload.setCode(AclfLoadCode.CONST_Z);
//				Complex3x1 shuntY = new Complex3x1(new Complex(0,-0.0005),new Complex(0.0,-0.0005),new Complex(0.0,-0.0005));
//				Shuntload.set3PhaseLoad(shuntY.multiply(scaleFactor));
//				bus.getThreePhaseLoadList().add(Shuntload);
//				
//			}
			
		}
		

		// add step down transformer between source bus and bus1
		
		DStab3PBranch xfr1 = ThreePhaseObjectFactory.create3PBranch(sourceBus.getId(), net.getBus("Bus"+startBusNum).getId(), "0", net);
		xfr1.setBranchCode(AclfBranchCode.XFORMER);
		xfr1.setToTurnRatio(1.02);
		xfr1.setZ( new Complex( 0.0, 0.08).multiply(100.0/XfrMVA));
		
		
		AcscXformerAdapter xfr01 = acscXfrAptr.apply(xfr1);
		xfr01.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
		xfr01.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
	

		
		
		
	DStab3PBranch xfr1_2 = ThreePhaseObjectFactory.create3PBranch(net.getBus("Bus"+startBusNum).getId(), net.getBus("Bus"+(startBusNum+1)).getId(), "0", net);
		xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
		xfr1_2.setToTurnRatio(1.02);
		xfr1_2.setZ( new Complex( 0.0, 0.06 ).multiply(100.0/XfrMVA));
	
	
	AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr1_2);
		xfr0.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
		xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);

		
		
		int k =0;
		for(int i =startBusNum+1;i<startBusNum+BusNum-1;i++){
			
			DStab3PBranch Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus"+i, "Bus"+(i+1), "0", net);
			Line2_3.setBranchCode(AclfBranchCode.LINE);
			
			
			Complex3x3 zabcActual = IEEEFeederLineCode.zMtx601;
			if(k>=3 && k<5)
				zabcActual =  IEEEFeederLineCode.zMtx602;
			else if (k>=5)
				zabcActual =  IEEEFeederLineCode.zMtx606;
			
			zabcActual = zabcActual.multiply(sectionLength[k]/zscaleFactor);
			
			Double zbase = net.getBus("Bus"+i).getBaseVoltage()*net.getBus("Bus"+i).getBaseVoltage()/net.getBaseMva()/1.0E6;
			Line2_3.setZabc(zabcActual.multiply(1/zbase));
			if(k==2 || k==4 || k==6){
				Complex3x3 shuntYabc = new Complex3x3(new Complex(0,0.0005),new Complex(0.0,0.0005),new Complex(0.0,0.0005));
				Line2_3.setFromShuntYabc(shuntYabc.multiply(scaleFactor));
			  //Line2_3.setFromShuntYabc(new new Complex3x1(new Complex(0,-0.0005),new Complex(0.0,-0.0005),new Complex(0.0,-0.0005));
			}
			k++;
		}
		
		
		
		return net; 
		
		
		
	}

private void buildFeederDynModel(DStabNetwork3Phase dsNet, int startBusNum, int endBusNum,
		double ACMotorPercent, double IndMotorPercent,
		double ACPhaseUnbalance, double totalLoadMW, double[] loadPercentAry) {
	
	
	int k = 0;
	for(int i =startBusNum;i<=endBusNum;i++){
		DStab3PBus loadBus = (DStab3PBus) dsNet.getBus("Bus"+i);
		
		/*
		Load3Phase load1 = new Load3PhaseImpl();
		load1.set3PhaseLoad(new Complex3x1(new Complex(0.3,0.05),new Complex(0.3,0.05),new Complex(0.3,0.05)));
		loadBus.getThreePhaseLoadList().add(load1);
		*/
			

		// AC motor, 50%
		if(ACMotorPercent>0){
				SinglePhaseACMotor ac1 = new SinglePhaseACMotor(loadBus,"1");
					ac1.setLoadPercent(ACMotorPercent-ACPhaseUnbalance);
					ac1.setPhase(PhaseCode.A);
				
					ac1.setTstall(0.05); // disable ac stalling
					ac1.setVstall(0.65);
					loadBus.getPhaseADynLoadList().add(ac1);
					
					
					
				SinglePhaseACMotor ac2 = new SinglePhaseACMotor(loadBus,"2");
					ac2.setLoadPercent(ACMotorPercent);
					ac2.setPhase(PhaseCode.B);
					ac2.setTstall(0.05); // disable ac stalling
					ac2.setVstall(0.65);
					loadBus.getPhaseBDynLoadList().add(ac2);
					

					
				SinglePhaseACMotor ac3 = new SinglePhaseACMotor(loadBus,"3");
					ac3.setLoadPercent(ACMotorPercent+ACPhaseUnbalance);
					ac3.setPhase(PhaseCode.C);
					ac3.setTstall(0.05); // disable ac stalling
					ac3.setVstall(0.65);
					loadBus.getPhaseCDynLoadList().add(ac3);
		
		}
		// 3 phase motor, 20%
		if(IndMotorPercent>0){
	  		InductionMotorImpl indMotor= new InductionMotorImpl(loadBus,"1");
			indMotor.setDStabBus(loadBus);

			indMotor.setXm(3.0);
			indMotor.setXl(0.07);
			indMotor.setRa(0.032);
			indMotor.setXr1(0.3);
			indMotor.setRr1(0.01);
			
	        double motorMVA = totalLoadMW*loadPercentAry[k]* IndMotorPercent/100.0/0.8;
			indMotor.setMvaBase(motorMVA);
			indMotor.setH(0.3);
			indMotor.setA(0.0); //Toreque = (a+bw+cw^2)*To;
			indMotor.setB(0.0); //Toreque = (a+bw+cw^2)*To;
			indMotor.setC(1.0); //Toreque = (a+bw+cw^2)*To;
			InductionMotor3PhaseAdapter indMotor3Phase = new InductionMotor3PhaseAdapter(indMotor);
			indMotor3Phase.setLoadPercent(IndMotorPercent); //0.06 MW
			loadBus.getThreePhaseDynLoadList().add(indMotor3Phase);	
		
		}
		// PV generation
		
//			Gen3Phase gen1 = new Gen3PhaseImpl();
//			gen1.setParentBus(loadBus);
//			gen1.setId("PV1");
//			gen1.setGen(new Complex(pvGen,0));  // total gen power, system mva based
//			
//			loadBus.getThreePhaseGenList().add(gen1);
//			
//			double pvMVABase = pvGen/0.8*100;
//			gen1.setMvaBase(pvMVABase); // for dynamic simulation only
//			gen1.setPosGenZ(new Complex(0,1.0E-1));   // assuming open-circuit
//			gen1.setNegGenZ(new Complex(0,1.0E-1));
//			gen1.setZeroGenZ(new Complex(0,1.0E-1));
//			//create the PV Distributed generation model
//			PVDistGen3Phase pv = new PVDistGen3Phase(gen1);
//			pv.setId("1");
//			pv.setUnderVoltTripAll(0.4);
//			pv.setUnderVoltTripStart(0.8);
		
		
			k++;
	}
}


}
