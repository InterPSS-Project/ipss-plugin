package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.esac6a.Esac6aData;
import org.interpss.dstab.control.exc.psse.esac6a.Esac6aExciter;
import org.interpss.dstab.mach.GenqecData;
import org.interpss.dstab.mach.GenqecMachine;
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
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.util.sample.SampleDStabCase;

/** Import, correction, equation and solver regression tests for ESAC6A. */
public class Esac6aExciterTest extends CorePluginTestSetup {
    private static final double TOL=1e-9;

    @Test void parsesRealCorpusRecordAndHoldsEquilibrium(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("esac6a.dyr");
        Files.writeString(dyr,"1 'ESAC6A' 1 0 531.499 21.3817 3 .23 .2 "
                +"9.08 -9.08 9.08 -9.08 .5 0 0 1 0 0 0 0 1 "
                +"4.9386 .0547 6.5848 .3648 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1);machine.setEfd(1.2);
        Esac6aExciter exciter=(Esac6aExciter)machine.getExciter();
        assertNotNull(exciter);assertEquals(531.499,exciter.getData().getKa(),TOL);
        assertEquals(3,exciter.getData().getTk(),TOL);
        assertEquals(.5,exciter.getData().getTe(),TOL);
        assertEquals(.3648,exciter.getData().getSe2(),TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        double initial=exciter.getOutput(machine);
        for(int i=0;i<1000;i++)step(exciter,machine,.0001);
        assertEquals(initial,exciter.getOutput(machine),1e-9);
    }

    @Test void catalogDefinesExactPsseSchemaAndTypedOnlySpeedMultiplier(){
        var descriptor=DynamicModelCatalog.find("ESAC6A").orElseThrow();
        assertEquals(23,descriptor.parameterCount());
        assertTrue(descriptor.recordSchema().accepts(23));
        assertFalse(descriptor.recordSchema().accepts(24));
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESAC6A").orElseThrow().isImplementedExactly());
    }

    @Test void fiveStateTrajectoryMatchesPublishedBlockEquations() throws Exception {
        Fixture f=fixture(baseData());
        f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double[] x={1.2,1.0,1.344,1.26,.32};
        double dt=.0001,maxError=0;
        for(int i=0;i<2000;i++){
            double[] d0=equationDerivatives(x,.1);double[] p=add(x,d0,dt);
            double[] d1=equationDerivatives(p,.1);
            for(int j=0;j<x.length;j++)x[j]+=.5*(d0[j]+d1[j])*dt;
            step(f.exciter,f.machine,dt);
            double error=.84+.1+1-x[1];
            double taOut=.4*error+x[2];
            double va=.25*taOut+x[3];
            double vfe=x[0],vh=.4*vfe,vf=vh/3+x[4],vr=va-vf;
            maxError=Math.max(maxError,Math.abs(x[0]-f.exciter.getInternalFieldVoltage()));
            maxError=Math.max(maxError,Math.abs(taOut-f.exciter.getTaOutput()));
            maxError=Math.max(maxError,Math.abs(va-f.exciter.getVaOutput()));
            maxError=Math.max(maxError,Math.abs(vfe-f.exciter.getFieldFeedback()));
            maxError=Math.max(maxError,Math.abs(vh-f.exciter.getVhOutput()));
            maxError=Math.max(maxError,Math.abs(vf-f.exciter.getFeedbackOutput()));
            maxError=Math.max(maxError,Math.abs(vr-f.exciter.getRegulatorOutput()));
        }
        assertTrue(maxError<1e-10,"ESAC6A equation max error="+maxError);
    }

    @Test void appliesPowerWorldCorrectionsAndInitializationLimitExpansion() throws Exception {
        Esac6aData data=baseData();data.setTr(.004);data.setTe(.005);
        data.setVamax(-2);data.setVamin(-3);data.setVrmax(-2);data.setVrmin(-3);
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Esac6aExciter exciter=builder.addExcEsac6a("Bus1","1",data);
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        exciter.configureIntegrationStep(.01,2);
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        assertEquals(0,exciter.tr,TOL);assertEquals(.02,exciter.te,TOL);
        assertTrue(exciter.vamax>=exciter.getVaOutput());
        assertTrue(exciter.vamin<=exciter.getVaOutput());
        assertTrue(exciter.vrmax>=exciter.getRegulatorOutput()/machine.getDStabBus().getVoltageMag());
        assertTrue(exciter.vrmin<=exciter.getRegulatorOutput()/machine.getDStabBus().getVoltageMag());
    }

    @Test void scalesRegulatorLimitsWithTerminalVoltage() throws Exception {
        Esac6aData data=baseData();data.setVrmax(2);data.setVrmin(-2);
        Fixture f=fixture(data);f.machine.getDStabBus().setVoltage(new Complex(.5,0));
        f.exciter.setRefPoint(f.exciter.getRefPoint()+100);
        assertEquals(1.0,f.exciter.getRegulatorOutput(),TOL);
    }

    @Test void routesVuelAndPreservesItsInitializationEquilibrium()throws Exception{
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Esac6aExciter exciter=builder.addExcEsac6a("Bus1","1",baseData());
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1);machine.setEfd(1.2);exciter.setVuel(.2);
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        double initial=exciter.getOutput(machine),ta0=exciter.getTaOutput();
        for(int i=0;i<1000;i++)step(exciter,machine,.0001);
        assertEquals(initial,exciter.getOutput(machine),1e-9);
        exciter.setVuel(.25);
        assertEquals(ta0+.02,exciter.getTaOutput(),TOL);
    }

    @Test void honorsTypedSpeedMultiplierAtNonUnitSpeed()throws Exception{
        Esac6aData data=baseData();data.setSpdmlt(1);Fixture f=fixture(data,1.02);
        assertEquals(1.2,f.exciter.getOutput(f.machine),1e-8);
        assertEquals(1.2/1.02,f.exciter.getInternalFieldVoltage(),1e-8);
    }

    @Test void sensesCompensatedMachineVoltageButKeepsRawTerminalLimitScaling()throws Exception{
        BaseDStabNetwork<?,?> network=SampleDStabCase.createDStabTestNet();
        DStabNetworkBuilder builder=new DStabNetworkBuilder(network);
        GenqecData machineData=new GenqecData(3.17,0,.003,2.37,1.87,.32,.52,.28,.20,.19,
                6.81,.85,.02,.02,.233,.797,.02,.10,0,.1,1);
        GenqecMachine machine=builder.addGenqec("Gen","G1",100,1,machineData);
        var bus=network.getDStabBus("Gen");bus.initStates();assertTrue(machine.initStates(bus));
        Esac6aData data=baseData();data.setTr(0);data.setVrmax(2);data.setVrmin(-2);
        Esac6aExciter exciter=builder.addExcEsac6a("Gen","G1",data);
        assertTrue(exciter.initStates(bus,machine));
        assertEquals(machine.getCompensatedVoltage(),exciter.getSensedVoltage(),TOL);
        assertTrue(Math.abs(exciter.getSensedVoltage()-bus.getVoltageMag())>1e-5);
        double rawLimit=bus.getVoltageMag()*exciter.vrmax;
        double compensatedLimit=exciter.getSensedVoltage()*exciter.vrmax;
        exciter.setRefPoint(exciter.getRefPoint()+100);
        assertEquals(rawLimit,exciter.getRegulatorOutput(),TOL);
        assertTrue(Math.abs(exciter.getRegulatorOutput()-compensatedLimit)>1e-5);
    }

    @Test void rejectsInvalidParameters()throws Exception{
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Esac6aData data=baseData();data.setTe(0);assertNull(builder.addExcEsac6a("Bus1","1",data));
        data=baseData();data.setTh(-.1);assertNull(builder.addExcEsac6a("Bus1","1",data));
    }

    @Test void participatesInFullDynamicSimulation() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        assertNotNull(builder.addExcEsac6a("Bus1","1",baseData()));
        DynamicSimuAlgorithm algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.005);algorithm.setTotalSimuTimeSec(.02);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        assertTrue(Double.isFinite(machine.getExciter().getOutput(machine)));
    }

    private static Fixture fixture(Esac6aData data) throws Exception {return fixture(data,1);}
    private static Fixture fixture(Esac6aData data,double speed) throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Esac6aExciter exciter=builder.addExcEsac6a("Bus1","1",data);
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(speed);machine.setEfd(1.2);
        assertNotNull(exciter);assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        return new Fixture(machine,exciter);
    }
    private static Esac6aData baseData(){
        Esac6aData d=new Esac6aData();d.setTr(.1);d.setKa(2);d.setTa(.1);d.setTk(.02);
        d.setTb(.2);d.setTc(.05);d.setVamax(99);d.setVamin(-99);d.setVrmax(99);d.setVrmin(-99);
        d.setTe(.4);d.setVfelim(0);d.setKh(.4);d.setVhmax(99);d.setTh(.3);d.setTj(.1);
        d.setKc(0);d.setKd(0);d.setKe(1);d.setE1(0);d.setSe1(0);d.setE2(0);d.setSe2(0);
        return d;
    }
    private static double[] equationDerivatives(double[] x,double referenceStep){
        double error=.84+referenceStep+1-x[1];double taOut=.4*error+x[2];
        double va=.25*taOut+x[3];double vfe=x[0],vh=.4*vfe,vf=vh/3+x[4],vr=va-vf;
        return new double[]{(vr-vfe)/.4,(1-x[1])/.1,
                (2*(1-.02/.1)*error-x[2])/.1,
                ((1-.05/.2)*taOut-x[3])/.2,
                ((1-.1/.3)*vh-x[4])/.3};
    }
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(Esac6aExciter exciter,Machine machine,double dt){
        exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,0);
        exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,1);
    }
    private record Fixture(Machine machine,Esac6aExciter exciter){}
}
