package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.interpss.dstab.control.exc.psse.rexsys.RexsysData;
import org.interpss.dstab.control.exc.psse.rexsys.RexsysExciter;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Diagram discriminators: input clipping must not suppress proportional AVR gain. */
public class RexsysVoltageInputLimitTest {
    private RexsysData data() {
        var d=new RexsysData();
        d.setTr(0.);d.setKvp(2000.);d.setKvi(0.);d.setVimax(.1);
        d.setTa(.02);d.setTb1(0.);d.setTc1(0.);d.setTb2(0.);d.setTc2(0.);
        d.setVrmax(1000.);d.setVrmin(-1000.);d.setKf(0.);d.setTf(1.);d.setTf1(0.);d.setTf2(0.);d.setFbf(1);
        d.setKip(2.);d.setKii(0.);d.setTp(.02);d.setVfmax(1000.);d.setVfmin(-1000.);
        d.setKh(.5);d.setKe(1.);d.setTe(.3);d.setKc(0.);d.setKd(0.);
        d.setE1(0.);d.setSe1(0.);d.setE2(0.);d.setSe2(0.);d.setFlimf(0);return d;
    }
    @Test void proportionalOnlyControllersBacksolveTheirInputsWithoutInventedIntegralBias() throws Exception {
        var builder=DStabBuilderTestFixture.createWithMachine();var d=data();
        var e=builder.addExcRexsys("Bus1","1",d);var m=builder.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);
        assertTrue(e.initStates(m.getDStabBus(),m));
        assertEquals(0.,e.getVoltagePiIntegralState(),0.);assertEquals(0.,e.getCurrentPiIntegralState(),0.);
        assertEquals(1.2,e.getRegulatorOutput(),1e-12);assertEquals(.0006,e.getVoltageErrorInput(),1e-12);
        assertEquals(1.0406,e.getRefPoint(),1e-12);assertEquals(.1,e.vimax,0.);
        for(int i=0;i<1000;i++){assertTrue(e.nextStep(.0001,DynamicSimuMethod.MODIFIED_EULER,m,0));assertTrue(e.nextStep(.0001,DynamicSimuMethod.MODIFIED_EULER,m,1));}
        assertEquals(1.2,e.getOutput(m),1e-10);
    }
    @Test void voltageInputLimitPrecedesBothGainsAndAllowsLargePiOutput() throws Exception {
        var builder=DStabBuilderTestFixture.createWithMachine();var d=data();d.setKii(1.);d.setKh(0.);
        var e=builder.addExcRexsys("Bus1","1",d);var m=builder.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);
        assertTrue(e.initStates(m.getDStabBus(),m));double initial=e.getRefPoint();
        e.setRefPoint(initial+.001);assertEquals(.001,e.getVoltageErrorInput(),1e-12);assertEquals(2.,e.getVoltagePiOutput(),1e-10);
        e.setRefPoint(initial+.2);assertEquals(.1,e.getVoltageErrorInput(),0.);assertEquals(200.,e.getVoltagePiOutput(),1e-12);
        e.setRefPoint(initial-.2);assertEquals(-.1,e.getVoltageErrorInput(),0.);assertEquals(-200.,e.getVoltagePiOutput(),1e-12);
    }
    @Test void voltageIntegralUsesLimitedErrorWithoutAnInventedPiOutputLimit() throws Exception {
        var builder=DStabBuilderTestFixture.createWithMachine();var d=data();d.setKvi(2.);d.setKii(1.);d.setKh(0.);
        var e=builder.addExcRexsys("Bus1","1",d);var m=builder.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);
        assertTrue(e.initStates(m.getDStabBus(),m));e.setRefPoint(e.getRefPoint()+.2);
        e.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,m,0);e.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,m,1);
        assertEquals(.0002,e.getVoltagePiIntegralState(),1e-14);assertEquals(200.0002,e.getVoltagePiOutput(),1e-10);
    }
    @Test void integratingControllersRetainTheRequiredBiasWithoutExpandingTheInputLimitToVr() throws Exception {
        var builder=DStabBuilderTestFixture.createWithMachine();var d=data();d.setKvi(2.);d.setKii(1.);
        var e=builder.addExcRexsys("Bus1","1",d);var m=builder.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);
        assertTrue(e.initStates(m.getDStabBus(),m));assertEquals(.6,e.getRegulatorOutput(),1e-12);
        assertEquals(.6,e.getVoltagePiIntegralState(),1e-12);assertEquals(1.2,e.getCurrentPiIntegralState(),1e-12);
        assertEquals(0.,e.getVoltageErrorInput(),1e-12);assertEquals(.1,e.vimax,0.);
    }
    @Test void aZeroGainCurrentControllerCannotSupportANonzeroFieldByAFrozenBias() throws Exception {
        var builder=DStabBuilderTestFixture.createWithMachine();var d=data();d.setKip(0.);
        var e=builder.addExcRexsys("Bus1","1",d);var m=builder.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);
        assertFalse(e.initStates(m.getDStabBus(),m));
    }
}
