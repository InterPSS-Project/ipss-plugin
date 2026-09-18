package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.ac7c.Ac7cData;
import org.interpss.dstab.control.exc.psse.ac7c.Ac7cExciter;
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

/** PowerWorld/IEEE AC7C import, equation, routing and solver tests. */
public class DStabNetworkBuilderAc7cTest extends CorePluginTestSetup {
    private static final double TOL=1e-10;

    @Test
    void parsesExactThirtyEightParameterPsseRecordWithoutPslfAlias(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Path dyr=dir.resolve("ac7c.dyr");
        Files.writeString(dyr,"1 'AC7C' 1 2 3 2 1 2 .01 7 8 9 .1 10 -10 11 12 13 -13 1.4 .6 .1 .2 .3 .4 .5 .6 .7 .8 14 -.2 5.6 .86 4.2 .5 .9 1.1 30 .12 20 .15 /\n");
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(dyr.toString());
        Ac7cExciter e=(Ac7cExciter)b.getDStabNetwork().getMachine("Bus1-mach1").getExciter();assertNotNull(e);
        Ac7cData d=e.getData();assertEquals(2,d.getOelLocation());assertEquals(3,d.getUelLocation());
        assertEquals(2,d.getVosLocation());assertEquals(1,d.getSw1());assertEquals(2,d.getSw2());
        assertEquals(.01,d.getTr(),TOL);assertEquals(7,d.getKpr(),TOL);assertEquals(8,d.getKir(),TOL);
        assertEquals(9,d.getKdr(),TOL);assertEquals(.1,d.getTdr(),TOL);assertEquals(10,d.getVrmax(),TOL);
        assertEquals(-10,d.getVrmin(),TOL);assertEquals(11,d.getKpa(),TOL);assertEquals(12,d.getKia(),TOL);
        assertEquals(13,d.getVamax(),TOL);assertEquals(-13,d.getVamin(),TOL);assertEquals(1.4,d.getKp(),TOL);
        assertEquals(.6,d.getKl(),TOL);assertEquals(.1,d.getKf1(),TOL);assertEquals(.2,d.getKf2(),TOL);
        assertEquals(.3,d.getKf3(),TOL);assertEquals(.4,d.getTf(),TOL);assertEquals(.5,d.getKc(),TOL);
        assertEquals(.6,d.getKd(),TOL);assertEquals(.7,d.getKe(),TOL);assertEquals(.8,d.getTe(),TOL);
        assertEquals(14,d.getVfemax(),TOL);assertEquals(-.2,d.getVemin(),TOL);assertEquals(5.6,d.getE1(),TOL);
        assertEquals(.86,d.getSe1(),TOL);assertEquals(4.2,d.getE2(),TOL);assertEquals(.5,d.getSe2(),TOL);
        assertEquals(.9,d.getKi(),TOL);assertEquals(1.1,d.getXl(),TOL);assertEquals(30,d.getThetaP(),TOL);
        assertEquals(.12,d.getKc1(),TOL);assertEquals(20,d.getVbmax(),TOL);assertEquals(.15,d.getKr(),TOL);
        assertTrue(p.getLastImportReport().isStrictlyComplete());var descriptor=DynamicModelCatalog.find("AC7C").orElseThrow();
        assertEquals(38,descriptor.parameterCount());assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(DynamicModelCatalog.find("ESAC7C").isEmpty());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESAC7C").orElseThrow().isImplementedExactly());
    }

    @Test
    void sixStateTrajectoryMatchesIndependentEquationOracle()throws Exception{
        Fixture f=fixture(dynamicData());f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double[] x={1.2,1.04,.36,0,1.2,1.2};double dt=.0001,max=0;
        for(int i=0;i<2000;i++){
            double[] d0=derivatives(x,.1),predict=add(x,d0,dt),d1=derivatives(predict,.1);
            for(int j=0;j<6;j++)x[j]+=.5*(d0[j]+d1[j])*dt;step(f.exciter,f.machine,dt);
            max=Math.max(max,Math.abs(x[0]-f.exciter.getInternalFieldVoltage()));
            max=Math.max(max,Math.abs(x[1]-f.exciter.getSensedVoltage()));
        }
        assertTrue(max<1e-9,"AC7C six-state equation max error="+max);
    }

