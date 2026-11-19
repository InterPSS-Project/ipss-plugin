package com.interpss.dstab.controller.gov.weccurgs3t;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.controller.gov.weccurgs3t.Urgs3tGovernor;

/** Independent algebra, ownership, accepted-history and allocation checks for both methods. */
public class Urgs3tSharedStateTest {
    static final double[] BASE = {.05,.1,.2,.5,1,2,1.2,0,0,0,99,99,1,99,0,1,0,0,0,
            0,0,1,1,0,0,0,0,0,0,0,0,0,100};
    static final Path CASE = Path.of("testData/adpter/psse/v33/HspModels");
    private static volatile double sink;
    @BeforeAll static void setup() { IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu"); }
    static Urgs3tGovernor model(double[] constants) throws Exception {
        Path out = Files.createTempDirectory(Path.of("target"), "urgs3t-unit-");
        String machine = Files.readAllLines(CASE.resolve("hyg3u1-1.dyr")).getFirst();
        StringBuilder record = new StringBuilder(machine).append("\n1 'URGS3T' '1' ");
        for (double c : constants) record.append(c).append(' ');
        record.append("/\n2 'GENCLS' '1' 99999 0 /\n");
        Files.writeString(out.resolve("dynamic.dyr"), record);
        var loader = new PSSEMultiFileLoader();
        var context = loader.loadDStab(CASE.resolve("network.raw").toString(), out.resolve("dynamic.dyr").toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());
        var machineObject = context.getDStabilityNet().getMachine("Bus1-mach1");
        var governor = assertInstanceOf(Urgs3tGovernor.class, machineObject.getGovernor());
        machineObject.setSpeed(1.); machineObject.setPm(.5);
        assertTrue(governor.initStates(machineObject.getDStabBus(), machineObject));
        return governor;
    }
    @Test void exactConstantsRatingAndCompensationInitialization() throws Exception {
        double[] c = BASE.clone(); c[9]=.1; c[32]=80; c[14]=.3; c[15]=1.2; c[29]=1.3; c[30]=.03; c[31]=.12;
        var g = model(c); double[] x = new double[5], rates = new double[5]; g.readStaggeredState(x,0);
        assertArrayEquals(new double[]{.725,.46875,.625,.725,.54375}, x, 2e-15);
        assertEquals(.725/1.3, g.getRefPoint(), 2e-15); assertEquals(.5,g.getOutput(g.getMachine()),2e-15);
        g.evaluateStaggered(.0005,rates,0); for(double rate:rates)assertEquals(0.,rate,2e-14);
        double[] copy=g.getConstants();copy[0]=91;assertArrayEquals(c,g.getConstants(),0.);
        assertThrows(IllegalArgumentException.class,()->new Urgs3tGovernor("short",new double[32]));
        assertThrows(IllegalArgumentException.class,()->new Urgs3tGovernor("extra",new double[34]));
        for(int index:new int[]{0,1,2,3,12,15,32}) {double[] bad=c.clone();bad[index]=0.;assertThrows(IllegalArgumentException.class,()->new Urgs3tGovernor("invalid",bad));}
        double[] bad=c.clone();bad[7]=Double.NaN;assertThrows(IllegalArgumentException.class,()->new Urgs3tGovernor("nonfinite",bad));
    }
    @Test void preparedCoefficientsRejectUnrepresentableProductsBeforeInitialization() {
        for(double value:new double[]{Double.MIN_NORMAL,Double.MAX_VALUE}) {
            double[] c=BASE.clone();c[2]=value;c[15]=value;
            assertThrows(IllegalArgumentException.class,()->new Urgs3tGovernor("engine-time",c));
        }
        double[] c=BASE.clone();c[14]=Double.MAX_VALUE;c[15]=Double.MIN_NORMAL;
        assertThrows(IllegalArgumentException.class,()->new Urgs3tGovernor("engine-ratio",c));
        double[] d=BASE.clone();d[29]=Double.MAX_VALUE;d[30]=Double.MAX_VALUE;d[31]=1.;
        assertThrows(IllegalArgumentException.class,()->new Urgs3tGovernor("governor-gain",d));
    }
    @Test void parameterScreenUsesActivePolesAndPreservesEnteredDataAndStates() throws Exception {
        double[] c=BASE.clone();c[15]=.001;c[3]=1e-6;c[31]=1e-6;
        var g=model(c);var m=g.getMachine();
        var before=g.getIntegrationStates();var history=g.getIntegrationHistories();
        var report=org.interpss.dstab.validation.DynamicParameterValidator.validate((com.interpss.dstab.BaseDStabNetwork<?,?>)m.getDStabBus().getNetwork(),1./240.,2.,ignored->1);
        assertEquals(1,report.checkedModels().get("Urgs3tGovernor"));assertFalse(report.uncoveredModels().containsKey("Urgs3tGovernor"));
        assertEquals(java.util.Set.of("B*T2"),report.issues().stream().filter(i->i.model().equals("Urgs3tGovernor")&&i.severity()==org.interpss.dstab.validation.DynamicParameterValidator.Severity.WARNING).map(i->i.parameter()).collect(java.util.stream.Collectors.toSet()));
        assertArrayEquals(c,g.getConstants(),0.);assertEquals(before,g.getIntegrationStates());assertEquals(history,g.getIntegrationHistories());
        m.getControllerList().remove(g);
        var diagram=new Urgs3tGovernor("diagram",c,Urgs3tGovernor.RuntimeConvention.PUBLISHED_DIAGRAM);diagram.setMachine(m);assertTrue(diagram.initStates(m.getDStabBus(),m));
        report=org.interpss.dstab.validation.DynamicParameterValidator.validate((com.interpss.dstab.BaseDStabNetwork<?,?>)m.getDStabBus().getNetwork(),1./240.,2.,ignored->1);
        assertEquals(java.util.Set.of("B*T2","T3"),report.issues().stream().filter(i->i.model().equals("Urgs3tGovernor")&&i.severity()==org.interpss.dstab.validation.DynamicParameterValidator.Severity.WARNING).map(i->i.parameter()).collect(java.util.stream.Collectors.toSet()));
        m.getControllerList().remove(diagram);c[29]=1.;
        var leadLag=new Urgs3tGovernor("lead-lag",c,Urgs3tGovernor.RuntimeConvention.SOURCE_RECORD);leadLag.setMachine(m);assertTrue(leadLag.initStates(m.getDStabBus(),m));
        report=org.interpss.dstab.validation.DynamicParameterValidator.validate((com.interpss.dstab.BaseDStabNetwork<?,?>)m.getDStabBus().getNetwork(),1./240.,2.,ignored->1);
        assertEquals(java.util.Set.of("B*T2","T5"),report.issues().stream().filter(i->i.model().equals("Urgs3tGovernor")&&i.severity()==org.interpss.dstab.validation.DynamicParameterValidator.Severity.WARNING).map(i->i.parameter()).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void sharedAndModifiedEulerUseTheSameEquationsWithoutTrialHistoryCommit() throws Exception {
        double[] c=BASE.clone();c[16]=.0002;c[17]=.00005;c[18]=.001;
        var g=model(c);var other=model(c);double h=.0005;
        g.getMachine().setSpeed(1.0004);other.getMachine().setSpeed(1.0004);
        double[] x=new double[5],rates=new double[5],trial=new double[5],corrector=new double[5];g.readStaggeredState(x,0);
        var histories=g.getIntegrationHistories();var states=g.getIntegrationStates();
        for(int i=0;i<5;i++){g.evaluateStaggered(h,rates,0);g.getOutput(g.getMachine());assertEquals(histories,g.getIntegrationHistories());assertEquals(states,g.getIntegrationStates());}
        assertEquals(-.07,rates[0],2e-13);
        for(int i=0;i<5;i++)trial[i]=x[i]+h*rates[i];g.applyStaggeredState(trial,0,h);g.evaluateStaggered(h,corrector,0);
        for(int i=0;i<5;i++)trial[i]=x[i]+.5*h*(rates[i]+corrector[i]);g.applyStaggeredState(trial,0,h);
        assertEquals(histories,g.getIntegrationHistories());g.completeStaggeredStep(h);
        assertTrue(other.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,other.getMachine(),0));
        assertTrue(other.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,other.getMachine(),1));
        other.readStaggeredState(x,0);g.readStaggeredState(trial,0);assertArrayEquals(x,trial,0.);
        assertEquals(other.getIntegrationHistories(),g.getIntegrationHistories());
        var accepted=g.getIntegrationHistories();g.completeStaggeredStep(0);g.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),0);
        assertEquals(accepted,g.getIntegrationHistories());
    }
    @Test void invalidTrialsAndOwnerReplacementFailWithoutPartialMutation() throws Exception {
        var g=model(BASE);double[] x=new double[5];g.readStaggeredState(x,0);var states=g.getIntegrationStates();
        x[0]=.1;x[4]=Double.NaN;assertThrows(IllegalArgumentException.class,()->g.applyStaggeredState(x,0,.001));assertEquals(states,g.getIntegrationStates());
        assertThrows(IndexOutOfBoundsException.class,()->g.readStaggeredState(new double[4],0));
        assertThrows(IllegalArgumentException.class,()->g.evaluateStaggered(-.1,x,0));
        g.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),0);
        assertThrows(IllegalStateException.class,()->g.readStaggeredState(x,0));
        g.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),1);
        var m=g.getMachine();m.getControllerList().remove(g);var replacement=new Urgs3tGovernor("replacement",BASE);replacement.setMachine(m);
        assertThrows(IllegalStateException.class,()->g.evaluateStaggered(.0005,x,0));
    }
    @Test void sourceExhaustConventionIsExplicitAndDiagramLagRemainsDynamic() throws Exception {
        double[] c=BASE.clone();c[4]=.51;
        for(var convention:Urgs3tGovernor.RuntimeConvention.values()) {
            var imported=model(c);assertEquals(Urgs3tGovernor.RuntimeConvention.SOURCE_RECORD,imported.getRuntimeConvention());
            var m=imported.getMachine();m.getControllerList().remove(imported);
            var g=new Urgs3tGovernor("exhaust",c,convention);g.setMachine(m);assertTrue(g.initStates(m.getDStabBus(),m));
            double[] states=new double[5],rates=new double[5];g.readStaggeredState(states,0);
            states[0]=.6;states[1]=.6;g.applyStaggeredState(states,0,.001);g.setRefPoint(.8);g.evaluateStaggered(.001,rates,0);
            assertEquals(-.7,rates[0],2e-14,"Temperature limit uses its state, not a fitted output correction");
            assertEquals(convention==Urgs3tGovernor.RuntimeConvention.PUBLISHED_DIAGRAM?.2:0.,rates[2],2e-14);
            assertArrayEquals(c,g.getConstants(),0.);
            g.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,m,0);g.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,m,1);
            g.readStaggeredState(states,0);
            if(convention==Urgs3tGovernor.RuntimeConvention.PUBLISHED_DIAGRAM)assertTrue(states[2]>.5);else assertEquals(.5,states[2],0.);
        }
    }
    @Test void mismatchedCorrectorPreservesPendingStateAndAllowsValidRetry() throws Exception {
        var g=model(BASE);var control=model(BASE);double h=.0005;
        g.applyReferenceEvent(.7);control.applyReferenceEvent(.7);
        g.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),0);
        control.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,control.getMachine(),0);
        var states=g.getIntegrationStates();var histories=g.getIntegrationHistories();
        var snapshot=g.getStateDerivativeSnapshot();
        assertThrows(IllegalStateException.class,()->g.nextStep(2*h,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),1));
        assertEquals(states,g.getIntegrationStates());assertEquals(histories,g.getIntegrationHistories());
        assertEquals(snapshot,g.getStateDerivativeSnapshot());
        g.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),1);
        control.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,control.getMachine(),1);
        assertEquals(control.getIntegrationStates(),g.getIntegrationStates());
        assertEquals(control.getIntegrationHistories(),g.getIntegrationHistories());
    }
    @Test void rejectedNumericalStagesDoNotPoisonStatesHistoriesOrDiagnostics() throws Exception {
        for(boolean corrector:new boolean[]{false,true}) {
            var g=model(BASE);var control=model(BASE);double h=.0005;
            g.applyReferenceEvent(.7);control.applyReferenceEvent(.7);
            if(corrector){g.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),0);control.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,control.getMachine(),0);}
            var states=g.getIntegrationStates();var histories=g.getIntegrationHistories();var snapshot=g.getStateDerivativeSnapshot();
            long revision=g.staggeredInputDiscontinuityVersion();
            g.getMachine().setSpeed(Double.NaN);
            assertThrows(IllegalStateException.class,()->g.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),corrector?1:0));
            assertEquals(states,g.getIntegrationStates());assertEquals(histories,g.getIntegrationHistories());
            assertEquals(snapshot,g.getStateDerivativeSnapshot());assertEquals(revision,g.staggeredInputDiscontinuityVersion());
            g.getMachine().setSpeed(1.);
            if(!corrector){g.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),0);control.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,control.getMachine(),0);}
            g.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),1);control.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,control.getMachine(),1);
            assertEquals(control.getIntegrationStates(),g.getIntegrationStates());assertEquals(control.getIntegrationHistories(),g.getIntegrationHistories());
        }
        var g=model(BASE);g.applyReferenceEvent(.7);
        var states=g.getIntegrationStates();var histories=g.getIntegrationHistories();
        assertThrows(IllegalStateException.class,()->g.nextStep(Double.MAX_VALUE,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),0));
        assertEquals(states,g.getIntegrationStates());assertEquals(histories,g.getIntegrationHistories());
        g.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),0);
        g.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),1);
    }
    @Test void sharedInvalidBoundaryAndOverflowingPowerRejectAtomically() throws Exception {
        double[] c=BASE.clone();c[22]=2.;var g=model(c);double[] states=new double[5],rates={11,12,13,14,15};
        g.readStaggeredState(states,0);var before=g.getIntegrationStates();var histories=g.getIntegrationHistories();
        var snapshot=g.getStateDerivativeSnapshot();long evaluations=g.getStaggeredEvaluationCount();
        g.getMachine().setSpeed(Double.NaN);
        assertThrows(IllegalStateException.class,()->g.evaluateStaggered(.001,rates,0));
        assertArrayEquals(new double[]{11,12,13,14,15},rates,0.);
        assertEquals(snapshot,g.getStateDerivativeSnapshot());assertEquals(evaluations,g.getStaggeredEvaluationCount());
        g.getMachine().setSpeed(1.);states[1]=Double.MAX_VALUE;
        assertThrows(IllegalStateException.class,()->g.applyStaggeredState(states,0,.001));
        assertEquals(before,g.getIntegrationStates());assertEquals(histories,g.getIntegrationHistories());
        g.publishAcceptedRunBoundary(); // Rejected trial must not leave a pending publication barrier.
        g.readStaggeredState(states,0);g.evaluateStaggered(.001,rates,0);
        g.applyStaggeredState(states,0,.001);g.completeStaggeredStep(.001);
    }
    @Test void rateLimiterPrecedesServoTimeAndOnlyRestrictsOpening() throws Exception {
        double[] c=BASE.clone();c[10]=.03;c[11]=.001;c[13]=.005;
        var g=model(c);double[] states=new double[5],rates=new double[5];g.readStaggeredState(states,0);
        g.setRefPoint(.8);g.evaluateStaggered(.001,rates,0);assertEquals(.3,rates[0],1e-14);
        states[0]+=.002;g.applyStaggeredState(states,0,.001);g.evaluateStaggered(.001,rates,0);assertEquals(.05,rates[0],1e-14);
        g.setRefPoint(.2);g.evaluateStaggered(.001,rates,0);assertEquals(-3.02,rates[0],1e-14);
    }
    @Test void acceptedReferenceEventsPreserveFirstLeftRateAndClearOnlyOnCommit() throws Exception {
        var g=model(BASE);double[] left=new double[5],actual=new double[5];g.evaluateStaggered(.0005,left,0);
        g.applyReferenceEvent(.6);g.applyReferenceEvent(.7);assertEquals(2,g.staggeredInputDiscontinuityVersion());
        assertTrue(g.readStaggeredInputDiscontinuityLeftRates(actual,0));assertArrayEquals(left,actual,0.);
        g.evaluateStaggered(.0005,actual,0);assertEquals(2.,actual[0],2e-14);
        g.completeStaggeredStep(0);assertTrue(g.readStaggeredInputDiscontinuityLeftRates(actual,0));
        g.applyReferenceEvent(.7);assertEquals(2,g.staggeredInputDiscontinuityVersion());
        g.completeStaggeredStep(.0005);assertFalse(g.readStaggeredInputDiscontinuityLeftRates(actual,0));
        g.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),0);
        assertThrows(IllegalStateException.class,()->g.applyReferenceEvent(.8));assertEquals(.7,g.getRefPoint(),0.);
        g.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),1);
        assertTrue(g.initStates(g.getMachine().getDStabBus(),g.getMachine()));assertEquals(0,g.staggeredInputDiscontinuityVersion());
    }
    @Test void sourceTurbinePublicationCommitsOnceAndDampingUsesLiveSpeedBeforeRatingConversion() throws Exception {
        double[] c=BASE.clone();c[32]=80;c[8]=.3;var g=model(c);
        double[] states=new double[5];g.readStaggeredState(states,0);states[1]+=.1;
        g.applyStaggeredState(states,0,.001);g.getMachine().setSpeed(1.001);
        double damping=.8*.3*.001;
        assertEquals(.5-damping,g.getOutput(g.getMachine()),1e-14);
        g.completeStaggeredStep(0);assertEquals(.5-damping,g.getOutput(g.getMachine()),1e-14);
        g.completeStaggeredStep(.001);assertEquals(.5-damping,g.getOutput(g.getMachine()),1e-14);
        var history=g.getIntegrationHistories();double[] rates=new double[5];
        for(int i=0;i<5;i++){g.evaluateStaggered(.001,rates,0);g.getOutput(g.getMachine());assertEquals(history,g.getIntegrationHistories());}
        g.completeStaggeredStep(.001);assertEquals(.58-damping,g.getOutput(g.getMachine()),1e-14);
        g.getMachine().setSpeed(1.);assertEquals(.58,g.getOutput(g.getMachine()),1e-14);
        g.getMachine().setPm(.4);assertTrue(g.initStates(g.getMachine().getDStabBus(),g.getMachine()));
        assertEquals(.4,g.getOutput(g.getMachine()),1e-14);
    }
    @Test void explicitRunBoundaryPublishesAcceptedPowerWithoutAdvancingAnyHistory() throws Exception {
        var g=model(BASE);var m=g.getMachine();double[] x=new double[5];g.readStaggeredState(x,0);
        x[1]+=.1;g.applyStaggeredState(x,0,.001);
        assertThrows(IllegalStateException.class,g::publishAcceptedRunBoundary);
        g.completeStaggeredStep(.001);
        assertEquals(.5,g.getOutput(m),1e-14);var before=new java.util.HashMap<>(g.getIntegrationHistories());
        var states=g.getIntegrationStates();long revision=g.staggeredInputDiscontinuityVersion();
        g.publishAcceptedRunBoundary();assertEquals(.6,g.getOutput(m),1e-14);
        before.put("Published turbine power",before.get("Accepted turbine power"));
        assertEquals(before,g.getIntegrationHistories());assertEquals(states,g.getIntegrationStates());
        assertEquals(revision,g.staggeredInputDiscontinuityVersion());
        g.publishAcceptedRunBoundary();g.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,m,0);
        assertEquals(before,g.getIntegrationHistories());
        g.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,m,0);
        assertThrows(IllegalStateException.class,g::publishAcceptedRunBoundary);
        g.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,m,1);
    }
    @Test void sourceDeadbandDirectionIsSeparateFromDiagramBacklashAndTrialsArePure() throws Exception {
        double[] c=BASE.clone();c[18]=.001;
        for(var convention:Urgs3tGovernor.RuntimeConvention.values()) {
            var old=model(c);var m=old.getMachine();m.getControllerList().remove(old);
            var g=new Urgs3tGovernor("deadband",c,convention);g.setMachine(m);assertTrue(g.initStates(m.getDStabBus(),m));
            double[] states=new double[5],vars=new double[5];g.readStaggeredState(states,0);
            states[1]=.5001;g.applyStaggeredState(states,0,.001);var histories=g.getIntegrationHistories();
            for(int i=0;i<4;i++) {
                g.readPublishedVariables(vars,0);
                assertEquals(convention==Urgs3tGovernor.RuntimeConvention.SOURCE_RECORD?.4991:.5,vars[4],1e-14);
                assertEquals(histories,g.getIntegrationHistories());
            }
            g.completeStaggeredStep(.001);states[1]=.499;
            g.applyStaggeredState(states,0,.001);g.readPublishedVariables(vars,0);assertEquals(.5,vars[4],1e-14);
        }
    }
    @Test void modifiedEulerCommandStepMatchesIndependentHeunEquations() throws Exception {
        for(double h:new double[]{.0005,.00025}) {
            var g=model(BASE);double[] states=new double[5];double initial=.5,increment=.2;
            g.applyReferenceEvent(initial+increment);
            g.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),0);
            g.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),1);g.readStaggeredState(states,0);
            double initialRate=increment/BASE[1];
            assertEquals(initial+h*initialRate*(1-h/(2*BASE[1])),states[0],1e-14);
            assertEquals(initial+h*h*initialRate/(2*BASE[2]),states[1],1e-14);
            assertEquals(initial,states[2],0.);
            assertEquals(initial+h*h*initialRate/(2*BASE[12]),states[3],1e-14);
            assertEquals(0.,states[4],0.);
            assertEquals(initial,g.getOutput(g.getMachine()),0.,"Accepted turbine publication remains separate from state advancement");
            assertTrue(Math.abs(states[0]-(initial+.75*h*initialRate))>.45*h,
                    "Modified Euler must not silently become the staggered event formula");
        }
    }
    @Test void warmedNumericalStagesAllocateNoPerStepObjects() throws Exception {
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());bean.setThreadAllocatedMemoryEnabled(true);
        for(boolean shared:new boolean[]{false,true}) {
            var g=model(BASE);double[] x=new double[5],rates=new double[5];long id=Thread.currentThread().threadId();
            for(int i=0;i<20000;i++)cycle(g,x,rates,shared);
            long before=bean.getThreadAllocatedBytes(id);for(int i=0;i<10000;i++)cycle(g,x,rates,shared);
            // 10,000 cycles: any per-step array or boxed value would allocate at least
            // 160 kB. A late JIT compilation can still allocate a few hundred bytes once.
            long bytes=bean.getThreadAllocatedBytes(id)-before;
            assertTrue(bytes<=16*1024,"Numerical loop allocation shared="+shared+": "+bytes+" bytes");
        }
    }
    private static void cycle(Urgs3tGovernor g,double[] x,double[] rates,boolean shared) {
        if(shared){g.readStaggeredState(x,0);g.evaluateStaggered(.0005,rates,0);g.applyStaggeredState(x,0,.0005);g.completeStaggeredStep(.0005);}
        else {g.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),0);g.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),1);}
        sink=g.getOutput(g.getMachine());
    }
}
