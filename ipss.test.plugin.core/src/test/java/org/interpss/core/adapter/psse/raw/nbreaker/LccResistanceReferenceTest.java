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

/** Independent finite-resistance DC boundary and AC power qualification. */
@Tag("extended")
class LccResistanceReferenceTest extends CorePluginTestSetup {
    private static final Path ARCHIVE=Path.of("testData/private/lcc-resistance-20261104/reference.zip");
    private static final String PREFIX="lcc-resistance-reference-20261104/";
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(strings={"rectifier-meter-zero","rectifier-meter-rectifier","rectifier-meter-inverter","rectifier-meter-both","inverter-meter-zero","inverter-meter-rectifier","inverter-meter-inverter","inverter-meter-both"})
    void independentResistanceAtBothTerminalsAndMeteringEnds(String profile)throws Exception {
        assertEquals("b190cd9481cec14edc749dea3caccab53cbfdb5883dc0b26c6977361695fddc7",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
        try(var archive=new ZipFile(ARCHIVE.toFile())) {
            String a=PREFIX+profile+"-a/",b=PREFIX+profile+"-b/";
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
            controls.setXfrTapControl(false);controls.setHvdcTapControl(false);
            algorithm.getLfAdjAlgo().getPowerAdjConfig().setPsXfrPControl(false);
            algorithm.getNetAdjAlgo().setAreaInterchangeControlEnabled(false);
            assertTrue(algorithm.loadflow());
            var line=(HvdcLine2TLCC<?>)network.getSpecialBranchList().get(0);
            String audit=System.getProperty("lcc.audit.output");
            var data=new java.util.LinkedHashMap<String,double[]>();
            for(var c:java.util.List.of(line.getRectifier(),line.getInverter()))
                data.put(c.getBus().getId(),new double[]{c.getIdc(),c.getDcVoltage(),c.getXformerTapSetting(),c.getFiringAng(),c.powerIntoConverter().getReal(),c.powerIntoConverter().getImaginary()});
            if(profile.endsWith("-zero")) {
                Path baseline=ARCHIVE.resolveSibling("zero-resistance-baseline.zip");
                assertEquals("9208f6a119b4311d470ac0443eeae569fcd8477ec5e9fe0dd0c6517886842dea",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(baseline))));
                try(var old=new ZipFile(baseline.toFile())) {
                    assertArrayEquals(old.getInputStream(old.getEntry(profile+".json")).readAllBytes(),mapper.writeValueAsBytes(data),"zero-resistance output remains exact");
                }
            }
            if(audit!=null) {
                Path directory=Path.of(audit);Files.createDirectories(directory);
                mapper.writeValue(directory.resolve(profile+".json").toFile(),data);
            }
            for(var converter:java.util.List.of(line.getRectifier(),line.getInverter())) {
                var expected=reference.get(converter.getBus().getId().substring(3));
                assertEquals(expected.get("DCCUR").asDouble(),converter.getIdc(),.006,"DC current A");
                assertEquals(expected.get("KVDC").asDouble()*1000,converter.getDcVoltage(),.25,"DC voltage V");
                assertEquals(expected.get("TAP").asDouble(),converter.getXformerTapSetting(),1e-6,"tap");
                assertEquals(expected.get("ANGLE").asDouble(),converter.getFiringAng(),2e-4,"angle degrees");
                assertEquals(expected.get("PAC").asDouble(),converter.powerIntoConverter().getReal()*100,.002,"AC MW");
                assertEquals(expected.get("QAC").asDouble(),converter.powerIntoConverter().getImaginary()*100,.002,"AC Mvar");
            }

        }
    }
}
