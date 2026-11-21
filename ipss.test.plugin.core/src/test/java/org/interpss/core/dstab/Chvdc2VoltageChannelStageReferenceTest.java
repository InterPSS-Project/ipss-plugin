package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Source-only check of the published rectifier-voltage observation stage. */
public class Chvdc2VoltageChannelStageReferenceTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/chvdc2-controller-20261102/captures.zip");
    private static final String SHA="75d8b6a733eb20426dbc8c8fe7c4efdab854c8e11a82da99ee98f6ce76377511";

    @Test void faultOnVoltageChannelFollowsTheNextPublishedAngleCoordinate()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        assertEquals(SHA,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(ARCHIVE))));
        var json=new ObjectMapper();
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            for(String fault:List.of("rectifier","inverter"))for(String step:List.of("h0p0005","h0p00025")) {
                String tag="stiff-legacy-"+fault+"-fault-flags001-"+step+"-";
                byte[] first=zip.getInputStream(zip.getEntry(tag+"a/native.csv")).readAllBytes();
                assertArrayEquals(first,zip.getInputStream(zip.getEntry(tag+"b/native.csv")).readAllBytes());
                List<Map<String,Double>> rows=Tiocr1ResetReferenceTest.csv(first);
                var equipment=json.readTree(zip.getInputStream(zip.getEntry(tag+"a/solved-equipment.json"))).path("7");
                double noLoad=3*Math.sqrt(2)/Math.PI*2*equipment.path("EBASE").asDouble()*1000
                        *equipment.path("TR").asDouble()/equipment.path("TAP").asDouble();
                double commutation=3/Math.PI*2*equipment.path("XC").asDouble();
                double sameSquared=0,nextSquared=0;int samples=0;
                for(int k=0;k<rows.size()-1;k++) {
                    var row=rows.get(k);double time=row.get("time_s");
                    if(time<=.052||time>=.095)continue;
                    var next=rows.get(k+1);
                    double observed=row.get("VAR6")*1000;
                    double noLoadAtBus=noLoad*row.get("V7"),drop=commutation*row.get("VAR5");
                    double same=noLoadAtBus*Math.cos(Math.toRadians(row.get("STATE6")))-drop;
                    double nextAngle=noLoadAtBus*Math.cos(Math.toRadians(next.get("STATE6")))-drop;
                    sameSquared+=(same-observed)*(same-observed);
                    nextSquared+=(nextAngle-observed)*(nextAngle-observed);samples++;
                }
                assertTrue(samples>=80,"Retain the complete fault-on observation window: "+tag);
                double sameRmse=Math.sqrt(sameSquared/samples),nextRmse=Math.sqrt(nextSquared/samples);
                assertTrue(sameRmse>5&&nextRmse<.2*sameRmse,
                        "The published voltage and angle have distinct stages: "+tag
                                +" same-row "+sameRmse+" V; next-angle "+nextRmse+" V");
            }
        }
    }
}
