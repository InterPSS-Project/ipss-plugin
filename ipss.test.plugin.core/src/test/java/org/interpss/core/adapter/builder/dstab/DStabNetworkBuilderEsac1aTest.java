package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.esac1a.Esac1aData;
import org.interpss.dstab.control.exc.psse.esac1a.Esac1aExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderEsac1aTest extends CorePluginTestSetup {
    private static final double TOL=1e-8;
    @TempDir Path tempDir;

    @Test
    void parsesPowerWorldPsseOrderInitializesAndResponds() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("esac1a.dyr");
        Files.writeString(dyr,"1 'ESAC1A' '1' 0 0 0 280.463 .02 16.5957 -16.595699 1.0339 .0162 1 .2 1 1 1.5823 .0919 2.1097 .827 9 -9 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());

        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0); machine.setEfd(1.2);
        Esac1aExciter exc=(Esac1aExciter)machine.getExciter();
        assertNotNull(exc);
        assertEquals(280.463,exc.getData().getKa(),TOL);
        assertEquals(16.5957,exc.getData().getVamax(),TOL);
        assertEquals(.2,exc.getData().getKc(),TOL);
        assertEquals(9.0,exc.getData().getVrmax(),TOL);
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        double initial=exc.getOutput(machine);
        assertEquals(1.2,initial,1e-6);

        machine.getDStabBus().setVoltage(new Complex(.99,0));
        for(int i=0;i<40;i++) {
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,0));
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        }
        assertTrue(exc.getOutput(machine)>initial);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void normalizesAndExpandsBothControlLimitPairs() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(2.0);
        Esac1aData data=new Esac1aData();
        data.setKa(100); data.setTa(.02); data.setTe(1); data.setTf(1);
        data.setKe(1); data.setVamax(-1); data.setVamin(1); data.setVrmax(-1); data.setVrmin(1);
        Esac1aExciter exc=builder.addExcEsac1a("Bus1","1",data);

        assertNotNull(exc);
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        assertTrue(exc.vamax>=2.0);
        assertEquals(-1.0,exc.vamin,TOL);
        assertTrue(exc.vrmax>=2.0);
        assertEquals(-1.0,exc.vrmin,TOL);
    }

    @Test
    void catalogAndParserRejectPowerWorldOnlySpdmltField() throws Exception {
        var descriptor=DynamicModelCatalog.find("ESAC1A").orElseThrow();
        assertEquals(19,descriptor.parameterCount());assertTrue(descriptor.recordSchema().accepts(19));
        assertFalse(descriptor.recordSchema().accepts(20));
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESAC1A").orElseThrow().isImplementedExactly());
        Path dyr=tempDir.resolve("powerworld-extension.dyr");
        Files.writeString(dyr,"1 'ESAC1A' 1 0 0 0 280 .02 16 -16 1 .02 1 .2 1 1 2 .1 3 .2 9 -9 1 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(DStabBuilderTestFixture.createWithMachine())
                .setStrictImport(true);
        assertThrows(Exception.class,()->parser.parseDynFile(dyr.toString()));
    }

    @Test
    void fiveCmlStatesMatchIndependentBlockwiseModifiedEulerOracle() throws Exception {
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
        assertTrue(maxError<1e-12,"ESAC1A blockwise modified-Euler max error="+maxError);
    }

    @Test
    void appliesPublishedCorrectionsAndLimiterGates() throws Exception {
        Esac1aData data=baseData();data.setTr(.006);data.setTb(.009);data.setTa(.015);
        data.setTe(.005);data.setTf(.006);data.setKa(0);data.setVamax(-2);data.setVamin(-3);
        data.setVrmax(-2);data.setVrmin(-3);
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Esac1aExciter exciter=builder.addExcEsac1a("Bus1","1",data);exciter.configureIntegrationStep(.01,2);
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        assertEquals(.01,exciter.tr,TOL);assertEquals(0,exciter.tb,TOL);assertEquals(.02,exciter.ta,TOL);
        assertEquals(.02,exciter.ka,TOL);assertEquals(50,exciter.integratorGain,TOL);assertEquals(.02,exciter.tf,TOL);
        assertTrue(exciter.vamax>=exciter.getRegulatorOutput());assertTrue(exciter.vrmax>=exciter.getGatedRegulatorOutput());
        exciter.setVuel(exciter.getRegulatorOutput()+1);assertEquals(exciter.getRegulatorOutput()+1,
                exciter.getGatedRegulatorOutput(),TOL);
        exciter.setVoel(exciter.getRegulatorOutput()+.5);assertEquals(exciter.getRegulatorOutput()+.5,
                exciter.getGatedRegulatorOutput(),TOL);
    }

    @Test
    void participatesInFullSimulation() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        assertNotNull(builder.addExcEsac1a("Bus1","1",baseData()));
        DynamicSimuAlgorithm algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(.005);
        algorithm.setTotalSimuTimeSec(.02);algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
    }

    private static Fixture fixture(Esac1aData data) throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Esac1aExciter exciter=builder.addExcEsac1a("Bus1","1",data);exciter.configureIntegrationStep(.001);
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));return new Fixture(machine,exciter);
    }

    private static Esac1aData baseData(){
        Esac1aData data=new Esac1aData();data.setTr(.1);data.setTb(.1);data.setTc(.02);
        data.setKa(2);data.setTa(.15);data.setVamax(99);data.setVamin(-99);
        data.setVrmax(99);data.setVrmin(-99);data.setTe(.2);data.setKf(.5);data.setTf(.3);
        data.setKc(0);data.setKd(0);data.setKe(1);data.setE1(0);data.setSe1(0);data.setE2(0);data.setSe2(0);
        return data;
    }

    private static double[] blockDerivatives(double[] x,double[] u){
        return new double[]{(u[0]-x[0])/.1,(u[1]-x[1])/.1,(2*u[2]-x[2])/.15,
                5*u[3],(u[4]-x[4])/.3};
    }

    private record Fixture(Machine machine,Esac1aExciter exciter){}
}
