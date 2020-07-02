package org.interpss.threePhase.test.system;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;
import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.Branch3Phase;
import org.interpss.threePhase.basic.Bus3Phase;
import org.interpss.threePhase.basic.IEEEFeederLineCode;
import org.interpss.threePhase.basic.Load3Phase;
import org.interpss.threePhase.basic.impl.Load3PhaseImpl;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.adpter.AcscXformer;
import com.interpss.core.net.NetworkType;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabBus;

public class Test_oneSource_multiFeeder {
	
	
	@Test
	public void test_oneSource_multiFeeder() throws InterpssException{
		
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		
		DStabNetwork3Phase net = ThreePhaseObjectFactory.create3PhaseDStabNetwork();
		
		double baseVolt4160 = 4160.0;
		double baseKva = 1000.0; // 1 MW
		
		// identify this is a distribution network
		net.setNetworkType(NetworkType.DISTRIBUTION);
		
		net.setBaseKva(baseKva);
		
		Bus3Phase sourceBus =  ThreePhaseObjectFactory.create3PDStabBus("sourceBus", net);
		sourceBus.setAttributes("source bus", "");
		sourceBus.setBaseVoltage(baseVolt4160);
		// set the bus to a non-generator bus
		sourceBus.setGenCode(AclfGenCode.SWING);
		// set the bus to a constant power load bus
		sourceBus.setLoadCode(AclfLoadCode.NON_LOAD);
		
		int feederNum = 2;
		for (int i = 1; i<= feederNum; i++){
			
			createFeeder(net, sourceBus, "Bus1", i, 1.0);
		}
		
		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(net);
		//distPFAlgo.orderDistributionBuses(true);
		
		assertTrue(distPFAlgo.powerflow());
		
		for(BaseDStabBus<?,?> bus: net.getBusList()){
			System.out.println("id, sortNum: "+bus.getId()+","+bus.getSortNumber());
		}
		
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(net));
		
	}
	
	
	
	
	private void createFeeder(DStabNetwork3Phase net, Bus3Phase sourceBus, String transBusId, int feederIdx ,double mvaBase ) throws InterpssException{
		
		   double ft2mile = 1.0/5280.0;
		  
		   double baseVolt4160 = 4160.0; //4.16 kV
		   double baseVolt480 = 480.0;
		   double vabase = mvaBase*1.0E6; // 1MW
		   
		   
		   double zBase4160 = baseVolt4160*baseVolt4160/vabase;
		   double zBase480  = baseVolt480*baseVolt480/vabase;
		   
		   double loadScaleFactor =3;
		   
		   String idPrefix = transBusId+"_feeder_"+feederIdx+"_";
		   

			

			Bus3Phase bus650 = (Bus3Phase) sourceBus;

		
			// voltage regulator bus RG60
			Bus3Phase busRG60 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"BusRG60", net);
			busRG60.setAttributes("feeder RG60", "");
			busRG60.setBaseVoltage(baseVolt4160);
			// set the bus to a non-generator bus
			busRG60.setGenCode(AclfGenCode.NON_GEN);
			// set the bus to a constant power load bus
			busRG60.setLoadCode(AclfLoadCode.NON_LOAD);
			
			
			Bus3Phase bus632 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus632", net);
			bus632.setAttributes("feeder 632", "");
			bus632.setBaseVoltage(baseVolt4160);
			// set the bus to a non-generator bus
	
			// set the bus to a constant power load bus
			bus632.setLoadCode(AclfLoadCode.NON_LOAD);
			
			
			Bus3Phase bus633 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus633", net);
			bus633.setAttributes("feeder 633", "");
			bus633.setBaseVoltage(baseVolt4160);

			// set the bus to a constant power load bus
			bus633.setLoadCode(AclfLoadCode.NON_LOAD);
			
			
			Bus3Phase bus634 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus634", net);
			bus634.setAttributes("feeder 634", "");
			bus634.setBaseVoltage(baseVolt480);
			// set the bus to a constant power load bus
			bus634.setLoadCode(AclfLoadCode.CONST_P);
			/*
			 * New Load.634a Bus1=634.1     Phases=1 Conn=Wye  Model=1 kV=0.277  kW=160   kvar=110 
          New Load.634b Bus1=634.2     Phases=1 Conn=Wye  Model=1 kV=0.277  kW=120   kvar=90 
          New Load.634c Bus1=634.3     Phases=1 Conn=Wye  Model=1 kV=0.277  kW=120   kvar=90 
			 */
			Load3Phase load634 = new Load3PhaseImpl();
			load634.set3PhaseLoad( new Complex3x1(new Complex(0.160,0.11),new Complex(0.120,0.09),new Complex(0.120,0.090)).multiply(loadScaleFactor));
			bus634.getThreePhaseLoadList().add(load634);
			
			
			
			
			Bus3Phase bus645 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus645", net);
			bus645.setAttributes("feeder 645", "");
			bus645.setBaseVoltage(baseVolt4160);
			// set the bus to a constant power load bus
			bus645.setLoadCode(AclfLoadCode.CONST_P);
			//New Load.645 Bus1=645.2       Phases=1 Conn=Wye  Model=1 kV=2.4      kW=170   kvar=125 
			Load3Phase load645 = new Load3PhaseImpl();
			load645.set3PhaseLoad( new Complex3x1(new Complex(0.0),new Complex(0.170,0.125),new Complex(0)).multiply(loadScaleFactor));
			bus645.getThreePhaseLoadList().add(load645);
			
			
			
			
			
			Bus3Phase bus646 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus646", net);
			bus646.setAttributes("feeder 646", "");
			bus646.setBaseVoltage(baseVolt4160);
			// set the bus to a constant power load bus
			bus646.setLoadCode(AclfLoadCode.NON_LOAD);
			bus645.setLoadCode(AclfLoadCode.CONST_P);
			//New Load.646 Bus1=646.2.3    Phases=1 Conn=Delta Model=2 kV=4.16    kW=230   kvar=132 
			Load3Phase load646 = new Load3PhaseImpl();
			load646.set3PhaseLoad( new Complex3x1(new Complex(0.0),new Complex(0.230/2,0.132/2),new Complex(0.230/2,0.132/2)).multiply(loadScaleFactor));
			bus646.getThreePhaseLoadList().add(load646);
			
			
			
			
			Bus3Phase bus671 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus671", net);
			bus671.setAttributes("feeder 671", "");
			bus671.setBaseVoltage(baseVolt4160);
			// set the bus to a constant power load bus
			bus671.setLoadCode(AclfLoadCode.CONST_P);
			// New Load.671 Bus1=671.1.2.3  Phases=3 Conn=Delta Model=1 kV=4.16   kW=1155 kvar=660
			Load3Phase load671 = new Load3PhaseImpl();
			load671.set3PhaseLoad(new Complex3x1(new Complex(1.155/3,0.660/3),new Complex(1.155/3,0.660/3),new Complex(1.155/3,0.660/3)).multiply(loadScaleFactor));
			bus671.getThreePhaseLoadList().add(load671);
			
			
			
			
			Bus3Phase bus684 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus684", net);
			bus684.setAttributes("feeder 684", "");
			bus684.setBaseVoltage(baseVolt4160);
			// set the bus to a constant power load bus
			bus684.setLoadCode(AclfLoadCode.NON_LOAD);
			
			
			Bus3Phase bus611 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus611", net);
			bus611.setAttributes("feeder 611", "");
			bus611.setBaseVoltage(baseVolt4160);
			// set the bus to a constant power load bus
			bus611.setLoadCode(AclfLoadCode.CONST_P);
			//New Load.611 Bus1=611.3      Phases=1 Conn=Wye  Model=5 kV=2.4  kW=170   kvar=80 
			Load3Phase load611 = new Load3PhaseImpl();
			load611.set3PhaseLoad(new Complex3x1(new Complex(0),new Complex(0),new Complex(0.170,0.080)).multiply(loadScaleFactor));
			bus611.getThreePhaseLoadList().add(load611);
			
			
			
			
			Bus3Phase bus652 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus652", net);
			bus652.setAttributes("feeder 652", "");
			bus652.setBaseVoltage(baseVolt4160);
			// set the bus to a constant power load bus
			bus652.setLoadCode(AclfLoadCode.NON_LOAD);
			//New Load.652 Bus1=652.1      Phases=1 Conn=Wye  Model=2 kV=2.4  kW=128   kvar=86 
			
			
			Bus3Phase bus680 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus680", net);
			bus680.setAttributes("feeder 680", "");
			bus680.setBaseVoltage(baseVolt4160);
			// set the bus to a constant power load bus
			bus680.setLoadCode(AclfLoadCode.NON_LOAD);
			
			
			Bus3Phase bus692 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus692", net);
			bus692.setAttributes("feeder 692", "");
			bus692.setBaseVoltage(baseVolt4160);
			// set the bus to a constant power load bus
			bus692.setLoadCode(AclfLoadCode.CONST_P);
			// New Load.692 Bus1=692.3.1    Phases=1 Conn=Delta Model=5 kV=4.16    kW=170   kvar=151 
			Load3Phase load692 = new Load3PhaseImpl();
			load692.set3PhaseLoad(new Complex3x1(new Complex(0.170/2,0.151/2),new Complex(0),new Complex(0.170/2,0.151/2)).multiply(loadScaleFactor));
			bus692.getThreePhaseLoadList().add(load692);
			
			

			Bus3Phase bus675 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus675", net);
			bus675.setAttributes("feeder 675", "");
			bus675.setBaseVoltage(baseVolt4160);
			// set the bus to a constant power load bus
			bus675.setLoadCode(AclfLoadCode.CONST_P);
			/*
			 * New Load.675a Bus1=675.1    Phases=1 Conn=Wye  Model=1 kV=2.4  kW=485   kvar=190 
          New Load.675b Bus1=675.2    Phases=1 Conn=Wye  Model=1 kV=2.4  kW=68   kvar=60 
          New Load.675c Bus1=675.3    Phases=1 Conn=Wye  Model=1 kV=2.4  kW=290   kvar=212 
			 */
			
			Load3Phase load675 = new Load3PhaseImpl();
			load675.set3PhaseLoad(new Complex3x1(new Complex(0.485,0.190),new Complex(0.068,0.06),new Complex(0.290,0.212)).multiply(loadScaleFactor));
			bus675.getThreePhaseLoadList().add(load675);
			
			
			// !Bus 670 is the concentrated point load of the distributed load on line 632 to 671 located at 1/3 the distance from node 632
			Bus3Phase bus670 = ThreePhaseObjectFactory.create3PDStabBus(idPrefix+"Bus670", net);
			bus670.setAttributes("feeder 670", "");
			bus670.setBaseVoltage(baseVolt4160);
			// set the bus to a constant power load bus
			bus670.setLoadCode(AclfLoadCode.CONST_P);
			/*
			 * New Load.670a Bus1=670.1    Phases=1 Conn=Wye  Model=1 kV=2.4  kW=17    kvar=10 
          New Load.670b Bus1=670.2    Phases=1 Conn=Wye  Model=1 kV=2.4  kW=66    kvar=38 
          New Load.670c Bus1=670.3    Phases=1 Conn=Wye  Model=1 kV=2.4  kW=117  kvar=68 
			 */
			Load3Phase load670 = new Load3PhaseImpl();
			load670.set3PhaseLoad(new Complex3x1(new Complex(0.017,0.01),new Complex(0.066,0.038),new Complex(0.117,0.068)).multiply(loadScaleFactor));
			bus670.getThreePhaseLoadList().add(load670);
			
			///////////////////////////////capacitors /////////////////////
			/* !CAPACITOR DEFINITIONS
			   New Capacitor.Cap1 Bus1=675 phases=3 kVAR=600 kV=4.16 
			   New Capacitor.Cap2 Bus1=611.3 phases=1 kVAR=100 kV=2.4 
			*/
			Load3Phase shunty675 = new Load3PhaseImpl();
			shunty675.set3PhaseLoad(new Complex3x1(new Complex(0,-0.2),new Complex(0.0,-0.2),new Complex(0.0,-0.2)).multiply(loadScaleFactor));
			bus675.getThreePhaseLoadList().add(shunty675);
			
			
			Load3Phase shunty611 = new Load3PhaseImpl();
			shunty611.set3PhaseLoad(new Complex3x1(new Complex(0.0, -0.1),new Complex(0.0,0.0),new Complex(0.0,0.0)).multiply(loadScaleFactor));
			bus611.getThreePhaseLoadList().add(shunty611);
			
						
			
			////////////////////////////////// transformers ////////////////////////////////////////////////////////
			
