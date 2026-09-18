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
import org.interpss.dstab.control.exc.psse.esac2a.Esac2aData;
import org.interpss.dstab.control.exc.psse.esac2a.Esac2aExciter;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.util.DynamicLimitIntegrationBlock;
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
import com.interpss.dstab.mach.MachineIfdBase;

public class DStabNetworkBuilderEsac2aTest extends CorePluginTestSetup {
    private static final double TOL=1e-8;
    @TempDir Path tempDir;

    @Test void parsesNativeRecordInitializesAndResponds() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("esac2a.dyr");
        Files.writeString(dyr,"1 'ESAC2A' '1' .02 1 1 100 .05 10 -10 1 20 -20 1 5 .1 .1 1 0 0 1 1 0 2 0 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1);machine.setEfd(1.2);Esac2aExciter exc=(Esac2aExciter)machine.getExciter();
        assertNotNull(exc);assertEquals(100,exc.getData().getKa(),TOL);
        assertEquals(1,exc.getData().getKb(),TOL);assertEquals(5,exc.getData().getVfemax(),TOL);
        assertEquals(.1,exc.getData().getKh(),TOL);assertEquals(0,exc.getData().getSpdmlt(),TOL);
        exc.configureIntegrationStep(.005);assertTrue(exc.initStates(machine.getDStabBus(),machine));
        double initial=exc.getOutput(machine);assertEquals(1.2,initial,1e-6);
        machine.getDStabBus().setVoltage(new Complex(.99,0));
        for(int i=0;i<40;i++)step(exc,machine,.005);
        assertTrue(Double.isFinite(exc.getOutput(machine)));assertNotEquals(initial,exc.getOutput(machine),1e-4);
        assertTrue(exc.fieldIntegrator.getY()<=exc.fieldIntegrator.getUpperLimit()+TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test void catalogAndParserEnforceNativePsseSchema() throws Exception {
        var descriptor=DynamicModelCatalog.find("ESAC2A").orElseThrow();
        assertEquals(22,descriptor.parameterCount());assertTrue(descriptor.recordSchema().accepts(22));
        assertFalse(descriptor.recordSchema().accepts(23));
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESAC2A").orElseThrow().isImplementedExactly());
        Path dyr=tempDir.resolve("powerworld-extension.dyr");
        Files.writeString(dyr,"1 'ESAC2A' 1 .1 .4 .1 20 .1 10 -10 1 10 -10 .4 100 .5 .1 .7 0 0 1 0 0 0 0 1 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(DStabBuilderTestFixture.createWithMachine())
                .setStrictImport(true);
        assertThrows(Exception.class,()->parser.parseDynFile(dyr.toString()));
    }

    @Test void fiveCmlStatesMatchIndependentBlockwiseModifiedEulerOracle() throws Exception {
        Fixture fixture=fixture(baseData());double dt=.0001,maxError=0;
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint()+.1);
        for(int n=0;n<2000;n++){
            double[] old=fixture.exciter.getStateSnapshot();
            assertTrue(fixture.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,fixture.machine,0));
            double[] predicted=fixture.exciter.getStateSnapshot(),firstInputs=fixture.exciter.getStateInputSnapshot();
            double[] d0=blockDerivatives(old,firstInputs);
            for(int i=0;i<old.length;i++)maxError=Math.max(maxError,Math.abs(predicted[i]-old[i]-d0[i]*dt));
            assertTrue(fixture.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,fixture.machine,1));
            double[] corrected=fixture.exciter.getStateSnapshot(),secondInputs=fixture.exciter.getStateInputSnapshot();
            double[] d1=blockDerivatives(predicted,secondInputs);
            for(int i=0;i<old.length;i++){
                double expected=predicted[i]+.5*(d1[i]-d0[i])*dt;
                maxError=Math.max(maxError,Math.abs(corrected[i]-expected));
            }
        }
        assertTrue(maxError<1e-12,"ESAC2A blockwise modified-Euler max error="+maxError);
    }

