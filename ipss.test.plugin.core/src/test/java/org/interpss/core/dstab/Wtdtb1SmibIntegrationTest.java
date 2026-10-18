package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.Wtdtb1Model;
import org.interpss.core.dstab.reference.Wtdtb1NativeReference;
import org.interpss.core.dstab.reference.Wtdtb1NativeReference.Profile;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Full-solver integration/lifecycle checks; not a declaration of native network parity. */
public class Wtdtb1SmibIntegrationTest extends CorePluginTestSetup {
    private static final Path CASE = Path.of("testData/adpter/psse/v33/renewable");

    @Test void flatRunRetainsInitializedDrivetrainAndVoltage() throws Exception {
        for (double step : new double[] {.0005, .00025}) run(false, false, step);
    }

    @Test void threeCycleFaultExcitesShaftAndSpeedAndClears() throws Exception {
        double[] coarse = run(true, false, .0005);
        double[] fine = run(true, false, .00025);
        for (int state = 0; state < 5; state++)
            assertTrue(fine[state] < .65 * coarse[state], "Coupled state " + state + " step convergence");
    }

    @Test void generatorTripHoldsTheAttachedDrivetrainStates() throws Exception {
        for (double step : new double[] {.0005, .00025}) run(true, true, step);
    }

    private static double[] run(boolean fault, boolean trip, double step) throws Exception {
        Profile reference = fault && !trip ? Wtdtb1NativeReference.profiles().stream()
                .filter(profile -> profile.name().equals(step == .0005 ? "two-coarse" : "two-fine"))
                .findFirst().orElseThrow() : null;
        int lastTick = reference == null ? (int)Math.round(1 / step)
                : reference.nominalTick(reference.samples() - 1);
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("regca_reeca_repca_bus1062.raw").toString(),
                CASE.resolve("regca_reeca_wtdtb1_bus1062.dyr").toString());
        var network = context.getDStabilityNet();
        network.setAllowGenWithoutMach(true);
        var algorithm = context.getDynSimuAlgorithm();
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(step);
        algorithm.setTotalSimuTimeSec(lastTick * step);
        algorithm.setSimuOutputHandler(new StateMonitor());
        if (fault) network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus2", network, SimpleFaultCode.GROUND_3P, new Complex(0, 1), null, .05, .05), "WTDTB1-fault");
        if (trip) network.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent(
                "Bus1", "1", network, .15), "WTDTB1-trip");
        assertTrue(algorithm.initialization());
        var gen = (DStabGen)network.getBus("Bus1").getContributeGen("1");
        var converter = (Regca1Model)gen.getDynamicGenDevice();
        var model = converter.getReeca1Controller().getWindControlStack().getTorsionalEmulation();
        assertNotNull(model);
        assertEquals(Wtdtb1Model.EquationConvention.PSSE_36_7, model.getEquationConvention());
        assertEquals(model.getNamedStates().get("Shaft Twist Angle"),
                converter.getNamedStates().get("WTDTB1.Shaft Twist Angle"));
        assertThrows(UnsupportedOperationException.class,
                () -> converter.getNamedStates().put("extra", 0.0));
        double[] initial = model.getNativeStates();
        double initialVoltage = network.getBus("Bus1").getVoltageMag();
        double minimumVoltage = initialVoltage, maximumSpeedDeviation = 0, maximumShaftDeviation = 0;
        double[] frozen = null;
        var rows = new ArrayList<String>();
        var observed = new ArrayList<double[]>();
        rows.add("time_s,shaft,wt_dev,wg_dev,angle_dev,pe_filter,v1,v2,v3,p_pu,q_pu");
        for (int tick = 0; tick <= lastTick; tick++) {
            if (tick > 0) assertTrue(algorithm.solveDEqnStep(true), "Step " + tick);
            double[] states = model.getNativeStates();
            for (double value : states) assertTrue(Double.isFinite(value));
            var electrical = converter.getNamedStates();
            double voltage = network.getBus("Bus1").getVoltageMag();
            double[] signals = Arrays.copyOf(states, 10);
            signals[5] = voltage;
            signals[6] = network.getBus("Bus2").getVoltageMag();
            signals[7] = network.getBus("Bus3").getVoltageMag();
            signals[8] = electrical.get("Active Power");
            signals[9] = electrical.get("Reactive Power");
            observed.add(signals);
            rows.add(algorithm.getSimuTime() + "," + Arrays.toString(states).replace("[", "").replace("]", "")
                    + "," + voltage + "," + network.getBus("Bus2").getVoltageMag()
                    + "," + network.getBus("Bus3").getVoltageMag() + ","
                    + electrical.get("Active Power") + "," + electrical.get("Reactive Power"));
            if (algorithm.getSimuTime() >= .16 && trip) {
                if (frozen == null) frozen = states;
                else assertArrayEquals(frozen, states, 0, "Tripped parent must not advance wind states");
            }
            if (!fault) {
                assertArrayEquals(initial, states, 1e-10);
                assertEquals(initialVoltage, voltage, 1e-9);
            }
            if (!trip || algorithm.getSimuTime() < .15) {
                minimumVoltage = Math.min(minimumVoltage, voltage);
                maximumSpeedDeviation = Math.max(maximumSpeedDeviation, Math.abs(states[2]));
                maximumShaftDeviation = Math.max(maximumShaftDeviation, Math.abs(states[0] - initial[0]));
            }
        }
        if (fault) {
            assertTrue(minimumVoltage < initialVoltage - .001);
            assertTrue(maximumSpeedDeviation > 1e-6);
            assertTrue(maximumShaftDeviation > 1e-5);
            if (!trip) assertTrue(network.getBus("Bus1").getVoltageMag() > minimumVoltage + .001);
        }
        if (trip) {
            assertNotNull(frozen);
            assertFalse(gen.isActive());
            assertFalse(converter.isActive());
        }
        // Keep reproducible trajectories local to this test module, without
        // reading or changing JVM-wide configuration shared by other tests.
        Path directory = Path.of("target", "wtdtb1-smib");
        Files.createDirectories(directory);
        String profile = trip ? "trip" : fault ? "fault" : "flat";
        Files.write(directory.resolve(profile + "-" + step + ".csv"), rows);
        return reference == null ? null : assertNativeFaultEnvelope(reference, observed);
    }

    private static double[] assertNativeFaultEnvelope(Profile reference, ArrayList<double[]> observed) {
        double[] maximum = new double[10], at = new double[10], unrepresentedPostMaximum = new double[5];
        int instantaneousPosts = 0;
        for (String line : reference.states().strip().split("\\R")) {
            double[] expected = Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();
            int row = (int)expected[0];
            double[] actual = observed.get(reference.nominalTick(row));
            boolean post = reference.isPostEvent(row);
            if (post) instantaneousPosts++;
            for (int channel = 0; channel < 10; channel++) {
                double error = Math.abs(actual[channel] - expected[channel + 2]);
                if (post && channel >= 5) {
                    // InterPSS emits the pre-event network endpoint, not a
                    // second instantaneous algebraic export. Preserve/report
                    // this unaccepted side; all five continuous states count.
                    unrepresentedPostMaximum[channel - 5] = Math.max(unrepresentedPostMaximum[channel - 5], error);
                } else if (error > maximum[channel]) {
                    maximum[channel] = error;
                    at[channel] = expected[1];
                }
            }
        }
        assertEquals(2, instantaneousPosts);
        double[] bounds = reference.step() == .0005
                ? new double[] {5.3e-5, 7e-7, 1.8e-6, 3.3e-5, 5e-5, 6e-5, 2.2e-5, 4e-6, 7.5e-5, 2.5e-6}
                : new double[] {2.8e-5, 3.5e-7, 9e-7, 1.75e-5, 2.5e-5, 6e-5, 2.2e-5, 4e-6, 4e-5, 2.5e-6};
        for (int channel = 0; channel < maximum.length; channel++)
            assertTrue(maximum[channel] <= bounds[channel], reference.name() + " coupled channel " + channel
                    + ": " + maximum[channel] + " > " + bounds[channel]);
        System.out.println("WTDTB1 coupled " + reference.name() + " max=" + Arrays.toString(maximum)
                + " at=" + Arrays.toString(at) + "; instantaneous network-post sides remain unaccepted: "
                + Arrays.toString(unrepresentedPostMaximum));
        return maximum;
    }
}
