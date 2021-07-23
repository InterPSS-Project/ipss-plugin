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
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.adpter.AcscXformerAdapter;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabLoad;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestTnD_IEEE39_Feeder {
	
	    //@Test
		public void test_3phase3Seq_IEEE39Bus_Feeder_powerflow() throws InterpssException{
			IpssCorePlugin.init();
			IpssCorePlugin.setLoggerLevel(Level.INFO);
			PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
			assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
					"testData/IEEE39Bus/IEEE39bus_v30.raw",
					"testData/IEEE39Bus/IEEE39bus_v30.seq",
					//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
					"testData/IEEE39Bus/IEEE39bus.dyr"
			}));
			DStabModelParser parser =(DStabModelParser) adapter.getModel();
			
			//System.out.println(parser.toXmlDoc());

			
			
			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			
		    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
		    
		    
		    
		    
		    double PVPenetrationLevel = .0;
		    double PVIncrement = PVPenetrationLevel/(1-PVPenetrationLevel) ;
		    double ACMotorPercent = 50;
		    double IndMotorPercent = 0;
		    double ACPhaseUnbalance = 0;
		   
		    
		   
		    double baseVolt = 12470;
			int feederBusNum = 9;
			
			double loadPF = 0.95;
			double loadUnbalanceFactor = 0.30;
			
			double [] loadDistribution = new double[]{0.25,0.20,0.15,0.15,0.1,0.1,0.05};
			double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.0,1.5,2,2}; // unit in mile
			//double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.5,0.5,0.5, 1}; // unit in mile
			
			
			double sysMVABASE = dsNet.getBaseMva();
			
			/**
			 * --------------------- Feeders below Bus 15---------------------------- 
			 */
			double netTotalLoad15 = dsNet.getBus("Bus15").getContributeLoadList().get(0).getLoadCP().getReal();
		    dsNet.getBus("Bus15").getContributeLoadList().remove(0);
		    dsNet.getBus("Bus15").setLoadCode(AclfLoadCode.NON_LOAD);
		    
			 
			 double totalLoad15 = netTotalLoad15*(1+PVIncrement)*sysMVABASE;
			 double XfrMVA = totalLoad15/0.8;
			 int startBusIndex = 150;
			createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus15"), startBusIndex , baseVolt,feederBusNum,totalLoad15,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
			
			buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoad15,loadDistribution);
			
			
		
			/**
			 * --------------------- Feeders below Bus 16---------------------------- 
			 */
			 double netTotalLoadBus16 = dsNet.getBus("Bus16").getContributeLoadList().get(0).getLoadCP().getReal();
			 dsNet.getBus("Bus16").getContributeLoadList().remove(0);
			 dsNet.getBus("Bus16").setLoadCode(AclfLoadCode.NON_LOAD);
			
			
			 double totalLoadBus16 = netTotalLoadBus16*(1+PVIncrement)*sysMVABASE;
			 XfrMVA = totalLoadBus16/0.8;
			 
			 startBusIndex =160;
			createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus16"), startBusIndex, baseVolt,feederBusNum,totalLoadBus16,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
			
			
			buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus16,loadDistribution);
			
			
			/**
			 * --------------------- Feeders below Bus 18---------------------------- 
			 */
			double netTotalLoadBus18 =dsNet.getBus("Bus18").getContributeLoadList().get(0).getLoadCP().getReal();
			 dsNet.getBus("Bus18").getContributeLoadList().remove(0);
			 dsNet.getBus("Bus18").setLoadCode(AclfLoadCode.NON_LOAD);
			 
			 double totalLoadBus18 = netTotalLoadBus18*(1+PVIncrement)*sysMVABASE;
			 XfrMVA = totalLoadBus18/0.8;
			 
			 startBusIndex =180;
			createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus18"), 180, baseVolt,feederBusNum,totalLoadBus18,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
			
			buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus18,loadDistribution);
			
			
		    
			/**
			 * --------------------- Feeders below Bus 26---------------------------- 
			 */
			double netTotalLoadBus26 = dsNet.getBus("Bus26").getContributeLoadList().get(0).getLoadCP().getReal();
		    dsNet.getBus("Bus26").getContributeLoadList().remove(0);
		    dsNet.getBus("Bus26").setLoadCode(AclfLoadCode.NON_LOAD);
		    
			 
			 double totalLoadBus26 = netTotalLoadBus26*(1+PVIncrement)*sysMVABASE;
			 XfrMVA = totalLoadBus26/0.8;
			 
			 startBusIndex =260;
			createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus26"), 260, baseVolt,feederBusNum,totalLoadBus26,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
			
			buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus26,loadDistribution);
			
		
			/**
			 * --------------------- Feeders below Bus 27---------------------------- 
			 */
			 double netTotalLoadBus27 = dsNet.getBus("Bus27").getContributeLoadList().get(0).getLoadCP().getReal();
			 dsNet.getBus("Bus27").getContributeLoadList().remove(0);
			 dsNet.getBus("Bus27").setLoadCode(AclfLoadCode.NON_LOAD);
			
			
			 double totalLoadBus27 = netTotalLoadBus27*(1+PVIncrement)*sysMVABASE;
			 XfrMVA = totalLoadBus27/0.8;
			 
			 startBusIndex =270;
			createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus27"), 270, baseVolt,feederBusNum,totalLoadBus27,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
			
			buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus27,loadDistribution);
			
			
			/**
			 * --------------------- Feeders below Bus 28---------------------------- 
			 */
			double netTotalLoadBus28 =dsNet.getBus("Bus28").getContributeLoadList().get(0).getLoadCP().getReal();
			 dsNet.getBus("Bus28").getContributeLoadList().remove(0);
			 dsNet.getBus("Bus28").setLoadCode(AclfLoadCode.NON_LOAD);
			 
			 double totalLoadBus28 = netTotalLoadBus28*(1+PVIncrement)*sysMVABASE;
			 XfrMVA = totalLoadBus28/0.8;
			 
			 startBusIndex =280;
			createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus28"), 280, baseVolt,feederBusNum,totalLoadBus28,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
			
			buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus28,loadDistribution);
			
			
			
			//TODO select 6 buses in the load center to replace them by detailed feeders

		    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
				 proc.addSubNetInterfaceBranch("Bus15->Bus150(0)",false);
				 proc.addSubNetInterfaceBranch("Bus16->Bus160(0)",false);
				 proc.addSubNetInterfaceBranch("Bus18->Bus180(0)",false);
				 proc.addSubNetInterfaceBranch("Bus26->Bus260(0)",false);
				 proc.addSubNetInterfaceBranch("Bus27->Bus270(0)",false);
				 proc.addSubNetInterfaceBranch("Bus28->Bus280(0)",false);
				
				 
				 proc.splitFullSystemIntoSubsystems(true);
				 
				 // currently, if a fault at transmission system is to be considered, then it should be set to 3phase
				// proc.set3PhaseSubNetByBusId("Bus1");
				//TODO this has to be manually identified
				 proc.set3PhaseSubNetByBusId("Bus151");
				 proc.set3PhaseSubNetByBusId("Bus161");
				 proc.set3PhaseSubNetByBusId("Bus181");   
				 proc.set3PhaseSubNetByBusId("Bus261"); 
				 proc.set3PhaseSubNetByBusId("Bus271"); 
				 proc.set3PhaseSubNetByBusId("Bus281"); 
		    
			
				 TDMultiNetPowerflowAlgorithm tdAlgo = new TDMultiNetPowerflowAlgorithm((BaseAclfNetwork<? extends AclfBus, ? extends AclfBranch>) dsNet,proc);
				 
				 //System.out.println(tdAlgo.getTransmissionNetwork().net2String());
				    
				 assertTrue(tdAlgo.powerflow()); 
				 
				 
				 System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
				 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
			
			 }
		
	
	//@Test
	public void test_3phase3Seq_IEEE39Bus_Feeder() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE39Bus/IEEE39bus_v30.raw",
				"testData/IEEE39Bus/IEEE39bus_v30.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE39Bus/IEEE39bus.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
	    
	    
	    
	    double PVPenetrationLevel = .0;
	    double PVIncrement = PVPenetrationLevel/(1-PVPenetrationLevel) ;
	    double ACMotorPercent = 50;
	    double IndMotorPercent = 0;
	    double ACPhaseUnbalance = 0;
	   
	    
	   
	    double baseVolt = 12470;
		int feederBusNum = 9;
		
		double loadPF = 0.95;
		double loadUnbalanceFactor = 0.00;
		
		double [] loadDistribution = new double[]{0.25,0.20,0.15,0.15,0.1,0.1,0.05};
		double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.0,1.5,2,2}; // unit in mile
		//double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.5,0.5,0.5, 1}; // unit in mile
		
		
		double sysMVABASE = dsNet.getBaseMva();
		
		/**
		 * --------------------- Feeders below Bus 15---------------------------- 
		 */
		double netTotalLoad15 = dsNet.getBus("Bus15").getContributeLoadList().get(0).getLoadCP().getReal();
	    dsNet.getBus("Bus15").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus15").setLoadCode(AclfLoadCode.NON_LOAD);
	    
		 
		 double totalLoad15 = netTotalLoad15*(1+PVIncrement)*sysMVABASE;
		 double XfrMVA = totalLoad15/0.8;
		 int startBusIndex = 150;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus15"), startBusIndex , baseVolt,feederBusNum,totalLoad15,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoad15,loadDistribution);
		
		
	
		/**
		 * --------------------- Feeders below Bus 16---------------------------- 
		 */
		 double netTotalLoadBus16 = dsNet.getBus("Bus16").getContributeLoadList().get(0).getLoadCP().getReal();
		 dsNet.getBus("Bus16").getContributeLoadList().remove(0);
		 dsNet.getBus("Bus16").setLoadCode(AclfLoadCode.NON_LOAD);
		
		
		 double totalLoadBus16 = netTotalLoadBus16*(1+PVIncrement)*sysMVABASE;
		 XfrMVA = totalLoadBus16/0.8;
		 
		 startBusIndex =160;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus16"), startBusIndex, baseVolt,feederBusNum,totalLoadBus16,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		
		buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus16,loadDistribution);
		
		
		/**
		 * --------------------- Feeders below Bus 18---------------------------- 
		 */
		double netTotalLoadBus18 =dsNet.getBus("Bus18").getContributeLoadList().get(0).getLoadCP().getReal();
		 dsNet.getBus("Bus18").getContributeLoadList().remove(0);
		 dsNet.getBus("Bus18").setLoadCode(AclfLoadCode.NON_LOAD);
		 
		 double totalLoadBus18 = netTotalLoadBus18*(1+PVIncrement)*sysMVABASE;
		 XfrMVA = totalLoadBus18/0.8;
		 
		 startBusIndex =180;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus18"), 180, baseVolt,feederBusNum,totalLoadBus18,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus18,loadDistribution);
		
		
	    
		/**
		 * --------------------- Feeders below Bus 26---------------------------- 
		 */
		double netTotalLoadBus26 = dsNet.getBus("Bus26").getContributeLoadList().get(0).getLoadCP().getReal();
	    dsNet.getBus("Bus26").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus26").setLoadCode(AclfLoadCode.NON_LOAD);
	    
		 
		 double totalLoadBus26 = netTotalLoadBus26*(1+PVIncrement)*sysMVABASE;
		 XfrMVA = totalLoadBus26/0.8;
		 
		 startBusIndex =260;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus26"), 260, baseVolt,feederBusNum,totalLoadBus26,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus26,loadDistribution);
		
	
		/**
		 * --------------------- Feeders below Bus 27---------------------------- 
		 */
		 double netTotalLoadBus27 = dsNet.getBus("Bus27").getContributeLoadList().get(0).getLoadCP().getReal();
		 dsNet.getBus("Bus27").getContributeLoadList().remove(0);
		 dsNet.getBus("Bus27").setLoadCode(AclfLoadCode.NON_LOAD);
		
		
		 double totalLoadBus27 = netTotalLoadBus27*(1+PVIncrement)*sysMVABASE;
		 XfrMVA = totalLoadBus27/0.8;
		 
		 startBusIndex =270;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus27"), 270, baseVolt,feederBusNum,totalLoadBus27,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus27,loadDistribution);
		
		
		/**
		 * --------------------- Feeders below Bus 28---------------------------- 
		 */
		double netTotalLoadBus28 =dsNet.getBus("Bus28").getContributeLoadList().get(0).getLoadCP().getReal();
		 dsNet.getBus("Bus28").getContributeLoadList().remove(0);
		 dsNet.getBus("Bus28").setLoadCode(AclfLoadCode.NON_LOAD);
		 
		 double totalLoadBus28 = netTotalLoadBus28*(1+PVIncrement)*sysMVABASE;
		 XfrMVA = totalLoadBus28/0.8;
		 
		 startBusIndex =280;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus28"), 280, baseVolt,feederBusNum,totalLoadBus28,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus28,loadDistribution);
		
		
		
		//TODO select 6 buses in the load center to replace them by detailed feeders

	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
			 proc.addSubNetInterfaceBranch("Bus15->Bus150(0)",false);
			 proc.addSubNetInterfaceBranch("Bus16->Bus160(0)",false);
			 proc.addSubNetInterfaceBranch("Bus18->Bus180(0)",false);
			 proc.addSubNetInterfaceBranch("Bus26->Bus260(0)",false);
			 proc.addSubNetInterfaceBranch("Bus27->Bus270(0)",false);
			 proc.addSubNetInterfaceBranch("Bus28->Bus280(0)",false);
			
			 
			 proc.splitFullSystemIntoSubsystems(true);
			 
			 // currently, if a fault at transmission system is to be considered, then it should be set to 3phase
			 proc.set3PhaseSubNetByBusId("Bus1");
			//TODO this has to be manually identified
			 proc.set3PhaseSubNetByBusId("Bus151");
			 proc.set3PhaseSubNetByBusId("Bus161");
			 proc.set3PhaseSubNetByBusId("Bus181");   
			 proc.set3PhaseSubNetByBusId("Bus261"); 
			 proc.set3PhaseSubNetByBusId("Bus271"); 
			 proc.set3PhaseSubNetByBusId("Bus281"); 
	    
		
			 TDMultiNetPowerflowAlgorithm tdAlgo = new TDMultiNetPowerflowAlgorithm((BaseAclfNetwork<? extends AclfBus, ? extends AclfBranch>) dsNet,proc);
			 
			 //System.out.println(tdAlgo.getTransmissionNetwork().net2String());
			    
			 assertTrue(tdAlgo.powerflow()); 
			 
			
			 
			 System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
			 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
		
		
			 MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
			  
			  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
			  DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
			    
			  
				dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
				dstabAlgo.setSimuStepSec(0.005d);
				dstabAlgo.setTotalSimuTimeSec(25.00);
				

				//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
				
				//applied the event
				// SLG FAULT: Bus 17
				// 3Phase fault: Bus 3
				dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus3",proc.getSubNetworkByBusId("Bus3"),SimpleFaultCode.GROUND_3P,new Complex(0.0),null,1.0d,0.07),"3phaseFault@Bus5");
		        
				
				StateMonitor sm = new StateMonitor();
				sm.addGeneratorStdMonitor(new String[]{"Bus30-mach1","Bus37-mach1","Bus38-mach1"});
				sm.addBusStdMonitor(new String[]{"Bus28","Bus27","Bus26","Bus24","Bus22","Bus18","Bus16","Bus15"});
				sm.add3PhaseBusStdMonitor(new String[]{"Bus288","Bus284","Bus282","Bus278","Bus274","Bus272","Bus268","Bus264","Bus262","Bus188","Bus184","Bus182","Bus180","Bus168","Bus164","Bus162","Bus160","Bus158","Bus157","Bus156","Bus154","Bus153","Bus152","Bus151","Bus150"});
				//String[] seqVotBusAry = new String[]{"Bus28","Bus27","Bus26","Bus24","Bus22","Bus18","Bus16","Bus15"};
				//sm.add3PhaseBusStdMonitor(seqVotBusAry);
				
				//1Phase AC motor extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getParentBus().getId()+"_phase"+this.getPhase();
				
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus152_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus152_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus152_phaseC");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus153_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus154_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus154_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus154_phaseC");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus155_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus156_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus157_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus158_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus158_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus158_phaseC");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus162_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus162_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus162_phaseC");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus163_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus164_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus164_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus164_phaseC");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus166_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus168_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus168_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus168_phaseC");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus182_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus182_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus182_phaseC");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus186_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus186_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus186_phaseC");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus188_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus188_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus188_phaseC");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus262_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus262_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus262_phaseC");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus264_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus264_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus264_phaseC");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus268_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus268_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus268_phaseC");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus272_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus272_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus272_phaseC");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus274_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus274_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus274_phaseC");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus278_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus278_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus278_phaseC");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus282_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus282_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus282_phaseC");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus284_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus284_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus284_phaseC");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus288_phaseA");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus288_phaseB");
				sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus288_phaseC");
				
				//3phase induction motor extended_device_Id = "IndMotor_"+this.getId()+"@"+this.getDStabBus().getId();
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus152");
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus154");
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus158");
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus262");
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus264");
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus268");
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus282");
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus284");
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus288");
				
				// PV gen
				// extended_device_Id = "PVGen3Phase_"+this.getId()+"@"+this.getParentGen().getParentBus().getId();
