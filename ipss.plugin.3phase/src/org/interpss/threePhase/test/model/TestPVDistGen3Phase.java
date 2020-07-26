package org.interpss.threePhase.test.model;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.sparse.ISparseEqnComplexMatrix3x3;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab3PGenImpl;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.algo.DynamicEventProcessor3Phase;
import org.interpss.threePhase.dynamic.model.PVDistGen3Phase;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.interpss.threePhase.util.ThreePhaseAclfOutFunc;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

import com.interpss.DStabObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.adpter.AcscXformer;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.net.NetworkType;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineModelType;

public class TestPVDistGen3Phase {
	
	//@Test
	public void testYMatrix3Phase() throws InterpssException{
        IpssCorePlugin.init();
		
		DStabNetwork3Phase distNet = createDistNetNoDG();
		
		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		//distPFAlgo.orderDistributionBuses(true);
		
		assertTrue(distPFAlgo.powerflow());
		
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(distNet));
		
		
		assertTrue(distNet.initDStabNet());
		
		System.out.println(distNet.net2String());
		
		// check the YMatrix
		//NOTE: YMatrix formed during initDStabNet();

		ISparseEqnComplexMatrix3x3 ymatrix = distNet.getYMatrixABC();
		
		Complex3x3 yiibus1 = ymatrix.getA(0, 0);
		
		/*
		 * yijbus12 = 
				aa = (-0.0, 24.509803921568626),ab = (0.0, 0.0),ac = (0.0, 0.0)
				ba = (0.0, 0.0),bb = (-0.0, 24.509803921568626),bc = (0.0, 0.0)
				ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (-0.0, 24.509803921568626)
				
				yiibus2 = 
				aa = (1.428751220055992, -24.124469612386697),ab = (0.0, 0.0),ac = (0.0, 0.0)
				ba = (0.0, -6.462348535570529E-27),bb = (1.428751220055992, -24.124469612386697),bc = (-6.462348535570529E-27, 0.0)
				ca = (0.0, -6.462348535570529E-27),cb = (-6.462348535570529E-27, 0.0),cc = (1.4287512200559913, -24.124469612386697)
				
				yijbus21 = 
				aa = (-0.0, 24.509803921568626),ab = (0.0, 0.0),ac = (0.0, 0.0)
				ba = (0.0, 0.0),bb = (-0.0, 24.509803921568626),bc = (0.0, 0.0)
				ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (-0.0, 24.509803921568626)
		 */
		
		Complex3x3 yiibus2 = ymatrix.getA(1, 1);
		
		Complex3x3 yijbus12 = ymatrix.getA(0, 1);
		// bus1 side is the delta 11, leading 30 degrees related to wye side (bus2)
		System.out.println("yijbus12 = \n"+ yijbus12);
		
		System.out.println("yiibus2 = \n"+ yiibus2);
		
		Complex3x3 yijbus21 = ymatrix.getA(1, 0);
		
		System.out.println("yijbus21 = \n"+ yijbus21);
		
		//Complex3x3 yiiBus2Load = yiibus2.add(yijbus21);
		
