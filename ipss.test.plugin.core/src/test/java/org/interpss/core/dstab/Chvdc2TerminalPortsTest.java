package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.dstab.hvdc.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.device.NetworkIterationAware.Context;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class Chvdc2TerminalPortsTest extends CorePluginTestSetup {
    @TempDir Path folder;
    record Fixture(Cdc6tCoupledNetworkTest.Fixture network,Chvdc2NetworkPreparation.Binding binding,Chvdc2TerminalPorts ports) {}
    private Fixture fixture(boolean dynamic)throws Exception {
        return fixture(dynamic,Chvdc2TerminalPorts.ProtectionClock.CALLER_TIME,0);
    }
    private Fixture fixture(boolean dynamic,Chvdc2TerminalPorts.ProtectionClock clock,double initialTime)throws Exception {
        var f=Cdc6tCoupledNetworkTest.load(folder);var n=f.context().getDStabilityNet();
        f.ports().rectifierPort().setDStabBus(null);f.ports().inverterPort().setDStabBus(null);
        var o=(HvdcLine2TLCC<?>)n.getSpecialBranchList().get(0);
        var p=new Chvdc2Parameters(o.getName(),Chvdc2Parameters.Dialect.CHVDC2U1,new int[]{0,0,dynamic?1:0},Chvdc2ParametersTest.constants());
        var b=Chvdc2NetworkPreparation.prepare(n,p,.0005,initialTime);var ports=new Chvdc2TerminalPorts(b,clock);ports.attach();
        return new Fixture(f,b,ports);
    }
    private static Context context(String stage,double time){return new Context(stage,time-.0005,time,50,1e-10);}
    private Fixture initiallyShortedFixture()throws Exception {
        var f=fixture(true);double[] boundary=new double[10];f.binding().runtime().readBoundary(boundary,0);
        f.ports().rectifierPort().setDStabBus(null);f.ports().inverterPort().setDStabBus(null);
        double[] c=Chvdc2ParametersTest.constants();
        c[Chvdc2Parameters.Constant.GAMMA_CF_DEG.ordinal()]=Math.toDegrees(boundary[8])+1;
        c[Chvdc2Parameters.Constant.V_AC_UCF_PU.ordinal()]=0;
        c[Chvdc2Parameters.Constant.T_CF_SEC.ordinal()]=.000001;
        var params=new Chvdc2Parameters(f.binding().owner().getName(),Chvdc2Parameters.Dialect.CHVDC2U1,new int[]{0,0,1},c);
        var binding=Chvdc2NetworkPreparation.prepare(f.network().context().getDStabilityNet(),params,.0005,0);
        var ports=new Chvdc2TerminalPorts(binding);ports.attach();return new Fixture(f.network(),binding,ports);
    }
    private static void initializePorts(Fixture f) {
        var p=f.ports();assertTrue(p.rectifierPort().initStates(p.rectifierPort().getDStabBus()));
        assertTrue(p.inverterPort().initStates(p.inverterPort().getDStabBus()));p.rectifierPort().acceptInitialEndpoint();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void completeNetworkMaintainsTheSolvedOperatingPointWithOneStateOwner(boolean staggered)throws Exception {
        var f=fixture(true);var a=f.network().context().getDynSimuAlgorithm();var n=a.getNetwork();
        double vr=n.getBus("Bus7").getVoltageMag(),vi=n.getBus("Bus9").getVoltageMag();
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.0005);a.setTotalSimuTimeSec(.1);
        a.setSimuOutputHandler(new StateMonitor());a.setSolver(staggered?new StaggeredEulerSolver(a):new DefaultDynamicSimulationSolver(a));
        assertTrue(a.initialization());assertEquals(16,f.ports().rectifierPort().staggeredStateCount());
        assertEquals(0,f.ports().inverterPort().staggeredStateCount());
        var compensation=n.getCustomBusCurrInjHashtable();
        assertTrue(compensation.getOrDefault("Bus7",Complex.ZERO).abs()<1e-7,"Rectifier transfer must not be counted twice");
        assertTrue(compensation.getOrDefault("Bus9",Complex.ZERO).abs()<1e-7,"Inverter transfer must not be counted twice");
        double[] initial=new double[16],end=new double[16];f.binding().runtime().readStaggeredState(initial,0);
        double worst=0;
        while(a.getSimuTime()<.1-.00025){assertTrue(a.solveDEqnStep(true));worst=Math.max(worst,Math.max(Math.abs(n.getBus("Bus7").getVoltageMag()-vr),Math.abs(n.getBus("Bus9").getVoltageMag()-vi)));}
        f.binding().runtime().readStaggeredState(end,0);assertArrayEquals(initial,end,1e-4);assertTrue(worst<1e-7,"No-event voltage drift "+worst);
    }
    @Test void outputObservationIsPureAndFailedNetworkRestoresItsWorkingHistory()throws Exception {
        var f=fixture(true);initializePorts(f);var p=f.ports().rectifierPort();var m=f.binding().runtime();
        Object output=p.getOutputObject();var states=m.getIntegrationStates();var histories=m.getIntegrationHistories();
        p.getDStabBus().setVoltage(new Complex(.95,0));
        for(int k=0;k<5;k++){assertSame(output,p.getOutputObject());assertEquals(states,m.getIntegrationStates());assertEquals(histories,m.getIntegrationHistories());}
        p.beginNetworkSolution(context("INITIAL",0));p.beforeNetworkIteration(0);assertNotEquals(output,p.getOutputObject());
        p.applyConvergedNetworkAction();p.endNetworkSolution(false);
        assertSame(output,p.getOutputObject());assertEquals(states,m.getIntegrationStates());assertEquals(histories,m.getIntegrationHistories());
    }
    @Test void pendingPredictorAndCorrectorNetworkFailureRollBackTogether()throws Exception {
        var f=fixture(true);initializePorts(f);var p=f.ports().rectifierPort();var m=f.binding().runtime();
        var states=m.getIntegrationStates();var histories=m.getIntegrationHistories();Object output=p.getOutputObject();
        p.getDStabBus().setVoltage(new Complex(1.02,0));
        p.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0);
        p.beginNetworkSolution(context("PREDICTOR",.0005));p.beforeNetworkIteration(0);p.applyConvergedNetworkAction();p.afterNetworkConvergence();p.endNetworkSolution(true);
        p.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,1);
        p.beginNetworkSolution(context("CORRECTOR",.0005));p.beforeNetworkIteration(0);p.applyConvergedNetworkAction();p.endNetworkSolution(false);
        assertEquals(states,m.getIntegrationStates());assertEquals(histories,m.getIntegrationHistories());assertSame(output,p.getOutputObject());
    }
    @Test void attachedDiagnosticsExposeOnlyTheOwnerWithoutEvaluatingInputs()throws Exception {
        var f=fixture(true);initializePorts(f);var p=f.ports().rectifierPort();var companion=f.ports().inverterPort();
        var runtime=f.binding().runtime();double[] rates=new double[p.staggeredStateCount()];
        p.evaluateStaggered(.0005,rates,0);
        var states=runtime.getIntegrationStates();var history=runtime.getIntegrationHistories();var derivative=runtime.getStateDerivativeSnapshot();
        Object output=p.getOutputObject();p.getDStabBus().setVoltage(new Complex(.9,0));
        assertFalse(derivative.rates().isEmpty());
        assertEquals(states,p.getIntegrationStates());assertEquals(history,p.getIntegrationHistories());
        assertEquals(derivative,p.getStateDerivativeSnapshot());assertSame(output,p.getOutputObject());
        assertTrue(companion.getIntegrationStates().isEmpty());assertTrue(companion.getIntegrationHistories().isEmpty());
        assertTrue(companion.getStateDerivativeSnapshot().rates().isEmpty());
        assertEquals(states,runtime.getIntegrationStates());assertEquals(history,runtime.getIntegrationHistories());
    }
    @Test void repeatedInitialSolveRefreshesInjectionWithoutAcceptingAnotherElectricalEpoch()throws Exception {
        var f=fixture(true);initializePorts(f);var p=f.ports().rectifierPort();var runtime=f.binding().runtime();
        var history=runtime.getIntegrationHistories();Object output=f.ports().inverterPort().getOutputObject();
        f.ports().inverterPort().getDStabBus().setVoltage(new Complex(.99,0));
        p.beginNetworkSolution(context("INITIAL",0));p.beforeNetworkIteration(0);
        assertFalse(p.applyConvergedNetworkAction());p.afterNetworkConvergence();p.endNetworkSolution(true);
        assertEquals(history,runtime.getIntegrationHistories());
        assertNotEquals(output,f.ports().inverterPort().getOutputObject());
        // The same time on the far side of an explicit event is a new boundary.
        p.beginNetworkSolution(new Context("INITIAL",0,0,50,1e-10,true));p.beforeNetworkIteration(0);
        p.applyConvergedNetworkAction();p.afterNetworkConvergence();p.endNetworkSolution(true);
        assertNotEquals(history,runtime.getIntegrationHistories());
    }
    @Test void acceptedOutputIsRetainedAndSharedEndpointIsCommittedOnce()throws Exception {
        var f=fixture(false);initializePorts(f);var p=f.ports().rectifierPort();var m=f.binding().runtime();
        int count=p.staggeredStateCount();double[] state=new double[count],rates=new double[count];
        p.getDStabBus().setVoltage(new Complex(1.02,0));p.readStaggeredState(state,0);p.evaluateStaggered(.0005,rates,0);
        for(int k=0;k<count;k++)state[k]+=.0005*rates[k];p.applyStaggeredState(state,0,.0005);p.completeStaggeredStep(.0005,.0005);
        assertThrows(IllegalStateException.class,()->p.completeStaggeredStep(.0005,.0005));
        p.beginNetworkSolution(context("DIRECT",.0005));p.beforeNetworkIteration(0);p.applyConvergedNetworkAction();p.afterNetworkConvergence();
        Object output=p.getOutputObject();p.endNetworkSolution(true);assertSame(output,p.getOutputObject());
        assertEquals(.0005,m.getIntegrationHistories().get("Accepted time (s)"),0);
        assertThrows(IllegalStateException.class,()->p.completeStaggeredStep(.0005,.001));
    }
    @Test void structuralAndIntegrationConfigurationGuardsAreExplicit()throws Exception {
        var f=fixture(true);var p=f.ports().rectifierPort();
        assertThrows(IllegalStateException.class,f.ports()::attach);
        assertThrows(IllegalArgumentException.class,()->new Chvdc2TerminalPorts(f.binding()).attach());
        assertThrows(IllegalArgumentException.class,()->p.configureIntegrationStep(.001));
        initializePorts(f);assertThrows(IllegalStateException.class,()->p.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,1));
        f.ports().inverterPort().setDStabBus(null);assertFalse(p.attachmentIntact());
        assertFalse(p.updateAttributes(false));
    }
    @Test void staticLinkIdentityMembershipAndStatusRemainOwned()throws Exception {
        var f=fixture(true);initializePorts(f);var p=f.ports().rectifierPort();var link=f.binding().owner();
        String name=link.getName();link.setName(name+"-changed");
        assertFalse(p.attachmentIntact());
        assertThrows(IllegalArgumentException.class,()->new Chvdc2TerminalPorts(f.binding()).attach());
        link.setName(name);assertTrue(p.attachmentIntact());
        link.setStatus(false);assertFalse(p.isOperational());assertEquals(Complex.ZERO,p.getOutputObject());
        assertEquals(Complex.ZERO,f.ports().inverterPort().getOutputObject());
        link.setStatus(true);assertTrue(p.isOperational());
        f.network().context().getDStabilityNet().getSpecialBranchList().remove(link);
        assertFalse(p.attachmentIntact());assertFalse(p.updateAttributes(false));
    }
    @Test void staggeredFaultClearingUsesTheAcceptedLeftRateAndKeepsTwoStepHistorySeparate()throws Exception {
        var f=fixture(true);var a=f.network().context().getDynSimuAlgorithm();var p=f.ports().rectifierPort();
        double h=.0005,clear=.004;int coordinate=11;
        class ObservedSolver extends StaggeredEulerSolver {
            final double[] rate=new double[16],state=new double[16];
            double previousStartRate,olderRate,leftRate,rightRate,initialState;
            boolean clearing,recording;
            ObservedSolver(){super(a);setReuseNetworkEndpoint(false);}
            @Override public void beforeStep(double time)throws com.interpss.dstab.common.DStabSimuException {
                clearing=Math.abs(time-clear)<1e-12;
                if(clearing){p.evaluateStaggered(h,rate,0);leftRate=rate[coordinate];
                    olderRate=previousStartRate;p.readStaggeredState(state,0);initialState=state[coordinate];}
                super.beforeStep(time);
            }
            @Override public boolean networkSolutionStep()throws com.interpss.dstab.common.DStabSimuException {
                boolean ok=super.networkSolutionStep();
                if(ok&&recording&&"INITIAL".equals(networkSolutionStage)){
                    p.evaluateStaggered(h,rate,0);previousStartRate=rate[coordinate];
                    if(clearing)rightRate=rate[coordinate];
                }
                return ok;
            }
        }
        var solver=new ObservedSolver();a.setSolver(solver);a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        a.setSimuStepSec(h);a.setTotalSimuTimeSec(clear+h);a.setSimuOutputHandler(new StateMonitor());
        a.getNetwork().addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent("Bus9",a.getNetwork(),
                com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.001,clear-.001),"Fault");
        assertTrue(a.initialization());solver.recording=true;
        while(a.getSimuTime()<clear+h/2)assertTrue(a.solveDEqnStep(true));
        double expected=solver.initialState+h*(1.5*.5*(solver.leftRate+solver.rightRate)-.5*solver.olderRate);
        p.readStaggeredState(solver.state,0);
        assertTrue(Math.abs(solver.leftRate-solver.olderRate)>1e-5,"The event must distinguish the two retained slopes");
        assertEquals(expected,solver.state[coordinate],1e-12,"Network event uses settled left rate while AB2 retains the older start rate");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void releasedShortCannotRetriggerInsideOneIntegrationEndpoint(boolean staggered)throws Exception {
        var f=initiallyShortedFixture();initializePorts(f);var p=f.ports().rectifierPort();var m=f.binding().runtime();
        assertTrue(m.inverterShorted());double h=.00001;
        double[] boundary=new double[10];m.readBoundary(boundary,0);
        double resistance=f.binding().owner().getRdc(org.interpss.numeric.datatype.Unit.UnitType.Ohm);
        double initialCurrent=boundary[2],initialRate=(boundary[0]-boundary[1]-resistance*boundary[2])/f.binding().effectiveHenries();
        if(staggered){
            double[] x=new double[16],rate=new double[16];p.readStaggeredState(x,0);p.evaluateStaggered(h,rate,0);
            for(int k=0;k<x.length;k++)x[k]+=h*rate[k];p.applyStaggeredState(x,0,h);p.completeStaggeredStep(h,h);
            p.beginNetworkSolution(context("DIRECT",h));
        }else{p.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,0);p.beginNetworkSolution(context("PREDICTOR",h));}
        p.beforeNetworkIteration(0);assertEquals(staggered,p.applyConvergedNetworkAction());assertEquals(!staggered,m.inverterShorted());
        p.beforeNetworkIteration(0);assertFalse(p.applyConvergedNetworkAction());assertEquals(!staggered,m.inverterShorted());
        p.afterNetworkConvergence();p.endNetworkSolution(true);
        if(!staggered){
            m.readBoundary(boundary,0);
            double predictedRate=(boundary[0]-boundary[1]-resistance*boundary[2])/f.binding().effectiveHenries();
            double expectedCurrent=initialCurrent+.5*h*(initialRate+predictedRate);
            p.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,1);p.beginNetworkSolution(context("CORRECTOR",h));
            p.beforeNetworkIteration(0);assertTrue(p.applyConvergedNetworkAction());assertFalse(m.inverterShorted());
            m.readBoundary(boundary,0);assertEquals(expectedCurrent,boundary[2],1e-10,"The interval corrects using its original shorted circuit");
            p.beforeNetworkIteration(0);assertFalse(p.applyConvergedNetworkAction());p.afterNetworkConvergence();p.endNetworkSolution(true);
        }
        assertFalse(m.inverterShorted(),"Final electrical publication must not introduce a second protection transition");
        var history=m.getIntegrationHistories();Object output=p.getOutputObject();
        // A distinct same-time external event may switch, but failed convergence rolls it back.
        p.beginNetworkSolution(new Context("INITIAL",h,h,50,1e-10,true));p.beforeNetworkIteration(0);
        assertTrue(p.applyConvergedNetworkAction());assertTrue(m.inverterShorted());p.endNetworkSolution(false);
        assertEquals(history,m.getIntegrationHistories());assertSame(output,p.getOutputObject());
        p.beginNetworkSolution(new Context("INITIAL",h,h,50,1e-10,true));p.beforeNetworkIteration(0);
        assertTrue(p.applyConvergedNetworkAction());p.afterNetworkConvergence();p.endNetworkSolution(true);assertTrue(m.inverterShorted());
    }
    @Test void commutationPickupDoesNotRetroactivelyShortTheCorrector()throws Exception {
        var f=fixture(true);initializePorts(f);var p=f.ports().rectifierPort();var m=f.binding().runtime();
        assertFalse(m.inverterShorted());double h=.00001;double[] boundary=new double[10];m.readBoundary(boundary,0);
        double resistance=f.binding().owner().getRdc(org.interpss.numeric.datatype.Unit.UnitType.Ohm);
        double initialCurrent=boundary[2],initialRate=(boundary[0]-boundary[1]-resistance*boundary[2])/f.binding().effectiveHenries();
        p.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,0);
        var inverter=f.ports().inverterPort().getDStabBus();inverter.setVoltage(inverter.getVoltage().multiply(.1));
        p.beginNetworkSolution(context("PREDICTOR",h));p.beforeNetworkIteration(0);m.readBoundary(boundary,0);
        assertTrue(Math.toDegrees(boundary[8])<10,"The predictor must cross the entered commutation threshold");
        assertFalse(p.applyConvergedNetworkAction());assertFalse(m.inverterShorted());
        p.afterNetworkConvergence();p.endNetworkSolution(true);m.readBoundary(boundary,0);
        double predictedRate=(boundary[0]-boundary[1]-resistance*boundary[2])/f.binding().effectiveHenries();
        double expectedCurrent=initialCurrent+.5*h*(initialRate+predictedRate);
        p.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,1);p.beginNetworkSolution(context("CORRECTOR",h));
        p.beforeNetworkIteration(0);assertTrue(p.applyConvergedNetworkAction());assertTrue(m.inverterShorted());
        m.readBoundary(boundary,0);assertEquals(expectedCurrent,boundary[2],1e-10);
        p.beforeNetworkIteration(0);assertFalse(p.applyConvergedNetworkAction());p.afterNetworkConvergence();p.endNetworkSolution(true);
        assertTrue(m.inverterShorted());assertEquals(h,m.getIntegrationHistories().get("Commutation transition time (s)"),0);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void explicitProtectionClockAccumulatesOncePerAcceptedOuterInterval(boolean staggered)throws Exception {
        double h=.0005;var f=fixture(true,Chvdc2TerminalPorts.ProtectionClock.BINARY32,-2*h);
        var a=f.network().context().getDynSimuAlgorithm();var m=f.binding().runtime();
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(h);a.setTotalSimuTimeSec(.1);
        a.setSimuOutputHandler(new StateMonitor());a.setSolver(staggered?new StaggeredEulerSolver(a):new DefaultDynamicSimulationSolver(a));
        assertTrue(a.initialization());a.setSimuTime(-2*h);float clock=(float)(-2*h);
        assertEquals((double)clock,m.getIntegrationHistories().get("Accepted time (s)"),0);
        for(int k=0;k<160;k++){
            assertTrue(a.solveDEqnStep(true));clock+=(float)h;
            assertEquals((double)clock,m.getIntegrationHistories().get("Accepted time (s)"),0);
            assertEquals((k-1)*h,a.getSimuTime(),1e-14,"The solver clock remains double precision");
        }
        assertNotEquals(a.getSimuTime(),(double)clock);
        var p=f.ports().rectifierPort();double time=a.getSimuTime();
        for(int k=0;k<2;k++){
            p.beginNetworkSolution(new Context("INITIAL",time,time,50,1e-10,true));
            p.beforeNetworkIteration(0);p.applyConvergedNetworkAction();p.afterNetworkConvergence();p.endNetworkSolution(true);
            assertEquals((double)clock,m.getIntegrationHistories().get("Accepted time (s)"),0,"Same-time events do not tick the clock");
        }
        initializePorts(f);assertEquals((double)(float)(-2*h),m.getIntegrationHistories().get("Accepted time (s)"),0);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void failedIntegrationDoesNotConsumeAProtectionClockTick(boolean staggered)throws Exception {
        var f=fixture(true,Chvdc2TerminalPorts.ProtectionClock.BINARY32,0);initializePorts(f);
        var p=f.ports().rectifierPort();var m=f.binding().runtime();var history=m.getIntegrationHistories();double h=.0005;
        for(boolean success:new boolean[]{false,true}){
            if(staggered){
                double[] x=new double[16],rate=new double[16];p.readStaggeredState(x,0);p.evaluateStaggered(h,rate,0);
                for(int k=0;k<x.length;k++)x[k]+=h*rate[k];
                p.applyStaggeredState(x,0,h);p.applyStaggeredState(x,0,h);p.completeStaggeredStep(h,h);
                p.beginNetworkSolution(context("DIRECT",h));
            }else{p.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,0);p.beginNetworkSolution(context("PREDICTOR",h));}
            p.beforeNetworkIteration(0);p.applyConvergedNetworkAction();p.afterNetworkConvergence();p.endNetworkSolution(staggered?success:true);
            if(!staggered){
                p.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,1);p.beginNetworkSolution(context("CORRECTOR",h));
                p.beforeNetworkIteration(0);p.applyConvergedNetworkAction();p.afterNetworkConvergence();p.endNetworkSolution(success);
            }
            assertEquals(success?(double)(float)h:0,m.getIntegrationHistories().get("Accepted time (s)"),0);
            if(!success)assertEquals(history,m.getIntegrationHistories());
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void unrepresentableProtectionClockAdvanceRollsBackWithoutChangingStates(boolean staggered)throws Exception {
        var f=fixture(true,Chvdc2TerminalPorts.ProtectionClock.BINARY32,1e8);initializePorts(f);
        var p=f.ports().rectifierPort();var m=f.binding().runtime();var history=m.getIntegrationHistories();
        var states=m.getIntegrationStates();double[] x=new double[16];p.readStaggeredState(x,0);
        for(int retry=0;retry<2;retry++){
            if(staggered)assertThrows(IllegalStateException.class,()->p.applyStaggeredState(x,0,.0005));
            else assertThrows(IllegalStateException.class,()->p.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0));
            assertEquals(states,m.getIntegrationStates());assertEquals(history,m.getIntegrationHistories());
        }
        assertThrows(NullPointerException.class,()->new Chvdc2TerminalPorts(f.binding(),null));
        var b=f.binding();var outside=new Chvdc2NetworkPreparation.Binding(b.owner(),
                new Chvdc2NetworkPreparation.OperatingPoint(b.operatingPoint().rectifierVolts(),b.operatingPoint().inverterVolts(),
                        b.operatingPoint().currentAmperes(),Double.MAX_VALUE),b.networkIntervalSeconds(),b.frequencyHz(),b.effectiveHenries(),b.capacitanceFarads(),b.runtime());
        assertThrows(IllegalArgumentException.class,()->new Chvdc2TerminalPorts(outside,Chvdc2TerminalPorts.ProtectionClock.BINARY32));
        assertEquals(Chvdc2TerminalPorts.ProtectionClock.CALLER_TIME,new Chvdc2TerminalPorts(b).protectionClock());
    }
}
