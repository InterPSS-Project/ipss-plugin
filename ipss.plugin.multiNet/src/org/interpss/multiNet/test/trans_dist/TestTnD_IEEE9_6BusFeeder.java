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
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.core.acsc.adpter.AcscXformerAdapter;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
public class TestTnD_IEEE9_6BusFeeder {
	
	@Test
	public void test_IEEE9_feeder_co_dynamicSim() throws InterpssException{
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
	    
	    double PVPenetrationLevel = .00;
	    double PVIncrement = PVPenetrationLevel/(1-PVPenetrationLevel) ;
	    
	    //original setting
//	    double ACMotorPercent = 40; 
//	    double IndMotorPercent = 5;
//	    double ACPhaseUnbalance = 5.0;
	    
	    //debug setting
	    double ACMotorPercent = 40.0; 
	    double IndMotorPercent = 50;
	    double ACPhaseUnbalance = 5.0;
	    
	    double[] loadPercentAry = new double[]{0.2,0.2,0.2,0.2,0.2} ;
	    
	    dsNet.getBus("Bus5").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus5").setLoadCode(AclfLoadCode.NON_LOAD);
	    
	    double netTotalLoad = 125;
	    double totalLoad = netTotalLoad*(1+PVIncrement);
	    
	    addFeeder2Bus(dsNet,(DStab3PBus) dsNet.getBus("Bus5"),10,netTotalLoad,PVIncrement,loadPercentAry);
	    
	    
	    
	    // add those dynamic load model on the figure
	    
	    double totalLoadMVA =totalLoad/0.8;
		double loadMVA = totalLoadMVA/5.0;
		double acPercent = ACMotorPercent/100.0;
		double motorPercent = IndMotorPercent/100.0;
		double pvPercent = PVIncrement ;
		
		double acMVA = loadMVA *acPercent/3.0;
		double motorMVA = loadMVA*motorPercent;
		double pvGen = netTotalLoad/5.0*pvPercent/100.0;
		
	
	    
	    buildFeederDynModel(dsNet, 12, 16, ACMotorPercent, IndMotorPercent,
				ACPhaseUnbalance, motorMVA);
	    
	    //==============================Bus 6========================================
	 
	    dsNet.getBus("Bus6").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus6").setLoadCode(AclfLoadCode.NON_LOAD);
	    
	    double netTotalLoadBus6 = 90;
	    double totalLoadBus6 = netTotalLoadBus6*(1+PVIncrement);
	    
	    addFeeder2Bus(dsNet,(DStab3PBus) dsNet.getBus("Bus6"),20,netTotalLoadBus6,PVIncrement,loadPercentAry);
	    
      // add those dynamic load model on the figure
	    
	    double totalLoadMVABus6 =totalLoadBus6/0.8;
		double loadMVABus6 = totalLoadMVABus6/5.0;
		//double acPercentBus6 = ACMotorPercent/100.0;
		double motorPercentBus6 = IndMotorPercent/100.0;
		double pvPercentBus6 = PVIncrement ;
		
		//acMVA= loadMVABus6 *acPercentBus6/3.0;
		motorMVA= loadMVABus6*motorPercentBus6;
		pvGen = netTotalLoadBus6/5.0*pvPercentBus6/100.0;
		
		buildFeederDynModel(dsNet, 22, 26, ACMotorPercent, IndMotorPercent,
				ACPhaseUnbalance, motorMVA);
		
	    
	    
	  //==============================Bus 8========================================
		 
	    dsNet.getBus("Bus8").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus8").setLoadCode(AclfLoadCode.NON_LOAD);
	    double netTotalLoadBus8 = 100;
	    double totalLoadBus8 = netTotalLoadBus8*(1+PVIncrement);
	    
	    addFeeder2Bus(dsNet,(DStab3PBus) dsNet.getBus("Bus8"),30,netTotalLoadBus8,PVIncrement,loadPercentAry);
	    
      // add those dynamic load model on the figure
	    
	    double totalLoadMVABus8 =totalLoadBus8/0.8;
		double loadMVABus8 = totalLoadMVABus8/5.0;
		double acPercentBus8 = ACMotorPercent/100.0;
		double motorPercentBus8 = IndMotorPercent/100.0;
		double pvPercentBus8 = PVIncrement;
		
		acMVA= loadMVABus8 *acPercentBus8/3.0;
		motorMVA= loadMVABus8*motorPercentBus8;
		pvGen = netTotalLoadBus8/5.0*pvPercentBus8/100.0;
		
		buildFeederDynModel(dsNet, 32, 36, ACMotorPercent, IndMotorPercent,
				ACPhaseUnbalance, motorMVA);
		
	    

	    
	    
	    //======================================================================
	   // System.out.println("net ="+dsNet.net2String());
	    
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
	    
		 TDMultiNetPowerflowAlgorithm tdAlgo = new TDMultiNetPowerflowAlgorithm((BaseAclfNetwork<? extends AclfBus, ? extends AclfBranch>) dsNet,proc);
		 
	    
		 assertTrue(tdAlgo.powerflow()); 
		 
		 //System.out.println(tdAlgo.getTransmissionNetwork().net2String());
		 
		 System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
		 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
	
	
			
		  MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
		  
		  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
		  DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
		    
		  
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(1);
			

			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			
			//applied the event
			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",proc.getSubNetworkByBusId("Bus5"),SimpleFaultCode.GROUND_3P,new Complex(0.0),null,1.0d,0.07),"3phaseFault@Bus5");
	        
			
			StateMonitor sm = new StateMonitor();
			sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
			sm.addBusStdMonitor(new String[]{"Bus36","Bus32","Bus26","Bus24","Bus22","Bus16","Bus14","Bus12","Bus8","Bus6", "Bus5","Bus4","Bus1"});
			sm.add3PhaseBusStdMonitor(new String[]{"Bus36","Bus32","Bus26","Bus24","Bus22","Bus16","Bus14","Bus12"});
			//1Phase AC motor extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getParentBus().getId()+"_phase"+this.getPhase();
			
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus12_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus12_phaseB");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus12_phaseC");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus14_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus14_phaseB");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus14_phaseC");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus16_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus22_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus24_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus24_phaseB");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus24_phaseC");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus26_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus32_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus32_phaseB");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_3@Bus32_phaseC");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus34_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus36_phaseA");
			
			//3phase induction motor extended_device_Id = "IndMotor_"+this.getId()+"@"+this.getDStabBus().getId();
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus12");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus14");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus16");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus22");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus24");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus26");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus32");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus34");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus36");
			
			// PV gen
			// extended_device_Id = "PVGen3Phase_"+this.getId()+"@"+this.getParentGen().getParentBus().getId();
			
			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus12");
			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus16");
			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus22");
			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus26");
			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus32");
			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus36");
			
			
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(5);
			dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			
			IpssLogger.getLogger().setLevel(Level.WARNING);
			
			PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
			
	        // Must use this dynamic event process to modify the YMatrixABC
