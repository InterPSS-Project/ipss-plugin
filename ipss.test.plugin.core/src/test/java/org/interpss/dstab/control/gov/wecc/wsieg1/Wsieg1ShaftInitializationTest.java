package org.interpss.dstab.control.gov.wecc.wsieg1;

import static org.junit.jupiter.api.Assertions.*;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.mach.Machine;

/** Each shaft's solved operating point determines its own mechanical-power normalization. */
public class Wsieg1ShaftInitializationTest {
    private static volatile double outputSink;
    @BeforeAll static void setup(){IpssCorePlugin.init();}
    record Pair(Wsieg1Governor owner,Machine primary,Machine secondary){}
    static Pair pair()throws Exception {
        var root=Wsieg1SharedSmibTest.CASE;
        var net=new PSSEMultiFileLoader().loadDStab(root.resolve("dual-network.raw").toString(),root.resolve("wsieg1-dual.dyr").toString()).getDStabilityNet();
        var primary=net.getMachine("Bus1-mach1");var secondary=net.getMachine("Bus2-mach1");
        var owner=(Wsieg1Governor)primary.getGovernor();owner.configureIntegrationStep(.0005);
        primary.setSpeed(1.);secondary.setSpeed(1.);primary.setPm(.24);secondary.setPm(.36);
        return new Pair(owner,primary,secondary);
    }
    static boolean initialize(Machine machine){return machine.getGovernor().initStates(machine.getDStabBus(),machine);}
    static void initializePair(Pair pair,boolean secondaryFirst) {
        assertTrue(initialize(secondaryFirst?pair.secondary:pair.primary));
        assertThrows(IllegalStateException.class,pair.owner::getSecondaryBaseScale);
        assertTrue(initialize(secondaryFirst?pair.primary:pair.secondary));
    }

    @Test void independentPowerAllocationIsPreparedInEitherInitializationOrder()throws Exception {
        for(boolean secondaryFirst:new boolean[]{false,true}) {
            var p=pair();initializePair(p,secondaryFirst);
            assertEquals(3.,p.owner.getSecondaryBaseScale(),1e-14);
            assertEquals(.24,p.owner.getOutput(p.primary),1e-15);
            assertEquals(.36,p.secondary.getGovernor().getOutput(p.secondary),1e-15);
            assertEquals(0,((StaggeredEulerParticipant)p.secondary.getGovernor()).staggeredStateCount());
            double[] state=new double[6];p.owner.readStaggeredState(state,0);state[2]=.2;state[3]=.5;state[4]=.4;
            p.owner.applyStaggeredState(state,0,.0005);
            assertEquals(3.*(.15*.2+.2*.5+.15*.4),p.secondary.getGovernor().getOutput(p.secondary),1e-15);
            assertEquals(3.,p.owner.getSecondaryBaseScale(),1e-14,"Moving states cannot renormalize a shaft");
        }
    }

    @Test void reinitializationPairsFreshPowersAndSupportsAZeroLoadedSecondary()throws Exception {
        for(boolean secondaryFirst:new boolean[]{false,true}) {
            var p=pair();initializePair(p,secondaryFirst);
            p.primary.setPm(.6);p.secondary.setPm(0.);initializePair(p,!secondaryFirst);
            assertEquals(0.,p.owner.getSecondaryBaseScale(),0.);assertEquals(0.,p.secondary.getGovernor().getOutput(p.secondary),0.);
            double[] state=new double[6];p.owner.readStaggeredState(state,0);state[2]+=.1;p.owner.applyStaggeredState(state,0,.0005);
            assertEquals(0.,p.secondary.getGovernor().getOutput(p.secondary),0.);
            p.primary.setPm(.2);p.secondary.setPm(.1);initializePair(p,secondaryFirst);
            assertEquals(1.,p.owner.getSecondaryBaseScale(),1e-14);
        }
    }

    @Test void repeatedPreparationDoesNotPairWithAnOldShaftOperatingPoint()throws Exception {
        var p=pair();initializePair(p,false);
        p.primary.setPm(.2);assertTrue(initialize(p.primary));p.primary.setPm(.4);assertTrue(initialize(p.primary));
        assertThrows(IllegalStateException.class,p.owner::getSecondaryBaseScale);
        p.secondary.setPm(.6);assertTrue(initialize(p.secondary));assertEquals(3.,p.owner.getSecondaryBaseScale(),1e-14);
    }

