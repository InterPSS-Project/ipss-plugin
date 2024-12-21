package org.interpss.multiNet.algo;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;
import static org.junit.Assert.assertTrue;

import java.awt.datatransfer.SystemFlavorMap;
import java.awt.font.TextHitInfo;
import java.awt.print.Printable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.ml.neuralnet.twod.util.HitHistogram;
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
import org.interpss.multiNet.test.unit.investigate_3PHSubNetYabc;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.threePhase.basic.IEEEFeederLineCode;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab3PGenImpl;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.model.DynGenModel3Phase;
import org.interpss.threePhase.dynamic.model.InductionMotor3PhaseAdapter;
import org.interpss.threePhase.dynamic.model.PVDistGen3Phase;
import org.interpss.threePhase.dynamic.model.PVDistGen3Phase_DER_A;
import org.interpss.threePhase.dynamic.model.impl.SinglePhaseACMotor;
import org.interpss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.interpss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.hazelcast.spi.impl.executionservice.impl.DelegatingTaskScheduler;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.core.acsc.adpter.AcscXformerAdapter;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabLoad;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.threePhase.basic.dstab.DStab3PGen;

import com.interpss.core.acsc.BaseAcscBus;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;

public class TestDER_A_model9Bus {
	@Test
	public void TestTnD_IEEE9_Feeder_constZLoad_SimulateFaultsAllBuses() throws InterpssException {
			
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
				
				
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();

		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
	    
	    createFeeder(dsNet);
	    
	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);

	    proc.addSubNetInterfaceBranch("Bus6->Bus10(0)",false);

		 
		proc.splitFullSystemIntoSubsystems(true);
		 
		proc.set3PhaseSubNetByBusId("Bus10");

	
		TDMultiNetPowerflowAlgorithm tdAlgo = new TDMultiNetPowerflowAlgorithm((BaseAclfNetwork<? extends AclfBus, ? extends AclfBranch>) dsNet,proc);
		 
		//System.out.println(tdAlgo.getTransmissionNetwork().net2String());
		//dsNet.initBusVoltage();
		//tdAlgo.getTransmissionNetwork().initBusVoltage();
		//tdAlgo.getTransmissionNetwork().setRefBusId("GBus31");
		//tdAlgo.getTransLfAlgorithm().setLfMethod(AclfMethodType.NR);
		//tdAlgo.getTransLfAlgorithm().getLfAdjAlgo().setApplyAdjustAlgo(false);
		//tdAlgo.getTransLfAlgorithm().setInitBusVoltage(false);
		//tdAlgo.getTransmissionNetwork().getBus("Bus31").getContributeGen(null).setPGenLimit(null)
		LoadflowAlgorithm tAlgo = tdAlgo.getTransLfAlgorithm();
		 tAlgo.setLfMethod(AclfMethodType.NR);
		 tAlgo.setTolerance(1.0E-6);
		 tAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		 tAlgo.setNonDivergent(true);
		 tAlgo.setInitBusVoltage(true); 
		 
		//dsNet.initBusVoltage();
		System.out.println(AclfOutFunc.loadFlowSummary(proc.getNet()));
		assertTrue(tdAlgo.powerflow()); 
		 
		 
		System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
		 //System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
	
	
		MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
		  
		  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
		DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
	    
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(1.0);
		
		//System.out.println(tdAlgo.getTransmissionNetwork().getNoBus());
		String fault_placement_busString = "Bus5";
		System.out.print("processing: ");
		System.out.println(fault_placement_busString);
		
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(fault_placement_busString,proc.getSubNetworkByBusId(fault_placement_busString),
				SimpleFaultCode.GROUND_3P,new Complex(0.0),null,0.5d,0.07),"3phaseFault@" + fault_placement_busString);
		
		StateMonitor sm = new StateMonitor();

		String[] busIdList = new String[tdAlgo.getTransmissionNetwork().getNoBus()];
		for (int i = 0; i < tdAlgo.getTransmissionNetwork().getNoBus(); i++) {
			busIdList[i]= tdAlgo.getTransmissionNetwork().getBusList().get(i).getId(); 
			//System.out.println(busIdList[i]);
		}
		//System.out.println(busIdList[250]);
		
		sm.addBusStdMonitor(busIdList);
		//sm.add3PhaseBusStdMonitor(busIdList);
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1", "Bus2-mach1", "Bus3-mach1"});
		//sm.addGeneratorStdMonitor(new String[]{"Bus10023-mach1"});
		//sm.add3PhaseBusStdMonitor(new String[]{"Bus28","Bus38"});
		//String[] seqVotBusAry = new String[]{"Bus28","Bus27","Bus26","Bus24","Bus22","Bus18","Bus16","Bus15"};
		//sm.add3PhaseBusStdMonitor(seqVotBusAry);
	
		sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus11");
		
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		
		IpssLogger.getLogger().setLevel(Level.WARNING);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		timer.start();
		
        // Must use this dynamic event process to modify the YMatrixABC
//					dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
		
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
				//System.out.println("");
				System.out.println(dstabAlgo.getSimuTime());
				System.out.println("");
				