//			dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
			
			dstabAlgo.setSolver( new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
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
			System.out.println(sm.toCSVString(sm.getBusVoltTable()));
			System.out.println(sm.toCSVString(sm.getBusAngleTable()));
			//System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
			//System.out.println(sm.toCSVString(sm.getAcMotorStateTable()));
			
			MonitorRecord volt_rec1 = sm.getBusVoltTable().get("Bus36").get(0);
		  	MonitorRecord volt_rec51 = sm.getBusVoltTable().get("Bus36").get(50);
		  	assertTrue(Math.abs(volt_rec1.getValue()-volt_rec51.getValue())<1.0E-3);
		  	
		  	
			MonitorRecord angle_rec1 = sm.getBusAngleTable().get("Bus32").get(0);
		  	MonitorRecord angle_rec51 = sm.getBusAngleTable().get("Bus32").get(50);
		  	assertTrue(Math.abs(angle_rec1.getValue()-angle_rec51.getValue())<2.0E-1);
			

	}


	private void buildFeederDynModel(DStabNetwork3Phase dsNet, int startBusNum, int endBusNum,
			double ACMotorPercent, double IndMotorPercent,
			double ACPhaseUnbalance, double motorMVA) {
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
			
			
			// 3 phase motor, 20%
			
		  		InductionMotor indMotor= new InductionMotorImpl(loadBus,"1");
				//indMotor.setDStabBus(loadBus);

				indMotor.setXm(3.0);
				indMotor.setXl(0.07);
				indMotor.setRa(0.032);
				indMotor.setXr1(0.3);
				indMotor.setRr1(0.01);
				
		
				indMotor.setMvaBase(motorMVA);
				indMotor.setH(0.3);
				indMotor.setA(0.0); //Toreque = (a+bw+cw^2)*To;
				indMotor.setB(0.0); //Toreque = (a+bw+cw^2)*To;
				indMotor.setC(1.0); //Toreque = (a+bw+cw^2)*To;
				InductionMotor3PhaseAdapter indMotor3Phase = new InductionMotor3PhaseAdapter(indMotor);
				indMotor3Phase.setLoadPercent(IndMotorPercent); //0.06 MW
				loadBus.getThreePhaseDynLoadList().add(indMotor3Phase);	
			
			
			// PV generation
			
//				Gen3Phase gen1 = new Gen3PhaseImpl();
//				gen1.setParentBus(loadBus);
//				gen1.setId("PV1");
//				gen1.setGen(new Complex(pvGen,0));  // total gen power, system mva based
//				
//				loadBus.getThreePhaseGenList().add(gen1);
//				
//				double pvMVABase = pvGen/0.8*100;
//				gen1.setMvaBase(pvMVABase); // for dynamic simulation only
//				gen1.setPosGenZ(new Complex(0,1.0E-1));   // assuming open-circuit
//				gen1.setNegGenZ(new Complex(0,1.0E-1));
//				gen1.setZeroGenZ(new Complex(0,1.0E-1));
//				//create the PV Distributed generation model
//				PVDistGen3Phase pv = new PVDistGen3Phase(gen1);
//				pv.setId("1");
//				pv.setUnderVoltTripAll(0.4);
//				pv.setUnderVoltTripStart(0.8);
			
			
		}
	}
	
	
	//@Test
	public void test_IEEE9_cmpldw_dynamicSim() throws InterpssException{
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
	    
	    
	    double PVPenetration = 0.0;
	    double ACMotorPercent = 40;
	    double MotorPercent = 5;
	    
	    dsNet.getBus("Bus5").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus5").setLoadCode(AclfLoadCode.NON_LOAD);
	    
	    double totalLoadBus5 = 125;
	    
	    addCMPLDW2Bus(dsNet,(DStab3PBus) dsNet.getBus("Bus5"),10,totalLoadBus5,PVPenetration, ACMotorPercent,MotorPercent);
	    

	    
	    dsNet.getBus("Bus6").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus6").setLoadCode(AclfLoadCode.NON_LOAD);
        double totalLoadBus6 = 90;
	    
	    addCMPLDW2Bus(dsNet,(DStab3PBus) dsNet.getBus("Bus6"),20,totalLoadBus6,PVPenetration,ACMotorPercent,MotorPercent);
	   
	    
	    
	    dsNet.getBus("Bus8").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus8").setLoadCode(AclfLoadCode.NON_LOAD);
        double totalLoadBus8 = 100;
	    
	    addCMPLDW2Bus(dsNet,(DStab3PBus) dsNet.getBus("Bus8"),30,totalLoadBus8,PVPenetration,ACMotorPercent,MotorPercent);
	    
	    
	   // System.out.println("net ="+dsNet.net2String());
	    
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
	    
		 TDMultiNetPowerflowAlgorithm tdAlgo = new TDMultiNetPowerflowAlgorithm((BaseAclfNetwork<? extends AclfBus, ? extends AclfBranch>) dsNet,proc);
		 
	    
		 assertTrue(tdAlgo.powerflow()); 
		 
		 //System.out.println(tdAlgo.getTransmissionNetwork().net2String());
		 
		 System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
		 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
	
	
			
		  MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
		  
		  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
		  DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
		    
		  
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(30);
			

			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			
			//applied the event
			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",proc.getSubNetworkByBusId("Bus5"),SimpleFaultCode.GROUND_3P,new Complex(0.0),null,0.1d,0.07),"3phaseFault@Bus5");
	        
			
			StateMonitor sm = new StateMonitor();
			sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
			sm.addBusStdMonitor(new String[]{"Bus32","Bus22","Bus12","Bus8","Bus6", "Bus5","Bus4","Bus1"});
			
			//1Phase AC motor extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getParentBus().getId()+"_phase"+this.getPhase();
			
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus12_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus22_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus32_phaseA");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus12");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus22");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1@Bus32");
			// extended_device_Id = "PVGen3Phase_"+this.getId()+"@"+this.getParentGen().getParentBus().getId();
			
			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus12");
			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus22");
			sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus32");
		
		
			
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(10);
			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			
			IpssLogger.getLogger().setLevel(Level.WARNING);
			
			PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
			
	        // Must use this dynamic event process to modify the YMatrixABC
