package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.ac7b.Ac7bExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

/** PSS/E AC7B/ESAC7B schema and attachment regression tests. */
public class Ac7bImportTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-12;
    @TempDir Path tempDir;

    @Test
    void parsesSuppliedAc7bRecordInItsTwentySevenParameterOrder() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("ac7b.dyr");
        Files.writeString(dyr, "1 'AC7B' '1' 0 26.6 26.6 0 .016668 25 -40.4 "
                + "1.391 1.159 25 -40.4 1 1 0 .864 .01 1 .1 1 1 1.2 "
                + "55.6 0 7.05 .41 9.4 1 /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ac7bExciter exciter = (Ac7bExciter) machine.getExciter();
        assertNotNull(exciter);
        assertSame(exciter, machine.getExciter());
        assertEquals("AC7B", exciter.getModelName());
        assertEquals(26.6, exciter.getData().getKpr(), TOL);
        assertEquals(.016668, exciter.getData().getTdr(), TOL);
        assertEquals(1.391, exciter.getData().getKpa(), TOL);
        assertEquals(1.159, exciter.getData().getKia(), TOL);
        assertEquals(.864, exciter.getData().getKf2(), TOL);
        assertEquals(.01, exciter.getData().getKf3(), TOL);
        assertEquals(.1, exciter.getData().getKc(), TOL);
        assertEquals(1.2, exciter.getData().getTe(), TOL);
        assertEquals(55.6, exciter.getData().getVfemax(), TOL);
        assertEquals(7.05, exciter.getData().getE1(), TOL);
        assertEquals(1.0, exciter.getData().getSe2(), TOL);
        assertEquals(0.0, exciter.getData().getSpdmlt(), TOL);

        machine.setEfd(1.2);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        double initial = exciter.getOutput(machine);
        for (int i = 0; i < 400; i++) {
            assertTrue(exciter.nextStep(.00025, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(exciter.nextStep(.00025, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
        assertEquals(initial, exciter.getOutput(machine), 1.0e-10);
    }

    @Test
    void parsesEsac7bReorderedFieldsAndAppliesSpeedMultiplier() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("esac7b.dyr");
        Files.writeString(dyr, "1 'ESAC7B' '1' .02 2 3 .4 .03 9 -9 1.5 2 8 -8 "
                + "1.2 1 .4 7 -.2 1 .1 .5 .2 .3 .6 .7 4 .1 5 .2 1 /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        machine.setSpeed(1.0);
        Ac7bExciter exciter = (Ac7bExciter) machine.getExciter();
        assertEquals("ESAC7B", exciter.getModelName());
        assertEquals(.4, exciter.getData().getTe(), TOL);
        assertEquals(7.0, exciter.getData().getVfemax(), TOL);
        assertEquals(-.2, exciter.getData().getVemin(), TOL);
        assertEquals(.1, exciter.getData().getKc(), TOL);
        assertEquals(.5, exciter.getData().getKd(), TOL);
        assertEquals(.2, exciter.getData().getKf1(), TOL);
        assertEquals(.3, exciter.getData().getKf2(), TOL);
        assertEquals(.6, exciter.getData().getKf3(), TOL);
        assertEquals(.7, exciter.getData().getTf(), TOL);
        assertEquals(1.0, exciter.getData().getSpdmlt(), TOL);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        assertEquals(1.2, exciter.getOutput(machine), 1.0e-10);
        machine.setSpeed(.98);
        assertEquals(1.176, exciter.getOutput(machine), 1.0e-10);
    }
}
