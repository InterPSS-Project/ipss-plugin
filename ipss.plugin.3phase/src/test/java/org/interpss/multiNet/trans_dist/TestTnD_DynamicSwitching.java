package org.interpss.multiNet.trans_dist;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;
import static org.interpss.threePhase.util.ThreePhaseUtilFunction.threePhaseInductionMotorAptr;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.dstab.dynLoad.InductionMotor;
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
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.algo.DynamicEventProcessor3Phase;
import org.interpss.threePhase.dynamic.model.DynLoadModel1Phase;
import org.interpss.threePhase.dynamic.model.DynLoadModel3Phase;
import org.interpss.threePhase.dynamic.model.impl.SinglePhaseACMotor;
import org.interpss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.core.acsc.adpter.AcscXformerAdapter;
import com.interpss.core.net.NetworkType;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.dstab.device.DynamicBusDevice;
import com.interpss.dstab.impl.DStabGenImpl;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineModelType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class TestTnD_DynamicSwitching {

	// @Test
	public void test_IEEE9_8Busfeeder_powerflow() throws InterpssException {
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.WARNING);
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet,
				new String[] { "testData/IEEE9Bus/ieee9.raw", "testData/IEEE9Bus/ieee9.seq",
						// "testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
						"testData/IEEE9Bus/ieee9_dyn.dyr" }));
		DStabModelParser parser = (DStabModelParser) adapter.getModel();

		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);

		// The only change to the normal data import is the use of
		// ODM3PhaseDStabParserMapper
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub()).map2Model(parser, simuCtx)) {
			System.out
					.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}

		DStabNetwork3Phase dsNet = (DStabNetwork3Phase) simuCtx.getDStabilityNet();

		double PVPenetrationLevel = .00;
		double PVIncrement = PVPenetrationLevel / (1 - PVPenetrationLevel);
		double ACMotorPercent = 40;
		double IndMotorPercent = 5;
		double ACPhaseUnbalance = 5.0;

		double baseVolt = 12470;
		int feederBusNum = 9;

		double loadPF = 0.95;
		double loadUnbalanceFactor = 0.00; // at the scale of 0 to 1

		double[] loadDistribution = new double[] { 0.25, 0.20, 0.15, 0.15, 0.1, 0.1, 0.05 };
		double[] feederSectionLenghth = new double[] { 0.5, 0.5, 1.0, 1.0, 1.5, 2, 2 }; // unit in mile
		// double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.5,0.5,0.5, 1}; //
		// unit in mile

		/**
		 * --------------------- Feeders below Bus 5----------------------------
		 */

		dsNet.getBus("Bus5").getContributeLoadList().remove(0);
		dsNet.getBus("Bus5").setLoadCode(AclfLoadCode.NON_LOAD);

		double netTotalLoad = 120;
		double totalLoad = netTotalLoad * (1 + PVIncrement);
		double XfrMVA = 150;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus5"), 10, baseVolt, feederBusNum, totalLoad, XfrMVA, loadPF,
				loadDistribution, loadUnbalanceFactor, feederSectionLenghth);

		/**
		 * --------------------- Feeders below Bus 6----------------------------
		 */

		dsNet.getBus("Bus6").getContributeLoadList().remove(0);
		dsNet.getBus("Bus6").setLoadCode(AclfLoadCode.NON_LOAD);

		double netTotalLoadBus6 = 90;
		double totalLoadBus6 = netTotalLoadBus6 * (1 + PVIncrement);
		XfrMVA = 120;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus6"), 20, baseVolt, feederBusNum, totalLoadBus6, XfrMVA,
				loadPF, loadDistribution, loadUnbalanceFactor, feederSectionLenghth);

		/**
		 * --------------------- Feeders below Bus 8----------------------------
		 */
		dsNet.getBus("Bus8").getContributeLoadList().remove(0);
		dsNet.getBus("Bus8").setLoadCode(AclfLoadCode.NON_LOAD);
		double netTotalLoadBus8 = 100;
		double totalLoadBus8 = netTotalLoadBus8 * (1 + PVIncrement);
		XfrMVA = 150;
		createFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus8"), 30, baseVolt, feederBusNum, totalLoadBus8, XfrMVA,
				loadPF, loadDistribution, loadUnbalanceFactor, feederSectionLenghth);

		SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		proc.addSubNetInterfaceBranch("Bus5->Bus10(0)", false);
		proc.addSubNetInterfaceBranch("Bus6->Bus20(0)", false);
		proc.addSubNetInterfaceBranch("Bus8->Bus30(0)", false);

		proc.splitFullSystemIntoSubsystems(true);

		// currently, if a fault at transmission system is to be considered, then it
		// should be set to 3phase
		proc.set3PhaseSubNetByBusId("Bus5");
		// TODO this has to be manually identified
		proc.set3PhaseSubNetByBusId("Bus11");
		proc.set3PhaseSubNetByBusId("Bus21");
		proc.set3PhaseSubNetByBusId("Bus31");

		System.out.println("external boundary bus: " + proc.getExternalSubNetBoundaryBusIdList());

		System.out.println("internal boundary bus: " + proc.getInternalSubNetBoundaryBusIdList());

		// TODO create TDMultiNetPowerflowAlgo

		TDMultiNetPowerflowAlgorithm tdAlgo = new TDMultiNetPowerflowAlgorithm((BaseAclfNetwork<? extends BaseAclfBus<?,?>, ? extends AclfBranch>)dsNet, proc);

		// System.out.println(tdAlgo.getTransmissionNetwork().net2String());

		assertTrue(tdAlgo.powerflow());

		System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(1)));
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(2)));

	}

	@Test
	public void test_IEEE9_8Busfeeder_dynSwitch() throws InterpssException {
		IpssCorePlugin.init();

		IpssCorePlugin.setLoggerLevel(Level.INFO);

		/**
		 * ----------------------------------------------------------- import the
		 * transmission network data
		 * ----------------------------------------------------------
		 */

		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[] { 
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq", 
				"testData/IEEE9Bus/ieee9_dyn_fullModel_v33.dyr"
				// "testData/IEEE9Bus/ieee9_dyn.dyr"
		}));
		DStabModelParser parser = (DStabModelParser) adapter.getModel();

		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);

		// The only change to the normal data import is the use of
		// ODM3PhaseDStabParserMapper
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub()).map2Model(parser, simuCtx)) {
			System.out
					.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}

		DStabNetwork3Phase dsNet = (DStabNetwork3Phase) simuCtx.getDStabilityNet();
		
		// modify the generators, if needed
//		DStab3PGen gen1 = dsNet.getBus("Bus1").getContributeGenList().get(0);
		DStabGenImpl gen1 = (DStabGenImpl) dsNet.getBus("Bus1").getContributeGenList().get(0);
		DStabGenImpl gen2 = (DStabGenImpl) dsNet.getBus("Bus2").getContributeGenList().get(0);
		DStabGenImpl gen3 = (DStabGenImpl) dsNet.getBus("Bus3").getContributeGenList().get(0);

		gen3.setGen(new Complex(0.1, 0.0665));
		gen3.setStatus(false);
		gen3.getParentBus().initContributeGen(false);
