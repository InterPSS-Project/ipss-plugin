package org.interpss._3phase.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.IEEEFeederLineCode;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.algo.DynamicEventProcessor3Phase;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.interpss.threePhase.util.ThreePhaseAclfOutFunc;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.net.NetworkType;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.dstab.devent.LoadChangeEventType;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineModelType;

public class Test8BusFeeder {

	//@Test
	public void test_PowerFlow() throws InterpssException{
		 IpssCorePlugin.init();
		double baseVolt = 12470;
		int feederBusNum = 8;
		double totalLoad = 100;
		double loadPF = 0.90;
		double loadUnbalanceFactor = 0.0;
		double [] loadDistribution = new double[]{0.25,0.20,0.15,0.15,0.1,0.1,0.05};
		double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.0,1.5,2,2}; // unit in mile
		//double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.5,0.5,0.5, 1}; // unit in mile
		DStabNetwork3Phase net = createFeeder(baseVolt,feederBusNum,totalLoad,loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);

		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(net);
		//distPFAlgo.orderDistributionBuses(true);

		assertTrue(distPFAlgo.powerflow());


		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(net));


	  	System.out.println(ThreePhaseAclfOutFunc.busLfSummary(net));

	  	assertEquals(net.getBus("Bus2").get3PhaseVotlages().a_0.abs(),1.008,1.0E-3);
	  	assertEquals(net.getBus("Bus2").get3PhaseVotlages().b_1.abs(),1.012,1.0E-3);
	  	assertEquals(net.getBus("Bus2").get3PhaseVotlages().c_2.abs(),1.008,1.0E-3);
	}

	@Test
	public void test_DStab_Simu() throws InterpssException{
		 IpssCorePlugin.init();
		double baseVolt = 12470;
		int feederBusNum = 8;
		double totalLoad = 100;
		double loadPF = 0.90;
		double loadUnbalanceFactor = 0.0;
		double [] loadDistribution = new double[]{0.25,0.20,0.15,0.15,0.1,0.1,0.05};
		double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.0,1.5,2,2}; // unit in mile
		//double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.5,0.5,0.5, 1}; // unit in mile
		DStabNetwork3Phase net = createFeeder(baseVolt,feederBusNum,totalLoad,loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);

		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(net);
		//distPFAlgo.orderDistributionBuses(true);

		assertTrue(distPFAlgo.powerflow());


		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(net));


		DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
				net, IpssCorePlugin.getMsgHub());



	  	dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.5);
	    //dstabAlgo.setRefMachine(net.getMachine("Bus3-mach1"));
		//distNet.addDynamicEvent(create3PhaseFaultEvent("Bus2",distNet,0.2,0.05),"3phaseFault@Bus2");

		net.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus8",
				net, LoadChangeEventType.FIXED_TIME, -0.2, .2),
				"LoadReduce20%@Bus8");

		StateMonitor sm = new StateMonitor();
		//sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus2","Bus1"});
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);

		dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());

	  	if(dstabAlgo.initialization()){
	  		System.out.println(ThreePhaseAclfOutFunc.busLfSummary(net));
	  		System.out.println(net.getMachineInitCondition());

	  		dstabAlgo.performSimulation();
	  	}
	  	System.out.println(sm.toCSVString(sm.getBusAngleTable()));
	  	System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	  	MonitorRecord rec1 = sm.getBusVoltTable().get("Bus2").get(1);
	  	MonitorRecord rec20 = sm.getBusVoltTable().get("Bus2").get(20);
	  	assertTrue(Math.abs(rec1.getValue()-rec20.getValue())<1.0E-4);
	}


	//@Test
	public void testPowerFlow_multi_islands() throws InterpssException{
		 IpssCorePlugin.init();
		double baseVolt = 12470;
		int feederBusNum = 8;
		double totalLoad = 100;
		double loadPF = 0.90;
		double loadUnbalanceFactor = 0.0;
		double [] loadDistribution = new double[]{0.25,0.20,0.15,0.0,0.1,0.1,0.2};
		double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.0,1.5,2,2}; // unit in mile
		//double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.5,0.5,0.5, 1}; // unit in mile
		DStabNetwork3Phase net = createFeeder(baseVolt,feederBusNum,totalLoad,loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);



		BaseDStabBus bus5= net.getBus("Bus5");

		DStab3PGen constantGen = ThreePhaseObjectFactory.create3PGenerator("Gen@Bus5");

		constantGen.setGen(new Complex(0.4, 0.2));
		constantGen.setMvaBase(100);
		constantGen.setPosGenZ(new Complex(0.0,0.05));
		constantGen.setNegGenZ(new Complex(0.0,0.05));
		constantGen.setZeroGenZ(new Complex(0.0,0.05));
		bus5.getContributeGenList().add(constantGen);


		EConstMachine mach = (EConstMachine)DStabObjectFactory.
				createMachine("MachId", "MachName", MachineModelType.ECONSTANT, net, "Bus5", "Gen@Bus5");

		mach.setRating(100, UnitType.mVA, net.getBaseKva());
		mach.setRatedVoltage(baseVolt);
		mach.setH(50000.0);
		mach.setXd1(0.05);

		//turn off line between bus 4 and 5
		net.getBranch("Bus4", "Bus5", "0").setStatus(false);
		net.getBranch("Bus7", "Bus8", "0").setStatus(false);

		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(net);
		//distPFAlgo.orderDistributionBuses(true);

		assertTrue(distPFAlgo.powerflow());


		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(net));
		System.out.println(ThreePhaseAclfOutFunc.busLfSummary(net));


		DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
				net, IpssCorePlugin.getMsgHub());



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
	  		System.out.println(ThreePhaseAclfOutFunc.busLfSummary(net));
	  		System.out.println(net.getMachineInitCondition());

	  		dstabAlgo.performSimulation();
	  	}
	  	System.out.println(sm.toCSVString(sm.getBusAngleTable()));
	  	System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	  	MonitorRecord rec1 = sm.getBusVoltTable().get("Bus2").get(1);
	  	MonitorRecord rec20 = sm.getBusVoltTable().get("Bus2").get(20);
	  	assertTrue(Math.abs(rec1.getValue()-rec20.getValue())<1.0E-4);
	}


	/**
	 * The first bus is feeder sending end, no load is connected; all the loads are connected at bus [2,...BusNum];
	 *
	 * The base case of the feeder is assumed to serve 8 MW load, feeder impedances are re-scaled based on the totalMW
	 * @param totalMW
	 * @return
	 * @throws InterpssException
	 */
