package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.junit.jupiter.api.Test;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.StaticLoadModel;

/** Establish which static quantities a two-terminal dynamic current owner must replace. */
public class Cdc6tNetworkOwnershipTest extends CorePluginTestSetup {
    @Test void staticExternalPowerIsNotConvertedIntoDynamicLoadOrCurrent() throws Exception {
        var network=DStabObjectFactory.createDStabilityNetwork();network.setBaseKva(100000);
        var bus=DStabObjectFactory.createDStabBus("terminal",network).orElseThrow();
        bus.setVoltage(new Complex(1,0));bus.setInitVoltMag(1);
        bus.setLoadCode(AclfLoadCode.CONST_P);bus.setLoadP(.3);bus.setLoadQ(.1);
        bus.setExternalPowerIntoNet(new Complex(-5.05,-3));
        assertEquals(new Complex(.3,.1),bus.calNetLoadResults());
        bus.setInitLoad(Complex.ZERO);bus.processInitLoad();
        assertEquals(new Complex(.3,.1),bus.getInitLoad());
        // Isolate the dynamic current path from ordinary static load conversion.
        bus.setLoadCode(AclfLoadCode.NON_LOAD);bus.setLoadP(0);bus.setLoadQ(0);
        bus.setInitLoad(Complex.ZERO);
        var current=bus.injCurDynamic(StaticLoadModel.CONST_Z,.7,.05);
        assertEquals(0,current.abs(),0,"Static external DC power is not automatically a dynamic injection");
        bus.setExternalPowerIntoNet(new Complex(5,-2.8));
        assertEquals(0,bus.injCurDynamic(StaticLoadModel.CONST_Z,.7,.05).abs(),0);
        var injection=new Complex(-5.05,3);
        var port=new com.interpss.dstab.device.impl.DynamicBusDeviceImpl() {
            @Override public Object getOutputObject(){return injection;}
        };
        port.setId("dc-terminal");port.setDStabBus(bus);
        port.setDeviceType(com.interpss.dstab.device.DynamicBusDeviceType.DYNAMIC_LOAD);
        bus.getDynamicBusDeviceList().add(port);
        assertEquals(injection,bus.injCurDynamic(StaticLoadModel.CONST_Z,.7,.05),
            "Explicit terminal port injects once without subtracting static external power");
        port.setStatus(false);
        assertEquals(0,bus.injCurDynamic(StaticLoadModel.CONST_Z,.7,.05).abs(),0);
    }
}
