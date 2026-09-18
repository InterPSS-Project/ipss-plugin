package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.ac11c.Ac11cData;
import org.interpss.dstab.control.exc.psse.ac11c.Ac11cExciter;
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
import com.interpss.dstab.controller.deqn.AbstractStabilizer;

/** PowerWorld/IEEE AC11C import, nine-state equation and switching tests. */
public class DStabNetworkBuilderAc11cTest extends CorePluginTestSetup {
    private static final double TOL=1e-10;

    @Test
    void parsesExactFortyParameterPsseRecordWithoutPslfAlias(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Path dyr=dir.resolve("ac11c.dyr");
        Files.writeString(dyr,"1 'AC11C' 1 2 3 2 2 .01 6 .2 7 .3 8 .4 9 .5 10 -10 11 -11 12 -12 .6 .1 .2 1 20 0 5.6 .86 4.2 .5 1.4 .9 .14 30 .12 20 1.1 .13 21 .2 .7 /\n");
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(dyr.toString());
        Ac11cExciter e=(Ac11cExciter)b.getDStabNetwork().getMachine("Bus1-mach1").getExciter();assertNotNull(e);
        Ac11cData d=e.getData();assertEquals(2,d.getOelLocation());assertEquals(3,d.getUelLocation());
        assertEquals(2,d.getVosLocation());assertEquals(2,d.getSw1());assertEquals(.01,d.getTr(),TOL);
        assertEquals(6,d.getKpa(),TOL);assertEquals(.2,d.getTia(),TOL);assertEquals(7,d.getKpu(),TOL);
        assertEquals(.3,d.getTiu(),TOL);assertEquals(8,d.getKb(),TOL);assertEquals(.4,d.getTb(),TOL);
        assertEquals(9,d.getKpo(),TOL);assertEquals(.5,d.getTio(),TOL);assertEquals(10,d.getVrsmax(),TOL);
        assertEquals(-10,d.getVrsmin(),TOL);assertEquals(11,d.getVrmax(),TOL);assertEquals(-11,d.getVrmin(),TOL);
        assertEquals(12,d.getVamax(),TOL);assertEquals(-12,d.getVamin(),TOL);assertEquals(.6,d.getTe(),TOL);
        assertEquals(.1,d.getKc(),TOL);assertEquals(.2,d.getKd(),TOL);assertEquals(1,d.getKe(),TOL);
        assertEquals(20,d.getVfemax(),TOL);assertEquals(0,d.getVemin(),TOL);assertEquals(5.6,d.getE1(),TOL);
        assertEquals(.86,d.getSe1(),TOL);assertEquals(4.2,d.getE2(),TOL);assertEquals(.5,d.getSe2(),TOL);
        assertEquals(1.4,d.getKp(),TOL);assertEquals(.9,d.getKi(),TOL);assertEquals(.14,d.getXl(),TOL);
        assertEquals(30,d.getThetaP(),TOL);assertEquals(.12,d.getKc1(),TOL);assertEquals(20,d.getVbmax1(),TOL);
        assertEquals(1.1,d.getKi2(),TOL);assertEquals(.13,d.getKc2(),TOL);assertEquals(21,d.getVbmax2(),TOL);
        assertEquals(.2,d.getKboost(),TOL);assertEquals(.7,d.getVboost(),TOL);
        assertTrue(p.getLastImportReport().isStrictlyComplete());var descriptor=DynamicModelCatalog.find("AC11C").orElseThrow();
        assertEquals(40,descriptor.parameterCount());assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(DynamicModelCatalog.find("ESAC11C").isEmpty());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESAC11C").orElseThrow().isImplementedExactly());
    }

    @Test
    void nineStateTrajectoryMatchesIndependentFigureE10Oracle()throws Exception{
        Fixture f=fixture(dynamicData());double vt=f.machine.getDStabBus().getVoltageMag();
        f.exciter.setRefPoint(f.exciter.getRefPoint()+.05);
        double[] x={1.2,vt,1.2,1.2,0,0,1.2,1.2,1.2};double dt=.0001,max=0;
        for(int i=0;i<1200;i++){
            double[] d0=derivatives(x,vt,.05),predict=add(x,d0,dt),d1=derivatives(predict,vt,.05);
            for(int j=0;j<9;j++)x[j]+=.5*(d0[j]+d1[j])*dt;step(f.exciter,f.machine,dt);
            double[] actual={f.exciter.getInternalFieldVoltage(),f.exciter.getSensedVoltage(),
                    f.exciter.getMainLagState(),f.exciter.getMainIntegralState(),
                    f.exciter.getPssLagState(),f.exciter.getPssIntegralState(),
                    f.exciter.getUelLagState(),f.exciter.getUelIntegralState(),f.exciter.getOelIntegralState()};
            for(int j=0;j<9;j++)max=Math.max(max,Math.abs(x[j]-actual[j]));
        }
        assertTrue(max<1e-9,"AC11C nine-state equation max error="+max);
    }

