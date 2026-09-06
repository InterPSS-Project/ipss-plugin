package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.rexsys.RexsysData;
import org.interpss.dstab.control.exc.psse.rexsys.RexsysExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

/** Import, correction and published-equation regression tests for REXSYS. */
public class RexsysExciterTest extends CorePluginTestSetup {
    @TempDir Path tempDir;

    @Test void parsesSuppliedThirtyOneParameterRecordAndHoldsEquilibrium() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("rexsys.dyr");
        Files.writeString(dyr,"1 'REXSYS' '1' .1 8 13 999 0 0 0 0 0 "
                +"15.4 -12.3 .02 .7 0 0 1 20 10 .02 15.4 -12.3 1 .5 .7 "
                +".1 .5 2.525 .1 3.367 .23 1 /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        RexsysExciter exciter=(RexsysExciter)machine.getExciter();
        assertNotNull(exciter);
        RexsysData d=exciter.getData();
        assertEquals(.1,d.getTr(),1e-12);assertEquals(8,d.getKvp(),1e-12);
        assertEquals(13,d.getKvi(),1e-12);assertEquals(15.4,d.getVrmax(),1e-12);
        assertEquals(.02,d.getKf(),1e-12);assertEquals(1,d.getFbf());
        assertEquals(20,d.getKip(),1e-12);assertEquals(.02,d.getTp(),1e-12);
        assertEquals(1,d.getKh(),1e-12);assertEquals(.7,d.getTe(),1e-12);
        assertEquals(.23,d.getSe2(),1e-12);assertEquals(1,d.getFlimf());
        machine.setEfd(1.2);
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        double initial=exciter.getOutput(machine);
        for(int i=0;i<1000;i++){
            assertTrue(exciter.nextStep(.0001,DynamicSimuMethod.MODIFIED_EULER,machine,0));
            assertTrue(exciter.nextStep(.0001,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        }
        assertEquals(initial,exciter.getOutput(machine),1e-9);
    }

    @Test void reducedSixStateTrajectoryMatchesPublishedBlockEquations() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        RexsysData d=baseData();
        RexsysExciter exciter=builder.addExcRexsys("Bus1","1",d);
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        exciter.setRefPoint(exciter.getRefPoint()+.1);
        double[] x={0,0,1.2,1.2,1.2}; // voltage I, VR, current I, bridge, VE
        double maxError=0,dt=.0001;
        for(int step=0;step<2000;step++){
            double[] d0=derivatives(x);double[] p=add(x,d0,dt);double[] d1=derivatives(p);
            for(int i=0;i<x.length;i++)x[i]+=.5*(d0[i]+d1[i])*dt;
            exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,0);
            exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,1);
            maxError=Math.max(maxError,Math.abs(x[1]-exciter.getRegulatorOutput()));
            maxError=Math.max(maxError,Math.abs(x[3]-exciter.getBridgeOutput()));
            maxError=Math.max(maxError,Math.abs(x[4]-exciter.getInternalFieldVoltage()));
        }
        assertTrue(maxError<1e-10,"REXSYS reduced-state max error="+maxError);
    }

    @Test void appliesPowerWorldCorrectionsAndInitializationLimitExpansion() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        RexsysData d=baseData();d.setTr(.004);d.setTa(.006);d.setTb1(.005);d.setTf(.006);
        d.setTf2(.005);d.setTp(.005);d.setTe(.005);d.setVimax(.01);
        d.setVrmax(-2);d.setVrmin(-3);d.setVfmax(-2);d.setVfmin(-3);
        RexsysExciter exciter=builder.addExcRexsys("Bus1","1",d);
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        exciter.configureIntegrationStep(.01,2);
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        assertEquals(0,exciter.tr,1e-12);assertEquals(0,exciter.ta,1e-12);
        assertEquals(0,exciter.tb1,1e-12);assertEquals(0,exciter.tf,1e-12);
        assertEquals(0,exciter.tf2,1e-12);assertEquals(.02,exciter.tp,1e-12);
        assertEquals(.02,exciter.te,1e-12);
        assertTrue(exciter.vimax>=Math.abs(exciter.getRegulatorOutput()));
        assertTrue(exciter.vrmax>=exciter.getRegulatorOutput());
        assertTrue(exciter.vfmax>=exciter.getBridgeOutput());
    }

    private static RexsysData baseData(){
        RexsysData d=new RexsysData();d.setTr(0);d.setKvp(1);d.setKvi(2);d.setVimax(99);
        d.setTa(.1);d.setTb1(0);d.setTc1(0);d.setTb2(0);d.setTc2(0);
        d.setVrmax(99);d.setVrmin(-99);d.setKf(0);d.setTf(0);d.setTf1(0);d.setTf2(0);d.setFbf(0);
        d.setKip(1);d.setKii(3);d.setTp(.2);d.setVfmax(99);d.setVfmin(-99);d.setKh(0);
        d.setKe(1);d.setTe(.3);d.setKc(0);d.setKd(0);d.setE1(0);d.setSe1(0);d.setE2(0);d.setSe2(0);d.setFlimf(0);
        return d;
    }
    private static double[] derivatives(double[] x){
        double voltagePi=.1+x[0];double vr=x[1];double currentPi=vr+x[2];
        return new double[]{.2,(voltagePi-vr)/.1,3*vr,(currentPi-x[3])/.2,(x[3]-x[4])/.3};
    }
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
}
