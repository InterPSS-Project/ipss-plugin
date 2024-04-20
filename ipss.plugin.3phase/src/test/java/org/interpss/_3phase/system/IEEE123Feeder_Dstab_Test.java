package org.interpss._3phase.system;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.dstab.dynLoad.impl.InductionMotorImpl;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.DStab1PLoad;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.algo.DynamicEventProcessor3Phase;
import org.interpss.threePhase.dynamic.model.impl.SinglePhaseACMotor;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.interpss.threePhase.util.ThreePhaseAclfOutFunc;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineModelType;

public class IEEE123Feeder_Dstab_Test {
	
	@Test
	public void testIEEE123BusPowerflow(){
		
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.parseFeederData("testData\\feeder\\IEEE123","IEEE123Master_Modified_v2.dss");
		
		parser.calcVoltageBases();
		
		double mvaBase = 1.0;
		parser.convertActualValuesToPU(1.0);
		
		DStabNetwork3Phase distNet = parser.getDistNetwork();
		
//		String netStrFileName = "testData\\feeder\\IEEE123\\ieee123_modified_netString.dat";
//		try {
//			Files.write(Paths.get(netStrFileName), parser.getDistNetwork().net2String().getBytes());
//		} catch (IOException e) {
//			
//			e.printStackTrace();
//		}
		
		// set the  turn ratios of regulators
		parser.getBranchByName("reg1a").setToTurnRatio(1.0438);
		
		
		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		//distPFAlgo.orderDistributionBuses(true);
		distPFAlgo.setInitBusVoltageEnabled(true);
		//distPFAlgo.setMaxIteration(1);
		distPFAlgo.setTolerance(5.0E-3); // tolearnce = 5 kva
		assertTrue(distPFAlgo.powerflow());
		
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(distNet));
		
		DStab3PBus bus150r = (DStab3PBus) distNet.getBus("150r");
		Complex3x1 vabc_150r = bus150r.get3PhaseVotlages();
		/*
		 * 150r,1.0437947512832042,-2.2960030801920628E-4,1.043702121833145,4.188670668153503,1.0437156069184814,2.09417437174131,1.04379 + j-0.00024  -0.52196 + j-0.90381  -0.52166 + j0.9040
		 */
		assertTrue(vabc_150r.subtract(new Complex3x1(new Complex(1.04379,-0.00024),new Complex(-0.52196,-0.90381),new Complex(-0.52166,0.9040))).absMax()<1.0E-4);
		
		/// Compared with IEEE TEST FEEDER RESULTS
		//RG1   |  1.0437 at    .00  |  1.0438 at -120.00  |  1.0438 at  120.00 |    
		assertTrue(Math.abs(vabc_150r.a_0.abs()-1.0437)<1.0E-3); 
		assertTrue(Math.abs(vabc_150r.b_1.abs()-1.0438)<1.0E-3); 
		assertTrue(Math.abs(vabc_150r.c_2.abs()-1.0438)<1.0E-3); 
		
		DStab3PBus bus21 = (DStab3PBus) distNet.getBus("21");
		Complex3x1 vabc_21 = bus21.get3PhaseVotlages();
		/*
		 * 21,0.9976629334520096,-0.039839533149384404,1.0320577255690082,4.166775074256249,1.0114596191804741,2.0742543870511136,0.99687 + j-0.03974  -0.53558 + j-0.88221  -0.48799 + j0.88596
         */
		
		
		/// Compared with IEEE TEST FEEDER RESULTS
		//  21    |   .9983 at  -2.34  |  1.0320 at -121.22  |  1.0111 at  118.81 |    .441
//		assertTrue(Math.abs(vabc_21.a_0.abs()-0.9983)<1.0E-3); //0.9983 is IEEE TEST FEEDER RESULT
//		assertTrue(Math.abs(vabc_21.b_1.abs()-1.0320)<1.0E-3); 
//		assertTrue(Math.abs(vabc_21.c_2.abs()-1.0111)<1.0E-3); 
		
		DStab3PBus bus30 = (DStab3PBus) distNet.getBus("30");
		Complex3x1 vabc_30 = bus30.get3PhaseVotlages();
		
		/*
		 *30,0.9963094030535219,-0.04263498620437,1.0332257087662153,4.167548363697641,1.008218641157312,2.073577445296613,0.9954 + j-0.04246  -0.5355 + j-0.88362  -0.48582 + j0.88345
         */
		