    @Test
    void implementsBothCompoundSourcesBoostThresholdAndPowerStageOrdering()throws Exception{
        Ac11cData d=algebraicData();d.setSw1(Ac11cExciter.SWITCH_A);d.setKp(1.4);d.setKi(.3);
        d.setXl(.2);d.setThetaP(30);d.setKc1(.1);d.setVbmax1(20);
        d.setKi2(.4);d.setKc2(.2);d.setVbmax2(21);d.setKboost(.15);d.setVboost(2);
        Fixture f=fixture(d);Complex kp=new Complex(1.4*Math.cos(Math.PI/6),1.4*Math.sin(Math.PI/6));
        Complex current=f.machine.getIxy().divide(f.machine.getIMultiFactor());
        double source1=kp.multiply(f.machine.getDStabBus().getVoltage())
                .add(Complex.I.multiply(new Complex(.3,0).add(kp.multiply(.2))).multiply(current)).abs();
        double source2=current.multiply(.4).abs()+.15,vfe=f.exciter.getFieldFeedback();
        double vb1=Math.min(20,source1*Exac1Exciter.rectifierFactor(.1*vfe/source1));
        double vb2=Math.min(21,source2*Exac1Exciter.rectifierFactor(.2*vfe/Math.max(source2,.001)));
        assertEquals(source1,f.exciter.getCompoundSource(),TOL);assertEquals(source2-.15,f.exciter.getCurrentSource2(),TOL);
        assertEquals(vb1,f.exciter.getBridge1Output(),TOL);assertEquals(vb2,f.exciter.getBridge2Output(),TOL);
        assertEquals(vb2,f.exciter.getBoostOutput(),TOL);
        assertEquals(f.exciter.getControlElementOutput()*vb1+vb2,f.exciter.getExciterInput(),TOL);
        f.exciter.vboost=0;assertEquals(0,f.exciter.getBoostOutput(),TOL);
    }

    @Test
    void routesLimitersAndOnlyUelOelGateOneActivateAlternateControllers()throws Exception{
        Ac11cData uelData=algebraicData();uelData.setUelLocation(2);Fixture uel=fixture(uelData);
        uel.exciter.setVuel(.4);assertTrue(uel.exciter.isUelControllerActive());
        assertEquals(uel.exciter.getUelRegulatorOutput(),uel.exciter.getSelectedRegulatorOutput(),TOL);
        Ac11cData oelData=algebraicData();oelData.setOelLocation(2);Fixture oel=fixture(oelData);
        oel.exciter.setVoel(-.4);assertTrue(oel.exciter.isOelControllerActive());
        assertEquals(oel.exciter.getOelRegulatorOutput(),oel.exciter.getSelectedRegulatorOutput(),TOL);
        Ac11cData sclData=algebraicData();sclData.setSclLocation(2);Fixture scl=fixture(sclData);
        scl.exciter.setVsclUel(.5);assertFalse(scl.exciter.isUelControllerActive());
        assertFalse(scl.exciter.isOelControllerActive());
        assertEquals(scl.exciter.getMainRegulatorOutput(),scl.exciter.getSelectedRegulatorOutput(),TOL);
        Ac11cData gate2Data=algebraicData();gate2Data.setUelLocation(3);gate2Data.setOelLocation(3);
        gate2Data.setSclLocation(3);Fixture gate2=fixture(gate2Data);gate2.exciter.setVuel(2);
        gate2.exciter.setVsclUel(2.1);gate2.exciter.setVoel(1.9);gate2.exciter.setVsclOel(1.8);
        assertEquals(1.8,gate2.exciter.getGatedRegulatorOutput(),TOL);
    }

    @Test
    void vosTwoReroutesPssOnlyWhileUelOrOelControllerIsActive()throws Exception{
        Ac11cData d=algebraicData();d.setVosLocation(2);d.setUelLocation(2);Fixture f=fixture(d);
        new FixedOutputStabilizer(f.machine,.1);
        assertEquals(.1,f.exciter.getGatedVoltageError(),TOL);assertEquals(0,f.exciter.getPssRegulatorInput(),TOL);
        f.exciter.setVuel(.5);assertTrue(f.exciter.isUelControllerActive());
        assertEquals(.5,f.exciter.getGatedVoltageError(),TOL);assertEquals(.1,f.exciter.getPssRegulatorInput(),TOL);
        Ac11cData atOutput=algebraicData();atOutput.setVosLocation(3);Fixture output=fixture(atOutput);
        new FixedOutputStabilizer(output.machine,.2);
        assertEquals(0,output.exciter.getGatedVoltageError(),TOL);
        assertEquals(.2,output.exciter.getPssRegulatorInput(),TOL);
    }

    @Test
    void figureE10FreezesIntegratorButContinuesDerivativeStateAtLimit()throws Exception{
        Ac11cData d=dynamicData();d.setVrmax(1.21);d.setVrmin(-100);Fixture f=fixture(d);
        double integral=f.exciter.getMainIntegralState(),lag=f.exciter.getMainLagState();
        f.exciter.setRefPoint(f.exciter.getRefPoint()+.2);step(f.exciter,f.machine,.01);
        assertEquals(integral,f.exciter.getMainIntegralState(),TOL);
        assertTrue(Math.abs(f.exciter.getMainLagState()-lag)>1e-6);
        Ac11cData pssData=dynamicData();pssData.setVosLocation(3);pssData.setVrsmax(.01);pssData.setVrsmin(-.01);
        Fixture pss=fixture(pssData);new FixedOutputStabilizer(pss.machine,.2);
        double pssIntegral=pss.exciter.getPssIntegralState();step(pss.exciter,pss.machine,.01);
        assertEquals(pssIntegral,pss.exciter.getPssIntegralState(),TOL);
        assertTrue(Math.abs(pss.exciter.getPssLagState())>1e-6);
    }

