package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.exc.psse.esurry.EsurryExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderEsurryTest extends CorePluginTestSetup {
    private static final double TOL=1.0e-8;
    @TempDir Path tempDir;

    @Test
    void parsesSuppliedTwentyParameterRecordAndResponds() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("esurry.dyr");
        Files.writeString(dyr,"1 'ESURRY' '1' .016668 .8 .1 .6 19 400 1 1 20 1.6 14 -12.6 2 3.01 .01 5.01 .2 .001 0 .8 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0); machine.setEfd(1.2);
        EsurryExciter exc=(EsurryExciter)machine.getExciter();
        assertNotNull(exc);
        assertEquals(.016668,exc.getData().getTr(),TOL);
        assertEquals(.8,exc.getData().getTa(),TOL);
        assertEquals(.1,exc.getData().getTb(),TOL);
        assertEquals(.6,exc.getData().getTc(),TOL);
        assertEquals(19.0,exc.getData().getTd(),TOL);
        assertEquals(400.0,exc.getData().getK10(),TOL);
        assertEquals(1.0,exc.getData().getT1(),TOL);
        assertEquals(1.0,exc.getData().getK16(),TOL);
        assertEquals(20.0,exc.getData().getKf(),TOL);
        assertEquals(1.6,exc.getData().getTf(),TOL);
        assertEquals(14.0,exc.getData().getVrmax(),TOL);
        assertEquals(-12.6,exc.getData().getVrmin(),TOL);
        assertEquals(2.0,exc.getData().getTe(),TOL);
        assertEquals(3.01,exc.getData().getE1(),TOL);
        assertEquals(.01,exc.getData().getSe1(),TOL);
        assertEquals(5.01,exc.getData().getE2(),TOL);
        assertEquals(.2,exc.getData().getSe2(),TOL);
        assertEquals(.001,exc.getData().getKc(),TOL);
        assertEquals(0.0,exc.getData().getKd(),TOL);
        assertEquals(.8,exc.getData().getKe(),TOL);
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        double initial=exc.getOutput(machine);
        assertEquals(1.2,initial,1.0e-6);
        for (int i=0;i<20;i++) {
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,0));
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        }
        assertEquals(initial,exc.getOutput(machine),1.0e-9);
        machine.getDStabBus().setVoltage(new Complex(.99,0));
        for (int i=0;i<40;i++) {
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,0));
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        }
        assertTrue(exc.getOutput(machine)>initial,
                () -> "initial="+initial+", final="+exc.getOutput(machine));
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void correctedFeedbackVoltageUsesInternalVeAndDemagnetizingCurrent() {
        double ve=3.5;
        double ifd=.6;
        double expected=ve*(.8+Exac1Exciter.saturation(ve,3.01,.01,5.01,.2));
        assertEquals(expected,EsurryExciter.feedbackVoltage(
                ve,ifd,.8,0,3.01,.01,5.01,.2),TOL);
        assertEquals(expected+.12,EsurryExciter.feedbackVoltage(
                ve,ifd,.8,.2,3.01,.01,5.01,.2),TOL);
    }

    @Test
    void acceptsApprovedExac1mAliasWithoutChangingTheRuntimeModel() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("exac1m.dyr");
        Files.writeString(dyr,"1 'EXAC1M' '1' .016668 .8 .1 .6 19 400 1 1 20 1.6 14 -12.6 2 3.01 .01 5.01 .2 .001 0 .8 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        assertTrue(machine.getExciter() instanceof EsurryExciter);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }
}
