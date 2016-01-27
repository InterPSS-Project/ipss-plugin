package org.ipss.multiNet.test;

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
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.PerformanceTimer;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDStabSimuHelper;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDStabSolverImpl;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDynEventProcessor;
import org.ipss.multiNet.algo.SubNetworkProcessor;
import org.ipss.multiNet.algo.powerflow.TDMultiNetPowerflowAlgorithm;
import org.ipss.threePhase.basic.Branch3Phase;
import org.ipss.threePhase.basic.Bus3Phase;
import org.ipss.threePhase.basic.Gen3Phase;
import org.ipss.threePhase.basic.Load3Phase;
import org.ipss.threePhase.basic.Phase;
import org.ipss.threePhase.basic.impl.Gen3PhaseImpl;
import org.ipss.threePhase.basic.impl.Load3PhaseImpl;
import org.ipss.threePhase.dynamic.DStabNetwork3Phase;
import org.ipss.threePhase.dynamic.algo.DynamicEventProcessor3Phase;
import org.ipss.threePhase.dynamic.model.InductionMotor3PhaseAdapter;
import org.ipss.threePhase.dynamic.model.PVDistGen3Phase;
import org.ipss.threePhase.dynamic.model.impl.SinglePhaseACMotor;
import org.ipss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.ipss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.ipss.threePhase.util.ThreePhaseAclfOutFunc;
import org.ipss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.adpter.AcscXformer;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.dstab.dynLoad.InductionMotor;
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
	    
	    dsNet.getBus("Bus5").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus5").setLoadCode(AclfLoadCode.NON_LOAD);
	    
	    double totalLoad = 125;
	    
	    addFeeder2Bus(dsNet,(Bus3Phase) dsNet.getBus("Bus5"),10,totalLoad);
	    
	    
	    // add those dynamic load model on the figure
	    
	    double totalLoadMVA =150;
		double loadMVA = totalLoadMVA/5.0;
		double acPercent = .50;
		double motorPercent = .20;
		double pvPercent = .15;
		
		double acMVA = loadMVA *acPercent/3.0;
		double motorMVA = loadMVA*motorPercent;
		double pvGen = totalLoad/5.0*pvPercent/100.0;
	    
	    for(int i =12;i<=16;i++){
			Bus3Phase loadBus = (Bus3Phase) dsNet.getBus("Bus"+i);
			
			/*
			Load3Phase load1 = new Load3PhaseImpl();
			load1.set3PhaseLoad(new Complex3x1(new Complex(0.3,0.05),new Complex(0.3,0.05),new Complex(0.3,0.05)));
			loadBus.getThreePhaseLoadList().add(load1);
			*/
				

//			// AC motor, 50%
//			
//			 SinglePhaseACMotor ac1 = new SinglePhaseACMotor(loadBus,"1");
//		  		ac1.setLoadPercent(50);
//		  		ac1.setPhase(Phase.A);
//		  		ac1.setMVABase(acMVA);
//		  		ac1.setTstall(99); // disable ac stalling
//		  		loadBus.getPhaseADynLoadList().add(ac1);
//		  		
//		  		
//		  		
//		  	SinglePhaseACMotor ac2 = new SinglePhaseACMotor(loadBus,"2");
//		  		ac2.setLoadPercent(50);
//		  		ac2.setPhase(Phase.B);
//		  		ac2.setMVABase(acMVA);
//		  		ac2.setTstall(99); // disable ac stalling
//		  		loadBus.getPhaseBDynLoadList().add(ac2);
//		  		
//
//		  		
//		  	SinglePhaseACMotor ac3 = new SinglePhaseACMotor(loadBus,"3");
//		  		ac3.setLoadPercent(50);
//		  		ac3.setPhase(Phase.C);
//		  		ac3.setMVABase(acMVA);
//		  		ac3.setTstall(99); // disable ac stalling
//		  		loadBus.getPhaseCDynLoadList().add(ac3);
//			
//			
//			// 3 phase motor, 20%
//			
//		  		InductionMotor indMotor= DStabObjectFactory.createInductionMotor("1");
//				indMotor.setDStabBus(loadBus);
//
//				indMotor.setXm(3.0);
//				indMotor.setXl(0.07);
//				indMotor.setRa(0.032);
//				indMotor.setXr1(0.3);
//				indMotor.setRr1(0.01);
//				
//		
//				indMotor.setMVABase(motorMVA );
//				indMotor.setH(1.0);
//				
//				InductionMotor3PhaseAdapter indMotor3Phase = new InductionMotor3PhaseAdapter(indMotor);
//				indMotor3Phase.setLoadPercent(motorPercent); //0.06 MW
//				loadBus.getThreePhaseDynLoadList().add(indMotor3Phase);	
//			
//			
//			// PV generation
//			
//				Gen3Phase gen1 = new Gen3PhaseImpl();
//				gen1.setParentBus(loadBus);
//				gen1.setId("PVGen");
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
//				pv.setUnderVoltTripAll(0.4);
//				pv.setUnderVoltTripStart(0.8);
			
			
		}
	    
	    
	   // System.out.println("net ="+dsNet.net2String());
	    
	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		 proc.addSubNetInterfaceBranch("Bus5->Bus10(0)",false);
		 proc.splitFullSystemIntoSubsystems(true);
		 //TODO this has to be manually identified
		 proc.set3PhaseSubNetByBusId("Bus11");
		 
		 System.out.println("external boundary bus: "+proc.getExternalSubNetBoundaryBusIdList());
		 
		 System.out.println("internal boundary bus: "+proc.getInternalSubNetBoundaryBusIdList());
		 
	    
	    
	    //TODO create TDMultiNetPowerflowAlgo
	    
		 TDMultiNetPowerflowAlgorithm tdAlgo = new TDMultiNetPowerflowAlgorithm(dsNet,proc);
		 
	    
		 assertTrue(tdAlgo.powerflow()); 
		 
		 //System.out.println(tdAlgo.getTransmissionNetwork().net2String());
		 
		 System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
		 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
	
	
			
		  MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
		  
		  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
		  DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
		    
		  
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(0.2);
			

			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			
			//applied the event
			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus9",dsNet,SimpleFaultCode.GROUND_3P,new Complex(0.0),null,0.01d,0.05),"3phaseFault@Bus5");
	        
			
			StateMonitor sm = new StateMonitor();
			sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
			sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus1"});
			
			//1Phase AC motor extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getParentBus().getId()+"_phase"+this.getPhase();
			
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus13_phaseA");
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(5);
			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			
			IpssLogger.getLogger().setLevel(Level.WARNING);
			
			PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
			
	        // Must use this dynamic event process to modify the YMatrixABC