//					for(String busId: sm.getBusPhAVoltTable().keySet()){
//						
//						 sm.addBusPhaseVoltageMonitorRecord( busId,dstabAlgo.getSimuTime(), ((DStab3PBus)proc.getSubNetworkByBusId(busId).getBus(busId)).get3PhaseVotlages());
//					}
				
			}
		}
		
		System.out.println("End of Simulation");
		
		timer.end();
		System.out.println("time :"+timer.getDuration());
		//System.out.println(sm.toCSVString(sm.getMachPeTable()));
		
		FileUtil.writeText2File("/Users/ocornmesser/Desktop/Research/Graduate/Thesis/results_raw/voltTable9Bus_Fault"+fault_placement_busString+"_PostSC.csv",
				 sm.toCSVString(sm.getBusVoltTable()));
		FileUtil.writeText2File("/Users/ocornmesser/Desktop/Research/Graduate/Thesis/results_raw/PVGenIpTable9Bus.csv",
				 sm.toCSVString(sm.getPvGenIpTable()));
		FileUtil.writeText2File("/Users/ocornmesser/Desktop/Research/Graduate/Thesis/results_raw/PVGenPTable9Bus.csv",
				 sm.toCSVString(sm.getPvGenPTable()));
		FileUtil.writeText2File("/Users/ocornmesser/Desktop/Research/Graduate/Thesis/results_raw/PVGenVTable9Bus.csv",
				 sm.toCSVString(sm.getPvGenVtTable()));
		FileUtil.writeText2File("/Users/ocornmesser/Desktop/Research/Graduate/Thesis/results_raw/genPOutput9Bus.csv",
				 sm.toCSVString(sm.getMachPeTable()));
//			FileUtil.writeText2File("/Users/ocornmesser/Desktop/Research/Graduate/Thesis/results_raw/busPhAVoltTableResult.csv",
//					 sm.toCSVString(sm.getBusPhAVoltTable()));
//			FileUtil.writeText2File("/Users/ocornmesser/Desktop/Research/Graduate/Thesis/results_raw/busPhBVoltTableResult.csv",
//					 sm.toCSVString(sm.getBusPhBVoltTable()));
//			FileUtil.writeText2File("/Users/ocornmesser/Desktop/Research/Graduate/Thesis/results_raw/busPhCVoltTableResult.csv",
//					 sm.toCSVString(sm.getBusPhCVoltTable()));
		
}
	
	
	public DStabNetwork3Phase createFeeder(DStabNetwork3Phase net) throws InterpssException{

	    
	    System.out.println("creating feeder at: Bus10");
		
		System.out.println("new dist. bus: Bus10");
		DStab3PBus bus10 = ThreePhaseObjectFactory.create3PDStabBus("Bus10", net);
		bus10.setAttributes("feeder bus 10", "");
		bus10.setBaseVoltage(69000.0);
		bus10.setGenCode(AclfGenCode.NON_GEN);
		bus10.setLoadCode(AclfLoadCode.NON_LOAD);
		
		System.out.println("new dist. bus: Bus11");
		DStab3PBus bus11 = ThreePhaseObjectFactory.create3PDStabBus("Bus11", net);
		bus11.setAttributes("feeder bus 11", "");
		bus11.setBaseVoltage(13800.0);
		bus11.setGenCode(AclfGenCode.GEN_PV);
		bus11.setLoadCode(AclfLoadCode.NON_LOAD);
		
		DStab3PBranch xfr1 = ThreePhaseObjectFactory.create3PBranch("Bus6", "Bus10", "0", net);
		xfr1.setBranchCode(AclfBranchCode.XFORMER);
		xfr1.setToTurnRatio(1.02);
		xfr1.setZ(new Complex( 0.0, 0.08));
		
		AcscXformerAdapter xfr01 = acscXfrAptr.apply(xfr1);
		xfr01.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
		xfr01.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
	
		System.out.println("new xfmr from Bus6 to Bus10");
		
		DStab3PBranch xfr1_2 = ThreePhaseObjectFactory.create3PBranch("Bus10","Bus11", "0", net);
		xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
		xfr1_2.setToTurnRatio(1.02);
		xfr1_2.setZ(new Complex( 0.0, 0.06 ));
	
	
		AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr1_2);
		xfr0.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
		xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);

		System.out.println("new xfmr from Bus10 to Bus11");
		
		double pvGen = 25;
	    DStab3PGen gen1 = ThreePhaseObjectFactory.create3PGenerator("DER_A");
	    DStab3PBus DERBus = (DStab3PBus) net.getBus("Bus11");
		gen1.setParentBus(DERBus);
		gen1.setId("DER_A");
		gen1.setGen(new Complex(pvGen/100, 0));  // total gen power, system mva based
		System.out.println("pv gen, PU: " + pvGen / 100);
		
		DERBus.getThreePhaseGenList().add(gen1);
		DERBus.getContributeGenList().add(gen1);
		
		double pvMVABase = pvGen/0.8;
		gen1.setMvaBase(pvMVABase); // for dynamic simulation only
		gen1.setPosGenZ(new Complex(0,2.5E-1));   // assuming open-circuit
		gen1.setNegGenZ(new Complex(0,2.5E-1));
		gen1.setZeroGenZ(new Complex(0,2.5E-1));
		//create the PV Distributed generation model
		PVDistGen3Phase_DER_A pv = new PVDistGen3Phase_DER_A(gen1);
		pv.setId("1");
		pv.enableVoltControl();
		pv.enablePowerFreqControl();
		pv.initStates(DERBus);
		
		return net; 
		
		
		
	}
    
	
}