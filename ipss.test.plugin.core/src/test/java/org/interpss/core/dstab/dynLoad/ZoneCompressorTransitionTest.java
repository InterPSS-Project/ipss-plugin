package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.impl.LD1PACImpl;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.core.aclf.AclfLoadCode;

/** Synthetic constant-power curve has the independent exact crossing sqrt(P/G). */
public class ZoneCompressorTransitionTest {
    private LD1PACImpl motor(LD1PACImpl.CharacteristicPolicy policy)throws Exception {
        var net=DStabObjectFactory.createDStabilityNetwork();net.setBaseKva(100000.);
        var bus=DStabObjectFactory.createDStabBus("Load",net).orElseThrow();
        bus.setLoadCode(AclfLoadCode.CONST_P);bus.setLoadP(1.);bus.setLoadQ(.2);bus.setInitLoad(new Complex(1.,.2));bus.setVoltage(Complex.ONE);bus.setFreq(1.);
        var motor=new LD1PACImpl(bus,"synthetic");motor.setCharacteristicPolicy(policy);
        motor.setLoadPercent(100.);motor.setLoadFactor(1.);motor.setMvaBase(100.);motor.setPowerFactor(1./Math.sqrt(1.04));
        motor.setRstall(.2);motor.setXstall(.2);motor.setVstall(.45);motor.setTstall(.03);motor.setVbrk(.9);motor.setLFadj(0.);
        motor.setKp1(0.);motor.setKp2(0.);motor.setNp1(1.);motor.setNp2(1.);motor.setKq1(0.);motor.setKq2(0.);motor.setNq1(1.);motor.setNq2(1.);
        motor.setTv(.032);motor.setTf(.05);motor.setTth(15.);assertTrue(motor.initStates());return motor;
    }
    private double crossing(LD1PACImpl motor)throws Exception {var field=LD1PACImpl.class.getDeclaredField("vstallbrk");field.setAccessible(true);return field.getDouble(motor);}
    private Complex power(LD1PACImpl motor,double voltage){motor.getDStabBus().setVoltage(new Complex(voltage));motor.getNortonCurInj();return motor.getLoadPQ();}
    @Test void electricalCrossingUsesThePowerCurveWithoutCappingAtTheTimedStallTrigger()throws Exception {
        var motor=motor(LD1PACImpl.CharacteristicPolicy.ZONE_COMPOSITE);double expected=Math.sqrt(1./2.5);
        assertEquals(expected,crossing(motor),1e-13);assertEquals(.45,motor.getVstall(),0.);assertEquals(.03,motor.getTstall(),0.);
        assertEquals(2.5*.55*.55,power(motor,.55).getReal(),1e-12);
        assertEquals(1.,power(motor,.7).getReal(),1e-12);
        Complex below=power(motor,expected-1e-7),above=power(motor,expected+1e-7);
        assertEquals(below.getReal(),above.getReal(),1e-6);
        assertTrue(below.getImaginary()-above.getImaginary()>.7,"Keep the characteristic's Q branch jump; do not smooth it to force convergence");
    }
    @Test void currentObservationsPreservePhysicalStatesTimersAndRepeatedInitialization()throws Exception {
        var motor=motor(LD1PACImpl.CharacteristicPolicy.ZONE_COMPOSITE);var states=motor.getIntegrationStates();var histories=motor.getIntegrationHistories();double initial=crossing(motor);
        for(double voltage:new double[]{.9,.55,.4,.55,.9})power(motor,voltage);
        assertEquals(states,motor.getIntegrationStates());assertEquals(histories,motor.getIntegrationHistories());assertEquals(0.,motor.getAcStallTimer(),0.);
        motor.getDStabBus().setVoltage(Complex.ONE);assertTrue(motor.initStates());assertEquals(initial,crossing(motor),0.);
        assertEquals(.45,motor.getVstall(),0.);assertEquals(.03,motor.getTstall(),0.);
    }
    @Test void standaloneAndLegacyFamiliesRetainTheirExistingTransitionPolicy()throws Exception {
        for(var policy:new LD1PACImpl.CharacteristicPolicy[]{LD1PACImpl.CharacteristicPolicy.STANDALONE,LD1PACImpl.CharacteristicPolicy.LEGACY_COMPOSITE}) {
            var motor=motor(policy);assertEquals(.45,crossing(motor),0.);assertEquals(1.,power(motor,.55).getReal(),1e-12);
        }
    }
}
