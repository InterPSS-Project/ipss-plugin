package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.common.exp.InterpssException;

/** Invalid layouts must not attach a partially initialized controller. */
public class Pss2bImportValidationTest {
    @BeforeAll static void setup() { IpssCorePlugin.init(); }

    @Test void standardRecordSelectsAcceptedSamplingWhileTypedDataRemainsAlgebraic(@TempDir Path folder) throws Exception {
        String record = Files.readAllLines(Path.of("testData/adpter/psse/v33/HspModels/pss2b.dyr"))
                .stream().filter(line -> line.contains("'PSS2B'")).findFirst().orElseThrow();
        Path file=folder.resolve("standard.dyr");Files.writeString(file,record+"\n");
        var builder=DStabBuilderTestFixture.createWithMachine();
        assertNotNull(new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(file.toString()));
        var model=assertInstanceOf(com.interpss.dstab.controller.pss.ieee1992pss2b.Ieee1992PSS2BStabilizer.class,
                builder.getDStabNetwork().getMachine("Bus1-mach1").getStabilizer());
        assertTrue(model.getData().isSampledInputPathOutputs());
        assertTrue(model.getData().isStandardInputLagPreparation());
        assertFalse(new com.interpss.dstab.controller.pss.ieee1992pss2b.Ieee1992PSS2BStabilizerData()
                .isSampledInputPathOutputs());
    }

    @Test void rejectMalformedStandardRecordsAndUnrealizableParameters(@TempDir Path folder) throws Exception {
        String record = Files.readAllLines(Path.of("testData/adpter/psse/v33/HspModels/pss2b.dyr"))
                .stream().filter(line -> line.contains("'PSS2B'")).findFirst().orElseThrow();
        var original = List.of(record.substring(0, record.lastIndexOf('/')).trim().split("\\s+"));
        for (String invalid : List.of("short", "long", "too-many-states", "improper", "nan", "infinite", "remote-machine")) {
            var fields = new ArrayList<>(original);
            switch (invalid) {
                case "short" -> fields.removeLast();
                case "long" -> fields.add("0");
                case "too-many-states" -> { fields.set(7, "5"); fields.set(8, "2"); }
                case "improper" -> fields.set(7, "0");
                case "nan" -> fields.set(19, "NaN");
                case "infinite" -> fields.set(26, "Infinity");
                case "remote-machine" -> fields.set(4, "2");
                default -> fail(invalid);
            }
            Path file = folder.resolve(invalid + ".dyr"); Files.writeString(file, String.join(" ", fields) + " /\n");
            var builder = DStabBuilderTestFixture.createWithMachine();
            var parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
            assertThrows(InterpssException.class, () -> parser.parseDynFile(file.toString()), invalid);
            assertNull(builder.getDStabNetwork().getMachine("Bus1-mach1").getStabilizer(), invalid);
        }
    }
}