//				
//				sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus12");
//				sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus18");
//				sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus22");
//				sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus28");
//				sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus32");
//				sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus38");
				
				
				// set the output handler
				dstabAlgo.setSimuOutputHandler(sm);
				dstabAlgo.setOutPutPerSteps(1);
				//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
				
				IpssLogger.getLogger().setLevel(Level.WARNING);
				
				PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
				
		        // Must use this dynamic event process to modify the YMatrixABC
//				dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
				
				MultiNet3Ph3SeqDStabSolverImpl solver = new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper);
				dstabAlgo.setSolver(solver  );
				dstabAlgo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(mNetHelper));
				
				if (dstabAlgo.initialization()) {
					//System.out.println(ThreePhaseAclfOutFunc.busLfSummary(dsNet));
					
					//System.out.println(dsNet.getMachineInitCondition());
					
					System.out.println("Running 3Phase/3sequence DStab co-simulation ...");
					timer.start();
					//dstabAlgo.performSimulation();
					
					while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
						
						dstabAlgo.solveDEqnStep(true);
						
						for(String busId: sm.getBusPhAVoltTable().keySet()){
							
							 sm.addBusPhaseVoltageMonitorRecord( busId,dstabAlgo.getSimuTime(), ((DStab3PBus)proc.getSubNetworkByBusId(busId).getBus(busId)).get3PhaseVotlages());
						}
						
					}
				}
				//System.out.println(sm.toCSVString(sm.getBusVoltTable()));
				//System.out.println(sm.toCSVString(sm.getBusAngleTable()));
				//System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
				//System.out.println(sm.toCSVString(sm.getAcMotorStateTable()));
				
				
				/*
				//tie-line current results
				FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_paper_dyn_sim//IEEE39_TnD//subNetCurInjResults_Ld_Comp_basecase.csv",
						solver.getRecordResults());
				
				
				FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_paper_dyn_sim//IEEE39_TnD//busVoltage.csv",
						sm.toCSVString(sm.getBusVoltTable()));
				FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_paper_dyn_sim//IEEE39_TnD//busPhAVoltage.csv",
						sm.toCSVString(sm.getBusPhAVoltTable()));
				FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_paper_dyn_sim//IEEE39_TnD//busPhBVoltage.csv",
						sm.toCSVString(sm.getBusPhBVoltTable()));
				FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_paper_dyn_sim//IEEE39_TnD//busPhCVoltage.csv",
						sm.toCSVString(sm.getBusPhCVoltTable()));
				
				FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_paper_dyn_sim//IEEE39_TnD//AcMotorState.csv",
						sm.toCSVString(sm.getAcMotorStateTable()));
				FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_paper_dyn_sim//IEEE39_TnD//AcMotorP.csv",
						sm.toCSVString(sm.getAcMotorPTable()));
				FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_paper_dyn_sim//IEEE39_TnD//AcMotorQ.csv",
						sm.toCSVString(sm.getAcMotorQTable()));
				*/
