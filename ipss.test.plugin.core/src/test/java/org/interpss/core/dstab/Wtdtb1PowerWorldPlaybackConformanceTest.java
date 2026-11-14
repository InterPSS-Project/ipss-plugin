package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

import org.interpss.core.dstab.reference.Wtdtb1PowerWorldPlaybackReference;
import org.interpss.core.dstab.reference.Wtdtb1PowerWorldNonUnitReference;
import org.interpss.core.dstab.reference.Wtdtb1PowerWorldPlaybackReference.Profile;
import com.interpss.dstab.renewable.Wtdtb1Data;
import com.interpss.dstab.renewable.Wtdtb1Model;
import org.junit.jupiter.api.Test;

/** Exact typed WTGT_B recorded-power replay, not native WTDTB1/full-network acceptance. */
public class Wtdtb1PowerWorldPlaybackConformanceTest {
    private static final Wtdtb1Data DATA = new Wtdtb1Data(4.5, 1.5, .6, 2, .4, .1);
    private static final double[] COARSE = {1.2e-7, 8e-8, 8e-8, 8e-8, 2e-7};
    private static final double[] FINE = {6e-8, 8e-8, 8e-8, 4e-8, 6e-8};
    private static final String[] LABELS = {"Shaft angle", "Turbine speed deviation",
            "Generator speed deviation", "Rotor angle deviation", "Power filter"};

    @Test void allFiveSourceDiagramStatesMatchTypedPowerWorldAtBothSteps() {
        double[] coarse = replay(Wtdtb1PowerWorldPlaybackReference.coarse(), DATA, COARSE);
        double[] fine = replay(Wtdtb1PowerWorldPlaybackReference.fine(), DATA, FINE);
        // Absolute speed export is single precision near 1 pu: its subtraction
        // has a ~6e-8 pu rounding floor, not a guarantee of halving at every step.
        for (int state : new int[] {0, 3, 4})
            assertTrue(fine[state] < .65 * coarse[state], LABELS[state] + " convergence");
    }

    @Test void frozenPowerAndCheckpointFingerprintsRejectMutations() throws Exception {
        var coarse = Wtdtb1PowerWorldPlaybackReference.coarse();
        var fine = Wtdtb1PowerWorldPlaybackReference.fine();
        String[] bodies = {coarse.powers(), coarse.states(), fine.powers(), fine.states()};
        String[] fingerprints = {
                "edd8c8295547ab2fd9c17ce3dc7e294e948e4f455d52be20a36a39a44e97e036",
                "0e52e1f9eefb03de43693eb3fac27a4f0c5e68ed0a2c315eaeaed14da20d3ab9",
                "11dd6aee07114d0ff22832dbcc31a6b41837c8c88730f6c2825c8914db177e06",
                "b0db68c94d9d7549d03b821714afb1cd512c211c54f7b99443fd4d4ad5c17946"};
        assertEquals(fingerprints[0], coarse.powerSha256());
        assertEquals(fingerprints[1], coarse.checkpointsSha256());
        assertEquals(fingerprints[2], fine.powerSha256());
        assertEquals(fingerprints[3], fine.checkpointsSha256());
        for (int i = 0; i < bodies.length; i++) {
            byte[] bytes = bodies[i].getBytes(StandardCharsets.UTF_8);
            assertEquals(fingerprints[i], digest(bytes));
            bytes[0] ^= 1;
            assertNotEquals(fingerprints[i], digest(bytes));
        }
        assertEquals("ee33f5ec2ce0d9d0c2cd81e661055a643df145361d7b1dc2764a19ad48bed392", coarse.rawCsvSha256());
        assertEquals("a350e905d33b630481f94091bc21f233bea8557faf3578b5ab468995a5925704", fine.rawCsvSha256());
    }

    @Test void referenceRejectsWrongFrequencyUnitsAndMissingPowerFilter() {
        var reference = Wtdtb1PowerWorldPlaybackReference.coarse();
        assertThrows(AssertionError.class, () -> replay(reference,
                new Wtdtb1Data(4.5, 1.5, .6, 2.0 / 60, .4, .1), COARSE));
        assertThrows(AssertionError.class, () -> replay(reference,
                new Wtdtb1Data(4.5, 1.5, .6, 2, .4, 0), COARSE));
    }

    @Test void declaredNonUnitSpeedRetainsAllFivePowerWorldStatesWithoutAngleDrift() {
        double[] coarse = replay(Wtdtb1PowerWorldNonUnitReference.coarse(), DATA,
                new double[] {1.4e-7, 8e-8, 8e-8, 8.5e-8, 2e-7}, .97);
        double[] fine = replay(Wtdtb1PowerWorldNonUnitReference.fine(), DATA,
                new double[] {7e-8, 8e-8, 8e-8, 4e-8, 6e-8}, .97);
        for (int state : new int[] {0, 3, 4}) assertTrue(fine[state] < .65 * coarse[state]);
    }

