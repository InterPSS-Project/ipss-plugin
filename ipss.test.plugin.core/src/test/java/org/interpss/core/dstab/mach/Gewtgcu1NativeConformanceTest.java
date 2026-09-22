package org.interpss.core.dstab.mach;

import org.interpss.core.dstab.reference.EmbeddedNativeTrajectoryValues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Gewtgcu1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Prescribed-voltage comparison against the independent vendor implementation. */
public class Gewtgcu1NativeConformanceTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of("testData", "reference", "psse",
            "smib-gewtgcu1", "psse.csv");

    @Test
    void prescribedVoltageMatchesAllThreePublishedStates() throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(EmbeddedNativeTrajectoryValues.bytes(REFERENCE)));
        assertTrue(EmbeddedNativeTrajectoryValues.manifest(REFERENCE.resolveSibling("manifest.json")).contains(hash));
        List<String> lines = EmbeddedNativeTrajectoryValues.lines(REFERENCE);
        assertEquals(804, lines.size());
        String[] headings = lines.get(0).split(",");
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < headings.length; i++) columns.put(headings[i], i);

        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1.raw").toString(),
                CASE.resolve("SMIB_v33_gewtgcu1.dyr").toString());
        var network = context.getDStabilityNet();
        DStabGen gen = (DStabGen) network.getBus("Bus1").getContributeGen("1");
        Gewtgcu1Model model = assertInstanceOf(Gewtgcu1Model.class,
                gen.getDynamicGenDevice());
        String[] initial = lines.get(1).split(",");
        var bus = network.getBus("Bus1");
        bus.setVoltage(new Complex(value(initial, columns, "REACTIVE_STATE"), 0.0));
        assertTrue(model.initStates(bus));

        double[] maximum = new double[3];
        double[] maximumTime = new double[3];
        double[] maximumActual = new double[3];
        double[] maximumExpected = new double[3];
        String[] previous = initial;
        double previousTime = value(initial, columns, "time_s");
        for (String line : lines.subList(2, lines.size())) {
            String[] row = line.split(",");
            double time = value(row, columns, "time_s");
            double dt = time - previousTime;
            if (dt > 1.0e-7) {
                bus.setVoltage(new Complex(value(previous, columns, "V_BUS1"), 0.0));
                assertTrue(model.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 0));
                bus.setVoltage(new Complex(value(row, columns, "V_BUS1"), 0.0));
                assertTrue(model.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 1));
                assertTrue(model.afterStep(dt));
            }
            double[] actual = {model.getActiveCurrentState(), model.getReactiveState(),
                    model.getFilteredVoltageState()};
            double[] expected = {value(row, columns, "IP_STATE"),
                    value(row, columns, "REACTIVE_STATE"), value(row, columns, "V_FILTER")};
            for (int i = 0; i < 3; i++) {
                double error = Math.abs(actual[i] - expected[i]);
                if (error > maximum[i]) {
                    maximum[i] = error;
                    maximumTime[i] = time;
                    maximumActual[i] = actual[i];
                    maximumExpected[i] = expected[i];
                }
            }
            previous = row;
            previousTime = time;
        }
        System.out.println("GEWTGCU1 native state max errors: "
                + java.util.Arrays.toString(maximum) + " at "
                + java.util.Arrays.toString(maximumTime) + " actual "
                + java.util.Arrays.toString(maximumActual) + " expected "
                + java.util.Arrays.toString(maximumExpected));
        assertTrue(maximum[0] < 0.0026, java.util.Arrays.toString(maximum));
        assertTrue(maximum[1] < 1.0e-12, java.util.Arrays.toString(maximum));
        assertTrue(maximum[2] < 0.00057, java.util.Arrays.toString(maximum));
    }

    private static double value(String[] row, Map<String, Integer> columns, String name) {
        return Double.parseDouble(row[columns.get(name)]);
    }
}