//				FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_paper_dyn_sim//IEEE39_TnD//IndMotorP.csv",
//						sm.toCSVString(sm.getMotorPTable()));
//				FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_paper_dyn_sim//IEEE39_TnD//IndMotorSlip.csv",
//						sm.toCSVString(sm.getMotorSlipTable()));
//				FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_paper_dyn_sim//IEEE39_TnD//IndMotorQ.csv",
//						sm.toCSVString(sm.getMotorQTable()));
				
//				FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_paper_dyn_sim//IEEE39_TnD//pvGenP.csv",
//						sm.toCSVString(sm.getPvGenPTable()));
//				FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_paper_dyn_sim//IEEE39_TnD//pvGenQ.csv",
//						sm.toCSVString(sm.getPvGenQTable()));
//				FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_paper_dyn_sim//IEEE39_TnD//GenPe.csv",
//						sm.toCSVString(sm.getMachPeTable()));
//				FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_paper_dyn_sim//IEEE39_TnD//GenPm.csv",
//						sm.toCSVString(sm.getMachPmTable()));
		
		 }
	
	
	@Test
	public void test_3phase3Seq_IEEE39Bus_Feeder_constZLoad() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE39Bus/IEEE39bus_v30.raw",
				"testData/IEEE39Bus/IEEE39bus_v30.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE39Bus/IEEE39bus.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
	    
	    
	    
	    double PVPenetrationLevel = .0;
	    double PVIncrement = PVPenetrationLevel/(1-PVPenetrationLevel) ;
	    double ACMotorPercent = 0;
	    double IndMotorPercent = 0;
	    double ACPhaseUnbalance = 0;
	   
	    
	   
	    double baseVolt = 12470;
		int feederBusNum = 9;
		
		double loadPF = 0.95;
		double loadUnbalanceFactor = 0.00;
		
		double [] loadDistribution = new double[]{0.25,0.20,0.15,0.15,0.1,0.1,0.05};
		double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.0,1.5,2,2}; // unit in mile
		//double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.5,0.5,0.5, 1}; // unit in mile
		
		
		double sysMVABASE = dsNet.getBaseMva();
		
		/**
		 * --------------------- Feeders below Bus 15---------------------------- 
		 */
		DStabLoad load = dsNet.getBus("Bus15").getContributeLoadList().get(0);
				
		double netTotalLoad15 = load.getLoadCP().getReal();
	    dsNet.getBus("Bus15").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus15").setLoadCode(AclfLoadCode.NON_LOAD);
	    
		 
		 double totalLoad15 = netTotalLoad15*(1+PVIncrement)*sysMVABASE;
		 double XfrMVA = totalLoad15/0.8;
		 int startBusIndex = 150;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus15"), startBusIndex , baseVolt,feederBusNum,totalLoad15,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		//buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoad15,loadDistribution);
		
		
	
		/**
		 * --------------------- Feeders below Bus 16---------------------------- 
		 */
		 DStabLoad load16 = dsNet.getBus("Bus16").getContributeLoadList().get(0);
		 double netTotalLoadBus16 = load16.getLoadCP().getReal();
		 dsNet.getBus("Bus16").getContributeLoadList().remove(0);
		 dsNet.getBus("Bus16").setLoadCode(AclfLoadCode.NON_LOAD);
		
		
		 double totalLoadBus16 = netTotalLoadBus16*(1+PVIncrement)*sysMVABASE;
		 XfrMVA = totalLoadBus16/0.8;
		 
		 startBusIndex =160;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus16"), startBusIndex, baseVolt,feederBusNum,totalLoadBus16,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		
		//buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus16,loadDistribution);
		
		
		/**
		 * --------------------- Feeders below Bus 18---------------------------- 
		 */
		DStabLoad load18 =dsNet.getBus("Bus18").getContributeLoadList().get(0);
		double netTotalLoadBus18 = load18.getLoadCP().getReal();
		
		dsNet.getBus("Bus18").getContributeLoadList().remove(0);
		dsNet.getBus("Bus18").setLoadCode(AclfLoadCode.NON_LOAD);
		 
		 double totalLoadBus18 = netTotalLoadBus18*(1+PVIncrement)*sysMVABASE;
		 XfrMVA = totalLoadBus18/0.8;
		 
		 startBusIndex =180;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus18"), 180, baseVolt,feederBusNum,totalLoadBus18,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		//buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus18,loadDistribution);
		
		
	    
		/**
		 * --------------------- Feeders below Bus 26---------------------------- 
		 */
		DStabLoad load26 =dsNet.getBus("Bus26").getContributeLoadList().get(0);
		double netTotalLoadBus26 = load26.getLoadCP().getReal();
		
		dsNet.getBus("Bus26").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus26").setLoadCode(AclfLoadCode.NON_LOAD);
	    
		 
		 double totalLoadBus26 = netTotalLoadBus26*(1+PVIncrement)*sysMVABASE;
		 XfrMVA = totalLoadBus26/0.8;
		 
		 startBusIndex =260;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus26"), 260, baseVolt,feederBusNum,totalLoadBus26,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		//buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus26,loadDistribution);
		
	
		/**
		 * --------------------- Feeders below Bus 27---------------------------- 
		 */
		 DStabLoad load27 =dsNet.getBus("Bus27").getContributeLoadList().get(0);
		 double netTotalLoadBus27 = load27.getLoadCP().getReal();
		
		 dsNet.getBus("Bus27").getContributeLoadList().remove(0);
		 dsNet.getBus("Bus27").setLoadCode(AclfLoadCode.NON_LOAD);
		
		
		 double totalLoadBus27 = netTotalLoadBus27*(1+PVIncrement)*sysMVABASE;
		 XfrMVA = totalLoadBus27/0.8;
		 
		 startBusIndex =270;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus27"), 270, baseVolt,feederBusNum,totalLoadBus27,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		//buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus27,loadDistribution);
		
		
		/**
		 * --------------------- Feeders below Bus 28---------------------------- 
		 */
		DStabLoad load28 =dsNet.getBus("Bus28").getContributeLoadList().get(0);
		double netTotalLoadBus28 = load28.getLoadCP().getReal();
		 
		 dsNet.getBus("Bus28").setLoadCode(AclfLoadCode.NON_LOAD);
		 
		 double totalLoadBus28 = netTotalLoadBus28*(1+PVIncrement)*sysMVABASE;
		 XfrMVA = totalLoadBus28/0.8;
		 
		 startBusIndex =280;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus28"), 280, baseVolt,feederBusNum,totalLoadBus28,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		//buildFeederDynModel(dsNet, startBusIndex+2, startBusIndex+feederBusNum-1,ACMotorPercent, IndMotorPercent,ACPhaseUnbalance, totalLoadBus28,loadDistribution);
		
		
		
		//TODO select 6 buses in the load center to replace them by detailed feeders

	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
			 proc.addSubNetInterfaceBranch("Bus15->Bus150(0)",false);
			 proc.addSubNetInterfaceBranch("Bus16->Bus160(0)",false);
			 proc.addSubNetInterfaceBranch("Bus18->Bus180(0)",false);
			 proc.addSubNetInterfaceBranch("Bus26->Bus260(0)",false);
			 proc.addSubNetInterfaceBranch("Bus27->Bus270(0)",false);
			 proc.addSubNetInterfaceBranch("Bus28->Bus280(0)",false);
			
			 
			 proc.splitFullSystemIntoSubsystems(true);
			 
			 // currently, if a fault at transmission system is to be considered, then it should be set to 3phase
			 proc.set3PhaseSubNetByBusId("Bus1");
			//TODO this has to be manually identified
			 proc.set3PhaseSubNetByBusId("Bus151");
			 proc.set3PhaseSubNetByBusId("Bus161");
			 proc.set3PhaseSubNetByBusId("Bus181");   
			 proc.set3PhaseSubNetByBusId("Bus261"); 
			 proc.set3PhaseSubNetByBusId("Bus271"); 
			 proc.set3PhaseSubNetByBusId("Bus281"); 
	    
		
			 TDMultiNetPowerflowAlgorithm tdAlgo = new TDMultiNetPowerflowAlgorithm((BaseAclfNetwork<? extends AclfBus, ? extends AclfBranch>) dsNet,proc);
			 
			 //System.out.println(tdAlgo.getTransmissionNetwork().net2String());
			    
			 assertTrue(tdAlgo.powerflow()); 
			 
			
			 
			 System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
			 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
		
		
			 MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
			  
			  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
			 DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
			    
			  
				dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
				dstabAlgo.setSimuStepSec(0.005d);
				dstabAlgo.setTotalSimuTimeSec(3.00);
				

				//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
				
				//applied the event
				// SLG FAULT: Bus 17
				// 3Phase fault: Bus 3
				dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus28",proc.getSubNetworkByBusId("Bus28"),SimpleFaultCode.GROUND_3P,new Complex(0.0),null,1.0d,0.07),"3phaseFault@Bus5");
		        
				
				StateMonitor sm = new StateMonitor();
				sm.addGeneratorStdMonitor(new String[]{"Bus30-mach1","Bus37-mach1","Bus38-mach1"});
				sm.addBusStdMonitor(new String[]{"Bus28","Bus27","Bus26","Bus24","Bus22","Bus18","Bus16","Bus15","Bus5","Bus2"});
				sm.add3PhaseBusStdMonitor(new String[]{"Bus28","Bus38"});
				//String[] seqVotBusAry = new String[]{"Bus28","Bus27","Bus26","Bus24","Bus22","Bus18","Bus16","Bus15"};
				//sm.add3PhaseBusStdMonitor(seqVotBusAry);
				
				//1Phase AC motor extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getParentBus().getId()+"_phase"+this.getPhase();
				
				
				
				//3phase induction motor extended_device_Id = "IndMotor_"+this.getId()+"@"+this.getDStabBus().getId();
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus152");
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus154");
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus158");
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus262");
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus264");
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus268");
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus282");
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus284");
//				sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus288");
				
				// PV gen
				// extended_device_Id = "PVGen3Phase_"+this.getId()+"@"+this.getParentGen().getParentBus().getId();
