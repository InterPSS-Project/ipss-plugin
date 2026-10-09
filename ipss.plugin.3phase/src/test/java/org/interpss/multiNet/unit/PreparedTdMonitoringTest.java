package org.interpss.multiNet.unit;
import static org.interpss.threePhase.util.ThreePhaseUtilFunction.threePhaseXfrAptr;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.multiNet.algo.MultiNet3Ph3SeqDStabSimuHelper;
import org.interpss.multiNet.algo.MultiNet3Ph3SeqDStabSolverImpl;
import org.interpss.multiNet.algo.MultiNet3Ph3SeqDynEventProcessor;
import org.interpss.multiNet.algo.MultiNet3PhPosSeqDynEventProcessor;
import org.interpss.multiNet.algo.SubNetworkProcessor;
import org.interpss.multiNet.algo.T3seqD3phaseMultiNetDStabSolverImpl;
import org.interpss.multiNet.algo.TposseqD3phaseMultiNetDStabSolverImpl;
import org.interpss.multiNet.algo.powerflow.TDMultiNetPowerflowAlgorithm;
import org.interpss.multiNet.algo.powerflow.TposSeqD3PhaseMultiNetPowerflowAlgorithm;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.algo.DynamicEventProcessor3Phase;
import org.interpss.threePhase.dynamic.impl.DStabNetwork3phaseImpl;
import org.interpss.threePhase.dynamic.model.impl.SinglePhaseACMotor;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.interpss.threePhase.powerflow.impl.DistributionPowerFlowAlgorithmImpl;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.threephase.Static3PXformer;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.net.NetworkType;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.dstab.devent.DynamicSimuEvent;
import com.interpss.dstab.devent.DynamicSimuEventType;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineModelType;


import static org.junit.jupiter.api.Assertions.*;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.datatype.DStabSimuEvent;

public class PreparedTdMonitoringTest {
    private StateMonitor simulate(boolean sequence, boolean primitive) throws Exception {
        return simulate(sequence, primitive, null);
    }
    private StateMonitor simulate(boolean sequence, boolean primitive, StateMonitor script) throws Exception {
        IpssCorePlugin.init();
        var net=create3BusSys();
        var processor=new SubNetworkProcessor(net);
        processor.addSubNetInterfaceBranch("Bus3->Bus2(0)",false);
        assertTrue(processor.splitFullSystemIntoSubsystems(true));
        processor.set3PhaseSubNetByBusId("Bus1");
        if(sequence) assertTrue(new TDMultiNetPowerflowAlgorithm(net,processor).powerflow());
        else assertTrue(new TposSeqD3PhaseMultiNetPowerflowAlgorithm(net,processor).powerflow());
        var algo=DStabObjectFactory.createDynamicSimuAlgorithm(net);
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuStepSec(.005);algo.setTotalSimuTimeSec(.2);algo.setOutputPerSteps(2);
        var monitor=new StateMonitor();monitor.addBusStdMonitor("Bus3");monitor.addGeneratorStdMonitor("1");
        if(primitive) monitor.prepareMonitoring(true);
        algo.setSimuOutputHandler(monitor);
        if(script != null) algo.setScriptOutputHandler(script);
        var helper=new MultiNet3Ph3SeqDStabSimuHelper(net,processor);
        if(sequence) {
            var solver=new T3seqD3phaseMultiNetDStabSolverImpl(algo,helper);solver.setTheveninEquivFlag(true);solver.setDistNetSolvedByPowerflowFlag(false);algo.setSolver(solver);
            algo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(helper));
        } else {
            var solver=new TposseqD3phaseMultiNetDStabSolverImpl(algo,helper);solver.setTheveninEquivFlag(true);solver.setDistNetSolvedByPowerflowFlag(false);algo.setSolver(solver);
            algo.setDynamicEventHandler(new MultiNet3PhPosSeqDynEventProcessor(helper));
        }
        assertTrue(algo.initialization());if(sequence) for(int step=0;step<40;step++) assertTrue(algo.solveDEqnStep(true));return monitor;
    }
    @Test void positiveSequenceInitializationMatchesLegacy() throws Exception { compare(false); }
    @Test void threeSequenceBaselineMatchesLegacy() throws Exception { compare(true); }
    private void compare(boolean sequence) throws Exception {
        var legacy=simulate(sequence,false);var primitive=simulate(sequence,true);
        var samples=primitive.getPrimitiveSamples(StateMonitor.ChannelCategory.BUS_OR_MACHINE,"Bus3",DStabOutSymbol.OUT_SYMBOL_BUS_VMAG);
        var old=legacy.getBusVoltTable().get("Bus3");assertEquals(old.size(),samples.values().length);
        for(int i=0;i<samples.values().length;i++) {assertEquals(old.get(i).value,samples.values()[i],1e-10);assertEquals(old.get(i).t,samples.times()[i],1e-12);}
        var oldSpeed=legacy.getMachSpeedTable().get("1");var speed=primitive.getMachSpeedTable().get("1");
        assertFalse(speed.isEmpty());assertEquals(oldSpeed.size(),speed.size());
        for(var entry:oldSpeed.entrySet()) assertEquals(entry.getValue().value,speed.get(entry.getKey()).value,1e-12);
    }
    private static class CountingScript extends StateMonitor {
        int busEvents, machineEvents;
        @Override public boolean onSimuEventStatus(DStabSimuEvent event) {
            if(event.getType()==DStabSimuEvent.PlotStepBusStates) busEvents++;
            if(event.getType()==DStabSimuEvent.PlotStepMachineStates) machineEvents++;
            return true;
        }
    }
    @Test void preparedInitializationPreservesScriptConsumers() throws Exception {
        var script=new CountingScript();simulate(false,true,script);
        assertTrue(script.busEvents>=3);assertTrue(script.machineEvents>=1);
    }

