package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.ac9c.Ac9cData;
import org.interpss.dstab.control.exc.psse.ac9c.Ac9cExciter;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** PowerWorld/IEEE AC9C import, equation, source and power-stage tests. */
public class DStabNetworkBuilderAc9cTest extends CorePluginTestSetup {
    private static final double TOL=1e-10;

    @Test
    void parsesExactFortyFiveParameterPsseRecordWithoutPslfAlias(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Path dyr=dir.resolve("ac9c.dyr");
        Files.writeString(dyr,"1 'AC9C' 1 2 3 2 .01 7 8 9 .1 10 -10 11 12 13 -13 14 .2 15 -15 .3 .4 .5 16 -16 1 .6 .7 .8 .9 17 -.2 5.6 .86 4.2 .5 1.4 .9 1.1 .12 .13 .14 30 20 21 .2 -.1 /\n");
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(dyr.toString());
        Ac9cExciter e=(Ac9cExciter)b.getDStabNetwork().getMachine("Bus1-mach1").getExciter();assertNotNull(e);
        Ac9cData d=e.getData();assertEquals(2,d.getOelLocation());assertEquals(3,d.getUelLocation());assertEquals(2,d.getSw1());
        assertEquals(.01,d.getTr(),TOL);assertEquals(7,d.getKpr(),TOL);assertEquals(8,d.getKir(),TOL);
        assertEquals(9,d.getKdr(),TOL);assertEquals(.1,d.getTdr(),TOL);assertEquals(10,d.getVpidmax(),TOL);
        assertEquals(-10,d.getVpidmin(),TOL);assertEquals(11,d.getKpa(),TOL);assertEquals(12,d.getKia(),TOL);
        assertEquals(13,d.getVamax(),TOL);assertEquals(-13,d.getVamin(),TOL);assertEquals(14,d.getKa(),TOL);
        assertEquals(.2,d.getTa(),TOL);assertEquals(15,d.getVrmax(),TOL);assertEquals(-15,d.getVrmin(),TOL);
        assertEquals(.3,d.getKf(),TOL);assertEquals(.4,d.getTf(),TOL);assertEquals(.5,d.getKfw(),TOL);
        assertEquals(16,d.getVfwmax(),TOL);assertEquals(-16,d.getVfwmin(),TOL);assertEquals(1,d.getSct());
        assertEquals(.6,d.getKc(),TOL);assertEquals(.7,d.getKd(),TOL);assertEquals(.8,d.getKe(),TOL);
        assertEquals(.9,d.getTe(),TOL);assertEquals(17,d.getVfemax(),TOL);assertEquals(-.2,d.getVemin(),TOL);
        assertEquals(5.6,d.getE1(),TOL);assertEquals(.86,d.getSe1(),TOL);assertEquals(4.2,d.getE2(),TOL);
        assertEquals(.5,d.getSe2(),TOL);assertEquals(1.4,d.getKp(),TOL);assertEquals(.9,d.getKi1(),TOL);
        assertEquals(1.1,d.getKi2(),TOL);assertEquals(.12,d.getKc1(),TOL);assertEquals(.13,d.getKc2(),TOL);
        assertEquals(.14,d.getXl(),TOL);assertEquals(30,d.getThetaP(),TOL);assertEquals(20,d.getVbmax1(),TOL);
        assertEquals(21,d.getVbmax2(),TOL);assertEquals(.2,d.getVlim1(),TOL);assertEquals(-.1,d.getVlim2(),TOL);
        assertTrue(p.getLastImportReport().isStrictlyComplete());var descriptor=DynamicModelCatalog.find("AC9C").orElseThrow();
        assertEquals(45,descriptor.parameterCount());assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(DynamicModelCatalog.find("ESAC9C").isEmpty());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESAC9C").orElseThrow().isImplementedExactly());
    }

    @Test
    void sevenStateTrajectoryMatchesIndependentEquationOracle()throws Exception{
        Fixture f=fixture(dynamicData());f.exciter.setRefPoint(f.exciter.getRefPoint()+.05);
        double[] x={1.2,1.04,0,.6,.6,1.2,.6};double dt=.0001,max=0;
        for(int i=0;i<2000;i++){
            double[] d0=derivatives(x,.05),predict=add(x,d0,dt),d1=derivatives(predict,.05);
            for(int j=0;j<7;j++)x[j]+=.5*(d0[j]+d1[j])*dt;step(f.exciter,f.machine,dt);
            max=Math.max(max,Math.abs(x[0]-f.exciter.getInternalFieldVoltage()));
            max=Math.max(max,Math.abs(x[1]-f.exciter.getSensedVoltage()));
            max=Math.max(max,Math.abs(x[5]-f.exciter.getBridgeOutput()));
            max=Math.max(max,Math.abs(x[6]-f.exciter.getFilteredFieldCurrent()));
        }
        assertTrue(max<1e-9,"AC9C seven-state equation max error="+max);
    }

