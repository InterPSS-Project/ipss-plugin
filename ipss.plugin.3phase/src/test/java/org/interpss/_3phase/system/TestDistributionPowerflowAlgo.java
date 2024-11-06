package org.interpss._3phase.system;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab3PGenImpl;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.abc.Static3PNetwork;
import com.interpss.core.abc.Static3PXformer;
import com.interpss.core.abc.Static3PhaseFactory;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.core.acsc.adpter.AcscXformerAdapter;
import com.interpss.core.net.Bus;
import com.interpss.core.net.NetworkType;

public class TestDistributionPowerflowAlgo {

	@Test
	public void testLineAndXfrGeneralizedMatrices() throws InterpssException {

		Static3PNetwork net = createDistNetNoDG();

		//--------------------------------------------------------------------------------------------
		// 1. Test the distribution line models


		DStab3PBranch line2_4 = (DStab3PBranch) net.getBranch("Bus4", "Bus2", "0");

		/*
		 *  [a] = U
		 */
		Complex3x3 a = line2_4.getToBusVabc2FromBusVabcMatrix();
		System.out.println("[a] ="+a.toString());
		assertTrue(Complex3x3.createUnitMatrix().subtract(a).abs()<1.0E-7);

		/*
		 *  [b] = Zabc
		 */
		Complex3x3 b = line2_4.getToBusIabc2FromBusVabcMatrix();
		System.out.println("[b] ="+b.toString());
		System.out.println("[Z120] ="+b.To120());
		assertTrue(line2_4.getZabc().subtract(b).abs()<1.0E-7);

		/*
		 * [c] = [0]
		 */
		Complex3x3 c = line2_4.getToBusVabc2FromBusIabcMatrix();
		System.out.println("[c] ="+c.toString());

		assertTrue(new Complex3x3().subtract(c).abs()<1.0E-7);

		/*
		 * [d] = U
		 */
		Complex3x3 d = line2_4.getToBusIabc2FromBusIabcMatrix();
		System.out.println("[d] ="+d.toString());
		assertTrue(Complex3x3.createUnitMatrix().subtract(d).abs()<1.0E-7);


		/*
		 * [A] = U
		 *
		 */
		Complex3x3 A = line2_4.getFromBusVabc2ToBusVabcMatrix();
		System.out.println("[A] ="+A.toString());

		assertTrue(Complex3x3.createUnitMatrix().subtract(A).abs()<1.0E-7);

		/*
		 * [B] = Zabc
		 */
		Complex3x3 B = line2_4.getToBusIabc2ToBusVabcMatrix();
		System.out.println("[B] ="+B.toString());
		assertTrue(line2_4.getZabc().subtract(B).abs()<1.0E-7);

		//---------------------------------------------------------------------------------------
		//             2. Test the transformer models
		//---------------------------------------------------------------------------------------

		// Grounded Wye- Grounded Wye
		DStab3PBranch line1_2 = (DStab3PBranch) net.getBranch("Bus1", "Bus2", "0");

		System.out.println("Zabc of xfr1_2 = "+line1_2.getZabc());

		Static3PXformer xfr1_2 = line1_2.to3PXformer();
		AcscXformerAdapter xfr0 = acscXfrAptr.apply(line1_2);
		xfr0.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
		xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);

		System.out.println("------------Grounded Wye- Grounded Wye------------");
		/*
		 * at
		 */
		Complex3x3 at = xfr1_2.getLVBusVabc2HVBusVabcMatrix();
		System.out.println("[at] ="+at.toString());
		assertTrue(Complex3x3.createUnitMatrix().multiply(1/xfr1_2.getToTurnRatio()).subtract(at).abs()<1.0E-7);

		/*
		 * bt
		 */

		Complex3x3 bt = xfr1_2.getLVBusIabc2HVBusVabcMatrix();
		System.out.println("[bt] ="+bt.toString());
		assertTrue(xfr1_2.getZabc().multiply(1/xfr1_2.getToTurnRatio()).subtract(bt).abs()<1.0E-7);

		/*
		 * ct
		 */