//			dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
			
			dstabAlgo.setSolver( new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
			dstabAlgo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(mNetHelper));
			
			if (dstabAlgo.initialization()) {
				System.out.println(ThreePhaseAclfOutFunc.busLfSummary(dsNet));
				
				System.out.println(dsNet.getMachineInitCondition());
				
				System.out.println("Running 3Phase DStab simulation ...");
				timer.start();
				//dstabAlgo.performSimulation();
				
				while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
					
					dstabAlgo.solveDEqnStep(true);
				}
			}
			System.out.println(sm.toCSVString(sm.getBusVoltTable()));
			System.out.println(sm.toCSVString(sm.getBusAngleTable()));
			System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
		
	
	}
	
	public void addFeeder2Bus(DStabNetwork3Phase net,Bus3Phase sourceBus, int startNum, double totalMW) throws InterpssException{
		
			
		   double scaleFactor = totalMW/5;
		   
		   System.out.println("scale factor = "+scaleFactor);
		   
		   int busIdx = startNum;
			
			Bus3Phase bus1 = ThreePhaseObjectFactory.create3PDStabBus("Bus"+(busIdx++), net);
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
			
			Branch3Phase xfr1 = ThreePhaseObjectFactory.create3PBranch(sourceBus.getId(), bus1.getId(), "0", net);
			xfr1.setBranchCode(AclfBranchCode.XFORMER);
			xfr1.setToTurnRatio(1.02);
			xfr1.setZ( new Complex( 0.0, 0.05 ));
			//xfr1_2.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex( 0.0, 0.04 )));
			//xfr1_2.setZ0( new Complex(0.0, 0.4 ));
			
			AcscXformer xfr01 = acscXfrAptr.apply(xfr1);
			xfr01.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
			xfr01.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
		

			
		Bus3Phase bus2 = ThreePhaseObjectFactory.create3PDStabBus("Bus"+(busIdx++), net);
			bus2.setAttributes("feeder bus 2", "");
			bus2.setBaseVoltage(12500.0);
			// set the bus to a non-generator bus
			bus2.setGenCode(AclfGenCode.NON_GEN);
			// set the bus to a constant power load bus
			bus2.setLoadCode(AclfLoadCode.CONST_P);
			
			
		Branch3Phase xfr1_2 = ThreePhaseObjectFactory.create3PBranch(bus1.getId(), bus2.getId(), "0", net);
			xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
			xfr1_2.setToTurnRatio(1.02);
			xfr1_2.setZ( new Complex( 0.0, 0.04 ));
			//xfr1_2.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex( 0.0, 0.04 )));
			//xfr1_2.setZ0( new Complex(0.0, 0.4 ));
		
		
		AcscXformer xfr0 = acscXfrAptr.apply(xfr1_2);
			xfr0.setFromConnectGroundZ(XfrConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
			xfr0.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);

			
			
		Bus3Phase bus3 = ThreePhaseObjectFactory.create3PDStabBus("Bus"+(busIdx++), net);
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
			
			
			Bus3Phase bus4 = ThreePhaseObjectFactory.create3PDStabBus("Bus"+(busIdx++), net);
			bus4.setAttributes("feeder bus 4", "");
			bus4.setBaseVoltage(12500.0);
			// set the bus to a non-generator bus
			bus4.setGenCode(AclfGenCode.GEN_PQ);
			// set the bus to a constant power load bus
			bus4.setLoadCode(AclfLoadCode.CONST_P);
			
			
			Bus3Phase bus5 = ThreePhaseObjectFactory.create3PDStabBus("Bus"+(busIdx++), net);
			bus5.setAttributes("feeder bus 5", "");
			bus5.setBaseVoltage(12500.0);
			// set the bus to a non-generator bus
			bus5.setGenCode(AclfGenCode.GEN_PQ);
			// set the bus to a constant power load bus
			bus5.setLoadCode(AclfLoadCode.CONST_P);
			
			
			Bus3Phase bus6 = ThreePhaseObjectFactory.create3PDStabBus("Bus"+(busIdx++), net);
			bus6.setAttributes("feeder bus 6", "");
			bus6.setBaseVoltage(12500.0);
			// set the bus to a non-generator bus
			bus6.setGenCode(AclfGenCode.GEN_PQ);
			// set the bus to a constant power load bus
			bus6.setLoadCode(AclfLoadCode.CONST_P);
			
			Bus3Phase bus7 = ThreePhaseObjectFactory.create3PDStabBus("Bus"+(busIdx++), net);
			bus7.setAttributes("feeder bus 7", "");
			bus7.setBaseVoltage(12500.0);
			// set the bus to a non-generator bus
			bus7.setGenCode(AclfGenCode.GEN_PQ);
			// set the bus to a constant power load bus
			bus7.setLoadCode(AclfLoadCode.CONST_P);
			
			
			for(int i =startNum+2;i<=6+startNum;i++){
				Bus3Phase loadBus = (Bus3Phase) net.getBus("Bus"+i);
				Load3Phase load1 = new Load3PhaseImpl();
				load1.set3PhaseLoad(new Complex3x1(new Complex(0.01,0.001),new Complex(0.010,0.001),new Complex(0.01,0.001)).multiply(scaleFactor));
				if(loadBus == null) throw new Error("i = "+i);
				loadBus.getThreePhaseLoadList().add(load1);
				
			}
			
		
			
			
			for(int i =startNum+1;i<6+startNum;i++){
				Branch3Phase Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus"+i, "Bus"+(i+1), "0", net);
				Line2_3.setBranchCode(AclfBranchCode.LINE);
				
				// unbalanced feeder
				//Complex3x3 zabcActual = this.getFeederZabc601().multiply(5.28/scaleFactor); // length is 1 mile per section, then consider the parallelism of rescaling 
				
				// 3phase balanced
				Complex3x3 zabcActual = this.getFeederEqualZabc601().multiply(5.28/scaleFactor);
				
				Double zbase = net.getBus("Bus"+i).getBaseVoltage()*net.getBus("Bus"+i).getBaseVoltage()/net.getBaseMva()/1.0E6;
				Line2_3.setZabc(zabcActual.multiply(1/zbase));
				
			}
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
