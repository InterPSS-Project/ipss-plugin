package org.interpss.threePhase.test;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.DStab3PBranch;
import org.interpss.threePhase.basic.DStab3PBus;
import org.interpss.threePhase.basic.Gen3Phase;
import org.interpss.threePhase.basic.Load3Phase;
import org.interpss.threePhase.basic.impl.Load3PhaseImpl;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.impl.DStabNetwork3phaseImpl;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.DStabObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.adpter.AcscXformer;
import com.interpss.core.net.NetworkType;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineModelType;

public class TestBase {

	
	 public DStabNetwork3Phase create2BusSys() throws InterpssException{
			
			DStabNetwork3Phase net = new DStabNetwork3phaseImpl();

			double baseKva = 100000.0;
			
			// set system basekva for loadflow calculation
			net.setBaseKva(baseKva);
		  
		   //Bus 1
			DStab3PBus bus1 = ThreePhaseObjectFactory.create3PDStabBus("Bus1", net);
	  		// set bus name and description attributes
	  		bus1.setAttributes("Bus 1", "");
	  		// set bus base voltage 
	  		bus1.setBaseVoltage(230000.0);
	  		// set bus to be a swing bus
	  		bus1.setGenCode(AclfGenCode.NON_GEN);
	  		// adapt the bus object to a swing bus object
	  		bus1.setLoadCode(AclfLoadCode.CONST_P);
	  		
	  		//bus1.setLoadPQ(new Complex(1.0,0.2));
	  		
	  		Load3Phase load1 = new Load3PhaseImpl();
			load1.set3PhaseLoad(new Complex3x1(new Complex(1.0,0.2),new Complex(1.0,0.2),new Complex(1.0,0.2)));
			bus1.getThreePhaseLoadList().add(load1);
	  		
			

	  	  	// Bus 3
			DStab3PBus bus3 = ThreePhaseObjectFactory.create3PDStabBus("Bus3", net);
	  		// set bus name and description attributes
	  		bus3.setAttributes("Bus 3", "");
	  		// set bus base voltage 
	  		bus3.setBaseVoltage(230000.0);
	  		// set bus to be a swing bus
	  		bus3.setGenCode(AclfGenCode.SWING);
	  		
	  		bus3.setSortNumber(1);
	  		bus3.setVoltage(new Complex(1.025,0));
	  		
	  		Gen3Phase gen2 = ThreePhaseObjectFactory.create3PGenerator("Gen2");
	  		gen2.setMvaBase(100.0);
	  		gen2.setDesiredVoltMag(1.025);
	  		//gen2.setGen(new Complex(0.7164,0.2710));
	  		gen2.setPosGenZ(new Complex(0.02,0.2));
	  		gen2.setNegGenZ(new Complex(0.02,0.2));
	  		gen2.setZeroGenZ(new Complex(0.000,1.0E9));
	  		
	  		//add to contributed gen list
	  		bus3.getContributeGenList().add(gen2);
	  		
	  		EConstMachine mach2 = (EConstMachine)DStabObjectFactory.
					createMachine("1", "Mach-1", MachineModelType.ECONSTANT, net, "Bus3", "Gen2");
	  		
	  		mach2.setRating(100, UnitType.mVA, net.getBaseKva());
			mach2.setRatedVoltage(230000.0);
			mach2.calMultiFactors();
			mach2.setH(5.0E6);
			mach2.setD(0.01);
			mach2.setRa(0.02);
			mach2.setXd1(0.20);
	  				
	  	
	  		
	  		DStab3PBranch bra = ThreePhaseObjectFactory.create3PBranch("Bus1", "Bus3", "0", net);
			bra.setBranchCode(AclfBranchCode.LINE);
			bra.setZ( new Complex(0.000,   0.100));
			bra.setHShuntY(new Complex(0, 0.200/2));
			bra.setZ0( new Complex(0.0,	  0.3));
			bra.setHB0(0.200/2);
	      
		
			//net.setBusNumberArranged(true);
	  		return net;
			
		}
	 
	 public DStabNetwork3Phase create6BusFeeder() throws InterpssException{
			
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
			bus1.setVoltage(new Complex(1.01,0));
			
			Gen3Phase constantGen = ThreePhaseObjectFactory.create3PGenerator("Source");
			constantGen.setMvaBase(100);
			constantGen.setPosGenZ(new Complex(0.0,0.05));
			constantGen.setNegGenZ(new Complex(0.0,0.05));
			constantGen.setZeroGenZ(new Complex(0.0,0.05));
			bus1.getContributeGenList().add(constantGen);
			
			
			EConstMachine mach = (EConstMachine)DStabObjectFactory.
					createMachine("MachId", "MachName", MachineModelType.ECONSTANT, net, "Bus1", "Source");
		
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
			xfr1_2.setZ( new Complex( 0.0, 0.04 ));
			//xfr1_2.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex( 0.0, 0.04 )));
			//xfr1_2.setZ0( new Complex(0.0, 0.4 ));
		
		
		AcscXformer xfr0 = acscXfrAptr.apply(xfr1_2);
			xfr0.setFromConnectGroundZ(XfrConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
			xfr0.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);

			
			
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
			
			
			for(int i =2;i<=6;i++){
				DStab3PBus loadBus = (DStab3PBus) net.getBus("Bus"+i);
				Load3Phase load1 = new Load3PhaseImpl();
				load1.set3PhaseLoad(new Complex3x1(new Complex(0.3,0.05),new Complex(0.3,0.05),new Complex(0.3,0.05)));
				loadBus.getThreePhaseLoadList().add(load1);
				
			}
			
			for(int i =2;i<6;i++){
				DStab3PBranch Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus"+i, "Bus"+(i+1), "0", net);
				Line2_3.setBranchCode(AclfBranchCode.LINE);
				Complex3x3 zabcActual = this.getFeederZabc601().multiply(5.28).multiply(0.05);
				Double zbase = net.getBus("Bus"+i).getBaseVoltage()*net.getBus("Bus"+i).getBaseVoltage()/net.getBaseMva()/1.0E6;
				Line2_3.setZabc(zabcActual.multiply(1/zbase));
				
			}
			
			
			
			return net; 
			
			
			
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
		
	    private Complex3x3 getFeederYabc601(){
			  return new Complex3x3();
		}
}
