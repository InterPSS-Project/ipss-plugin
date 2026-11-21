package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.stream.Stream;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.dstab.dynLoad.impl.Cmldblu1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.interpss.dstab.dynLoad.impl.InductionMotorImpl;
import org.interpss.dstab.dynLoad.impl.LD1PACImpl;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Composite-load leaf devices must actually advance in each supported solver. */
public class Cmldblu1SharedSmibTest {
    static final Path CASE = Path.of("testData/adpter/psse/v33/HspModels");
    @BeforeAll static void setup() {
        IpssCorePlugin.init();
        IpssCorePlugin.configureSparseSolver("java-klu");
    }

    static Stream<Arguments> profiles() {
        return Stream.of("cmldblu1", "cmldblu1-mixed", "cmldblu1-electronic").flatMap(fixture -> Stream.of(false, true)
                .flatMap(staggered -> Stream.of(false, true).flatMap(flat -> Stream.of(.0005, .00025)
                        .map(h -> Arguments.of(fixture, staggered, flat, h)))));
    }
    @ParameterizedTest @MethodSource("profiles")
    void completeFlatAndFaultRecordings(String fixture, boolean staggered, boolean flat, double h) throws Exception {
        var recording = Cmldblu1SharedRecording.simulate(fixture, staggered, flat, h);
        Path output = Files.createTempDirectory(Path.of("target"), "cmldblu1-shared-");
        String name = fixture + "-" + (staggered ? "staggered" : "euler") + "-"
                + (flat ? "flat" : "fault") + "-h" + h + ".csv.gz";
        recording.write(output.resolve(name));
        System.out.println("CMLDBLU1 recording " + output.getFileName() + "/" + name);
        for (var row : recording.frames) if (flat) {
            for (String key : new String[] {"V1", "V2", "V3", "ANGLE_DEG", "Load.V"})
                assertEquals(recording.initial.values().get(key), row.values().get(key), 1e-5, key);
        }
        var motor = (InductionMotorImpl) recording.model.getInductionMotorA();
        var compressor = (LD1PACImpl) recording.model.get1PhaseACMotor();
        if (motor != null) assertEquals(staggered, motor.getStaggeredEvaluationCount() > 0);
        for (var child : new org.interpss.dstab.dynLoad.InductionMotor[] {
                recording.model.getInductionMotorB(), recording.model.getInductionMotorC() })
            if (child != null) assertEquals(staggered, ((InductionMotorImpl) child).getStaggeredEvaluationCount() > 0);
        if (compressor != null) {
            assertEquals(staggered, compressor.getStaggeredEvaluationCount() > 0);
            assertEquals(.5, compressor.getIntegrationHistories().get("AcceptedTime"), 1e-12,
                    "Accepted time advances once per step, never once per RHS evaluation");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void expandedMotorElectricalStatesRespondToTheFault(boolean staggered) throws Exception {
        var loader = new PSSEMultiFileLoader();
        var context = loader.loadDStab(CASE.resolve("load-network.raw").toString(),
                CASE.resolve("cmldblu1.dyr").toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());
        var algorithm = context.getDynSimuAlgorithm();
        var net = context.getDStabilityNet();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setTotalSimuTimeSec(.15);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        if (staggered) algorithm.setSolver(new StaggeredEulerSolver(algorithm));
        net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2", net,
                SimpleFaultCode.GROUND_3P, new Complex(0, .2), null, .05, .05), "fault");
        assertTrue(algorithm.initialization());
        var model = assertInstanceOf(Cmldblu1Model.class, net.getDStabBus("Bus2").getInfoOnlyDynModel());
        var motor = model.getInductionMotorA();
        var initialEp = motor.getEp();
        double maximumChange = 0;
        while (algorithm.getSimuTime() < .15 - .00025) {
            assertTrue(algorithm.solveDEqnStep(true));
            maximumChange = Math.max(maximumChange, motor.getEp().subtract(initialEp).abs());
        }
        System.out.println("CMLDBLU1 " + (staggered ? "staggered" : "modified Euler")
                + " maximum motor E-prime change = " + maximumChange);
        assertTrue(maximumChange > .01, "Expanded motor electrical states must not be silently frozen");
    }
}