		/// Compared with IEEE TEST FEEDER RESULTS
		// 30    |   .9969 at  -2.50  |  1.0331 at -121.18  |  1.0078 at  118.77 |    .701
//		assertTrue(Math.abs(vabc_30.a_0.abs()-0.9969)<1.0E-3);
//		assertTrue(Math.abs(vabc_30.b_1.abs()-1.0331)<1.0E-3);
//		assertTrue(Math.abs(vabc_30.c_2.abs()-1.0078)<1.0E-3);
	}
	
	//@Test
	public void testIEEE123BusDstabSim() throws InterpssException{
		
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.parseFeederData("testData\\feeder\\IEEE123","IEEE123Master_Modified_v2.dss");
		
		parser.calcVoltageBases();
		
		double mvaBase = 100.0;
		parser.convertActualValuesToPU(mvaBase);
		
		DStabNetwork3Phase distNet = parser.getDistNetwork();
		
//		String netStrFileName = "testData\\feeder\\IEEE123\\ieee123_modified_netString.dat";
//		try {
//			Files.write(Paths.get(netStrFileName), parser.getDistNetwork().net2String().getBytes());
//		} catch (IOException e) {
//			
//			e.printStackTrace();
//		}
		
		// set the  turn ratios of regulators
		parser.getBranchByName("reg1a").setToTurnRatio(1.0438);
		
		
		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);

		assertTrue(distPFAlgo.powerflow());
		
		// output three phase total load
		for(DStab3PBus bus3P: distNet.getBusList()){
			
			System.out.println(bus3P.getId()+", total loads, "+ bus3P.get3PhaseTotalLoad().multiply(100.0*1000.0/3).toString());
		}
		
		// Add the dyanmic machine to the source Bus
		BaseDStabBus bus150 = distNet.getBus("150");
		
		DStabGen constantGen = DStabObjectFactory.createDStabGen();
		constantGen.setId("Source");
		constantGen.setMvaBase(100);
		constantGen.setPosGenZ(new Complex(0.0,0.05));
		constantGen.setNegGenZ(new Complex(0.0,0.05));
		constantGen.setZeroGenZ(new Complex(0.0,0.05));
		bus150.getContributeGenList().add(constantGen);
		
		
		EConstMachine mach = (EConstMachine)DStabObjectFactory.
				createMachine("MachId", "MachName", MachineModelType.ECONSTANT, distNet, "150", "Source");
	
		mach.setRating(100, UnitType.mVA, distNet.getBaseKva());
		mach.setRatedVoltage(4160.0);
		mach.setH(50000.0);
		mach.setXd1(0.05);
		
	    double ACMotorPercent = 50;
	    double IndMotorPercent = 0;
	    double ACPhaseUnbalance = 0;
	    
		List<String> acMotorIds= buildFeederDynModel(distNet, ACMotorPercent, IndMotorPercent,ACPhaseUnbalance);
		
		
		DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
				distNet, IpssCorePlugin.getMsgHub());
			
	
	  	
	  	dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(1.0);
	    //dstabAlgo.setRefMachine(net.getMachine("Bus3-mach1"));
		//distNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("150r", distNet, SimpleFaultCode.GROUND_LG,new Complex(0,0.0),new Complex(0,0.0), 0.5,0.07), "SLG@Bus1");
        
		
		StateMonitor sm = new StateMonitor();
		//sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1"});
		sm.addBusStdMonitor(new String[]{"150","150r","300","30","21"});
		sm.add3PhaseBusStdMonitor(new String[]{"150","150r","300","30","21"});
		
		for(String acMotorId: acMotorIds)
		    sm.addDynDeviceMonitor(DynDeviceType.ACMotor, acMotorId);
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
				
	  	if(dstabAlgo.initialization()){
	  		System.out.println(ThreePhaseAclfOutFunc.busLfSummary(distNet));
	  		System.out.println(distNet.getMachineInitCondition());
	  		
	  		for(String busId: sm.getBusPhAVoltTable().keySet()){
				
				 sm.addBusPhaseVoltageMonitorRecord( busId,dstabAlgo.getSimuTime(), ((DStab3PBus)distNet.getBus(busId)).get3PhaseVotlages());
			}
	  	    double vsag = 0.4;
	  		//dstabAlgo.performSimulation();
	  		while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
				
				dstabAlgo.solveDEqnStep(true);
				
				for(String busId: sm.getBusPhAVoltTable().keySet()){
					
					 sm.addBusPhaseVoltageMonitorRecord( busId,dstabAlgo.getSimuTime(), ((DStab3PBus)distNet.getBus(busId)).get3PhaseVotlages());
				}
				
				
				if(dstabAlgo.getSimuTime()>0.5 && dstabAlgo.getSimuTime()<0.5833){
					mach.setE(vsag);
				}
				else if (dstabAlgo.getSimuTime()>=0.6){
					mach.setE(1.0);
				}
			}
	  	}
	  	
	  	
	  	
	  	
	  	System.out.println(sm.toCSVString(sm.getBusPhAVoltTable()));
	  	System.out.println(sm.toCSVString(sm.getBusPhBVoltTable()));
	  	System.out.println(sm.toCSVString(sm.getBusPhCVoltTable()));
	  	