		Complex3x3 ct = xfr1_2.getLVBusVabc2HVBusIabcMatrix();
		System.out.println("[ct] ="+ct.toString());
		assertTrue(ct.abs()<1.0E-7);

		/*
		 * dt
		 */

		Complex3x3 dt = xfr1_2.getLVBusIabc2HVBusIabcMatrix();
		System.out.println("[dt] ="+dt.toString());
		assertTrue(Complex3x3.createUnitMatrix().multiply(xfr1_2.getToTurnRatio()).subtract(dt).abs()<1.0E-7);


		/*
		 * At
		 */

		Complex3x3 At = xfr1_2.getHVBusVabc2LVBusVabcMatrix();
		System.out.println("[At] ="+At.toString());
		assertTrue(Complex3x3.createUnitMatrix().multiply(xfr1_2.getToTurnRatio()).subtract(At).abs()<1.0E-7);

		/*
		 * Bt
		 */

		Complex3x3 Bt = xfr1_2.getLVBusIabc2LVBusVabcMatrix();
		System.out.println("[Bt] ="+Bt.toString());
		assertTrue(xfr1_2.getZabc().subtract(Bt).abs()<1.0E-7);






		//
		// Delta-Grounded Wye
		//
		xfr0 = acscXfrAptr.apply(line1_2);
		xfr0.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0.0,0.0), UnitType.PU);
		xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);

		System.out.println("-----------Delta-Grounded Wye Step-down------------");
		/*
		 * at
		 */
		at = xfr1_2.getLVBusVabc2HVBusVabcMatrix();
		System.out.println("[at] ="+at.toString());
		Complex3x3 W_AV = new Complex3x3();
		W_AV.ab = new Complex(2);
		W_AV.ac = new Complex(1);

		W_AV.ba = new Complex(1);
		W_AV.bc = new Complex(2);

		W_AV.ca = new Complex(2);
		W_AV.cb = new Complex(1);

		W_AV = W_AV.multiply(-1.0/3.0*(xfr1_2.getFromTurnRatio()*Math.sqrt(3)/xfr1_2.getToTurnRatio()));


		assertTrue(W_AV.subtract(at).abs()<1.0E-7);

		/*
		 * bt
		 */

		bt = xfr1_2.getLVBusIabc2HVBusVabcMatrix();
		System.out.println("[bt] ="+bt.toString());
		assertTrue(W_AV.multiply(xfr1_2.getZabc()).subtract(bt).abs()<1.0E-7);

		/*
		 * ct
		 */

		ct = xfr1_2.getLVBusVabc2HVBusIabcMatrix();
		System.out.println("[ct] ="+ct.toString());
		assertTrue(ct.abs()<1.0E-7);

		/*
		 * dt
		 */

		dt = xfr1_2.getLVBusIabc2HVBusIabcMatrix();
		System.out.println("[dt] ="+dt.toString());

		Complex3x3 dt1 = new Complex3x3();
		dt1.aa = new Complex(1);
		dt1.ab = new Complex(-1);

		dt1.bb = new Complex(1);
		dt1.bc = new Complex(-1);

		dt1.ca = new Complex(-1);
		dt1.cc = new Complex(1);

		//dt1 = dt1.multiply(xfr1_2.getToTurnRatio());

		assertTrue(dt1.multiply(xfr1_2.getToTurnRatio()/Math.sqrt(3)/xfr1_2.getFromTurnRatio()).subtract(dt).abs()<1.0E-7);


		/*
		 * At
		 */

		At = xfr1_2.getHVBusVabc2LVBusVabcMatrix();
		System.out.println("[At] ="+At.toString());

		Complex3x3 AV = new Complex3x3();
		AV.ab = new Complex(-1);

		AV.bc = new Complex(-1);

		AV.ca = new Complex(-1);

		AV = AV.multiply(Math.sqrt(3)*xfr1_2.getFromTurnRatio()/xfr1_2.getToTurnRatio());


		assertTrue(AV.inv().multiply(dt1).subtract(At).abs()<1.0E-7);

		/*
		 * Bt
		 */

		Bt = xfr1_2.getLVBusIabc2LVBusVabcMatrix();
		System.out.println("[Bt] ="+Bt.toString());
		assertTrue(xfr1_2.getZabc().subtract(Bt).abs()<1.0E-7);


	}


	//@Test
	public void testDistBusOrdering() throws InterpssException {
		Static3PNetwork net = createDistNetNoDG();

		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(net);
		distPFAlgo.orderDistributionBuses(true);

		for(Bus bus:net.getBusList()){
			System.out.println("sortNum of Bus - "+bus.getId()+" is "+bus.getSortNumber());
		}

	}


	@Test
	public void testDistBusPF() throws InterpssException {
		Static3PNetwork net = createDistNetNoDG();

		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(net);
		//distPFAlgo.orderDistributionBuses(true);

		assertTrue(distPFAlgo.powerflow());

		/*
		 *  Vabc of bus -Bus1,1.0100 + j0.0000  -0.5050 + j-0.87469  -0.5050 + j0.87469
			Vabc of bus -Bus2,0.99636 + j-0.05941  -0.54963 + j-0.83317  -0.44673 + j0.89258
			Vabc of bus -Bus3,0.99075 + j-0.07914  -0.56392 + j-0.81844  -0.42683 + j0.89759
			Vabc of bus -Bus4,0.98834 + j-0.09907  -0.57997 + j-0.80639  -0.40837 + j0.90546
		 */
		for(BaseAclfBus bus:net.getBusList()){
			DStab3PBus bus3P = (DStab3PBus) bus;
			System.out.println("Vabc of bus -"+bus3P.getId()+","+bus3P.get3PhaseVotlages().toString());
		}

		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(net));

	}

	@Test
	public void testDistPFWithDG() throws InterpssException {
		Static3PNetwork net = createDistNetWithDG();

		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(net);
		//distPFAlgo.orderDistributionBuses(true);

		assertTrue(distPFAlgo.powerflow());

		/*
		 *  Vabc of bus -Bus1,1.0100 + j0.0000  -0.5050 + j-0.87469  -0.5050 + j0.87469
			Vabc of bus -Bus2,0.99636 + j-0.05941  -0.54963 + j-0.83317  -0.44673 + j0.89258
			Vabc of bus -Bus3,0.99075 + j-0.07914  -0.56392 + j-0.81844  -0.42683 + j0.89759
			Vabc of bus -Bus4,0.98834 + j-0.09907  -0.57997 + j-0.80639  -0.40837 + j0.90546
		 */
		for(BaseAclfBus bus:net.getBusList()){
			DStab3PBus bus3P = (DStab3PBus) bus;
			System.out.println("Vabc of bus -"+bus3P.getId()+","+bus3P.get3PhaseVotlages().toString());
		}

		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(net));

	}

	private Static3PNetwork createDistNetNoDG() throws InterpssException{
		// step-1 create the network object

		Static3PNetwork net = Static3PhaseFactory.eINSTANCE.createStatic3PNetwork();
		// identify this is a distribution network
		net.setNetworkType(NetworkType.DISTRIBUTION);


		// step-2 create all the bus objects

		DStab3PBus bus1 = ThreePhaseObjectFactory.create3PAclfBus("Bus1", net);
  		bus1.setAttributes("69 kV feeder source", "");
  		bus1.setBaseVoltage(69000.0);
  		// set the bus to a non-generator bus
  		bus1.setGenCode(AclfGenCode.SWING);
  		// set the bus to a constant power load bus
  		bus1.setLoadCode(AclfLoadCode.NON_LOAD);
  		bus1.setVoltage(new Complex(1.01,0));


  		DStab3PBus bus2 = ThreePhaseObjectFactory.create3PAclfBus("Bus2", net);
  		bus2.setAttributes("13.8 kV feeder bus 2", "");
  		bus2.setBaseVoltage(13800.0);
  		// set the bus to a non-generator bus
  		bus2.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus2.setLoadCode(AclfLoadCode.NON_LOAD);


  		DStab3PBus bus3 = ThreePhaseObjectFactory.create3PAclfBus("Bus3", net);
  		bus3.setAttributes("13.8 kV feeder bus 3", "");
  		bus3.setBaseVoltage(13800.0);
  		// set the bus to a non-generator bus
  		bus3.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus3.setLoadCode(AclfLoadCode.CONST_P);

  		DStab3PLoad load1 = new DStab3PLoadImpl();
  		load1.set3PhaseLoad(new Complex3x1(new Complex(0.5,0.1),new Complex(0.5,0.1),new Complex(0.5,0.1)));
  		bus3.getThreePhaseLoadList().add(load1);
  		//bus3.setLoadPQ(new Complex(0.5,0.1));


  		DStab3PBus bus4 = ThreePhaseObjectFactory.create3PAclfBus("Bus4", net);
  		bus4.setAttributes("13.8 kV feeder bus 4", "");
  		bus4.setBaseVoltage(13800.0);
  		// set the bus to a non-generator bus
  		bus4.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus4.setLoadCode(AclfLoadCode.CONST_P);

  		//bus4.setLoadPQ(new Complex(1,0.1));

  		DStab3PLoad load2 = new DStab3PLoadImpl();
  		load2.set3PhaseLoad(new Complex3x1(new Complex(1,0.1),new Complex(1,0.1),new Complex(1,0.1)));
  		bus4.getThreePhaseLoadList().add(load2);


  		// step-3 create all branch objects, including line and transformers

  		DStab3PBranch xfr1_2 = ThreePhaseObjectFactory.create3PBranch("Bus1", "Bus2", "0", net);
  		xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
  		xfr1_2.setToTurnRatio(1.02);

  		xfr1_2.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex( 0.0, 0.04 )));
  	    //xfr1_2.setZ( new Complex( 0.0, 0.04 ));
  		//xfr1_2.setZ0( new Complex(0.0, 0.4 ));


		AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr1_2);
		xfr0.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
		xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);

		// for testing connection and from-to relationship only