//		((BaseDStabBus) gen3.getParentBus()).resetSeqEquivLoad();

		double PVPenetrationLevel = .00;
		double PVIncrement = PVPenetrationLevel / (1 - PVPenetrationLevel);
		double ACMotorPercent = 00;
		double IndMotorPercent = 0;
		double ACPhaseUnbalance = 0.0;

		double baseVolt = 12470;
		int feederBusNum = 9;

		double loadPF = 0.95;
		double loadUnbalanceFactor = 0.00;
		double loadScaleFactor = 0.7; // This is to scale down the total load to provide room to mimic load pickup

		double[] loadDistribution = new double[] { 0.25, 0.20, 0.15, 0.15, 0.1, 0.1, 0.05 };
		double[] feederSectionLenghth = new double[] { 0.5, 0.5, 1.0, 1.0, 1.5, 2, 2 }; // unit in mile
		// double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.5,0.5,0.5, 1}; //
		// unit in mile

		/**
		 * ----------------------------------------------------------- create the
		 * distribution systems to replace the original loads at the transmission buses
		 * ----------------------------------------------------------
		 */

		/**
		 * --------------------- Feeders below Bus 5----------------------------
		 */

		dsNet.getBus("Bus5").getContributeLoadList().remove(0);
		dsNet.getBus("Bus5").setLoadCode(AclfLoadCode.NON_LOAD);

		double netTotalLoad = 120*loadScaleFactor;
		double totalLoad = netTotalLoad * (1 + PVIncrement);
		double XfrMVA = 150;
		int startBusIndex = 10;
		createBalancedFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus5"), startBusIndex, baseVolt, feederBusNum, totalLoad,
				XfrMVA, loadPF, loadDistribution, loadUnbalanceFactor, feederSectionLenghth);
		Complex temp = new Complex(0.27, 0.088);
		dsNet.getBus("Bus12").getThreePhaseLoadList().get(0).set3PhaseLoad(new Complex3x1(temp, temp, temp));
		buildFeederDynModel(dsNet, startBusIndex + 2, startBusIndex + feederBusNum - 1, ACMotorPercent, IndMotorPercent,
				ACPhaseUnbalance, totalLoad, loadDistribution);

		/**
		 * --------------------- Feeders below Bus 6----------------------------
		 */

		dsNet.getBus("Bus6").getContributeLoadList().remove(0);
		dsNet.getBus("Bus6").setLoadCode(AclfLoadCode.NON_LOAD);

		double netTotalLoadBus6 = 90*loadScaleFactor;
		double totalLoadBus6 = netTotalLoadBus6 * (1 + PVIncrement);
		XfrMVA = 120;

		startBusIndex = 20;
		createBalancedFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus6"), startBusIndex, baseVolt, feederBusNum,
				totalLoadBus6, XfrMVA, loadPF, loadDistribution, loadUnbalanceFactor, feederSectionLenghth);

		// buildFeederDynModel(dsNet, startBusIndex+2,
		// startBusIndex+feederBusNum-1,ACMotorPercent,
		// IndMotorPercent,ACPhaseUnbalance, totalLoadBus6,loadDistribution);

		/**
		 * --------------------- Feeders below Bus 8----------------------------
		 */
		dsNet.getBus("Bus8").getContributeLoadList().remove(0);
		dsNet.getBus("Bus8").setLoadCode(AclfLoadCode.NON_LOAD);

		double netTotalLoadBus8 = 100*loadScaleFactor;
		double totalLoadBus8 = netTotalLoadBus8 * (1 + PVIncrement);
		XfrMVA = 150;

		startBusIndex = 30;
		createBalancedFeeder(dsNet, (DStab3PBus) dsNet.getBus("Bus8"), startBusIndex, baseVolt, feederBusNum,
				totalLoadBus8, XfrMVA, loadPF, loadDistribution, loadUnbalanceFactor, feederSectionLenghth);

		// buildFeederDynModel(dsNet, startBusIndex+2,
		// startBusIndex+feederBusNum-1,ACMotorPercent,
		// IndMotorPercent,ACPhaseUnbalance, totalLoadBus8,loadDistribution);

		/**
		 * ----------------------------------------------------------- split the T&D
		 * network into 4 subnetworks
		 * ----------------------------------------------------------
		 */

		SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		proc.addSubNetInterfaceBranch("Bus5->Bus10(0)", false);

		// Note: this is to create unbalanced fault at Bus 5
		// proc.addSubNetInterfaceBranch("Bus4->Bus5(0)",false);
		// proc.addSubNetInterfaceBranch("Bus5->Bus7(0)",true);

		proc.addSubNetInterfaceBranch("Bus6->Bus20(0)", false);
		proc.addSubNetInterfaceBranch("Bus8->Bus30(0)", false);

		proc.splitFullSystemIntoSubsystems(true);

		// currently, if a fault at transmission system is to be considered, then it
		// should be set to 3phase
		// proc.set3PhaseSubNetByBusId("Bus4");
		// TODO this has to be manually identified
		proc.set3PhaseSubNetByBusId("Bus11");
		proc.set3PhaseSubNetByBusId("Bus21");
		proc.set3PhaseSubNetByBusId("Bus31");
		
		//!!!determine the static load modeling
		for(BaseDStabNetwork subnet:proc.getSubNetworkList()) {
			subnet.setStaticLoadIncludedInYMatrix(false);
		}

		System.out.println("external boundary bus: " + proc.getExternalSubNetBoundaryBusIdList());

		System.out.println("internal boundary bus: " + proc.getInternalSubNetBoundaryBusIdList());

		// TODO create TDMultiNetPowerflowAlgo

		TDMultiNetPowerflowAlgorithm tdAlgo = new TDMultiNetPowerflowAlgorithm(dsNet, proc);

	    //System.out.println(tdAlgo.getTransmissionNetwork().net2String());

		assertTrue(tdAlgo.powerflow());
//		System.out.println(tdAlgo.getDistributionNetworkList().get(0).net2String());
//		System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
//		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(1)));
//		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(2)));

		/**
		 * ----------------------------------------------------------- From here dynamic
		 * simulation switching loop starts
		 * ----------------------------------------------------------
		 */

		String[] dyn_event_time_list = { "fault@10", "fault@20" };
		int ev_ind;
		for (ev_ind = 0; ev_ind < dyn_event_time_list.length; ev_ind++) {
			// make sure dynamic event list is empty in the beginning of each loop
			dsNet.getDynamicEventList().clear();

			MultiNet3Ph3SeqDStabSimuHelper mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet, proc);
			// create multiNet3Seq3PhDStabHelper and initialize the subsystem
			DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(dsNet,
					IpssCorePlugin.getMsgHub());

			StateMonitor sm = new StateMonitor();
			sm.addGeneratorStdMonitor(new String[] { "Bus1-mach1", "Bus2-mach1", "Bus3-mach1" });
			String[] monitor_bus = { "Bus1", "Bus2", "Bus5", "Bus10", "Bus11", "Bus12", "Bus14", "Bus16", "Bus18" };
			sm.addBusStdMonitor(monitor_bus);
			sm.add3PhaseBusStdMonitor(monitor_bus);
			sm.addBranchStdMonitor("Bus5->Bus10Dummy(1)");
			sm.addBranchStdMonitor("Bus6->Bus20Dummy(1)");
			sm.addBranchStdMonitor("Bus8->Bus30Dummy(1)");
		

//			sm.addBusStdMonitor(new String[] { "Bus38", "Bus32", "Bus28", "Bus24", "Bus22", "Bus18", "Bus14", "Bus12",
//					"Bus8", "Bus6", "Bus5", "Bus4", "Bus1" });
//			sm.add3PhaseBusStdMonitor(new String[] { "Bus38", "Bus34", "Bus32", "Bus28", "Bus24", "Bus22", "Bus18",
//					"Bus15", "Bus14", "Bus12", "Bus11", "Bus10" });
			// String[] seqVotBusAry = new String[]{"Bus5","Bus4","Bus7"};
			// sm.add3PhaseBusStdMonitor(seqVotBusAry);

			// 1Phase AC motor extended_device_Id =
			// "ACMotor_"+this.getId()+"@"+this.getParentBus().getId()+"_phase"+this.getPhase();
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus12_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus12_phaseB");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus12_phaseC");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus14_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus14_phaseB");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus14_phaseC");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus18_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus18_phaseB");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus18_phaseC");

			// 3phase induction motor extended_device_Id =
			// "IndMotor_"+this.getId()+"@"+this.getDStabBus().getId();
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus12");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus14");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus18");

			// PV gen
			// extended_device_Id =
			// "PVGen3Phase_"+this.getId()+"@"+this.getParentGen().getParentBus().getId();
//			    sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus12");

			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(1);
			// dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));

			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(100.50);

			// dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));

			// apply the event
			// somehow fault event only works at t<1.05 and no other dynamic events are
			// working
			if (ev_ind == 0) {
//				dsNet.addDynamicEvent(
//						DStabObjectFactory.createBusFaultEvent("Bus10", proc.getSubNetworkByBusId("Bus10"),
//								SimpleFaultCode.GROUND_LG, new Complex(0.0), new Complex(0.0), 1.0d, 0.07),
//						"3phaseFault@Bus10");

				// load change event
//				dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus12",
//						proc.getSubNetworkByBusId("Bus12"), LoadChangeEventType.FIXED_TIME, 0.4, 1.0),
//						"LoadInc40%@Bus12");
//				dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus13",
//						proc.getSubNetworkByBusId("Bus12"), LoadChangeEventType.FIXED_TIME, 0.4, 1.0),
//						"LoadInc40%@Bus13");
//				dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus14",
//						proc.getSubNetworkByBusId("Bus12"), LoadChangeEventType.FIXED_TIME, 0.4, 1.0),
//						"LoadInc40%@Bus14");
//				dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus18",
//						proc.getSubNetworkByBusId("Bus18"), LoadChangeEventType.FIXED_TIME, 0.14, 1.0),
//						"LoadInc40%@Bus18");

//				 Generator trip event
//				dsNet.addDynamicEvent(
//						DStabObjectFactory.createGeneratorTripEvent("Bus3", "1", proc.getSubNetworkByBusId("Bus3"), 1),
//						"Bus1_Mach1_trip_1sec");

				// Generator energization event
//				dsNet.addDynamicEvent(DStabObjectFactory.createGeneratorEnergizationEvent("Bus3", "1",
//						proc.getSubNetworkByBusId("Bus3"), 1), "Bus3_Mach1_connect_1sec");

			} else if (ev_ind == 1) {
//				dsNet.addDynamicEvent(
//						DStabObjectFactory.createBusFaultEvent("Bus20", proc.getSubNetworkByBusId("Bus20"),
//								SimpleFaultCode.GROUND_LG, new Complex(0.0), new Complex(0.0), 1.0d, 0.07),
//						"3phaseFault@Bus20");
//				dsNet.addDynamicEvent(
//						DStabObjectFactory.createGeneratorTripEvent("Bus3", "1", proc.getSubNetworkByBusId("Bus3"), 1),
//						"Bus1_Mach1_connect_1sec");
//				
//				dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus22",
//						proc.getSubNetworkByBusId("Bus22"), LoadChangeEventType.FIXED_TIME, 0.4, 1.0),
//						"LoadInc%@Bus22");
//				dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus23",
//						proc.getSubNetworkByBusId("Bus23"), LoadChangeEventType.FIXED_TIME, 0.4, 1.0),
//						"LoadInc40%@Bus23");
//				dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus24",
//						proc.getSubNetworkByBusId("Bus24"), LoadChangeEventType.FIXED_TIME, 0.4, 1.0),
//						"LoadInc40%@Bus24");
//				dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus28",
//						proc.getSubNetworkByBusId("Bus28"), LoadChangeEventType.FIXED_TIME, 0.4, 1.0),
//						"LoadInc40%@Bus28");
			}