    @Test void publishedLimiterGatesAndDynamicFieldLimitAreApplied() throws Exception {
        Esac2aData data=baseData();data.setKh(.5);data.setKb(2);Fixture fixture=fixture(data);
        double vfe=fixture.exciter.getFieldVaule("this.vfe.y");
        assertEquals(data.getKh()*vfe+vfe/data.getKb(),fixture.exciter.va0,TOL);
        assertEquals(vfe,fixture.exciter.getFieldCurrentControllerOutput(),TOL);
        fixture.exciter.setVuel(vfe+.2);fixture.exciter.setVoel(vfe+.1);
        assertEquals(vfe+.1,fixture.exciter.getLimiterGateOutput(),TOL,
                "UEL high-value gate precedes OEL low-value gate");
        fixture.exciter.clearVuel();fixture.exciter.clearVoel();
        step(fixture.exciter,fixture.machine,.001);
        double ifd=fixture.machine.calculateIfd(MachineIfdBase.EXCITER);
        if(!Double.isFinite(ifd))ifd=0;
        double ve=fixture.exciter.fieldIntegrator.getY();
        double denominator=fixture.exciter.ke+Exac1Exciter.saturation(ve,
                fixture.exciter.e1,fixture.exciter.se1,fixture.exciter.e2,fixture.exciter.se2);
        assertEquals((fixture.exciter.vfemaxEffective-fixture.exciter.kd*ifd)/denominator,
                fixture.exciter.fieldIntegrator.getUpperLimit(),1e-7);
        assertEquals(0,fixture.exciter.fieldIntegrator.getLowerLimit(),TOL);
    }

    @Test void appliesPublishedTimeGainAndLimitCorrections() throws Exception {
        Esac2aData data=baseData();data.setTr(.004);data.setTb(.009);data.setTa(.011);
        data.setTe(.006);data.setTf(.007);data.setKa(0);data.setKb(0);
        data.setVamax(-2);data.setVamin(-3);data.setVrmax(-2);data.setVrmin(-3);
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Esac2aExciter exciter=builder.addExcEsac2a("Bus1","1",data);assertNotNull(exciter);
        exciter.configureIntegrationStep(.01,2);assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        assertEquals(0,exciter.tr,TOL);assertEquals(0,exciter.tb,TOL);assertEquals(.02,exciter.ta,TOL);
        assertEquals(.02,exciter.ka,TOL);assertEquals(.02,exciter.kb,TOL);
        assertEquals(50,exciter.integratorGain,TOL);assertEquals(.02,exciter.tf,TOL);
        assertTrue(exciter.vamax>=exciter.getRegulatorOutput());assertTrue(exciter.vamin<=exciter.getRegulatorOutput());
        assertTrue(exciter.vrmax>=exciter.getGatedRegulatorOutput());assertTrue(exciter.vrmin<=exciter.getGatedRegulatorOutput());
    }

    @Test void participatesInFullSimulation() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        assertNotNull(builder.addExcEsac2a("Bus1","1",baseData()));
        DynamicSimuAlgorithm algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(.005);
        algorithm.setTotalSimuTimeSec(.02);algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
    }

    @Test void dynamicIntegratorEnforcesBothBoundsWithoutWindup() {
        DynamicLimitIntegrationBlock block=new DynamicLimitIntegrationBlock(2,1,0);
        assertTrue(block.initStateY0(.9));block.eulerStep1(10,.1);block.eulerStep2(10,.1);
        assertEquals(1,block.getY(),TOL);block.setLimits(.8,.2);assertEquals(.8,block.getY(),TOL);
        block.eulerStep1(-10,.1);block.eulerStep2(-10,.1);assertEquals(.2,block.getY(),TOL);
    }

    private Fixture fixture(Esac2aData data) throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Esac2aExciter exciter=builder.addExcEsac2a("Bus1","1",data);exciter.configureIntegrationStep(.001);
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));return new Fixture(machine,exciter);
    }
    private static Esac2aData baseData(){
        Esac2aData data=new Esac2aData();data.setTr(.1);data.setTb(.1);data.setTc(.02);
        data.setKa(2);data.setTa(.15);data.setVamax(99);data.setVamin(-99);data.setKb(1);
        data.setVrmax(99);data.setVrmin(-99);data.setTe(.2);data.setVfemax(100);data.setKh(.5);
        data.setKf(.5);data.setTf(.3);data.setKc(0);data.setKd(0);data.setKe(1);
        data.setE1(0);data.setSe1(0);data.setE2(0);data.setSe2(0);return data;
    }
    private static double[] blockDerivatives(double[] x,double[] u){
        return new double[]{(u[0]-x[0])/.1,(u[1]-x[1])/.1,(2*u[2]-x[2])/.15,
                5*u[3],(u[4]-x[4])/.3};
    }
    private static void step(Esac2aExciter exciter,Machine machine,double dt){
        assertTrue(exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        assertTrue(exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,1));
    }
    private record Fixture(Machine machine,Esac2aExciter exciter){}
}
