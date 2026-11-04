package org.interpss.core.adapter.psse.raw.nbreaker;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;

/** Independent converter-only voltage-control and minimum-angle qualification.
 * Capacitor commutation and full-network reference equivalence remain separate. */
@Tag("extended")
class NodebreakerConverterAuditTest extends CorePluginTestSetup {
    private static final Path ARCHIVE=Path.of("testData/private/lcc-gamma-limit-20261104/reference.zip");
    private static final String PREFIX="isolated-lcc-loadflow-20261104-zero-xcap-continued-equipment-converter-only-";
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(strings={"","tap-off-","inverter-meter-","tap-off-inverter-meter-"})
    void independentVoltageControlAtBothMeteringEnds(String profile)throws Exception {
        assertEquals("5c6177d0a66ebaaf7035851f9c5ca49c117aad1ff277a27d38606ba007b291b9",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
        try(var archive=new ZipFile(ARCHIVE.toFile())) {
            String a=PREFIX+profile+"a/",b=PREFIX+profile+"b/";
            var mapper=new ObjectMapper();
            for(String entry:new String[]{"equipment.json","solved.json"})
                assertArrayEquals(archive.getInputStream(archive.getEntry(a+entry)).readAllBytes(),
                        archive.getInputStream(archive.getEntry(b+entry)).readAllBytes(),"independent cold repetition");
            for(String dir:new String[]{a,b})
                assertEquals("complete",mapper.readTree(archive.getInputStream(archive.getEntry(dir+"manifest.json"))).get("status").asText());
            var reference=mapper.readTree(archive.getInputStream(archive.getEntry(a+"equipment.json")));
            Path input=temporary.resolve("network.raw");
            Files.copy(archive.getInputStream(archive.getEntry(a+"network.raw")),input);
            var network=new PSSEDirectParser().parse(input.toString());
            var algorithm=LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network);
            var controls=algorithm.getLfAdjAlgo().getVoltAdjConfig();
            controls.setXfrTapControl(false);controls.setHvdcTapControl(!profile.contains("tap-off"));
            algorithm.getLfAdjAlgo().getPowerAdjConfig().setPsXfrPControl(false);
            algorithm.getNetAdjAlgo().setAreaInterchangeControlEnabled(false);
            assertTrue(algorithm.loadflow());
            var line=(HvdcLine2TLCC<?>)network.getSpecialBranchList().get(0);
            for(var converter:java.util.List.of(line.getRectifier(),line.getInverter())) {
                var expected=reference.get(converter.getBus().getId().substring(3));
                assertEquals(expected.get("DCCUR").asDouble(),converter.getIdc(),.006,"DC current A");
                assertEquals(expected.get("KVDC").asDouble()*1000,converter.getDcVoltage(),.2,"DC voltage V");
                assertEquals(expected.get("TAP").asDouble(),converter.getXformerTapSetting(),1e-6,"tap");
            }
            assertEquals(reference.get("3021").get("ANGLE").asDouble(),line.getInverter().getFiringAng(),1e-4,"inverter angle degrees");
            // These references retain finite converter resistance. Full AC P/Q and
            // rectifier-angle parity require that independent remaining model work.
        }
    }
}