//			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus20",proc.getSubNetworkByBusId("Bus20"),SimpleFaultCode.GROUND_LG,new Complex(0.0),new Complex(0.0),1.0d,0.07),"3phaseFault@Bus20");

			// dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus28",
			// proc.getSubNetworkByBusId("Bus28"),LoadChangeEventType.FIXED_TIME,-0.4,
			// 1.0),"LoadReduce40%@Bus18");
//			dsNet.addDynamicEvent(createGeneratorTripEvent("Bus2", "1", proc.getSubNetworkByBusId("Bus2"), 1),"Bus1_Mach1_trip_1sec");

			IpssLogger.getLogger().setLevel(Level.WARNING);

			PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
			timer.start();
			// Must use this dynamic event process to modify the YMatrixABC
//			dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());

			dstabAlgo.setSolver(new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
			dstabAlgo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(mNetHelper));

			if (dstabAlgo.initialization()) {
				// System.out.println(ThreePhaseAclfOutFunc.busLfSummary(dsNet));

				// System.out.println(dsNet.getMachineInitCondition());

				System.out.println("* STATUS -->* " + ev_ind + " 3Phase/3sequence DStab co-simulation running ...");
				Hashtable<String, Hashtable<Integer, MonitorRecord>> a = sm.getBusFreqTable();
				int ai = 0;
				double[] errorarray = new double[1000];
				while (dstabAlgo.getSimuTime() <= dstabAlgo.getTotalSimuTimeSec()) {

					if (dstabAlgo.getSimuTime() > 0.89 & dstabAlgo.getSimuTime() < 0.91) {
						System.out.println("at 0.9 sec: " + dsNet.getBus("Bus12").calcNetPowerIntoNetwork());
						System.out.println("at 0.9 sec: " + dsNet.getBus("Bus10").calcNetPowerIntoNetwork());
					}
					if (dstabAlgo.getSimuTime() > 1.09 & dstabAlgo.getSimuTime() < 1.11) {
						System.out.println("at 1.1 sec: " + dsNet.getBus("Bus12").calcNetPowerIntoNetwork());
						System.out.println("at 1.1 sec bus10: " + dsNet.getBus("Bus10").calcNetPowerIntoNetwork());
					}

					dstabAlgo.solveDEqnStep(true);
					double simt = dstabAlgo.getSimuTime();
					// if(t>tevent){
					for (String busId : sm.getBusPhAVoltTable().keySet()) {
						// somehow sm.getBusPhAVoltTables and B and C phase dont get recorded
						// automatically
						// thats why need to add manually
						DStab3PBus b = (DStab3PBus) proc.getSubNetworkByBusId(busId).getBus(busId);
						sm.addBusPhaseVoltageMonitorRecord(busId, dstabAlgo.getSimuTime(), b.get3PhaseVotlages());
						// for comparison use this sm.getBusPhAVoltTable().get(busId).get(0).getValue()
					}
					if (dstabAlgo.getSimuTime() > 5.000) {
						String[] paramt;
						if (IndMotorPercent > 0) {
							paramt = new String[] { "Volt", "Freq", "Pgen", "Slip" };
						} else {
							paramt = new String[] { "Volt", "Freq", "Pgen" };
						}
						double[] tolerance = new double[] { 1.0e-4, 1.0e-4, 1.0e-6, 1.0e-4 }; // 0: default tolerance
						if (is_dynsim_converged(paramt, tolerance, simt, errorarray, ai, sm)) {
							break;
						}
					}
					ai = ai + 1;
				}
			}

			System.out.println(
					"* STATUS -->* Dynamics have settled, switching to power flow at t = " + dstabAlgo.getSimuTime());

			// Recording dynamics simulation values
			FileUtil.writeText2File("busvolt_" + ev_ind + ".csv", sm.toCSVString(sm.getBusVoltTable()));
			FileUtil.writeText2File("busvoltA_" + ev_ind + ".csv", sm.toCSVString(sm.getBusPhAVoltTable()));
			FileUtil.writeText2File("genPe_" + ev_ind + ".csv", sm.toCSVString(sm.getMachPeTable()));
			FileUtil.writeText2File("Freq_" + ev_ind + ".csv", sm.toCSVString(sm.getBusFreqTable()));
			FileUtil.writeText2File("ind_p_" + ev_ind + ".csv", sm.toCSVString(sm.getMotorPTable()));
			FileUtil.writeText2File("ind_q_" + ev_ind + ".csv", sm.toCSVString(sm.getMotorQTable()));
			FileUtil.writeText2File("ind_s_" + ev_ind + ".csv", sm.toCSVString(sm.getMotorSlipTable()));
			FileUtil.writeText2File("ac_p_" + ev_ind + ".csv", sm.toCSVString(sm.getAcMotorPTable()));
			FileUtil.writeText2File("ac_q_" + ev_ind + ".csv", sm.toCSVString(sm.getAcMotorQTable()));
			FileUtil.writeText2File("branch_p_" + ev_ind + ".csv", sm.toCSVString(sm.getBranchFlowPTable()));

			/**
			 * ----------------------------------------------------------- Update the power
			 * flow model with settled dynamic values: Generation and Loads
			 * ----------------------------------------------------------
			 */

			// powerflow update for generators
			System.out.println("* STATUS -->* Updating post dynamic generation for steady state");
			// proc.getSubNetworkList()dsNet
			
			//TODO the generation and load update below could be combined in one loop
			String Busid = "Bus1";
			for (int i = 0; i < proc.getSubNetworkList().size(); i++) {
				
				//reset the initialization status
				proc.getSubNetworkList().get(i).setDStabNetInitialized(false);
				proc.getSubNetworkList().get(i).setLfConverged(false);
				((DStabNetwork3Phase)proc.getSubNetworkList().get(i)).setLoadModelConverted(false);

				for (int j = 0; j < proc.getSubNetworkList().get(i).getBusList().size(); j++) {
					Busid = proc.getSubNetworkList().get(i).getBus(j).getId();
					DStab3PBus bus = (DStab3PBus) proc.getSubNetworkList().get(i).getBus(Busid);
					
					if (bus.isGen()) {
						bus.setVoltage(bus.getThreeSeqVoltage().b_1);
					}
					if(bus.isSwing()) {
						bus.toSwingBus().setDesiredVoltMag(bus.getVoltageMag());
					}
					Complex3x1 vabc = bus.get3PhaseVotlages();
					double totalBusGenP = 0;
					double totalBusGenQ = 0;
					for (DStabGen gen: bus.getContributeGenList()) {
						if (gen.isStatus()) {
							if (bus.getMachine() != null) {
								gen.setGen(new Complex(bus.getMachine().getPe(),bus.getMachine().getQGen()));
								gen.setDesiredVoltMag(bus.getVoltageMag());
								
								totalBusGenP += bus.getMachine().getPe();
								totalBusGenQ += bus.getMachine().getQGen();
							}
						}
						
					}
					if (bus.isGen()) {
						bus.initContributeGen(false);
					}

				}
			}
			System.out.println("* STATUS -->* Updating post dynamic load for steady state");
			// Load Updating post dynamics
			boolean loadupdate = false;
			// newload.set3PhaseLoad(dynLoad3P.getPower3Phase(UnitType.PU));
			// Complex total3PhaseDynLoadPQ=(0,0);
			for (int i = 0; i < proc.getSubNetworkList().size(); i++) {
				Complex3x1 total_load_net = new Complex3x1();
				Complex3x1 total_load_net1 = new Complex3x1();
				for (int j = 0; j < proc.getSubNetworkList().get(i).getBusList().size(); j++) {
					Busid = proc.getSubNetworkList().get(i).getBus(j).getId();
					DStab3PBus bus = (DStab3PBus) proc.getSubNetworkList().get(i).getBus(Busid);
					
					Complex3x1 vabc = bus.get3PhaseVotlages();
					if (!bus.getThreePhaseLoadList().isEmpty()) {
						System.out.println(Busid + " Initia:" + bus.getThreePhaseLoadList().get(0).getInit3PhaseLoad());
//						System.out.println(Busid + " before:" + bus.cal3PhaseStaticLoad());
//						System.out.println(Busid + " before:" + bus.get3PhaseNetLoadResults());
						System.out.println(Busid + " before:" + bus.calcNetPowerIntoNetwork().multiply(-1));
						total_load_net = total_load_net.add(bus.calcNetPowerIntoNetwork().multiply(-1));
					
					}
					// this update includes both single-phase and three-phase static loads
					    loadupdate = updateDistNetBusLoads((DStabNetwork3Phase) proc.getSubNetworkList().get(i), Busid);
					
					if (!bus.getThreePhaseLoadList().isEmpty()) {
						total_load_net1 = total_load_net1.add(bus.getThreePhaseLoadList().get(0).getInit3PhaseLoad());
						System.out.println(Busid + " after :" + bus.getThreePhaseLoadList().get(0).getInit3PhaseLoad());
//						System.out.println(Busid + " after:" + bus.cal3PhaseStaticLoad());
//						System.out.println(Busid + " after:" + bus.get3PhaseNetLoadResults());
						System.out.println("");
					}
					
					//reset frequency
					bus.setFreq(1.0);
					//reset the Load Change Factor
					bus.setAccumulatedLoadChangeFactor(0.0);
					//reset sequence equivalent Load
					bus.resetSeqEquivLoad();
					
				}
				System.out.println(
						"[End of Dynamic] total load is " + total_load_net + " at network " + proc.getSubNetworkList().get(i).getId());
				System.out.println(
						"[After update  ] total load is " + total_load_net1 + " at network " + proc.getSubNetworkList().get(i).getId());

			}
			
			TDMultiNetPowerflowAlgorithm tdAlgo1 = new TDMultiNetPowerflowAlgorithm(
					(BaseAclfNetwork<? extends BaseAclfBus<?,?>, ? extends AclfBranch>) dsNet, proc);
			
			if (ev_ind == 0) {
				
				//assertTrue(tdAlgo1.powerflow());
				//System.out.println("===Before gen 3 set to true \n"+AclfOutFunc.loadFlowSummary(tdAlgo1.getTransmissionNetwork()));
				
				//turn on gen 3.
				DStabGenImpl gen3_1 = (DStabGenImpl) tdAlgo1.getTransmissionNetwork().getBus("Bus3").getContributeGenList().get(0);
				gen3_1.setGen(new Complex(0.0, 0.00));
				gen3_1.setStatus(true);
				gen3_1.getDynamicGenDevice().setStatus(true);
				tdAlgo1.getTransmissionNetwork().getBus("Bus3").setGenCode(AclfGenCode.GEN_PV);
				tdAlgo1.getTransmissionNetwork().getBus("Bus3").initContributeGen(false);
				((BaseDStabBus) tdAlgo1.getTransmissionNetwork().getBus("Bus3")).resetSeqEquivLoad();
				
				//tdAlgo1.getTransmissionNetwork().setLfConverged(false);
				
				//((DStabNetwork3Phase)tdAlgo1.getTransmissionNetwork()).getGenWithoutMachBusList().remove(tdAlgo1.getTransmissionNetwork().getBus("Bus3"));
				
				assertTrue(tdAlgo1.powerflow());
	
				System.out.println("===after gen 3 set to ture \n"+AclfOutFunc.loadFlowSummary(tdAlgo1.getTransmissionNetwork()));
			}

			else
			  assertTrue(tdAlgo1.powerflow());

