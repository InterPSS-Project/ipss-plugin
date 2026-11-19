package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.interpss.core.dstab.reference.EmbeddedCsvTrajectoryValues;
import org.interpss.core.dstab.reference.EmbeddedNativeTrajectoryValues;
import org.interpss.core.dstab.reference.EmbeddedTrajectoryReference;
import org.junit.jupiter.api.Test;

/** Guards the source-neutral, in-code checkpoint stores used by public regression tests. */
public class ReferenceArtifactManifestIntegrityTest {
    @Test
    void everyNativeCheckpointMatchesItsFixedFingerprint() {
        assertEquals(63, EmbeddedNativeTrajectoryValues.checkpointPaths().size());
        for (String key : EmbeddedNativeTrajectoryValues.checkpointPaths()) {
            Path path = Path.of(key);
            byte[] checkpoint = EmbeddedNativeTrajectoryValues.bytes(path);
            EmbeddedNativeTrajectoryValues.verifyCheckpoint(path, checkpoint);
            // Existing trajectory tests use this API; its result must stay fixed
            // when the supplied data are damaged.
            String expected = EmbeddedNativeTrajectoryValues.manifest(
                    path.resolveSibling("manifest.json"));
            assertEquals(64, expected.length(), key);
            checkpoint[checkpoint.length - 1] ^= 1;
            assertThrows(IllegalArgumentException.class,
                    () -> EmbeddedNativeTrajectoryValues.verifyCheckpoint(path, checkpoint), key);
            assertEquals(expected, EmbeddedNativeTrajectoryValues.manifest(
                    path.resolveSibling("manifest.json")), key);
        }
    }

    @Test
    void unknownNativeCheckpointCannotAcquireAComputedFingerprint() {
        assertThrows(IllegalArgumentException.class,
                () -> EmbeddedNativeTrajectoryValues.verifyCheckpoint(
                        Path.of("unknown", "psse.csv"), new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> EmbeddedNativeTrajectoryValues.manifest(
                        Path.of("unknown", "manifest.json")));
    }

    @Test
    void zeroTimeNativeFixturesMatchTheirManifest() throws Exception {
        for(String model:new String[]{"SCRX","ESST4B","AC1C","AC2C","AC3C","AC7B","REXSYS","GASTWDD","GAST2AD"}) {
            byte[] bytes=org.interpss.core.dstab.reference.EmbeddedZeroTimeBoundaryValues.bytes(model);
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            org.junit.jupiter.api.Assertions.assertEquals(
                    org.interpss.core.dstab.reference.EmbeddedZeroTimeBoundaryValues.sha256(model),hash,model);
        }
    }

    @Test
    void embeddedCheckpointStoresAreReadableWithoutExternalArtifacts() throws Exception {
        assertFalse(EmbeddedCsvTrajectoryValues.lines("genrou-smib-line-trip.csv").isEmpty());
        assertFalse(EmbeddedNativeTrajectoryValues.lines(
                Path.of("smib-genrou-exdc2", "psse.csv")).isEmpty());
        assertFalse(EmbeddedTrajectoryReference.embedded("smib-genqec")
                .samples().isEmpty());
    }
}
