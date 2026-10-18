package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import static org.interpss.dstab.renewable.Regfmc1Data.Parameter.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;

class Regfmc1FoundationTest {
    private static Regfmc1Data data(int priority){return Regfmc1Data.nativePsse(new Complex(.015,.23),new int[]{1,priority,1},
        new double[]{.4,.35,3,.2,-.2,.025,.35,.02,.01,.02,1.5,0,100,3,.05,1.35,-1.35,1.5,-1.5,.2,-.35,.025,.3,.8,-.8,.6,-.6,.9,0,2,.15,-.15,.9,1.2});}
    @Test void exactCardinalityOrderAndDefensiveCopies() {
        var d=data(0);assertEquals(34,d.constants().length);assertEquals(3,d.toNativeIcons().length);
        assertEquals(.35,d.value(KPE));assertEquals(1.5,d.value(H));assertEquals(.9,d.value(KF));
        assertEquals(11,Regfmc1Data.STATE_COUNT);assertEquals(27,Regfmc1Data.VAR_COUNT);
        double[] c=d.constants();int[] f=d.toNativeIcons();c[0]=5;f[0]=0;
        assertEquals(.4,d.value(MQ));assertTrue(d.enabled(Regfmc1Data.Flag.FFLAG));
        assertThrows(IllegalArgumentException.class,()->Regfmc1Data.nativePsse(d.sourceImpedance(),new int[4],d.constants()));
        assertThrows(IllegalArgumentException.class,()->Regfmc1Data.nativePsse(d.sourceImpedance(),d.toNativeIcons(),new double[35]));
    }
    @Test void documentedCorrectionsRetainRawValues() {
        var d=data(0);double[] c=d.constants();c[KF.ordinal()]=0;c[VMIN.ordinal()]=-2;c[TE.ordinal()]=.0001;
        var v=Regfmc1Data.nativePsse(d.sourceImpedance(),new int[]{-3,2,0},c);
        assertTrue(v.enabled(Regfmc1Data.Flag.FFLAG));assertTrue(v.enabled(Regfmc1Data.Flag.PQFLAG));assertFalse(v.enabled(Regfmc1Data.Flag.VFLAG));
        assertEquals(-3,v.rawFlag(Regfmc1Data.Flag.FFLAG));assertEquals(1,v.currentPriorityFactor());assertEquals(.01,v.minimumGflVoltage());
        assertEquals(.001,v.internalVoltageTimeConstant(.0005));assertEquals(.0001,v.value(TE));
        assertEquals(1.02,v.gflVoltageReference(1.02));assertEquals(0,v.value(VREF0));
    }
    @Test void rejectsNonfiniteAndUndefinedParametersWithoutGuessingVendorCorrections() {
        var d=data(0);
        for(int i=0;i<34;i++){double[] c=d.constants();c[i]=Double.NaN;assertThrows(IllegalArgumentException.class,()->Regfmc1Data.nativePsse(d.sourceImpedance(),d.toNativeIcons(),c));}
        for(var p:new Regfmc1Data.Parameter[]{MQ,KIE,TE,H,MP,IMAX}){double[] c=d.constants();c[p.ordinal()]=0;assertThrows(IllegalArgumentException.class,()->Regfmc1Data.nativePsse(d.sourceImpedance(),d.toNativeIcons(),c));}
        double[] c=d.constants();c[KF.ordinal()]=1.1;assertThrows(IllegalArgumentException.class,()->Regfmc1Data.nativePsse(d.sourceImpedance(),d.toNativeIcons(),c));
        assertThrows(IllegalArgumentException.class,()->Regfmc1Data.nativePsse(Complex.ZERO,d.toNativeIcons(),d.constants()));
        assertThrows(IllegalArgumentException.class,()->d.internalVoltageTimeConstant(0));
        assertThrows(IllegalArgumentException.class,()->d.gflVoltageReference(Double.NaN));
    }
    @Test void gflDeadbandPowerBoundsAndTerminalFrame() {
        var g=Regfmc1NetworkBoundary.gridFollowing(data(0),.4,.2,1,1,Math.PI/2);
        assertEquals(.4,g.ip());assertEquals(.2,g.iq());assertEquals(0,g.voltageInjection());
        assertEquals(0,g.current().subtract(new Complex(.2,.4)).abs(),1e-14);
        var low=Regfmc1NetworkBoundary.gridFollowing(data(0),2,-2,.8,1,0);
        assertEquals(.8/.9,low.ip(),1e-14);assertEquals(-.6/.9+.1,low.iq(),1e-14);assertEquals(.1,low.voltageInjection(),1e-14);
        var high=Regfmc1NetworkBoundary.gridFollowing(data(0),-2,2,1.2,1,0);
        assertEquals(-.8/1.2,high.ip(),1e-14);assertEquals(.6/1.2-.1,high.iq(),1e-14);
        assertThrows(IllegalArgumentException.class,()->Regfmc1NetworkBoundary.gridFollowing(data(0),Double.NaN,0,1,1,0));
    }
    @Test void priorityUsesUnclippedReferenceAndHandlesBothSigns() {
        // Large voltage injection makes raw Iq exceed Imax: remaining Ip radius is zero,
        // even though the priority limit clips actual Iq below Imax.
        var q=Regfmc1NetworkBoundary.gridFollowing(data(0),.8,.6,0,1.02,0);
        assertEquals(0,q.ipmax());assertEquals(0,q.ip());assertEquals(1.08,q.iq(),1e-14);
        var p=Regfmc1NetworkBoundary.gridFollowing(data(1),-.8,-.6,.9,.9,0);
        assertEquals(1.08,p.ipmax(),1e-14);assertEquals(Math.sqrt(1.44-Math.pow(.8/.9,2)),p.iqmax(),1e-14);
        assertTrue(p.ip()<0);assertTrue(p.iq()<0);
    }
    @Test void hybridScalesBothBranchesTogetherAndReconstructsNortonVoltage() {
        var d=data(0);Complex v=new Complex(.1,.2),egfm=new Complex(1.1,-.3),gfl=new Complex(.3,-.4);
        Complex raw=egfm.subtract(v).divide(d.sourceImpedance()).add(gfl);
        var b=Regfmc1NetworkBoundary.hybrid(d,egfm,v,gfl);
        assertTrue(b.currentRatio()>1);assertEquals(1.2,b.totalCurrent().abs(),1e-14);
        assertEquals(raw.getArgument(),b.totalCurrent().getArgument(),1e-14);
        assertEquals(0,b.gflCurrent().subtract(gfl.divide(b.currentRatio())).abs(),1e-14);
        assertEquals(0,b.internalVoltage().subtract(v).divide(d.sourceImpedance()).subtract(b.gfmCurrent()).abs(),1e-14);
        var zero=Regfmc1NetworkBoundary.hybrid(d,v,v,Complex.ZERO);assertEquals(0,zero.currentRatio());assertEquals(0,zero.totalCurrent().abs());
        var cancel=Regfmc1NetworkBoundary.hybrid(d,v.add(d.sourceImpedance().multiply(gfl.negate())),v,gfl);
        assertEquals(0,cancel.totalCurrent().abs(),1e-14);assertEquals(0,cancel.gflCurrent().subtract(gfl).abs(),1e-14);
    }
    @Test void gfmIsZeroAtEquilibriumWhileGflCarriesMachinePower() {
        var d=data(0);Complex terminal=new Complex(1,.2),power=new Complex(.4,.2);
        var g=Regfmc1NetworkBoundary.gridFollowing(d,power.getReal(),power.getImaginary(),terminal.abs(),terminal.abs(),terminal.getArgument());
        var b=Regfmc1NetworkBoundary.hybrid(d,terminal,terminal,g.current());
        assertEquals(0,b.gfmCurrent().abs());assertEquals(0,terminal.multiply(b.totalCurrent().conjugate()).subtract(power).abs(),1e-14);
        assertTrue(b.currentRatio()<1);
    }
}
