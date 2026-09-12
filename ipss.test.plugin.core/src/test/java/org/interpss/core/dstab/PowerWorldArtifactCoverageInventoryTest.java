package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.List;

import org.interpss.core.dstab.reference.PowerWorldCsvReference;
import org.junit.jupiter.api.Test;

/** Verifies representative categories in the embedded public checkpoint inventory. */
public class PowerWorldArtifactCoverageInventoryTest {
    @Test
    void embeddedInventoryCoversMachinesExcitersGovernorsAndStabilizers() throws Exception {
        for (String modelCase : List.of("smib-genqec", "smib-genrou-ac1c",
                "smib-genrou-tgov1", "smib-genrou-esst1a-pss2a")) {
            assertFalse(PowerWorldCsvReference.read(Path.of("testData", "reference", "powerworld",
                    modelCase, "powerworld.csv")).samples().isEmpty(), modelCase);
        }
    }
}
