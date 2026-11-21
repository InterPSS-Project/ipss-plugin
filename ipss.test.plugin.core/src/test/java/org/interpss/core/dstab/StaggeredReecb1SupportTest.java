package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import com.interpss.dstab.renewable.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Two-step electrical controls preserve physical constraints and pure refresh semantics. */
class StaggeredReecb1SupportTest {
    private Reecb1Data data(int qFlag,int vFlag,int priority,double time) {
        return new Reecb1Data(0,0,vFlag,qFlag,priority,.8,1.2,time,0,0,0,10,-10,0,time,
                .44,-.44,1.1,.9,0,.1,0,2,time,.05,-.05,1,0,1.1,time);
    }
    private double[] coordinates(Reecb1Model m){return new double[]{m.getMeasuredVoltage(),m.getMeasuredActivePower(),m.getActivePowerOrder(),m.getReactiveCurrentState(),m.getQControlIntegral(),m.getVoltageControlIntegral()};}
    @Test void positiveTimeMeasurementRatesUseTheExistingEquationsAndStoreWeights() {
        var m=new Reecb1Model(data(0,1,1,.1));m.initialize(.8,.2,1.);
        double h=1./240.;m.stepTwoStep(h,.4,.1,.9,1.);
        assertEquals(1.+1.5*h*(-.1/.1),m.getMeasuredVoltage(),1e-12);
        assertEquals(.8+1.5*h*(-.4/.1),m.getMeasuredActivePower(),1e-12);
        assertEquals(.8,m.getActivePowerOrder(),0.);assertEquals(.2,m.getReactiveCurrentState(),1e-12);
    }
    @Test void observationRefreshDoesNotConsumeARebaseOrChangeContinuousCoordinates()throws Exception {
        var m=new Reecb1Model(data(0,1,1,.1));m.initialize(.8,.2,1.);m.rebaseNextTwoStep();
        var f=Reecb1Model.class.getDeclaredField("twoStep");f.setAccessible(true);Object h=f.get(m);
        var pending=h.getClass().getDeclaredField("eventPending");pending.setAccessible(true);
        double[] before=coordinates(m);double publishedP=m.getTwoStepIpcmd(),publishedQ=m.getTwoStepIqcmd();
        for(int i=0;i<20;i++)m.refreshTwoStep(.4,.1,.9,1.);
        assertArrayEquals(before,coordinates(m),0.);assertTrue(pending.getBoolean(h));
        assertEquals(publishedP,m.getTwoStepIpcmd(),0.);assertEquals(publishedQ,m.getTwoStepIqcmd(),0.);
        m.stepTwoStep(1./240.,.4,.1,.9,1.);assertFalse(pending.getBoolean(h));
    }
    @ParameterizedTest @CsvSource({"0,0,0","0,1,1","1,0,0","1,1,1"})
    void priorityCircleAndDipFreezeApplyToEveryControlBranch(int qFlag,int vFlag,int priority) {
        var m=new Reecb1Model(data(qFlag,vFlag,priority,.1));m.initialize(.8,.2,1.);
        double order=m.getActivePowerOrder(),outer=m.getQControlIntegral(),inner=m.getVoltageControlIntegral();
        m.stepTwoStep(1./240.,.4,.1,.5,1.);
        assertTrue(m.isVoltageDip());assertEquals(order,m.getActivePowerOrder(),0.);
        assertEquals(outer,m.getQControlIntegral(),0.);assertEquals(inner,m.getVoltageControlIntegral(),0.);
        assertTrue(m.getMeasuredVoltage()<1.);assertTrue(m.getMeasuredActivePower()<.8);
        assertTrue(Math.hypot(m.getTwoStepIpcmd(),m.getTwoStepIqcmd())<=1.1+1e-12);
        for(int i=0;i<240;i++)m.stepTwoStep(1./240.,.8,.2,1.,1.);
        for(double x:coordinates(m))assertTrue(Double.isFinite(x));
        assertTrue(Math.hypot(m.getTwoStepIpcmd(),m.getTwoStepIqcmd())<=1.1+1e-12);
    }
    @Test void collapsedLagsAndDirectVoltageBranchKeepTheirPhysicalReference() {
        var m=new Reecb1Model(data(1,0,0,0.));m.initialize(.8,.2,1.03);
        m.stepTwoStep(1./240.,.8,.2,1.04,1.);
        assertEquals(1.04,m.getMeasuredVoltage(),0.);assertEquals(.8,m.getMeasuredActivePower(),0.);
        assertEquals(0.,m.getQControlIntegral(),0.);
        assertTrue(m.getReactiveCurrentState()<.2/1.03,"Higher terminal voltage decreases capacitive command");
    }
    @Test void invalidIntervalsAreRejectedBeforeAnyStateAdvance() {
        var m=new Reecb1Model(data(0,1,1,.1));m.initialize(.8,.2,1.);var x=coordinates(m);
        for(double step:new double[]{-.01,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->m.stepTwoStep(step,.4,.1,.9,1.));
        assertArrayEquals(x,coordinates(m),0.);
    }
}
