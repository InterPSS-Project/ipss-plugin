package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import com.interpss.dstab.controller.exc.ieee1981st1.IEEE1981ST1Exciter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Native reference-step discriminators for ST1's local integration and publication. */
public class PsseSt1LocalIntegrationTest {
    @Test
    void defaultIsOneAndExplicitTenAndMachineOverrideRemainAvailable() throws Exception {
        var defaultFixture=load(false,.0005);
        assertEquals(1,IEEE1981ST1Exciter.DEFAULT_LOCAL_SUBSTEPS);
        assertEquals(1,defaultFixture.solver().getSt1Substeps());
        assertEquals(1,defaultFixture.solver().getSt1Substeps("Bus1-mach1"));
        double[] expected=trajectory(defaultFixture);
        assertArrayEquals(expected,trajectory(load(false,.0005,s->s.setSt1Substeps(1))),0.);
        double[] ten=trajectory(load(false,.0005,s->s.setSt1Substeps(10)));
        assertNotEquals(expected[0],ten[0],"explicit ten must retain its distinct integration");
        var overridden=load(false,.0005,s->{s.setSt1Substeps(4);s.setSt1Substeps("Bus1-mach1",10);});
        assertEquals(4,overridden.solver().getSt1Substeps());
        assertEquals(10,overridden.solver().getSt1Substeps("Bus1-mach1"));
        assertArrayEquals(ten,trajectory(overridden),0.);
    }

    @Test
    void configuredCountsReachBothLocalIntegrationPaths() throws Exception {
            for (boolean zeroTa : new boolean[] {false,true}) {
                double[] ten=null;
                for (int count : new int[] {10,1,4,128}) {

                    var fixture=load(zeroTa,.0005,s->{s.setSt1Substeps(count);s.setSt1LocalStoreEnabled(true);});
                    assertEquals(.0005,fixture.algorithm().getSimuStepSec(),0.);
                    double[] direct=trajectory(fixture);

                    double[] fallback=trajectory(load(zeroTa,.0005,s->{s.setSt1Substeps(count);s.setSt1LocalStoreEnabled(false);}));
                    assertArrayEquals(direct,fallback,1e-12,"local count "+count);
                    if(count==10)ten=direct;
                    else assertNotEquals(ten[0],direct[0],"count must change the local integration");
                }
            }
    }

    @Test
    void invalidCountsTargetsAndLateChangesAreRejected() throws Exception {
        var fixture=load(false,.0005);
        var fresh=new PsseStaggeredEulerSolver(fixture.algorithm());
        for(int count:new int[]{Integer.MIN_VALUE,-1,0,129,Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,()->fresh.setSt1Substeps(count));
            assertThrows(IllegalArgumentException.class,()->fresh.setSt1Substeps("Bus1-mach1",count));
        }
        assertThrows(IllegalArgumentException.class,()->fresh.setSt1Substeps(null,4));
        assertThrows(IllegalArgumentException.class,()->fresh.setSt1Substeps(" ",4));
        assertThrows(IllegalStateException.class,()->fixture.solver().setSt1Substeps(4));
        assertThrows(IllegalStateException.class,()->fixture.solver().setSt1Substeps("Bus1-mach1",4));
        assertThrows(IllegalArgumentException.class,()->load(false,.0005,s->s.setSt1Substeps("missing",4)));
        fresh.setSt1Substeps("Bus1-mach1",4);
        fixture.machine().getControllerList().remove(fixture.exciter());
        assertThrows(IllegalArgumentException.class,fresh::initialization);
    }

    @Test
    void configuredCountSurvivesReinitializationWithFreshHistory() throws Exception {
        var fixture=load(false,.0005,s->s.setSt1Substeps("Bus1-mach1",4));
        int states=fixture.solver().getBoundDifferentialStateCount();
        fixture.algorithm().getNetwork().setDStabNetInitialized(false);
        assertTrue(fixture.algorithm().getAclfAlgorithm().loadflow());
        assertTrue(fixture.algorithm().initialization());
        assertEquals(4,fixture.solver().getSt1Substeps("Bus1-mach1"));
        assertEquals(states,fixture.solver().getBoundDifferentialStateCount());
        assertArrayEquals(trajectory(load(false,.0005,s->s.setSt1Substeps(4))),trajectory(fixture),1e-10);
    }

    private static double[] trajectory(Fixture fixture) throws Exception {
        double[] samples=new double[40];
        jump(fixture,.001);
        for(int i=0;i<20;i++) {
            if(i==8)jump(fixture,-.0005);
            assertTrue(fixture.algorithm().solveDEqnStep(true));
            samples[2*i]=fixture.machine().getEfd();
            samples[2*i+1]=fixture.machine().getSpeed();
        }
        return samples;
    }