//				
//				sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus12");
//				sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus18");
//				sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus22");
//				sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus28");
//				sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus32");
//				sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus38");
				
				
				// set the output handler
				dstabAlgo.setSimuOutputHandler(sm);
				dstabAlgo.setOutPutPerSteps(5);
				//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
				
				IpssLogger.getLogger().setLevel(Level.WARNING);
				
				PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
				
				timer.start();
				
		        // Must use this dynamic event process to modify the YMatrixABC
//				dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
				
				MultiNet3Ph3SeqDStabSolverImpl solver = new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper);
				dstabAlgo.setSolver(solver  );
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
				System.out.println("time :"+timer.getDuration());
				System.out.println(sm.toCSVString(sm.getBusVoltTable()));
				System.out.println(sm.toCSVString(sm.getBusFreqTable()));
				//System.out.println(sm.toCSVString(sm.getBusAngleTable()));
				//System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
				//System.out.println(sm.toCSVString(sm.getAcMotorStateTable()));
				
				//tie-line current results
//				FileUtil.writeText2File("TnD_paper_dyn_sim//IEEE39_TnD//subNetCurInjResults_Ld_Comp_basecase.csv",
//						solver.getRecordResults());
				
				//
				
				/*
				FileUtil.writeText2File("TnD_paper_dyn_sim//IEEE39_TnD//busVoltage.csv",
						sm.toCSVString(sm.getBusVoltTable()));
				FileUtil.writeText2File("TnD_paper_dyn_sim//IEEE39_TnD//busPhAVoltage.csv",
						sm.toCSVString(sm.getBusPhAVoltTable()));
				FileUtil.writeText2File("TnD_paper_dyn_sim//IEEE39_TnD//busPhBVoltage.csv",
						sm.toCSVString(sm.getBusPhBVoltTable()));
				FileUtil.writeText2File("TnD_paper_dyn_sim//IEEE39_TnD//busPhCVoltage.csv",
						sm.toCSVString(sm.getBusPhCVoltTable()));
				  */
   
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
//			// shunt compensation
//			if(i ==startBusNum+3 || i == startBusNum+5 || i==startBusNum+7){
//				Load3Phase Shuntload = new Load3PhaseImpl();
//				Complex3x1 shuntY = new Complex3x1(new Complex(0,-0.0005),new Complex(0.0,-0.0005),new Complex(0.0,-0.0005));
//				Shuntload.set3PhaseLoad(shuntY.multiply(scaleFactor));
//				bus.getThreePhaseLoadList().add(Shuntload);
//			}
			
		}
		

		// add step down transformer between source bus and bus1
		
		DStab3PBranch xfr1 = ThreePhaseObjectFactory.create3PBranch(sourceBus.getId(), net.getBus("Bus"+startBusNum).getId(), "0", net);
		xfr1.setBranchCode(AclfBranchCode.XFORMER);
		xfr1.setToTurnRatio(1.02);
		xfr1.setZ( new Complex( 0.0, 0.08).multiply(100.0/XfrMVA));
		
		
		AcscXformerAdapter xfr01 = acscXfrAptr.apply(xfr1);
		xfr01.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
		xfr01.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
	

		
		
		
	DStab3PBranch xfr1_2 = ThreePhaseObjectFactory.create3PBranch(net.getBus("Bus"+startBusNum).getId(), net.getBus("Bus"+(startBusNum+1)).getId(), "0", net);
		xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
		xfr1_2.setToTurnRatio(1.02);
		xfr1_2.setZ( new Complex( 0.0, 0.06 ).multiply(100.0/XfrMVA));
	
	
	AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr1_2);
		xfr0.setFromConnectGroundZ(XfrConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
		xfr0.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);

		
		
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
		
		 SinglePhaseACMotor ac1 = new SinglePhaseACMotor(loadBus,"1");
	  		ac1.setLoadPercent(ACMotorPercent-ACPhaseUnbalance);
	  		ac1.setPhase(PhaseCode.A);
	  	
	  		ac1.setTstall(0.033); // disable ac stalling
	  		ac1.setVstall(0.65);
	  		loadBus.getPhaseADynLoadList().add(ac1);
	  		
	  		
	  		
	  	SinglePhaseACMotor ac2 = new SinglePhaseACMotor(loadBus,"2");
	  		ac2.setLoadPercent(ACMotorPercent);
	  		ac2.setPhase(PhaseCode.B);
	  		ac2.setTstall(0.033); // disable ac stalling
	  		ac2.setVstall(0.65);
	  		loadBus.getPhaseBDynLoadList().add(ac2);
	  		

	  		
	  	SinglePhaseACMotor ac3 = new SinglePhaseACMotor(loadBus,"3");
	  		ac3.setLoadPercent(ACMotorPercent+ACPhaseUnbalance);
	  		ac3.setPhase(PhaseCode.C);
	  		ac3.setTstall(0.033); // disable ac stalling
	  		ac3.setVstall(0.65);
	  		loadBus.getPhaseCDynLoadList().add(ac3);
		
		
		// 3 phase motor, 20%
		    if(IndMotorPercent>0.0){
		  		InductionMotorImpl indMotor= new InductionMotorImpl(loadBus,"1");
				//indMotor.setDStabBus(loadBus);
	
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
