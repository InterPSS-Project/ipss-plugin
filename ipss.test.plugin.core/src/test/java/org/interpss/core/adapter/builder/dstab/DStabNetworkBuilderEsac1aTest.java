package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.esac1a.Esac1aData;
import org.interpss.dstab.control.exc.psse.esac1a.Esac1aExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
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
}