//			Branch3Phase xfr1_2 = ThreePhaseObjectFactory.create3PBranch("SubBus", "Bus650", "0", net);
//			xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
//			xfr1_2.setToTurnRatio(1.0);
//			xfr1_2.setZ( new Complex( 0.0, 0.0001 ));
//			
//		
//		    AcscXformer xfr0 = acscXfrAptr.apply(xfr1_2);
//			xfr0.setFromConnectGroundZ(XfrConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
//			xfr0.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);

			
			Branch3Phase xfr2_3 = ThreePhaseObjectFactory.create3PBranch( bus650.getId(), idPrefix+"BusRG60","0", net);
			xfr2_3.setBranchCode(AclfBranchCode.XFORMER);
			xfr2_3.setToTurnRatio(1.055);
			xfr2_3.setZ( new Complex( 0.0, 0.00001 ));
			
		
		    AcscXformer xfr2 = acscXfrAptr.apply(xfr2_3);
			xfr2.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
			xfr2.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
			
			
			Branch3Phase xfr633_634 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus633", idPrefix+"Bus634", "0", net);
			xfr633_634.setBranchCode(AclfBranchCode.XFORMER);
			xfr633_634.setToTurnRatio(1.0);
			xfr633_634.setZ( new Complex( 0.0, 0.02 ));
			
		
		    AcscXformer xfr3 = acscXfrAptr.apply(xfr633_634);
			xfr3.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
			xfr3.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
			
			
			///////////////////////////////////////////////////////// LINES ////////////////////////////////////////
			
			//!LINE DEFINITIONS 
			//New Line.650632    Phases=3 Bus1=RG60.1.2.3   Bus2=632.1.2.3  LineCode=mtx601 Length=2000 units=ft 
			Branch3Phase Line650_632 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"BusRG60", idPrefix+"Bus632", "0", net);
			Line650_632.setBranchCode(AclfBranchCode.LINE);
			
			double length =2000.0*ft2mile; // convert to miles
			Complex3x3 zabc_pu = IEEEFeederLineCode.zMtx601.multiply(length/zBase4160);
			Line650_632.setZabc(zabc_pu);
			
			//New Line.632670    Phases=3 Bus1=632.1.2.3    Bus2=670.1.2.3  LineCode=mtx601 Length=667  units=ft
			
			Branch3Phase Line632_670 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus632", idPrefix+"Bus670", "0", net);
			Line632_670.setBranchCode(AclfBranchCode.LINE);
			length =667.0*ft2mile; // convert to miles
			zabc_pu = IEEEFeederLineCode.zMtx601.multiply(length/zBase4160);
			Line632_670.setZabc(zabc_pu);
			
			
			//New Line.670671    Phases=3 Bus1=670.1.2.3    Bus2=671.1.2.3  LineCode=mtx601 Length=1333 units=ft
			Branch3Phase Line670_671 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus670", idPrefix+"Bus671", "0", net);
			Line670_671.setBranchCode(AclfBranchCode.LINE);
			length =1333.0*ft2mile; // convert to miles
			zabc_pu = IEEEFeederLineCode.zMtx601.multiply(length/zBase4160);
			Line670_671.setZabc(zabc_pu);
			
			
			//New Line.671680    Phases=3 Bus1=671.1.2.3    Bus2=680.1.2.3  LineCode=mtx601 Length=1000 units=ft 
			Branch3Phase Line671_680 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus671", idPrefix+"Bus680", "0", net);
			Line671_680.setBranchCode(AclfBranchCode.LINE);
			length =1000.0*ft2mile; // convert to miles
			zabc_pu = IEEEFeederLineCode.zMtx601.multiply(length/zBase4160);
			Line671_680.setZabc(zabc_pu);
			
			
			//New Line.632633    Phases=3 Bus1=632.1.2.3    Bus2=633.1.2.3  LineCode=mtx602 Length=500  units=ft
			Branch3Phase Line632_633 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus632", idPrefix+"Bus633", "0", net);
			Line632_633.setBranchCode(AclfBranchCode.LINE);
			length =500.0*ft2mile; // convert to miles
			zabc_pu = IEEEFeederLineCode.zMtx602.multiply(length/zBase4160);
			Line632_633.setZabc(zabc_pu);			
			
			
			
			//New Line.632645    Phases=2 Bus1=632.3.2      Bus2=645.3.2    LineCode=mtx603 Length=500  units=ft 
			
			Branch3Phase Line632_645 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus632", idPrefix+"Bus645", "0", net);
			Line632_645.setBranchCode(AclfBranchCode.LINE);
			length =500.0*ft2mile; // convert to miles
			zabc_pu = IEEEFeederLineCode.zMtx603.multiply(length/zBase4160);
			Line632_645.setZabc(zabc_pu);
			
			
			
			
			//New Line.645646    Phases=2 Bus1=645.3.2      Bus2=646.3.2    LineCode=mtx603 Length=300  units=ft 
			
			Branch3Phase Line645_646 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus645", idPrefix+"Bus646", "0", net);
			Line645_646.setBranchCode(AclfBranchCode.LINE);
			length = 300.0*ft2mile; // convert to miles
			zabc_pu = IEEEFeederLineCode.zMtx603.multiply(length/zBase4160);
			Line645_646.setZabc(zabc_pu);
			
			
			
			//New Line.692675    Phases=3 Bus1=692.1.2.3    Bus2=675.1.2.3  LineCode=mtx606 Length=500  units=ft
			
			Branch3Phase Line692_675 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus692", idPrefix+"Bus675", "0", net);
			Line692_675.setBranchCode(AclfBranchCode.LINE);
			length = 500.0*ft2mile; // convert to miles
			zabc_pu = IEEEFeederLineCode.zMtx606.multiply(length/zBase4160);
			Line692_675.setZabc(zabc_pu);
			
			
			// New Line.671684    Phases=2 Bus1=671.1.3      Bus2=684.1.3    LineCode=mtx604 Length=300  units=ft
			
			Branch3Phase Line671_684 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus671", idPrefix+"Bus684", "0", net);
			Line671_684.setBranchCode(AclfBranchCode.LINE);
			length = 300.0*ft2mile; // convert to miles
			zabc_pu = IEEEFeederLineCode.zMtx604.multiply(length/zBase4160);
			Line671_684.setZabc(zabc_pu);
			
			
			
			// New Line.684611    Phases=1 Bus1=684.3        Bus2=611.3      LineCode=mtx605 Length=300  units=ft 
			
			Branch3Phase Line684_611 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus684", idPrefix+"Bus611", "0", net);
			Line684_611.setBranchCode(AclfBranchCode.LINE);
			length = 300.0*ft2mile; // convert to miles
			zabc_pu = IEEEFeederLineCode.zMtx605.multiply(length/zBase4160);
			Line684_611.setZabc(zabc_pu);
			
			// New Line.684652    Phases=1 Bus1=684.1        Bus2=652.1      LineCode=mtx607 Length=800  units=ft 

			Branch3Phase Line684_652 = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus684", idPrefix+"Bus652", "0", net);
			Line684_652.setBranchCode(AclfBranchCode.LINE);
			length = 800.0*ft2mile; // convert to miles
			zabc_pu = IEEEFeederLineCode.zMtx607.multiply(length/zBase4160);
			Line684_652.setZabc(zabc_pu);
			
			

			//!SWITCH DEFINITIONS 
			//New Line.671692    Phases=3 Bus1=671   Bus2=692  Switch=y  r1=1e-4 r0=1e-4 x1=0.000 x0=0.000 c1=0.000 c0=0.000
			
			Branch3Phase Line671_692  = ThreePhaseObjectFactory.create3PBranch(idPrefix+"Bus671", idPrefix+"Bus692", "0", net);
			Line671_692.setBranchCode(AclfBranchCode.LINE);
			zabc_pu = new Complex3x3(new Complex(1.0E-6,0),new Complex(1.0E-6,0),new Complex(1.0e-6,0));
			Line671_692.setZabc(zabc_pu);            
			
			
	}

}