    @Test
    void implementsCompensatedIndependentAndSelfExcitedPowerSources()throws Exception{
        Ac7cData d=algebraicData();d.setSw1(Ac7cExciter.SWITCH_A);d.setKp(2);d.setKi(.3);d.setXl(.2);d.setThetaP(30);
        d.setKc1(.1);d.setVbmax(20);Fixture f=fixture(d);
        Complex kp=new Complex(2*Math.cos(Math.PI/6),2*Math.sin(Math.PI/6));
        Complex currentMachineBase=f.machine.getIxy().divide(f.machine.getIMultiFactor());
        double expectedY=kp.multiply(f.machine.getDStabBus().getVoltage())
                .add(Complex.I.multiply(new Complex(.3,0).add(kp.multiply(.2))).multiply(currentMachineBase)).abs();
        assertEquals(expectedY,f.exciter.getPotentialSource(),TOL);
        double expectedBridge=Math.min(20,expectedY*Exac1Exciter.rectifierFactor(.1*f.exciter.getFieldFeedback()/expectedY));
        assertEquals(expectedBridge,f.exciter.getSelectedSupply(),TOL);

        Ac7cData independent=algebraicData();independent.setSw1(Ac7cExciter.SWITCH_B);independent.setKp(1.3);
        Fixture i=fixture(independent);assertEquals(1.3,i.exciter.getSelectedSupply(),TOL);
        Ac7cData self=algebraicData();self.setSw2(Ac7cExciter.SWITCH_B);self.setKr(.4);
        Fixture s=fixture(self);assertEquals(.4*s.exciter.getOutput(s.machine),s.exciter.getSelectedSupply(),TOL);
    }

    @Test
    void routesEveryOelUelAndSclTakeoverLocation()throws Exception{
        Ac7cExciter u1=fixtureWithLocations(0,2,0);u1.setVuel(.5);assertEquals(.5,u1.getPidError(),TOL);
        Ac7cExciter o1=fixtureWithLocations(2,0,0);o1.setVoel(-.4);assertEquals(-.4,o1.getPidError(),TOL);
        Ac7cExciter u2=fixtureWithLocations(0,3,0);u2.setVuel(.6);assertEquals(.6,u2.getPidOutput(),TOL);
        Ac7cExciter o2=fixtureWithLocations(3,0,0);o2.setVoel(-.6);assertEquals(-.6,o2.getPidOutput(),TOL);
        Ac7cExciter o3=fixtureWithLocations(4,0,0);o3.setVoel(.7);assertEquals(.7,o3.getPiOutput(),TOL);
        Ac7cExciter s1=fixtureWithLocations(0,0,2);s1.setVsclUel(.8);assertEquals(.8,s1.getPidError(),TOL);
        s1.setVsclOel(-.8);assertEquals(-.8,s1.getPidError(),TOL);
        Ac7cExciter s2=fixtureWithLocations(0,0,3);s2.setVsclUel(.9);assertEquals(.9,s2.getPidOutput(),TOL);
        s2.setVsclOel(-.9);assertEquals(-.9,s2.getPidOutput(),TOL);
    }

    @Test
    void appliesSummationRoutingAndFieldCurrentLowerLimit()throws Exception{
        Ac7cData d=algebraicData();d.setOelLocation(1);d.setUelLocation(1);d.setSclLocation(1);
        Fixture f=fixture(d);f.exciter.setVuel(.2);f.exciter.setVoel(-.1);f.exciter.setVsclSum(.3);
        assertEquals(.4,f.exciter.getPidError(),TOL);
        Ac7cData lower=algebraicData();lower.setKl(.5);Fixture l=fixture(lower);l.exciter.setRefPoint(l.exciter.getRefPoint()-10);
        assertEquals(-.5*l.exciter.getFieldFeedback(),l.exciter.getExciterInput(),TOL);
    }

