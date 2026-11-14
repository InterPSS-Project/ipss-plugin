package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.hvdc.model.*;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.device.NetworkIterationAware.Context;

public class Cdc6tTerminalPortsTest extends CorePluginTestSetup {
    private record ProtectedPair(Cdc6tNormalTerminalPorts ports,Cdc6tProtectionCoordinator coordinator) {}
    private static ProtectedPair protectedPair(){
        return protectedPair(runtime());
    }
    private static ProtectedPair protectedPair(Cdc6tNormalRuntime runtime){
        var net=DStabObjectFactory.createDStabilityNetwork();net.setBaseKva(100000);
        var r=DStabObjectFactory.createDStabBus("R",net).orElseThrow();
        var i=DStabObjectFactory.createDStabBus("I",net).orElseThrow();r.setVoltage(Complex.ONE);i.setVoltage(Complex.ONE);
        var coordinator=new Cdc6tProtectionCoordinator(new Cdc6tRectifierProtection(.9,-999,999,.94,.01,.02),
                new Cdc6tInverterProtection(.9,.02,.92,.01,.02),new Cdc6tBypassProtection(0,0,.01,.92,.01,.02),
                new Cdc6tModeSwitch(true,0,.02),new Cdc6tRecoveryRamp(250000,500000,500,1000,2,2));
        var pair=new Cdc6tNormalTerminalPorts("protected",r,i,runtime,500000,1000,505000,coordinator);
        pair.attach();assertTrue(pair.rectifierPort().initStates(r));return new ProtectedPair(pair,coordinator);
    }
    private static void solve(Cdc6tNormalTerminalPorts.Port port,String stage,double t,boolean success){
        port.beginNetworkSolution(new Context(stage,t,t,50,1e-6));port.beforeNetworkIteration(0);
        if(success){
            int passes=0;
            while(port.applyConvergedNetworkAction()){assertTrue(++passes<50);port.beforeNetworkIteration(0);}
            port.afterNetworkConvergence();
        }
        port.endNetworkSolution(success);
    }
    private static double[] history(Cdc6tProtectionCoordinator c){double[] result=new double[23];c.readAcceptedHistory(result,0);return result;}

