package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Gewtgcu1Model;
import org.interpss.dstab.mach.Gewtptu1Data;
import org.interpss.dstab.mach.Gewtptu1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Synthetic schema, state-equation, and coupled pitch checks. */
public class Gewtptu1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final String SYNTHETIC_DYR = """
            1 'USRMDL' '1' 'GEWTGCU1' 101 1 2 18 3 3
              40 0
              1.5 0.33403 0.50 0.90 2.775 1.20 1.00 0.40 0.90 10.0 0.02
              0.40 0.00 0.70 0.55 0.90 1.00 0.10 /
            1 'USRMDL' '1' 'GEWT2MU1' 103 0 1 5 4 3 0
              4.61 0.09 0.68 1.37 0.19 /
            1 'USRMDL' '1' 'GEWTARU1' 105 0 1 9 1 4 0
              19.1 2.2 26.0 -1.2 0.047 1.16 52.0 97.0 1180 /
            1 'USRMDL' '1' 'GEWTPTU1' 104 0 2 10 3 3 0 0
              0.21 11.7 3.2 1.9 1.4 -1.2 25.0 -4.8 4.2 0.73 /
            2 'GENCLS' '1' 99999.0 0.0 /
            """;

    @Test
    void modifiedEulerMatchesIndependentThreeStateCalculation() {
        Gewtptu1Model model = new Gewtptu1Model(data());
        model.initialize(4.0, 1.0);
        model.step(.01, 1.02, .75, 0);
        model.step(.01, 1.02, .75, 1);
        assertEquals(4.0006, model.getSpeedIntegral(), 1.0e-12);
        assertEquals(.00075, model.getPowerIntegral(), 1.0e-12);
        assertEquals(4.01660875, model.getPitch(), 1.0e-12);
        assertEquals(3, model.getNamedStates().size());
    }

    @Test
    void pitchAndRateLimitsBlockOutwardMotion() {
        Gewtptu1Model model = new Gewtptu1Model(data());
        model.initialize(20.0, 1.0);
        model.step(.1, 1.5, 1.5, 0);
        model.step(.1, 1.5, 1.5, 1);
        assertEquals(20.0, model.getPitch(), 0.0);
        assertEquals(20.0, model.getSpeedIntegral(), 0.0);
        assertEquals(0.0, model.getPowerIntegral(), 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> new Gewtptu1Data(-.1, 1, 1, 1, 1, 0, 20, -5, 5, .7));
    }

    @Test
    void exactWrapperAttachesAndFeedsAerodynamicPitch(@TempDir Path tempDir)
            throws Exception {
        IpssCorePlugin.init();
        Path dyr = tempDir.resolve("synthetic-ge-pitch.dyr");
        Files.writeString(dyr, SYNTHETIC_DYR);
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1_psse36.raw").toString(), dyr.toString());
        DynamicSimuAlgorithm algorithm = context.getDynSimuAlgorithm();
        DStabGen gen = (DStabGen) context.getDStabilityNet().getBus("Bus1")
                .getContributeGen("1");
        Gewtgcu1Model generator = assertInstanceOf(Gewtgcu1Model.class,
                gen.getDynamicGenDevice());
        assertEquals(18, DynamicModelCatalog.find("GEWTPTU1").orElseThrow()
                .parameterCount());
        assertNotNull(generator.getPitchController());
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        assertTrue(algorithm.solveDEqnStep(true));
        assertTrue(generator.getNamedStates().containsKey("Output Lag"));
        assertEquals(generator.getPitchController().getPitch(),
                generator.getAerodynamicModel().getNamedStates().get("Pitch"), 0.0);
    }

    private static Gewtptu1Data data() {
        return new Gewtptu1Data(.2, 12.0, 3.0, 2.0, 1.5,
                0.0, 20.0, -5.0, 4.0, .70);
    }
}
