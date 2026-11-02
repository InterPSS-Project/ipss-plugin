package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.hvdc.*;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.device.NetworkIterationAware.Context;

public class Cdc6tTerminalPortsTest extends CorePluginTestSetup {
    private record ProtectedPair(Cdc6tNormalTerminalPorts ports,Cdc6tProtectionCoordinator coordinator) {}
    private static ProtectedPair protectedPair(){
        var net=DStabObjectFactory.createDStabilityNetwork();net.setBaseKva(100000);
        var r=DStabObjectFactory.createDStabBus("R",net).orElseThrow();
        var i=DStabObjectFactory.createDStabBus("I",net).orElseThrow();r.setVoltage(Complex.ONE);i.setVoltage(Complex.ONE);
        var coordinator=new Cdc6tProtectionCoordinator(new Cdc6tRectifierProtection(.9,-999,999,.94,.01,.02),
                new Cdc6tInverterProtection(.9,.02,.92,.01,.02),new Cdc6tBypassProtection(0,0,.01,.92,.01,.02),
                new Cdc6tModeSwitch(true,0,.02),new Cdc6tRecoveryRamp(250000,500000,500,1000,2,2));
        var pair=new Cdc6tNormalTerminalPorts("protected",r,i,runtime(),500000,1000,505000,coordinator);
        pair.attach();assertTrue(pair.rectifierPort().initStates(r));return new ProtectedPair(pair,coordinator);
    }
    private static void solve(Cdc6tNormalTerminalPorts.Port port,String stage,double t,boolean success){
        port.beginNetworkSolution(new Context(stage,t,t,50,1e-6));port.beforeNetworkIteration(0);
        if(success)port.afterNetworkConvergence();port.endNetworkSolution(success);
    }
    private static double[] history(Cdc6tProtectionCoordinator c){double[] result=new double[23];c.readAcceptedHistory(result,0);return result;}

    @Test void predictorAndCorrectorShareProvisionalRecoveryButFailureDiscardsIt(){
        var f=protectedPair();var p=f.ports().rectifierPort();var inv=f.ports().inverterPort();var c=f.coordinator();
        solve(p,"INITIAL",0,true);inv.getDStabBus().setVoltage(new Complex(.8,0));
        solve(p,"INITIAL",.05,true);solve(p,"INITIAL",.071,true);
        assertEquals(Cdc6tNormalRuntime.OperatingMode.BLOCKED,c.operatingMode());
        inv.getDStabBus().setVoltage(Complex.ONE);solve(p,"INITIAL",.1,true);
        double[] committed=history(c);
        solve(p,"PREDICTOR",.12,true);
        assertEquals(Cdc6tNormalRuntime.OperatingMode.RECOVERING,c.operatingMode());
        assertArrayEquals(committed,history(c));
        // Recovered-link voltage can be below the blocked-link release threshold.
        inv.getDStabBus().setVoltage(new Complex(.915,0));
        solve(p,"CORRECTOR",.12,false);
        assertEquals(Cdc6tNormalRuntime.OperatingMode.BLOCKED,c.operatingMode());assertArrayEquals(committed,history(c));
        inv.getDStabBus().setVoltage(Complex.ONE);solve(p,"PREDICTOR",.12,true);
        inv.getDStabBus().setVoltage(new Complex(.915,0));solve(p,"CORRECTOR",.12,true);
        assertEquals(Cdc6tNormalRuntime.OperatingMode.RECOVERING,c.operatingMode());assertEquals(.12,history(c)[0],0);
        committed=history(c);
        for(int j=0;j<20;j++){p.getOutputObject();inv.getOutputObject();p.getStates(null);}
        solve(inv,"INITIAL",.13,true);assertArrayEquals(committed,history(c),"Only the owner can accept history");
        assertTrue(p.initStates(p.getDStabBus()));assertEquals(Double.NEGATIVE_INFINITY,history(c)[0]);
        assertEquals(Cdc6tNormalRuntime.OperatingMode.NORMAL,c.operatingMode());
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
        return new Cdc6tNormalRuntime(new Cdc6tMeasurements(.05,.05,.05),
            new Cdc6tCurrentOrder(Cdc6tCurrentOrder.Mode.POWER,-500,500,0),
            new Cdc6tVoltageCurrentLimit(250,450,425,2000,500,2000),
            new Cdc6tConductingLink(new Cdc6tConverterBoundary(true,2,230000,.9263,.9375,13.5939,1e8),
                new Cdc6tConverterBoundary(false,2,230000,.9097,.94375,13.3174,1e8),5,Math.toRadians(5),Math.toRadians(20)));
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