    @Test
    void exst1ReferenceJumpUsesTheObservedLocalStepAtTwoNetworkSteps() throws Exception {
        // Fresh commercial two-bus experiments, Ka=261, Ta=.08, Tc=1,
        // Tb=8.55, Tr=Kf=0, +.001 VREF. These first increments discriminate
        // local ten-step integration from the common network-step recurrence.
        for (double[] sample : new double[][] {{.0005,.00019025802612304688},
                {.005,.0018546581268310547}}) {
            var fixture=load(false,sample[0],s->s.setSt1Substeps(10));
            double initial=fixture.machine().getEfd();
            jump(fixture,.001);
            assertTrue(fixture.algorithm().solveDEqnStep(true));
            assertEquals(sample[1],fixture.machine().getEfd()-initial,4e-7,
                    "native EXST1 first increment at network step "+sample[0]);
        }
    }

    @Test
    void zeroTaFieldPublishesBeforeTheLastAlgebraicRefresh() throws Exception {
        var fixture=load(true,.0005,s->s.setSt1Substeps(10));
        double initial=fixture.machine().getEfd();
        jump(fixture,.001);
        assertTrue(fixture.algorithm().solveDEqnStep(true));
        // Native ESST1A first field increment; native VA is .000000715 pu
        // above the machine field channel at this same observation endpoint.
        assertEquals(.011477351188659668,fixture.machine().getEfd()-initial,1e-6);
        double published=fixture.machine().getEfd();
        assertTrue(fixture.exciter().getRegulatorOutput()-published>2e-7,
                "zero-Ta regulator state and published machine EFD are distinct stages");
        assertTrue(fixture.exciter().nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,fixture.machine(),0));
        assertEquals(published,fixture.machine().getEfd(),0.,
                "an algebraic controller observation must not republish the machine field");
    }

    @Test
    void disabledLocalControllerFreezesAndReinitializationDoesNotDuplicateIt() throws Exception {
        var fixture=load(false,.0005,s->s.setSt1Substeps(10));
        int count=fixture.solver().getBoundDifferentialStateCount();
        double initial=fixture.machine().getEfd();
        fixture.exciter().setStatus(false);
        jump(fixture,.001);
        for(int i=0;i<3;i++)assertTrue(fixture.algorithm().solveDEqnStep(true));
        assertEquals(initial,fixture.machine().getEfd(),0.);
        fixture.exciter().setStatus(true);
        fixture.algorithm().getNetwork().setDStabNetInitialized(false);
        assertTrue(fixture.algorithm().getAclfAlgorithm().loadflow());
        assertTrue(fixture.algorithm().initialization());
        assertEquals(count,fixture.solver().getBoundDifferentialStateCount());
        initial=fixture.machine().getEfd();
        jump(fixture,.001);
        assertTrue(fixture.algorithm().solveDEqnStep(true));
        assertEquals(.00019025802612304688,fixture.machine().getEfd()-initial,4e-7);
    }

    private static void jump(Fixture fixture,double change) {
        fixture.exciter().setRefPoint(fixture.exciter().getRefPoint()+change);
        fixture.solver().notifyDiscontinuity();
    }

    private static Fixture load(boolean esst1a,double step) throws Exception {
        return load(esst1a,step,s->{});
    }

    private static Fixture load(boolean esst1a,double step,
            java.util.function.Consumer<PsseStaggeredEulerSolver> configure) throws Exception {
        IpssCorePlugin.init();
        String base="testData/adpter/psse/v33/SMIB/";
        var context=new PSSEMultiFileLoader().loadDStab(base+"SMIB_v33.raw",
                base+(esst1a?"SMIB_v33_genrou_esst1a.dyr":"SMIB_v33_genrou_exst1.dyr"));
        var machine=context.getDStabilityNet().getMachine("Bus1-mach1");
        var exciter=(IEEE1981ST1Exciter)machine.getExciter();
        var data=exciter.getData();
        data.setTr(0.);data.setKf(0.);data.setTf(1.);data.setVimax(.1);data.setVimin(-.1);
        data.setKa(esst1a?275.:261.);data.setTa(esst1a?0.:.08);
        data.setTc(esst1a?1.85:1.);data.setTb(esst1a?8.96:8.55);
        data.setTc1(esst1a?1.:0.);data.setTb1(esst1a?4.95:0.);
        data.setVrmax(5.);data.setVrmin(-5.);data.setVamax(999.);data.setVamin(-999.);
        data.setKc(esst1a?.09:.15);data.setKlr(0.);data.setIlr(0.);
        var algorithm=context.getDynSimuAlgorithm();
        algorithm.getAclfAlgorithm().setTolerance(1e-10);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var solver=new PsseStaggeredEulerSolver(algorithm);algorithm.setSolver(solver);
        configure.accept(solver);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(step);
        algorithm.setTotalSimuTimeSec(.1);algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        return new Fixture(algorithm,solver,machine,exciter);
    }

    private record Fixture(com.interpss.dstab.algo.DynamicSimuAlgorithm algorithm,
            PsseStaggeredEulerSolver solver,com.interpss.dstab.mach.Machine machine,
            IEEE1981ST1Exciter exciter) {}
}
