package org.interpss.dstab.control.gov.wecc.wsieg1;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.*;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.util.IntegrationStateMonitor.Stage;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Shared equations, explicit history acceptance and one turbine owner under both methods. */
public class Wsieg1SharedStateTest {
    private static final double H=.0005;
    private static final String[] NAMES=Wsieg1SharedSmibTest.NAMES;
    private static volatile double sink;
    @BeforeAll static void setup(){IpssCorePlugin.init();}

    private static Wsieg1Governor model(String fixture,boolean bypass)throws Exception {
        Path root=Wsieg1SharedSmibTest.CASE;String name=fixture.equals("standard")?"wsieg1":"wsieg1-"+fixture;
        var context=new PSSEMultiFileLoader().loadDStab(root.resolve(fixture.equals("dual")?"dual-network.raw":"network.raw").toString(),root.resolve(name+".dyr").toString());
        var machine=context.getDStabilityNet().getMachine("Bus1-mach1");var g=(Wsieg1Governor)machine.getGovernor();
        if(bypass) {
            double[] c=g.getConstants();for(int i:new int[]{1,2,8,11,14,17})c[i]=0.;
            String id=g.getId();machine.getControllerList().remove(g);
            g=new Wsieg1Governor(id+"_bypass",c);g.setMachine(machine);
        }
        machine.setPm(.4);machine.setPe(.4);machine.setSpeed(1.);assertTrue(g.initStates(machine.getDStabBus(),machine));
        if(fixture.equals("dual")) {
            var secondary=context.getDStabilityNet().getMachine("Bus2-mach1");assertTrue(secondary.getGovernor().initStates(secondary.getDStabBus(),secondary));
        }
        return g;
    }