		//System.out.println(yiiBus2Load.toString());
		
	}
	
	@Test
	public void testPVDistGen3Model() throws InterpssException{
		
		IpssCorePlugin.init();
		
		DStabNetwork3Phase distNet = createDistNetWithDG();
		
		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		//distPFAlgo.orderDistributionBuses(true);
		
		assertTrue(distPFAlgo.powerflow());
		
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(distNet));
		
		DStabGen gen = (DStabGen) distNet.getBus("Bus3").getContributeGen("PVGen");
		PVDistGen3Phase pv =(PVDistGen3Phase) gen.getDynamicGenDevice();
		assertTrue(pv!=null);
		
		//assertTrue(distNet.initDStabNet());
		pv.initStates(distNet.getBus("Bus3"));
		
		// TEST positiveSeqGenPQ
		assertTrue(pv.getPosSeqGenPQ().subtract(new Complex(0.5,0)).abs() <1.0E-6);
	
		// run one step
		
		
		// test the injectCurrent, check whether it can produce the same positive sequence genPQ
		
		
		Complex vpos = distNet.getBus("Bus3").getThreeSeqVoltage().b_1;
		
		//  Ip part equals to 0.5/vmag
		double ip= pv.getPosSeqGenPQ().getReal()/vpos.abs();
		
		// since reactive power is zero, the Iq part must be zero;
		
		assertTrue(pv.getPosSeqIpq().subtract(new Complex(ip,0)).abs()<1.0E-5);
		
		Complex ipos = pv.getPosSeqIpq();
		
		Complex calcPower = ipos.multiply(vpos.abs());
		
		assertTrue(pv.getPosSeqGenPQ().subtract(calcPower).abs()<1.0E-5);
		
		
		
	}
	
	//@Test
	public void testNetworkSolutionWithPVDistGen3Phase() throws InterpssException{
		
		IpssCorePlugin.init();
		
		DStabNetwork3Phase distNet = createDistNetWithDG();
		
		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		//distPFAlgo.orderDistributionBuses(true);
		
		assertTrue(distPFAlgo.powerflow());
		
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(distNet));
		
		DStabGen gen = (DStabGen) distNet.getBus("Bus3").getContributeGen("PVGen");
		PVDistGen3Phase pv =(PVDistGen3Phase) gen.getDynamicGenDevice();
		assertTrue(pv!=null);
		
		assertTrue(distNet.initDStabNet());
		
		
		
		// check the YMatrix
		//NOTE: YMatrix formed during initDStabNet();

		ISparseEqnComplexMatrix3x3 ymatrix = distNet.getYMatrixABC();
		
		// check ymatrix;
		System.out.println(ymatrix.toString());
				
		//TODO need to check the function of YiiABC();
				
		//MatrixOutputUtil.matrixToMatlabMFile("ymatrixABC.m", ymatrix.getSparseEqnComplex());
		
		//ymatrix.setBi(new Complex3x1, i);
		//check the network solution
		assertTrue(distNet.solveNetEqn());
		
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(distNet));
		
		
	}
	
	
	//@Test
	public void testNetworkSolutionNOPV() throws InterpssException{
		
		IpssCorePlugin.init();
		
		DStabNetwork3Phase distNet = createDistNetNoDG();
		
		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		//distPFAlgo.orderDistributionBuses(true);
		
		assertTrue(distPFAlgo.powerflow());
		
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(distNet));
		
		
		assertTrue(distNet.initDStabNet());
		
		
		
		// check the YMatrix
		//NOTE: YMatrix formed during initDStabNet();

		ISparseEqnComplexMatrix3x3 ymatrix = distNet.getYMatrixABC();
		
		Complex3x3 yiibus2 = ymatrix.getA(1, 1);
		
		System.out.println("yiibus2 ="+yiibus2.toString());
		
		// check ymatrix;
		System.out.println(ymatrix.toString());
				
		//TODO need to check the function of YiiABC();
				
		//MatrixOutputUtil.matrixToMatlabMFile("ymatrixABC.m", ymatrix.getSparseEqnComplex());
		
		//ymatrix.setBi(new Complex3x1, i);
		//check the network solution
		assertTrue(distNet.solveNetEqn());
