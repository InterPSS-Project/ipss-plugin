package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper;
import com.interpss.dstab.controller.cml.annotate.util.CMLSymbolMapper;
import com.interpss.dstab.datatype.ExpCalculator;
import com.interpss.dstab.mach.Machine;

/** Public whole-graph numerical/ownership checks; no private case or fitted bounds. */
public class Esst1aWholeLocalIntegrationTest {
    private record Fixture(Machine machine,IEEE1981ST1Exciter exciter) { }
    private Fixture fixture(boolean leadlag,boolean prepared,int count) throws Exception {
        IpssCorePlugin.init();var builder=DStabBuilderTestFixture.createWithMachine();var net=builder.getDStabNetwork();
        assertTrue(com.interpss.core.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net).loadflow());
        var m=net.getMachine("Bus1-mach1");assertTrue(m.initStates(net.getDStabBus("Bus1")));
        var e=builder.addExcEsst1a("Bus1","1",1,1,leadlag?.012:.018,1e6,-1e6,
            leadlag?.06:0,leadlag?.32:0,leadlag?1:0,leadlag?1:0,
            leadlag?280:360,leadlag?.012:.06,1e6,-1e6,1e6,-1e6,0,leadlag?.006:.06,leadlag?.4:.5,0,99);
        assertTrue(e.initStates(net.getDStabBus("Bus1"),m));e.setModifiedEulerLocalIntegrationSteps(count);
        if(prepared){e.compileRuntimePlan();e.enableModifiedEulerEvaluation();}
        return new Fixture(m,e);
    }
    private void step(Fixture f,double dt,double voltage0,double voltage1) {
        f.machine.getDStabBus().setVoltageMag(voltage0);assertTrue(f.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,f.machine,0));
        f.machine.getDStabBus().setVoltageMag(voltage1);assertTrue(f.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,f.machine,1));
    }
    private static void same(Fixture a,Fixture b,double tolerance) {
        for(var entry:a.exciter.getNamedStateBlocks().entrySet()) {
            var x=entry.getValue();var y=b.exciter.getNamedStateBlocks().get(entry.getKey());
            assertEquals(x.getStateX(),y.getStateX(),tolerance,entry.getKey());
            assertEquals(((com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter)x).getDX_dt(),((com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter)y).getDX_dt(),tolerance,entry.getKey()+" derivative");
        }
    }
    @Test public void constantBoundaryWholeReplayMatchesSequentialSmallHeunSteps() throws Exception {
        for(boolean leadlag:new boolean[]{false,true}) {
            var coarse=fixture(leadlag,false,4);var small=fixture(leadlag,true,1);
            coarse.exciter.setRefPoint(coarse.exciter.getRefPoint()+.00001);
            small.exciter.setRefPoint(small.exciter.getRefPoint()+.00001);
            String entered=new com.google.gson.Gson().toJson(coarse.exciter.getData());double h=.0033333;
            for(int n=0;n<100;n++) {
                step(coarse,h,1.04,1.04);
                for(int i=0;i<4;i++)step(small,h/4,1.04,1.04);
                same(coarse,small,1e-11);
            }
            assertEquals(entered,new com.google.gson.Gson().toJson(coarse.exciter.getData()));
        }
    }
    @Test public void genericAndPreparedOwnersUseIdenticalWholeGraphStages() throws Exception {
        var a=fixture(true,false,4);var b=fixture(true,true,4);
        for(int i=0;i<50;i++) {
            double left=1.04+.001*Math.sin(i*.2),right=1.04+.001*Math.sin((i+1)*.2);
            a.machine.getDStabBus().setVoltageMag(left);b.machine.getDStabBus().setVoltageMag(left);
            assertTrue(a.exciter.nextStep(.0033333,DynamicSimuMethod.MODIFIED_EULER,a.machine,0));
            assertTrue(b.exciter.nextStep(.0033333,DynamicSimuMethod.MODIFIED_EULER,b.machine,0));same(a,b,0.);
            a.machine.getDStabBus().setVoltageMag(right);b.machine.getDStabBus().setVoltageMag(right);
            assertTrue(a.exciter.nextStep(.0033333,DynamicSimuMethod.MODIFIED_EULER,a.machine,1));
            assertTrue(b.exciter.nextStep(.0033333,DynamicSimuMethod.MODIFIED_EULER,b.machine,1));same(a,b,0.);
        }
    }
    @SuppressWarnings("unchecked")
    @Test public void zeroTimeObservationPreservesPendingOriginStatesAndDerivatives() throws Exception {
        var a=fixture(false,true,4);var b=fixture(false,false,4);double h=.0033333;
        a.exciter.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,a.machine,0);
        b.exciter.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,b.machine,0);
        var before=a.exciter.getModifiedEulerLocalIntegrationSnapshot();
        a.machine.getDStabBus().setVoltageMag(1.039);
        a.exciter.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,a.machine,0);
        a.exciter.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,a.machine,1);
        var after=a.exciter.getModifiedEulerLocalIntegrationSnapshot();assertEquals(true,after.get("pending"));
        Map<String,Object> old=(Map<String,Object>)before.get("blocks"),now=(Map<String,Object>)after.get("blocks");
        for(String key:old.keySet())for(int index:new int[]{1,2})assertEquals(((double[])old.get(key))[index],((double[])now.get(key))[index],0.);
        var oldOrigin=(Map<String,Object>)before.get("acceptedBlocks");var newOrigin=(Map<String,Object>)after.get("acceptedBlocks");
        for(String key:oldOrigin.keySet())assertArrayEquals((double[])oldOrigin.get(key),(double[])newOrigin.get(key),0.);
        ((double[])after.get("acceptedBoundary"))[0]=-999.;
        assertNotEquals(-999.,((double[])a.exciter.getModifiedEulerLocalIntegrationSnapshot().get("acceptedBoundary"))[0]);
        a.machine.getDStabBus().setVoltageMag(1.041);b.machine.getDStabBus().setVoltageMag(1.041);
        a.exciter.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,a.machine,1);b.exciter.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,b.machine,1);same(a,b,0.);
    }
    @Test public void boundaryPssIsSampledOncePerOuterStageNotPerLocalSubstep() throws Exception {
        var f=fixture(false,false,4);int[] reads={0};double[] value={.01};
        var pss=new com.interpss.dstab.mach.impl.MachineControllerImpl() {
            @Override public double getOutput(Machine machine){reads[0]++;return value[0];}
        };
        pss.setType(com.interpss.dstab.mach.MachineControllerType.STABILIZER);pss.setStatus(true);
        f.machine.getControllerList().add(pss);
        f.exciter.nextStep(.0033333,DynamicSimuMethod.MODIFIED_EULER,f.machine,0);assertEquals(1,reads[0]);
        value[0]=-.01;
        f.exciter.nextStep(.0033333,DynamicSimuMethod.MODIFIED_EULER,f.machine,1);assertEquals(2,reads[0]);
    }
    @Test public void rampCorrectorReplaysAcceptedTransducerRatherThanContinuingPredictor() throws Exception {
        var f=fixture(false,false,4);double h=.0033333,start=f.exciter.getSensedVoltage();
        f.exciter.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,f.machine,0);
        assertEquals(start,f.exciter.getSensedVoltage(),1e-14);
        f.machine.getDStabBus().setVoltageMag(1.06);
        f.exciter.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,f.machine,1);
        // Closed-form discrete transfer of m Heun stages under a linear ramp.
        // This checks the numerical contract, not a fitted continuous-solution tolerance.
        int m=4;double r=h/(m*.018),amplification=1-r+.5*r*r;
        double expected=1.06+(.5*r-1)*(1.06-start)/m*(1-Math.pow(amplification,m))/(1-amplification);
        assertEquals(expected,f.exciter.getSensedVoltage(),1e-13);
    }
    @Test public void heldInputsPreserveLeftBoundaryAndRestoreEndpointOwnership() throws Exception {
        var held = fixture(false, true, 4);
        var constant = fixture(false, false, 4);
        var mode = IEEE1981ST1Exciter.LocalInputSampling.HELD_AT_INTERVAL_START;
        held.exciter.setModifiedEulerLocalInputSampling(mode);
        double step = .0033333;
        double reference = held.exciter.getRefPoint();
        double nextReference = reference + .0002;
        double leftVoltage = 1.035;
        double rightVoltage = 1.062;

        held.machine.getDStabBus().setVoltageMag(leftVoltage);
        assertTrue(held.exciter.nextStep(step, DynamicSimuMethod.MODIFIED_EULER, held.machine, 0));
        assertThrows(IllegalStateException.class, () -> held.exciter.setModifiedEulerLocalInputSampling(
                IEEE1981ST1Exciter.LocalInputSampling.INTERPOLATED_ENDPOINTS));
        held.machine.getDStabBus().setVoltageMag(rightVoltage);
        held.exciter.setRefPoint(nextReference);
        assertTrue(held.exciter.nextStep(step, DynamicSimuMethod.MODIFIED_EULER, held.machine, 1));
        step(constant, step, leftVoltage, leftVoltage);
        same(held, constant, 1e-12);
        assertEquals(nextReference, held.exciter.getRefPoint(), 0.);
        assertEquals(rightVoltage, held.machine.getDStabBus().getVoltageMag(), 1e-12);

        assertTrue(held.exciter.nextStep(0., DynamicSimuMethod.MODIFIED_EULER, held.machine, 0));
        same(held, constant, 1e-12);
        held.exciter.clearDirectEvaluation();
        assertEquals(mode, held.exciter.getModifiedEulerLocalInputSampling());
    }

    @Test public void intervalConfigurationGraphAndMethodFailuresAreExplicit() throws Exception {
        var f=fixture(false,false,1);
        for(int count:new int[]{0,-1,129})assertThrows(IllegalArgumentException.class,()->f.exciter.setModifiedEulerLocalIntegrationSteps(count));
        ((DelayControlBlock)f.exciter.getNamedStateBlocks().get("trDelayBlock")).setLocalIntegrationSteps(2);
        assertThrows(IllegalStateException.class,()->f.exciter.setModifiedEulerLocalIntegrationSteps(4));
        ((DelayControlBlock)f.exciter.getNamedStateBlocks().get("trDelayBlock")).setLocalIntegrationSteps(1);
        f.exciter.setModifiedEulerLocalIntegrationSteps(4);
        assertThrows(IllegalStateException.class,()->f.exciter.nextStep(.0033333,DynamicSimuMethod.MODIFIED_EULER,f.machine,1));
        assertThrows(IllegalArgumentException.class,()->f.exciter.nextStep(.0033333,DynamicSimuMethod.RUNGE_KUTTA,f.machine,0));
        f.exciter.nextStep(.0033333,DynamicSimuMethod.MODIFIED_EULER,f.machine,0);
        assertThrows(IllegalStateException.class,()->f.exciter.setModifiedEulerLocalIntegrationSteps(2));
        assertThrows(IllegalStateException.class,f.exciter::clearDirectEvaluation);
        assertThrows(IllegalStateException.class,f.exciter::invalidateRuntimePlan);
        assertThrows(IllegalArgumentException.class,()->f.exciter.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,f.machine,1));
        f.exciter.nextStep(.0033333,DynamicSimuMethod.MODIFIED_EULER,f.machine,1);
        f.exciter.clearDirectEvaluation();f.exciter.setModifiedEulerLocalIntegrationSteps(1);
        var expression=new ExpCalculator("this.refPoint");CMLSymbolMapper.mapExpressionSymbolStr2Type(expression.getRecList());
        for(var w:f.exciter.getFieldWrapperList())if(w.getFieldName().equals("gainBlock"))((BlockFieldAnWrapper<?>)w).setInputExp(expression);
        assertThrows(IllegalStateException.class,()->f.exciter.setModifiedEulerLocalIntegrationSteps(4));
    }
    @Test public void finiteReferenceAndActiveLimiterInputsReplayTheirLinearBoundaryPath() throws Exception {
        var a=fixture(false,false,4);var b=fixture(false,true,1);double h=.0033333;
        double ref=a.exciter.getRefPoint();a.exciter.setVuel(.00001);b.exciter.setVuel(.00001);
        a.exciter.setVoel(50.);b.exciter.setVoel(50.);
        a.exciter.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,a.machine,0);
        a.exciter.setRefPoint(ref+.0002);a.exciter.setVuel(.00005);a.exciter.setVoel(40.);
        a.exciter.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,a.machine,1);
        for(int i=0;i<4;i++) {
            double l=(double)i/4,r=(double)(i+1)/4;
            b.exciter.setRefPoint(ref+.0002*l);b.exciter.setVuel(.00001+.00004*l);b.exciter.setVoel(50.-10.*l);
            b.exciter.nextStep(h/4,DynamicSimuMethod.MODIFIED_EULER,b.machine,0);
            b.exciter.setRefPoint(ref+.0002*r);b.exciter.setVuel(.00001+.00004*r);b.exciter.setVoel(50.-10.*r);
            b.exciter.nextStep(h/4,DynamicSimuMethod.MODIFIED_EULER,b.machine,1);
        }
        same(a,b,1e-11);assertEquals(ref+.0002,a.exciter.getRefPoint(),0.);
        assertEquals(.00005,a.exciter.vuel,0.);assertEquals(40.,a.exciter.voel,0.);
        assertTrue(a.exciter.isActive());
    }
    @Test public void absentLimiterTransitionIsRejectedWithoutDiscardingPendingOrigin() throws Exception {
        var f=fixture(false,false,4);double h=.0033333;
        f.exciter.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,f.machine,0);
        var before=f.exciter.getNamedStates();f.exciter.setVoel(30.);
        assertThrows(IllegalArgumentException.class,()->f.exciter.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,f.machine,1));
        assertEquals(true,f.exciter.getModifiedEulerLocalIntegrationSnapshot().get("pending"));
        assertEquals(before,f.exciter.getNamedStates());assertEquals(30.,f.exciter.voel,0.);
        assertTrue(f.exciter.isActive());
        f.exciter.setVoel(Double.POSITIVE_INFINITY);
        f.exciter.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,f.machine,1);
    }
    @Test public void reinitializationClearsPendingOriginRetainingConfiguredCount() throws Exception {
        var f=fixture(true,true,4);f.exciter.nextStep(.0033333,DynamicSimuMethod.MODIFIED_EULER,f.machine,0);
        assertTrue(f.exciter.initStates(f.machine.getDStabBus(),f.machine));
        assertEquals(4,f.exciter.getModifiedEulerLocalIntegrationSteps());
        assertEquals(false,f.exciter.getModifiedEulerLocalIntegrationSnapshot().get("pending"));
        step(f,.0033333,1.04,1.04);
    }
    @Test public void defaultOneRetainsCompatibleVersusPreparedTrajectoryAndConfiguration() throws Exception {
        var a=fixture(true,false,1);var b=fixture(true,true,1);
        for(int i=0;i<30;i++){double v=1.04+.001*Math.sin(i);step(a,.0005,v,v);step(b,.0005,v,v);same(a,b,1e-11);}
        assertEquals(1,a.exciter.getModifiedEulerLocalIntegrationSteps());
    }
}
