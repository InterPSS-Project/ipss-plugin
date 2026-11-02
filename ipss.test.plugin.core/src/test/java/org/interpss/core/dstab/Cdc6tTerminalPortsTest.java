package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.hvdc.*;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;

public class Cdc6tTerminalPortsTest extends CorePluginTestSetup {
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