//		assertTrue(distNet.solveNetEqn());
//		assertTrue(distNet.solveNetEqn());
//		assertTrue(distNet.solveNetEqn());
		
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(distNet));
		
		
	}
	
	/*
	 * ==================Distribtuion power flow results============
		
		Bus results: 
		Bus1,1.01,0.0,1.01,4.188790204786391,1.01,2.0943951023931953,1.0100 + j0.0000  -0.5050 + j-0.87469  -0.5050 + j0.87469
		Bus2,1.0250634873088649,-0.5614946229613161,1.0256661283173656,3.6386959124997253,1.0254803079127308,1.5405036404753871,0.86768 + j-0.5458  -0.90153 + j-0.48912  0.03106 + j1.02501
		Bus3,1.0247543585963361,-0.5424625749134737,1.025579290014406,3.6577169143658015,1.025319427432163,1.5595283176961696,0.87764 + j-0.52903  -0.89199 + j-0.50614  0.01155 + j1.02525
		
		Branch results: 
		Bus1->Bus2(0), Iabc (from) = 0.83441 + j-0.22313  -0.45888 + j-0.5949  -0.37552 + j0.81803, Iabc (to) = 0.76743 + j-0.6126  -0.64947 + j-0.2337  0.12975 + j0.7765
		Bus2->Bus3(0), Iabc (from) = -0.41928 + j0.24913  0.42539 + j0.23854  -0.00611 + j-0.48767, Iabc (to) = -0.41928 + j0.24913  0.42539 + j0.23854  -0.00611 + j-0.48767

	 */
	
	//@Test
	public void testTwoBusLoadOnlyDstabSim() throws InterpssException{
		
        IpssCorePlugin.init();
		
		DStabNetwork3Phase distNet = createDistNetNoDG();
		
		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		//distPFAlgo.orderDistributionBuses(true);
		
		assertTrue(distPFAlgo.powerflow());
		
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(distNet));
		
		DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
				distNet, IpssCorePlugin.getMsgHub());
			
	
	  	
	  	dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.5);
	    //dstabAlgo.setRefMachine(net.getMachine("Bus3-mach1"));
		//distNet.addDynamicEvent(create3PhaseFaultEvent("Bus2",distNet,0.2,0.05),"3phaseFault@Bus2");
        
		
		StateMonitor sm = new StateMonitor();
		//sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus2","Bus1"});
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
				
	  	if(dstabAlgo.initialization()){
	  		System.out.println(ThreePhaseAclfOutFunc.busLfSummary(distNet));
	  		System.out.println(distNet.getMachineInitCondition());
	  	
	  		dstabAlgo.performSimulation();
	  	}
	  	System.out.println(sm.toCSVString(sm.getBusAngleTable()));
	  	System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	  	MonitorRecord rec1 = sm.getBusVoltTable().get("Bus2").get(1);
	  	MonitorRecord rec20 = sm.getBusVoltTable().get("Bus2").get(20);
	  	assertTrue(Math.abs(rec1.getValue()-rec20.getValue())<1.0E-4);
	}
	
	
	@Test
	public void testThreeBusWithPVDstabSim() throws InterpssException{
		
        IpssCorePlugin.init();
		
		DStabNetwork3Phase distNet = createDistNetWithDG();
		
		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		//distPFAlgo.orderDistributionBuses(true);
		
		assertTrue(distPFAlgo.powerflow());
		
		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(distNet));
		
		DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
				distNet, IpssCorePlugin.getMsgHub());
			
	
	  	
	  	dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.5);
	    //dstabAlgo.setRefMachine(net.getMachine("Bus3-mach1"));
		distNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",distNet, SimpleFaultCode.GROUND_3P, new Complex(0,0), null, 0.2, 0.05),"fault@bus2");
        
		
		StateMonitor sm = new StateMonitor();
		//sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus3","Bus2","Bus1"});
		
		sm.addDynDeviceMonitor(DynDeviceType.PVGen, "PVGen3Phase_1@Bus3");
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);
		
		dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
				
	  	if(dstabAlgo.initialization()){
	  		System.out.println(ThreePhaseAclfOutFunc.busLfSummary(distNet));
	  		System.out.println(distNet.getMachineInitCondition());
	  	
	  		dstabAlgo.performSimulation();
	  	}
	  	
	  	//System.out.println(sm.toCSVString(sm.getBusAngleTable()));
	  	System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		//FileUtil.writeText2File("C:\\results.csv", sm.toCSVString(sm.getBusVoltTable()));

	  	MonitorRecord rec1 = sm.getBusVoltTable().get("Bus2").get(1);
	  	MonitorRecord rec20 = sm.getBusVoltTable().get("Bus2").get(20);
	  	assertTrue(Math.abs(rec1.getValue()-rec20.getValue())<1.0E-4);
	  	
	  	System.out.println(sm.toCSVString(sm.getPvGenPTable()));
	  
	  	System.out.println(sm.toCSVString(sm.getPvGenQTable()));
	  	System.out.println(sm.toCSVString(sm.getPvGenIpTable()));
	  	
	  	
	  	MonitorRecord pvGenP1 = sm.getPvGenPTable().get("PVGen3Phase_1@Bus3").get(1);
	  	MonitorRecord pvGenP20 = sm.getPvGenPTable().get("PVGen3Phase_1@Bus3").get(20);
	  	assertTrue(Math.abs(pvGenP1.getValue()-pvGenP20.getValue())<2.0E-4);
	  	
	  	MonitorRecord pvGenQ1 = sm.getPvGenQTable().get("PVGen3Phase_1@Bus3").get(1);
	  	MonitorRecord pvGenQ20 = sm.getPvGenQTable().get("PVGen3Phase_1@Bus3").get(20);
	  	assertTrue(Math.abs(pvGenQ1.getValue()-pvGenQ20.getValue())<2.0E-4);
	  	
	}
	
	private DStabNetwork3Phase createDistNetWithDG() throws InterpssException{
		
		
		
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
			
			DStab3PGen constantGen = ThreePhaseObjectFactory.create3PGenerator("Source");
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
			bus2.setAttributes("13.8 V feeder bus 2", "");
			bus2.setBaseVoltage(13800.0);
			// set the bus to a non-generator bus
			bus2.setGenCode(AclfGenCode.NON_GEN);
			// set the bus to a constant power load bus
			bus2.setLoadCode(AclfLoadCode.CONST_P);
			
			DStab3PLoad load1 = new DStab3PLoadImpl();
			load1.set3PhaseLoad(new Complex3x1(new Complex(1.5,0.1),new Complex(1.5,0.1),new Complex(1.5,0.1)));
			bus2.getThreePhaseLoadList().add(load1);

			
			
			DStab3PBus bus3 = ThreePhaseObjectFactory.create3PDStabBus("Bus3", net);
			bus3.setAttributes("13.8 V feeder bus 3", "");
			bus3.setBaseVoltage(13800.0);
			// set the bus to a non-generator bus
			bus3.setGenCode(AclfGenCode.GEN_PQ);
			// set the bus to a constant power load bus
			bus3.setLoadCode(AclfLoadCode.NON_LOAD);
			
			
			DStab3PGen gen1 = new DStab3PGenImpl();
			gen1.setParentBus(bus3);
			gen1.setId("PVGen");
			gen1.setGen(new Complex(0.5,0));  // total gen power, system mva based
			
			bus3.getThreePhaseGenList().add(gen1);
			
			gen1.setMvaBase(100); // for dynamic simulation only
			gen1.setPosGenZ(new Complex(0,1.0E-1));   // assuming open-circuit
			gen1.setNegGenZ(new Complex(0,1.0E-1));
			gen1.setZeroGenZ(new Complex(0,1.0E-1));
			//create the PV Distributed gen model
			PVDistGen3Phase pv = new PVDistGen3Phase(gen1);
			pv.setId("1");
			
			
			

			
			DStab3PBranch xfr1_2 = ThreePhaseObjectFactory.create3PBranch("Bus1", "Bus2", "0", net);
			xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
			xfr1_2.setToTurnRatio(1.02);
			xfr1_2.setZ( new Complex( 0.0, 0.04 ));
			//xfr1_2.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex( 0.0, 0.04 )));
			//xfr1_2.setZ0( new Complex(0.0, 0.4 ));
		
		
			AcscXformer xfr0 = acscXfrAptr.apply(xfr1_2);
			xfr0.setFromConnectGroundZ(XfrConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
			xfr0.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
			
			// for testing connection and from-to relationship only
	//		xfr0.setToConnectGroundZ(XfrConnectCode.DELTA, new Complex(0.0,0.0), UnitType.PU);
	//		xfr0.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
			
			DStab3PBranch Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus2", "Bus3", "0", net);
			Line2_3.setBranchCode(AclfBranchCode.LINE);
			Line2_3.setZ( new Complex( 0.0, 0.04 ));
			Line2_3.setZ0( new Complex(0.0, 0.08 ));
				
			
			
			
	    return  net;
	}
	
private DStabNetwork3Phase createDistNetNoDG() throws InterpssException{
		
		
		
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
			
			DStab3PGen constantGen = ThreePhaseObjectFactory.create3PGenerator("Source");
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
			bus2.setAttributes("13.8 V feeder bus 2", "");
			bus2.setBaseVoltage(13800.0);
			// set the bus to a non-generator bus
			bus2.setGenCode(AclfGenCode.NON_GEN);
			// set the bus to a constant power load bus
			bus2.setLoadCode(AclfLoadCode.CONST_P);
			
			DStab3PLoad load1 = new DStab3PLoadImpl();
			load1.set3PhaseLoad(new Complex3x1(new Complex(1.5,0.1),new Complex(1.5,0.1),new Complex(1.5,0.1)));
			bus2.getThreePhaseLoadList().add(load1);

			
			
//		Bus3Phase bus3 = ThreePhaseObjectFactory.create3PDStabBus("Bus3", net);
//			bus3.setAttributes("13.8 V feeder bus 3", "");
//			bus3.setBaseVoltage(13800.0);
//			// set the bus to a non-generator bus
//			bus3.setGenCode(AclfGenCode.GEN_PQ);
//			// set the bus to a constant power load bus
//			bus3.setLoadCode(AclfLoadCode.NON_LOAD);
//			
//			
//			Gen3Phase gen1 = new Gen3PhaseImpl();
//			gen1.setParentBus(bus3);
//			gen1.setId("PVGen");
//			gen1.setGen(new Complex(0.5,0));  // total gen power, system mva based
//			
//			bus3.getThreePhaseGenList().add(gen1);
//			
//			gen1.setMvaBase(100); // for dynamic simulation only
//			gen1.setPosGenZ(new Complex(0,1.0E-1));   // assuming open-circuit
//			gen1.setNegGenZ(new Complex(0,1.0E-1));
//			gen1.setZeroGenZ(new Complex(0,1.0E-1));
//			//create the PV Distributed gen model
//			PVDistGen3Phase pv = new PVDistGen3Phase(gen1);
			
			
			

			
			DStab3PBranch xfr1_2 = ThreePhaseObjectFactory.create3PBranch("Bus1", "Bus2", "0", net);
			xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
			xfr1_2.setToTurnRatio(1.02);
			xfr1_2.setZ( new Complex( 0.0, 0.04 ));
			//xfr1_2.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex( 0.0, 0.04 )));
			//xfr1_2.setZ0( new Complex(0.0, 0.4 ));
		
		
			AcscXformer xfr0 = acscXfrAptr.apply(xfr1_2);
			xfr0.setFromConnectGroundZ(XfrConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
			xfr0.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
			
			// for testing connection and from-to relationship only
	//		xfr0.setToConnectGroundZ(XfrConnectCode.DELTA, new Complex(0.0,0.0), UnitType.PU);
	//		xfr0.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
			
//			Branch3Phase Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus2", "Bus3", "0", net);
//			Line2_3.setBranchCode(AclfBranchCode.LINE);
//			Line2_3.setZ( new Complex( 0.0, 0.04 ));
//			Line2_3.setZ0( new Complex(0.0, 0.08 ));
				
			
			
			
	    return  net;
	}

}