private DStabNetwork3Phase create3BusSys() throws InterpssException{
		
		DStabNetwork3Phase net = new DStabNetwork3phaseImpl();

		double baseKva = 100000.0;
		
		// set system basekva for loadflow calculation
		net.setBaseKva(baseKva);
	  
	    //Bus 1
		DStab3PBus bus1 = ThreePhaseObjectFactory.create3PDStabBus("Bus1", net);
  		// set bus name and description attributes
  		bus1.setAttributes("Bus 1", "");
  		// set bus base voltage 
  		bus1.setBaseVoltage(12500.0);
  		// set bus to be a swing bus
  		bus1.setGenCode(AclfGenCode.NON_GEN);
  		bus1.setLoadCode(AclfLoadCode.CONST_P);
  		DStab3PLoad load1 = new DStab3PLoadImpl();
  		
  		//load1.setVminpu(0.5);
  		
		load1.set3PhaseLoad(new Complex3x1(new Complex(1,0.1),new Complex(1,0.1),new Complex(1.0,0.1)));
	
		bus1.getThreePhaseLoadList().add(load1);
  			
  		
  		
  		
  	// Bus 2
		DStab3PBus bus2 = ThreePhaseObjectFactory.create3PDStabBus("Bus2", net);
  		// set bus name and description attributes
  		bus2.setAttributes("Bus 2", "");
  		// set bus base voltage 
  		bus2.setBaseVoltage(230000.0);
  		
  		bus2.setGenCode(AclfGenCode.NON_GEN);
  		bus2.setLoadCode(AclfLoadCode.NON_LOAD);
  		
//  	  	// Bus 2
//  		Bus3Phase bus3 = ThreePhaseObjectFactory.create3PDStabBus("Bus2A", net);
//  		// set bus name and description attributes
//  		bus3.setAttributes("Bus 2", "");
//  		// set bus base voltage 
//  		bus3.setBaseVoltage(12500.0);
//  		
//  		bus3.setGenCode(AclfGenCode.NON_GEN);
//  		bus3.setLoadCode(AclfLoadCode.NON_LOAD);

  		
  	  	// Bus 3
  		DStab3PBus bus3 = ThreePhaseObjectFactory.create3PDStabBus("Bus3", net);
  		// set bus name and description attributes
  		bus3.setAttributes("Bus 3", "");
  		// set bus base voltage 
  		bus3.setBaseVoltage(230000.0);
  		// set bus to be a swing bus
  		bus3.setGenCode(AclfGenCode.SWING);
  		
  		DStab3PGen gen2 = ThreePhaseObjectFactory.create3PGenerator("Gen1");
  		gen2.setMvaBase(100.0);
  		gen2.setDesiredVoltMag(1.0);
  		//gen2.setGen(new Complex(0.7164,0.2710));
  		gen2.setPosGenZ(new Complex(0.02,0.2));
  		gen2.setNegGenZ(new Complex(0.02,0.2));
  		gen2.setZeroGenZ(new Complex(0,1.0E9));
  		
  		//add to contributed gen list
  		bus3.getContributeGenList().add(gen2);
  		
  		EConstMachine mach2 = (EConstMachine)DStabObjectFactory.
				createMachine("1", "Mach-1", MachineModelType.ECONSTANT, net, "Bus3", "Gen1");
  		
  		mach2.setRating(100, UnitType.mVA, net.getBaseKva());
		mach2.setRatedVoltage(230000.0);
		mach2.calMultiFactors();
		mach2.setH(5.0E6);
		mach2.setD(0.01);
		mach2.setRa(0.02);
		mach2.setXd1(0.20);

  
		//////////////////transformers///////////////////////////////////////////
		DStab3PBranch xfr12 = ThreePhaseObjectFactory.create3PBranch("Bus2", "Bus1", "0", net);
		xfr12.setBranchCode(AclfBranchCode.XFORMER);
		xfr12.setZ( new Complex( 0.0, 0.05 ));
		xfr12.setZ0( new Complex(0.0, 0.05 ));
		Static3PXformer xfr = threePhaseXfrAptr.apply(xfr12);
		
		//TODO change for testing
		xfr.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
		xfr.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0.0,0.0), UnitType.PU);
  		
		
  		DStab3PBranch bra23 = ThreePhaseObjectFactory.create3PBranch("Bus3", "Bus2", "0", net);
		bra23.setBranchCode(AclfBranchCode.LINE);
		bra23.setZ( new Complex(0.01,   0.100));
		bra23.setHShuntY(new Complex(0, 0.200/2));
		bra23.setZ0( new Complex(0.03,	  0.3));
		bra23.setHB0(0.200/2);
		
//  		Branch3Phase bra2A = ThreePhaseObjectFactory.create3PBranch("Bus2A", "Bus1", "0", net);
//		bra2A.setBranchCode(AclfBranchCode.LINE);
//		bra2A.setZ( new Complex(0.06,   0.06));
//		bra2A.setZ0( new Complex(0.1,	  0.1));
//		bra2A.setHShuntY(new Complex(0, 0.00));
//		bra2A.setHB0(0.00);
		
		//net.setBusNumberArranged(true);
  		return net;
		
	}
}
