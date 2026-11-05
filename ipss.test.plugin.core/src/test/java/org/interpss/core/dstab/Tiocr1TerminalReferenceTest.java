package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Independent terminal selection with unequal charging currents at the two ends. */
public class Tiocr1TerminalReferenceTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/tiocr-terminal-20261104/captures.zip");
    @ParameterizedTest @CsvSource({"false,from","true,from","false,to","true,to"})
    void completeTerminalTrajectory(boolean staggered,String terminal,@TempDir Path staged)throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent terminal reference captures");
        assertEquals("7cebab325935cd36a247d93b0b3c2f056183bb5b03e61c6ff8fb4389324cbd2e",sha(Files.readAllBytes(ARCHIVE)));
        List<Map<String,Double>> reference;
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            byte[] first=zip.getInputStream(zip.getEntry(terminal+"/a/native.csv")).readAllBytes();
            assertArrayEquals(first,zip.getInputStream(zip.getEntry(terminal+"/b/native.csv")).readAllBytes(),"Independent cold repeat");
            reference=Tiocr1ResetReferenceTest.csv(first).stream().filter(r->r.get("time_s")>=0).toList();
            for(String name:List.of("network.raw","dynamic.dyr"))
                Files.write(staged.resolve(name),zip.getInputStream(zip.getEntry(terminal+"/a/"+name)).readAllBytes());
        }
        HspUsageRankedModelTest.setup();
        Path output=Files.createTempDirectory(Path.of("target"),"tiocr-terminal-"+terminal+"-");
        HspUsageRankedModelTest.run("tiocr1-terminal",false,staggered,false,
                staged.resolve("network.raw"),staged.resolve("dynamic.dyr"),output);
        var actual=Tiocr1ResetReferenceTest.csv(Files.readAllBytes(output.resolve("tiocr1-terminal"+(staggered?"-staggered":"")+"-fault.csv")));
        assertEquals(reference.size(),actual.size(),"Complete reference grid and both event sides");
        var bounds=new LinkedHashMap<String,Double>();
        bounds.put("V1",staggered?4e-6:.00027);bounds.put("V2",staggered?9e-7:.00008);
        bounds.put("P_PU",staggered?3.6e-6:.00014);bounds.put("Q_PU",staggered?2.6e-5:.00185);
        bounds.put("ANGLE_DEG",staggered?9.5e-5:.00025);bounds.put("SPEED",staggered?4.2e-8:1.6e-6);
        var maxima=new LinkedHashMap<String,Double>();bounds.keySet().forEach(k->maxima.put(k,0.));
        var squares=new LinkedHashMap<String,Double>();bounds.keySet().forEach(k->squares.put(k,0.));
        double selectedError=0,wrongEndError=0,currentError=0,positionError=0;
        int duplicates=0;
        for(int i=0;i<reference.size();i++) {
            var r=reference.get(i);var a=actual.get(i);
            assertEquals(r.get("time_s"),a.get("time_s"),5e-6);
            if(i>0&&r.get("time_s").equals(reference.get(i-1).get("time_s")))duplicates++;
            Complex v1=phasor(r,"1"),v2=phasor(r,"2");
            // The monitored branch has X=.2, total B=.2 on the system base.
            Complex series=v1.subtract(v2).multiply(new Complex(0,-5));
            double from=series.add(v1.multiply(new Complex(0,.1))).abs();
            double to=series.negate().add(v2.multiply(new Complex(0,.1))).abs();
            selectedError=Math.max(selectedError,Math.abs((terminal.equals("from")?from:to)-r.get("VAR0")));
            wrongEndError=Math.max(wrongEndError,Math.abs((terminal.equals("from")?to:from)-r.get("VAR0")));
            currentError=Math.max(currentError,Math.abs(a.get("RELAY.Current")-r.get("VAR0")));
            positionError=Math.max(positionError,Math.abs(a.get("RELAY.Position")-r.get("VAR1")));
            for(String key:bounds.keySet()) {
                double error=a.get(key)-r.get(key);
                maxima.put(key,Math.max(maxima.get(key),Math.abs(error)));
                squares.put(key,squares.get(key)+error*error);
            }
        }
        assertEquals(2,duplicates,"Application and clearing retain both sides");
        assertTrue(selectedError<6e-7,"Reference current obeys the selected terminal equation");
        assertTrue(wrongEndError>.02,"Charging distinguishes the opposite terminal");
        System.out.println("TIOCR terminal "+terminal+" staggered="+staggered+" boundary="+maxima
                +" current="+currentError+" position="+positionError+" equation="+selectedError+" wrongEnd="+wrongEndError);
        for(var bound:bounds.entrySet())assertTrue(maxima.get(bound.getKey())<=bound.getValue(),bound.getKey()+" error="+maxima.get(bound.getKey()));
        for(String key:List.of("V1","V2","P_PU","Q_PU")) {
            double range=reference.stream().mapToDouble(r->r.get(key)).max().orElseThrow()
                    -reference.stream().mapToDouble(r->r.get(key)).min().orElseThrow();
            assertTrue(range>0.);
            double nrmse=Math.sqrt(squares.get(key)/reference.size())/range;
            assertTrue(nrmse<.001,key+" full-reference-range NRMSE="+nrmse);
        }
        assertTrue(currentError<(staggered?1e-6:5.8e-4),"Complete terminal-current trajectory");
        assertTrue(positionError<(staggered?3.3e-7:2.2e-5),"Complete relay-position trajectory");
    }
    private static Complex phasor(Map<String,Double> row,String bus) {
        double angle=Math.toRadians(row.get("A"+bus)),v=row.get("V"+bus);
        return new Complex(v*Math.cos(angle),v*Math.sin(angle));
    }
    private static String sha(byte[] bytes)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
