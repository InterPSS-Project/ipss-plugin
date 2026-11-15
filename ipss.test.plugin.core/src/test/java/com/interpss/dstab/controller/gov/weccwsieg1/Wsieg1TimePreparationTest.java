package com.interpss.dstab.controller.gov.weccwsieg1;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.validation.DynamicParameterValidator;
import org.interpss.dstab.validation.DynamicParameterValidator.Severity;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.controller.gov.weccwsieg1.Wsieg1Governor;

/** Outer-step preparation is shared, immutable during stages and separate from the valve servo. */
public class Wsieg1TimePreparationTest {
    private static final double H=1./240;
    @BeforeAll static void setup(){IpssCorePlugin.init();}
    private static Wsieg1Governor model(Consumer<double[]> edit)throws Exception {
        var root=Wsieg1SharedSmibTest.CASE;
        var net=new PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(),root.resolve("wsieg1.dyr").toString()).getDStabilityNet();
        var machine=net.getMachine("Bus1-mach1");var old=(Wsieg1Governor)machine.getGovernor();double[] c=old.getConstants();edit.accept(c);
        machine.getControllerList().remove(old);var g=new Wsieg1Governor("prepared",c);g.setMachine(machine);
        machine.setPm(.4);machine.setPe(.4);machine.setSpeed(1.);return g;
    }
    private static void initialize(Wsieg1Governor g,double step){g.configureIntegrationStep(step);assertTrue(g.initStates(g.getMachine().getDStabBus(),g.getMachine()));}

    @Test void valveCurveUnusedSlotsMustBeTrailing()throws Exception {
        // A zero pair or repeated final pair denotes unused capacity, not an
        // interior point that a later entered pair may silently skip.
        assertThrows(IllegalArgumentException.class,()->model(c->{
            c[27]=0.;c[28]=0.;c[29]=2.;c[30]=2.;
        }));
        assertThrows(IllegalArgumentException.class,()->model(c->{
            c[27]=1.;c[28]=1.;c[29]=2.;c[30]=2.;
        }));
        assertNotNull(model(c->{})); // The supplied trailing-unused curve remains valid.
    }

    @Test void malformedCurveGapIsRejectedThroughTheDyrImport(@TempDir Path staged)throws Exception {
        var root=Wsieg1SharedSmibTest.CASE;
        var lines=new ArrayList<>(Files.readAllLines(root.resolve("wsieg1.dyr")));
        for(int i=0;i<lines.size();i++)if(lines.get(i).contains("'WSIEG1'")) {
            String[] fields=lines.get(i).trim().split("\\s+");
            fields[5+27]="0";fields[5+28]="0";
            fields[5+29]="2";fields[5+30]="2";
            lines.set(i,String.join(" ",fields));
        }
        Path malformed=staged.resolve("malformed-curve.dyr");Files.write(malformed,lines);
        var loader=new PSSEMultiFileLoader();
        var context=loader.loadDStab(root.resolve("network.raw").toString(),malformed.toString());
        var rejected=loader.getLastDynamicImportReport().failures().stream()
                .filter(entry->entry.canonicalModelName().equals("WSIEG1")).findFirst().orElseThrow();
        assertEquals(org.interpss.fadapter.psse.dyr.DynamicModelImportStatus.REJECTED,rejected.status());
        assertTrue(rejected.message().contains("unused valve-curve slots must be trailing"));
        assertNull(context.getDStabilityNet().getMachine("Bus1-mach1").getGovernor());
    }

    @Test void eachLagUsesTheInclusiveOuterIntervalBoundaryButServoAndLeadRemainEntered()throws Exception {
        for(double multiple:new double[]{0.,.5,1.,2.,2.01,4.}) {
            var g=model(c->{for(int i:new int[]{1,8,11,14,17})c[i]=multiple*H;c[2]=multiple==0?0:.02;c[3]=H/2;});
            double[] entered=g.getConstants();initialize(g,H);
            for(int i:new int[]{1,8,11,14,17})assertEquals(multiple<=2?0.:multiple*H,g.getPreparedTimeConstant(i),0.);
            assertEquals(H/2,g.getPreparedTimeConstant(3),0.);assertArrayEquals(entered,g.getConstants(),0.);
            g.getMachine().setSpeed(1.0004);double[] rates=new double[6];g.evaluateStaggered(H/16,rates,0);
            if(multiple<=2)for(int i:new int[]{0,2,3,4,5})assertEquals(0.,rates[i],0.);
            assertArrayEquals(entered,g.getConstants(),0.);
        }
    }

    @Test void changingConfiguredStepRequiresReinitializationAndLocalStepsCannotReprepare()throws Exception {
        var g=model(c->{c[1]=H;c[2]=H/4;c[8]=H;});initialize(g,H);double[] rates=new double[6];
        g.configureIntegrationStep(H/8);
        for(double local:new double[]{H,H/2,H/8}) {
            g.evaluateStaggered(local,rates,0);assertEquals(0.,g.getPreparedTimeConstant(1),0.);assertEquals(0.,g.getPreparedTimeConstant(8),0.);
        }
        assertTrue(g.initStates(g.getMachine().getDStabBus(),g.getMachine()));assertEquals(H,g.getPreparedTimeConstant(1),0.);assertEquals(H,g.getPreparedTimeConstant(8),0.);
        for(double invalid:new double[]{-.1,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->g.configureIntegrationStep(invalid));
        assertEquals(H,g.getPreparedTimeConstant(1),0.);assertThrows(IllegalArgumentException.class,()->g.getPreparedTimeConstant(2));
    }

    @Test void unconfiguredTypedUseRetainsEnteredPositiveLags()throws Exception {
        var g=model(c->{c[1]=H/2;c[2]=H/8;c[8]=H/2;});
        assertThrows(IllegalStateException.class,()->g.getPreparedTimeConstant(1));initialize(g,0.);
        assertEquals(H/2,g.getPreparedTimeConstant(1),0.);assertEquals(H/2,g.getPreparedTimeConstant(8),0.);
    }

    @Test void preparedAndExplicitBypassEquationsAndAcceptedHistoriesMatchInBothMethods()throws Exception {
        for(boolean shared:new boolean[]{false,true}) {
            var prepared=model(c->{c[1]=H;c[2]=.02;for(int i:new int[]{8,11,14,17})c[i]=2*H;});
            var explicit=model(c->{c[1]=c[2]=0.;for(int i:new int[]{8,11,14,17})c[i]=0.;});
            initialize(prepared,H);initialize(explicit,H);double[] a=new double[6],b=new double[6],rates=new double[6];
            for(int tick=0;tick<200;tick++) {
                double speed=1.+.0004*Math.sin(tick*.1);
                for(var g:List.of(prepared,explicit)) {
                    g.getMachine().setSpeed(speed);
                    if(shared){g.readStaggeredState(a,0);g.evaluateStaggered(H,rates,0);for(int i=0;i<6;i++)a[i]+=H*rates[i];g.applyStaggeredState(a,0,H);g.completeStaggeredStep(H);}
                    else {assertTrue(g.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),0));assertTrue(g.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),1));}
                }
                prepared.readPublishedState(a,0);explicit.readPublishedState(b,0);assertArrayEquals(a,b,0.);
                assertEquals(explicit.getIntegrationStates(),prepared.getIntegrationStates());assertEquals(explicit.getIntegrationHistories(),prepared.getIntegrationHistories());
                assertEquals(explicit.getOutput(explicit.getMachine()),prepared.getOutput(prepared.getMachine()),0.);
            }
        }
    }

    @Test void validationUsesOuterStepWithoutMutatingDataOrInitializingTheModel()throws Exception {
        var g=model(c->{c[1]=H;c[2]=H/4;c[8]=2*H;c[3]=H/2;});var net=(com.interpss.dstab.BaseDStabNetwork<?,?>)g.getMachine().getDStabBus().getNetwork();
        double[] entered=g.getConstants();var states=g.getIntegrationStates();var history=g.getIntegrationHistories();
        var report=DynamicParameterValidator.validate(net,H,2.,machine->128);
        assertEquals(1,report.checkedModels().get("Wsieg1Governor"));assertFalse(report.uncoveredModels().containsKey("Wsieg1Governor"));assertEquals(0,report.count(Severity.ERROR));
        for(String name:List.of("T1 preparation","T4 preparation")) {
            var issue=report.issues().stream().filter(i->i.parameter().equals(name)).findFirst().orElseThrow();assertEquals(Severity.INFO,issue.severity());assertEquals(H,issue.effectiveStepSeconds(),0.);
        }
        assertTrue(report.issues().stream().anyMatch(i->i.parameter().equals("T3")&&i.severity()==Severity.WARNING));
        assertArrayEquals(entered,g.getConstants(),0.);assertEquals(states,g.getIntegrationStates());assertEquals(history,g.getIntegrationHistories());
        assertThrows(IllegalStateException.class,()->g.getPreparedTimeConstant(1));
        var fine=DynamicParameterValidator.validate(net,H/8,2.,machine->1);assertFalse(fine.issues().stream().anyMatch(i->i.parameter().endsWith(" preparation")));
    }

    @Test void bothActualSolversPrepareShortLagsBeforeAnyStateStep(@TempDir Path staged)throws Exception {
        var original=Files.readAllLines(Wsieg1SharedSmibTest.CASE.resolve("wsieg1.dyr"));
        for(boolean bypass:new boolean[]{false,true}) {
            var lines=new ArrayList<>(original);
            for(int i=0;i<lines.size();i++)if(lines.get(i).contains("'WSIEG1'")) {
                String[] fields=lines.get(i).trim().split("\\s+");
                for(int index:new int[]{1,8,11,14,17})fields[index+5]=Double.toString(bypass?0.:H/2);
                fields[7]=Double.toString(bypass?0.:.02);lines.set(i,String.join(" ",fields));
            }
            Files.write(staged.resolve(bypass?"bypass.dyr":"short.dyr"),lines);
        }
        for(boolean shared:new boolean[]{false,true}) {
            var shortRun=Wsieg1SharedSmibTest.simulate(Wsieg1SharedSmibTest.CASE.resolve("network.raw"),staged.resolve("short.dyr"),shared,"fault",H,.5,List.of());
            var bypassRun=Wsieg1SharedSmibTest.simulate(Wsieg1SharedSmibTest.CASE.resolve("network.raw"),staged.resolve("bypass.dyr"),shared,"fault",H,.5,List.of());
            assertEquals(bypassRun.initial,shortRun.initial);assertEquals(bypassRun.frames,shortRun.frames);
        }
    }

    @Test void invalidPrimaryTurbineAllocationIsRejectedBeforeEitherSolverInitializes()throws Exception {
        for(boolean shared:new boolean[]{false,true}) {
            var root=Wsieg1SharedSmibTest.CASE;var context=new PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(),root.resolve("wsieg1.dyr").toString());
            var m=context.getDStabilityNet().getMachine("Bus1-mach1");var old=(Wsieg1Governor)m.getGovernor();double[] c=old.getConstants();for(int i:new int[]{9,12,15,18})c[i]=0.;
            m.getControllerList().remove(old);new Wsieg1Governor("invalid-allocation",c).setMachine(m);var algorithm=context.getDynSimuAlgorithm();algorithm.setSimuStepSec(H);
            var solver=shared?new org.interpss.dstab.algo.StaggeredEulerSolver(algorithm):new org.interpss.dstab.algo.DefaultDynamicSimulationSolver(algorithm);
            assertThrows(IllegalArgumentException.class,solver::initialization);
            assertTrue(solver.getParameterValidationReport().issues().stream().anyMatch(i->i.parameter().equals("primary turbine fraction sum")&&i.severity()==Severity.ERROR));
        }
    }

    @Test void passiveSecondaryAndInactivePrimaryRemainExplicitInValidationCoverage()throws Exception {
        var root=Wsieg1SharedSmibTest.CASE;var net=new PSSEMultiFileLoader().loadDStab(root.resolve("dual-network.raw").toString(),root.resolve("wsieg1-dual.dyr").toString()).getDStabilityNet();
        net.getMachine("Bus1-mach1").getGovernor().setStatus(false);
        var report=DynamicParameterValidator.validate(net,H,2.,machine->1);
        assertEquals(1,report.checkedModels().get("Wsieg1Governor"));assertEquals(1,report.checkedModels().get("SecondaryShaft"));
        assertFalse(report.uncoveredModels().containsKey("SecondaryShaft"));
        assertFalse(report.issues().stream().filter(i->i.model().equals("Wsieg1Governor")).findFirst().orElseThrow().initiallyActive());
        assertTrue(report.issues().stream().anyMatch(i->i.parameter().equals("shared turbine owner")));
    }
}
