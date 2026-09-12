package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;

import org.interpss.core.dstab.reference.EmbeddedCsvTrajectoryValues;
import org.interpss.core.dstab.reference.EmbeddedNativeTrajectoryValues;
import org.interpss.core.dstab.reference.PowerWorldCsvReference;
import org.junit.jupiter.api.Test;

/** Guards the source-neutral, in-code checkpoint stores used by public regression tests. */
public class ReferenceArtifactManifestIntegrityTest {
    @Test
    void embeddedCheckpointStoresAreReadableWithoutExternalArtifacts() throws Exception {
        assertFalse(EmbeddedCsvTrajectoryValues.lines("genrou-smib-line-trip.csv").isEmpty());
        assertFalse(EmbeddedNativeTrajectoryValues.lines(
                Path.of("smib-genrou-exdc2", "psse.csv")).isEmpty());
        assertFalse(PowerWorldCsvReference.read(
                Path.of("testData", "reference", "powerworld", "smib-genqec", "powerworld.csv"))
                .samples().isEmpty());
    }
}