//			dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
			
			dstabAlgo.setSolver( new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
			dstabAlgo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(mNetHelper));
			
			if (dstabAlgo.initialization()) {
				//System.out.println(ThreePhaseAclfOutFunc.busLfSummary(dsNet));
				
				//System.out.println(dsNet.getMachineInitCondition());
				
				System.out.println("Running 3Phase/3sequence DStab co-simulation ...");
				timer.start();
				//dstabAlgo.performSimulation();
				
				while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
					
					dstabAlgo.solveDEqnStep(true);
				}
			}
//			System.out.println(sm.toCSVString(sm.getBusVoltTable()));
//			System.out.println(sm.toCSVString(sm.getBusAngleTable()));
//			System.out.println(sm.toCSVString(sm.getAcMotorStateTable()));
			
			FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_combined_dyn_sim//cmpld_busVoltage.csv",
					sm.toCSVString(sm.getBusVoltTable()));
			FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_combined_dyn_sim//cmpld_AcMotorState.csv",
					sm.toCSVString(sm.getAcMotorStateTable()));
			FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_combined_dyn_sim//cmpld_AcMotorP.csv",
					sm.toCSVString(sm.getAcMotorPTable()));
			FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_combined_dyn_sim//cmpld_AcMotorQ.csv",
					sm.toCSVString(sm.getAcMotorQTable()));
			FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_combined_dyn_sim//cmpld_IndMotorP.csv",
					sm.toCSVString(sm.getMotorPTable()));
			FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_combined_dyn_sim//cmpld_IndMotorSlip.csv",
					sm.toCSVString(sm.getMotorSlipTable()));
			FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_combined_dyn_sim//cmpld_IndMotorQ.csv",
					sm.toCSVString(sm.getMotorQTable()));
			FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_combined_dyn_sim//cmpld_pvGenP.csv",
					sm.toCSVString(sm.getPvGenPTable()));
			FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_combined_dyn_sim//cmpld_pvGenQ.csv",
					sm.toCSVString(sm.getPvGenQTable()));
			FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_combined_dyn_sim//cmpld_pvGenIt.csv",
					sm.toCSVString(sm.getPvGenItTable()));
			FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_combined_dyn_sim//cmpld_GenPe.csv",
					sm.toCSVString(sm.getMachPeTable()));
			FileUtil.writeText2File("E://Dropbox//PhD project//test data and results//TnD_combined_dyn_sim//cmpld_GenPm.csv",
					sm.toCSVString(sm.getMachPmTable()));
		
	
	}
	
	public void addFeeder2Bus(DStabNetwork3Phase net,DStab3PBus sourceBus, int startNum, double netTotalLoad, double pvIncrement,double[] loadPercentAry) throws InterpssException{
		
		   double totalMW = netTotalLoad*(1+pvIncrement);
		   double scaleFactor = totalMW/5;
		   //double scaleFactor = netTotalLoad/5;
		   double zScaleFactor = netTotalLoad/5;
		   
		   System.out.println("scale factor = "+scaleFactor);
		   
		   int busIdx = startNum;
			
		   DStab3PBus bus1 = ThreePhaseObjectFactory.create3PDStabBus("Bus"+(busIdx++), net);
			bus1.setAttributes("69 kV feeder source", "");
			bus1.setBaseVoltage(69000.0);
			// set the bus to a non-generator bus
			bus1.setGenCode(AclfGenCode.SWING);
			// set the bus to a constant power load bus
			bus1.setLoadCode(AclfLoadCode.NON_LOAD);
			bus1.setVoltage(new Complex(1.01,0));
			
//			DStabGen constantGen = DStabObjectFactory.createDStabGen();
//			constantGen.setId("Source");
//			constantGen.setMvaBase(100);
//			constantGen.setPosGenZ(new Complex(0.0,0.05));
//			constantGen.setNegGenZ(new Complex(0.0,0.05));
//			constantGen.setZeroGenZ(new Complex(0.0,0.05));
//			bus1.getContributeGenList().add(constantGen);
//			
//			
//			EConstMachine mach = (EConstMachine)DStabObjectFactory.
//					createMachine("MachId", "MachName", MachineType.ECONSTANT, net, "Bus1", "Source");
//		
//			mach.setRating(100, UnitType.mVA, net.getBaseKva());
//			mach.setRatedVoltage(69000.0);
//			mach.setH(50000.0);
//			mach.setXd1(0.05);
			
		
			// add step down transformer between source bus and bus1
			
			DStab3PBranch xfr1 = ThreePhaseObjectFactory.create3PBranch(sourceBus.getId(), bus1.getId(), "0", net);
			xfr1.setBranchCode(AclfBranchCode.XFORMER);
			xfr1.setToTurnRatio(1.02);
			xfr1.setZ( new Complex( 0.0, 0.05 ));
			//xfr1_2.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex( 0.0, 0.04 )));
			//xfr1_2.setZ0( new Complex(0.0, 0.4 ));
			
			AcscXformerAdapter xfr01 = acscXfrAptr.apply(xfr1);
			xfr01.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
			xfr01.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
		

			
			DStab3PBus bus2 = ThreePhaseObjectFactory.create3PDStabBus("Bus"+(busIdx++), net);
			bus2.setAttributes("feeder bus 2", "");
			bus2.setBaseVoltage(12500.0);
			// set the bus to a non-generator bus
			bus2.setGenCode(AclfGenCode.NON_GEN);
			// set the bus to a constant power load bus
			bus2.setLoadCode(AclfLoadCode.CONST_P);
			
			
		DStab3PBranch xfr1_2 = ThreePhaseObjectFactory.create3PBranch(bus1.getId(), bus2.getId(), "0", net);
			xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
			xfr1_2.setToTurnRatio(1.02);
			xfr1_2.setZ( new Complex( 0.0, 0.04 ));
			//xfr1_2.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex( 0.0, 0.04 )));
			//xfr1_2.setZ0( new Complex(0.0, 0.4 ));
		
		
		AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr1_2);
			xfr0.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
			xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);

			
			
			DStab3PBus bus3 = ThreePhaseObjectFactory.create3PDStabBus("Bus"+(busIdx++), net);
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
			
			
			DStab3PBus bus4 = ThreePhaseObjectFactory.create3PDStabBus("Bus"+(busIdx++), net);
			bus4.setAttributes("feeder bus 4", "");
			bus4.setBaseVoltage(12500.0);
			// set the bus to a non-generator bus
			bus4.setGenCode(AclfGenCode.GEN_PQ);
			// set the bus to a constant power load bus
			bus4.setLoadCode(AclfLoadCode.CONST_P);
			
			
			DStab3PBus bus5 = ThreePhaseObjectFactory.create3PDStabBus("Bus"+(busIdx++), net);
			bus5.setAttributes("feeder bus 5", "");
			bus5.setBaseVoltage(12500.0);
			// set the bus to a non-generator bus
			bus5.setGenCode(AclfGenCode.GEN_PQ);
			// set the bus to a constant power load bus
			bus5.setLoadCode(AclfLoadCode.CONST_P);
			
			
			DStab3PBus bus6 = ThreePhaseObjectFactory.create3PDStabBus("Bus"+(busIdx++), net);
			bus6.setAttributes("feeder bus 6", "");
			bus6.setBaseVoltage(12500.0);
			// set the bus to a non-generator bus
			bus6.setGenCode(AclfGenCode.GEN_PQ);
			// set the bus to a constant power load bus
			bus6.setLoadCode(AclfLoadCode.CONST_P);
			
			DStab3PBus bus7 = ThreePhaseObjectFactory.create3PDStabBus("Bus"+(busIdx++), net);
			bus7.setAttributes("feeder bus 7", "");
			bus7.setBaseVoltage(12500.0);
			// set the bus to a non-generator bus
			bus7.setGenCode(AclfGenCode.GEN_PQ);
			// set the bus to a constant power load bus
			bus7.setLoadCode(AclfLoadCode.CONST_P);
			
			int j = 0;
			for(int i =startNum+2;i<=6+startNum;i++){
				DStab3PBus loadBus = (DStab3PBus) net.getBus("Bus"+i);
				DStab3PLoad load1 = new DStab3PLoadImpl();
				load1.set3PhaseLoad(new Complex3x1(new Complex(0.01,0.002*(1-pvIncrement)),new Complex(0.010,0.002*(1-pvIncrement)),new Complex(0.01,0.002*(1-pvIncrement))).multiply(totalMW*loadPercentAry[j++]));
				if(loadBus == null) throw new Error("i = "+i);
				loadBus.getThreePhaseLoadList().add(load1);
				
			}
			
		
			
			
			for(int i =startNum+1;i<6+startNum;i++){
				DStab3PBranch Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus"+i, "Bus"+(i+1), "0", net);
				Line2_3.setBranchCode(AclfBranchCode.LINE);
				
				// unbalanced feeder
				//Complex3x3 zabcActual = this.getFeederZabc601().multiply(5.28/scaleFactor); // length is 1 mile per section, then consider the parallelism of rescaling 
				
				// 3phase balanced
				Complex3x3 zabcActual = this.getFeederEqualZabc601().multiply(5.28/zScaleFactor);
				
				Double zbase = net.getBus("Bus"+i).getBaseVoltage()*net.getBus("Bus"+i).getBaseVoltage()/net.getBaseMva()/1.0E6;
				Line2_3.setZabc(zabcActual.multiply(1/zbase));
				
			}
		}
	
	public void addCMPLDW2Bus(DStabNetwork3Phase net,DStab3PBus sourceBus, int startNum, double netTotalMW, double PVPenetrationLevel, double ACPercent, double indMotorPercent  ) throws InterpssException{
		   
		   double pvIncrement = PVPenetrationLevel/ (1-PVPenetrationLevel);
		   
		   //TODO
		   double totalMW = netTotalMW*(1+ pvIncrement);
		  // double totalMW = netTotalMW;
		   
		   
		   double scaleFactor = totalMW;
		   double ZScaleFactor = netTotalMW/5.0;
		  
		   
		   System.out.println("scale factor = "+scaleFactor);
		   
		   int busIdx = startNum;
			
		   DStab3PBus bus1 = ThreePhaseObjectFactory.create3PDStabBus("Bus"+(busIdx++), net);
			bus1.setAttributes("69 kV feeder source", "");
			bus1.setBaseVoltage(69000.0);
			// set the bus to a non-generator bus
			bus1.setGenCode(AclfGenCode.NON_GEN);
			// set the bus to a constant power load bus
			bus1.setLoadCode(AclfLoadCode.NON_LOAD);
			bus1.setVoltage(new Complex(1.01,0));
			
			
		
			// add step down transformer between source bus and bus1
			
			DStab3PBranch xfr1 = ThreePhaseObjectFactory.create3PBranch(sourceBus.getId(), bus1.getId(), "0", net);
			xfr1.setBranchCode(AclfBranchCode.XFORMER);
			xfr1.setToTurnRatio(1.02);
			xfr1.setZ( new Complex( 0.0, 0.05 ));
			//xfr1_2.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex( 0.0, 0.04 )));
			//xfr1_2.setZ0( new Complex(0.0, 0.4 ));
			
			AcscXformerAdapter xfr01 = acscXfrAptr.apply(xfr1);
			xfr01.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
			xfr01.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
		

			
			DStab3PBus bus2 = ThreePhaseObjectFactory.create3PDStabBus("Bus"+(busIdx++), net);
			bus2.setAttributes("feeder bus 2", "");
			bus2.setBaseVoltage(12500.0);
			// set the bus to a non-generator bus
			bus2.setGenCode(AclfGenCode.NON_GEN);
			// set the bus to a constant power load bus
			bus2.setLoadCode(AclfLoadCode.NON_LOAD);
			
			
		DStab3PBranch xfr1_2 = ThreePhaseObjectFactory.create3PBranch(bus1.getId(), bus2.getId(), "0", net);
			xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
			xfr1_2.setToTurnRatio(1.02);
			xfr1_2.setZ( new Complex( 0.0, 0.04 ));
			//xfr1_2.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex( 0.0, 0.04 )));
			//xfr1_2.setZ0( new Complex(0.0, 0.4 ));
		
		
		AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr1_2);
			xfr0.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
			xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);

			
			
			DStab3PBus bus3 = ThreePhaseObjectFactory.create3PDStabBus("Bus"+(busIdx++), net);
			bus3.setAttributes("feeder bus 3", "");
			bus3.setBaseVoltage(12500.0);
			// set the bus to a non-generator bus
			bus3.setGenCode(AclfGenCode.GEN_PQ);
			// set the bus to a constant power load bus
			bus3.setLoadCode(AclfLoadCode.CONST_P);
			
			
			
			
			for(int i =startNum+2;i<=2+startNum;i++){
				DStab3PBus loadBus = (DStab3PBus) net.getBus("Bus"+i);
				DStab3PLoad load1 = new DStab3PLoadImpl();
				load1.set3PhaseLoad(new Complex3x1(new Complex(0.01,0.002*(1-pvIncrement)),new Complex(0.010,0.002*(1-pvIncrement)),new Complex(0.01,0.002*(1-pvIncrement))).multiply(scaleFactor));
				if(loadBus == null) throw new Error("i = "+i);
				loadBus.getThreePhaseLoadList().add(load1);
				
			}
			
		
			
			for(int i =startNum+1;i<2+startNum;i++){
				DStab3PBranch Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus"+i, "Bus"+(i+1), "0", net);
				Line2_3.setBranchCode(AclfBranchCode.LINE);
				
				// unbalanced feeder
				//Complex3x3 zabcActual = this.getFeederZabc601().multiply(5.28/scaleFactor); // length is 1 mile per section, then consider the parallelism of rescaling 
				
				// 3phase balanced
				//
				Complex3x3 zabcActual = this.getFeederEqualZabc601().multiply(5.28/ZScaleFactor*2.5);
				
				Double zbase = net.getBus("Bus"+i).getBaseVoltage()*net.getBus("Bus"+i).getBaseVoltage()/net.getBaseMva()/1.0E6;
				Line2_3.setZabc(zabcActual.multiply(1/zbase));
				
			}
			
			
			 // add those dynamic load model on the figure
		    
		    double totalLoadMVA =totalMW/0.8;
			double loadMVA = totalLoadMVA;  // all connected to one 12.5 kV load bus, which mimics the composite load model
	
			double pvPercent = pvIncrement;
			
			double acMVA = loadMVA *ACPercent/100.0/3.0;
		
			double pvGen = netTotalMW*pvPercent/100.0;
			double motorMVA= loadMVA*indMotorPercent/100.0;
		    
			DStab3PBus loadBus = (DStab3PBus) net.getBus("Bus"+(2+startNum));
				
				/*
				Load3Phase load1 = new Load3PhaseImpl();
				load1.set3PhaseLoad(new Complex3x1(new Complex(0.3,0.05),new Complex(0.3,0.05),new Complex(0.3,0.05)));
				loadBus.getThreePhaseLoadList().add(load1);
				*/
					

				// AC motor, 50%
				
				 SinglePhaseACMotor ac1 = new SinglePhaseACMotor(loadBus,"1");
			  		ac1.setLoadPercent(ACPercent);
			  		ac1.setPhase(PhaseCode.A);
			  		ac1.setMvaBase(acMVA);
			  	
			  		ac1.setTstall(0.05); // disable ac stalling
			  		ac1.setVstall(0.65);
			  		loadBus.getPhaseADynLoadList().add(ac1);
			  		
			  		
			  		
			  SinglePhaseACMotor ac2 = new SinglePhaseACMotor(loadBus,"2");
			  		ac2.setLoadPercent(ACPercent);
			  		ac2.setPhase(PhaseCode.B);
			  		ac2.setMvaBase(acMVA);
			  	
			  		ac2.setTstall(0.05); // disable ac stalling
			  		ac2.setVstall(0.65);
			  		loadBus.getPhaseBDynLoadList().add(ac2);
			  		

			  		
			  	SinglePhaseACMotor ac3 = new SinglePhaseACMotor(loadBus,"3");
			  		ac3.setLoadPercent(ACPercent);
			  		ac3.setPhase(PhaseCode.C);
			  		ac3.setMvaBase(acMVA);
			  		ac3.setTstall(0.05); // disable ac stalling
			  		ac3.setVstall(0.65);
			  		loadBus.getPhaseCDynLoadList().add(ac3);
				
				
				// 3 phase motor, 20%
				
			  		InductionMotor indMotor= new InductionMotorImpl(loadBus, "1");
					//indMotor.setDStabBus(loadBus);

					indMotor.setXm(3.0);
					indMotor.setXl(0.07);
					indMotor.setRa(0.032);
					indMotor.setXr1(0.3);
					indMotor.setRr1(0.01);
					
					indMotor.setH(0.3);
					indMotor.setA(0.0); //Toreque = (a+bw+cw^2)*To;
					indMotor.setB(0.0); //Toreque = (a+bw+cw^2)*To;
					indMotor.setC(1.0); //Toreque = (a+bw+cw^2)*To;
					
					indMotor.setMvaBase(motorMVA);
					
					InductionMotor3PhaseAdapter indMotor3Phase = new InductionMotor3PhaseAdapter(indMotor);
					indMotor3Phase.setLoadPercent(indMotorPercent); //0.06 MW
					loadBus.getThreePhaseDynLoadList().add(indMotor3Phase);	
				
				
				// PV generation
				
