package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.interpss.core.dstab.reference.PsseRunQuality;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PsseRunQualityTest {
    @TempDir Path directory;
    private static final String LOG = "PSS(R)E 36.7.0\n Initial conditions check O.K.\n";
    private static final String CSV = "time_s,Iq,V\n-.00025,0,1\n0,0,1\n.05,0,1\n"
            + ".05,.5,1.1\n.500009,0,1\n";

    @Test void retainsInitializationHistoryEventSidesAndDeclaredClockDrift() throws Exception {
        var result = audit(LOG, CSV, .5, .00001);
        assertEquals(5, result.samples());
        assertEquals(2, result.channels());
        assertEquals(1, result.repeatedTimeSamples());
        assertEquals(1, result.initializationHistorySamples());
        assertEquals(.500009, result.finalTime());
    }

    @Test void rejectsNetworkFailureEvenWithGoodInitializationAndCompleteExport() {
        for (String failure : new String[]{"Network not converged at TIME = 0.101",
                "NETWORK   NOT CONVERGED", "Network solution did not converge",
                "Network failed to converge"}) {
            assertThrows(IllegalArgumentException.class, () -> audit(LOG + failure, CSV, .5, .00001));
        }
    }

    @Test void rejectsSuspectInitializationEvenWhenAnOkayMarkerAlsoExists() {
        for (String failure : new String[]{"INITIAL CONDITIONS SUSPECT", "Suspect initial condition",
                "Initial condition check failed", "Traceback (most recent call last):"}) {
            assertThrows(IllegalArgumentException.class, () -> audit(failure + "\n" + LOG, CSV, .5, .00001));
        }
    }

    @Test void rejectsMissingInitializationEvidence() {
        assertThrows(IllegalArgumentException.class, () -> audit("", CSV, .5, .00001));
    }

    @Test void rejectsNonfiniteChannels() {
        for (String value : new String[]{"NaN", "Infinity", "-Infinity"})
            assertThrows(IllegalArgumentException.class,
                    () -> audit(LOG, CSV.replace(".5,1.1", value + ",1.1"), .5, .00001));
    }

    @Test void rejectsWrongWidthAndInvalidNumbers() {
        for (String invalid : new String[]{CSV.replace(".5,1.1", ".5"),
                CSV.replace(".5,1.1", ".5,1.1,"), CSV.replace(".5,1.1", "invalid,1.1"), CSV + "\n"})
            assertThrows(IllegalArgumentException.class, () -> audit(LOG, invalid, .5, .00001));
    }

    @Test void rejectsMissingOrAmbiguousChannelHeaders() {
        for (String header : new String[]{"time,Iq,V", "time_s,Iq,iq", "time_s,Iq,", "time_s"})
            assertThrows(IllegalArgumentException.class,
                    () -> audit(LOG, CSV.replace("time_s,Iq,V", header), .5, .00001));
    }

    @Test void rejectsDecreasingTimeAndMissingTimeZero() {
        assertThrows(IllegalArgumentException.class, () -> audit(LOG, CSV.replace(".500009", ".04"), .5, .00001));
        assertThrows(IllegalArgumentException.class, () -> audit(LOG, CSV.replace("\n0,0,1", "\n.001,0,1"), .5, .00001));
    }

    @Test void rejectsTruncatedAndOvershootingExports() {
        for (String time : new String[]{".499", ".501"})
            assertThrows(IllegalArgumentException.class, () -> audit(LOG, CSV.replace(".500009", time), .5, .00001));
        assertThrows(IllegalArgumentException.class, () -> audit(LOG, "time_s,Iq\n", .5, .00001));
    }

    @Test void rejectsInvalidDeclaredTiming() {
        for (double end : new double[]{0, -.5, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> audit(LOG, CSV, end, .00001));
        for (double tolerance : new double[]{-.001, .5, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> audit(LOG, CSV, .5, tolerance));
    }

    private PsseRunQuality.Summary audit(String log, String csv, double end, double tolerance) throws Exception {
        Path logPath = directory.resolve("native.log");
        Path csvPath = directory.resolve("native.csv");
        Files.writeString(logPath, log);
        Files.writeString(csvPath, csv);
        return PsseRunQuality.audit(logPath, csvPath, end, tolerance);
    }
}
