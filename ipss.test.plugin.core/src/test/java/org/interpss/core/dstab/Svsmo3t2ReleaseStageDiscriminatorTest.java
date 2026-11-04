package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.svc.Svsmo3t2Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Independent release-interval recurrence, not a full-trajectory acceptance test. */
public class Svsmo3t2ReleaseStageDiscriminatorTest extends CorePluginTestSetup {
    private static final double H = .0005;

    @Test void heldDerivativeExplainsReleaseIncrementWithoutANetworkCorrection() throws Exception {
        Path root = Svsmo3t2LimiterSmibTest.referenceRoot().resolve("short-overvoltage-block");
        var manifest = new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
        var files = manifest.get("files").fields();
        while (files.hasNext()) {
            var entry = files.next();
            Path file = root.resolve(entry.getKey()).normalize();
            assertTrue(file.startsWith(root));
            assertEquals(entry.getValue().asText(), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
        }
        var lines = Files.readAllLines(root.resolve("reference.csv"));
        var names = Arrays.asList(lines.getFirst().split(","));
        var rows = lines.stream().skip(1).map(l -> Arrays.stream(l.split(",")).mapToDouble(Double::parseDouble).toArray()).toList();
        assertEquals(605, rows.size(), "Retain every original sample, including event duplicates");
        for (var row : rows) for (double value : row) assertTrue(Double.isFinite(value));
        int current = names.indexOf("VAR_0"), voltage = names.indexOf("V_3"), reference = names.indexOf("VAR_6");
        int release = -1;
        for (int i = 2; i < rows.size(); i++)
            if (rows.get(i - 1)[current] == 0 && rows.get(i)[current] > 0 && rows.get(i)[0] > .055) { release = i; break; }
        assertTrue(release > 2);
        var older = rows.get(release - 2); var left = rows.get(release - 1); var right = rows.get(release);
        assertEquals(.0755, right[0], 2e-7);
        var x = states(left, names); var old = states(older, names);
        double vref = left[reference];
        // This profile has zero sensor numerator and a unity transient gain.
        // The published coordinates therefore equal numerical storage here.
        var model = seeded(root, x, left[voltage], vref);
        var data = model.getData();
        assertEquals(0, data.tc1(), 0); assertEquals(data.tc2(), data.tb2(), 0);
        assertEquals(0, data.slowResetFlag()); assertEquals(0, data.deadband(), 0);
        assertEquals(20, data.kp(), 0); assertEquals(5, data.ki(), 0);
        assertEquals(.05, data.tb1(), 0); assertEquals(.01, data.firingTime(), 0);
        assertEquals(.0125, data.xc0(), 0);
        var first = rates(x, left[voltage], vref, false);
        var heldPrevious = rates(old, older[voltage], vref, true);
        double[] nativeTwoStep = new double[6];
        for (int i = 0; i < 6; i++) nativeTwoStep[i] = x[i] + H * (1.5 * first[i] - .5 * heldPrevious[i]);
        assertArrayEquals(states(right, names), nativeTwoStep, 2e-7,
                "Independent state history predicts the source release endpoint");
        double[] exposed = new double[6]; model.evaluateStaggered(H, exposed, 0);
        assertArrayEquals(first, exposed, 2e-12);
        model.applyStaggeredState(nativeTwoStep, 0, H);
        assertArrayEquals(nativeTwoStep, numerical(model), 2e-12);

        double[] predicted = new double[6], heun = new double[6];
        for (int i = 0; i < 6; i++) predicted[i] = x[i] + H * first[i];
        var second = rates(predicted, right[voltage], vref, false);
        for (int i = 0; i < 6; i++) heun[i] = x[i] + .5 * H * (first[i] + second[i]);
        var coupledInput = seeded(root, x, left[voltage], vref);
        assertTrue(coupledInput.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, 0));
        coupledInput.getDStabBus().setVoltage(new Complex(right[voltage], 0));
        assertTrue(coupledInput.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, 1));
        assertArrayEquals(heun, numerical(coupledInput), 2e-12, "Exact Heun recurrence remains unchanged");
        var heldInput = seeded(root, x, left[voltage], vref);
        assertTrue(heldInput.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(heldInput.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, 1));
        assertEquals(numerical(coupledInput)[2], numerical(heldInput)[2], 0,
                "Changing only corrector network voltage cannot alter this release lag increment");
        assertEquals(.5 * H / .05 * (right[voltage] - left[voltage]),
                numerical(coupledInput)[0] - numerical(heldInput)[0], 2e-12);
        double methodGap = nativeTwoStep[2] - heun[2];
        double holdContribution = -.5 * H * heldPrevious[2];
        double stageContribution = .5 * H * (2 * first[2] - second[2]);
        assertEquals(methodGap, holdContribution + stageContribution, 2e-12);
        assertTrue(methodGap > .01, "The two stage contracts are discriminated, not conflated");
        System.out.println("SVS_RELEASE_STAGE native=" + states(right,names)[2] + " twoStep=" + nativeTwoStep[2]
                + " heun=" + heun[2] + " methodGap=" + methodGap
                + " retainedHoldContribution=" + holdContribution
                + " derivativeWeightContribution=" + stageContribution);
    }

    private static double[] rates(double[] x, double voltage, double reference, boolean held) {
        double error = reference - x[0] - .0125 * x[2];
        double command = held ? 0 : 20 * error + x[1];
        assertTrue(Math.abs(command) < 1.25, "Release current limit is inactive");
        return new double[]{(voltage-x[0])/.05, held?0:5*error, (command-x[2])/.01, 0, 0, (error-x[5])/.01};
    }
    private static double[] states(double[] row, List<String> names) {
        var result = new double[6];
        for (int i=0;i<6;i++) result[i]=row[names.indexOf("STATE_"+i)];
        return result;
    }
    private static double[] numerical(Svsmo3t2Model model) {
        double[] values = new double[6]; model.readStaggeredState(values,0); return values;
    }
    private static Svsmo3t2Model seeded(Path root, double[] states, double voltage, double reference) throws Exception {
        var context = new PSSEMultiFileLoader().loadDStab(root.resolve("smib.raw").toString(),root.resolve("smib.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm();
        algorithm.getAclfAlgorithm().getLfAdjAlgo().setLocalContinuousSvcAsPv(true);
        algorithm.getAclfAlgorithm().setTolerance(1e-10); assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuOutputHandler(new StateMonitor()); assertTrue(algorithm.initialization());
        var bus = context.getDStabilityNet().getDStabBus("Bus3");
        var model = (Svsmo3t2Model)bus.getDynamicBusDeviceList().stream().filter(Svsmo3t2Model.class::isInstance).findFirst().orElseThrow();
        bus.setVoltage(new Complex(voltage,0)); model.setScheduledVoltage(reference);
        model.applyStaggeredState(states,0,H); return model;
    }
}
