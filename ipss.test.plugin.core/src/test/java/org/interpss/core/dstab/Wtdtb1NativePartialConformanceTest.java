package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.interpss.core.dstab.reference.Wtdtb1NativeReference;
import org.interpss.core.dstab.reference.Wtdtb1NativeReference.Profile;
import org.interpss.dstab.renewable.Wtdtb1Model;
import org.junit.jupiter.api.Test;

/** Native 36.7 playback; non-unit integrated-coordinate precision is explicitly still open. */
public class Wtdtb1NativePartialConformanceTest {
    @Test void unitSpeedProfilesMatchAllFiveStatesAtBothSteps() {
        int profiles = 0;
        for (var profile : Wtdtb1NativeReference.profiles()) if (profile.initialSpeed() == 1) {
            replay(profile, false);
            profiles++;
        }
        assertEquals(10, profiles);
    }

    @Test void nonUnitSpeedProfilesCheckSpeedFilterAndKinematicsWithoutClaimingAbsoluteAngleParity() {
        int profiles = 0;
        for (var profile : Wtdtb1NativeReference.profiles()) if (profile.initialSpeed() != 1) {
            replay(profile, true);
            profiles++;
        }
        assertEquals(4, profiles);
    }

    @Test void frozenNativeMatrixAndBothEventSidesRejectMutation() {
        var profiles = Wtdtb1NativeReference.profiles();
        assertEquals(14, profiles.size());
        StringBuilder bundle = new StringBuilder();
        for (var profile : profiles) {
            profile.verify();
            bundle.append(profile.name()).append('|').append(profile.rawCsvSha256()).append('|')
                    .append(profile.inputsSha256()).append('|').append(profile.statesSha256()).append('\n');
            assertThrows(IllegalArgumentException.class,
                    () -> profile.verify("9" + profile.inputs().substring(1), profile.states()));
            assertThrows(IllegalArgumentException.class,
                    () -> profile.verify(profile.inputs(), "9" + profile.states().substring(1)));
        }
        assertEquals("69d8920a617600b4f397bef59f98add26e24dc4087958a327f63765673fd9ace",
                Wtdtb1NativeReference.digest(bundle.toString()));
    }

