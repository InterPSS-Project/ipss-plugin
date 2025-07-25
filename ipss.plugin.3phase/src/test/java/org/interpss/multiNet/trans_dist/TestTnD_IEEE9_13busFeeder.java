package org.interpss.multiNet.trans_dist;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
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
import org.interpss.threePhase.dynamic.algo.DynamicEventProcessor3Phase;
import org.interpss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.core.acsc.adpter.AcscXformerAdapter;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class TestTnD_IEEE9_13busFeeder {
	
	@Test
	public void test_IEEE9_TD_dynamicSim_multiFeeder() throws InterpssException{
		IpssCorePlugin.init();
		
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
				//"testData/IEEE9Bus/ieee9_dyn_fullModel_v33.dyr"
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
	 
//		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
//		aclfAlgo.setLfMethod(AclfMethod.PQ);
//		aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
//		aclfAlgo.setTolerance(1.0E-6);
//		assertTrue(aclfAlgo.loadflow());
//		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
	    
	
	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
	    
	    List<String> replaceBusIdList = new ArrayList<>(); 
		List<String> interfaceBusIdList = new ArrayList<>(); 
	        // add distribution systems
		int replacedBusNum = 0;
		int totalFeederNum = 0;
		
		// NOTE: it is not allowed to iterate the buslist and add a new bus to it;
		// so need to save the Ids of targeted buses to a list first;
		
		//LIST: 1,
		
		for(DStab3PBus b:dsNet.getBusList()){
	        if(b.getArea().getNumber()==1){
	        	if(b.isActive() && b.isLoad() && (!b.isGen()) && b.getLoadP()>1.0 && b.getLoadP()<6 ){//&& 
	        		
	        		replaceBusIdList.add(b.getId());
		              
	        	}
	         }
		   }

		for (String id: replaceBusIdList){
			
			String[] interfaceIds = replaceLoadByFeeder(dsNet,id);
            
            proc.addSubNetInterfaceBranch(interfaceIds[0],false);
            
            interfaceBusIdList.add(interfaceIds[1]);
            
            replacedBusNum +=1;
            
            totalFeederNum+=Integer.valueOf(interfaceIds[2]);
		}
		    
		    
		System.out.println("replaced load bus num, total feeder num: "+replacedBusNum+","+totalFeederNum);
				
				 
        proc.splitFullSystemIntoSubsystems(true);
        
		 for(BaseDStabNetwork subnet: proc.getSubNetworkList()) {
			 subnet.setStaticLoadIncludedInYMatrix(true);
		 }
        
       // proc.set3PhaseSubNetByBusId("Bus3");
				 
				 // currently, if a fault at transmission system is to be considered, then it should be set to 3phase
				// proc.set3PhaseSubNetByBusId("Bus1");
				//TODO this has to be manually identified
		    for(String busId: interfaceBusIdList){
		        proc.set3PhaseSubNetByBusId(busId);
		    }
		    
		    System.out.println("distribution sys num:"+ (proc.getSubNetworkList().size()-1));
		    // dist pf not converged 
		    // subnet- Bus562_LVBus
		    // subnet- 16
		    // System.out.println("dist Net -34  :"+proc.getSubNetwork("SubNet-34").getBusList().get(0));
		    
			
		    TDMultiNetPowerflowAlgorithm tdAlgo = new TDMultiNetPowerflowAlgorithm((BaseAclfNetwork<? extends BaseAclfBus<?,?>, ? extends AclfBranch>) dsNet,proc);
		    
		   // System.out.println(tdAlgo.getDistributionNetworkList().get(0).net2String());
				 
				 //System.out.println(tdAlgo.getTransmissionNetwork().net2String());
			 LoadflowAlgorithm tAlgo = tdAlgo.getTransLfAlgorithm();
			 tAlgo.setLfMethod(AclfMethodType.NR);
			 tAlgo.setTolerance(1.0E-5);
			 tAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
			 //tAlgo.setNonDivergent(true);
			 tAlgo.setInitBusVoltage(true); 
			 // TODO initBusVoltage can be updated to set init to true for the first iteration, while
			 // the remaining iterations can reuse last step solution results as the starting point, such that
			 // simulation time for the transmission part can be reduced.
			 
			 assertTrue(tdAlgo.powerflow()); 
		   
		  // System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
		  // System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
			 
		  MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
		  
		  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
		  DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
		    
		  
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(0.3);
			

			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			
			//applied the event
			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus4",proc.getSubNetworkByBusId("Bus4"),SimpleFaultCode.GROUND_3P,new Complex(0.0),null,1.0d,0.07),"3phaseFault@Bus5");
	        
			
			StateMonitor sm = new StateMonitor();
			sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
			sm.addBusStdMonitor(new String[]{"Bus3","Bus2","Bus7","Bus6", "Bus5","Bus4","Bus1"});
			
			// sm.add3PhaseBusStdMonitor(new String[]{"Bus288","Bus284","Bus282","Bus278","Bus274","Bus272","Bus268","Bus264","Bus262","Bus188","Bus184","Bus182","Bus180","Bus168","Bus164","Bus162","Bus160","Bus158","Bus157","Bus156","Bus154","Bus153","Bus152","Bus151","Bus150"});
			 String idPrefix = "Bus5_feeder_1_";
			   DStabNetwork3Phase distNet_1 = (DStabNetwork3Phase) proc.getSubNetworkByBusId(idPrefix+"BusRG60");
			   
			  
			   
			 String[] feederIdAry = new String[13];
			 
			  int k = 0;
			   for(DStab3PBus dsBus:distNet_1.getBusList()){
				   if(dsBus.getId().startsWith(idPrefix)){
					   if(k<13){
						   feederIdAry[k] = dsBus.getId();
						   k++;
					   }
				   }
			   }
			   sm.add3PhaseBusStdMonitor(feederIdAry);
			//1Phase AC motor extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getParentBus().getId()+"_phase"+this.getPhase();
			
					
			
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutputPerSteps(1);
			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			
			IpssLogger.getLogger().setLevel(Level.WARNING);
			
			PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
			
	        // Must use this dynamic event process to modify the YMatrixABC
			dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
			
			dstabAlgo.setSolver( new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
			dstabAlgo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(mNetHelper));
			
			if (dstabAlgo.initialization()) {
				//System.out.println(ThreePhaseAclfOutFunc.busLfSummary(dsNet));
				
				//System.out.println(dsNet.getMachineInitCondition());
				
				//System.out.println("Running 3Phase/3sequence DStab co-simulation ...");
				timer.start();
				//dstabAlgo.performSimulation();
				
				while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
					
					for(String busId: sm.getBusPhAVoltTable().keySet()){
						
						 sm.addBusPhaseVoltageMonitorRecord( busId,dstabAlgo.getSimuTime(), ((DStab3PBus)proc.getSubNetworkByBusId(busId).getBus(busId)).get3PhaseVotlages());
					}
					
					dstabAlgo.solveDEqnStep(true);
				
					
				}
			}
			System.out.println(sm.toCSVString(sm.getBusVoltTable()));
			System.out.println(sm.toCSVString(sm.getBusAngleTable()));
			System.out.println(sm.toCSVString(sm.getMachAngleTable()));
			System.out.println(sm.toCSVString(sm.getMachSpeedTable()));
			System.out.println(sm.toCSVString(sm.getBusPhAVoltTable()));
			System.out.println(sm.toCSVString(sm.getBusPhBVoltTable()));
			System.out.println(sm.toCSVString(sm.getBusPhCVoltTable()));
			
			
			MonitorRecord volt_rec1 = sm.getBusVoltTable().get("Bus3").get(0);
		  	MonitorRecord volt_rec51 = sm.getBusVoltTable().get("Bus3").get(50);
		  	assertTrue(Math.abs(volt_rec1.getValue()-volt_rec51.getValue())<1.0E-3);
		  	
		  	
			MonitorRecord angle_rec1 = sm.getBusAngleTable().get("Bus2").get(0);
		  	MonitorRecord angle_rec51 = sm.getBusAngleTable().get("Bus2").get(50);
		  	assertTrue(Math.abs(angle_rec1.getValue()-angle_rec51.getValue())<1.0E-1);
		  	
		  	
			MonitorRecord voltPhA_rec1 = sm.getBusPhAVoltTable().get("Bus5_feeder_1_BusRG60").get(0);
		  	MonitorRecord voltPhA_rec51 = sm.getBusPhAVoltTable().get("Bus5_feeder_1_BusRG60").get(50);
		  	assertTrue(Math.abs(voltPhA_rec1.getValue()-voltPhA_rec51.getValue())<1.0E-3);
		  	
		  	
			
//			FileUtil.writeText2File("output/IEEE9_T_busVoltage.csv",sm.toCSVString(sm.getBusVoltTable()));
//			FileUtil.writeText2File("output/IEEE9_TD_GenPe.csv",sm.toCSVString(sm.getMachPeTable()));
//	        FileUtil.writeText2File("output/IEEE9_TD_GenPm.csv",sm.toCSVString(sm.getMachPmTable()));
//	        FileUtil.writeText2File("output/IEEE9_TD_GenSpd.csv",sm.toCSVString(sm.getMachSpeedTable()));
//	        FileUtil.writeText2File("output/IEEE9_D_busPhAVoltage.csv",sm.toCSVString(sm.getBusPhAVoltTable()));
//	        FileUtil.writeText2File("output/IEEE9_D_busPhBVoltage.csv",sm.toCSVString(sm.getBusPhBVoltTable()));
//	        FileUtil.writeText2File("output/IEEE9_D_busPhCVoltage.csv",sm.toCSVString(sm.getBusPhCVoltTable()));

	
	}
	
	
	
	/**
	 * The loads connected to a transmission bus are replaced by IEEE13 feeders.
	 * @param net
	 * @param transBusId
	 * @throws InterpssException
	 */
	private String[] replaceLoadByFeeder(DStabNetwork3Phase net,String transBusId) throws InterpssException{
			
			double baseVolt4160 = 4160.0;
			
			DStab3PBus transBus = (DStab3PBus) net.getBus(transBusId);
			
			Complex loadPQ = new Complex(transBus.getLoadP(), transBus.getLoadQ());
			
			String[] interfaceIds = new String[3];
			
			double tapRatio = 1/transBus.getVoltageMag();
			
			if(tapRatio <0.95) tapRatio = 0.95;
			else if(tapRatio >1.1) tapRatio =1.1;
			
			double transMVA = loadPQ.getReal()*100.0/0.8;
			
			double xfrZ = 0.05*100.0/transMVA;
			
			if(transBus.getId().equals("Bus1")){
				System.out.println("processing bus1");
			}
			
			if(transBus.isActive() && transBus.isLoad() ){
			
				DStab3PBus sourceBus = ThreePhaseObjectFactory.create3PDStabBus(transBusId+"_LVBus", net);
				sourceBus.setAttributes("trans LV bus", "");
				sourceBus.setBaseVoltage(baseVolt4160);
		
				sourceBus.setLoadCode(AclfLoadCode.NON_LOAD);
				sourceBus.setVoltage(transBus.getVoltage());
				
				DStab3PBranch xfr1_2 = ThreePhaseObjectFactory.create3PBranch(transBusId, transBusId+"_LVBus", "0", net);
				xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
				xfr1_2.setToTurnRatio(tapRatio);
				xfr1_2.setZ( new Complex( 0.0, xfrZ ));
				
			
			    AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr1_2);
				xfr0.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
				xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
				
				interfaceIds[0]  =xfr1_2.getId();
				interfaceIds[1]  =sourceBus.getId();
				
				
				//three-phase total power on three-phase MVA base: (3.5797255313759795, 1.745961739090089)
				Complex feederLoadPQ = new Complex (0.0358,0.0125);
				
				int numberOfFeeder = (int) (loadPQ.getReal()/feederLoadPQ.getReal()); // this should be calcuated based on loadPQ
				
				for (int i = 1; i<numberOfFeeder+1;i++){
					createFeeder(net, sourceBus, transBusId, i, 100.0 );
				}
				
				interfaceIds[2] =  Integer.toString(numberOfFeeder);
				
				
				//TODO process the negative and zero sequence load data;
			    
				
				//transBus.setLoadCode(AclfLoadCode.CONST_P);
				transBus.getContributeLoadList().clear();
				
				// estimated PQ compensation
				Complex distTotalLoadPQ = feederLoadPQ.multiply(numberOfFeeder);
				
				double dP = loadPQ.getReal()-distTotalLoadPQ.getReal();
				// total Var at the transmission bus is equal to total feeder Q plus step-down transformer Var consumption
				double dQ = loadPQ.getImaginary()-distTotalLoadPQ.getImaginary()-(distTotalLoadPQ.abs()*xfr1_2.getAdjustedZ().getImaginary());
				
				transBus.setLoadP(dP);
				transBus.setLoadQ(dQ);
				//transBus.setLoadPQ(new Complex(0,0));
				
			}
			
			return interfaceIds ;
			
		}
		
		
		
		private void createFeeder(DStabNetwork3Phase net, DStab3PBus sourceBus, String transBusId, int feederIdx, double mvaBase ) throws InterpssException{
			
			   double ft2mile = 1.0/5280.0;
			  
			   double baseVolt4160 = 4160.0; //4.16 kV
			   double baseVolt480 = 480.0;
			   double vabase = mvaBase*1.0E6; // 1MW
			   
			   
			   double zBase4160 = baseVolt4160*baseVolt4160/vabase;
			   double zBase480  = baseVolt480*baseVolt480/vabase;
			   
			   double loadScaleFactor =3/mvaBase;
			   
			   String idPrefix = transBusId+"_feeder_"+feederIdx+"_";
			   
			   //DStabNetwork3Phase net = ThreePhaseObjectFactory.create3PhaseDStabNetwork();
				
				
				// identify this is a distribution network
				//net.setNetworkType(NetworkType.DISTRIBUTION);
				
				

			   DStab3PBus bus650 = (DStab3PBus) sourceBus;

			
				// voltage regulator bus RG60
				DStab3PBus busRG60 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"BusRG60", net);
				busRG60.setAttributes("feeder RG60", "");
				busRG60.setBaseVoltage(baseVolt4160);
				// set the bus to a non-generator bus
				busRG60.setGenCode(AclfGenCode.NON_GEN);
				// set the bus to a constant power load bus
				busRG60.setLoadCode(AclfLoadCode.NON_LOAD);
				
				
				DStab3PBus bus632 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus632", net);
				bus632.setAttributes("feeder 632", "");
				bus632.setBaseVoltage(baseVolt4160);
				// set the bus to a non-generator bus
		
				// set the bus to a constant power load bus
				bus632.setLoadCode(AclfLoadCode.NON_LOAD);
				
				
				DStab3PBus bus633 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus633", net);
				bus633.setAttributes("feeder 633", "");
				bus633.setBaseVoltage(baseVolt4160);

				// set the bus to a constant power load bus
				bus633.setLoadCode(AclfLoadCode.NON_LOAD);
				
				
				DStab3PBus bus634 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus634", net);
				bus634.setAttributes("feeder 634", "");
				bus634.setBaseVoltage(baseVolt480);
				// set the bus to a constant power load bus
				bus634.setLoadCode(AclfLoadCode.CONST_P);
				/*
				 * New Load.634a Bus1=634.1     Phases=1 Conn=Wye  Model=1 kV=0.277  kW=160   kvar=110 
	             New Load.634b Bus1=634.2     Phases=1 Conn=Wye  Model=1 kV=0.277  kW=120   kvar=90 
	             New Load.634c Bus1=634.3     Phases=1 Conn=Wye  Model=1 kV=0.277  kW=120   kvar=90 
				 */
				DStab3PLoad load634 = new DStab3PLoadImpl();
				load634.set3PhaseLoad( new Complex3x1(new Complex(0.160,0.11),new Complex(0.120,0.09),new Complex(0.120,0.090)).multiply(loadScaleFactor));
				bus634.getThreePhaseLoadList().add(load634);
				
				
				
				
				DStab3PBus bus645 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus645", net);
				bus645.setAttributes("feeder 645", "");
				bus645.setBaseVoltage(baseVolt4160);
				// set the bus to a constant power load bus
				bus645.setLoadCode(AclfLoadCode.CONST_P);
				//New Load.645 Bus1=645.2       Phases=1 Conn=Wye  Model=1 kV=2.4      kW=170   kvar=125 
				DStab3PLoad load645 = new DStab3PLoadImpl();
				load645.set3PhaseLoad( new Complex3x1(new Complex(0.0),new Complex(0.170,0.125),new Complex(0)).multiply(loadScaleFactor));
				bus645.getThreePhaseLoadList().add(load645);
				
				
				
				
				
				DStab3PBus bus646 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus646", net);
				bus646.setAttributes("feeder 646", "");
				bus646.setBaseVoltage(baseVolt4160);
				// set the bus to a constant power load bus
			
				bus646.setLoadCode(AclfLoadCode.CONST_P);
				//New Load.646 Bus1=646.2.3    Phases=1 Conn=Delta Model=2 kV=4.16    kW=230   kvar=132 
				DStab3PLoad load646 = new DStab3PLoadImpl();
				load646.set3PhaseLoad( new Complex3x1(new Complex(0.0),new Complex(0.230/2,0.132/2),new Complex(0.230/2,0.132/2)).multiply(loadScaleFactor));
				bus646.getThreePhaseLoadList().add(load646);
				
				
				
				
				DStab3PBus bus671 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus671", net);
				bus671.setAttributes("feeder 671", "");
				bus671.setBaseVoltage(baseVolt4160);
				// set the bus to a constant power load bus
				bus671.setLoadCode(AclfLoadCode.CONST_P);
				// New Load.671 Bus1=671.1.2.3  Phases=3 Conn=Delta Model=1 kV=4.16   kW=1155 kvar=660
				DStab3PLoad load671 = new DStab3PLoadImpl();
				load671.set3PhaseLoad(new Complex3x1(new Complex(1.155/3,0.660/3),new Complex(1.155/3,0.660/3),new Complex(1.155/3,0.660/3)).multiply(loadScaleFactor));
				bus671.getThreePhaseLoadList().add(load671);
				
				
				
				
				DStab3PBus bus684 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus684", net);
				bus684.setAttributes("feeder 684", "");
				bus684.setBaseVoltage(baseVolt4160);
				// set the bus to a constant power load bus
				bus684.setLoadCode(AclfLoadCode.NON_LOAD);
				
				
				DStab3PBus bus611 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus611", net);
				bus611.setAttributes("feeder 611", "");
				bus611.setBaseVoltage(baseVolt4160);
				// set the bus to a constant power load bus
				bus611.setLoadCode(AclfLoadCode.CONST_P);
				//New Load.611 Bus1=611.3      Phases=1 Conn=Wye  Model=5 kV=2.4  kW=170   kvar=80 
				DStab3PLoad load611 = new DStab3PLoadImpl();
				load611.set3PhaseLoad(new Complex3x1(new Complex(0),new Complex(0),new Complex(0.170,0.080)).multiply(loadScaleFactor));
				bus611.getThreePhaseLoadList().add(load611);
				
				
				
				
				DStab3PBus bus652 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus652", net);
				bus652.setAttributes("feeder 652", "");
				bus652.setBaseVoltage(baseVolt4160);
				// set the bus to a constant power load bus
				bus652.setLoadCode(AclfLoadCode.CONST_P);
				//New Load.652 Bus1=652.1      Phases=1 Conn=Wye  Model=2 kV=2.4  kW=128   kvar=86
				DStab3PLoad load652 = new DStab3PLoadImpl();
				load652.set3PhaseLoad(new Complex3x1(new Complex(0.128,0.086),new Complex(0),new Complex(0.,0.)).multiply(loadScaleFactor));
				bus652.getThreePhaseLoadList().add(load652);
				
				
				DStab3PBus bus680 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus680", net);
				bus680.setAttributes("feeder 680", "");
				bus680.setBaseVoltage(baseVolt4160);
				// set the bus to a constant power load bus
				bus680.setLoadCode(AclfLoadCode.NON_LOAD);
				
				
				DStab3PBus bus692 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus692", net);
				bus692.setAttributes("feeder 692", "");
				bus692.setBaseVoltage(baseVolt4160);
				// set the bus to a constant power load bus
				bus692.setLoadCode(AclfLoadCode.CONST_P);
				// New Load.692 Bus1=692.3.1    Phases=1 Conn=Delta Model=5 kV=4.16    kW=170   kvar=151 
				DStab3PLoad load692 = new DStab3PLoadImpl();
				load692.set3PhaseLoad(new Complex3x1(new Complex(0.170/2,0.151/2),new Complex(0),new Complex(0.170/2,0.151/2)).multiply(loadScaleFactor));
				bus692.getThreePhaseLoadList().add(load692);
				
				

				DStab3PBus bus675 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus675", net);
				bus675.setAttributes("feeder 675", "");
				bus675.setBaseVoltage(baseVolt4160);
				// set the bus to a constant power load bus
				bus675.setLoadCode(AclfLoadCode.CONST_P);
				/*
				 * New Load.675a Bus1=675.1    Phases=1 Conn=Wye  Model=1 kV=2.4  kW=485   kvar=190 
	             New Load.675b Bus1=675.2    Phases=1 Conn=Wye  Model=1 kV=2.4  kW=68   kvar=60 
	             New Load.675c Bus1=675.3    Phases=1 Conn=Wye  Model=1 kV=2.4  kW=290   kvar=212 
				 */
				
				DStab3PLoad load675 = new DStab3PLoadImpl();
				load675.set3PhaseLoad(new Complex3x1(new Complex(0.485,0.190),new Complex(0.068,0.06),new Complex(0.290,0.212)).multiply(loadScaleFactor));
				bus675.getThreePhaseLoadList().add(load675);
				
				
				// !Bus 670 is the concentrated point load of the distributed load on line 632 to 671 located at 1/3 the distance from node 632
				DStab3PBus bus670 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus670", net);
				bus670.setAttributes("feeder 670", "");
				bus670.setBaseVoltage(baseVolt4160);
				// set the bus to a constant power load bus
				bus670.setLoadCode(AclfLoadCode.CONST_P);
				/*
				 * New Load.670a Bus1=670.1    Phases=1 Conn=Wye  Model=1 kV=2.4  kW=17    kvar=10 
	             New Load.670b Bus1=670.2    Phases=1 Conn=Wye  Model=1 kV=2.4  kW=66    kvar=38 
	             New Load.670c Bus1=670.3    Phases=1 Conn=Wye  Model=1 kV=2.4  kW=117  kvar=68 
				 */
				DStab3PLoad load670 = new DStab3PLoadImpl();
				load670.set3PhaseLoad(new Complex3x1(new Complex(0.017,0.01),new Complex(0.066,0.038),new Complex(0.117,0.068)).multiply(loadScaleFactor));
				bus670.getThreePhaseLoadList().add(load670);
				
				///////////////////////////////capacitors /////////////////////
				/* !CAPACITOR DEFINITIONS
				   New Capacitor.Cap1 Bus1=675 phases=3 kVAR=600 kV=4.16 
				   New Capacitor.Cap2 Bus1=611.3 phases=1 kVAR=100 kV=2.4 
				*/
				DStab3PLoad shunty675 = new DStab3PLoadImpl();
				shunty675.set3PhaseLoad(new Complex3x1(new Complex(0,-0.2),new Complex(0.0,-0.2),new Complex(0.0,-0.2)).multiply(loadScaleFactor));
				bus675.getThreePhaseLoadList().add(shunty675);
				
				
				DStab3PLoad shunty611 = new DStab3PLoadImpl();
				shunty611.set3PhaseLoad(new Complex3x1(new Complex(0.0, 0.0),new Complex(0.0,0.0),new Complex(0.0,-0.1)).multiply(loadScaleFactor));
				bus611.getThreePhaseLoadList().add(shunty611);
				
				//ADDITIONAL SHUNT
				DStab3PLoad shunty500kVar = new DStab3PLoadImpl();
				shunty500kVar.set3PhaseLoad(new Complex3x1(new Complex(0,-0.2),new Complex(0.0,-0.2),new Complex(0.0,-0.2)).multiply(loadScaleFactor));
				bus632.getThreePhaseLoadList().add(shunty500kVar);
				
				
				////////////////////////////////// transformers ////////////////////////////////////////////////////////
				
