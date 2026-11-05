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
import java.util.Set;
import java.util.zip.ZipFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Complete independent mixed static/electronic response with static frequency coefficients. */
public class CompositeMixedStaticFrequencyTest {
    private record Profile(String name,String archive,String sha,Set<String> inactive,
            double step,double endTime,int rows) { }
    private static Stream<Arguments> profiles() {
        var merged=new Profile("merged", "cmldblu1-mixed-frequency-20261105",
                "ad659d1bbf30cc17a2f2b0ea9834f9c08675e0457a8cb9a6f846a1e374aad7cb",
                Set.of("VAR69","VAR70","VAR71","VAR72"),.0005,.5,1005);
        var distributed=new Profile("distributed", "cmldblu1-distributed-frequency-20261105",
                "40faed43c8f82bfaedfaea9b3b582b09e2bb82696e4b09736a50ed78113cf479",
                Set.of(),.0005,.5,1005);
        var duration=new Profile("distributed-duration", "cmldblu1-distributed-frequency-duration-20261105",
                "a099a24d1d1403ca7d3d8cdc27e15720323297bba430396e639bade8a56a5f2d",
                Set.of("VAR69","VAR70","VAR71","VAR72"),1.0/240,10+1.0/240,2406);
        return Stream.of(Arguments.of(merged,false),Arguments.of(merged,true),
                Arguments.of(distributed,false),Arguments.of(distributed,true),
                Arguments.of(duration,false),Arguments.of(duration,true));
    }

    private static List<Map<String,Double>> rows(byte[] raw) throws Exception {
        try(var reader=new java.io.BufferedReader(new StringReader(new String(raw,StandardCharsets.UTF_8)))) {
            String[] names=reader.readLine().split(",");var result=new ArrayList<Map<String,Double>>();
            for(String line;(line=reader.readLine())!=null;) {
                String[] parts=line.split(",");assertEquals(names.length,parts.length);
                var row=new LinkedHashMap<String,Double>();
                for(int i=0;i<names.length;i++)row.put(names[i],Double.parseDouble(parts[i]));
                result.add(row);
            }
            return result;
        }
    }

    @ParameterizedTest @MethodSource("profiles")
    void mixedFrequencyPortFollowsCompleteIndependentFault(Profile profile,boolean staggered,
            @TempDir Path staged) throws Exception {
        Path archive=Path.of("testData/private/verification-artifacts",profile.archive(),"captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive));
        assertEquals(profile.sha(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        List<Map<String,Double>> source;
        try(var zip=new ZipFile(archive.toFile())) {
            var first=rows(zip.getInputStream(zip.getEntry("a/native.csv")).readAllBytes());
            var repeat=rows(zip.getInputStream(zip.getEntry("b/native.csv")).readAllBytes());
            assertEquals(first.size(),repeat.size());assertEquals(profile.rows(),first.size());
            for(int i=0;i<first.size();i++)for(var field:first.get(i).entrySet())
                if(!profile.inactive().contains(field.getKey()))assertEquals(field.getValue(),repeat.get(i).get(field.getKey()),
                        "Cold-repeat field " + field.getKey() + " row " + i);
            source=first.stream().filter(row->row.get("time_s")>=0).toList();
            Files.write(staged.resolve("dynamic.dyr"),zip.getInputStream(zip.getEntry("a/dynamic.dyr")).readAllBytes());
        }
        assertEquals(profile.rows()-2,source.size());
        assertTrue(source.stream().anyMatch(row->row.get("VAR57")>.9));
        assertTrue(source.stream().anyMatch(row->row.get("VAR58")<-.3));
        int duplicateTimes=0;
        for(int i=1;i<source.size();i++)if(source.get(i-1).get("time_s").equals(source.get(i).get("time_s")))duplicateTimes++;
        assertEquals(2,duplicateTimes);
        var actual=Cmldblu1SharedRecording.simulateStaticControl(staged.resolve("dynamic.dyr"),
                staggered,profile.step(),.04,0,profile.endTime());
        assertEquals(source.size(),actual.frames.size());
        assertEquals(.5,actual.model.getData().value(22),1e-7);
        assertEquals(.5,actual.model.getData().value(31),1e-7);
        assertEquals(.5,actual.model.getData().value(36),1e-7);
        assertEquals(profile.name().equals("merged"),actual.model.getLoadBus()==actual.model.getDStabBus());
        double[] clock=new double[(int)Math.ceil(profile.endTime()/profile.step())+5];float tick=0;
        for(int i=1;i<clock.length;i++){tick+=(float)profile.step();clock[i]=tick;}
        for(int i=0;i<source.size();i++) {
            int ordinal=(int)Math.round(actual.frames.get(i).time()/profile.step());
            assertEquals(clock[ordinal],source.get(i).get("time_s"),
                    profile.step()<.001 ? 6e-13 : 6e-11);
        }
        for(String[] pair:new String[][]{{"V1","V1"},{"V2","V2"},{"V3","V3"},
                {"VAR25","STATIC.P"},{"VAR26","STATIC.Q"},
                {"VAR57","STATIC.LowVoltageG"},{"VAR58","STATIC.LowVoltageB"},
                {"ANGLE_DEG","ANGLE_DEG"}}) {
            double sum=0,peak=0,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(int i=0;i<source.size();i++) {
                double expected=source.get(i).get(pair[0]);
                double observed=actual.frames.get(i).values().get(pair[1]);
                double error=observed-expected;sum+=error*error;peak=Math.max(peak,Math.abs(error));
                min=Math.min(min,expected);max=Math.max(max,expected);
            }
            double rmse=Math.sqrt(sum/source.size()),nrmse=rmse/(max-min);
            System.out.printf("CMLD_MIXED_FREQ %s %s %s rmse=%.12g nrmse=%.12g peak=%.12g%n",
                    profile.name(),staggered,pair[1],rmse,nrmse,peak);
            if(pair[1].equals("ANGLE_DEG"))assertTrue(rmse<.01,pair[1]+" RMSE="+rmse);
            else if(pair[1].startsWith("STATIC.LowVoltage"))assertTrue(peak<5e-6,pair[1]+" peak="+peak);
            else assertTrue(nrmse<.001,pair[1]+" NRMSE="+nrmse);
        }
    }
}