//					Gen3Phase gen1 = new Gen3PhaseImpl();
//					gen1.setParentBus(loadBus);
//					gen1.setId("PV1");
//					gen1.setGen(new Complex(pvGen,0));  // total gen power, system mva based
//					
//					loadBus.getThreePhaseGenList().add(gen1);
//					
//					double pvMVABase = pvGen/0.8*100;
//					gen1.setMvaBase(pvMVABase); // for dynamic simulation only
//					gen1.setPosGenZ(new Complex(0,1.0E-1));   // assuming open-circuit
//					gen1.setNegGenZ(new Complex(0,1.0E-1));
//					gen1.setZeroGenZ(new Complex(0,1.0E-1));
//					//create the PV Distributed generation model
//					PVDistGen3Phase pv = new PVDistGen3Phase(gen1);
//					pv.setId("1");
//					pv.setUnderVoltTripAll(0.4);
//					pv.setUnderVoltTripStart(0.8);
				
			
		}
	
		
		//TODO  1 Mile = 5280 feets
		//ohms per 1000ft
		private Complex3x3 getFeederZabc601(){
			  Complex3x3 zabc= new Complex3x3();
			  zabc.aa = new Complex(0.0882,0.2074);
			  zabc.ab = new Complex(0.0312,0.0935);
			  zabc.ac = new Complex(0.0306,0.0760);
			  zabc.ba =  zabc.ab;
			  zabc.bb =  new Complex(0.0902, 0.2008);
			  zabc.bc =  new Complex(0.0316,0.0856);
			  zabc.ca =  zabc.ac;
			  zabc.cb =  zabc.bc;
			  zabc.cc =  new Complex(0.0890,0.2049);
			  
			  return zabc;
			  
		}
		
		//TODO  1 Mile = 5280 feets
		//ohms per 1000ft
		private Complex3x3 getFeederEqualZabc601(){
			  Complex3x3 zabc= new Complex3x3();
			  zabc.aa = new Complex(0.0882,0.2074);
			  zabc.ab = new Complex(0.0312,0.0935);
			  zabc.ac =  zabc.ab;
			  zabc.ba =  zabc.ab;
			  zabc.bb =  zabc.aa;
			  zabc.bc =  zabc.ab;
			  zabc.ca =  zabc.ab;
			  zabc.cb =  zabc.ab;
			  zabc.cc =  zabc.aa;
			  
			  return zabc;
			  
		}

}
