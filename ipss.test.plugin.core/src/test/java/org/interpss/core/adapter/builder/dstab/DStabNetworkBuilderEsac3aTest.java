package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.esac3a.Esac3aData;
import org.interpss.dstab.control.exc.psse.esac3a.Esac3aExciter;
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

public class DStabNetworkBuilderEsac3aTest extends CorePluginTestSetup {
    private static final double TOL=1e-8;
    @TempDir Path tempDir;

    @Test void parsesExactNativeRecordInitializesAndResponds() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("esac3a.dyr");
        Files.writeString(dyr,"1 'ESAC3A' '1' .1 .2 .05 20 .1 10 -10 .4 0 1 .1 .7 .2 2 0 0 1 100 0 0 0 0 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Esac3aExciter exciter=(Esac3aExciter)machine.getExciter();assertNotNull(exciter);
        assertEquals(20,exciter.getData().getKa(),TOL);assertEquals(1,exciter.getData().getKr(),TOL);
        assertEquals(.2,exciter.getData().getKn(),TOL);assertEquals(100,exciter.getData().getVfemax(),TOL);
        assertEquals(0,exciter.getData().getSpdmlt(),TOL);
        exciter.configureIntegrationStep(.001);assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        double initial=exciter.getOutput(machine);assertEquals(1.2,initial,1e-6);
        machine.getDStabBus().setVoltage(new Complex(.99,0));
        for(int i=0;i<100;i++)step(exciter,machine,.001);
        assertTrue(Double.isFinite(exciter.getOutput(machine)));assertNotEquals(initial,exciter.getOutput(machine),1e-5);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test void catalogMapsBothApprovedRowsToOneNativePsseModelWithoutInventedAlias() throws Exception {
        var descriptor=DynamicModelCatalog.find("ESAC3A").orElseThrow();
        assertEquals(22,descriptor.parameterCount());assertTrue(descriptor.recordSchema().accepts(22));
        assertFalse(descriptor.recordSchema().accepts(23));
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(DynamicModelCatalog.find("EXAC3A").isEmpty());
        var legacy=WeccApprovedDynamicModelCatalog.findExciter("EXAC3A").orElseThrow();
        var ieee=WeccApprovedDynamicModelCatalog.findExciter("ESAC3A").orElseThrow();
        assertEquals("ESAC3A",legacy.psseModel());assertEquals("ESAC3A",ieee.psseModel());
        assertTrue(legacy.isImplementedExactly());assertTrue(ieee.isImplementedExactly());

        Path alias=tempDir.resolve("invalid-alias.dyr");
        Files.writeString(alias,"1 'EXAC3A' 1 .1 .2 .05 20 .1 10 -10 .4 0 1 .1 .7 .2 2 0 0 1 100 0 0 0 0 /\n");
        assertThrows(Exception.class,()->new PSSEDStabDirectParser(DStabBuilderTestFixture.createWithMachine())
                .setStrictImport(true).parseDynFile(alias.toString()));
        Path extension=tempDir.resolve("invalid-extension.dyr");
        Files.writeString(extension,"1 'ESAC3A' 1 .1 .2 .05 20 .1 10 -10 .4 0 1 .1 .7 .2 2 0 0 1 100 0 0 0 0 1 /\n");
        assertThrows(Exception.class,()->new PSSEDStabDirectParser(DStabBuilderTestFixture.createWithMachine())
                .setStrictImport(true).parseDynFile(extension.toString()));
    }

    @Test void fiveStatesMatchIndependentModifiedEulerEquationOracle() throws Exception {
        Fixture fixture=fixture(baseData());double[] oracle=fixture.exciter.getStateSnapshot();
        double dt=.0001,maxError=0;fixture.exciter.setRefPoint(fixture.exciter.getRefPoint()+.1);
        double reference=fixture.exciter.getRefPoint(),vt=fixture.machine.getDStabBus().getVoltageMag();
        for(int n=0;n<2000;n++){
            double[] d0=derivatives(oracle,reference,vt);
            double[] predicted=advance(oracle,d0,dt);
            assertTrue(fixture.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,fixture.machine,0));
            double[] actualPredicted=fixture.exciter.getStateSnapshot();
            for(int i=0;i<oracle.length;i++)maxError=Math.max(maxError,Math.abs(actualPredicted[i]-predicted[i]));
            double[] d1=derivatives(predicted,reference,vt);
            for(int i=0;i<oracle.length;i++)oracle[i]+=.5*(d0[i]+d1[i])*dt;
            assertTrue(fixture.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,fixture.machine,1));
            double[] actual=fixture.exciter.getStateSnapshot();
            for(int i=0;i<oracle.length;i++)maxError=Math.max(maxError,Math.abs(actual[i]-oracle[i]));
        }
        assertTrue(maxError<1e-12,"ESAC3A five-state max error="+maxError);
    }

    @Test void usesExciterOutputForNonlinearFeedbackAndRoutesPublishedLimiters() throws Exception {
        Esac3aData data=baseData();data.setKd(.4);data.setKf(.2);data.setKn(.8);data.setEfdn(1.0);
        Fixture fixture=fixture(data);double efd=fixture.exciter.getOutput(fixture.machine);
        double expected=data.getKf()*data.getEfdn()+data.getKn()*(efd-data.getEfdn());
        assertEquals(expected,fixture.exciter.getNonlinearFeedback(),TOL,
                "nonlinear feedback characteristic is driven by EFD, not VFE");
        assertNotEquals(expected,fixture.exciter.getFieldFeedback()*data.getKn(),1e-4);
        double baseInput=fixture.exciter.getRegulatorInput();
        fixture.exciter.setVoel(.2);assertEquals(baseInput+.05,fixture.exciter.getRegulatorInput(),TOL,
                "OEL enters before the Tc/Tb lead-lag");
        fixture.exciter.setVuel(fixture.exciter.getRegulatorInput()+.3);
        assertEquals(baseInput+.35,fixture.exciter.getRegulatorInput(),TOL,
                "VUEL is a high-value gate after the OEL summation and rate feedback");
    }

    @Test void appliesDynamicFieldBoundsAndPublishedCorrections() throws Exception {
        Esac3aData data=baseData();data.setTr(.004);data.setTb(.009);data.setTa(.011);
        data.setTe(.006);data.setTf(.007);data.setKa(0);data.setKr(0);
        data.setVamax(-2);data.setVamin(-3);data.setVemin(2);data.setVfemax(2);
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Esac3aExciter exciter=builder.addExcEsac3a("Bus1","1",data);assertNotNull(exciter);
        exciter.configureIntegrationStep(.01,2);assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        assertEquals(0,exciter.tr,TOL);assertEquals(0,exciter.tb,TOL);assertEquals(.02,exciter.ta,TOL);
        assertEquals(.02,exciter.ka,TOL);assertEquals(.02,exciter.kr,TOL);
        assertEquals(.02,exciter.te,TOL);assertEquals(.02,exciter.tf,TOL);
        assertEquals(1.2,exciter.vemin,1e-6);assertTrue(exciter.vamax>=exciter.getRegulatorOutput());
        assertTrue(exciter.vamin<=exciter.getRegulatorOutput());
        assertTrue(exciter.getInternalFieldVoltage()<=exciter.getDynamicUpperLimit()+TOL);

        Esac3aData boundary=baseData();boundary.setTr(.005);boundary.setTb(.01);boundary.setTa(.01);
        Esac3aExciter boundaryExciter=new Esac3aExciter("boundary",boundary,machine);
        boundaryExciter.configureIntegrationStep(.01,2);
        assertTrue(boundaryExciter.initStates(machine.getDStabBus(),machine));
        assertEquals(.01,boundaryExciter.tr,TOL);assertEquals(.02,boundaryExciter.tb,TOL);
        assertEquals(.02,boundaryExciter.ta,TOL);
    }

    @Test void fieldIntegratorDoesNotWindUpAtMovingUpperBound() throws Exception {
        Esac3aData data=baseData();data.setVfemax(1.25);data.setTa(0);data.setTb(0);data.setTc(0);Fixture fixture=fixture(data);
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint()+10);
        for(int i=0;i<5000;i++)step(fixture.exciter,fixture.machine,.0001);
        assertTrue(fixture.exciter.getInternalFieldVoltage()<=fixture.exciter.getDynamicUpperLimit()+TOL);
        double atLimit=fixture.exciter.getInternalFieldVoltage();
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint()-20);
        for(int i=0;i<20;i++)step(fixture.exciter,fixture.machine,.0001);
        assertTrue(fixture.exciter.getInternalFieldVoltage()<atLimit,
                "field state must leave the upper limit immediately after the command reverses");
    }

    @Test void participatesInFullSimulation() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        assertNotNull(builder.addExcEsac3a("Bus1","1",baseData()));
        DynamicSimuAlgorithm algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(.005);
        algorithm.setTotalSimuTimeSec(.02);algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
    }

    private Fixture fixture(Esac3aData data) throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Esac3aExciter exciter=builder.addExcEsac3a("Bus1","1",data);assertNotNull(exciter);
        exciter.configureIntegrationStep(.001);assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        return new Fixture(machine,exciter);
    }
    private static Esac3aData baseData(){
        Esac3aData data=new Esac3aData();data.setTr(.1);data.setTb(.2);data.setTc(.05);
        data.setKa(2);data.setTa(.15);data.setVamax(99);data.setVamin(-99);data.setTe(.2);
        data.setVemin(0);data.setKr(1);data.setKf(.5);data.setTf(.3);data.setKn(.8);
        data.setEfdn(1.3);data.setKc(0);data.setKd(0);data.setKe(1);data.setVfemax(100);
        data.setE1(0);data.setSe1(0);data.setE2(0);data.setSe2(0);return data;
    }
    private static double[] derivatives(double[] x,double reference,double vt){
        double efd=x[0],vn=efd<=1.3?.5*efd:.5*1.3+.8*(efd-1.3);
        double feedback=(vn-x[4])/.3,error=reference-x[1];
        double leadLag=.25*error+.75*x[3],regulatorInput=leadLag-feedback;
        return new double[]{(x[2]*efd-x[0])/.2,(vt-x[1])/.1,
                (2*regulatorInput-x[2])/.15,(error-x[3])/.2,(vn-x[4])/.3};
    }
    private static double[] advance(double[] x,double[] derivative,double dt){
        double[] value=x.clone();for(int i=0;i<x.length;i++)value[i]+=derivative[i]*dt;return value;
    }
    private static void step(Esac3aExciter exciter,Machine machine,double dt){
        assertTrue(exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        assertTrue(exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,1));
    }
    private record Fixture(Machine machine,Esac3aExciter exciter){}
}
