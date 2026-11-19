package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.ReferenceLoadModel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.controller.exc.dc4b.Dc4bExciter;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;

/**
 * Native PSS/E 36.7 full-solver trajectory contract for DC4B.
 *
 * <p>The reference was recorded with PSS/E 36.7 for the public SMIB GENROU + DC4B case:
 * a three-cycle j0.2 pu fault at bus 1, a 0.5 ms step and constant-impedance load
 * (src/test/python/psse_dc4b_probe.py). It confirms the PID non-windup limit on the
 * combined PID output (docs/dynamic-models/dc4b-combined-pid-limit.md): with the
 * earlier limit on the PI subtotal the rotor angle differed by 0.022 degrees and the
 * field voltage by 0.006 pu. PSS/E stores the PI, derivative and feedback states in
 * other coordinates, so those three are not compared.
 *
 * <p>Generated trajectory references stay in the private verification archive
 * (see .gitignore), so the test is skipped where the reference is absent, as in CI.
 */
public class Dc4bPsseSmibConformanceTest {
    private static final double STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final String REFERENCE = "/reference/psse/smib-genrou-dc4b.csv.gz";
    private static final String REFERENCE_SHA256 =
            "6617eae084accc4d8651cd266f45f0041000b5121fa04e23329ff25b00951d35";

    @Test
    void threeCycleFaultMatchesPsseBoundaryMachineAndExciterOutputs() throws Exception {
        Assumptions.assumeTrue(Dc4bPsseSmibConformanceTest.class.getResource(REFERENCE) != null,
                "PSS/E DC4B reference is only in the private verification archive");
        IpssCorePlugin.init();
        var context = ReferenceLoadModel.constantImpedanceLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(), CASE.resolve("SMIB_v33_genrou_dc4b.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "GENROU + DC4B SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", network,
                SimpleFaultCode.GROUND_3P, new Complex(0, .2), null, .05, .05), "SmibFault");
        assertTrue(algorithm.initialization(), "GENROU + DC4B SMIB initialization");

        RoundRotorMachine machine = (RoundRotorMachine) network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        Dc4bExciter exciter = (Dc4bExciter) machine.getExciter();
        double initialAngle = machine.getAngle() - referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        actual.add(row(network, machine, referenceMachine, exciter, initialAngle));
        while (algorithm.getSimuTime() < 1 - STEP / 2) {
            assertTrue(algorithm.solveDEqnStep(true));
            actual.add(row(network, machine, referenceMachine, exciter, initialAngle));
        }

        List<double[]> reference = reference();
        double[] first = reference.get(0);
        double initialPsseAngle = first[3] - first[4];
        String[] label = {"Bus1 V", "Bus2 V", "P pu", "Q pu", "relative angle", "relative speed",
                "sensed Vt", "Vr", "EField"};
        double[] maximum = new double[label.length], at = new double[label.length];
        int compared = 0;
        for (double[] expected : reference) {
            double time = expected[0];
            // Skip the history row and both sides of the two switching instants.
            if (time < 0 || Math.abs(time - .05) < STEP || Math.abs(time - .10) < STEP) continue;
            int tick = (int) Math.round(time / STEP);
            double[] row = actual.get(tick);
            double[] psse = {expected[1], expected[2], expected[5], expected[6],
                    expected[3] - expected[4] - initialPsseAngle, expected[7] - expected[8],
                    expected[9], expected[10], expected[11]};
            for (int i = 0; i < label.length; i++) {
                double error = Math.abs(row[i] - psse[i]);
                if (error > maximum[i]) { maximum[i] = error; at[i] = time; }
            }
            compared++;
        }
        assertTrue(compared > 1900, "Every regular PSS/E sample is compared");
        System.out.println("DC4B PSS/E max errors: " + Arrays.toString(maximum));
        System.out.println("DC4B PSS/E max-error times: " + Arrays.toString(at));
        // The voltage, power, speed and Vr maxima fall on the first step after a
        // switching instant; elsewhere bus voltages agree to 1e-5 pu and power to 1e-4 pu.
        double[] tolerance = {3e-4, 1e-4, 2e-4, 3e-3, 6e-4, 2.5e-6, 2.5e-3, .08, 3e-4};
        for (int i = 0; i < label.length; i++)
            assertTrue(maximum[i] <= tolerance[i], String.format(Locale.ROOT, "%s max error %.6g at %.4f exceeds %.6g",
                    label[i], maximum[i], at[i], tolerance[i]));
    }

    private static double[] row(com.interpss.dstab.BaseDStabNetwork<?, ?> network, RoundRotorMachine machine,
            Machine referenceMachine, Dc4bExciter exciter, double initialAngle) {
        Complex v = network.getBus("Bus1").getVoltage();
        Complex s = v.multiply(machine.getIgen().subtract(v.multiply(machine.getYgen())).conjugate());
        return new double[] {network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                s.getReal(), s.getImaginary(),
                Math.toDegrees(machine.getAngle() - referenceMachine.getAngle() - initialAngle),
                machine.getSpeed() - referenceMachine.getSpeed(),
                exciter.getNamedState("Sensed Vt"), exciter.getNamedState("Vr"), exciter.getNamedState("EField")};
    }

    private static List<double[]> reference() throws Exception {
        byte[] data;
        try (var stream = Dc4bPsseSmibConformanceTest.class.getResourceAsStream(REFERENCE)) {
            assertNotNull(stream, "PSS/E DC4B reference");
            try (var gzip = new GZIPInputStream(stream)) { data = gzip.readAllBytes(); }
        }
        assertEquals(REFERENCE_SHA256, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));
        var rows = new ArrayList<double[]>();
        try (var reader = new BufferedReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(data), StandardCharsets.UTF_8))) {
            assertEquals("time_s,V_BUS1,V_BUS2,MACH_ANGLE,REF_ANGLE,P_PU,Q_PU,MACH_SPEED,REF_SPEED,"
                    + "SENSED_VT,REGULATOR_VR,EXCITER_EFD", reader.readLine());
            for (String line; (line = reader.readLine()) != null;)
                rows.add(Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray());
        }
        return rows;
    }
}
