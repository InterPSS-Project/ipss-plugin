package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Gewtgcu1Model;
import org.interpss.dstab.mach.Gewtgdu1Data;
import org.interpss.dstab.mach.Gewtgdu1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Synthetic wrapper, waveform, and coupled-response checks. */
public class Gewtgdu1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final String SYNTHETIC_DYR = """
            1 'USRMDL' '1' 'GEWTGCU1' 101 1 2 18 3 3
              40 0
              1.5 0.33403 0.50 0.90 2.775 1.20 1.00 0.40 0.90 10.0 0.02
              0.40 0.00 0.70 0.55 0.90 1.00 0.10 /
            1 'USRMDL' '1' 'GEWT2MU1' 103 0 1 5 4 3 0
              4.37 0.08 0.71 1.43 0.17 /
            1 'USRMDL' '1' 'GEWTARU1' 105 0 1 9 1 4 0
              18.7 2.4 25.5 -1.5 0.043 1.19 51.7 96.5 1175 /
            1 'USRMDL' '1' 'GEWTGDU1' 106 0 1 6 0 4 0
              0.013 0.041 1.7 0.073 0.127 -0.46 /
            2 'GENCLS' '1' 99999.0 0.0 /
            """;

    @Test
    void waveformMatchesPublishedGustAndRampShapes() {
        Gewtgdu1Data data = data();
        Gewtgdu1Model model = new Gewtgdu1Model(data);
        model.initialize(10.3);
        assertEquals(0.0, model.gustAt(data.gustStart()), 0.0);
        assertEquals(1.7, model.gustAt(data.gustStart() + data.gustDuration()/2.0), 1.0e-15);
        assertEquals(0.0, model.gustAt(data.gustStart() + data.gustDuration()), 1.0e-15);
        assertEquals(-0.23, model.rampAt((data.rampStart() + data.rampEnd())/2.0), 1.0e-15);
        assertEquals(-0.46, model.rampAt(data.rampEnd() + 1.0), 0.0);
    }

    @Test
    void exactWrapperAttachesAndDrivesAerodynamics(@TempDir Path tempDir) throws Exception {
        Fixture fixture = load(tempDir, SYNTHETIC_DYR);
        assertEquals(13, DynamicModelCatalog.find("GEWTGDU1").orElseThrow().parameterCount());
        assertNotNull(fixture.wind);
        double baseWind = fixture.wind.getInitialWindSpeed();
        for (int index = 0; index < 20; index++) assertTrue(fixture.algorithm.solveDEqnStep(true));
        assertEquals(baseWind, fixture.wind.getWindSpeed(), 1.0e-12);
        for (int index = 0; index < 20; index++) assertTrue(fixture.algorithm.solveDEqnStep(true));
        assertNotEquals(baseWind, fixture.wind.getWindSpeed());
        assertTrue(fixture.generator.getNamedStates().containsKey("Gust component"));
    }

    @Test
    void rejectsWrongAllocationAndInvalidTiming(@TempDir Path tempDir) throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new Gewtgdu1Data(.1, 0.0, 1.0, .2, .3, .4));
        Fixture fixture = load(tempDir,
                SYNTHETIC_DYR.replace("106 0 1 6 0 4", "106 0 1 7 0 4"));
        assertTrue(fixture.wind == null);
    }

    private static Gewtgdu1Data data() {
        return new Gewtgdu1Data(.013, .041, 1.7, .073, .127, -.46);
    }

    private static Fixture load(Path tempDir, String text) throws Exception {
        IpssCorePlugin.init();
        Path dyr = tempDir.resolve("synthetic-ge-wind-signal.dyr");
        Files.writeString(dyr, text);
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1_psse36.raw").toString(), dyr.toString());
        DynamicSimuAlgorithm algorithm = context.getDynSimuAlgorithm();
        DStabGen gen = (DStabGen) context.getDStabilityNet().getBus("Bus1")
                .getContributeGen("1");
        Gewtgcu1Model generator = assertInstanceOf(Gewtgcu1Model.class,
                gen.getDynamicGenDevice());
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(0.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        return new Fixture(generator, generator.getWindModel(), algorithm);
    }

    private record Fixture(Gewtgcu1Model generator, Gewtgdu1Model wind,
            DynamicSimuAlgorithm algorithm) { }
}