    @Test void nonUnitFrozenInputsAndStatesRejectMutation() throws Exception {
        var coarse = Wtdtb1PowerWorldNonUnitReference.coarse();
        var fine = Wtdtb1PowerWorldNonUnitReference.fine();
        String[] bodies = {coarse.powers(), coarse.states(), fine.powers(), fine.states()};
        String[] hashes = {"8bde783bd1a3c30748e820da84b563e88bed0e0b4b960710fa2c98c0d6d533fb",
                "f4fdcafa81620b4a46c2f60891d14783bb8db4dd49283a25690db33b2bfafaef",
                "b79d8dac6390824071661367067ac367c346c496e14dbe40680dc136c4385c2e",
                "afc29874e733bb8068f4c20fdc09f1ca0736ca4ebb745a880d642c7533d00ee8"};
        assertEquals("3f5b3e9278f4ec69db11cdca52e6266e3fe6d062c21a5eaf39d2b955323c46c4", coarse.rawCsvSha256());
        assertEquals("a2e139c8136461be00bfc3b7601d803a3eba4e3e1ca0915a076bc86191560622", fine.rawCsvSha256());
        for (int i = 0; i < bodies.length; i++) {
            byte[] bytes = bodies[i].getBytes(StandardCharsets.UTF_8);
            assertEquals(hashes[i], digest(bytes));
            bytes[0] ^= 1;
            assertNotEquals(hashes[i], digest(bytes));
        }
    }

    private static double[] replay(Profile reference, Wtdtb1Data data, double[] bounds) {
        return replay(reference, data, bounds, 1);
    }
    private static double[] replay(Profile reference, Wtdtb1Data data, double[] bounds, double initialSpeed) {
        var inputs = reference.powers().lines().map(Wtdtb1PowerWorldPlaybackConformanceTest::numbers).toList();
        var checkpoints = reference.states().lines().map(Wtdtb1PowerWorldPlaybackConformanceTest::numbers).toList();
        assertEquals(reference.samples(), inputs.size());
        assertEquals(reference.checkpoints(), checkpoints.size());
        assertEquals(203, checkpoints.size(), "Every 5 ms plus both instantaneous post-event samples");
        var model = new Wtdtb1Model(data, 60);
        model.initialize(inputs.getFirst()[1], initialSpeed);
        double[] maximum = new double[5], maximumTime = new double[5];
        double[] previous = inputs.getFirst();
        int next = 0, duplicateEvents = 0;
        for (int row = 0; row < inputs.size(); row++) {
            double[] input = inputs.get(row);
            double dt = input[0] - previous[0];
            if (row > 0) {
                assertTrue(dt >= 0);
                if (dt == 0) {
                    duplicateEvents++;
                    assertTrue(input[0] == .05 || input[0] == .1);
                    double[] before = model.getNativeStates();
                    model.step(0, 0, input[1], 0);
                    if (data.tp() > 0) assertArrayEquals(before, model.getNativeStates(), 0);
                } else {
                    assertEquals(reference.step(), dt, 1e-12);
                    model.step(dt, 0, previous[1], 0);
                    model.step(dt, 0, input[1], 1);
                }
            }
            if (next < checkpoints.size() && (int)checkpoints.get(next)[0] == row) {
                double[] expected = checkpoints.get(next++);
                double[] actual = model.getNativeStates();
                var snapshot = model.getNamedStates();
                assertEquals(snapshot, model.getNamedStates());
                assertArrayEquals(actual, model.getNativeStates(), 0);
                for (int state = 0; state < 5; state++) {
                    double error = Math.abs(actual[state] - expected[state + 1]);
                    if (error > maximum[state]) {
                        maximum[state] = error;
                        maximumTime[state] = input[0];
                    }
                    assertTrue(error <= bounds[state], LABELS[state] + " at " + input[0]
                            + ": error=" + error + ", bound=" + bounds[state]);
                }
            }
            previous = input;
        }
        assertEquals(2, duplicateEvents);
        assertEquals(checkpoints.size(), next);
        assertEquals(1, previous[0], 0);
        System.out.println("WTGT_B typed power replay step=" + reference.step()
                + " max=" + Arrays.toString(maximum) + " at=" + Arrays.toString(maximumTime));
        return maximum;
    }

    private static double[] numbers(String text) {
        return Arrays.stream(text.split(",")).mapToDouble(Double::parseDouble).toArray();
    }
    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
