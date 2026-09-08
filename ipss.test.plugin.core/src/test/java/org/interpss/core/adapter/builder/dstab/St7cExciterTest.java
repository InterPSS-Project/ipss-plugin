package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.st7c.St7cData;
import org.interpss.dstab.control.exc.psse.st7c.St7cExciter;
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

/** Official-schema, equation, limiter, correction and solver tests for PSS/E ST7C. */
public class St7cExciterTest extends CorePluginTestSetup {
    private static final double TOL=1e-9;

    @Test void parsesExactSeventeenParameterSchema(@TempDir Path dir)throws Exception{
        Path dyr=dir.resolve("st7c.dyr");Files.writeString(dyr,
                "1 'ST7C' 1 2 3 .1 .05 .2 5 -5 2 10 -10 .1 .2 .03 .3 .4 .5 .25 /\n");
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);parser.parseDynFile(dyr.toString());
        St7cExciter exciter=(St7cExciter)builder.getDStabNetwork().getMachine("Bus1-mach1").getExciter();assertNotNull(exciter);
        St7cData data=exciter.getData();assertEquals(2,data.getOel());assertEquals(3,data.getUel());assertEquals(.1,data.getTr(),TOL);
        assertEquals(.05,data.getTg(),TOL);assertEquals(.2,data.getTf(),TOL);assertEquals(5,data.getVmax(),TOL);
        assertEquals(-5,data.getVmin(),TOL);assertEquals(2,data.getKpa(),TOL);assertEquals(10,data.getVrmax(),TOL);
        assertEquals(-10,data.getVrmin(),TOL);assertEquals(.1,data.getKh(),TOL);assertEquals(.2,data.getKl(),TOL);
        assertEquals(.03,data.getTc(),TOL);assertEquals(.3,data.getTb(),TOL);assertEquals(.4,data.getKia(),TOL);
        assertEquals(.5,data.getTia(),TOL);assertEquals(.25,data.getTa(),TOL);assertTrue(parser.getLastImportReport().isStrictlyComplete());
        var descriptor=DynamicModelCatalog.find("ST7C").orElseThrow();assertEquals(17,descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());assertTrue(DynamicModelCatalog.find("ESST7C").isEmpty());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESST7C").orElseThrow().isImplementedExactly());
    }

    @Test void fiveStateModifiedEulerTrajectoryMatchesDynawoEquations()throws Exception{
        Fixture fixture=fixture(baseData());fixture.exciter.setRefPoint(fixture.exciter.getRefPoint()+.1);
        double[] expected=fixture.exciter.getStateSnapshot();double dt=.00005,maxError=0;
        for(int i=0;i<2000;i++){
            double[] first=derivatives(expected,fixture.exciter.getRefPoint());double[] predicted=add(expected,first,dt);
            double[] second=derivatives(predicted,fixture.exciter.getRefPoint());
            for(int j=0;j<expected.length;j++)expected[j]+=.5*(first[j]+second[j])*dt;
            step(fixture.exciter,fixture.machine,dt);double[] actual=fixture.exciter.getStateSnapshot();
            for(int j=0;j<actual.length;j++)maxError=Math.max(maxError,Math.abs(expected[j]-actual[j]));
        }
        assertTrue(maxError<1e-10,"ST7C five-state max error="+maxError);
    }

    @Test void initializesPublishedLowPassFeedbackWithNegativeSummingPolarity()throws Exception{
        Fixture fixture=fixture(baseData());
        assertEquals(.2*1.2,fixture.exciter.getFeedbackOutput(),TOL);
        assertEquals(1.2+.2*1.2,fixture.exciter.getSecondLeadLagOutput(),TOL);
        assertEquals(1.2,fixture.exciter.getPreFiringField(),TOL);
        assertEquals(1.04+(1+.2)*1.2/2,fixture.exciter.getRefPoint(),TOL);
    }

    @Test void implementsEveryPublishedOelAndUelLocation()throws Exception{
        St7cData data=baseData();data.setOel(1);Fixture oel1=fixture(data);double base=oel1.exciter.getReferenceFeedback();oel1.exciter.setVoel(-.1);
        assertEquals(base-.1,oel1.exciter.getReferenceFeedback(),TOL);
        data=baseData();data.setUel(1);Fixture uel1=fixture(data);base=uel1.exciter.getReferenceFeedback();uel1.exciter.setVuel(.1);
        assertEquals(base+.1,uel1.exciter.getReferenceFeedback(),TOL);
        data=baseData();data.setOel(2);Fixture oel2=fixture(data);oel2.exciter.setVoel(.5);assertEquals(.5,oel2.exciter.getReferenceFeedback(),TOL);
        data=baseData();data.setUel(2);Fixture uel2=fixture(data);uel2.exciter.setVuel(2);assertEquals(2,uel2.exciter.getReferenceFeedback(),TOL);
        data=baseData();data.setOel(3);Fixture oel3=fixture(data);oel3.exciter.setVoel(.8);assertTrue(oel3.exciter.getPreFiringField()<=.8+TOL);
        data=baseData();data.setUel(3);Fixture uel3=fixture(data);uel3.exciter.setVuel(2);assertTrue(uel3.exciter.getPreFiringField()>=2-TOL);
    }

    @Test void appliesPowerWorldCorrectionsAndInitializationLimitExpansion()throws Exception{
        St7cData data=baseData();data.setTr(.003);data.setTa(.015);data.setVmax(-1);data.setVmin(-2);data.setVrmax(-1);data.setVrmin(-2);
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();St7cExciter exciter=builder.addExcSt7c("Bus1","1",data);
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);exciter.configureIntegrationStep(.01,2);
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));assertEquals(0,exciter.tr,TOL);assertEquals(.02,exciter.getFiringTimeConstant(),TOL);
        assertTrue(exciter.vmax>=exciter.getReferenceFeedback());assertTrue(exciter.vmin<=exciter.getReferenceFeedback());
        assertEquals(1.2,exciter.getOutput(machine),TOL);
    }

    @Test void supportsCorrectedAlgebraicFiringPathAndRejectsInvalidParameters()throws Exception{
        St7cData data=baseData();data.setTa(.003);DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        St7cExciter exciter=builder.addExcSt7c("Bus1","1",data);Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);exciter.configureIntegrationStep(.01,2);assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        assertEquals(0,exciter.getFiringTimeConstant(),TOL);exciter.setRefPoint(exciter.getRefPoint()+.1);assertTrue(exciter.getOutput(machine)>1.2);
        data=baseData();data.setKia(0);builder=DStabBuilderTestFixture.createWithMachine();exciter=builder.addExcSt7c("Bus1","1",data);
        machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);assertFalse(exciter.initStates(machine.getDStabBus(),machine));
        data=baseData();data.setTa(Double.NaN);builder=DStabBuilderTestFixture.createWithMachine();exciter=builder.addExcSt7c("Bus1","1",data);
        machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);assertFalse(exciter.initStates(machine.getDStabBus(),machine));
    }

    @Test void holdsEquilibriumAndParticipatesInFullSimulation()throws Exception{
        Fixture fixture=fixture(baseData());for(int i=0;i<1000;i++)step(fixture.exciter,fixture.machine,.0001);
        assertEquals(1.2,fixture.exciter.getOutput(fixture.machine),TOL);
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();assertNotNull(builder.addExcSt7c("Bus1","1",baseData()));
        DynamicSimuAlgorithm algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(.005);algorithm.setTotalSimuTimeSec(.02);
        algorithm.setSimuOutputHandler(new StateMonitor());assertTrue(algorithm.getAclfAlgorithm().loadflow());
        assertTrue(algorithm.initialization());assertTrue(algorithm.performSimulation());
    }

    private static Fixture fixture(St7cData data)throws Exception{
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        St7cExciter exciter=builder.addExcSt7c("Bus1","1",data);machine.setEfd(1.2);assertNotNull(exciter);
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));return new Fixture(machine,exciter);
    }
    private static St7cData baseData(){St7cData data=new St7cData();data.setTr(.1);data.setTg(.2);data.setTf(.4);data.setVmax(5);data.setVmin(-5);
        data.setKpa(2);data.setVrmax(10);data.setVrmin(-10);data.setKh(.1);data.setKl(.1);data.setTc(.3);data.setTb(.5);
        data.setKia(.2);data.setTia(1);data.setTa(.25);return data;}
    private static double[] derivatives(double[] x,double reference){double input=.5*x[0]+x[1],amplifier=2*(reference-input);
        double second=.6*amplifier+x[2],pre=second-x[3];return new double[]{(1.04-x[0])/.1,((1-.5)*x[0]-x[1])/.4,
                ((1-.6)*amplifier-x[2])/.5,(.2*x[4]-x[3])/1,(pre-x[4])/.25};}
    private static double[] add(double[] state,double[] derivative,double dt){double[] result=new double[state.length];
        for(int i=0;i<state.length;i++)result[i]=state[i]+derivative[i]*dt;return result;}
    private static void step(St7cExciter exciter,Machine machine,double dt){assertTrue(exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        assertTrue(exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,1));}
    private record Fixture(Machine machine,St7cExciter exciter){}
}