//			 System.out.println(tdAlgo.getTransmissionNetwork().net2String());
//			System.out.println(tdAlgo.getDistributionNetworkList().get(0).net2String());

//			System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo1.getTransmissionNetwork()));
			System.out
					.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo1.getDistributionNetworkList().get(0)));
////			 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo1.getDistributionNetworkList().get(1)));
//			 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo1.getDistributionNetworkList().get(2))); 

			timer.end();
			System.out.println("total sim time = " + timer.getDuration());
//			System.out.println(sm.toCSVString(sm.getBusVoltTable()));
//			System.out.println(sm.toCSVString(sm.getBusAngleTable()));
			// System.out.println(sm.toCSVString(sm.g etAcMotorPTable()));
			// System.out.println(sm.toCSVString(sm.getAcMotorStateTable()));

			/**
			 * ----------------------------------------------------------- Verification of
			 * updated post-dynamic power flow results
			 * ----------------------------------------------------------
			 */

			// create check for post dynamic results and steady state results
			BaseAclfNetwork<?, ?> net0 = tdAlgo1.getDistributionNetworkList().get(0);
			int final_ind = (int) (dstabAlgo.getSimuTime() * 200);
			double tol = 0.001;
			boolean match_flag = true;
			ArrayList<String> unmatched_bus = new ArrayList<String>();
			for (int i = 0; i < proc.getSubNetworkList().size(); i++) {
				net0 = proc.getSubNetworkList().get(i);
				for (BaseAclfBus bus : net0.getBusList()) {
					DStab3PBus bus3P = (DStab3PBus) bus;
					bus3P.get3PhaseVotlages().toString();
					double volt_pf = bus3P.get3PhaseVotlages().abs() / 3;
					if (sm.getBusVoltTable().get(bus3P.getId()) != null) {
						double volt_dyn = sm.getBusVoltTable().get(bus3P.getId()).get(final_ind).getValue();
//						assertTrue(Math.abs(volt_pf - volt_dyn)<tol);
						if (Math.abs(volt_pf - volt_dyn) > tol) {
							match_flag = false;
							unmatched_bus.add(bus3P.getId());
						}
					}

				}
			}
			if (match_flag == false) {
				System.out.println(
						"post dynamic power flow voltages are NOT matching within " + tol + " for following buses: ");
				System.out.println(unmatched_bus);
			} else {
				System.out.println("post dynamic power flow voltages matched within " + tol + " for all buses ");
			}

		}

	}