    private static void replay(Profile profile, boolean nonUnit) {
        profile.verify();
        var inputs = profile.inputs().lines().map(Wtdtb1NativePartialConformanceTest::numbers).toList();
        var checkpoints = profile.states().lines().map(Wtdtb1NativePartialConformanceTest::numbers).toList();
        assertEquals(profile.samples(), inputs.size());
        assertEquals(profile.checkpoints(), checkpoints.size());
        var model = Wtdtb1Model.forNativePsse367(profile.parameters(), 60);
        model.setAerodynamicPresent(profile.aerodynamic());
        model.initialize(profile.initialElectricalPower(), profile.initialSpeed());
        double[] initial = model.getNativeStates();
        double initialNativeWt = checkpoints.getFirst()[3], initialNativeWg = checkpoints.getFirst()[4];
        double initialShaftError = initial[0] - checkpoints.getFirst()[2];
        double[] maximum = new double[5], at = new double[5];
        double integratedGenMismatch = 0, integratedShaftMismatch = 0;
        double previousGenMismatch = 0, previousShaftMismatch = 0;
        double maxRotorDefect = 0, maxShaftDefect = 0;
        double[] previous = inputs.getFirst();
        int next = 0, lastCheckpointTick = 0, eventRows = 0;
        for (int row = 0; row < inputs.size(); row++) {
            double[] input = inputs.get(row);
            if (row == 0 || profile.isPostEvent(row)) {
                model.step(0, input[1], input[0], 0);
                if (row > 0) eventRows++;
            } else {
                // Declared native integration step, not the drifting float
                // serialization clock; actual raw clocks remain in checkpoints.
                model.step(profile.step(), previous[1], previous[0], 0);
                model.step(profile.step(), input[1], input[0], 1);
            }
            if (next < checkpoints.size() && (int)checkpoints.get(next)[0] == row) {
                double[] expected = checkpoints.get(next++), actual = model.getNativeStates();
                int tick = profile.nominalTick(row);
                double elapsed = (tick - lastCheckpointTick) * profile.step();
                double genMismatch = actual[2] - initial[2] - (expected[4] - initialNativeWg);
                double turbineMismatch = actual[1] - initial[1] - (expected[3] - initialNativeWt);
                double shaftMismatch = turbineMismatch - genMismatch;
                integratedGenMismatch += .5 * elapsed * (previousGenMismatch + genMismatch);
                integratedShaftMismatch += .5 * elapsed * (previousShaftMismatch + shaftMismatch);
                maxRotorDefect = Math.max(maxRotorDefect,
                        Math.abs(actual[3] - expected[5] - 120 * Math.PI * integratedGenMismatch));
                if (!model.isSingleMass()) maxShaftDefect = Math.max(maxShaftDefect,
                        Math.abs(actual[0] - expected[2] - initialShaftError
                                - 120 * Math.PI * integratedShaftMismatch));
                previousGenMismatch = genMismatch;
                previousShaftMismatch = shaftMismatch;
                lastCheckpointTick = tick;
                double[] bounds = bounds(profile);
                for (int state = 0; state < 5; state++) {
                    double error = Math.abs(actual[state] - expected[state + 2]);
                    if (error > maximum[state]) { maximum[state] = error; at[state] = expected[1]; }
                    // Do not hide the non-converging non-unit integrated
                    // coordinates behind wider absolute trajectory bounds.
                    // Their speed-error integral is checked separately below.
                    if (!nonUnit || state != 0 && state != 3)
                        assertTrue(error <= bounds[state], profile.name() + " state " + state
                                + " at native clock " + expected[1] + ": " + error + " > " + bounds[state]);
                }
            }
            previous = input;
        }
        assertEquals(2, eventRows);
        assertEquals(checkpoints.size(), next);
        if (nonUnit) {
            assertTrue(maxRotorDefect < 5e-7, profile.name() + " rotor kinematic defect=" + maxRotorDefect);
            assertTrue(maxShaftDefect < 5e-7, profile.name() + " shaft kinematic defect=" + maxShaftDefect);
            if (model.isSingleMass()) assertEquals(0, maximum[0], 0);
        }
        System.out.println("WTDTB1 " + profile.name() + " max=" + Arrays.toString(maximum)
                + " at=" + Arrays.toString(at) + " rotorDefect=" + maxRotorDefect
                + " shaftDefect=" + maxShaftDefect
                + (nonUnit ? "; absolute integrated-coordinate parity remains open" : ""));
    }

    private static double[] bounds(Profile profile) {
        double step = profile.step();
        boolean one = profile.parameters().htfrac() == 0;
        boolean zero = profile.parameters().tp() == 0;
        if (profile.initialSpeed() != 1)
            // Bounds belong to the initialization-accepted PFLAG=0 cases.
            // Native float-state stalling is separately measured, not fitted
            // away by changing gains or widening integrated-coordinate bounds.
            return new double[] {0, one ? 8e-9 : 3.8e-7,
                    one ? 8.5e-7 : Math.max(4.3e-7, .0014 * step), 0, 1e-12};
        if (one) return new double[] {1e-12, 1e-12, (zero ? .00165 : .00048) * step,
                zero ? .0145 * step : 2.3e-6, zero ? 1e-12 : .04 * step};
        if (zero) return new double[] {1.3e-6, .0008 * step, .0012 * step, 5e-7, 1e-12};
        return new double[] {2e-6, 3e-8, .0012 * step, profile.aerodynamic() ? 1.2e-6 : 3e-6,
                profile.aerodynamic() ? 1e-12 : .04 * step};
    }
    private static double[] numbers(String text) {
        return Arrays.stream(text.split(",")).mapToDouble(Double::parseDouble).toArray();
    }
}
