package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import static org.interpss.dstab.renewable.Regfmb1Data.Parameter.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;

class Regfmb1FoundationTest {
    private static Regfmb1Data data(int... flags){return Regfmb1Data.nativePsse(new Complex(.015,.23),flags,
            new double[]{.05,0,5.86,.01,.1,-.1,.265,2.65,.1,-.1,.02,3,.001,100,50,1.2,.9,2,2,.02,.02,.02,.02,1,.05});}
    @Test void exactNativeCardinalityAndOrder() {
        var d=data(0,0,0,0,1,0);assertEquals(25,d.constants().length);assertEquals(6,d.toNativeIcons().length);
        assertEquals(.05,d.value(MQ));assertEquals(3,d.value(H));assertEquals(.02,d.value(TIF));assertEquals(.05,d.value(VPLLFRZ));
        assertEquals(14,Regfmb1Data.STATE_COUNT);assertEquals(17,Regfmb1Data.VAR_COUNT);
        assertThrows(IllegalArgumentException.class,()->Regfmb1Data.nativePsse(new Complex(0,.1),new int[5],d.constants()));
        assertThrows(IllegalArgumentException.class,()->Regfmb1Data.nativePsse(new Complex(0,.1),new int[6],new double[26]));
    }
    @Test void nativeArraysAreDefensivelyCopiedAndRawFlagsRetained() {
        var d=data(-2,3,0,5,1,0);double[] c=d.constants();int[] flags=d.toNativeIcons();c[0]=999;flags[0]=0;
        assertEquals(.05,d.value(MQ));assertEquals(-2,d.rawFlag(Regfmb1Data.Flag.WFLAG));
        assertTrue(d.enabled(Regfmb1Data.Flag.WFLAG));assertFalse(d.currentDroop());
        assertTrue(data(0,3,2,0,1,0).currentDroop());
    }
    @Test void invalidNumbersAndUndefinedDynamicParametersAreRejected() {
        var d=data(0,0,0,0,1,0);
        for(int i=0;i<25;i++){double[] c=d.constants();c[i]=Double.NaN;assertThrows(IllegalArgumentException.class,()->Regfmb1Data.nativePsse(d.sourceImpedance(),d.toNativeIcons(),c));}
        double[] c=d.constants();c[H.ordinal()]=0;assertThrows(IllegalArgumentException.class,()->Regfmb1Data.nativePsse(d.sourceImpedance(),d.toNativeIcons(),c));
        assertThrows(IllegalArgumentException.class,()->Regfmb1Data.nativePsse(Complex.ZERO,d.toNativeIcons(),d.constants()));
    }
    @Test void nativeEffectiveLimitsDoNotMutateTheSourceRecord() {
        var d=data(0,0,0,0,1,0);double[] c=d.constants();c[IMAXSS.ordinal()]=2;c[IMAXF.ordinal()]=1;c[VPLLFRZ.ordinal()]=.5;
        var corrected=Regfmb1Data.nativePsse(d.sourceImpedance(),d.toNativeIcons(),c);
        assertEquals(1,corrected.steadyCurrentLimit());assertEquals(2,corrected.faultCurrentLimit());
        assertEquals(2,corrected.value(IMAXSS));assertEquals(.1,corrected.pllFreezeVoltage());
    }
    @Test void initialVoltageReconstructsPhysicalPowerAtNonzeroTerminalAngle() {
        var d=data(0,0,0,0,1,0);Complex v=new Complex(1.01,.2),s=new Complex(.4,.2);
        Complex e=Regfmb1NetworkBoundary.initialInternalVoltage(d,v,s);
        assertEquals(0,v.multiply(e.subtract(v).divide(d.sourceImpedance()).conjugate()).subtract(s).abs(),1e-14);
        assertThrows(IllegalArgumentException.class,()->Regfmb1NetworkBoundary.initialInternalVoltage(d,Complex.ZERO,s));
    }
    @Test void faultCurrentCapPreservesDirectionAndNortonCancellation() {
        var d=data(0,0,0,0,1,0);Complex v=new Complex(.1,-.2),e=new Complex(1.2,.3);
        Complex raw=e.subtract(v).divide(d.sourceImpedance()),limited=Regfmb1NetworkBoundary.current(d,e,v);
        assertEquals(2,limited.abs(),1e-14);assertEquals(raw.getArgument(),limited.getArgument(),1e-14);
        Complex physical=Regfmb1NetworkBoundary.limitedInternalVoltage(d,e,v).subtract(v).divide(d.sourceImpedance());
        assertEquals(0,physical.subtract(limited).abs(),1e-14);
        Complex small=v.add(d.sourceImpedance().multiply(new Complex(.1,.2)));
        assertEquals(0,Regfmb1NetworkBoundary.current(d,small,v).subtract(new Complex(.1,.2)).abs(),1e-14);
    }
    @Test void frameRotationAndSteadyPriorityLimitsFollowTheDeclaredEquations() {
        assertEquals(0,Regfmb1NetworkBoundary.toDq(new Complex(0,1),Math.PI/2).subtract(Complex.ONE).abs(),1e-14);
        var q=Regfmb1NetworkBoundary.limits(data(0,0,0,0,1,0),.3,-.4,1);
        assertEquals(Math.sqrt(1.44-.16),q.idmax(),1e-14);assertEquals(1.08,q.iqmax(),1e-14);
        var p=Regfmb1NetworkBoundary.limits(data(0,0,0,1,1,0),.3,-.4,1);
        assertEquals(1.08,p.idmax(),1e-14);assertEquals(Math.sqrt(1.44-.09),p.iqmax(),1e-14);
        var zero=Regfmb1NetworkBoundary.limits(data(0,0,0,0,1,0),.3,2,0);
        assertEquals(0,zero.idmax(),0);assertEquals(zero.emax(),zero.emin(),0);
    }
}
