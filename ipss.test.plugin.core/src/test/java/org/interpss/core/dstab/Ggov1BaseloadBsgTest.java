package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1Governor;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.PsseDStabSolver;
import org.interpss.fadapter.psse.PsseGovernorBsgProcessor.Status;
import org.interpss.fadapter.psse.export.PSSEJsonExporter;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** GGOV1 policy regression using only the public SMIB network and DYR. */
public class Ggov1BaseloadBsgTest extends CorePluginTestSetup {
    private static final Path SMIB = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final DynamicSimuMethod METHOD = DynamicSimuMethod.MODIFIED_EULER;
    private static final double STEP = .005;
    private static final int STEPS = 2000;
    private static final double TOLERANCE = 1e-8;
    private static final double RESPONSE = 1e-4;

    @TempDir Path directory;

    static Stream<Arguments> policiesAndSpeedSteps() {
        var cases = Stream.<Arguments>builder();
        for (int baseload = 0; baseload <= 3; baseload++) {
            for (int bsg = 0; bsg <= 2; bsg++) {
                cases.add(Arguments.of(baseload, bsg, -.002));
                cases.add(Arguments.of(baseload, bsg, .002));
            }
        }
        return cases.build();
    }

    @ParameterizedTest(name = "BASLOD {0}, BSG {1}, speed step {2} pu")
    @MethodSource("policiesAndSpeedSteps")
    void travelPolicyAndShaftPowerLimitsRemainIndependent(
            int baseload, int bsg, double speedStep) throws Exception {
        var source = new PSSEDirectParser().parse(SMIB.resolve("SMIB_v33.raw").toString());
        source.getBus("Bus1").getContributeGen("1").setBaseLoadFlag(baseload);
        Path rawx = directory.resolve("smib.rawx");
        Files.writeString(rawx, new PSSEJsonExporter(source).export().toString());
        Path policy = directory.resolve("governor.bsg");
        Files.writeString(policy, "1, 'GENERATOR', 100, '1', " + bsg + "\n");

        var context = new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false)
                .loadDStab(rawx.toString(), SMIB.resolve("SMIB_v33_genrou_ggov1.dyr").toString(),
                        policy.toString());
        var machine = context.getDStabilityNet().getMachine("Bus1-mach1");
        var governor = assertInstanceOf(PsseGgov1Governor.class, machine.getGovernor());
        var algorithm = context.getDynSimuAlgorithm();
        var solver = assertInstanceOf(PsseDStabSolver.class, algorithm.getSolver());
        assertEquals(Status.REGISTERED, solver.getGovernorBlockingReport().getFirst().status());
        assertEquals(baseload, machine.getParentGen().getBaseLoadFlag());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(METHOD);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(STEP * STEPS);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());

        var report = solver.getGovernorBlockingReport().getFirst();
        double initialValve = governor.getValveStroke();
        double enteredMaximum = governor.getData().getVmax();
        assertTrue(enteredMaximum - initialValve > RESPONSE, "Fixture must have opening headroom");
        assertEquals(bsg == 0 ? Status.NORMAL : Status.APPLIED, report.status());
        assertEquals(bsg == 0 ? enteredMaximum : initialValve, governor.getEffectiveVmax(), TOLERANCE);
        if (bsg != 0) {
            assertEquals(enteredMaximum, report.oldMaximum(), TOLERANCE);
            assertEquals(initialValve, report.newMaximum(), TOLERANCE);
        }
        assertEquals(baseload, machine.getParentGen().getBaseLoadFlag());

        double initialPower = machine.getPm();
        double initialElectricalPower = machine.getPe();
        double initialAccelerationLag = governor.getAccelerationControlState();
        double minPowerChange = 0.0;
        double maxPowerChange = 0.0;
        for (int step = 0; step < STEPS; step++) {
            for (int stage = 0; stage < 2; stage++) {
                // Prescribe the same governor inputs at both stage boundaries.
                // The ordinary machine step still integrates and publishes shaft power.
                machine.setSpeed(1.0 + speedStep);
                machine.setPe(initialElectricalPower);
                assertTrue(machine.nextStep(STEP, METHOD, stage));
                double command = governor.getOutput(machine);
                double expectedPower = switch (baseload) {
                    case 1 -> Math.min(initialPower, command);
                    case 2 -> initialPower;
                    case 3 -> Math.max(initialPower, command);
                    default -> command;
                };
                assertEquals(expectedPower, machine.getPm(), TOLERANCE);
            }
            double change = machine.getPm() - initialPower;
            minPowerChange = Math.min(minPowerChange, change);
            maxPowerChange = Math.max(maxPowerChange, change);
        }

        assertTrue(governor.isActive());
        assertTrue(Math.abs(governor.getAccelerationControlState() - initialAccelerationLag) > RESPONSE,
                "The governor must integrate even when shaft power is held");
        assertEquals(bsg == 0 ? enteredMaximum : initialValve, governor.getEffectiveVmax(), TOLERANCE);
        assertEquals(enteredMaximum, governor.getData().getVmax(), TOLERANCE);
        assertEquals(baseload, machine.getParentGen().getBaseLoadFlag());

        if (baseload == 2 || (baseload == 3 && bsg != 0)) {
            assertEquals(0.0, minPowerChange, TOLERANCE);
            assertEquals(0.0, maxPowerChange, TOLERANCE);
        } else if (speedStep > 0 && baseload != 3) {
            assertTrue(minPowerChange < -RESPONSE, "Overspeed must reduce shaft power when allowed");
        } else if (speedStep < 0 && bsg == 0 && baseload != 1) {
            assertTrue(maxPowerChange > RESPONSE, "Underspeed must increase shaft power when allowed");
        }
    }
}
