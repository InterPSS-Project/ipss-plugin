package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent load-only protection isolates current removal from line switching. */
public class Tiocr1LoadOnlyReferenceTest {
    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void completeLoadOnlyTrajectory(boolean staggered) throws Exception {
        var archive=Path.of("testData/private/verification-artifacts/tiocr-load-only-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent load-only reference captures");
        assertEquals("a28cc3d5271f6a0a6fd9fd270c7f962152653c0333bad2ee64b577f678cd5441",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        List<Map<String,Double>> reference;
        try(var z=new ZipFile(archive.toFile())) {
            reference=Tiocr1ResetReferenceTest.csv(z.getInputStream(z.getEntry("a/native.csv")).readAllBytes())
                    .stream().filter(v->v.get("time_s")>=0).toList();
        }
        HspUsageRankedModelTest.run("tiocr1-load-only",false,staggered);
        var actual=Tiocr1ResetReferenceTest.csv(Files.readAllBytes(Path.of("target/hsp-model-actual-20261030",
                "tiocr1-load-only"+(staggered?"-staggered":"")+"-fault.csv")));
        assertEquals(reference.size(),actual.size(),"Complete grid and event-side samples");
        var bounds=Map.of("V1",staggered?3e-6:.0002,"V2",staggered?1e-6:.000067,
                "P_PU",staggered?3.7e-6:.000134,"Q_PU",staggered?2.9e-5:.0019,
                "ANGLE_DEG",5.6e-5,"SPEED",staggered?4.1e-8:1.36e-6);
        var errors=new TreeMap<String,Double>();bounds.keySet().forEach(k->errors.put(k,0.));
        var times=new TreeMap<String,Double>();
        for(int i=0;i<reference.size();i++) {
            var a=actual.get(i);var b=reference.get(i);
            assertEquals(b.get("time_s"),a.get("time_s"),5e-6);
            for(String k:bounds.keySet()) {
                double error=Math.abs(a.get(k)-b.get(k));
                if(error>errors.get(k)){errors.put(k,error);times.put(k,b.get("time_s"));}
            }
        }
        System.out.println("Load-only boundary "+staggered+" "+errors+" peak times "+times);
        for(String k:List.of("V1","V2","P_PU","Q_PU")) {
            double sum=0,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(int i=0;i<reference.size();i++) {
                double value=reference.get(i).get(k),delta=actual.get(i).get(k)-value;
                sum+=delta*delta;min=Math.min(min,value);max=Math.max(max,value);
            }
            assertTrue(max>min);
            double nrmse=Math.sqrt(sum/reference.size())/(max-min);
            System.out.println("Load-only NRMSE "+staggered+" "+k+"="+nrmse);
            assertTrue(nrmse<.001,k+" NRMSE "+nrmse);
        }
        for(String k:bounds.keySet())assertTrue(errors.get(k)<=bounds.get(k),k+" error "+errors.get(k));
        int latchedSamples=0;
        for(int i=0;i<reference.size();i++) {
            // The third published slot is the scheduled breaker deadline, not
            // elapsed breaker time. Load-only action leaves the monitored line
            // energized and the independently observed moving element latched.
            var expected=reference.get(i);
            if(expected.get("VAR2")<9999. && expected.get("time_s")>=expected.get("VAR2")) {
                assertEquals(1.,expected.get("VAR1"),0.,"Reference retains the energized moving element");
                assertEquals(expected.get("VAR1"),actual.get(i).get("RELAY.Position"),0.,
                        "Load-only shedding must not clear the monitored relay position");
                latchedSamples++;
            }
        }
        assertTrue(latchedSamples>500,"Complete post-operation history is checked");
        var curve=new com.interpss.dstab.relay.model.Tiocr1Data(new double[]{.4,.1,1.1,.04,1.5,.03,2,.02,3,.01,5,.005,.01,.1});
        double leftPosition=0,rightPosition=0,leftError=0,rightError=0,actualPositionError=0;
        for(int i=1;i<reference.size();i++) {
            var left=reference.get(i-1);var right=reference.get(i);
            if(right.get("time_s")>left.get("time_s")) {
                if(leftPosition<1)leftPosition=Math.max(0,Math.min(1,leftPosition+.0005*curve.rate(left.get("VAR0"))));
                if(rightPosition<1)rightPosition=Math.max(0,Math.min(1,rightPosition+.0005*curve.rate(right.get("VAR0"))));
            }
            leftError=Math.max(leftError,Math.abs(leftPosition-right.get("VAR1")));
            rightError=Math.max(rightError,Math.abs(rightPosition-right.get("VAR1")));
            actualPositionError=Math.max(actualPositionError,Math.abs(actual.get(i).get("RELAY.Position")-right.get("VAR1")));
        }
        assertTrue(leftError<3e-7,"Recorded left-current recurrence reproduces every position sample");
        assertTrue(rightError>1e-3,"Endpoint-current substitution must fail the independent discriminator");
        System.out.println("Load-only moving-element error "+staggered+"="+actualPositionError);
        assertTrue(actualPositionError<(staggered?8e-7:3e-5),"Complete coupled moving-element trajectory");
    }
}
