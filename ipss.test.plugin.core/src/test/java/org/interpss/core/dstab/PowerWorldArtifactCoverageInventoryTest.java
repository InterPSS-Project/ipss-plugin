package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.fadapter.psse.dyr.PowerWorldArtifactCoverageInventory;
import org.interpss.fadapter.psse.dyr.PowerWorldArtifactCoverageInventory.Row;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

public class PowerWorldArtifactCoverageInventoryTest {
    @Test
    void validatesManifestsAndReportsApprovedModelGaps() throws Exception {
        var report = PowerWorldArtifactCoverageInventory.scan(repositoryRoot());

        assertEquals(62, report.completedArtifactCount());
        assertEquals(3, report.categories().get("SYNCHRONOUS_MACHINE").approvedPsseRows());
        assertEquals(3, report.categories().get("SYNCHRONOUS_MACHINE").exactLoadableRows());
        assertEquals(2, report.categories().get("SYNCHRONOUS_MACHINE").rowsWithDirectArtifact());
        assertEquals(64, report.categories().get("EXCITER").approvedPsseRows());
        assertEquals(64, report.categories().get("EXCITER").exactLoadableRows());
        assertEquals(40, report.categories().get("EXCITER").rowsWithDirectArtifact());
        assertEquals(24, report.categories().get("GOVERNOR").approvedPsseRows());
        assertEquals(24, report.categories().get("GOVERNOR").exactLoadableRows());
        assertEquals(18, report.categories().get("GOVERNOR").rowsWithDirectArtifact());
        assertEquals(15, report.categories().get("STABILIZER").approvedPsseRows());
        assertEquals(13, report.categories().get("STABILIZER").exactLoadableRows());
        assertEquals(1, report.categories().get("STABILIZER").rowsWithDirectArtifact());
        assertTrue(row(report, "GENROU").hasDirectArtifact());
        assertTrue(row(report, "GENQEC").hasDirectArtifact());
        assertFalse(row(report, "GENQEJ").hasDirectArtifact());
        assertTrue(row(report, "ESDC1A").hasDirectArtifact());
        assertTrue(row(report, "ESDC2A").hasDirectArtifact());
        assertTrue(row(report, "ESDC3A").hasDirectArtifact());
        assertTrue(row(report, "PSS2A").hasDirectArtifact());
        assertTrue(row(report, "HYGOV2D").hasDirectArtifact());
        assertTrue(row(report, "HYGOVD").hasDirectArtifact());
        assertTrue(row(report, "TGOV1").hasDirectArtifact());
        assertTrue(row(report, "TGOV1D").hasDirectArtifact());
        assertTrue(row(report, "GASTD").hasDirectArtifact());
        assertTrue(row(report, "GAST2AD").hasDirectArtifact());
        assertTrue(row(report, "GASTWDD").hasDirectArtifact());
        assertTrue(row(report, "DEGOV1D").hasDirectArtifact());
        assertTrue(row(report, "TGOV3D").hasDirectArtifact());
        assertTrue(row(report, "GGOV1D").hasDirectArtifact());
        assertTrue(row(report, "IEEEG3D").hasDirectArtifact());
        assertTrue(row(report, "HYGOV4").hasDirectArtifact());
        assertTrue(row(report, "HYGOVD").isExactLoadable(),
                "HYGOVD needs an artifact, not another runtime implementation");

        Path output = Path.of("target", "powerworld-artifact-coverage");
        report.write(output);
        Path json = output.resolve("powerworld-artifact-coverage.json");
        Path markdown = output.resolve("powerworld-artifact-coverage.md");
        assertEquals(report.toJson(), Files.readString(json));
        assertEquals(report.toMarkdown(), Files.readString(markdown));
        assertTrue(JsonParser.parseString(Files.readString(json)).isJsonObject());
        assertFalse(Files.readString(markdown).contains("| GOVERNOR | HYGOVD |"));
    }

    private static Row row(PowerWorldArtifactCoverageInventory.Report report, String name) {
        return report.rows().stream().filter(row -> row.approvedName().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError("Missing approved row " + name));
    }

    private static Path repositoryRoot() {
        String configured = System.getProperty("maven.multiModuleProjectDirectory");
        Path root = configured == null || configured.isBlank()
                ? Path.of("..").toAbsolutePath().normalize()
                : Path.of(configured).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(root.resolve("pom.xml")),
                "Cannot locate repository root: " + root);
        return root;
    }
}
