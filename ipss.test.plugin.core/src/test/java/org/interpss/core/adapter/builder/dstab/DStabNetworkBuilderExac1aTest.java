package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.exac1a.Exac1aExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderExac1aTest extends CorePluginTestSetup {
    private static final double TOL=1e-8;
    @TempDir Path tempDir;

    @Test
    void parsesRealRecordAndUsesEfdFeedback() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("exac1a.dyr");
        Files.writeString(dyr,"1 'EXAC1A' '1' .016668 1 1 150 .016668 10 -10 .15 .01 2.6 .36 .89 1 4.65 .037 6.19 .113 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());

        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0); machine.setEfd(1.2);
        Exac1aExciter exc=(Exac1aExciter)machine.getExciter();
        assertNotNull(exc);
        assertEquals(150.0,exc.getData().getKa(),TOL);
        assertEquals(.36,exc.getData().getKc(),TOL);
        assertEquals(.89,exc.getData().getKd(),TOL);
        AnControllerField washout=Exac1aExciter.class.getField("washout")
                .getAnnotation(AnControllerField.class);
        assertEquals("this.rectifier.y",washout.input());

        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        double initial=exc.getOutput(machine);
        assertEquals(1.2,initial,1e-6);
        machine.getDStabBus().setVoltage(new Complex(.99,0));
        for(int i=0;i<40;i++) {
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,0));
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        }
        assertTrue(Double.isFinite(exc.getOutput(machine)));
        assertTrue(exc.getOutput(machine)>initial);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }
}