    @Test
    void solvesTheZeroTeAlgebraicFieldLoop()throws Exception{
        Ac7cData d=dynamicData();d.setTe(0);Fixture f=fixture(d);
        f.exciter.setRefPoint(f.exciter.getRefPoint()+.05);
        for(int i=0;i<20;i++)step(f.exciter,f.machine,.001);
        assertTrue(Double.isFinite(f.exciter.getInternalFieldVoltage()));
        assertEquals(f.exciter.getFieldFeedback(),f.exciter.getExciterInput(),1e-9);
    }

    @Test
    void correctsParametersExpandsLimitsAndParticipatesInSolver()throws Exception{
        Ac7cData d=dynamicData();d.setTr(.004);d.setTdr(.012);d.setTf(.01);d.setTe(.01);
        d.setKpr(0);d.setKir(0);d.setVrmax(-1);d.setVrmin(-2);d.setVamax(-1);d.setVamin(-2);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Ac7cExciter e=b.addExcAc7c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));
        assertEquals(0,e.tr,TOL);assertEquals(.02,e.tdr,TOL);assertEquals(.02,e.tf,TOL);assertEquals(.02,e.te,TOL);
        assertEquals(40,e.kpr,TOL);assertTrue(e.vrmax>=e.getPidOutput());assertTrue(e.vrmin<=e.getPidOutput());
        assertTrue(e.vamax>=e.getPiOutput());assertTrue(e.vamin<=e.getPiOutput());
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);a.setSimuOutputHandler(new StateMonitor());
        assertTrue(a.getAclfAlgorithm().loadflow());assertTrue(a.initialization());assertTrue(a.performSimulation());
    }

    private static Ac7cExciter fixtureWithLocations(int oel,int uel,int scl)throws Exception{
        Ac7cData d=algebraicData();d.setOelLocation(oel);d.setUelLocation(uel);d.setSclLocation(scl);return fixture(d).exciter;
    }
    private static Fixture fixture(Ac7cData d)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Ac7cExciter e=b.addExcAc7c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(b,m,e);
    }
    private static Ac7cData algebraicData(){Ac7cData d=new Ac7cData();d.setVosLocation(1);d.setSw1(2);d.setSw2(1);
        d.setTr(0);d.setKpr(1);d.setKir(0);d.setKdr(0);d.setTdr(0);d.setVrmax(100);d.setVrmin(-100);
        d.setKpa(1);d.setKia(0);d.setVamax(100);d.setVamin(-100);d.setKp(1);d.setKl(1);
        d.setKf1(0);d.setKf2(0);d.setKf3(0);d.setTf(0);d.setKc(0);d.setKd(0);d.setKe(1);d.setTe(.4);
        d.setVfemax(100);d.setVemin(0);d.setE1(0);d.setSe1(0);d.setE2(0);d.setSe2(0);
        d.setKi(0);d.setXl(0);d.setThetaP(0);d.setKc1(0);d.setVbmax(100);d.setKr(0);return d;}
    private static Ac7cData dynamicData(){Ac7cData d=algebraicData();d.setTr(.1);d.setKpr(2);d.setKir(3);d.setKdr(.4);d.setTdr(.2);
        d.setKpa(4);d.setKia(5);d.setKf1(.1);d.setKf2(.2);d.setKf3(.3);d.setTf(.5);d.setTe(.4);return d;}
    private static double[] derivatives(double[] x,double step){double vfe=x[0],rate=.3*(vfe-x[5])/.5;
        double error=1.04+step-x[1]-rate,derivative=.4*(error-x[3])/.2,pid=2*error+x[2]+derivative;
        double piError=pid-.3*vfe,pi=4*piError+x[4],efe=Math.max(pi,-vfe);
        return new double[]{(efe-vfe)/.4,(1.04-x[1])/.1,3*error,(error-x[3])/.2,5*piError,(vfe-x[5])/.5};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(Ac7cExciter e,Machine m,double dt){assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));}
    private record Fixture(DStabNetworkBuilder builder,Machine machine,Ac7cExciter exciter){}
}