    @Test void pureRhsMatchesOrdinaryEvaluationWithoutAdvancingStateOrHistory()throws Exception {
        for(String profile:List.of("standard","curve"))for(boolean bypass:new boolean[]{false,true}) {
            var g=model(profile,bypass);var ordinary=model(profile,bypass);g.getMachine().setSpeed(1.0004);ordinary.getMachine().setSpeed(1.0004);
            double[] values=new double[10],rates=new double[10];Arrays.fill(values,71.);Arrays.fill(rates,93.);
            var states=g.getIntegrationStates();var history=g.getIntegrationHistories();g.readStaggeredState(values,2);
            assertTrue(ordinary.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,ordinary.getMachine(),0));
            for(int repeat=0;repeat<10;repeat++) {
                g.evaluateStaggered(H,rates,2);
                for(int i=0;i<6;i++){assertEquals(states.get(NAMES[i]),values[i+2],0.);assertEquals(ordinary.getStateDerivativeSnapshot().rates().get(NAMES[i]),rates[i+2],0.);}
                assertEquals(states,g.getIntegrationStates());assertEquals(history,g.getIntegrationHistories());
                assertEquals(ordinary.getOutput(ordinary.getMachine()),g.getOutput(g.getMachine()),0.);
            }
            assertEquals(71.,values[0]);assertEquals(71.,values[9]);assertEquals(93.,rates[0]);assertEquals(93.,rates[9]);
            if(bypass)for(int i:new int[]{0,2,3,4,5})assertEquals(0.,rates[i+2],0.);
        }
    }

    @Test void externalModifiedEulerUsesTheSameEquationsProjectionAndAcceptedHistories()throws Exception {
        for(String profile:List.of("standard","curve"))for(boolean bypass:new boolean[]{false,true}) {
            var shared=model(profile,bypass);var ordinary=model(profile,bypass);
            double[] start=new double[6],a=new double[6],b=new double[6],trial=new double[6];
            for(int tick=0;tick<1000;tick++) {
                double speed=1.+.0004*Math.sin(tick*.017);shared.getMachine().setSpeed(speed);ordinary.getMachine().setSpeed(speed);
                if(tick==100||tick==500){double increment=tick==100?.03:-.06;shared.setRefPoint(shared.getRefPoint()+increment);ordinary.setRefPoint(ordinary.getRefPoint()+increment);}
                shared.readStaggeredState(start,0);shared.evaluateStaggered(H,a,0);
                for(int i=0;i<6;i++)trial[i]=start[i]+H*a[i];shared.applyStaggeredState(trial,0,H);shared.evaluateStaggered(H,b,0);
                for(int i=0;i<6;i++)trial[i]=start[i]+.5*H*(a[i]+b[i]);shared.applyStaggeredState(trial,0,H);shared.completeStaggeredStep(H);
                assertTrue(ordinary.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,ordinary.getMachine(),0));assertTrue(ordinary.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,ordinary.getMachine(),1));
                assertEquals(ordinary.getIntegrationStates(),shared.getIntegrationStates());assertEquals(ordinary.getIntegrationHistories(),shared.getIntegrationHistories());
                assertEquals(ordinary.getOutput(ordinary.getMachine()),shared.getOutput(shared.getMachine()),0.);
            }
        }
    }

    @Test void limiterProjectionAndTrialRollbackCannotCommitBacklashOrHysteresis()throws Exception {
        var g=model("curve",false);double[] initial=new double[6],trial=new double[6],rates=new double[6];g.readStaggeredState(initial,0);
        System.arraycopy(initial,0,trial,0,6);trial[1]=g.getEffectiveMaximum()+1.;var history=g.getIntegrationHistories();
        g.applyStaggeredState(trial,0,H);g.setRefPoint(g.getEffectiveMaximum()+1.);g.evaluateStaggered(H,rates,0);
        assertEquals(g.getEffectiveMaximum(),g.getValvePosition(),0.);assertEquals(0.,rates[1],0.);assertEquals(history,g.getIntegrationHistories());
        g.applyStaggeredState(initial,0,H);assertEquals(history,g.getIntegrationHistories());
        trial[1]=g.getEffectiveMinimum()-1.;g.applyStaggeredState(trial,0,H);g.setRefPoint(g.getEffectiveMinimum()-1.);g.evaluateStaggered(H,rates,0);
        assertEquals(g.getEffectiveMinimum(),g.getValvePosition(),0.);assertEquals(0.,rates[1],0.);
        g.getMachine().setSpeed(1.0004);g.completeStaggeredStep(H);assertNotEquals(history,g.getIntegrationHistories());
        var accepted=g.getIntegrationHistories();g.evaluateStaggered(H,rates,0);assertEquals(accepted,g.getIntegrationHistories());
        assertThrows(UnsupportedOperationException.class,()->accepted.clear());
    }

    @Test void invalidTrialsOwnershipChangesAndUnfinishedPredictorsFailBeforeSharedMutation()throws Exception {
        var g=model("standard",false);double[] values=new double[6],rates=new double[6];g.readStaggeredState(values,0);var before=g.getIntegrationStates();
        values[0]=.2;values[5]=Double.NaN;assertThrows(IllegalArgumentException.class,()->g.applyStaggeredState(values,0,H));assertEquals(before,g.getIntegrationStates());
        assertThrows(IndexOutOfBoundsException.class,()->g.readStaggeredState(new double[5],0));
        for(double h:new double[]{0.,-.1,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->g.evaluateStaggered(h,rates,0));
        var m=g.getMachine();assertTrue(g.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,m,0));assertThrows(IllegalStateException.class,()->g.readStaggeredState(values,0));
        assertTrue(g.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,m,1));g.readStaggeredState(values,0);
        m.setPm(Double.NaN);assertFalse(g.initStates(m.getDStabBus(),m));assertEquals(Stage.NOT_EVALUATED,g.getStateDerivativeSnapshot().stage());
        assertThrows(IllegalStateException.class,()->g.evaluateStaggered(H,rates,0));m.setPm(.4);assertTrue(g.initStates(m.getDStabBus(),m));
        var replacement=new Wsieg1Governor("replacement",g.getConstants());m.getControllerList().remove(g);replacement.setMachine(m);
        assertThrows(IllegalStateException.class,()->g.readStaggeredState(values,0));
    }

    @Test void bypassedCompensationPublishesLiveInputWithoutAdvancingHistory()throws Exception {
        for(boolean shared:new boolean[]{false,true}) {
            var g=model("standard",true);double[] published=new double[8],rates=new double[6];
            for(double speed:new double[]{1.0004,.9997,1.}) {
                g.getMachine().setSpeed(speed);
                if(shared)g.evaluateStaggered(H,rates,0);
                else assertTrue(g.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),0));
                var states=g.getIntegrationStates();var histories=g.getIntegrationHistories();
                var derivatives=g.getStateDerivativeSnapshot();Arrays.fill(published,79.);
                for(int repeat=0;repeat<5;repeat++) {
                    g.readPublishedState(published,1);
                    assertEquals(20.*(speed-1.),published[1],1e-15);
                    assertEquals(states,g.getIntegrationStates());assertEquals(histories,g.getIntegrationHistories());
                    assertEquals(derivatives,g.getStateDerivativeSnapshot());
                }
                assertEquals(0.,states.get(NAMES[0]),0.);
                assertEquals(0.,derivatives.rates().get(NAMES[0]),0.);
                assertEquals(79.,published[0]);assertEquals(79.,published[7]);
            }
        }
    }

    @Test void publishedCompensationAndBypassedDiagnosticsDoNotChangeStoredCoordinates()throws Exception {
        var g=model("standard",false);double[] values=new double[6],published=new double[10];g.readStaggeredState(values,0);double initial=values[5];
        values[0]=.003;values[4]+=.02;g.applyStaggeredState(values,0,H);var states=g.getIntegrationStates();var history=g.getIntegrationHistories();
        Arrays.fill(published,79.);g.readPublishedState(published,2);double[] c=g.getConstants();
        assertEquals((1-c[2]/c[1])*values[0],published[2],0.);assertEquals(states.get("Turbine 4"),published[7],0.);
        assertNotEquals(initial,published[7]);assertEquals(states,g.getIntegrationStates());assertEquals(history,g.getIntegrationHistories());
        assertEquals(79.,published[0]);assertEquals(79.,published[9]);
    }

    @Test void derivativeSnapshotsRetainTheirEvaluationCoordinatesAndAcceptedHistory()throws Exception {
        var g=model("standard",false);var initial=g.getStateDerivativeSnapshot();assertEquals(Stage.INITIALIZATION,initial.stage());
        initial.rates().values().forEach(rate->assertEquals(0.,rate,1e-14));var states=g.getIntegrationStates();var history=g.getIntegrationHistories();
        g.getMachine().setSpeed(1.0004);double[] rates=new double[6];g.evaluateStaggered(H,rates,0);var snapshot=g.getStateDerivativeSnapshot();
        assertEquals(Stage.STAGGERED,snapshot.stage());assertEquals(states,snapshot.evaluationStates());assertEquals(history,g.getIntegrationHistories());
        for(int i=0;i<6;i++)assertEquals(rates[i],snapshot.rates().get(NAMES[i]),0.);
        assertEquals(states,initial.evaluationStates());assertThrows(UnsupportedOperationException.class,()->snapshot.rates().clear());
    }

    @Test void passiveSecondaryOwnsNoStatesAndCannotAdvanceTheSharedTurbine()throws Exception {
        var g=model("dual",false);var net=(com.interpss.dstab.BaseDStabNetwork<?,?>)g.getMachine().getDStabBus().getNetwork();var secondary=net.getMachine("Bus2-mach1");
        assertTrue(Wsieg1Governor.isSecondaryShaft(secondary.getGovernor()));var passive=(StaggeredEulerParticipant)secondary.getGovernor();assertEquals(0,passive.staggeredStateCount());
        var states=g.getIntegrationStates();var history=g.getIntegrationHistories();secondary.setSpeed(.99);
        for(int repeat=0;repeat<10;repeat++){passive.readStaggeredState(new double[0],0);passive.evaluateStaggered(H,new double[0],0);passive.applyStaggeredState(new double[0],0,H);passive.completeStaggeredStep(H);}
        assertEquals(states,g.getIntegrationStates());assertEquals(history,g.getIntegrationHistories());assertEquals(0,g.getStaggeredEvaluationCount());
        assertEquals(g.getOutput(g.getMachine()),secondary.getGovernor().getOutput(secondary),1e-14);
        assertThrows(IllegalArgumentException.class,()->g.attachSecondaryShaft(net.getMachine("Bus3-mach1")));
    }

    @Test void warmedNumericalUpdatesReuseAllStateRateAndObservationBuffers()throws Exception {
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();assertTrue(bean.isThreadAllocatedMemorySupported());bean.setThreadAllocatedMemoryEnabled(true);
        for(boolean staggered:new boolean[]{false,true})for(boolean bypass:new boolean[]{false,true}) {
            var g=model("curve",bypass);double[] x=new double[6],rates=new double[6],published=new double[6];
            run(g,staggered,x,rates,published,20000);long id=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(id);
            run(g,staggered,x,rates,published,20000);long allocated=bean.getThreadAllocatedBytes(id)-before;
            System.out.println("WSIEG1 warmed numerical allocation staggered="+staggered+" bypass="+bypass+" bytes="+allocated);assertTrue(allocated<1024,"No per-step numerical allocation: "+allocated);
        }
    }
    private static void run(Wsieg1Governor g,boolean shared,double[] values,double[] rates,double[] published,int count) {
        var m=g.getMachine();for(int tick=0;tick<count;tick++) {
            m.setSpeed(1.+.0004*Math.sin(tick*.017));
            if(shared){g.readStaggeredState(values,0);g.evaluateStaggered(H,rates,0);for(int i=0;i<6;i++)values[i]+=H*rates[i];g.applyStaggeredState(values,0,H);g.completeStaggeredStep(H);}
            else {g.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,m,0);g.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,m,1);}
            g.readPublishedState(published,0);sink=published[1]+g.getOutput(m);
        }
    }
}
