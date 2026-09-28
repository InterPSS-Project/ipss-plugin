package org.interpss.threePhase;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.interpss.IpssCorePlugin;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenDssInputHandleTest {
    @TempDir Path root;
    @BeforeAll static void initialize() { IpssCorePlugin.init(); }

    @Test void closesMasterAndRedirectedInputsAfterSuccessfulParse() throws Exception {
        Path master = master("New Circuit.test basekv=12.47 pu=1.0 phases=3 bus1=source\nRedirect lines.dss\n");
        Path lines = Files.writeString(root.resolve("lines.dss"),
                "New Line.feeder bus1=source.1.2.3 bus2=loadbus.1.2.3 phases=3 r1=0.01 x1=0.02 r0=0.03 x0=0.06 length=1 units=kft\n");
        assertTrue(new OpenDSSDataParser().parseFeederData(root.toString(), master.getFileName().toString()));
        Files.delete(master);
        Files.delete(lines);
        assertFalse(Files.exists(master));
        assertFalse(Files.exists(lines));
    }

    @Test void closesMasterAfterInvalidCircuit() throws Exception {
        Path master = master("New Circuit.test basekv=not-a-number pu=1.0 phases=3 bus1=source\n");
        assertFalse(new OpenDSSDataParser().parseFeederData(root.toString(), master.getFileName().toString()));
        Files.delete(master);
        assertFalse(Files.exists(master));
    }

    @Test void closesRedirectedFileAfterDeviceFailure() throws Exception {
        Path master = master("New Circuit.test basekv=12.47 pu=1.0 phases=3 bus1=source\nRedirect lines.dss\n");
        Path lines = Files.writeString(root.resolve("lines.dss"),
                "New Line.feeder bus1=source.1.2.3 bus2=loadbus.1.2.3 phases=3 r1=not-a-number x1=0.02 r0=0.03 x0=0.06 length=1 units=kft\n");
        assertFalse(new OpenDSSDataParser().parseFeederData(root.toString(), master.getFileName().toString()));
        Files.delete(master);
        Files.delete(lines);
        assertFalse(Files.exists(lines));
    }

    private Path master(String text) throws Exception {
        return Files.writeString(root.resolve("Master.dss"), text);
    }
}