    @Test
    void correctsPublishedTimesAndGainsExpandsLimitsAndParticipatesInSolver()throws Exception{
        Ac11cData d=dynamicData();d.setTr(.004);d.setTia(.01);d.setTiu(.01);d.setTio(.01);d.setTe(.01);
        d.setKpa(0);d.setKpu(0);d.setKb(0);d.setTb(0);d.setKpo(0);
        d.setVrsmax(-1);d.setVrsmin(-2);d.setVrmax(-1);d.setVrmin(-2);d.setVamax(-1);d.setVamin(-2);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Ac11cExciter e=b.addExcAc11c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);e.configureIntegrationStep(.01,2);
        assertTrue(e.initStates(m.getDStabBus(),m));assertEquals(0,e.tr,TOL);assertEquals(.02,e.tia,TOL);
        assertEquals(.02,e.tiu,TOL);assertEquals(.02,e.tio,TOL);assertEquals(.02,e.te,TOL);
        assertEquals(.02,e.kpa,TOL);assertEquals(.02,e.kpu,TOL);assertEquals(.02,e.kb,TOL);
        assertEquals(.02,e.tb,TOL);assertEquals(.02,e.kpo,TOL);assertTrue(e.vrmax>=e.getLimitedRegulatorOutput());
        assertTrue(e.vamax>=e.getControlElementOutput());assertTrue(e.vrsmax>=e.getPssRegulatorOutput());
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);
        a.setSimuOutputHandler(new StateMonitor());assertTrue(a.getAclfAlgorithm().loadflow());
        assertTrue(a.initialization());assertTrue(a.performSimulation());
    }

    @Test
    void solvesZeroTeExciterAsAlgebraicConstraint()throws Exception{
        Ac11cData d=algebraicData();d.setTe(0);Fixture f=fixture(d);
        f.exciter.setRefPoint(f.exciter.getRefPoint()+.05);step(f.exciter,f.machine,.01);
        assertTrue(Double.isFinite(f.exciter.getInternalFieldVoltage()));
        assertEquals(f.exciter.getExciterInput(),f.exciter.getFieldFeedback(),1e-9);
    }

    private static Fixture fixture(Ac11cData d)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Ac11cExciter e=b.addExcAc11c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertTrue(e.initStates(m.getDStabBus(),m));
        return new Fixture(b,m,e);
    }
    private static Ac11cData algebraicData(){Ac11cData d=new Ac11cData();d.setVosLocation(1);d.setSw1(2);
        d.setTr(0);d.setKpa(1);d.setTia(1);d.setKpu(1);d.setTiu(1);d.setKb(1);d.setTb(1);
        d.setKpo(1);d.setTio(1);d.setVrsmax(100);d.setVrsmin(-100);d.setVrmax(100);d.setVrmin(-100);
        d.setVamax(100);d.setVamin(-100);d.setTe(.4);d.setKc(0);d.setKd(0);d.setKe(1);
        d.setVfemax(100);d.setVemin(0);d.setE1(0);d.setSe1(0);d.setE2(0);d.setSe2(0);
        d.setKp(1);d.setKi(0);d.setXl(0);d.setThetaP(0);d.setKc1(0);d.setVbmax1(100);
        d.setKi2(0);d.setKc2(0);d.setVbmax2(100);d.setKboost(0);d.setVboost(0);return d;}
    private static Ac11cData dynamicData(){Ac11cData d=algebraicData();d.setTr(.1);d.setKpa(2);d.setTia(.5);
        d.setKpu(1.5);d.setTiu(.4);d.setKb(.5);d.setTb(.25);d.setKpo(1.2);d.setTio(.3);return d;}
    private static double[] derivatives(double[] x,double vt,double refStep){double error=vt+refStep-x[1];
        double mainPi=2*error+x[3],main=mainPi+.5*(mainPi-x[2]);
        double uelPi=1.5*error+x[7];return new double[]{(main-x[0])/.4,(vt-x[1])/.1,
                .5/.25*(mainPi-x[2]),2/.5*error,.5/.25*(0-x[4]),0,
                .5/.25*(uelPi-x[6]),1.5/.4*error,1.2/.3*error};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(Ac11cExciter e,Machine m,double dt){assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));}
    private record Fixture(DStabNetworkBuilder builder,Machine machine,Ac11cExciter exciter){}

    private static final class FixedOutputStabilizer extends AbstractStabilizer {
        private final double output;
        FixedOutputStabilizer(Machine machine,double output){super("fixed-pss","Fixed PSS","test");this.output=output;setMachine(machine);}
        @Override public double getOutput(Machine machine){return output;}
    }
}
