package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.*;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.core.dstab.reference.RelativeRmse;
import com.interpss.dstab.hvdc.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Complete joint-controller replay with explicit local stages; independent network coupling remains separate. */
public class Chvdc2ControlAssemblyReferenceTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/chvdc2-converter-20261102/protection-captures.zip");
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Map<String,JsonNode> VALID=new TreeMap<>();
    private static final int LOCAL_STEPS=10;
    private static final double[] LIMITS={1e-5,1e-5,1e-5,1e-5,.002,.002,.002,.002,1e-5,1e-5,1e-5,1e-5,1e-5,1e-5,.002,.002,.002,.001,.001};
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    @BeforeAll static void setup()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain complete independent controller captures");
        assertEquals("5245279e56e57373bdd8afc23fc5e033b73aef0b5e667f41a9d50c4e7ae53b16",sha(Files.readAllBytes(ARCHIVE)));VALID.clear();
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            var matrix=JSON.readTree(z.getInputStream(z.getEntry("manifest.json")));
            for(var file:matrix.path("files").properties())assertEquals(file.getValue().asText(),sha(z.getInputStream(z.getEntry(file.getKey())).readAllBytes()));
            for(var profile:matrix.path("profiles")) {
                if(!profile.path("status").asText().equals("complete")||!profile.path("repeat").asText().equals("a"))continue;
                String key=profile.path("batch").asText()+"/"+profile.path("tag").asText();
                var m=JSON.readTree(z.getInputStream(z.getEntry(key+"/manifest.json")));
                assertTrue(m.path("initialization_and_network_valid").asBoolean());assertEquals(m.path("entered_constants"),m.path("initialized_constants"));
                assertArrayEquals(z.getInputStream(z.getEntry(key+"/native.csv")).readAllBytes(),z.getInputStream(z.getEntry(key.substring(0,key.length()-1)+"b/native.csv")).readAllBytes());
                VALID.put(key,m);
            }
        }
        assertEquals(2,VALID.size());
    }
    static Stream<Arguments> profiles(){return VALID.keySet().stream().flatMap(k->Stream.of(false,true).map(shared->Arguments.of(k,shared)));}
    static Stream<String> captures(){return VALID.keySet().stream();}
    @ParameterizedTest @MethodSource("captures")
    void referenceRamlFloorPreservesTheEnteredPiLimitAndItsMemory(String key)throws Exception {
        var manifest=VALID.get(key);double[] c=JSON.convertValue(manifest.path("entered_constants"),double[].class);
        double h=manifest.path("settings").path("step").asDouble();List<Map<String,Double>> rows;
        try(var z=new ZipFile(ARCHIVE.toFile())){rows=Tiocr1ResetReferenceTest.csv(z.getInputStream(z.getEntry(key+"/native.csv")).readAllBytes());}
        int observed=0;double maximumIncrementError=0;
        for(int i=0;i+1<rows.size();i++) {
            var a=rows.get(i);var b=rows.get(i+1);
            if(a.get("time_s").equals(b.get("time_s"))||a.get("VAR19")<=a.get("STATE4")+1)continue;
            observed++;assertTrue(b.get("STATE4")<a.get("VAR19")-1,"The moving floor must not overwrite the PI state");
            assertTrue(b.get("STATE6")>=a.get("VAR19")-.002,"The firing-angle coordinate follows the moving floor");
            double meanError=.5*(a.get("STATE0")+b.get("STATE0")-(a.get("VAR13")+b.get("VAR13"))/c[28]);
            maximumIncrementError=Math.max(maximumIncrementError,Math.abs(b.get("STATE4")-a.get("STATE4")-h*c[1]*meanError));
        }
        assertTrue(observed>5);assertTrue(maximumIncrementError<8*Math.ulp(32f),"PI increments follow current error inside the entered bounds: "+maximumIncrementError);
    }
    private static void input(Chvdc2ControlAssembly model,Map<String,Double> row,double rated) {
        model.setInput(row.get("VAR5"),row.get("VAR7"),row.get("VAR6"),row.get("VAR8"),row.get("V7"),row.get("V9"),row.get("VAR15")*rated,row.get("VAR0"),row.get("VAR4"));
    }
    @ParameterizedTest @MethodSource("profiles")
    void replayEveryJointControllerStateAndPublishedBound(String key,boolean staggered)throws Exception {
        var manifest=VALID.get(key);double H=manifest.path("settings").path("step").asDouble(),h=H/LOCAL_STEPS;
        double[] constants=JSON.convertValue(manifest.path("entered_constants"),double[].class);
        List<Map<String,Double>> rows;JsonNode equipment;
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            rows=Tiocr1ResetReferenceTest.csv(z.getInputStream(z.getEntry(key+"/native.csv")).readAllBytes());
            equipment=JSON.readTree(z.getInputStream(z.getEntry(key+"/solved-equipment.json"))).path("9");
        }
        var model=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2U1,JSON.convertValue(manifest.path("settings").path("flags"),int[].class),constants)
                .prepareControls(equipment.path("TR").asDouble(),equipment.path("TAP").asDouble());
        var first=rows.getFirst();input(model,first,constants[28]);model.initialize(first.get("STATE6"),first.get("STATE7"),first.get("time_s"));
        double[] x=new double[15],f=new double[15],old=new double[15],left=new double[15],trial=new double[15],value=new double[19],maximum=new double[19],times=new double[19];
        RelativeRmse[] relative=new RelativeRmse[19];for(int i=0;i<relative.length;i++)relative[i]=new RelativeRmse();
        var alignedRaml=new RelativeRmse();int actualFirst=-1,actualLast=-1,referenceFirst=-1,referenceLast=-1,ramlTransitionRows=0;
        double actualFirstTime=Double.NaN,actualLastTime=Double.NaN,referenceFirstTime=Double.NaN,referenceLastTime=Double.NaN;
        boolean started=false;long sequence=0,revision=0;int duplicates=0,activations=0,releases=0;double lastOutput=Double.NaN;
        Path out=Files.createTempDirectory(Path.of("target"),"chvdc2-joint-"+(staggered?"staggered-":"euler-"));
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("replay.csv"),BenchmarkOutputGuard.estimateCsvBytes(rows.size(),58))) {
            w.write("time_s");for(int i=0;i<19;i++)w.write(",value_"+i+",reference_"+i+",error_"+i);w.write("\n");
            for(int n=0;n<rows.size();n++) {
                var row=rows.get(n);double time=row.get("time_s");input(model,row,constants[28]);model.readStaggeredState(x,0);System.arraycopy(x,0,value,0,15);
                boolean eventLeft=n+1<rows.size()&&time==rows.get(n+1).get("time_s");
                if(n>0&&time==rows.get(n-1).get("time_s"))duplicates++;
                model.beginTransaction();
                int steps=eventLeft?0:LOCAL_STEPS;
                for(int local=0;local<=steps;local++) {
                    if(local==steps&&steps>0)break;
                    boolean active=model.ramlActive();model.acceptBoundary(time,sequence++);
                    if(active!=model.ramlActive()){if(model.ramlActive())activations++;else releases++;}
                    // Test the final-local-evaluation publication hypothesis; this is not a timestamp shift.
                    value[15]=model.rectifierMinimumDegrees();value[16]=model.inverterMaximumDegrees();
                    value[17]=model.rectifierCurrentOrderAmperes();value[18]=model.inverterCurrentOrderAmperes();
                    if(steps==0)break;
                    if(staggered) {
                        model.readStaggeredState(x,0);model.evaluateStaggered(h,f,0);if(!started)System.arraycopy(f,0,old,0,15);
                        long now=model.staggeredInputDiscontinuityVersion();
                        if(now!=revision){assertTrue(model.readStaggeredInputDiscontinuityLeftRates(left,0));for(int i=0;i<15;i++)f[i]=.5*(f[i]+left[i]);revision=now;}
                        for(int i=0;i<15;i++)trial[i]=x[i]+h*(1.5*f[i]-.5*old[i]);model.applyStaggeredState(trial,0,h);model.readStaggeredState(x,0);
                        for(int i=0;i<15;i++)old[i]=x[i]==trial[i]?f[i]:0;started=true;
                    }else {
                        model.step(h,0);active=model.ramlActive();model.evaluatePredictorProtection(time,sequence++);
                        if(active!=model.ramlActive()){if(model.ramlActive())activations++;else releases++;}
                        model.step(h,1);
                    }
                }
                model.commitTransaction();assertFalse(model.inverterShorted());
                w.write(Double.toString(time));
                for(int i=0;i<19;i++) {
                    double reference=row.get(i<15?"STATE"+i:i==15?"VAR19":i==16?"VAR20":i==17?"VAR13":"VAR14");
                    double error=value[i]-reference;if(Math.abs(error)>maximum[i]){maximum[i]=Math.abs(error);times[i]=time;}
                    relative[i].add(value[i],reference);
                    if(i==15){
                        boolean actualActive=value[i]>constants[3]+1.,referenceActive=reference>constants[3]+1.;
                        if(actualActive){if(actualFirst<0){actualFirst=n;actualFirstTime=time;}actualLast=n;actualLastTime=time;}
                        if(referenceActive){if(referenceFirst<0){referenceFirst=n;referenceFirstTime=time;}referenceLast=n;referenceLastTime=time;}
                        if(actualActive!=referenceActive)ramlTransitionRows++;
                        else alignedRaml.add(value[i],reference);
                    }
                    w.write(","+value[i]+","+reference+","+error);
                }
                w.write("\n");lastOutput=value[15];
            }
        }
        var report=new LinkedHashMap<String,Object>();report.put("profile",key);report.put("staggered",staggered);report.put("local_steps",LOCAL_STEPS);
        double[] relativeValues=Arrays.stream(relative).mapToDouble(RelativeRmse::value).toArray();
        double alignedRamlValue=alignedRaml.value();
        report.put("maximum_absolute_errors",maximum);report.put("maximum_error_time_s",times);report.put("relative_rmse",relativeValues);report.put("samples",rows.size());report.put("event_duplicates",duplicates);
        report.put("raml_aligned_relative_rmse",alignedRamlValue);report.put("raml_transition_rows",ramlTransitionRows);
        report.put("raml_transition_indices",new int[]{actualFirst,referenceFirst,actualLast,referenceLast});
        report.put("raml_transition_times_s",new double[]{actualFirstTime,referenceFirstTime,actualLastTime,referenceLastTime});
        report.put("activations",activations);report.put("releases",releases);report.put("recorded_internal_state_resets_after_initialization",0);report.put("full_network_coupling_accepted",false);
        report.put("input_contract","Recorded physical terminal inputs held over each local cycle; all controller states evolve without resets");
        report.put("output_publication_hypothesis","Last local derivative evaluation before its state update; event-left observations have zero duration");
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"),30000)){JSON.writerWithDefaultPrettyPrinter().writeValue(w,report);}
        assertEquals(2,duplicates);assertEquals(1,activations);assertEquals(1,releases);assertEquals(constants[3],lastOutput);
        assertTrue(actualFirst>=0&&referenceFirst>=0&&actualLast>=actualFirst&&referenceLast>=referenceFirst,
                "Both RAML activation and release must be observed");
        assertTrue(Math.abs(actualFirst-referenceFirst)<=1&&Math.abs(actualLast-referenceLast)<=1,
                "RAML activation and release may differ by at most one retained sample");
        assertTrue(Math.abs(actualFirstTime-referenceFirstTime)<=H*1.01&&Math.abs(actualLastTime-referenceLastTime)<=H*1.01,
                "RAML transition time may differ by at most one network step");
        assertEquals(Math.abs(actualFirst-referenceFirst)+Math.abs(actualLast-referenceLast),ramlTransitionRows,
                "Only the two RAML transition rows may have a different active regime");
        var gates=new ArrayList<org.junit.jupiter.api.function.Executable>();
        for(int i=0;i<19;i++){int channel=i;gates.add(()->assertTrue(
                (channel==15?alignedRamlValue:relativeValues[channel])<RelativeRmse.ONE_PERCENT,
                "Channel "+channel+" relative RMSE "+(channel==15?alignedRamlValue:relativeValues[channel])
                        +"; full metric "+relativeValues[channel]+"; maximum "+maximum[channel]+" at "+times[channel]));}
        assertAll(key+" / "+staggered,gates);
    }
}