//		xfr0.setToConnectGroundZ(XfrConnectCode.DELTA, new Complex(0.0,0.0), UnitType.PU);
//		xfr0.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);

		DStab3PBranch Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus2", "Bus3", "0", net);
		Line2_3.setBranchCode(AclfBranchCode.LINE);
		Line2_3.setZ( new Complex( 0.0, 0.04 ));
		Line2_3.setZ0( new Complex(0.0, 0.08 ));


		DStab3PBranch Line2_4 = ThreePhaseObjectFactory.create3PBranch("Bus4", "Bus2", "0", net);
		Line2_4.setBranchCode(AclfBranchCode.LINE);
		Line2_4.setZ( new Complex( 0.0, 0.04 ));
		Line2_4.setZ0( new Complex(0.0, 0.08 ));



	    return net;

	}

	private Static3PNetwork createDistNetWithDG() throws InterpssException{

		/**
		 * create a 3-phase network object
		 */
		Static3PNetwork net = Static3PhaseFactory.eINSTANCE.createStatic3PNetwork();
		// identify this is a distribution network
		net.setNetworkType(NetworkType.DISTRIBUTION);


		/**
		 * create 3-phase buses
		 */
		DStab3PBus bus1 = ThreePhaseObjectFactory.create3PAclfBus("Bus1", net);
  		bus1.setAttributes("69 kV feeder source", "");
  		bus1.setBaseVoltage(69000.0);
  		// set the bus to a non-generator bus
  		bus1.setGenCode(AclfGenCode.SWING);
  		// set the bus to a constant power load bus
  		bus1.setLoadCode(AclfLoadCode.NON_LOAD);
  		bus1.setVoltage(new Complex(1.01,0));




  		DStab3PBus bus2 = ThreePhaseObjectFactory.create3PAclfBus("Bus2", net);
  		bus2.setAttributes("13.8 V feeder bus 2", "");
  		bus2.setBaseVoltage(13800.0);
  		// set the bus to a non-generator bus
  		bus2.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus2.setLoadCode(AclfLoadCode.NON_LOAD);



  		DStab3PBus bus3 = ThreePhaseObjectFactory.create3PAclfBus("Bus3", net);
  		bus3.setAttributes("13.8 V feeder bus 3", "");
  		bus3.setBaseVoltage(13800.0);
  		// set the bus to a non-generator bus
  		bus3.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus3.setLoadCode(AclfLoadCode.CONST_P);

  		DStab3PLoad load1 = new DStab3PLoadImpl();
  		load1.set3PhaseLoad(new Complex3x1(new Complex(0.5,0.1),new Complex(0.3,0.1),new Complex(0.4,0.1)));
  		bus3.getThreePhaseLoadList().add(load1);




  		DStab3PBus bus4 = ThreePhaseObjectFactory.create3PAclfBus("Bus4", net);
  		bus4.setAttributes("13.8 V feeder bus 4", "");
  		bus4.setBaseVoltage(13800.0);
  		// set the bus to a non-generator bus
  		bus4.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus4.setLoadCode(AclfLoadCode.CONST_P);

  		// a three-phase load at bus 4
  		DStab3PLoad load2 = new DStab3PLoadImpl();
  		load2.set3PhaseLoad(new Complex3x1(new Complex(1.5,0.3),new Complex(1.2,0.2),new Complex(1.0,0.1)));
  		bus4.getThreePhaseLoadList().add(load2);

  		// a DG at bus 4
  		DStab3PGen gen1 = new DStab3PGenImpl();
  		gen1.setParentBus(bus4);
  		gen1.setGen(new Complex(0.5,0));  // total gen power, system mva based
  		gen1.setMvaBase(10);
  		bus4.getThreePhaseGenList().add(gen1);



  		/**
		 * create 3-phase branches
		 */

  		DStab3PBranch xfr1_2 = ThreePhaseObjectFactory.create3PBranch("Bus1", "Bus2", "0", net);
  		// set the branch type to be transformer
  		xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
  		xfr1_2.setToTurnRatio(1.02);
  		// lead impedance Xl = 0.04 pu
  		xfr1_2.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex( 0.0, 0.04 )));

		//set the transformer connection type
		AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr1_2);

		// Step down transformer, high voltage side delta, low voltage side grounded wye
		xfr0.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
		xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);




		DStab3PBranch Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus2", "Bus3", "0", net);
		Line2_3.setBranchCode(AclfBranchCode.LINE);
		Line2_3.setZ( new Complex( 0.0, 0.04 ));
		Line2_3.setZ0( new Complex(0.0, 0.08 ));


		DStab3PBranch Line2_4 = ThreePhaseObjectFactory.create3PBranch("Bus4", "Bus2", "0", net);
		Line2_4.setBranchCode(AclfBranchCode.LINE);
		Line2_4.setZ( new Complex( 0.0, 0.04 ));
		Line2_4.setZ0( new Complex(0.0, 0.08 ));



	    return net;

	}


	private Static3PNetwork createNet4PosSeqPF() throws InterpssException{
		Static3PNetwork net = Static3PhaseFactory.eINSTANCE.createStatic3PNetwork();
		// identify this is a distribution network
		net.setNetworkType(NetworkType.DISTRIBUTION);

		DStab3PBus bus1 = ThreePhaseObjectFactory.create3PAclfBus("Bus1", net);
  		bus1.setAttributes("69 kV feeder source", "");
  		bus1.setBaseVoltage(69000.0);
  		// set the bus to a non-generator bus
  		bus1.setGenCode(AclfGenCode.SWING);
  		// set the bus to a constant power load bus
  		bus1.setLoadCode(AclfLoadCode.NON_LOAD);


  		DStab3PBus bus2 = ThreePhaseObjectFactory.create3PAclfBus("Bus2", net);
  		bus2.setAttributes("13.8 V feeder bus 2", "");
  		bus2.setBaseVoltage(13800.0);
  		// set the bus to a non-generator bus
  		bus2.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus2.setLoadCode(AclfLoadCode.NON_LOAD);


  		DStab3PBus bus3 = ThreePhaseObjectFactory.create3PAclfBus("Bus3", net);
  		bus3.setAttributes("13.8 V feeder bus 3", "");
  		bus3.setBaseVoltage(13800.0);
  		// set the bus to a non-generator bus
  		bus3.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus3.setLoadCode(AclfLoadCode.CONST_P);
//  		Load3Phase load1 = new Load3PhaseImpl();
//  		load1.set3PhaseLoad(new Complex3x1(new Complex(0.5,0.1),new Complex(0.5,0.1),new Complex(0.5,0.1)));
//  		bus3.getThreePhaseLoadList().add(load1);
  		//bus3.setLoadPQ(new Complex(0.5,0.1));


  		DStab3PBus bus4 = ThreePhaseObjectFactory.create3PAclfBus("Bus4", net);
  		bus4.setAttributes("13.8 V feeder bus 4", "");
  		bus4.setBaseVoltage(13800.0);
  		// set the bus to a non-generator bus
  		bus4.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus4.setLoadCode(AclfLoadCode.CONST_P);

  		bus4.setLoadP(1);
  		bus4.setLoadQ(0.1);

//  		Load3Phase load2 = new Load3PhaseImpl();
//  		load2.set3PhaseLoad(new Complex3x1(new Complex(1,0.1),new Complex(1,0.1),new Complex(1,0.1)));
//  		bus4.getThreePhaseLoadList().add(load2);


		DStab3PBranch Line1_2 = ThreePhaseObjectFactory.create3PBranch("Bus1", "Bus2", "0", net);
		Line1_2.setBranchCode(AclfBranchCode.LINE);
		Line1_2.setZ( new Complex( 0.0, 0.04 ));
		Line1_2.setZ0( new Complex(0.0, 0.08 ));


		DStab3PBranch Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus2", "Bus3", "0", net);
		Line2_3.setBranchCode(AclfBranchCode.LINE);
		Line2_3.setZ( new Complex( 0.0, 0.04 ));
		Line2_3.setZ0( new Complex(0.0, 0.08 ));


		DStab3PBranch Line2_4 = ThreePhaseObjectFactory.create3PBranch("Bus2", "Bus4", "0", net);
		Line2_4.setBranchCode(AclfBranchCode.LINE);
		Line2_4.setZ( new Complex( 0.0, 0.04 ));
		Line2_4.setZ0( new Complex(0.0, 0.08 ));



		//////////////////transformers///////////////////////////////////////////

//		Branch3Phase xfr5_10 = ThreePhaseObjectFactory.create3PBranch("Bus5", "Bus10", "0", dsNet);
//		xfr5_10.setBranchCode(AclfBranchCode.XFORMER);
//		xfr5_10.setZ( new Complex( 0.0, 0.08 ));
//		xfr5_10.setZ0( new Complex(0.0, 0.08 ));
//
//
//		AcscXformer xfr0 = acscXfrAptr.apply(xfr5_10);
//		xfr0.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
//		xfr0.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
//
//
//		Branch3Phase xfr10_11 = ThreePhaseObjectFactory.create3PBranch("Bus10", "Bus11", "0", dsNet);
//		xfr10_11.setBranchCode(AclfBranchCode.XFORMER);
//		xfr10_11.setZ( new Complex( 0.0, 0.06 ));
//		xfr10_11.setZ0( new Complex(0.0, 0.06 ));
//
//		AcscXformer xfr1 = acscXfrAptr.apply(xfr10_11);
//		xfr1.setFromConnectGroundZ(XfrConnectCode.DELTA, new Complex(0.0,0.0), UnitType.PU);
//		xfr1.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
//
//
//
//		Branch3Phase xfr11_12 = ThreePhaseObjectFactory.create3PBranch("Bus11", "Bus12", "0", dsNet);
//		xfr11_12.setBranchCode(AclfBranchCode.XFORMER);
//		xfr11_12.setZ( new Complex( 0.0, 0.025 ));
//		xfr11_12.setZ0( new Complex(0.0, 0.025 ));
//		xfr11_12.setToTurnRatio(1.01);
//		AcscXformer xfr2 = acscXfrAptr.apply(xfr11_12);
//		xfr2.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
//		xfr2.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
	    return net;
	}
}
