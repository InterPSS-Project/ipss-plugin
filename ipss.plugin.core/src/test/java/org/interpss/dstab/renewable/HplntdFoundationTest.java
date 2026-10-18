package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class HplntdFoundationTest {
    private static final HplntdAggregation.Key A=new HplntdAggregation.Key("Bus1","1"),B=new HplntdAggregation.Key("Bus4","1");
    private static List<HplntdAggregation.Device> devices(boolean second) {
        return List.of(new HplntdAggregation.Device(A,50,1,2,20,10,true),new HplntdAggregation.Device(B,100,2,1,30,5,second));
    }
    private static double[] constants() {
        return new double[]{.02,1,5,0,.05,.7,0,.05,.02,1,-1,0,0,1,-1,1,.1,.02,-.0006,.0006,99,-99,2,-1,.1,20,20,
                1.08,.95,1,-1,99,-99,99,-99,99,-99,.8,-.8,99,-99,1,-1,.02,-.25,-.5,.3,.4,.5,.1,.06,120,1,.02,.8,.015,-.015,0,99,-99,99};
    }
    private static HplntdData data(double[] c) {
        return new HplntdData(3,new RepccData.Branch(2,3,"1"),1,0,1,1,1,0,new RepccData.Branch(0,0,"0"),
                Collections.nCopies(10,new RepccData.Shunt(0,"0")),3,1,c);
    }
    @Test void exactSourceLayoutsAndDefensiveDataStorage() {
        assertEquals(35,HplntdData.ICON_COUNT);assertEquals(61,HplntdData.Parameter.values().length);assertEquals(13,HplntdData.STATE_COUNT);assertEquals(45,HplntdData.VAR_COUNT);
        assertEquals(504,HplntdData.USER_BUS_TYPE);assertEquals("HPLNTDU",HplntdData.NATIVE_MODEL);
        double[] c=constants();var d=data(c);c[0]=9;d.constants()[0]=8;assertEquals(.02,d.value(HplntdData.Parameter.TFLTR));
        assertThrows(UnsupportedOperationException.class,()->d.shunts().clear());
        assertEquals(9,ReaxdData.ICON_COUNT);assertEquals(107,ReaxdData.USER_MODEL_TYPE);assertEquals(8,ReaxdData.Parameter.values().length);
        assertEquals(2,ReaxdData.STATE_COUNT);assertEquals(7,ReaxdData.VAR_COUNT);
    }
    @Test void malformedPlantNumbersTimesAndBoundsAreRejectedWithoutCorrection() {
        assertThrows(IllegalArgumentException.class,()->data(new double[60]));
        for(var parameter:List.of(HplntdData.Parameter.PLANT_MVA,HplntdData.Parameter.TFLTR)) {
            double[] c=constants();c[parameter.ordinal()]=-1;assertThrows(IllegalArgumentException.class,()->data(c));
        }
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY}) {
            double[] c=constants();c[0]=bad;assertThrows(IllegalArgumentException.class,()->data(c));
        }
        double[] c=constants();c[HplntdData.Parameter.VFRZH.ordinal()]=.6;assertThrows(IllegalArgumentException.class,()->data(c));
    }
    @Test void downstreamRelativeLinksAndMinimumTimesAreExplicit() {
        int[] indexes={37,38,39,42,43,44,45};double[] c={1,0,2,.0001,2,-1,1,-1};
        var d=new ReaxdData(2,"HPLNTDU",indexes,c);indexes[0]=0;c[0]=5;
        assertTrue(d.validRelativeVariables(45));assertFalse(d.validRelativeVariables(44));assertEquals(1,d.value(ReaxdData.Parameter.KZ));
        d.relativeVariables()[0]=0;assertEquals(37,d.relativeVariables()[0]);
        assertEquals(.001,d.activeTime(.0005));assertEquals(.001,d.reactiveTime(.0005));assertEquals(.0005,d.activeTime(.00025));
        c[1]=-.1;var shortTime=new ReaxdData(2,"HPLNTDU",indexes,c);
        assertEquals(-.1,shortTime.value(ReaxdData.Parameter.TZ));assertEquals(.001,shortTime.activeTime(.0005));
        assertThrows(IllegalArgumentException.class,()->d.activeTime(0));
        assertFalse(new ReaxdData(2,"HPLNTDU",new int[7],c).validRelativeVariables(45));
        assertThrows(IllegalArgumentException.class,()->new ReaxdData(2,"MODELNAME_TOO_LONG",indexes,c));
    }
    @Test void unequalBasesAndParticipationConservePhysicalPowerOnEachAxis() {
        var a=new HplntdAggregation(0,devices(true));var s=a.refresh(devices(true));
        assertEquals(150,s.plantMva());assertEquals(250,s.weightedKz());assertEquals(200,s.weightedKw());
        assertEquals(1.0/3,s.initialActive(),1e-15);assertEquals(.1,s.initialReactive(),1e-15);
        var x=s.dispatch(A,.1,.04);var y=s.dispatch(B,.1,.04);assertTrue(x.enabled());assertTrue(y.enabled());
        assertEquals(.3,x.rawActive(),1e-15);assertEquals(.15,y.rawActive(),1e-15);
        assertEquals(.06,x.activeIncrement(),1e-15);assertEquals(.12,y.activeIncrement(),1e-15);
        assertEquals(15,50*x.activeIncrement()+100*y.activeIncrement(),1e-14);
        assertEquals(6,50*x.reactiveIncrement()+100*y.reactiveIncrement(),1e-14);
        assertThrows(UnsupportedOperationException.class,()->s.shares().clear());
    }
    @Test void explicitPlantBaseIsDistinctFromTheCalculatedDownstreamSum() {
        var s=new HplntdAggregation(200,devices(true)).refresh(devices(true));
        assertEquals(200,s.plantMva());assertEquals(150,s.calculatedMva());assertEquals(.25,s.initialActive());
        assertEquals(20,50*s.dispatch(A,.1,0).activeIncrement()+100*s.dispatch(B,.1,0).activeIncrement(),1e-14);
        var paused=s.pausedView();assertEquals(200,paused.plantMva());assertEquals(0,paused.calculatedMva());assertEquals(0,paused.weightedKz());assertEquals(0,paused.weightedKw());
        assertEquals(s.initialActive(),paused.initialActive());
    }
    @Test void tripAndReturnRecalculateLiveWeightsButNeverReinitializeOffsets() {
        for(double specified:new double[]{0,200}) {
            var a=new HplntdAggregation(specified,devices(true));var initial=a.refresh(devices(true));var tripped=a.refresh(devices(false));
            assertEquals(specified==0?50:200,tripped.plantMva());assertEquals(50,tripped.calculatedMva());
            assertEquals(1,tripped.shares().get(A).activeWeight());assertEquals(1,tripped.shares().get(A).reactiveWeight());
            assertFalse(tripped.dispatch(B,.1,.2).enabled(),"An ignored unit holds its output; it does not receive a zero command");
            assertEquals(initial.initialActive(),tripped.initialActive());assertEquals(initial.initialReactive(),tripped.initialReactive());
            assertEquals(initial,a.refresh(devices(true)));
        }
    }
    @Test void zeroAxisWeightIsNotTheSameAsAnIgnoredUnit() {
        var ds=List.of(new HplntdAggregation.Device(A,50,0,1,20,10,true),new HplntdAggregation.Device(B,100,0,0,30,5,true));
        var s=new HplntdAggregation(0,ds).refresh(ds);assertEquals(50,s.plantMva());
        var out=s.dispatch(A,.2,.3);assertTrue(out.enabled());assertEquals(0,out.activeIncrement());assertEquals(.3,out.reactiveIncrement());
        assertFalse(s.dispatch(B,.2,.3).enabled());
    }
    @Test void noLiveAutoBaseIgnoresCommandsAndKeepsTheOriginalInitialCoordinate() {
        var a=new HplntdAggregation(0,devices(true));var ds=devices(false).stream().map(d->new HplntdAggregation.Device(d.key(),d.mva(),d.kz(),d.kw(),d.activeMW(),d.reactiveMvar(),false)).toList();
        var s=a.refresh(ds);assertTrue(s.ignored());assertEquals(0,s.plantMva());assertFalse(s.dispatch(A,.2,.3).enabled());
        assertEquals(1.0/3,s.initialActive(),1e-15);
        var empty=new HplntdAggregation(0,List.of()).refresh(List.of());assertTrue(empty.ignored());assertEquals(0,empty.initialActive());
    }
    @Test void invalidMembershipAndNumericalOverflowFailAtomically() {
        var a=new HplntdAggregation(0,devices(true));
        assertThrows(IllegalArgumentException.class,()->a.refresh(List.of(devices(true).getFirst())));
        assertThrows(IllegalArgumentException.class,()->new HplntdAggregation(0,List.of(devices(true).getFirst(),devices(true).getFirst())));
        assertThrows(IllegalArgumentException.class,()->new HplntdAggregation(Double.NaN,devices(true)));
        assertThrows(IllegalArgumentException.class,()->new HplntdAggregation.Device(A,50,-1,1,20,10,true));
        assertThrows(IllegalArgumentException.class,()->new HplntdAggregation(0,List.of(new HplntdAggregation.Device(A,Double.MAX_VALUE,2,1,0,0,true))));
        assertThrows(IllegalArgumentException.class,()->a.refresh(devices(true)).dispatch(A,Double.NaN,0));
        assertEquals(150,a.refresh(devices(true)).plantMva());
    }
}
