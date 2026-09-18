package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Gewtaru1Data;
import org.interpss.dstab.mach.Gewtaru1Model;
import org.interpss.dstab.mach.Gewtgcu1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Synthetic schema, equation, state, and coupled-flat-run checks. */
public class Gewtaru1ModelTest {
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
            2 'GENCLS' '1' 99999.0 0.0 /
            """;

    @Test
    void exactWrapperAttachesAndRemainsFlat(@TempDir Path tempDir) throws Exception {
        Fixture fixture = load(tempDir, SYNTHETIC_DYR);
        assertEquals(16, DynamicModelCatalog.find("GEWTARU1").orElseThrow().parameterCount());
        assertNotNull(fixture.aerodynamic);
        assertEquals(fixture.generator.getP(),
                fixture.aerodynamic.getMechanicalPower(
                        fixture.generator.getDriveTrain().getTurbineSpeed()), 1.0e-12);
        assertTrue(fixture.aerodynamic.getNamedStates().containsKey("Conversion smoothing lag"));
        double initialSpeed = fixture.generator.getDriveTrain().getGeneratorSpeed();
        for (int index = 0; index < 20; index++) assertTrue(fixture.algorithm.solveDEqnStep(true));
        assertEquals(initialSpeed, fixture.generator.getDriveTrain().getGeneratorSpeed(), 2.0e-8);
    }

    @Test
    void publishedPolynomialAndConversionLagMatchIndependentCheckpoints() {
        Gewtaru1Data data = data();
        Gewtaru1Model model = new Gewtaru1Model(data);
        assertEquals(0.42013015488990996, model.powerCoefficient(3.2, 7.4), 1.0e-15);
        model.initialize(0.61, 1.0, 1.5);
        double changedWind = model.getWindVelocity() * 1.03;
        model.setWindVelocity(changedWind);
        double target = model.aerodynamicPower(1.0, changedWind, data.pitchMinimum());
        double dt = 0.002;
        double d0 = (target - 0.61) / data.conversionTime();
        model.step(dt, 1.0, 0);
        double predictor = 0.61 + dt * d0;
        assertEquals(predictor, model.getTorque(), 1.0e-14);
        double d1 = (target - predictor) / data.conversionTime();
        model.step(dt, 1.0, 1);
        assertEquals(0.61 + 0.5 * dt * (d0 + d1), model.getTorque(), 1.0e-14);
    }

    @Test
    void rejectsWrongAllocationAndInvalidRanges(@TempDir Path tempDir) throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new Gewtaru1Data(2, 3, 20, 0, .1, 1.2, 50, 90, 1200));
        Fixture fixture = load(tempDir,
                SYNTHETIC_DYR.replace("105 0 1 9 1 4", "105 0 1 8 1 4"));
        assertTrue(fixture.aerodynamic == null);
    }

    private static Gewtaru1Data data() {
        return new Gewtaru1Data(18.7, 2.4, 25.5, -1.5,
                0.043, 1.19, 51.7, 96.5, 1175);
    }

    private static Fixture load(Path tempDir, String text) throws Exception {
        IpssCorePlugin.init();
        Path dyr = tempDir.resolve("synthetic-ge-aerodynamic.dyr");
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
        return new Fixture(generator, generator.getAerodynamicModel(), algorithm);
    }

    private record Fixture(Gewtgcu1Model generator, Gewtaru1Model aerodynamic,
            DynamicSimuAlgorithm algorithm) { }
}
