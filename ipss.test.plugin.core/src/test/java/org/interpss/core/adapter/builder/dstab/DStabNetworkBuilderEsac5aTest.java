package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.esac5a.Esac5aData;
import org.interpss.dstab.control.exc.psse.esac5a.Esac5aExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderEsac5aTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @TempDir Path tempDir;

    @Test
    void parsesRealPsseSchemaInitializesAndRespondsToVoltageDrop() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("esac5a.dyr");
        Files.writeString(dyr,
                "1 'ESAC5A' '1' 0 400 .02 7.3 -7.3 1 .8 .03 1 .8 0 5.6 .86 4.2 .5 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        machine.setEfd(1.2);
        Esac5aExciter exciter = (Esac5aExciter) machine.getExciter();
        assertNotNull(exciter);
        assertEquals(400.0, exciter.getData().getKa(), TOL);
        assertEquals(.8, exciter.getData().getTf2(), TOL);
        assertEquals(0.0, exciter.getData().getTf3(), TOL);
        assertEquals(.5, exciter.getData().getSe2(), TOL);
        assertEquals(0.0, exciter.getData().getSpdmlt(), TOL);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        double initial = exciter.getOutput(machine);
        assertEquals(machine.getEfd(), initial, 1.0e-6);

        machine.getDStabBus().setVoltage(new Complex(.99, 0.0));
        for (int i = 0; i < 40; i++) {
            assertTrue(exciter.nextStep(.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(exciter.nextStep(.005, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
        assertTrue(exciter.getOutput(machine) > initial);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void normalizesAndExpandsRegulatorLimitsAtInitialization() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(2.0);
        Esac5aData data = data();
        data.setVrmax(-1.0);
        data.setVrmin(1.0);
        Esac5aExciter exciter = builder.addExcEsac5a("Bus1", "1", data);

        assertNotNull(exciter);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        assertTrue(exciter.vrmax >= 2.0);
        assertEquals(-1.0, exciter.vrmin, TOL);
    }

    @Test
    void rejectsInvalidDynamicParameters() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Esac5aData data = data();
        data.setTe(0.0);
        assertNull(builder.addExcEsac5a("Bus1", "1", data));
        data = data(); data.setTf2(-.1);
        assertNull(builder.addExcEsac5a("Bus1", "1", data));
    }

    private static Esac5aData data() {
        Esac5aData data = new Esac5aData();
        data.setKa(50.0); data.setTa(.02); data.setVrmax(5.0); data.setVrmin(-5.0);
        data.setKe(1.0); data.setTe(.8); data.setKf(.03);
        data.setTf1(1.0); data.setTf2(.8); data.setTf3(0.0);
        data.setE1(4.0); data.setSe1(.1); data.setE2(5.0); data.setSe2(.2);
        return data;
    }
}
