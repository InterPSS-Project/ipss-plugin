package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.esac8b.Esac8bData;
import org.interpss.dstab.control.exc.psse.esac8b.Esac8bExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Native 15-CON PSS/E ESAC8B coverage on the shared AC8B PID engine. */
public class DStabNetworkBuilderEsac8bTest extends CorePluginTestSetup {
    private static final double TOL=1e-10;
    @TempDir Path tempDir;

    @Test void parsesExactRecordAndMapsOnlyTheApprovedCrossCatalogRow() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("esac8b.dyr");
        Files.writeString(dyr,"1 'ESAC8B' '1' .02 15 .8 10 .03 .6 .04 5 -4 .4 1 5.7 .1 7.6 .4 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Esac8bExciter exciter=(Esac8bExciter)builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getExciter();
        assertNotNull(exciter);assertEquals("ESAC8B",exciter.getName());
        assertEquals(15,exciter.getData().getKpr(),TOL);assertEquals(.6,exciter.getData().getKa(),TOL);
        assertEquals(.4,exciter.getData().getTe(),TOL);assertEquals(7.6,exciter.getData().getE2(),TOL);
        assertEquals(15,DynamicModelCatalog.find("ESAC8B").orElseThrow().parameterCount());
        assertEquals("ESAC8B",WeccApprovedDynamicModelCatalog.findExciter("EXAC8B")
                .orElseThrow().interpssModel());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());

        Path extension=tempDir.resolve("esac8b-extra.dyr");
        Files.writeString(extension,"1 'ESAC8B' '1' .02 15 .8 10 .03 .6 .04 5 -4 .4 1 5.7 .1 7.6 .4 1 /\n");
        assertThrows(Exception.class,()->new PSSEDStabDirectParser(
                DStabBuilderTestFixture.createWithMachine()).setStrictImport(true)
                .parseDynFile(extension.toString()));
        assertFalse(DynamicModelCatalog.find("EXAC8B").isPresent(),
                "GE EXAC8B must not become a native PSS/E alias");
    }

    @Test void fiveStatesMatchThePublishedBaslerDecsEquations() throws Exception {
        Fixture fixture=fixture(baseData());fixture.exciter.setVuel(.1);
        double[] x=fixture.exciter.getStateSnapshot();double dt=.00025,maxError=0;
        for(int n=0;n<1000;n++){
            double[] d0=derivatives(x,.1);double[] predicted=add(x,d0,dt);
            double[] d1=derivatives(predicted,.1);
            for(int i=0;i<x.length;i++)x[i]+=.5*(d0[i]+d1[i])*dt;
            step(fixture.exciter,fixture.machine,dt);
            double[] actual=fixture.exciter.getStateSnapshot();
            for(int i=0;i<x.length;i++)maxError=Math.max(maxError,Math.abs(x[i]-actual[i]));
        }
        assertTrue(maxError<1e-12,"ESAC8B five-state max error="+maxError);
    }

    @Test void appliesPtiCorrectionsAndExpandsOnlyTheRegulatorLimits() throws Exception {
        Esac8bData data=baseData();data.setTr(.006);data.setTdr(.011);data.setTa(.011);
        data.setTe(.006);data.setKa(0);data.setKpr(0);data.setKir(0);
        data.setVrmax(-2);data.setVrmin(-3);
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Esac8bExciter exciter=builder.addExcEsac8b("Bus1","1",data);exciter.configureIntegrationStep(.01,2);
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        assertEquals(0,exciter.tr,TOL);assertEquals(.02,exciter.tdr,TOL);
        assertEquals(.02,exciter.ta,TOL);assertEquals(.02,exciter.te,TOL);
        assertEquals(.02,exciter.ka,TOL);assertEquals(40,exciter.kpr,TOL);
        assertTrue(exciter.vrmax>=exciter.getRegulatorOutput());assertEquals(-3,exciter.vrmin,TOL);

        Esac8bData boundary=baseData();boundary.setTr(.01);boundary.setTdr(.01);boundary.setTa(.01);
        Fixture boundaryFixture=fixtureWithoutInitialization(boundary);
        boundaryFixture.exciter.configureIntegrationStep(.01,2);
        assertTrue(boundaryFixture.exciter.initStates(boundaryFixture.machine.getDStabBus(),boundaryFixture.machine));
        assertEquals(.02,boundaryFixture.exciter.tr,TOL);assertEquals(.02,boundaryFixture.exciter.tdr,TOL);
        assertEquals(.02,boundaryFixture.exciter.ta,TOL);
    }

    @Test void rejectsZeroFieldTimeAndParticipatesInTheFullSolver() throws Exception {
        Esac8bData invalid=baseData();invalid.setTe(0);
        DStabNetworkBuilder badBuilder=DStabBuilderTestFixture.createWithMachine();
        Esac8bExciter bad=badBuilder.addExcEsac8b("Bus1","1",invalid);
        assertFalse(bad.initStates(bad.getMachine().getDStabBus(),bad.getMachine()));

        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        assertNotNull(builder.addExcEsac8b("Bus1","1",baseData()));
        DynamicSimuAlgorithm algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(.005);
        algorithm.setTotalSimuTimeSec(.02);algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
    }

    private static Esac8bData baseData(){
        Esac8bData d=new Esac8bData();d.setTr(.02);d.setKpr(2);d.setKir(3);d.setKdr(.4);
        d.setTdr(.03);d.setKa(1.5);d.setTa(.3);d.setVrmax(99);d.setVrmin(-99);
        d.setTe(.4);d.setKe(1);d.setE1(0);d.setSe1(0);d.setE2(0);d.setSe2(0);return d;
    }
    private static Fixture fixture(Esac8bData data)throws Exception{
        Fixture fixture=fixtureWithoutInitialization(data);
        assertTrue(fixture.exciter.initStates(fixture.machine.getDStabBus(),fixture.machine));return fixture;
    }
    private static Fixture fixtureWithoutInitialization(Esac8bData data)throws Exception{
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Esac8bExciter exciter=builder.addExcEsac8b("Bus1","1",data);
        return new Fixture(exciter,machine);
    }
    private static double[] derivatives(double[] x,double error){
        double derivative=.4*(error-x[3])/.03;
        return new double[]{(x[4]-x[0])/.4,0,3*error,(error-x[3])/.03,
                (1.5*(2*error+x[2]+derivative)-x[4])/.3};
    }
    private static double[] add(double[] x,double[] d,double dt){double[] y=x.clone();for(int i=0;i<y.length;i++)y[i]+=d[i]*dt;return y;}
    private static void step(Esac8bExciter e,Machine m,double dt){
        assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));
        assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));
    }
    private record Fixture(Esac8bExciter exciter,Machine machine){}
}
