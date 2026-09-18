package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.interpss.core.dstab.reference.EmbeddedTrajectoryReference;
import org.junit.jupiter.api.Test;

/** Verifies representative categories in the embedded public checkpoint inventory. */
public class EmbeddedTrajectoryCoverageInventoryTest {
    @Test
    void embeddedInventoryCoversMachinesExcitersGovernorsAndStabilizers() throws Exception {
        for (String modelCase : List.of("smib-genqec", "smib-genrou-ac1c",
                "smib-genrou-tgov1", "smib-genrou-esst1a-pss2a")) {
            assertFalse(EmbeddedTrajectoryReference.embedded(modelCase).samples().isEmpty(), modelCase);
        }
    }
}