//	@Test
	public void test_8Busfeeder_dynSwitch() throws InterpssException {
		IpssCorePlugin.init();

		IpssCorePlugin.setLoggerLevel(Level.INFO);

		DStabNetwork3Phase dsNet = create6busFeeder();

		/**
		 * ----------------------------------------------------------- From here dynamic
		 * simulation switching loop starts
		 * ----------------------------------------------------------
		 */

		String[] dyn_event_time_list = { "fault@10", "fault@20" };
		int ev_ind;
		for (ev_ind = 0; ev_ind < dyn_event_time_list.length; ev_ind++) {
			// Lets run a powerflow before starting dynamic simulation
			// create a load flow algorithm object
			DistributionPowerFlowAlgorithm algo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(dsNet);
			// run load flow using default setting
			assertTrue(algo.powerflow());
//			System.out.println(dsNet.net2String());
			System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(dsNet));

			// make sure dynamic event list is empty in the beginning of each loop
			dsNet.getDynamicEventList().clear();
			DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(dsNet,
					IpssCorePlugin.getMsgHub());

			StateMonitor sm = new StateMonitor();
			sm.addGeneratorStdMonitor(new String[] { "MachId" });
			sm.addBusStdMonitor(new String[] { "Bus1", "Bus2", "Bus3", "Bus4", "Bus5", "Bus6" });
			sm.add3PhaseBusStdMonitor(new String[] { "Bus1", "Bus2", "Bus3", "Bus4", "Bus5", "Bus6" });

			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(1);
			// dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));

			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(100.50);

			// dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));

			// apply the event
			// somehow fault event only works at t<1.05 and no other dynamic events are
			// working
			if (ev_ind == 0) {
//				dsNet.addDynamicEvent(
//						DStabObjectFactory.createBusFaultEvent("Bus10", proc.getSubNetworkByBusId("Bus10"),
//								SimpleFaultCode.GROUND_LG, new Complex(0.0), new Complex(0.0), 1.0d, 0.07),
//						"3phaseFault@Bus10");

				// load change event
//				dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus6", dsNet,
//						LoadChangeEventType.FIXED_TIME, -0.9, 1.0), "LoadReduce40%@Bus6");
//				dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus5", dsNet,
//						LoadChangeEventType.FIXED_TIME, -0.9, 1.0), "LoadReduce40%@Bus5");
//				dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus4", dsNet,
//						LoadChangeEventType.FIXED_TIME, -0.9, 1.0), "LoadReduce40%@Bus4");
				
//				dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus28", dsNet,
//				LoadChangeEventType.FIXED_TIME, 0.4, 1.0), "LoadInc40%@Bus28");
//		dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus38", dsNet,
//				LoadChangeEventType.FIXED_TIME, 0.4, 1.0), "LoadInc40%@Bus5");
//		dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus18", dsNet,
//				LoadChangeEventType.FIXED_TIME, 0.4, 1.0), "LoadInc40%@Bus18");

//				 Generator trip event
//				dsNet.addDynamicEvent(
//						DStabObjectFactory.createGeneratorTripEvent("Bus3", "1", proc.getSubNetworkByBusId("Bus3"), 1),
//						"Bus1_Mach1_trip_1sec");

				// Generator energization event
//				dsNet.addDynamicEvent(DStabObjectFactory.createGeneratorEnergizationEvent("Bus3", "1",
//						proc.getSubNetworkByBusId("Bus3"), 1), "Bus3_Mach1_connect_1sec");

			} else if (ev_ind == 1) {
//				dsNet.addDynamicEvent(
//						DStabObjectFactory.createBusFaultEvent("Bus20", proc.getSubNetworkByBusId("Bus20"),
//								SimpleFaultCode.GROUND_LG, new Complex(0.0), new Complex(0.0), 1.0d, 0.07),
//						"3phaseFault@Bus20");
//				dsNet.addDynamicEvent(
//						DStabObjectFactory.createGeneratorTripEvent("Bus3", "1", proc.getSubNetworkByBusId("Bus3"), 1),
//						"Bus1_Mach1_connect_1sec");
			}

//			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus20",proc.getSubNetworkByBusId("Bus20"),SimpleFaultCode.GROUND_LG,new Complex(0.0),new Complex(0.0),1.0d,0.07),"3phaseFault@Bus20");

			// dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus28",
			// proc.getSubNetworkByBusId("Bus28"),LoadChangeEventType.FIXED_TIME,-0.4,
			// 1.0),"LoadReduce40%@Bus18");
//			dsNet.addDynamicEvent(createGeneratorTripEvent("Bus2", "1", proc.getSubNetworkByBusId("Bus2"), 1),"Bus1_Mach1_trip_1sec");

			IpssLogger.getLogger().setLevel(Level.WARNING);

			PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
			timer.start();
			// Must use this dynamic event process to modify the YMatrixABC
			dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
			//dsNet.initDStabNet();

			if (dstabAlgo.initialization()) {
				// System.out.println(ThreePhaseAclfOutFunc.busLfSummary(dsNet));

				// System.out.println(dsNet.getMachineInitCondition());

				System.out.println("* STATUS -->* " + ev_ind + " 3Phase/3sequence DStab simulation running ...");
				Hashtable<String, Hashtable<Integer, MonitorRecord>> a = sm.getBusFreqTable();
				int ai = 0;
				double[] errorarray = new double[1000];
				while (dstabAlgo.getSimuTime() <= dstabAlgo.getTotalSimuTimeSec()) {

					dstabAlgo.solveDEqnStep(true);
					double simt = dstabAlgo.getSimuTime();
					// if(t>tevent){
					for (String busId : sm.getBusPhAVoltTable().keySet()) {
						// somehow sm.getBusPhAVoltTables and B and C phase dont get recorded
						// automatically
						// thats why need to add manually
						DStab3PBus b = (DStab3PBus) dsNet.getBus(busId);
						sm.addBusPhaseVoltageMonitorRecord(busId, dstabAlgo.getSimuTime(), b.get3PhaseVotlages());
						// for comparison use this sm.getBusPhAVoltTable().get(busId).get(0).getValue()
					}
					if (dstabAlgo.getSimuTime() > 5.000) {
						String[] paramt;
						paramt = new String[] { "Volt", "Freq", "Pgen" };
						double[] tolerance = new double[] { 1.0e-4, 1.0e-4, 1.0e-6, 1.0e-4 }; // 0: default tolerance
						if (is_dynsim_converged(paramt, tolerance, simt, errorarray, ai, sm)) {
							break;
						}
					}
					ai = ai + 1;
				}
			}

			System.out.println(
					"* STATUS -->* Dynamics have settled, switching to power flow at t = " + dstabAlgo.getSimuTime());

			// Recording dynamics simulation values
			FileUtil.writeText2File("busvolt_" + ev_ind + ".csv", sm.toCSVString(sm.getBusVoltTable()));
			FileUtil.writeText2File("busvoltA_" + ev_ind + ".csv", sm.toCSVString(sm.getBusPhAVoltTable()));
			FileUtil.writeText2File("genPe_" + ev_ind + ".csv", sm.toCSVString(sm.getMachPeTable()));
			FileUtil.writeText2File("Freq_" + ev_ind + ".csv", sm.toCSVString(sm.getBusFreqTable()));
			FileUtil.writeText2File("ind_p_" + ev_ind + ".csv", sm.toCSVString(sm.getMotorPTable()));
			FileUtil.writeText2File("ind_q_" + ev_ind + ".csv", sm.toCSVString(sm.getMotorQTable()));
			FileUtil.writeText2File("ind_s_" + ev_ind + ".csv", sm.toCSVString(sm.getMotorSlipTable()));
			FileUtil.writeText2File("ac_p_" + ev_ind + ".csv", sm.toCSVString(sm.getAcMotorPTable()));
			FileUtil.writeText2File("ac_q_" + ev_ind + ".csv", sm.toCSVString(sm.getAcMotorQTable()));

			/**
			 * ----------------------------------------------------------- Update the power
			 * flow model with settled dynamic values: Generation and Loads
			 * ----------------------------------------------------------
			 */

			// powerflow update for generators
			System.out.println("* STATUS -->* Updating post dynamic generation for steady state");
			// proc.getSubNetworkList()dsNet

			String Busid = "Bus1";
			for (int j = 0; j < dsNet.getBusList().size(); j++) {
				Busid = dsNet.getBus(j).getId();
				DStab3PBus bus = (DStab3PBus) dsNet.getBus(Busid);
				Complex3x1 vabc = bus.get3PhaseVotlages();
				if (!bus.getContributeGenList().isEmpty()) {
//						bus.getContributeGenList().get(0).getGen()
//						System.out.println(Busid + " before: " + bus.getContributeGenList().get(0));
					System.out.println("");
				}
				if (dsNet.getBus(Busid).getGenCode() == AclfGenCode.GEN_PV) {
					dsNet.getBus(Busid).setGenP(dsNet.getBus(Busid).getMachine().getPe());
					dsNet.getBus(Busid).setGenQ(dsNet.getBus(Busid).getMachine().getQGen());
				}
				if (!bus.getContributeGenList().isEmpty()) {
//						System.out.println(Busid + " after:" + bus.getContributeGenList().get(0).getGen().getReal());
				}
			}
			System.out.println("* STATUS -->* Updating post dynamic load for steady state");
			// Load Updating post dynamics
			boolean loadupdate = false;
			// newload.set3PhaseLoad(dynLoad3P.getPower3Phase(UnitType.PU));
			// Complex total3PhaseDynLoadPQ=(0,0);
			Complex3x1 total_load_net = new Complex3x1();
			Complex3x1 total_load_net1 = new Complex3x1();
			for (int j = 0; j < dsNet.getBusList().size(); j++) {
				Busid = dsNet.getBus(j).getId();
				DStab3PBus bus = (DStab3PBus) dsNet.getBus(Busid);
				
				//reset seq equiv load init
				
				
				Complex3x1 vabc = bus.get3PhaseVotlages();
				if (!bus.getThreePhaseLoadList().isEmpty()) {
					System.out.println(Busid + " before:" + bus.getThreePhaseLoadList().get(0).getInit3PhaseLoad());
					System.out.println(Busid + " before:" + bus.cal3PhaseStaticLoad());
//						System.out.println(Busid + " before:" + bus.get3PhaseNetLoadResults());
					System.out.println(Busid + " before:" + bus.calcNetPowerIntoNetwork().multiply(-1));
					total_load_net = total_load_net.add(bus.calcNetPowerIntoNetwork().multiply(-1));
				}

				loadupdate = updateDistNetBusLoads((DStabNetwork3Phase) dsNet, Busid);
				if (!bus.getThreePhaseLoadList().isEmpty()) {
					total_load_net1 = total_load_net1.add(bus.getThreePhaseLoadList().get(0).getInit3PhaseLoad());
//					System.out.println(Busid + " after:" + bus.getThreePhaseLoadList().get(0).getInit3PhaseLoad());
//						System.out.println(Busid + " after:" + bus.cal3PhaseStaticLoad());
//						System.out.println(Busid + " after:" + bus.get3PhaseNetLoadResults());
//					System.out.println("");
				}
				bus.setAccumulatedLoadChangeFactor(0);
			}
//			System.out.println("total load is " + total_load_net);
//			System.out.println("total load is " + total_load_net1);

			DistributionPowerFlowAlgorithm algo1 = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(dsNet);
			assertTrue(algo1.powerflow());
			System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(dsNet));
			timer.end();
			System.out.println("total sim time = " + timer.getDuration());

			/**
			 * ----------------------------------------------------------- Verification of
			 * updated post-dynamic power flow results
			 * ----------------------------------------------------------
			 */

			// create check for post dynamic results and steady state results
			int final_ind = (int) (dstabAlgo.getSimuTime() * 200);
			double tol = 0.001;
			boolean match_flag = true;
			ArrayList<String> unmatched_bus = new ArrayList<String>();

			for (BaseAclfBus bus : dsNet.getBusList()) {
				DStab3PBus bus3P = (DStab3PBus) bus;
				bus3P.get3PhaseVotlages().toString();
				double volt_pf = bus3P.get3PhaseVotlages().abs() / 3;
				if (sm.getBusVoltTable().get(bus3P.getId()) != null) {
					double volt_dyn = sm.getBusVoltTable().get(bus3P.getId()).get(final_ind).getValue();
//						assertTrue(Math.abs(volt_pf - volt_dyn)<tol);
					if (Math.abs(volt_pf - volt_dyn) > tol) {
						match_flag = false;
						unmatched_bus.add(bus3P.getId());
					}
				}
			}
			if (match_flag == false) {
				System.out.println(
						"post dynamic power flow voltages are NOT matching within " + tol + "for following buses: ");
				System.out.println(unmatched_bus);
			} else {
				System.out.println("post dynamic power flow voltages matched within " + tol + " for all buses ");
			}

		}

	}

	public boolean is_dynsim_converged(String[] param, double[] tolerance, double simt, double[] errorarray, int ai,
			StateMonitor sm) {
		boolean chk = false;
		boolean converge_flag = false;
		for (int idx = 0; idx < param.length; idx++) {
			chk = convergeCHK(ai, sm, param[idx], tolerance[idx], simt, errorarray);
			if (chk == false) {
				// if even one parameter is not converged, continue simulation, return false
				converge_flag = false;
				return false;
			} else {
				converge_flag = true;
			}
		}
		if (converge_flag == true) {
			// only if all params are converged, break the simulation, return true
			return true;
		}
		return false;
	}

	public boolean convergeCHK(int ai, StateMonitor sm, String param, double tol, double simt, double[] errorarray) {
		// boolean converge = false;

		// StateMonitor sm=a;
		double maxerror = errorarray[0];
		double Tol = 1E-6;
		// ai=ai+1;
		int busindex = 0;
		Hashtable<String, Hashtable<Integer, MonitorRecord>> a = sm.getBusPhAVoltTable();
		// if(t>tevent){

		if (param == "Freq") {
			a = sm.getBusFreqTable();
			Tol = 1E-7;
		} else if (param == "Volt") {
			// sm.getBusVoltTable() is probably avergae of all 3 phases.
			// sm.getBusPhAVoltTable(),sm.getBusPhBVoltTable(),sm.getBusPhCVoltTable()
			a = sm.getBusVoltTable();
			Tol = 1E-6;
		} else if (param == "Pgen") {
			a = sm.getMachPeTable();
			Tol = 1E-7;
		} else if (param == "Slip") {
			a = sm.getMotorSlipTable();
			Tol = 1E-7;
		} else {
			System.out.println("Unknown convergence parameter");

		}
		// if tolerance input is 0, use default, otherwise
		if (tol > 0) {
			Tol = tol;
		}
		if (ai > 0) {
			for (String busId : a.keySet()) {

				// a.addBusPhaseVoltageMonitorRecord( busId,dstabAlgo.getSimuTime(),
				// ((Bus3Phase)proc.getSubNetworkByBusId(busId).getBus(busId)).get3PhaseVotlages());
				// for comparison use this sm.getBusPhAVoltTable().get(busId).get(0).getValue()

				double error = a.get(busId).get(ai).getValue() - a.get(busId).get(ai - 1).getValue();
				errorarray[busindex] = Math.abs(error);
				busindex++;
			}
		}

		for (int mi = 1; mi < busindex; mi++) {
			if (errorarray[mi] > maxerror) {
				maxerror = errorarray[mi];
			}
		}

		if (simt > 1.02) {
			if (maxerror < Tol) {
				return true;
			}

		}

		return false;
	}

	// currently it only supports single-phase A/C motor load model, and assume all
	// other loads represented by constant impedance
	private boolean updateDistNetBusLoads(DStabNetwork3Phase distNet, String Busid) {
		// private boolean updateDistNetBusLoads(DStabNetwork3Phase distNet, String
		// Busid, double loadModelVminpu){
		// iterate over all dynamic load models, and update them by calling the
		// nextStep() functions

		// obtain the dynamic load model total load, and update the bus total load
		// accordingly.
		// Stotal = Smotor+ (1-Frac_dyn)*initialTotalLoad
		// here need to use the Bus3Phase.get3PhaseInitTotalLoad and
		// getInit3PhaseVolages functions

		// for( BaseDStabBus b : distNet.getBusList()) {
		BaseDStabBus b = distNet.getBus(Busid);
		if (b.isActive()) {
			DStab3PBus bus3p = (DStab3PBus) b;
			Complex3x1 load3P = new Complex3x1();
			Complex3x1 total3PhaseDynLoadPQ = new Complex3x1();

			double phaseADynLoadPercentage = 0.0;
			double phaseBDynLoadPercentage = 0.0;
			double phaseCDynLoadPercentage = 0.0;

			for (DynamicBusDevice dynDevice : bus3p.getDynamicBusDeviceList()) {
				if (dynDevice instanceof InductionMotor) {
					DynLoadModel3Phase dynLoad3P = threePhaseInductionMotorAptr.apply((InductionMotor) dynDevice);
					((InductionMotor) dynDevice).setLoadPercent(dynLoad3P.getLoadPercent());
					if (dynLoad3P.isActive()) {
//                        dynLoad3P.initStates();
//                        total3PhaseDynLoadPQ = total3PhaseDynLoadPQ.add(dynLoad3P.getInitLoadPQ3Phase());
						total3PhaseDynLoadPQ = total3PhaseDynLoadPQ.subtract(dynLoad3P.getPower3Phase(UnitType.PU));

					}
				} else if (dynDevice instanceof DynLoadModel1Phase) {
					DynLoadModel1Phase dynLoad1P = (DynLoadModel1Phase) dynDevice;
					if (dynLoad1P.isActive()) {
//						dynLoad3P.initStates();
						if (dynLoad1P.getPhase() == PhaseCode.A) {
							total3PhaseDynLoadPQ.a_0 = total3PhaseDynLoadPQ.a_0.add(dynLoad1P.getLoadPQ());
						} else if (dynLoad1P.getPhase() == PhaseCode.B) {
							total3PhaseDynLoadPQ.b_1 = total3PhaseDynLoadPQ.b_1.add(dynLoad1P.getLoadPQ());
						} else if (dynLoad1P.getPhase() == PhaseCode.C) {
							total3PhaseDynLoadPQ.c_2 = total3PhaseDynLoadPQ.c_2.add(dynLoad1P.getLoadPQ());
						}
					}
				}

			}

//			if (bus3p.getPhaseADynLoadList().size() > 0) {
//				for (DynLoadModel1Phase dynLdPhA : bus3p.getPhaseADynLoadList()) {
//					// TODO need to check the unit
//					if (dynLdPhA.getLoadPercent() > 0) {
//						load3P.a_0 = load3P.a_0.add(dynLdPhA.getLoadPQ());
//					}
//
//					phaseADynLoadPercentage += dynLdPhA.getLoadPercent();
//				}
//			}
//
//			if (bus3p.getPhaseBDynLoadList().size() > 0) {
//				for (DynLoadModel1Phase dynLdPhB : bus3p.getPhaseBDynLoadList()) {
//
//					// TODO need to check the unit
//					if (dynLdPhB.getLoadPercent() > 0) {
//						load3P.b_1 = load3P.b_1.add(dynLdPhB.getLoadPQ());
//					}
//
//					phaseBDynLoadPercentage += dynLdPhB.getLoadPercent();
//
//				}
//
//			}
//
//			if (bus3p.getPhaseCDynLoadList().size() > 0) {
//				for (DynLoadModel1Phase dynLdPhC : bus3p.getPhaseCDynLoadList()) {
//
//					// TODO need to check the unit
//
//					if (dynLdPhC.getLoadPercent() > 0) {
//						load3P.c_2 = load3P.c_2.add(dynLdPhC.getLoadPQ());
//					}
//
//					phaseCDynLoadPercentage += dynLdPhC.getLoadPercent();
//				}
//
//			}

			if (bus3p.get3PhaseTotalLoad().absMax() > 0.0) {// There
															// are
															// dynamic
															// loads
				/*
				 * alok-ankit Complex3x1 vfact =
				 * (bus3p.get3PhaseVotlages().multiply(bus3p.get3PhaseVotlages().conjugate()));
				 * Complex3x1 vbas =
				 * bus3p.get3PhaseInitVoltage().multiply(bus3p.get3PhaseInitVoltage().conjugate(
				 * ));
				 * 
				 * Complex pa =
				 * bus3p.get3PhaseNetLoadResults().a_0.multiply(vfact.a_0.divide(vbas.a_0));
				 * Complex pb =
				 * bus3p.get3PhaseNetLoadResults().b_1.multiply(vfact.b_1.divide(vbas.b_1));
				 * Complex pc =
				 * bus3p.get3PhaseNetLoadResults().c_2.multiply(vfact.c_2.divide(vbas.c_2));
				 * Complex3x1 initNonDynLoad3P = new Complex3x1(pa,pb,pc); // Complex3x1
				 * initNonDynLoad3P = bus3p.get3PhaseNetLoadResults().dotProduct(vfact); //
				 * V^2/Vb^2.
				 */
				Complex3x1 initNonDynLoad3P = bus3p.cal3PhaseStaticLoad();
//				Complex3x1 initNonDynLoad3P = bus3p.calcNetPowerIntoNetwork().multiply(-1);
//				Complex3x1 initNonDynLoad3P = bus3p.getThreePhaseLoadList().get(0).getInit3PhaseLoad()
//						.multiply(1 + bus3p.getAccumulatedLoadChangeFactor());
//				bus3p.setAccumulatedLoadChangeFactor(0);
				bus3p.getThreePhaseLoadList().clear();

				// bus3p.get3PhaseVotlages().multiply(bus3p.get3PhaseVotlages().conjugate())

				// TODO here assume all loads are constant power loads
				bus3p.setLoadCode(AclfLoadCode.CONST_P);

				DStab3PLoad load1 = new DStab3PLoadImpl();

//				  		System.out.println("3phase dyn load = "+load3P.toString());

				load1.set3PhaseLoad(total3PhaseDynLoadPQ.add(initNonDynLoad3P));// the new total load;

				// load1.setVminpu(loadModelVminpu);

				bus3p.getThreePhaseLoadList().add(load1);

			}

			// TODO Implement addition of voltage dependent loads and non dynamic voltage
			// dependent load update.

		} // end of if-active
			// } // end of for-loop

		return true;

	}

	/**
	 * The first bus is feeder sending end, no load is connected; all the loads are
	 * connected at bus [2,...BusNum];
	 * 
	 * The base case of the feeder is assumed to serve 8 MW load, feeder impedances
	 * are re-scaled based on the totalMW
	 * 
	 * @param totalMW
	 * @return
	 * @throws InterpssException
	 */
	public DStabNetwork3Phase createFeeder(DStabNetwork3Phase net, DStab3PBus sourceBus, int startBusNum,
			double baseVolt, int BusNum, double totalMW, double XfrMVA, double loadPF, double[] loadPercentAry,
			double loadUnbalanceFactor, double[] sectionLength) throws InterpssException {

		double scaleFactor = totalMW;
		double zscaleFactor = totalMW / 8.0;
		double q2pfactor = Math.tan(Math.acos(loadPF));

		int loadIdx = 0;
		for (int i = startBusNum; i < startBusNum + BusNum; i++) {
			DStab3PBus bus = ThreePhaseObjectFactory.create3PDStabBus("Bus" + i, net);
			bus.setAttributes("feeder bus " + i, "");
			bus.setBaseVoltage(baseVolt);

			// set the bus to a non-generator bus
			bus.setGenCode(AclfGenCode.GEN_PQ);
			// set the bus to a constant power load bus
			bus.setLoadCode(AclfLoadCode.CONST_P);

			if (i == startBusNum) {
				bus.setBaseVoltage(69000.0);
				bus.setGenCode(AclfGenCode.NON_GEN);
				// set the bus to a constant power load bus
				bus.setLoadCode(AclfLoadCode.NON_LOAD);
			}

			if (i >= startBusNum + 2) {
				DStab3PLoad load1 = new DStab3PLoadImpl();
				Complex3x1 load3Phase = new Complex3x1(new Complex(0.01, 0.01 * q2pfactor),
						new Complex(0.01, 0.01 * q2pfactor).multiply(1 - loadUnbalanceFactor),
						new Complex(0.01, 0.01 * q2pfactor).multiply(1 + loadUnbalanceFactor));
				load1.set3PhaseLoad(load3Phase.multiply(scaleFactor * loadPercentAry[loadIdx++]));
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

		DStab3PBranch xfr1 = ThreePhaseObjectFactory.create3PBranch(sourceBus.getId(),
				net.getBus("Bus" + startBusNum).getId(), "0", net);
		xfr1.setBranchCode(AclfBranchCode.XFORMER);
		xfr1.setToTurnRatio(1.02);
		xfr1.setZ(new Complex(0.0, 0.08).multiply(100.0 / XfrMVA));

		AcscXformerAdapter xfr01 = acscXfrAptr.apply(xfr1);
		xfr01.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0, 0.0));
		xfr01.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0, 0.0));

		DStab3PBranch xfr1_2 = ThreePhaseObjectFactory.create3PBranch(net.getBus("Bus" + startBusNum).getId(),
				net.getBus("Bus" + (startBusNum + 1)).getId(), "0", net);
		xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
		xfr1_2.setToTurnRatio(1.02);
		xfr1_2.setZ(new Complex(0.0, 0.06).multiply(100.0 / XfrMVA));

		AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr1_2);
		//xfr0.setFromConnectGroundZ(XfrConnectCode.DELTA11, new Complex(0.0, 0.0), UnitType.PU);
		//xfr0.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0, 0.0), UnitType.PU);
		xfr0.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA11, new Complex(0.0, 0.0));
		xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0, 0.0));

		int k = 0;
		for (int i = startBusNum + 1; i < startBusNum + BusNum - 1; i++) {

			DStab3PBranch Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus" + i, "Bus" + (i + 1), "0", net);
			Line2_3.setBranchCode(AclfBranchCode.LINE);

			Complex3x3 zabcActual = IEEEFeederLineCode.zMtx601;
			if (k >= 3 && k < 5)
				zabcActual = IEEEFeederLineCode.zMtx602;
			else if (k >= 5)
				zabcActual = IEEEFeederLineCode.zMtx606;

			zabcActual = zabcActual.multiply(sectionLength[k] / zscaleFactor);

			Double zbase = net.getBus("Bus" + i).getBaseVoltage() * net.getBus("Bus" + i).getBaseVoltage()
					/ net.getBaseMva() / 1.0E6;
			Line2_3.setZabc(zabcActual.multiply(1 / zbase));
			if (k == 2 || k == 4 || k == 6) {
				Complex3x3 shuntYabc = new Complex3x3(new Complex(0, 0.0005), new Complex(0.0, 0.0005),
						new Complex(0.0, 0.0005));
				Line2_3.setFromShuntYabc(shuntYabc.multiply(scaleFactor));
				// Line2_3.setFromShuntYabc(new new Complex3x1(new Complex(0,-0.0005),new
				// Complex(0.0,-0.0005),new Complex(0.0,-0.0005));
			}
			k++;
		}

		return net;

	}

	public DStabNetwork3Phase createBalancedFeeder(DStabNetwork3Phase net, DStab3PBus sourceBus, int startBusNum,
			double baseVolt, int BusNum, double totalMW, double XfrMVA, double loadPF, double[] loadPercentAry,
			double loadUnbalanceFactor, double[] sectionLength) throws InterpssException {

		double scaleFactor = totalMW;
		double zscaleFactor = totalMW / 8.0;
		double q2pfactor = Math.tan(Math.acos(loadPF));

		int loadIdx = 0;
		for (int i = startBusNum; i < startBusNum + BusNum; i++) {
			DStab3PBus bus = ThreePhaseObjectFactory.create3PDStabBus("Bus" + i, net);
			bus.setAttributes("feeder bus " + i, "");
			bus.setBaseVoltage(baseVolt);

			// set the bus to a non-generator bus
			bus.setGenCode(AclfGenCode.GEN_PQ);
			// set the bus to a constant power load bus
			bus.setLoadCode(AclfLoadCode.CONST_P);

			if (i == startBusNum) {
				bus.setBaseVoltage(69000.0);
				bus.setGenCode(AclfGenCode.NON_GEN);
				// set the bus to a constant power load bus
				bus.setLoadCode(AclfLoadCode.NON_LOAD);
			}

			if (i >= startBusNum + 2) {
				DStab3PLoad load1 = new DStab3PLoadImpl();
				Complex3x1 load3Phase = new Complex3x1(new Complex(0.01, 0.01 * q2pfactor),
						new Complex(0.01, 0.01 * q2pfactor).multiply(1 - loadUnbalanceFactor),
						new Complex(0.01, 0.01 * q2pfactor).multiply(1 + loadUnbalanceFactor));
				load1.set3PhaseLoad(load3Phase.multiply(scaleFactor * loadPercentAry[loadIdx++]));
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

		DStab3PBranch xfr1 = ThreePhaseObjectFactory.create3PBranch(sourceBus.getId(),
				net.getBus("Bus" + startBusNum).getId(), "0", net);
		xfr1.setBranchCode(AclfBranchCode.XFORMER);
		xfr1.setToTurnRatio(1.02);
		xfr1.setZ(new Complex(0.0, 0.08).multiply(100.0 / XfrMVA));

		AcscXformerAdapter xfr01 = acscXfrAptr.apply(xfr1);
//		xfr01.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0, 0.0), UnitType.PU);
//		xfr01.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0, 0.0), UnitType.PU);
		xfr01.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0, 0.0));
		xfr01.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0, 0.0));

		DStab3PBranch xfr1_2 = ThreePhaseObjectFactory.create3PBranch(net.getBus("Bus" + startBusNum).getId(),
				net.getBus("Bus" + (startBusNum + 1)).getId(), "0", net);
		xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
		xfr1_2.setToTurnRatio(1.02);
		xfr1_2.setZ(new Complex(0.0, 0.06).multiply(100.0 / XfrMVA));

		AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr1_2);
