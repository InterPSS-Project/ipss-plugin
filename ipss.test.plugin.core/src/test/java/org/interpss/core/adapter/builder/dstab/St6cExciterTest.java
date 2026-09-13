package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.complex.Complex;
import com.interpss.common.exp.InterpssException;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.st6c.St6cData;
import org.interpss.dstab.control.exc.psse.st6c.St6cExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Official-schema, equation, limiter and solver tests for native PSS/E ST6C. */
public class St6cExciterTest extends CorePluginTestSetup {
    private static final double TOL=1e-9;
    private static final Path CORPUS=Path.of(System.getProperty("psse.testcases.root",
            Path.of("testData", "private", "model-corpus").toString()))
            .resolve("24LW1a1p_package (1)/24LW1a1p_package/24LW11p.dyr");

    @Test void parsesOfficialTwentyNineParameterSchemaAndRealRecord(@TempDir Path dir)throws Exception{
        Path dyr=dir.resolve("st6c.dyr");
        Files.writeString(dyr,"1 'ST6C' 1 4 2 0 1 0 100 70 0 1 999 -999 0 1.2 1.882 25 1.45 5.25 -3.5 0 1 5.25 -3.5 0 1 0 0 0 0 99 /\n");
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(dyr.toString());
        St6cExciter e=(St6cExciter)b.getDStabNetwork().getMachine("Bus1-mach1").getExciter();assertNotNull(e);
        St6cData d=e.getData();assertEquals(4,d.getOel());assertEquals(2,d.getUel());assertEquals(0,d.getScl());assertEquals(1,d.getSw1());
        assertEquals(70,d.getKia(),TOL);assertEquals(999,d.getVamax(),TOL);assertEquals(-999,d.getVamin(),TOL);
        assertEquals(1.882,d.getKci(),TOL);assertEquals(25,d.getKlr(),TOL);assertEquals(1.45,d.getIlr(),TOL);
        assertEquals(5.25,d.getVrmax(),TOL);assertEquals(-3.5,d.getVrmin(),TOL);assertEquals(1,d.getTg(),TOL);
        assertEquals(5.25,d.getVmmax(),TOL);assertEquals(-3.5,d.getVmmin(),TOL);assertEquals(99,d.getVbmax(),TOL);
        assertTrue(p.getLastImportReport().isStrictlyComplete());
        var descriptor=DynamicModelCatalog.find("ST6C").orElseThrow();assertEquals(29,descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESST6C").orElseThrow().isImplementedExactly());
        Machine machine=b.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        assertTrue(e.initStates(machine.getDStabBus(),machine));double initial=e.getOutput(machine);
        for(int i=0;i<1000;i++)step(e,machine,.0001);assertEquals(initial,e.getOutput(machine),1e-9);
    }

    @Test void allThirtySuppliedNativeRecordsHaveExactSchema()throws Exception{
        assumeTrue(Files.isRegularFile(CORPUS),"Missing supplied ST6C corpus: "+CORPUS);
        Pattern pattern=Pattern.compile("(?ims)^\\s*\\d+\\s+'ST6C'\\s+[^/]+/");Matcher matcher=pattern.matcher(Files.readString(CORPUS));int count=0;
        while(matcher.find()){String record=matcher.group();assertEquals(32,
                PsseDyrRecordReader.tokenize(record.substring(0,record.lastIndexOf('/'))).size());count++;}
        assertEquals(30,count);
    }

    @Test void parsesExactSt6cu1WrapperAndRejectsWrongAllocation(@TempDir Path dir)throws Exception{
        String body=" 4 0 4 25 5 3 4 2 0 1 0 100 70 1 .05 999 -999 0 1.2 1.882 25 1.45 5.25 -3.5 .1 .1 5.25 -3.5 .02 1 0 0 0 0 99 /\n";
        Path valid=dir.resolve("valid.dyr");Files.writeString(valid,"1 'USRMDL' '1' 'ST6CU1'"+body);
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(valid.toString());
        St6cExciter exciter=(St6cExciter)builder.getDStabNetwork().getMachine("Bus1-mach1").getExciter();
        assertNotNull(exciter);assertEquals(4,exciter.getData().getOel());assertEquals(99,exciter.getData().getVbmax(),TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());

        Path invalid=dir.resolve("invalid.dyr");Files.writeString(invalid,"1 'USRMDL' '1' 'ST6CU1' 4 0 4 25 4 3"+body.substring(" 4 0 4 25 5 3".length()));
        DStabNetworkBuilder rejected=DStabBuilderTestFixture.createWithMachine();
        PSSEDStabDirectParser bad=new PSSEDStabDirectParser(rejected).setStrictImport(true);
        assertThrows(InterpssException.class,()->bad.parseDynFile(invalid.toString()));
        assertNull(rejected.getDStabNetwork().getMachine("Bus1-mach1").getExciter());
    }

    @Test void fiveStatesMatchIndependentModifiedEulerOracle()throws Exception{
        Fixture f=fixture(baseData());f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double[] x={1.04,1.344,0,.24,1.2};double dt=.0001,max=0;
        for(int n=0;n<2000;n++){
            double[] d0=derivatives(x,.1),p=add(x,d0,dt),d1=derivatives(p,.1);
            for(int i=0;i<5;i++)x[i]+=.5*(d0[i]+d1[i])*dt;step(f.exciter,f.machine,dt);
            double vi=1.04+.1-x[0],va=3*vi+x[1]+.5*(vi-x[2])/.1,vr=va-.6*x[3];
            max=Math.max(max,Math.abs(x[0]-f.exciter.getSensedVoltage()));max=Math.max(max,Math.abs(vi-f.exciter.getRegulatorInput()));
            max=Math.max(max,Math.abs(va-f.exciter.getVaOutput()));max=Math.max(max,Math.abs(vr-f.exciter.getVmInput()));
            max=Math.max(max,Math.abs(x[4]-f.exciter.getVmOutput()));max=Math.max(max,Math.abs(x[4]-f.exciter.getOutput(f.machine)));
        }
        assertTrue(max<1e-10,"ST6C five-state max error="+max);
    }

    @Test void routesAllFourLimiterLocations()throws Exception{
        St6cData d=baseData();d.setOel(1);d.setUel(1);d.setScl(1);Fixture a=fixture(d);
        a.exciter.setVoel(-.01);a.exciter.setVuel(.02);a.exciter.setVsclOel(-.03);a.exciter.setVsclUel(.04);
        assertEquals(.02,a.exciter.getRegulatorInput(),TOL);
        d=baseData();d.setOel(2);d.setUel(2);d.setScl(2);Fixture b=fixture(d);
        b.exciter.setVoel(-.1);b.exciter.setVuel(.2);b.exciter.setVsclOel(.15);b.exciter.setVsclUel(.25);
        assertEquals(-.1,b.exciter.getRegulatorInput(),TOL);
        d=baseData();d.setOel(3);d.setUel(3);d.setScl(3);Fixture c=fixture(d);
        c.exciter.setVoel(-.01);c.exciter.setVuel(.02);c.exciter.setVsclOel(-.03);c.exciter.setVsclUel(.04);
        assertEquals(.02,c.exciter.getRegulatorInput(),TOL);
        d=baseData();d.setOel(4);d.setUel(4);d.setScl(4);Fixture out=fixture(d);
        out.exciter.setVuel(2);out.exciter.setVsclUel(2.5);out.exciter.setVoel(2.4);out.exciter.setVsclOel(2.3);
        assertEquals(2.3,out.exciter.getVmInput(),TOL);
    }

    @Test void appliesBridgeSourceCurrentLimiterCorrectionsAndLimitExpansion()throws Exception{
        St6cData d=baseData();d.setTr(.004);d.setTg(.015);d.setTa(.015);d.setKpa(0);d.setKia(0);
        d.setVamax(-2);d.setVamin(-3);d.setVrmax(-2);d.setVrmin(-3);d.setVmmax(-2);d.setVmmin(-3);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St6cExciter e=b.addExcSt6c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);e.configureIntegrationStep(.01,2);
        assertTrue(e.initStates(m.getDStabBus(),m));assertEquals(0,e.tr,TOL);assertEquals(.02,e.tg,TOL);assertEquals(.02,e.ta,TOL);assertEquals(40,e.kpa,TOL);
        assertTrue(e.vamax>=e.getVaOutput()&&e.vamin<=e.getVaOutput());assertTrue(e.vrmax>=e.getVmInput()&&e.vrmin<=e.getVmInput());
        assertTrue(e.vmmax>=e.getVmOutput()&&e.vmmin<=e.getVmOutput());
        assertEquals(1,e.getAvailableBridge(),TOL);
        Complex vt=m.getDStabBus().getVoltage(),it=m.getIxy().divide(m.getIMultiFactor());
        assertEquals(vt.add(Complex.I.multiply(it).multiply(e.xl)).abs(),e.getPotentialSource(),TOL);
        d=baseData();d.setKci(0);d.setIlr(0);d.setKlr(1);Fixture limited=fixture(d);assertTrue(limited.exciter.getCurrentLimitOutput()<=0);
    }