public DStabNetwork3Phase createFeeder(double baseVolt, int BusNum, double totalMW, double loadPF, double[] loadPercentAry, double loadUnbalanceFactor, double[] sectionLength) throws InterpssException{

	    double scaleFactor = totalMW;
	    double zscaleFactor =  totalMW/8.0;
	    double q2pfactor = Math.tan(Math.acos(loadPF));

	    DStabNetwork3Phase net = ThreePhaseObjectFactory.create3PhaseDStabNetwork();


		// identify this is a distribution network
		net.setNetworkType(NetworkType.DISTRIBUTION);

		DStab3PBus bus1 = ThreePhaseObjectFactory.create3PDStabBus("Bus1", net);
		bus1.setAttributes("feeder source", "");
		bus1.setBaseVoltage(baseVolt);
		// set the bus to a non-generator bus
		bus1.setGenCode(AclfGenCode.SWING);
		// set the bus to a constant power load bus
		bus1.setLoadCode(AclfLoadCode.NON_LOAD);
		bus1.setVoltage(new Complex(1.02,0));

		DStab3PGen constantGen = ThreePhaseObjectFactory.create3PGenerator("Source");
		constantGen.setMvaBase(100);
		constantGen.setPosGenZ(new Complex(0.0,0.05));
		constantGen.setNegGenZ(new Complex(0.0,0.05));
		constantGen.setZeroGenZ(new Complex(0.0,0.05));
		bus1.getContributeGenList().add(constantGen);


		EConstMachine mach = (EConstMachine)DStabObjectFactory.
				createMachine("MachId", "MachName", MachineModelType.ECONSTANT, net, "Bus1", "Source");

		mach.setRating(100, UnitType.mVA, net.getBaseKva());
		mach.setRatedVoltage(baseVolt);
		mach.setH(50000.0);
		mach.setXd1(0.05);



		for(int i =2;i<=BusNum;i++){
			DStab3PBus bus = ThreePhaseObjectFactory.create3PDStabBus("Bus"+i, net);
			bus.setAttributes("feeder bus "+i, "");
			bus.setBaseVoltage(baseVolt);
			// set the bus to a non-generator bus
			bus.setGenCode(AclfGenCode.GEN_PQ);
			// set the bus to a constant power load bus
			bus.setLoadCode(AclfLoadCode.CONST_P);

			DStab3PLoad load1 = new DStab3PLoadImpl();
			Complex3x1 load3Phase = new Complex3x1(new Complex(0.01,0.01*q2pfactor),new Complex(0.01,0.01*q2pfactor).multiply(1-loadUnbalanceFactor),new Complex(0.01,0.01*q2pfactor).multiply(1+loadUnbalanceFactor));
			load1.set3PhaseLoad(load3Phase.multiply(scaleFactor*loadPercentAry[i-2]));
			bus.getThreePhaseLoadList().add(load1);

			if(i ==3 || i == 5 || i==7){
				DStab3PLoad Shuntload = new DStab3PLoadImpl();
				Complex3x1 shuntY = new Complex3x1(new Complex(0,-0.0005),new Complex(0.0,-0.0005),new Complex(0.0,-0.0005));
				Shuntload.set3PhaseLoad(shuntY.multiply(scaleFactor));
				bus.getThreePhaseLoadList().add(Shuntload);
			}

		}

		int k =0;
		for(int i =1;i<BusNum;i++){

			DStab3PBranch Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus"+i, "Bus"+(i+1), "0", net);
			Line2_3.setBranchCode(AclfBranchCode.LINE);


			Complex3x3 zabcActual = IEEEFeederLineCode.zMtx601;
			if(k>=3 && k<5) {
				zabcActual =  IEEEFeederLineCode.zMtx602;
			} else if (k>=5) {
				zabcActual =  IEEEFeederLineCode.zMtx606;
			}

			zabcActual = zabcActual.multiply(sectionLength[k]/zscaleFactor);

			double zbase = net.getBus("Bus"+i).getBaseVoltage()*net.getBus("Bus"+i).getBaseVoltage()/net.getBaseMva()/1.0E6;
			Line2_3.setZabc(zabcActual.multiply(1/zbase));


			k++;
		}



		return net;



	}

}
