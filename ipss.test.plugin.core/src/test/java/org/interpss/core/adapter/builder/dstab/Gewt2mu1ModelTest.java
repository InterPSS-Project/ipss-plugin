package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Gewt2mu1Data;
import org.interpss.dstab.mach.Gewt2mu1Model;
import org.interpss.dstab.mach.Gewtgcu1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.cache.StateMonitor;

/** Exact wrapper, equations, initialization, and named-state checks. */
public class Gewt2mu1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final String SYNTHETIC_DYR = """
            1 'USRMDL' '1' 'GEWTGCU1' 101 1 2 18 3 3
              40 0
              1.5 0.33403 0.50 0.90 2.775 1.20 1.00 0.40 0.90 10.0 0.02
              0.40 0.00 0.70 0.55 0.90 1.00 0.10 /
            1 'USRMDL' '1' 'GEWT2MU1' 103 0 1 5 4 3 0
              4.37 0.08 0.71 1.43 0.17 /
            2 'GENCLS' '1' 99999.0 0.0 /
            """;

    @Test
    void exactWrapperAttachesAndInitializesFourStates(@TempDir Path tempDir)
            throws Exception {
        Fixture fixture = load(tempDir, SYNTHETIC_DYR);
        assertEquals(12, DynamicModelCatalog.find("GEWT2MU1").orElseThrow().parameterCount());
        assertNotNull(fixture.driveTrain);
        assertEquals(4, fixture.driveTrain.getNamedStates().size());
        assertTrue(fixture.driveTrain.getNamedStates().containsKey("Shaft twist angle"));
        assertTrue(fixture.driveTrain.getNamedStates().containsKey("Generator rotor angle deviation"));
        assertEquals(fixture.generator.getP(), fixture.driveTrain.getAerodynamicPower(), 1.0e-12);
        double initialSpeed = fixture.driveTrain.getGeneratorSpeed();
        for (int index = 0; index < 20; index++) {
            assertTrue(fixture.algorithm.solveDEqnStep(true));
        }
        assertEquals(initialSpeed, fixture.driveTrain.getGeneratorSpeed(), 2.0e-8);
    }

    @Test
    void stiffnessAndModifiedEulerStepMatchIndependentEquations() {
        Gewt2mu1Data data = new Gewt2mu1Data(4.37, 0.08, 0.71, 1.43, 0.17);
        Gewt2mu1Model model = new Gewt2mu1Model(data);
        model.initialize(0.63, 1.0, 60.0);
        double ht = data.h() * data.turbineInertiaFraction();
        double hg = data.h() - ht;
        double omega0 = 2.0 * Math.PI * 60.0;
        double torsional = 2.0 * Math.PI * data.firstShaftFrequencyHz();
        double stiffness = 2.0 * ht * hg * torsional * torsional / (data.h() * omega0);
        assertEquals(stiffness, model.shaftStiffness(), 1.0e-15);

        model.setAerodynamicPower(0.67);
        double initialAngle = model.getShaftAngle();
        double initialTorque = stiffness * initialAngle;
        double dTurbine0 = (0.67 - initialTorque) / (2.0 * ht);
        double dGenerator0 = (initialTorque - 0.61) / (2.0 * hg);
        double dt = 0.002;
        double predictedTurbine = 1.0 + dt * dTurbine0;
        double predictedGenerator = 1.0 + dt * dGenerator0;
        double predictedAngle = initialAngle;
        model.step(dt, 0.61, 0);
        assertEquals(predictedTurbine, model.getTurbineSpeed(), 1.0e-15);
        assertEquals(predictedGenerator, model.getGeneratorSpeed(), 1.0e-15);
        assertEquals(predictedAngle, model.getShaftAngle(), 1.0e-15);

        double speedDifference = predictedTurbine - predictedGenerator;
        double predictedTorque = stiffness * predictedAngle;
        double dampingTorque = data.shaftDamping() * speedDifference;
        double dTurbine1 = (0.67 / predictedTurbine - predictedTorque - dampingTorque)
                / (2.0 * ht);
        double dGenerator1 = (predictedTorque + dampingTorque
                - 0.61 / predictedGenerator
                - data.damp() * (predictedGenerator - 1.0)) / (2.0 * hg);
        model.step(dt, 0.61, 1);
        assertEquals(1.0 + 0.5 * dt * (dTurbine0 + dTurbine1),
                model.getTurbineSpeed(), 1.0e-14);
        assertEquals(1.0 + 0.5 * dt * (dGenerator0 + dGenerator1),
                model.getGeneratorSpeed(), 1.0e-14);
        assertEquals(initialAngle + 0.5 * dt * omega0 * speedDifference,
                model.getShaftAngle(), 1.0e-14);
    }

    @Test
    void rejectsWrongAllocationAndInvalidConstants(@TempDir Path tempDir) throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new Gewt2mu1Data(4.0, 0.1, 1.0, 1.2, 0.2));
        Fixture fixture = load(tempDir,
                SYNTHETIC_DYR.replace("103 0 1 5 4 3 0", "103 0 1 5 3 3 0"));
        assertTrue(fixture.driveTrain == null);
    }

    private static Fixture load(Path tempDir, String dyrText) throws Exception {
        IpssCorePlugin.init();
        Path dyr = tempDir.resolve("synthetic-ge-two-mass.dyr");
        Files.writeString(dyr, dyrText);
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1.raw").toString(), dyr.toString());
        var algorithm = context.getDynSimuAlgorithm();
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
        return new Fixture(generator, generator.getDriveTrain(), algorithm);
    }

    private record Fixture(Gewtgcu1Model generator, Gewt2mu1Model driveTrain,
            DynamicSimuAlgorithm algorithm) { }
}
