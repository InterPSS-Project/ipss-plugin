package org.interpss.core.adapter.psse.raw.nbreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.BaseDStabNetwork;

/** Ensures node-breaker substation records can populate a DStab network. */
class PSSE_DStab_SubstationImport_Test extends CorePluginTestSetup {
    private static final String RAW = "testData/psse/nbreaker/twoSubstations_rev35.raw";

    @Test
    void importsSubstationsIntoDStabNetwork(@TempDir Path tempDir) throws Exception {
        Path emptyDyr = tempDir.resolve("empty.dyr");
        Files.writeString(emptyDyr, "");

        BaseDStabNetwork<?, ?> network = new PSSEMultiFileLoader()
                .loadDStab(RAW, emptyDyr.toString()).getDStabilityNet();

        assertTrue(network.isNodeBreakerModel());
        assertEquals(2, network.getSubstationMap().size());
        assertEquals("S1", network.getSubstation("1").getName().trim());
        assertEquals("S2", network.getSubstation("2").getName().trim());
    }
}