    @Test
    void sumsCompoundAndSpecialCurrentControlledRectifierSources()throws Exception{
        Ac9cData d=algebraicData();d.setSw1(Ac9cExciter.SWITCH_A);d.setKp(2);d.setKi1(.3);d.setKi2(.4);
        d.setXl(.2);d.setThetaP(30);d.setKc1(.1);d.setKc2(.2);d.setVbmax1(20);d.setVbmax2(21);
        Fixture f=fixture(d);Complex kp=new Complex(2*Math.cos(Math.PI/6),2*Math.sin(Math.PI/6));
        Complex current=f.machine.getIxy().divide(f.machine.getIMultiFactor());
        double source1=kp.multiply(f.machine.getDStabBus().getVoltage())
                .add(Complex.I.multiply(new Complex(.3,0).add(kp.multiply(.2))).multiply(current)).abs();
        double source2=current.multiply(.4).abs();
        assertEquals(source1,f.exciter.getCompoundSource(),TOL);assertEquals(source2,f.exciter.getCurrentSource(),TOL);
        double vfe=f.exciter.getFieldFeedback();
        double expected=Math.min(20,source1*Exac1Exciter.rectifierFactor(.1*vfe/source1))
                +Math.min(21,source2*Exac1Exciter.rectifierFactor(.2*vfe/source2));
        assertEquals(expected,f.exciter.getControlledAvailableVoltage(),TOL);
    }

    @Test
    void auxiliaryBridgeUsesPointZeroZeroOneOnlyAsItsDivisionFloor()throws Exception{
        Ac9cData d=algebraicData();d.setKi2(1e-5);d.setKc2(.2);d.setVbmax2(10);
        Fixture f=fixture(d);double raw=f.exciter.getCurrentSource(),vfe=f.exciter.getFieldFeedback();
        double expected=raw*Exac1Exciter.rectifierFactor(.2*vfe/.001);
        assertEquals(expected,f.exciter.getControlledAvailableVoltage(),TOL);
        Ac9cData disabled=algebraicData();disabled.setKi2(0);disabled.setKc2(.2);disabled.setVbmax2(10);
        assertEquals(0,fixture(disabled).exciter.getControlledAvailableVoltage(),TOL);
    }

    @Test
    void implementsThyristorAndAllThreeChopperRegions()throws Exception{
        Fixture thyristor=fixture(powerStageData(Ac9cExciter.THYRISTOR,-1,-2,0));
        assertEquals(thyristor.exciter.getControlledAvailableVoltage(),thyristor.exciter.getPowerStageBias(),TOL);
        Fixture high=fixture(powerStageData(Ac9cExciter.CHOPPER,-1,-2,0));
        assertEquals(high.exciter.getControlledAvailableVoltage(),high.exciter.getPowerStageBias(),TOL);
        Fixture middle=fixture(powerStageData(Ac9cExciter.CHOPPER,2,-1,0));
        assertEquals(0,middle.exciter.getPowerStageBias(),TOL);
        Fixture low=fixture(powerStageData(Ac9cExciter.CHOPPER,2,1.5,.0833333333333333));
        assertEquals(-low.exciter.getFreeWheelFeedback(),low.exciter.getPowerStageBias(),TOL);
    }

    @Test
    void routesSummationAndBothTakeoverGateLocations()throws Exception{
        Ac9cExciter sum=fixtureWithLocations(1,1,1);sum.setVoel(-.1);sum.setVuel(.2);sum.setVsclSum(.3);
        assertEquals(.4,sum.getVoltageError(),TOL);
        Ac9cExciter u1=fixtureWithLocations(0,2,0);u1.setVuel(.5);assertEquals(.5,u1.getCurrentError(),TOL);
        Ac9cExciter o1=fixtureWithLocations(2,0,0);o1.setVoel(-.4);assertEquals(-.4,o1.getCurrentError(),TOL);
        Ac9cExciter s1=fixtureWithLocations(0,0,2);s1.setVsclUel(.3);assertEquals(.3,s1.getCurrentError(),TOL);
        s1.setVsclOel(-.3);assertEquals(-.3,s1.getCurrentError(),TOL);
        Ac9cExciter u2=fixtureWithLocations(0,3,0);u2.setVuel(1.6);assertEquals(1.6,u2.getGatedCurrentRegulatorOutput(),TOL);
        Ac9cExciter o2=fixtureWithLocations(3,0,0);o2.setVoel(-.6);assertEquals(-.6,o2.getGatedCurrentRegulatorOutput(),TOL);
        Ac9cExciter s2=fixtureWithLocations(0,0,3);s2.setVsclUel(1.5);assertEquals(1.5,s2.getGatedCurrentRegulatorOutput(),TOL);
        s2.setVsclOel(-.5);assertEquals(-.5,s2.getGatedCurrentRegulatorOutput(),TOL);
    }

