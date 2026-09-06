package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderExac1Test extends CorePluginTestSetup {
    private static final double TOL=1e-8;
    @TempDir Path tempDir;

    @Test
    void parsesCompleteRecordInitializesAndResponds() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("exac1.dyr");
        Files.writeString(dyr,"1 'EXAC1' '1' 0 0 0 400 .066667 8.104 -8.104 .4617 .035 1 .2774 .5 1 2.0831 .0819 2.7774 .4095 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0); machine.setEfd(1.2);
        Exac1Exciter exc=(Exac1Exciter)machine.getExciter();
        assertNotNull(exc);
        assertEquals(400,exc.getData().getKa(),TOL);
        assertEquals(.2774,exc.getData().getKc(),TOL);
        assertEquals(.4095,exc.getData().getSe2(),TOL);
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        double initial=exc.getOutput(machine);
        assertEquals(1.2,initial,1e-6);
        machine.getDStabBus().setVoltage(new Complex(.99,0));
        for(int i=0;i<40;i++){
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,0));
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        }
        assertTrue(exc.getOutput(machine)>initial,
                () -> "initial="+initial+", final="+exc.getOutput(machine));
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void rectifierMatchesAllAndesPiecewiseRegionsAndInverseInitialization() {
        double[] inputs={-.1,.2,.5,.8,1.2};
        double[] expected={1,1-.577*.2,Math.sqrt(.75-.25),1.732*.2,0};
        for(int i=0;i<inputs.length;i++) assertEquals(expected[i],Exac1Exciter.rectifierFactor(inputs[i]),TOL);
        for(double loading:new double[]{.2,.5,.8}) {
            double efd=Exac1Exciter.rectifierFactor(loading);
            assertEquals(1.0,Exac1Exciter.solveInternalVoltage(efd,loading),TOL);
        }
    }
}