    @Test void rejectedNetworkEntryLeavesValidRetryAvailable(){
        var f=protectedPair();var p=f.ports().rectifierPort();solve(p,"INITIAL",.1,true);
        double[] accepted=history(f.coordinator());
        assertThrows(IllegalArgumentException.class,()->p.beginNetworkSolution(new Context("UNKNOWN",.1,.1,50,1e-6)));
        assertArrayEquals(accepted,history(f.coordinator()),0);
        solve(p,"INITIAL",.1,true);
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,.09}){
            assertThrows(IllegalArgumentException.class,()->p.beginNetworkSolution(new Context("INITIAL",invalid,invalid,50,1e-6)));
            assertArrayEquals(accepted,history(f.coordinator()),0);
            solve(p,"INITIAL",.1,true);
        }
    }

    @Test void mismatchedCorrectorDoesNotDiscardPendingProtectionEndpoint(){
        var f=protectedPair();var p=f.ports().rectifierPort();var inv=f.ports().inverterPort();
        solve(p,"INITIAL",0,true);inv.getDStabBus().setVoltage(new Complex(.8,0));
        solve(p,"INITIAL",.05,true);solve(p,"INITIAL",.071,true);
        inv.getDStabBus().setVoltage(Complex.ONE);solve(p,"INITIAL",.1,true);
        solve(p,"PREDICTOR",.12,true);double[] accepted=history(f.coordinator());
        assertThrows(IllegalStateException.class,()->p.beginNetworkSolution(new Context("CORRECTOR",.1,.13,50,1e-6)));
        assertArrayEquals(accepted,history(f.coordinator()),0);
        solve(p,"CORRECTOR",.12,true);
        assertEquals(Cdc6tNormalRuntime.OperatingMode.RECOVERING,f.coordinator().operatingMode());
        assertEquals(.12,history(f.coordinator())[0],0);
    }

    @Test void rejectedStaggeredEndpointCanBeReplacedBeforeOpeningNetworkTrial(){
        var f=protectedPair();var p=f.ports().rectifierPort();solve(p,"INITIAL",.1,true);
        double[] accepted=history(f.coordinator());
        p.completeStaggeredStep(.01,.09);
        assertThrows(IllegalArgumentException.class,()->p.beginNetworkSolution(new Context("DIRECT",0,0,50,1e-6)));
        assertArrayEquals(accepted,history(f.coordinator()),0);
        p.completeStaggeredStep(.01,.11);solve(p,"DIRECT",0,true);
        assertEquals(.11,history(f.coordinator())[0],0);
    }

    @Test void predictorDefersRecoveryAndFailedCorrectorDiscardsIt(){
        var f=protectedPair();var p=f.ports().rectifierPort();var inv=f.ports().inverterPort();var c=f.coordinator();
        solve(p,"INITIAL",0,true);inv.getDStabBus().setVoltage(new Complex(.8,0));
        solve(p,"INITIAL",.05,true);solve(p,"INITIAL",.071,true);
        assertEquals(Cdc6tNormalRuntime.OperatingMode.BLOCKED,c.operatingMode());
        long blockedVersion=p.staggeredInputDiscontinuityVersion();assertEquals(1,blockedVersion);
        assertEquals(0,inv.staggeredInputDiscontinuityVersion());
        inv.getDStabBus().setVoltage(Complex.ONE);solve(p,"INITIAL",.1,true);
        double[] committed=history(c);
        solve(p,"PREDICTOR",.12,true);
        assertEquals(Cdc6tNormalRuntime.OperatingMode.BLOCKED,c.operatingMode(),"Predictor retains the interval's discrete regime");
        assertEquals(blockedVersion,p.staggeredInputDiscontinuityVersion(),"Predictor cannot publish an event");
        assertArrayEquals(committed,history(c));
        // The corrector can release at this endpoint, but a failed solve discards it.
        solve(p,"CORRECTOR",.12,false);
        assertEquals(Cdc6tNormalRuntime.OperatingMode.BLOCKED,c.operatingMode());assertArrayEquals(committed,history(c));
        assertEquals(blockedVersion,p.staggeredInputDiscontinuityVersion(),"Rejected recovery cannot notify integration");
        inv.getDStabBus().setVoltage(Complex.ONE);solve(p,"PREDICTOR",.12,true);
        solve(p,"CORRECTOR",.12,true);
        assertEquals(Cdc6tNormalRuntime.OperatingMode.RECOVERING,c.operatingMode());assertEquals(.12,history(c)[0],0);
        assertEquals(blockedVersion+1,p.staggeredInputDiscontinuityVersion());
        committed=history(c);
        for(int j=0;j<20;j++){p.getOutputObject();inv.getOutputObject();p.getStates(null);}
        solve(inv,"INITIAL",.13,true);assertArrayEquals(committed,history(c),"Only the owner can accept history");
        assertTrue(p.initStates(p.getDStabBus()));assertEquals(Double.NEGATIVE_INFINITY,history(c)[0]);
        assertEquals(0,p.staggeredInputDiscontinuityVersion());
        assertEquals(Cdc6tNormalRuntime.OperatingMode.NORMAL,c.operatingMode());
    }

    @Test void endpointRecoveryCannotEnterThePrecedingModifiedEulerInterval(){
        var f=protectedPair();var p=f.ports().rectifierPort();var inv=f.ports().inverterPort();
        solve(p,"INITIAL",0,true);inv.getDStabBus().setVoltage(new Complex(.8,0));
        solve(p,"INITIAL",.05,true);solve(p,"INITIAL",.071,true);
        inv.getDStabBus().setVoltage(Complex.ONE);solve(p,"INITIAL",.1,true);
        assertEquals(Cdc6tNormalRuntime.OperatingMode.BLOCKED,f.coordinator().operatingMode());
        double[] initial=new double[3],corrected=new double[3],afterAction=new double[3];
        p.readStaggeredState(initial,0);
        double h=.02,lag=.05,decay=1-h/lag+.5*h*h/(lag*lag);
        p.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,0);
        solve(p,"PREDICTOR",.12,true);
        assertEquals(Cdc6tNormalRuntime.OperatingMode.BLOCKED,f.coordinator().operatingMode());
        p.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,1);
        p.readStaggeredState(corrected,0);
        for(int i=0;i<3;i++)assertEquals(initial[i]*decay,corrected[i],1e-9,"Heun decay toward zero throughout the blocked interval");
        solve(p,"CORRECTOR",.12,true);p.readStaggeredState(afterAction,0);
        assertEquals(Cdc6tNormalRuntime.OperatingMode.RECOVERING,f.coordinator().operatingMode());
        assertArrayEquals(corrected,afterAction,0,"Endpoint action changes inputs, not the continuous states");
        double[] left=new double[3];assertTrue(p.readStaggeredInputDiscontinuityLeftRates(left,0));
        for(int i=0;i<3;i++)assertEquals(-corrected[i]/lag,left[i],1e-9,
                "Local discontinuity retains the corrected left endpoint, not the preceding integration rate");
        p.getOutputObject();double[] again=new double[3];assertTrue(p.readStaggeredInputDiscontinuityLeftRates(again,0));
        assertArrayEquals(left,again,0,"Refreshing right-side electrical output cannot overwrite accepted left rates");
        assertFalse(inv.readStaggeredInputDiscontinuityLeftRates(new double[0],0));
    }

    @Test void completedRecoveryChangesElectricalModeWithoutResettingIntegrationHistory(){
        var f=protectedPair();var p=f.ports().rectifierPort();var inv=f.ports().inverterPort();
        solve(p,"INITIAL",0,true);inv.getDStabBus().setVoltage(new Complex(.8,0));
        solve(p,"INITIAL",.05,true);solve(p,"INITIAL",.071,true);
        inv.getDStabBus().setVoltage(Complex.ONE);solve(p,"INITIAL",.1,true);solve(p,"INITIAL",.12,true);
        assertEquals(Cdc6tNormalRuntime.OperatingMode.RECOVERING,f.coordinator().operatingMode());
        long version=p.staggeredInputDiscontinuityVersion();double[] state=new double[3],after=new double[3];
        p.readStaggeredState(state,0);double[] committed=history(f.coordinator());
        p.beginNetworkSolution(new Context("CORRECTOR",.37,.371,50,1e-6));p.beforeNetworkIteration(0);
        assertTrue(p.applyConvergedNetworkAction());assertFalse(p.requiresIntegrationInputDiscontinuity());
        p.endNetworkSolution(false);
        assertArrayEquals(committed,history(f.coordinator()),0);assertEquals(version,p.staggeredInputDiscontinuityVersion());
        assertEquals(Cdc6tNormalRuntime.OperatingMode.RECOVERING,f.coordinator().operatingMode());
        p.beginNetworkSolution(new Context("CORRECTOR",.37,.371,50,1e-6));p.beforeNetworkIteration(0);
        assertTrue(p.applyConvergedNetworkAction());assertFalse(p.requiresIntegrationInputDiscontinuity());
        p.beforeNetworkIteration(0);assertFalse(p.applyConvergedNetworkAction());p.afterNetworkConvergence();p.endNetworkSolution(true);
        assertEquals(Cdc6tNormalRuntime.OperatingMode.NORMAL,f.coordinator().operatingMode());
        assertEquals(version,p.staggeredInputDiscontinuityVersion(),"Normal control resumes with existing derivative history");
        p.readStaggeredState(after,0);assertArrayEquals(state,after,0,"The handoff cannot advance continuous states");
    }

    @Test void transitionObserverPublishesBothSidesOnlyAfterSuccessfulCommit(){
        var f=protectedPair();var p=f.ports().rectifierPort();var inv=f.ports().inverterPort();
        var events=new java.util.ArrayList<Cdc6tNetworkTransition>();f.ports().setTransitionObserver(events::add);
        solve(p,"INITIAL",0,true);inv.getDStabBus().setVoltage(new Complex(.8,0));
        solve(p,"INITIAL",.05,true);solve(p,"INITIAL",.071,true);
        assertEquals(1,events.size());var block=events.get(0);
        assertEquals(.071,block.time(),0);assertEquals(Cdc6tNormalRuntime.OperatingMode.NORMAL,block.beforeMode());
        assertEquals(Cdc6tNormalRuntime.OperatingMode.BLOCKED,block.afterMode());
        assertTrue(block.before().rectifierP()>0);assertEquals(0,block.after().rectifierP(),0);
        assertEquals(block.before().measuredInverterVolts(),block.after().measuredInverterVolts(),0);
        inv.getDStabBus().setVoltage(Complex.ONE);solve(p,"INITIAL",.1,true);
        double[] acceptedLeft=new double[3],observedLeft=new double[3];
        assertTrue(p.readStaggeredInputDiscontinuityLeftRates(acceptedLeft,0));
        p.beginNetworkSolution(new Context("CORRECTOR",.1,.12,50,1e-6));p.beforeNetworkIteration(0);
        assertThrows(IllegalStateException.class,()->f.ports().setTransitionObserver(null));
        assertTrue(p.applyConvergedNetworkAction());assertEquals(1,events.size());
        assertTrue(p.readStaggeredInputDiscontinuityLeftRates(observedLeft,0));assertArrayEquals(acceptedLeft,observedLeft,0);
        p.endNetworkSolution(false);assertEquals(1,events.size(),"Rejected action publishes neither side");
        assertTrue(p.readStaggeredInputDiscontinuityLeftRates(observedLeft,0));assertArrayEquals(acceptedLeft,observedLeft,0);
        assertEquals(Cdc6tNormalRuntime.OperatingMode.BLOCKED,f.coordinator().operatingMode());
        solve(p,"INITIAL",.12,true);assertEquals(2,events.size());
        var recovery=events.get(1);assertEquals(.12,recovery.time(),0);
        assertEquals(0,recovery.before().rectifierP(),0);assertTrue(recovery.after().rectifierP()>0);
        assertEquals(recovery.before().measuredAmperes(),recovery.after().measuredAmperes(),0);
        solve(p,"INITIAL",.12,true);p.getOutputObject();inv.getOutputObject();assertEquals(2,events.size());
        f.ports().setTransitionObserver(null);assertTrue(p.initStates(p.getDStabBus()));
        assertEquals(2,events.size(),"Reinitialization and disabled recording publish nothing");
        assertFalse(p.readStaggeredInputDiscontinuityLeftRates(observedLeft,0));
    }
    @Test void transitionSnapshotsEvaluateAlgebraicMeasurementsOnTheirOwnSide(){
        var runtime=runtime(new Cdc6tMeasurements(0,0,0));var f=protectedPair(runtime);var p=f.ports().rectifierPort();
        var events=new java.util.ArrayList<Cdc6tNetworkTransition>();f.ports().setTransitionObserver(events::add);
        p.configureIntegrationStep(.0005);solve(p,"INITIAL",0,true);
        p.getDStabBus().setVoltage(new Complex(.8,0));solve(p,"INITIAL",.05,true);
        assertEquals(1,events.size());var event=events.get(0);
        assertEquals(event.before().inverterVolts(),event.before().measuredInverterVolts(),0);
        assertEquals(event.before().rectifierVolts(),event.before().measuredRectifierVolts(),0);
        assertTrue(event.before().measuredAmperes()>0);
        assertEquals(0,event.after().measuredInverterVolts(),0);assertEquals(0,event.after().measuredAmperes(),0);
        double[] state=new double[3];runtime.readStaggeredState(state,0);assertArrayEquals(new double[]{0,0,0},state,0);
    }
    @Test void recordingFailurePropagatesAfterCommitAndClearsPendingSnapshots(){
        var f=protectedPair();var p=f.ports().rectifierPort();int[] calls={0};
        f.ports().setTransitionObserver(event->{calls[0]++;throw new IllegalStateException("Recorder failure");});
        solve(p,"INITIAL",0,true);p.getDStabBus().setVoltage(new Complex(.8,0));
        assertThrows(IllegalStateException.class,()->solve(p,"INITIAL",.05,true));
        assertEquals(Cdc6tNormalRuntime.OperatingMode.BLOCKED,f.coordinator().operatingMode());
        assertEquals(.05,history(f.coordinator())[0],0);assertEquals(1,calls[0]);
        solve(p,"INITIAL",.05,true);assertEquals(1,calls[0],"A failed recorder must not duplicate an already committed action");
        f.ports().setTransitionObserver(null);
    }
    @Test void explicitExternalBoundaryCanBlockBeforeInfeasibleCurrentAndStillRollback(){
        var f=protectedPair();var p=f.ports().rectifierPort();solve(p,"INITIAL",0,true);
        double[] committed=history(f.coordinator());
        p.getDStabBus().setVoltage(new Complex(.005,0));
        p.beginNetworkSolution(new Context("INITIAL",.05,.05,50,1e-6));
        assertThrows(IllegalArgumentException.class,()->p.beforeNetworkIteration(0),"An arbitrary unconverged voltage must not trigger a protection shortcut");
        p.endNetworkSolution(false);assertArrayEquals(committed,history(f.coordinator()));
        for(boolean accepted:new boolean[]{false,true}){
            p.beginNetworkSolution(new Context("INITIAL",.05,.05,50,1e-6,true));p.beforeNetworkIteration(0);
            assertEquals(Cdc6tNormalRuntime.OperatingMode.BLOCKED,f.coordinator().operatingMode());
            assertEquals(Complex.ZERO,p.getOutputObject());assertArrayEquals(committed,history(f.coordinator()));
            if(accepted){assertFalse(p.applyConvergedNetworkAction());p.afterNetworkConvergence();}
            p.endNetworkSolution(accepted);
            if(!accepted){assertEquals(Cdc6tNormalRuntime.OperatingMode.NORMAL,f.coordinator().operatingMode());assertArrayEquals(committed,history(f.coordinator()));}
        }
        assertEquals(.05,history(f.coordinator())[0],0);assertEquals(1,p.staggeredInputDiscontinuityVersion());
    }

    @Test void staggeredEndpointCommitsOnlyAfterConvergenceAndDirectProbesKeepClock(){
        var f=protectedPair();var p=f.ports().rectifierPort();var c=f.coordinator();
        solve(p,"INITIAL",0,true);double[] committed=history(c);
        solve(p,"DIRECT",.01,true);assertArrayEquals(committed,history(c));
        p.completeStaggeredStep(.01,.01);solve(p,"DIRECT",0,false);assertArrayEquals(committed,history(c));
        p.completeStaggeredStep(.01,.01);solve(p,"DIRECT",0,true);assertEquals(.01,history(c)[0],0);
        committed=history(c);solve(p,"PREDICTOR",.02,true);assertArrayEquals(committed,history(c));
        assertTrue(p.initStates(p.getDStabBus()));assertEquals(Double.NEGATIVE_INFINITY,history(c)[0]);
    }
    private static Cdc6tNormalRuntime runtime() {
        return runtime(new Cdc6tMeasurements(.05,.05,.05));
    }
    private static Cdc6tNormalRuntime runtime(Cdc6tMeasurements measurements) {
        return new Cdc6tNormalRuntime(measurements,
            new Cdc6tCurrentOrder(Cdc6tCurrentOrder.Mode.POWER,-500,500,0),
            new Cdc6tVoltageCurrentLimit(250,450,425,2000,500,2000),
            new Cdc6tConductingLink(new Cdc6tConverterBoundary(true,2,230000,.9263,.9375,13.5939,1e8),
                new Cdc6tConverterBoundary(false,2,230000,.9097,.94375,13.3174,1e8),5,Math.toRadians(5),Math.toRadians(20)));
    }
    @Test void algebraicProjectionRequiresConvergenceAndGettersCannotResampleCommands(){
        for(boolean staggered:new boolean[]{false,true}){
            var runtime=runtime(new Cdc6tMeasurements(0,0,0));
            var f=protectedPair(runtime);var p=f.ports().rectifierPort();var inv=f.ports().inverterPort();
            p.configureIntegrationStep(.0005);
            solve(p,"INITIAL",0,true);
            double[] original=new double[3],actual=new double[3],boundary=new double[8];
            runtime.readStaggeredState(original,0);
            inv.getDStabBus().setVoltage(new Complex(.91,0));
            solve(p,"INITIAL",.05,false);runtime.readStaggeredState(actual,0);assertArrayEquals(original,actual);
            solve(p,"INITIAL",.05,true);runtime.readStaggeredState(actual,0);assertTrue(actual[0]<original[0]);
            runtime.readBoundary(boundary,0);assertEquals(1000,boundary[0]*1e8/boundary[6],1e-8);
            var output=p.getOutputObject();
            for(int k=0;k<10;k++){assertEquals(output,p.getOutputObject());inv.getOutputObject();p.getStates(null);}
            runtime.readBoundary(boundary,0);assertEquals(1000,boundary[0]*1e8/boundary[6],1e-8,"Projection does not change this endpoint's command");
            if(staggered)p.completeStaggeredStep(.0005,.0505);
            else {p.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0);p.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,1);}
            p.getOutputObject();runtime.readBoundary(boundary,0);
            assertTrue(boundary[0]*1e8/boundary[6]>1000,"The next integration command sees the projected measurement");
        }
    }
    @Test void twoPortsRefreshBothVoltagesButOnlyRectifierIntegrates() {
        var net=DStabObjectFactory.createDStabilityNetwork();net.setBaseKva(100000);
        var r=DStabObjectFactory.createDStabBus("R",net).orElseThrow();
        var i=DStabObjectFactory.createDStabBus("I",net).orElseThrow();r.setVoltage(Complex.ONE);i.setVoltage(Complex.ONE);
        var runtime=runtime();var pair=new Cdc6tNormalTerminalPorts("link",r,i,runtime,500000,1000,505000);
        pair.attach();assertTrue(pair.inverterPort().initStates(i));assertTrue(pair.rectifierPort().initStates(r));
        assertThrows(IllegalStateException.class,pair::attach);
        assertEquals(3,pair.rectifierPort().staggeredStateCount());assertEquals(0,pair.inverterPort().staggeredStateCount());
        assertEquals(-5.05,((Complex)pair.rectifierPort().getOutputObject()).getReal(),1e-12);
        assertEquals(5,((Complex)pair.inverterPort().getOutputObject()).getReal(),1e-12);
        i.setVoltage(new Complex(.85,0));
        double[] before=new double[3],after=new double[3];runtime.readStaggeredState(before,0);
        pair.inverterPort().nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0);
        runtime.readStaggeredState(after,0);assertArrayEquals(before,after,0);
        pair.rectifierPort().nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0);
        pair.inverterPort().nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1);
        pair.rectifierPort().nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1);
        runtime.readStaggeredState(after,0);assertTrue(after[0]<before[0]);
        double[] rates=new double[3];pair.rectifierPort().evaluateStaggered(.001,rates,0);assertTrue(rates[0]<0);
        pair.inverterPort().setStatus(false);
        assertFalse(pair.rectifierPort().isOperational());assertEquals(Complex.ZERO,pair.rectifierPort().getOutputObject());
        runtime.readStaggeredState(before,0);pair.rectifierPort().nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0);
        runtime.readStaggeredState(after,0);assertArrayEquals(before,after,0);
        pair.inverterPort().setStatus(true);i.getDynamicBusDeviceList().remove(pair.inverterPort());
        assertFalse(pair.rectifierPort().attachmentIntact());
        assertThrows(IllegalStateException.class,()->pair.rectifierPort().getOutputObject());
    }
}
