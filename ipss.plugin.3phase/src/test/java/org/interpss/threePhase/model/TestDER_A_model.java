package org.interpss.threePhase.model;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab3PGenImpl;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.algo.DynamicEventProcessor3Phase;
import org.interpss.threePhase.dynamic.model.PVDistGen3Phase;
import org.interpss.threePhase.dynamic.model.impl.MachModel_DER_A_v4;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.interpss.threePhase.util.ThreePhaseAclfOutFunc;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.core.acsc.adpter.AcscXformerAdapter;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.net.NetworkType;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineModelType;
import com.interpss.dstab.mach.RoundRotorMachine;

public class TestDER_A_model {

	@Test
	public void testThreeBusWithPVDstabSim() throws InterpssException{

        IpssCorePlugin.init();

        DStabNetwork3Phase distNet = createDistNetWithDG();
		//DStabNetwork3Phase distNet = create3BusDistNetOnlyDG();

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
		distNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus4",distNet, SimpleFaultCode.GROUND_3P, new Complex(0,0), null, 0.1, 0.05),"fault@bus4");


		StateMonitor sm = new StateMonitor();
		//sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus4", "Bus3","Bus2","Bus1"});
		sm.addBranchStdMonitor("Bus1->Bus2(0)");

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
	  	System.out.println(sm.toCSVString(sm.getPvGenPTable()));
	  	//System.out.println(sm.toCSVString(sm.getPvGenQTable()));
	  	//System.out.println(sm.toCSVString(sm.getPvGenIpTable()));
	  	//System.out.println(sm.toCSVString(sm.getPvGenIqTable()));

		MonitorRecord rec1 = sm.getBusVoltTable().get("Bus2").get(1);
	  	MonitorRecord rec18 = sm.getBusVoltTable().get("Bus2").get(18);
	  	assertTrue(Math.abs(rec1.getValue()-rec18.getValue())<1.0E-4);

		MonitorRecord rec98 = sm.getBusVoltTable().get("Bus2").get(98);
		assertTrue(Math.abs(rec98.getValue()-rec18.getValue())<1.0E-4);
		
		MonitorRecord pvgenp_1 = sm.getPvGenPTable().get("PVGen3Phase_1@Bus3").get(1);
		MonitorRecord pvgenp_18 = sm.getPvGenPTable().get("PVGen3Phase_1@Bus3").get(18);
		assertTrue(Math.abs(pvgenp_1.getValue()-pvgenp_18.getValue())<1.0E-4);

		MonitorRecord pvgenp_98 = sm.getPvGenPTable().get("PVGen3Phase_1@Bus3").get(98);
		assertTrue(Math.abs(pvgenp_98.getValue()-pvgenp_18.getValue())<1.0E-3);
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

		DStab3PBus bus2 = ThreePhaseObjectFactory.create3PDStabBus("Bus2", net);
		bus2.setAttributes("13.8 V feeder bus 2", "");
		bus2.setBaseVoltage(13800.0);
		// set the bus to a non-generator bus
		bus2.setGenCode(AclfGenCode.NON_GEN);
		// set the bus to a constant power load bus
		bus2.setLoadCode(AclfLoadCode.CONST_P);

		DStab3PBus bus3 = ThreePhaseObjectFactory.create3PDStabBus("Bus3", net);
		bus3.setAttributes("13.8 V feeder bus 3", "");
		bus3.setBaseVoltage(13800.0);
		// set the bus to a non-generator bus
		bus3.setGenCode(AclfGenCode.GEN_PV);
		// set the bus to a constant power load bus
		bus3.setLoadCode(AclfLoadCode.CONST_P);

		DStab3PBus bus4 = ThreePhaseObjectFactory.create3PDStabBus("Bus4", net);
		bus4.setAttributes("13.8 V feeder bus 4", "");
		bus4.setBaseVoltage(13800.0);
		bus4.setGenCode(AclfGenCode.NON_GEN);
		// set the bus to a constant power load bus
		bus4.setLoadCode(AclfLoadCode.CONST_P);


		DStab3PLoad load1 = new DStab3PLoadImpl();
		load1.set3PhaseLoad(new Complex3x1(new Complex(1.0,0.1),new Complex(1.0,0.1),new Complex(1.0,0.1)));
		bus2.getThreePhaseLoadList().add(load1);

		DStab3PLoad load2 = new DStab3PLoadImpl();
		load2.set3PhaseLoad(new Complex3x1(new Complex(0.1,0.0),new Complex(0.1,0.0),new Complex(0.1,0.0)));
		bus3.getThreePhaseLoadList().add(load2);

		DStab3PLoad load3 = new DStab3PLoadImpl();
		load3.set3PhaseLoad(new Complex3x1(new Complex(0.4,0.0),new Complex(0.4,0.0),new Complex(0.4,0.0)));
		bus4.getThreePhaseLoadList().add(load3);


		DStab3PBranch xfr1_2 = ThreePhaseObjectFactory.create3PBranch("Bus1", "Bus2", "0", net);
		xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
		xfr1_2.setToTurnRatio(1.0);
		xfr1_2.setZ( new Complex( 0.0, 0.04 ));
		//xfr1_2.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex( 0.0, 0.04 )));
		//xfr1_2.setZ0( new Complex(0.0, 0.4 ));

		AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr1_2);
		xfr0.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
		xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);


		DStab3PBranch Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus2", "Bus3", "0", net);
		Line2_3.setBranchCode(AclfBranchCode.LINE);
		Line2_3.setZ( new Complex( 0.0, 0.04 ));
		Line2_3.setZ0( new Complex(0.0, 0.08 ));

		DStab3PBranch Line2_4 = ThreePhaseObjectFactory.create3PBranch("Bus2", "Bus4", "0", net);
		Line2_4.setBranchCode(AclfBranchCode.LINE);
		Line2_4.setZ( new Complex( 0.0, 0.04 ));
		Line2_4.setZ0( new Complex(0.0, 0.08 ));


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

		// RoundRotorMachine mach = (RoundRotorMachine)DStabObjectFactory.
		// createMachine("MachId", "MachName", MachineModelType.EQ11_ED11_ROUND_ROTOR, net, "Bus1", "Source");
		// mach.setRating(100, UnitType.mVA, net.getBaseKva());
		// mach.setRatedVoltage(69000.0);
		// mach.calMultiFactors();
		// mach.setH(50000.0);
		// mach.setD(0.00);
		// mach.setRa(0.000);
		// mach.setXd(2.1);
		// mach.setXq(0.05); // match with set gen Z
		// mach.setXd1(0.05); // match with set gen Z
		// mach.setXq1(0.05); // cHANGED
		// mach.setXd11(0.05); // match with set gen Z
		// mach.setXl(0.03);
		// mach.setTd01(5.6);
		// mach.setTq01(0.75);
		// mach.setTd011(0.03);
		// mach.setTq011(0.05);
		// mach.setSe100(0.0);   // no saturation
		// mach.setSe120(0.0);
		// mach.setXq11(0.05); // match with set gen Z
		//mach.setSliner(2.0);  // no saturation


		DStab3PGen gen1 = new DStab3PGenImpl();
		gen1.setParentBus(bus3);
		gen1.setId("PVGen");
		gen1.setGen(new Complex(0.5,0.0));  // total gen power, system mva based

		bus3.getThreePhaseGenList().add(gen1);
		bus3.getContributeGenList().add(gen1);

		gen1.setMvaBase(100); // for dynamic simulation only
		gen1.setPosGenZ(new Complex(0,2.5E-1));   // assuming open-circuit
		gen1.setNegGenZ(new Complex(0,2.5E-1));
		gen1.setZeroGenZ(new Complex(0,2.5E-1));

		MachModel_DER_A_v4 pv = new MachModel_DER_A_v4(gen1);
		pv.setId("1");
		pv.initStates(bus3);
		pv.enableVoltControl();
		pv.enablePowerFreqControl();
		pv.isNotMultiNetMode();
		//pv.setDebugMode(true);

		// //create the PV Distributed gen model using PVD1
		// PVDistGen3Phase pv = new PVDistGen3Phase(gen1);
		// pv.setId("1");


	    return  net;
	}



}









