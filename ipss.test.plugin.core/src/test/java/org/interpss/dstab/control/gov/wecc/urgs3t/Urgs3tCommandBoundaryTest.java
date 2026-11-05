package org.interpss.dstab.control.gov.wecc.urgs3t;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Localizes command and network boundaries without replacing trajectory acceptance. */
public class Urgs3tCommandBoundaryTest {
    private static final Path CONTROLS=Path.of("testData/private/verification-artifacts/urgs3t-initial-20261102/network-captures.zip");
    private static final String SHA="1a4a503a0aa3c9b2d423570019e574d97eae891d480d506aa8cec7c021b13196";
    private static final ObjectMapper JSON=new ObjectMapper();
    @BeforeAll static void setup()throws Exception {
        Urgs3tReferenceTest.setup();
        Assumptions.assumeTrue(Files.isRegularFile(CONTROLS),"Retain independent network controls");
        assertEquals(SHA,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(CONTROLS))));
    }
    static Stream<Arguments> profiles(){return Stream.of("temperature-limits","rate-limits").flatMap(p->Stream.of(false,true).map(m->Arguments.of(p,m)));}
    @Test void referenceCommandIncrementUsesItsStaggeredEventWeight()throws Exception {
        for(String step:List.of("0p0005","0p00025")) {
            double h=Double.parseDouble(step.replace('p','.'));
            var rows=Urgs3tReferenceTest.rows(Urgs3tReferenceTest.stage("speed-deadband-h"+step+"-t0p5-command-a").resolve("native.csv"));
            int event=-1;
            for(int i=1;i<rows.size()-1;i++)if(rows.get(i).get("time_s").equals(rows.get(i-1).get("time_s"))){event=i;break;}
            assertTrue(event>0);var at=rows.get(event);var next=rows.get(event+1);var following=rows.get(event+2);
            assertEquals(.05,at.get("time_s"),1e-7);
            assertEquals(0.,at.get("SPEED"),1e-12);
            double initial=at.get("STATE0"),initialRate=.2/.1;
            double staggered=initial+.75*h*initialRate;
            double heun=initial+h*initialRate*(1-h/(2*.1));
            assertEquals(staggered,next.get("STATE0"),5e-8,"Reference derivative average precedes the two-step formula");
            assertTrue(Math.abs(heun-next.get("STATE0"))>.45*h,"Full right-side Heun step must be independently distinguished");
            double eventRate=.5*initialRate;
            double acceptedRate=(initial+.2-next.get("STATE0"))/.1;
            double twoStep=next.get("STATE0")+h*(1.5*acceptedRate-.5*eventRate);
            assertEquals(twoStep,following.get("STATE0"),5e-8,"The next interval retains the averaged event rate as two-step history");
            assertTrue(Math.abs(following.get("STATE0")-(next.get("STATE0")+h*acceptedRate))>.4*h,
                    "The second reference increment distinguishes stored history from an ordinary single-step update");
        }
    }
    @ParameterizedTest @ValueSource(strings={"temperature-limits","rate-limits"})
    void tighteningOnlyReferenceNetworkTolerancePreservesEverySample(String profile)throws Exception {
        Path original=Urgs3tReferenceTest.stage(profile+"-h0p0005-t0p5-command-a");
        try(var z=new ZipFile(CONTROLS.toFile())) {
            for(String tolerance:List.of("1e-06","1e-08"))for(String repeat:List.of("a","b")) {
                String tag=profile+"-h0p0005-t0p5-command-tol"+tolerance+"-"+repeat;
                var manifest=JSON.readTree(z.getInputStream(z.getEntry(tag+"/manifest.json")));
                assertEquals("complete",manifest.path("status").asText());
                assertEquals(Double.parseDouble(tolerance),manifest.path("settings").path("network_tolerance").asDouble(),0.);
                for(var field:manifest.path("files").properties()) {
                    byte[] bytes=z.getInputStream(z.getEntry(tag+"/"+field.getKey())).readAllBytes();
                    assertEquals(field.getValue().asText(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
                }
                for(String name:List.of("network.raw","dynamic.dyr","native.csv"))
                    assertArrayEquals(Files.readAllBytes(original.resolve(name)),z.getInputStream(z.getEntry(tag+"/"+name)).readAllBytes(),tag+" "+name);
            }
        }
    }
    @ParameterizedTest @MethodSource("profiles")
    void retainCompleteCandidateNetworkToleranceDiscriminators(String profile,boolean shared)throws Exception {
        Path input=Urgs3tReferenceTest.stage(profile+"-h0p0005-t0p5-command-a");
        var reference=Urgs3tReferenceTest.rows(input.resolve("native.csv"));
        var results=new ArrayList<Map<String,Object>>();
        Path output=Files.createTempDirectory(Path.of("target"),"urgs3t-command-network-");
        Urgs3tReferenceTest.Simulation baseline=null;
        for(double tolerance:new double[]{1e-6,1e-8,1e-10}) {
            var simulation=Urgs3tReferenceTest.simulate(input,shared,tolerance);
            if(baseline==null)baseline=simulation;
            else {
                assertEquals(baseline.initial(),simulation.initial());
                assertEquals(baseline.frames(),simulation.frames(),"These synthetic trajectories are unchanged by network tolerance alone");
            }
            var result=new LinkedHashMap<String,Object>();result.put("network_tolerance",tolerance);
            result.putAll(compare(output,Double.toString(tolerance),simulation,reference));results.add(result);
        }
        try(var writer=BenchmarkOutputGuard.newWriter(output.resolve("diagnostic.json"),100000)) {
            JSON.writerWithDefaultPrettyPrinter().writeValue(writer,Map.of("profile",profile,"method",shared?"staggered":"modified-euler","diagnostic_only",true,"results",results));
        }
        System.out.println("URGS3T command network discriminator "+profile+" shared="+shared+" output="+output.getFileName());
    }
    static Map<String,Object> compare(Path output,String label,Urgs3tReferenceTest.Simulation simulation,List<Map<String,Double>> reference)throws Exception {
        int index=0,startup=0;
        var keys=new TreeSet<>(reference.getFirst().keySet());keys.remove("time_s");
        var maximum=new TreeMap<String,Double>();var squared=new TreeMap<String,Double>();
        var low=new TreeMap<String,Double>();var high=new TreeMap<String,Double>();
        for(String key:keys){maximum.put(key,0.);squared.put(key,0.);low.put(key,Double.POSITIVE_INFINITY);high.put(key,Double.NEGATIVE_INFINITY);}
        try(var writer=BenchmarkOutputGuard.newWriter(output.resolve("actual-"+label+".csv"),BenchmarkOutputGuard.estimateCsvBytes(reference.size(),2+2*keys.size()))) {
            writer.write("source_time_s,actual_time_s,"+String.join(",",keys));for(String key:keys)writer.write(",REFERENCE."+key);writer.write("\n");
            float clock=0;long tick=0;
            for(var r:reference) {
                double time=r.get("time_s");var a=time<0?simulation.initial():simulation.frames().get(index++);
                if(time<0)startup++;else {
                    long next=Math.round(a.get("time_s")/.0005);assertTrue(next>=tick&&next-tick<=1);
                    while(tick<next){clock+=(float).0005;tick++;}assertEquals((double)clock,time,1e-10);
                }
                writer.write(time+","+a.get("time_s"));
                for(String key:keys) {
                    double error=a.get(key)-r.get(key);assertTrue(Double.isFinite(error));
                    maximum.put(key,Math.max(maximum.get(key),Math.abs(error)));squared.put(key,squared.get(key)+error*error);
                    low.put(key,Math.min(low.get(key),r.get(key)));high.put(key,Math.max(high.get(key),r.get(key)));writer.write(","+a.get(key));
                }
                for(String key:keys)writer.write(","+r.get(key));writer.write("\n");
            }
        }
        assertEquals(2,startup);assertEquals(reference.size(),simulation.frames().size()+startup);
        var rmse=new TreeMap<String,Double>();var nrmse=new TreeMap<String,Double>();
        for(String key:keys){double error=Math.sqrt(squared.get(key)/reference.size());rmse.put(key,error);if(high.get(key)>low.get(key))nrmse.put(key,error/(high.get(key)-low.get(key)));}
        return Map.of("maximum_absolute",maximum,"rmse",rmse,"range_nrmse",nrmse);
    }

}
