package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.esac2a.Esac2aExciter;
import org.interpss.dstab.control.util.DynamicLimitIntegrationBlock;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderEsac2aTest extends CorePluginTestSetup {
    private static final double TOL=1e-8;
    @TempDir Path tempDir;

    @Test
    void parsesInitializesAndRespondsWithoutUnsupportedFallback() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("esac2a.dyr");
        Files.writeString(dyr,"1 'ESAC2A' '1' .02 1 1 100 .05 10 -10 1 20 -20 1 5 .1 .1 1 0 0 1 1 0 2 0 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());

        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);machine.setEfd(1.2);
        Esac2aExciter exc=(Esac2aExciter)machine.getExciter();
        assertNotNull(exc);
        assertEquals(100.0,exc.getData().getKa(),TOL);
        assertEquals(1.0,exc.getData().getKb(),TOL);
        assertEquals(5.0,exc.getData().getVfemax(),TOL);
        assertEquals(0.1,exc.getData().getKh(),TOL);
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        double initial=exc.getOutput(machine);
        assertEquals(1.2,initial,1e-6);

        machine.getDStabBus().setVoltage(new Complex(.99,0));
        for(int i=0;i<40;i++) {
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,0));
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        }
        assertTrue(exc.getOutput(machine)>initial);
        assertTrue(exc.fieldIntegrator.getY()<=exc.fieldIntegrator.getUpperLimit()+TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void dynamicIntegratorEnforcesBothBoundsWithoutWindup() {
        DynamicLimitIntegrationBlock block=new DynamicLimitIntegrationBlock(2.0,1.0,0.0);
        assertTrue(block.initStateY0(.9));
        block.eulerStep1(10.0,.1);
        block.eulerStep2(10.0,.1);
        assertEquals(1.0,block.getY(),TOL);
        block.setLimits(.8,.2);
        assertEquals(.8,block.getY(),TOL);
        block.eulerStep1(-10.0,.1);
        block.eulerStep2(-10.0,.1);
        assertEquals(.2,block.getY(),TOL);
    }
}