    @Test void holdsEquilibriumAndParticipatesInFullSimulation()throws Exception{
        Fixture f=fixture(baseData());double initial=f.exciter.getOutput(f.machine);for(int i=0;i<1000;i++)step(f.exciter,f.machine,.0001);
        assertEquals(initial,f.exciter.getOutput(f.machine),1e-9);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();assertNotNull(b.addExcSt6c("Bus1","1",baseData()));
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);a.setSimuOutputHandler(new StateMonitor());assertTrue(a.getAclfAlgorithm().loadflow());
        assertTrue(a.initialization());assertTrue(a.performSimulation());
    }

    private static Fixture fixture(St6cData d)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St6cExciter e=b.addExcSt6c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertNotNull(e);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e);
    }
    private static St6cData baseData(){St6cData d=new St6cData();d.setOel(1);d.setUel(1);d.setScl(0);d.setSw1(2);
        d.setTr(.1);d.setKpa(3);d.setKia(4);d.setKda(.5);d.setTda(.1);d.setVamax(99);d.setVamin(-99);
        d.setKff(.4);d.setKm(.6);d.setKci(1);d.setKlr(1);d.setIlr(100);d.setVrmax(99);d.setVrmin(-99);
        d.setKg(.2);d.setTg(.3);d.setVmmax(99);d.setVmmin(-99);d.setTa(.2);d.setKp(1);d.setKi(0);d.setXl(0);d.setThetaP(0);d.setKc(0);d.setVbmax(99);return d;}
    private static double[] derivatives(double[] x,double step){double vi=1.04+step-x[0],va=3*vi+x[1]+.5*(vi-x[2])/.1,vr=va-.6*x[3];
        return new double[]{(1.04-x[0])/.1,4*vi,(vi-x[2])/.1,(.2*x[4]-x[3])/.3,(vr-x[4])/.2};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(St6cExciter e,Machine m,double dt){e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0);e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1);}
    private record Fixture(Machine machine,St6cExciter exciter){}
}