//				Branch3Phase xfr1_2 = ThreePhaseObjectFactory.create3PBranch("SubBus", "Bus650", "0", net);
//				xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
//				xfr1_2.setToTurnRatio(1.0);
//				xfr1_2.setZ( new Complex( 0.0, 0.0001 ));
//				
//			
//			    AcscXformer xfr0 = acscXfrAptr.apply(xfr1_2);
//				xfr0.setFromConnectGroundZ(XfrConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
//				xfr0.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);

				
				DStab3PBranch xfr2_3 = ThreePhaseObjectFactory.create3PBranch( bus650.getId(), idPrefix+"BusRG60","0", net);
				xfr2_3.setBranchCode(AclfBranchCode.XFORMER);
				xfr2_3.setToTurnRatio(1.055);
				xfr2_3.setZ( new Complex( 0.0, 0.00001 ));
				
			
			    AcscXformerAdapter xfr2 = acscXfrAptr.apply(xfr2_3);
				xfr2.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
				xfr2.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
				
				
				DStab3PBranch xfr633_634 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus633", idPrefix+"Bus634", "0", net);
				xfr633_634.setBranchCode(AclfBranchCode.XFORMER);
				xfr633_634.setToTurnRatio(1.0);
				xfr633_634.setZ( new Complex( 0.0, 0.02 ));
				
			
			    AcscXformerAdapter xfr3 = acscXfrAptr.apply(xfr633_634);
				xfr3.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
				xfr3.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
				
				
				///////////////////////////////////////////////////////// LINES ////////////////////////////////////////
				
				//!LINE DEFINITIONS 
				//New Line.650632    Phases=3 Bus1=RG60.1.2.3   Bus2=632.1.2.3  LineCode=mtx601 Length=2000 units=ft 
				DStab3PBranch Line650_632 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"BusRG60", idPrefix+"Bus632", "0", net);
				Line650_632.setBranchCode(AclfBranchCode.LINE);
				
				double length =2000.0*ft2mile; // convert to miles
				Complex3x3 zabc_pu = IEEEFeederLineCode.zMtx601.multiply(length/zBase4160);
				Line650_632.setZabc(zabc_pu);
				
				//New Line.632670    Phases=3 Bus1=632.1.2.3    Bus2=670.1.2.3  LineCode=mtx601 Length=667  units=ft
				
				DStab3PBranch Line632_670 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus632", idPrefix+"Bus670", "0", net);
				Line632_670.setBranchCode(AclfBranchCode.LINE);
				length =667.0*ft2mile; // convert to miles
				zabc_pu = IEEEFeederLineCode.zMtx601.multiply(length/zBase4160);
				Line632_670.setZabc(zabc_pu);
				
				
				//New Line.670671    Phases=3 Bus1=670.1.2.3    Bus2=671.1.2.3  LineCode=mtx601 Length=1333 units=ft
				DStab3PBranch Line670_671 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus670", idPrefix+"Bus671", "0", net);
				Line670_671.setBranchCode(AclfBranchCode.LINE);
				length =1333.0*ft2mile; // convert to miles
				zabc_pu = IEEEFeederLineCode.zMtx601.multiply(length/zBase4160);
				Line670_671.setZabc(zabc_pu);
				
				
				//New Line.671680    Phases=3 Bus1=671.1.2.3    Bus2=680.1.2.3  LineCode=mtx601 Length=1000 units=ft 
				DStab3PBranch Line671_680 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus671", idPrefix+"Bus680", "0", net);
				Line671_680.setBranchCode(AclfBranchCode.LINE);
				length =1000.0*ft2mile; // convert to miles
				zabc_pu = IEEEFeederLineCode.zMtx601.multiply(length/zBase4160);
				Line671_680.setZabc(zabc_pu);
				
				
				//New Line.632633    Phases=3 Bus1=632.1.2.3    Bus2=633.1.2.3  LineCode=mtx602 Length=500  units=ft
				DStab3PBranch Line632_633 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus632", idPrefix+"Bus633", "0", net);
				Line632_633.setBranchCode(AclfBranchCode.LINE);
				length =500.0*ft2mile; // convert to miles
				zabc_pu = IEEEFeederLineCode.zMtx602.multiply(length/zBase4160);
				Line632_633.setZabc(zabc_pu);			
				
				
				
				//New Line.632645    Phases=2 Bus1=632.3.2      Bus2=645.3.2    LineCode=mtx603 Length=500  units=ft 
				
				DStab3PBranch Line632_645 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus632", idPrefix+"Bus645", "0", net);
				Line632_645.setBranchCode(AclfBranchCode.LINE);
				length =500.0*ft2mile; // convert to miles
				zabc_pu = IEEEFeederLineCode.zMtx603.multiply(length/zBase4160);
				Line632_645.setZabc(zabc_pu);
				
				
				
				
				//New Line.645646    Phases=2 Bus1=645.3.2      Bus2=646.3.2    LineCode=mtx603 Length=300  units=ft 
				
				DStab3PBranch Line645_646 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus645", idPrefix+"Bus646", "0", net);
				Line645_646.setBranchCode(AclfBranchCode.LINE);
				length = 300.0*ft2mile; // convert to miles
				zabc_pu = IEEEFeederLineCode.zMtx603.multiply(length/zBase4160);
				Line645_646.setZabc(zabc_pu);
				
				
				
				//New Line.692675    Phases=3 Bus1=692.1.2.3    Bus2=675.1.2.3  LineCode=mtx606 Length=500  units=ft
				
				DStab3PBranch Line692_675 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus692", idPrefix+"Bus675", "0", net);
				Line692_675.setBranchCode(AclfBranchCode.LINE);
				length = 500.0*ft2mile; // convert to miles
				zabc_pu = IEEEFeederLineCode.zMtx606.multiply(length/zBase4160);
				Line692_675.setZabc(zabc_pu);
				
				
				// New Line.671684    Phases=2 Bus1=671.1.3      Bus2=684.1.3    LineCode=mtx604 Length=300  units=ft
				
				DStab3PBranch Line671_684 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus671", idPrefix+"Bus684", "0", net);
				Line671_684.setBranchCode(AclfBranchCode.LINE);
				length = 300.0*ft2mile; // convert to miles
				zabc_pu = IEEEFeederLineCode.zMtx604.multiply(length/zBase4160);
				Line671_684.setZabc(zabc_pu);
				
				
				
				// New Line.684611    Phases=1 Bus1=684.3        Bus2=611.3      LineCode=mtx605 Length=300  units=ft 
				
				DStab3PBranch Line684_611 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus684", idPrefix+"Bus611", "0", net);
				Line684_611.setBranchCode(AclfBranchCode.LINE);
				length = 300.0*ft2mile; // convert to miles
				zabc_pu = IEEEFeederLineCode.zMtx605.multiply(length/zBase4160);
				Line684_611.setZabc(zabc_pu);
				
				// New Line.684652    Phases=1 Bus1=684.1        Bus2=652.1      LineCode=mtx607 Length=800  units=ft 

				DStab3PBranch Line684_652 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus684", idPrefix+"Bus652", "0", net);
				Line684_652.setBranchCode(AclfBranchCode.LINE);
				length = 800.0*ft2mile; // convert to miles
				zabc_pu = IEEEFeederLineCode.zMtx607.multiply(length/zBase4160);
				Line684_652.setZabc(zabc_pu);
				
				

				//!SWITCH DEFINITIONS 
				//New Line.671692    Phases=3 Bus1=671   Bus2=692  Switch=y  r1=1e-4 r0=1e-4 x1=0.000 x0=0.000 c1=0.000 c0=0.000
				
				DStab3PBranch Line671_692  = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus671", idPrefix+"Bus692", "0", net);
				Line671_692.setBranchCode(AclfBranchCode.LINE);
				zabc_pu = new Complex3x3(new Complex(1.0E-6,0),new Complex(1.0E-6,0),new Complex(1.0e-6,0));
				Line671_692.setZabc(zabc_pu);            
				
				
		}

}
