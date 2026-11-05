package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent low-voltage static-load response with active frequency coefficients. */
public class CompositeStaticFrequencyLowVoltageTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/cmldblu1-frequency-static-20261105/captures.zip");
    private static final String SHA="6b8f9b955ccb6307cfe719463e45ae862cc224e5cd2e6f61eeceb594d18fa3b5";

    private static List<Map<String,Double>> rows(byte[] raw) throws Exception {
        try(var reader=new java.io.BufferedReader(new StringReader(new String(raw,StandardCharsets.UTF_8)))) {
            var names=reader.readLine().split(",");var rows=new ArrayList<Map<String,Double>>();
            for(String line;(line=reader.readLine())!=null;) {
                var parts=line.split(",");assertEquals(names.length,parts.length);
                var row=new LinkedHashMap<String,Double>();
                for(int k=0;k<names.length;k++)row.put(names[k],Double.parseDouble(parts[k]));
                rows.add(row);
            }
            return rows;
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void lowVoltageStaticPortKeepsFrequencyDependentCurrent(boolean staggered,@TempDir Path staged)throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        assertEquals(SHA,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
        List<Map<String,Double>> source;
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            byte[] first=zip.getInputStream(zip.getEntry("a/native.csv")).readAllBytes();
            assertArrayEquals(first,zip.getInputStream(zip.getEntry("b/native.csv")).readAllBytes());
            source=rows(first).stream().filter(row->row.get("time_s")>=0).toList();
            Files.write(staged.resolve("dynamic.dyr"),zip.getInputStream(zip.getEntry("a/dynamic.dyr")).readAllBytes());
        }
        assertEquals(1003,source.size());
        assertTrue(source.stream().anyMatch(row->row.get("VAR57")>1),"The reference must prepare a low-voltage admittance");
        assertTrue(source.stream().anyMatch(row->row.get("VAR58")<-.5),"The reference must prepare reactive admittance");
        int duplicateTimes=0;
        for(int i=1;i<source.size();i++)if(source.get(i-1).get("time_s").equals(source.get(i).get("time_s")))duplicateTimes++;
        assertEquals(2,duplicateTimes,"Retain both event-side samples");
        var actual=Cmldblu1SharedRecording.simulateStaticControl(staged.resolve("dynamic.dyr"),staggered,.0005,.04,0);
        assertEquals(source.size(),actual.frames.size());
        assertEquals(.5,actual.model.getData().value(31),1e-7);
        assertEquals(.5,actual.model.getData().value(36),1e-7);
        double[] referenceClock=new double[1001];float clock=0;
        for(int i=1;i<referenceClock.length;i++){clock+=(float).0005;referenceClock[i]=clock;}
        for(int i=0;i<source.size();i++) {
            int ordinal=(int)Math.round(actual.frames.get(i).time()/.0005);
            assertEquals(referenceClock[ordinal],source.get(i).get("time_s"),6e-13);
        }
        for(String[] pair:new String[][]{{"V1","V1"},{"V2","V2"},{"V3","V3"},
                {"VAR25","STATIC.P"},{"VAR26","STATIC.Q"},
                {"VAR57","STATIC.LowVoltageG"},{"VAR58","STATIC.LowVoltageB"},
                {"ANGLE_DEG","ANGLE_DEG"}}) {
            double squared=0,peak=0,minimum=Double.POSITIVE_INFINITY,maximum=Double.NEGATIVE_INFINITY;
            for(int i=0;i<source.size();i++) {
                double expected=source.get(i).get(pair[0]);
                double observed=actual.frames.get(i).values().get(pair[1]);
                double error=observed-expected;squared+=error*error;peak=Math.max(peak,Math.abs(error));
                minimum=Math.min(minimum,expected);maximum=Math.max(maximum,expected);
            }
            double rmse=Math.sqrt(squared/source.size());
            System.out.printf("CMLD_STATIC_FREQ %s %s rmse=%.12g nrmse=%.12g peak=%.12g%n",
                    staggered,pair[1],rmse,rmse/(maximum-minimum),peak);
            if(pair[1].equals("ANGLE_DEG"))assertTrue(rmse<.01,pair[1]+" RMSE="+rmse);
            else if(pair[1].startsWith("STATIC.LowVoltage"))assertTrue(peak<5e-6,pair[1]+" peak="+peak);
            else assertTrue(rmse/(maximum-minimum)<.001,pair[1]+" NRMSE="+rmse/(maximum-minimum));
        }
    }
}