    @Test
    void correctsTimesExpandsFourInitializationLimitsAndParticipatesInSolver()throws Exception{
        Ac9cData d=dynamicData();d.setTr(.004);d.setTdr(.012);d.setTa(.011);d.setTf(.012);d.setTe(.01);
        d.setVpidmax(-1);d.setVpidmin(-2);d.setVamax(-1);d.setVamin(-2);d.setVrmax(-1);d.setVrmin(-2);
        d.setVfwmax(-1);d.setVfwmin(-2);DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();
        Ac9cExciter e=b.addExcAc9c("Bus1","1",d);Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");
        m.setEfd(1.2);e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));
        assertEquals(0,e.tr,TOL);assertEquals(.02,e.tdr,TOL);assertEquals(.02,e.ta,TOL);
        assertEquals(.02,e.tf,TOL);assertEquals(.02,e.te,TOL);assertTrue(e.vpidmax>=e.getIfdReference());
        assertTrue(e.vamax>=e.getCurrentRegulatorOutput());assertTrue(e.vrmax>=e.getBridgeOutput());
        assertTrue(e.vfwmax>=e.getFreeWheelFeedback());
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);a.setSimuOutputHandler(new StateMonitor());
        assertTrue(a.getAclfAlgorithm().loadflow());assertTrue(a.initialization());assertTrue(a.performSimulation());
    }

    @Test
    void freezesBothNonWindupIntegratorsWhileTheirOutputsAreLimited()throws Exception{
        Ac9cData d=dynamicData();d.setVpidmax(.61);d.setVpidmin(-100);d.setVamax(.61);d.setVamin(-100);
        Fixture f=fixture(d);double pid0=f.exciter.getPidIntegralState(),current0=f.exciter.getCurrentIntegralState();
        f.exciter.setRefPoint(f.exciter.getRefPoint()+.2);step(f.exciter,f.machine,.01);
        assertEquals(.61,f.exciter.getIfdReference(),TOL);
        assertEquals(.61,f.exciter.getCurrentRegulatorOutput(),TOL);
        assertEquals(pid0,f.exciter.getPidIntegralState(),TOL);
        assertEquals(current0,f.exciter.getCurrentIntegralState(),TOL);
    }

    @Test
    void solvesZeroTeExciterAsAnAlgebraicConstraint()throws Exception{
        Ac9cData d=algebraicData();d.setTe(0);Fixture f=fixture(d);
        f.exciter.setRefPoint(f.exciter.getRefPoint()+.05);step(f.exciter,f.machine,.01);
        assertTrue(Double.isFinite(f.exciter.getInternalFieldVoltage()));
        assertEquals(f.exciter.getExciterInput(),f.exciter.getFieldFeedback(),1e-9);
    }

    private static Ac9cExciter fixtureWithLocations(int oel,int uel,int scl)throws Exception{
        Ac9cData d=algebraicData();d.setOelLocation(oel);d.setUelLocation(uel);d.setSclLocation(scl);return fixture(d).exciter;
    }
    private static Ac9cData powerStageData(int sct,double vlim1,double vlim2,double kfw){
        Ac9cData d=algebraicData();d.setSct(sct);d.setSw1(Ac9cExciter.SWITCH_B);d.setKp(1.3);
        d.setVbmax1(10);d.setKfw(kfw);d.setVlim1(vlim1);d.setVlim2(vlim2);return d;
    }
    private static Fixture fixture(Ac9cData d)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Ac9cExciter e=b.addExcAc9c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(b,m,e);
    }
    private static Ac9cData algebraicData(){Ac9cData d=new Ac9cData();d.setSw1(2);d.setSct(1);
        d.setTr(0);d.setKpr(1);d.setKir(0);d.setKdr(0);d.setTdr(0);d.setVpidmax(100);d.setVpidmin(-100);
        d.setKpa(1);d.setKia(0);d.setVamax(100);d.setVamin(-100);d.setKa(1);d.setTa(0);
        d.setVrmax(100);d.setVrmin(-100);d.setKf(0);d.setTf(0);d.setKfw(0);d.setVfwmax(100);d.setVfwmin(-100);
        d.setKc(0);d.setKd(0);d.setKe(1);d.setTe(.4);d.setVfemax(100);d.setVemin(0);
        d.setE1(0);d.setSe1(0);d.setE2(0);d.setSe2(0);d.setKp(0);d.setKi1(0);d.setKi2(0);
        d.setKc1(0);d.setKc2(0);d.setXl(0);d.setThetaP(0);d.setVbmax1(100);d.setVbmax2(100);
        d.setVlim1(0);d.setVlim2(-.1);return d;}
    private static Ac9cData dynamicData(){Ac9cData d=algebraicData();d.setTr(.1);d.setKpr(2);d.setKir(3);
        d.setKdr(.4);d.setTdr(.2);d.setKpa(1.5);d.setKia(2.5);d.setKa(2);d.setTa(.3);
        d.setKf(.5);d.setTf(.2);d.setTe(.4);return d;}
    private static double[] derivatives(double[] x,double step){double error=1.04+step-x[1];
        double derivative=.4*(error-x[2])/.2,ifdRef=2*error+x[3]+derivative,currentError=ifdRef-x[6];
        double vavr=1.5*currentError+x[4];return new double[]{(x[5]-x[0])/.4,(1.04-x[1])/.1,
                (error-x[2])/.2,3*error,2.5*currentError,(2*vavr-x[5])/.3,(.5*x[0]-x[6])/.2};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(Ac9cExciter e,Machine m,double dt){assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));}
    private record Fixture(DStabNetworkBuilder builder,Machine machine,Ac9cExciter exciter){}
}