//		xfr0.setFromConnectGroundZ(XfrConnectCode.DELTA11, new Complex(0.0, 0.0), UnitType.PU);
//		xfr0.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0, 0.0), UnitType.PU);
		xfr0.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA11, new Complex(0.0, 0.0));
		xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0, 0.0));

		int k = 0;
		for (int i = startBusNum + 1; i < startBusNum + BusNum - 1; i++) {

			DStab3PBranch Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus" + i, "Bus" + (i + 1), "0", net);
			Line2_3.setBranchCode(AclfBranchCode.LINE);

			Complex3x3 zabcActual = IEEEFeederLineCode.zMtx601;
			if (k >= 3 && k < 5)
				zabcActual = IEEEFeederLineCode.zMtx602;
			else if (k >= 5)
				zabcActual = IEEEFeederLineCode.zMtx606;

			zabcActual.ab = new Complex(0, 0);
			zabcActual.ac = new Complex(0, 0);
			zabcActual.ba = new Complex(0, 0);
			zabcActual.bc = new Complex(0, 0);
			zabcActual.ca = new Complex(0, 0);
			zabcActual.cb = new Complex(0, 0);

			zabcActual.bb = zabcActual.aa;
			zabcActual.cc = zabcActual.aa;

			zabcActual = zabcActual.multiply(sectionLength[k] / zscaleFactor);

			Double zbase = net.getBus("Bus" + i).getBaseVoltage() * net.getBus("Bus" + i).getBaseVoltage()
					/ net.getBaseMva() / 1.0E6;
			Line2_3.setZabc(zabcActual.multiply(1 / zbase));
			if (k == 2 || k == 4 || k == 6) {
				Complex3x3 shuntYabc = new Complex3x3(new Complex(0, 0.0005), new Complex(0.0, 0.0005),
						new Complex(0.0, 0.0005));
				Line2_3.setFromShuntYabc(shuntYabc.multiply(scaleFactor));
				// Line2_3.setFromShuntYabc(new new Complex3x1(new Complex(0,-0.0005),new
				// Complex(0.0,-0.0005),new Complex(0.0,-0.0005));
			}
			k++;
		}

		return net;

	}

	private void buildFeederDynModel(DStabNetwork3Phase dsNet, int startBusNum, int endBusNum, double ACMotorPercent,
			double IndMotorPercent, double ACPhaseUnbalance, double totalLoadMW, double[] loadPercentAry) {

		int k = 0;
		for (int i = startBusNum; i <= endBusNum; i++) {
			DStab3PBus loadBus = (DStab3PBus) dsNet.getBus("Bus" + i);

			/*
			 * Load3Phase load1 = new Load3PhaseImpl(); load1.set3PhaseLoad(new
			 * Complex3x1(new Complex(0.3,0.05),new Complex(0.3,0.05),new
			 * Complex(0.3,0.05))); loadBus.getThreePhaseLoadList().add(load1);
			 */

			// AC motor, 50%
			if (ACMotorPercent > 0) {

				SinglePhaseACMotor ac1 = new SinglePhaseACMotor(loadBus, "1");
				ac1.setLoadPercent(ACMotorPercent - ACPhaseUnbalance);
				ac1.setPhase(PhaseCode.A);

				ac1.setTstall(0.05); // disable ac stalling
				ac1.setVstall(0.65);
				loadBus.getPhaseADynLoadList().add(ac1);

				SinglePhaseACMotor ac2 = new SinglePhaseACMotor(loadBus, "2");
				ac2.setLoadPercent(ACMotorPercent);
				ac2.setPhase(PhaseCode.B);
				ac2.setTstall(0.05); // disable ac stalling
				ac2.setVstall(0.65);
				loadBus.getPhaseBDynLoadList().add(ac2);

				SinglePhaseACMotor ac3 = new SinglePhaseACMotor(loadBus, "3");
				ac3.setLoadPercent(ACMotorPercent + ACPhaseUnbalance);
				ac3.setPhase(PhaseCode.C);
				ac3.setTstall(0.05); // disable ac stalling
				ac3.setVstall(0.65);
				loadBus.getPhaseCDynLoadList().add(ac3);
			}

			// 3 phase motor, 20%
			if (IndMotorPercent > 0) {

				InductionMotorImpl indMotor = new InductionMotorImpl(loadBus, "1");
				indMotor.setDStabBus(loadBus);

				indMotor.setXm(3.0);
				indMotor.setXl(0.07);
				indMotor.setRa(0.032);
				indMotor.setXr1(0.3);
				indMotor.setRr1(0.01);

				double motorMVA = totalLoadMW * loadPercentAry[k] * IndMotorPercent / 100.0 / 0.8;
				indMotor.setMvaBase(motorMVA);
				indMotor.setH(0.3);
				indMotor.setA(0.0); // Toreque = (a+bw+cw^2)*To;
				indMotor.setB(0.0); // Toreque = (a+bw+cw^2)*To;
				indMotor.setC(1.0); // Toreque = (a+bw+cw^2)*To;
				indMotor.setLoadPercent(IndMotorPercent);
				// InductionMotor3PhaseAdapter indMotor3Phase = new
				// InductionMotor3PhaseAdapter(indMotor);
				// indMotor3Phase.setLoadPercent(IndMotorPercent); // 0.06 MW
				// loadBus.getThreePhaseDynLoadList().add(indMotor3Phase);
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

	public DStabNetwork3Phase create6busFeeder() throws InterpssException {

		DStabNetwork3Phase net = ThreePhaseObjectFactory.create3PhaseDStabNetwork();

		// identify this is a distribution network
		net.setNetworkType(NetworkType.DISTRIBUTION);

		DStab3PBus bus1 = ThreePhaseObjectFactory.create3PDStabBus("Bus1", net);
		bus1.setAttributes("69 kV feeder source", "");
		bus1.setBaseVoltage(69000.0);
		// set the bus to a non-generator bus
		bus1.setGenCode(AclfGenCode.SWING);
		// set the bus to a constant power load bus
		bus1.setLoadCode(AclfLoadCode.NON_LOAD);
		bus1.setVoltage(new Complex(1.01, 0));

		DStab3PGen constantGen = ThreePhaseObjectFactory.create3PGenerator("Source");
		constantGen.setMvaBase(100);
		constantGen.setPosGenZ(new Complex(0.0, 0.05));
		constantGen.setNegGenZ(new Complex(0.0, 0.05));
		constantGen.setZeroGenZ(new Complex(0.0, 0.05));
		bus1.getContributeGenList().add(constantGen);

		EConstMachine mach = (EConstMachine) DStabObjectFactory.createMachine("MachId", "MachName",
				MachineModelType.ECONSTANT, net, "Bus1", "Source");

		mach.setRating(100, UnitType.mVA, net.getBaseKva());
		mach.setRatedVoltage(69000.0);
		mach.setH(50000.0);
		mach.setXd1(0.05);

		DStab3PBus bus2 = ThreePhaseObjectFactory.create3PDStabBus("Bus2", net);
		bus2.setAttributes("feeder bus 2", "");
		bus2.setBaseVoltage(12500.0);
		// set the bus to a non-generator bus
		bus2.setGenCode(AclfGenCode.NON_GEN);
		// set the bus to a constant power load bus
		bus2.setLoadCode(AclfLoadCode.CONST_P);

		DStab3PBranch xfr1_2 = ThreePhaseObjectFactory.create3PBranch("Bus1", "Bus2", "0", net);
		xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
		xfr1_2.setToTurnRatio(1.02);
		xfr1_2.setZ(new Complex(0.0, 0.04));
		// xfr1_2.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex( 0.0, 0.04
		// )));
		// xfr1_2.setZ0( new Complex(0.0, 0.4 ));

		AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr1_2);
//		xfr0.setFromConnectGroundZ(XfrConnectCode.DELTA11, new Complex(0.0, 0.0), UnitType.PU);
//		xfr0.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0, 0.0), UnitType.PU);
		xfr0.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA11, new Complex(0.0, 0.0));
		xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0, 0.0));
		
		DStab3PBus bus3 = ThreePhaseObjectFactory.create3PDStabBus("Bus3", net);
		bus3.setAttributes("feeder bus 3", "");
		bus3.setBaseVoltage(12500.0);
		// set the bus to a non-generator bus
		bus3.setGenCode(AclfGenCode.GEN_PQ);
		// set the bus to a constant power load bus
		bus3.setLoadCode(AclfLoadCode.CONST_P);

