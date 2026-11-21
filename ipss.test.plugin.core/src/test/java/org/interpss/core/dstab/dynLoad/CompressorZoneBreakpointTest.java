package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.util.TreeMap;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.dynLoad.impl.LD1PACImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.interpss.dstab.DStabObjectFactory;

/** Public synthetic controls for the zone-only electrical/relay breakpoint split.
 * The fixture is deliberately independent of private system constants. Independent
 * current-branch evidence remains a separate retained QA acceptance artifact.
 */
public class CompressorZoneBreakpointTest {
    private static final double POWER_TOLERANCE = 1e-4;
    // Constant running P=1 and Rstall=Xstall=.2 give Gstall=2.5.
    // Their electrical intersection is sqrt(1/2.5), independently of Vstall.
    private static final double ROOT = Math.sqrt(.4);
    @BeforeAll static void setup() { IpssCorePlugin.init(); }
    private static LD1PACImpl motor(LD1PACImpl.CharacteristicPolicy policy) {
        var net=DStabObjectFactory.createDStabilityNetwork();net.setBaseKva(100000);
        var bus=DStabObjectFactory.createDStabBus("synthetic-load",net).orElseThrow();
        bus.setLoadCode(com.interpss.core.aclf.AclfLoadCode.CONST_P);bus.setLoadP(.4);bus.setLoadQ(.4*Math.tan(Math.acos(.95)));
        bus.setVoltage(Complex.ONE);bus.setFreq(1.);bus.setBaseVoltage(230000);
        bus.setInitLoad(new Complex(.4,.4*Math.tan(Math.acos(.95))));
        var m=new LD1PACImpl(bus,"synthetic-compressor");m.setCharacteristicPolicy(policy);
        m.setLoadPercent(100);m.setMvaBase(0);m.setLoadFactor(1.);m.setPowerFactor(.95);
        m.setRstall(.2);m.setXstall(.2);m.setVstall(.45);m.setVbrk(.9);m.setLFadj(0);
        m.setKp1(0);m.setNp1(1);m.setKq1(0);m.setNq1(1);
        m.setKp2(0);m.setNp2(2);m.setKq2(2);m.setNq2(2);
        m.setTstall(.04);m.setTv(.02);m.setTf(.05);m.setTth(10);m.setCmpKpf(0);m.setCmpKqf(0);
        assertTrue(m.initStates());return m;
    }
    private static double breakpoint(LD1PACImpl m)throws Exception {
        Field f=LD1PACImpl.class.getDeclaredField("vstallbrk");f.setAccessible(true);return f.getDouble(m);
    }
    private static Complex power(LD1PACImpl m,double v) {
        m.getDStabBus().setVoltage(new Complex(v,0));m.getOutputObject();
        return m.getLoadPQ().multiply(m.getDStabBus().getNetwork().getBaseMva()/m.getMvaBase());
    }
    @Test void zoneIntersectionRemainsIndependentOfStallPickupAndIsRefined()throws Exception {
        var m=motor(LD1PACImpl.CharacteristicPolicy.ZONE_COMPOSITE);
        assertTrue(ROOT>.45);assertEquals(ROOT,breakpoint(m),1e-10);
        assertEquals(.45,m.getVstall(),0);assertEquals(.04,m.getTstall(),0);
        assertEquals(1,2.5*Math.pow(breakpoint(m),2),1e-10);
    }
    @ParameterizedTest @EnumSource(value=LD1PACImpl.CharacteristicPolicy.class,names={"STANDALONE","LEGACY_COMPOSITE"})
    void standaloneAndLegacyKeepTheirSeparateCappedCharacteristic(LD1PACImpl.CharacteristicPolicy policy)throws Exception {
        var m=motor(policy);assertEquals(.45,breakpoint(m),0);
        assertEquals(1,power(m,.5).getReal(),POWER_TOLERANCE);
    }
    @Test void zoneRunningMotorUsesImpedanceBetweenRelayPickupAndElectricalIntersection() {
        var m=motor(LD1PACImpl.CharacteristicPolicy.ZONE_COMPOSITE);
        Complex p=power(m,.5);double expected=2.5*.5*.5;
        assertEquals(expected,p.getReal(),POWER_TOLERANCE);assertEquals(expected,p.getImaginary(),POWER_TOLERANCE);
        assertEquals(1.,m.getIntegrationHistories().get("RunA"),0);
        assertEquals(1.,m.getIntegrationHistories().get("RunB"),0);
    }
    @Test void commonElectricalTransitionRetainsItsReactivePowerJump() {
        var m=motor(LD1PACImpl.CharacteristicPolicy.ZONE_COMPOSITE);double e=1e-7;
        Complex below=power(m,ROOT-e),above=power(m,ROOT+e);
        assertEquals(below.getReal(),above.getReal(),POWER_TOLERANCE);
        assertEquals(2.5*Math.pow(ROOT-e,2),below.getImaginary(),POWER_TOLERANCE);
        assertEquals(Math.tan(Math.acos(.95))+2*Math.pow(.9-ROOT-e,2),above.getImaginary(),POWER_TOLERANCE);
        assertTrue(Math.abs(above.getImaginary()-below.getImaginary())>.1,"Do not impose artificial Q continuity");
    }
    @Test void trialCurrentsDoNotAdvanceProtectionOrIntegrationHistory()throws Exception {
        var m=motor(LD1PACImpl.CharacteristicPolicy.ZONE_COMPOSITE);
        var states=new TreeMap<>(m.getIntegrationStates());var history=new TreeMap<>(m.getIntegrationHistories());
        Complex before=power(m,.5);power(m,.3);Complex after=power(m,.5);
        assertEquals(before.getReal(),after.getReal(),POWER_TOLERANCE);assertEquals(before.getImaginary(),after.getImaginary(),POWER_TOLERANCE);
        assertEquals(states,m.getIntegrationStates());assertEquals(history,m.getIntegrationHistories());
        assertEquals(.45,m.getVstall(),0);assertEquals(.04,m.getTstall(),0);assertEquals(ROOT,breakpoint(m),1e-10);
    }
    @Test void freshZoneInitializationRebuildsSameRootWithoutChangingPickup()throws Exception {
        var m=motor(LD1PACImpl.CharacteristicPolicy.ZONE_COMPOSITE);assertTrue(m.initStates());
        assertEquals(ROOT,breakpoint(m),1e-10);assertEquals(.45,m.getVstall(),0);
    }
}