//	  	System.out.println(sm.toCSVString(sm.getBusAngleTable()));
	  	System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	  	MonitorRecord rec1 = sm.getBusVoltTable().get("30").get(1);
	  	MonitorRecord rec20 = sm.getBusVoltTable().get("30").get(20);
	  	assertTrue(Math.abs(rec1.getValue()-rec20.getValue())<1.0E-4);
	  	
	  	
	  	MonitorRecord rec0_21 = sm.getBusVoltTable().get("21").get(0);
	  	MonitorRecord rec1_21 = sm.getBusVoltTable().get("21").get(50);
	  	MonitorRecord rec50_21 = sm.getBusVoltTable().get("21").get(50);
	  	assertTrue(Math.abs(rec0_21.getValue()-rec1_21.getValue())<1.0E-2);
	  	assertTrue(Math.abs(rec1_21.getValue()-rec50_21.getValue())<1.0E-4);
	  	
		FileUtil.writeText2File("output//IEEE123//AcMotorState.csv",
				sm.toCSVString(sm.getAcMotorStateTable()));
		FileUtil.writeText2File("output//IEEE123////AcMotorP.csv",
				sm.toCSVString(sm.getAcMotorPTable()));
		FileUtil.writeText2File("output//IEEE123//AcMotorQ.csv",
				sm.toCSVString(sm.getAcMotorQTable()));

	}
	
	 private List<String> buildFeederDynModel(DStabNetwork3Phase dsNet, double ACMotorPercent, double IndMotorPercent,double ACPhaseUnbalance) {
			
		    List<String> monList  = new ArrayList<>();
			double Vstallmin = 0.40,Vstallmax = 0.5;
			double Tstallmin = 2/60.0,Tstallmax = 5.0/60.0;
			
	  		double tstall = 0.033;
	  		double vstall = 0.45;
	  		
	  		Hashtable<String, Double> tStallTable = new Hashtable<>();
	  		Hashtable<String, Double> vStallTable = new Hashtable<>();
			
			int k = 0;
			for(DStab3PBus bus: dsNet.getBusList()){
				if(bus.isLoad()){
				DStab3PBus loadBus = bus;
				
				/*
				Load3Phase load1 = new Load3PhaseImpl();
				load1.set3PhaseLoad(new Complex3x1(new Complex(0.3,0.05),new Complex(0.3,0.05),new Complex(0.3,0.05)));
				loadBus.getThreePhaseLoadList().add(load1);
				*/
					

				// AC motor, 50%
				if(loadBus.getSinglePhaseLoadList().size()>0.0){
					
				 for(DStab1PLoad ld1P: loadBus.getSinglePhaseLoadList()){
					 switch (ld1P.getPhaseCode()){
					 
					 case A: 
						 SinglePhaseACMotor ac1 = new SinglePhaseACMotor(loadBus,"1");
					  		ac1.setLoadPercent(ACMotorPercent-ACPhaseUnbalance);
					  		ac1.setPhase(PhaseCode.A);
					  	    
					  		tstall = randDouble(Tstallmin,Tstallmax);
					  		vstall = randDouble(Vstallmin,Vstallmax);
					  		
					  		ac1.setTstall(tstall); // disable ac stalling
					  		ac1.setVstall(vstall);
					  		loadBus.getPhaseADynLoadList().add(ac1);
					  		
					  		monList.add(ac1.getExtendedDeviceId());
					  		
					  		tStallTable.put(ac1.getExtendedDeviceId(), tstall);
					  		vStallTable.put(ac1.getExtendedDeviceId(), vstall);
					  		
					  		break;
					 case B:
					  		
					  		
					  	SinglePhaseACMotor ac2 = new SinglePhaseACMotor(loadBus,"2");
					  		ac2.setLoadPercent(ACMotorPercent);
					  		ac2.setPhase(PhaseCode.B);
					  		tstall = randDouble(Tstallmin,Tstallmax);
					  		vstall = randDouble(Vstallmin,Vstallmax);
					  		
					  		ac2.setTstall(tstall); // disable ac stalling
					  		ac2.setVstall(vstall);
					  		loadBus.getPhaseBDynLoadList().add(ac2);
					  		monList.add(ac2.getExtendedDeviceId());
					  		
					  		tStallTable.put(ac2.getExtendedDeviceId(), tstall);
					  		vStallTable.put(ac2.getExtendedDeviceId(), vstall);
		                  break;
		                  
					 case C:
					  		
					  	SinglePhaseACMotor ac3 = new SinglePhaseACMotor(loadBus,"3");
					  		ac3.setLoadPercent(ACMotorPercent+ACPhaseUnbalance);
					  		ac3.setPhase(PhaseCode.C);
					  		tstall = randDouble(Tstallmin,Tstallmax);
					  		vstall = randDouble(Vstallmin,Vstallmax);
					  		
					  		ac3.setTstall(tstall); // disable ac stalling
					  		ac3.setVstall(vstall);
					  		loadBus.getPhaseCDynLoadList().add(ac3);
					  		monList.add(ac3.getExtendedDeviceId());
					  		
					  		tStallTable.put(ac3.getExtendedDeviceId(), tstall);
					  		vStallTable.put(ac3.getExtendedDeviceId(), vstall);
					  		
					  	 break;
					 }
				 }
				}
				
				for(DStab3PLoad ld3P: loadBus.getThreePhaseLoadList()){
				// 3 phase motor, 20%
				    if(IndMotorPercent>0.0){
				  		InductionMotor indMotor= new InductionMotorImpl(loadBus,"1");
						//indMotor.setDStabBus(loadBus);
						indMotor.setLoadPercent(IndMotorPercent);
			
						indMotor.setXm(3.0);
						indMotor.setXl(0.07);
						indMotor.setRa(0.032);
						indMotor.setXr1(0.3);
						indMotor.setRr1(0.01);
						
				        double motorMVA = ld3P.getLoad(1.0).getReal()*IndMotorPercent/100.0/0.8;
						indMotor.setMvaBase(motorMVA);
						indMotor.setH(0.3);
						indMotor.setA(0.0); //Toreque = (a+bw+cw^2)*To;
						indMotor.setB(0.0); //Toreque = (a+bw+cw^2)*To;
						indMotor.setC(1.0); //Toreque = (a+bw+cw^2)*To;
//						InductionMotor3PhaseAdapter indMotor3Phase = new InductionMotor3PhaseAdapter(indMotor);
//						indMotor3Phase.setLoadPercent(IndMotorPercent); //0.06 MW
//						loadBus.getThreePhaseDynLoadList().add(indMotor3Phase);	
				    }
				}
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
				
				
					k++;
			  }
			}
			
			System.out.println("tstall settings:"+tStallTable.values());
			System.out.println("vstall settings:"+vStallTable.values());
			
			return monList;
		 }

	  private static int randInt(int min, int max) {

		    // Usually this can be a field rather than a method variable
		    Random rand = new Random();

		    // nextInt is normally exclusive of the top value,
		    // so add 1 to make it inclusive
		    int randomNum = rand.nextInt((max - min) + 1) + min;

		    return randomNum;
		}
	  
	  private static double randDouble(double min, double max) {

		    // Usually this can be a field rather than a method variable
		    Random rand = new Random();

		    // nextInt is normally exclusive of the top value,
		    // so add 1 to make it inclusive
		    double randomNum = rand.nextDouble()*(max - min) + min;

		    return randomNum;
		}

}