    @Test void nonfiniteOrUndefinedNormalizationCannotExposeThePreviousScale()throws Exception {
        var p=pair();initializePair(p,false);p.secondary.setPm(Double.NaN);assertFalse(initialize(p.secondary));
        assertThrows(IllegalStateException.class,p.owner::getSecondaryBaseScale);
        p.secondary.setPm(.1);p.primary.setPm(0.);assertTrue(initialize(p.primary));assertFalse(initialize(p.secondary));
        assertThrows(IllegalStateException.class,p.owner::getSecondaryBaseScale);
        assertThrows(IllegalStateException.class,()->p.owner.readStaggeredState(new double[6],0));
    }

    @Test void bothShaftsReusePreparedNormalizationWithoutPerStepAllocation()throws Exception {
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());bean.setThreadAllocatedMemoryEnabled(true);
        for(boolean shared:new boolean[]{false,true}) {
            var p=pair();initializePair(p,false);double[] state=new double[6],rates=new double[6];
            updateBoth(p,shared,state,rates);long id=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(id);
            updateBoth(p,shared,state,rates);long allocated=bean.getThreadAllocatedBytes(id)-before;
            System.out.println("WSIEG1 paired-shaft allocation shared="+shared+" bytes="+allocated);
            assertTrue(allocated<1024,"No per-step paired-shaft allocation: "+allocated);
            assertEquals(3.,p.owner.getSecondaryBaseScale(),1e-14);
        }
    }

    @Test void inactiveSecondaryDoesNotRequireUnexecutedMachineInitialization()throws Exception {
        for(int inactiveOwner=0;inactiveOwner<3;inactiveOwner++)for(boolean secondaryFirst:new boolean[]{false,true}) {
            var p=pair();p.secondary.setPm(Double.NaN);
            if(inactiveOwner==0)p.secondary.setStatus(false);
            else if(inactiveOwner==1)p.secondary.getParentGen().setStatus(false);
            else p.secondary.getDStabBus().setStatus(false);
            assertTrue(initialize(p.primary));assertEquals(0.,p.owner.getSecondaryBaseScale(),0.);
            assertEquals(.24,p.owner.getOutput(p.primary),1e-15);
            assertEquals(0.,p.secondary.getGovernor().getOutput(p.secondary),0.);
            double[] state=new double[6],rates=new double[6];p.owner.readStaggeredState(state,0);
            p.owner.evaluateStaggered(.0005,rates,0);for(double rate:rates)assertEquals(0.,rate,1e-14);
            p.owner.applyStaggeredState(state,0,.0005);p.owner.completeStaggeredStep(.0005);
            assertTrue(p.owner.nextStep(.0005,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,p.primary,0));
            assertTrue(p.owner.nextStep(.0005,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,p.primary,1));
            // A fresh initialization after enabling the device must replace the
            // zero scale; this does not claim hot reconnection support.
            p.secondary.setStatus(true);p.secondary.getParentGen().setStatus(true);p.secondary.getDStabBus().setStatus(true);
            assertFalse(p.secondary.getGovernor().nextStep(.0005,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,p.secondary,0));
            assertThrows(IllegalStateException.class,()->((StaggeredEulerParticipant)p.secondary.getGovernor()).readStaggeredState(new double[0],0));
            p.secondary.setPm(.36);initializePair(p,secondaryFirst);
            assertEquals(3.,p.owner.getSecondaryBaseScale(),1e-14);
        }
    }

    private static void updateBoth(Pair p,boolean shared,double[] state,double[] rates) {
        for(int tick=0;tick<20000;tick++) {
            p.primary.setSpeed(1.+.0001*Math.sin(tick*.01));
            if(shared) {
                p.owner.readStaggeredState(state,0);p.owner.evaluateStaggered(.0005,rates,0);
                for(int i=0;i<state.length;i++)state[i]+=.0005*rates[i];
                p.owner.applyStaggeredState(state,0,.0005);p.owner.completeStaggeredStep(.0005);
            } else {
                p.owner.nextStep(.0005,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,p.primary,0);
                p.owner.nextStep(.0005,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,p.primary,1);
            }
            outputSink=p.owner.getOutput(p.primary)+p.secondary.getGovernor().getOutput(p.secondary);
        }
    }
}