//			Gen3Phase gen1 = new Gen3PhaseImpl();
//			gen1.setParentBus(bus3);
//			gen1.setId("PVGen");
//			gen1.setGen(new Complex(0.5,0));  // total gen power, system mva based
//			
//			bus3.getThreePhaseGenList().add(gen1);

		DStab3PBus bus4 = ThreePhaseObjectFactory.create3PDStabBus("Bus4", net);
		bus4.setAttributes("feeder bus 4", "");
		bus4.setBaseVoltage(12500.0);
		// set the bus to a non-generator bus
		bus4.setGenCode(AclfGenCode.GEN_PQ);
		// set the bus to a constant power load bus
		bus4.setLoadCode(AclfLoadCode.CONST_P);

		DStab3PBus bus5 = ThreePhaseObjectFactory.create3PDStabBus("Bus5", net);
		bus5.setAttributes("feeder bus 5", "");
		bus5.setBaseVoltage(12500.0);
		// set the bus to a non-generator bus
		bus5.setGenCode(AclfGenCode.GEN_PQ);
		// set the bus to a constant power load bus
		bus5.setLoadCode(AclfLoadCode.CONST_P);

		DStab3PBus bus6 = ThreePhaseObjectFactory.create3PDStabBus("Bus6", net);
		bus6.setAttributes("feeder bus 6", "");
		bus6.setBaseVoltage(12500.0);
		// set the bus to a non-generator bus
		bus6.setGenCode(AclfGenCode.GEN_PQ);
		// set the bus to a constant power load bus
		bus6.setLoadCode(AclfLoadCode.CONST_P);

		for (int i = 2; i <= 6; i++) {
			DStab3PBus loadBus = (DStab3PBus) net.getBus("Bus" + i);
			DStab3PLoad load1 = new DStab3PLoadImpl();
			load1.set3PhaseLoad(
					new Complex3x1(new Complex(0.01, 0.001), new Complex(0.01, 0.001), new Complex(0.01, 0.001)));
			loadBus.getThreePhaseLoadList().add(load1);

		}

		for (int i = 2; i < 6; i++) {
			DStab3PBranch Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus" + i, "Bus" + (i + 1), "0", net);
			Line2_3.setBranchCode(AclfBranchCode.LINE);
			Complex3x3 zabcActual = this.getFeederZabc601().multiply(5.28);
			Double zbase = net.getBus("Bus" + i).getBaseVoltage() * net.getBus("Bus" + i).getBaseVoltage()
					/ net.getBaseMva() / 1.0E6;
			Line2_3.setZabc(zabcActual.multiply(1 / zbase));

		}

		return net;

	}

	private Complex3x3 getFeederZabc601() {
		Complex3x3 zabc = new Complex3x3();
		zabc.aa = new Complex(0.065625, 0.192784091);
		zabc.ab = new Complex(0.029545455, 0.095018939);
		zabc.ac = zabc.ab;
		zabc.ba = zabc.ab;
		zabc.bb = zabc.aa;
		zabc.bc = zabc.ab;
		zabc.ca = zabc.ac;
		zabc.cb = zabc.bc;
		zabc.cc = zabc.aa;

		return zabc;
	}
}