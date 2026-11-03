package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.*;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.hvdc.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Independent coupled stiff-source AC/DC faults. No recorded trajectory drives a state or terminal voltage. */
public class Chvdc2CoupledReferenceTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/chvdc2-controller-20261102/captures.zip");
    private static final Path CAPACITOR_ARCHIVE=Path.of("testData/private/verification-artifacts/chvdc2-capacitor-20261102/captures.zip");
    private static final Map<String,JsonNode> PROFILES=new LinkedHashMap<>();
    private static final Map<String,Path> PROFILE_ARCHIVES=new HashMap<>();
    private static final double[] LIMITS={1e-5,1e-5,.01,.01,1e-4,1e-4,1e-4,1e-4,.002,.002,
            1e-5,1e-5,1e-5,1e-5,.002,.002,.002,.002,1e-5,1e-5,1e-5,1e-5,1e-5,1e-5,.002,.01};
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    @BeforeAll static void setup()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent coupled controls");
        Assumptions.assumeTrue(Files.isRegularFile(CAPACITOR_ARCHIVE),"Retain independent capacitance controls");
        PROFILES.clear();PROFILE_ARCHIVES.clear();
        addProfiles(ARCHIVE,"75d8b6a733eb20426dbc8c8fe7c4efdab854c8e11a82da99ee98f6ce76377511");assertEquals(4,PROFILES.size());
        addProfiles(CAPACITOR_ARCHIVE,"03701c023b4d419217368086599ecbe234d767d7993a45b14d0ba0a4965131e0");assertEquals(7,PROFILES.size());
    }
    private static void addProfiles(Path archive,String digest)throws Exception {
        assertEquals(digest,sha(Files.readAllBytes(archive)));var selected=new TreeMap<String,JsonNode>();
        try(var z=new ZipFile(archive.toFile())) {
            var matrix=JSON.readTree(z.getInputStream(z.getEntry("manifest.json")));
            for(var file:matrix.path("files").properties())assertEquals(file.getValue().asText(),sha(z.getInputStream(z.getEntry(file.getKey())).readAllBytes()));
            for(var p:matrix.path("profiles")) {
                String tag=p.path("tag").asText();if(!p.path("repeat").asText().equals("a")||tag.contains("limited"))continue;
                var m=JSON.readTree(z.getInputStream(z.getEntry(tag+"/manifest.json")));
                if(!m.path("status").asText().equals("complete"))continue;
                assertEquals("complete",m.path("status").asText());assertTrue(m.path("initialization_and_network_valid").asBoolean());
                assertEquals(m.path("entered_constants"),m.path("initialized_constants"));
                assertArrayEquals(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes(),z.getInputStream(z.getEntry(tag.substring(0,tag.length()-1)+"b/native.csv")).readAllBytes());
                selected.put(tag,m);PROFILE_ARCHIVES.put(tag,archive);
            }
        }
        PROFILES.putAll(selected);
    }
    static Stream<Arguments> profiles(){return PROFILES.keySet().stream().flatMap(k->Stream.of(false,true).map(shared->Arguments.of(k,shared)));}
    private static LccConverterBoundary converter(JsonNode equipment,boolean rectifier) {
        return new LccConverterBoundary(rectifier,2,equipment.path("EBASE").asDouble()*1000,equipment.path("TR").asDouble(),equipment.path("TAP").asDouble(),equipment.path("XC").asDouble(),1e8);
    }
    /** Exact circuit reduction for the synthetic parallel source branches with frozen
     * classical internal phasors. The captures check every source rotor angle is fixed. */
    private static final class Circuit {
        // Each source has X=.18 on 10000 MVA plus .001 on the 100 MVA network base; two in parallel.
        final double reactance=(.18*100/10000+.001)/2,reference;
        final double[] e=new double[4],v=new double[4],current=new double[4];
        int faultSide=-1,maximumIterations;long solves,iterations,sequence;
        Circuit(Map<String,Double> initial,double reference) {
            this.reference=reference;
            for(int side=0;side<2;side++) {
                double angle=Math.toRadians(initial.get(side==0?"A7":"A9")),magnitude=initial.get(side==0?"V7":"V9");
                double re=magnitude*Math.cos(angle),im=magnitude*Math.sin(angle),p=initial.get(side==0?"VAR9":"VAR11")/100,q=initial.get(side==0?"VAR10":"VAR12")/100;
                double ir=(-p*Math.cos(angle)-q*Math.sin(angle))/magnitude,ii=(-p*Math.sin(angle)+q*Math.cos(angle))/magnitude;
                v[2*side]=re;v[2*side+1]=im;e[2*side]=re+reactance*ii;e[2*side+1]=im-reactance*ir;
            }
        }
        void bind(Chvdc2CoupledRuntime model){model.setBoundary(v[0],v[1],v[2],v[3],reference,0);}
        void solve(Chvdc2CoupledRuntime model) {
            solves++;
            for(int n=1;n<=50;n++) {
                bind(model);model.evaluateInjections(current,0);double error=0;
                for(int side=0;side<2;side++) {
                    double denominator=side==faultSide?1+reactance/.2:1;
                    double re=(e[2*side]-reactance*current[2*side+1])/denominator,im=(e[2*side+1]+reactance*current[2*side])/denominator;
                    error=Math.max(error,Math.hypot(re-v[2*side],im-v[2*side+1]));v[2*side]=re;v[2*side+1]=im;
                }
                iterations++;maximumIterations=Math.max(maximumIterations,n);
                if(error<1e-10){bind(model);return;}
            }
            fail("Coupled AC circuit did not converge in fifty iterations");
        }
        void accept(Chvdc2CoupledRuntime model,double time){solve(model);model.acceptBoundary(time,sequence++);}
    }
    @ParameterizedTest @MethodSource("profiles")
    void completeCoupledFaultTrajectory(String tag,boolean staggered)throws Exception {
        var manifest=PROFILES.get(tag);double h=manifest.path("settings").path("step").asDouble();
        double[] constants=JSON.convertValue(manifest.path("entered_constants"),double[].class);JsonNode equipment;List<Map<String,Double>> rows;
        try(var z=new ZipFile(PROFILE_ARCHIVES.get(tag).toFile())) {
            equipment=JSON.readTree(z.getInputStream(z.getEntry(tag+"/solved-equipment.json")));
            rows=Tiocr1ResetReferenceTest.csv(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes());
            String raw=new String(z.getInputStream(z.getEntry(tag+"/network.raw")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(raw.contains(".18"));assertTrue(raw.contains(".001"));
        }
        var initial=rows.getFirst();for(int source=1;source<=4;source++)for(var row:rows)assertEquals(initial.get("M"+source+"_ANGLE"),row.get("M"+source+"_ANGLE"),"The reduced reference must retain fixed source angles");
        var p=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2U1,new int[]{0,0,1},constants);
        var r=equipment.path("7");var i=equipment.path("9");
        var line=p.prepareDcLine(5,2,r.path("XC").asDouble(),i.path("XC").asDouble(),60,h);
        var model=new Chvdc2CoupledRuntime(p,converter(r,true),converter(i,false),line,i.path("TR").asDouble(),i.path("TAP").asDouble());
        var circuit=new Circuit(initial,initial.get("VAR15")*constants[28]);circuit.bind(model);
        // Preserve measured initial current; restore the sub-channel-resolution ohmic voltage drop.
        model.initialize(initial.get("VAR8")*1000+5*initial.get("VAR5"),initial.get("VAR8")*1000,initial.get("time_s"));
        circuit.accept(model,initial.get("time_s"));
        int count=model.staggeredStateCount();assertEquals(line.capacitanceFarads()>0?18:16,count);int channels=10+count;
        double[] limits=Arrays.copyOf(LIMITS,channels);if(count==18){limits[26]=.1;limits[27]=.01;}
        double[] x=new double[count],f=new double[count],old=new double[count],left=new double[count],trial=new double[count],b=new double[10],maximum=new double[channels],times=new double[channels];
        boolean started=false,event=false;int duplicates=0;
        Path out=Files.createTempDirectory(Path.of("target"),"chvdc2-coupled-"+(staggered?"staggered-":"euler-"));
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.csv"),BenchmarkOutputGuard.estimateCsvBytes(rows.size(),1+3*channels))) {
            w.write("time_s");for(int k=0;k<channels;k++)w.write(",value_"+k+",reference_"+k+",error_"+k);w.write("\n");
            for(int n=0;n<rows.size();n++) {
                var row=rows.get(n);double time=row.get("time_s");
                if(n>0) {
                    if(time==rows.get(n-1).get("time_s")) {
                        model.evaluateStaggered(h,left,0);event=true;duplicates++;
                        circuit.faultSide=duplicates==1?(tag.contains("rectifier-fault")?0:1):-1;
                    }else if(staggered) {
                        circuit.solve(model);model.readStaggeredState(x,0);model.evaluateStaggered(h,f,0);if(!started)System.arraycopy(f,0,old,0,count);
                        if(event){for(int k=0;k<count;k++)f[k]=.5*(f[k]+left[k]);event=false;}
                        for(int k=0;k<count;k++)trial[k]=x[k]+h*(1.5*f[k]-.5*old[k]);model.applyStaggeredState(trial,0,h);model.readStaggeredState(x,0);
                        for(int k=0;k<count;k++)old[k]=x[k]==trial[k]?f[k]:0;started=true;
                    }else {
                        circuit.solve(model);model.step(h,0);circuit.solve(model);model.evaluatePredictorProtection(time,circuit.sequence++);model.step(h,1);
                    }
                    circuit.accept(model,time);
                }
                model.readAcceptedBoundary(b,0);model.readPublishedStates(x,0);
                double[] values={Math.hypot(circuit.v[0],circuit.v[1]),Math.hypot(circuit.v[2],circuit.v[3]),b[2],b[3],b[4],b[5],b[6],b[7],x[6],x[7]};
                double[] reference={row.get("V7"),row.get("V9"),row.get("VAR5"),row.get("VAR7"),row.get("VAR9")/100,row.get("VAR10")/100,row.get("VAR11")/100,row.get("VAR12")/100,row.get("STATE6"),row.get("STATE7")};
                w.write(Double.toString(time));for(int k=0;k<channels;k++){
                    double actual=k<10?values[k]:x[k-10],expected=k<10?reference[k]:row.get("STATE"+(k-10));
                    double error=actual-expected;if(Math.abs(error)>maximum[k]){maximum[k]=Math.abs(error);times[k]=time;}w.write(","+actual+","+expected+","+error);
                }w.write("\n");
                assertFalse(model.inverterShorted(),"Nominal controls must not enter an unobserved protection regime");
            }
        }
        var report=new LinkedHashMap<String,Object>();report.put("profile",tag);report.put("staggered",staggered);report.put("maximum_absolute_errors",maximum);report.put("maximum_error_time_s",times);report.put("limits",limits);
        report.put("entered_capacitance_microfarads",constants[47]);report.put("effective_capacitance_farads",line.capacitanceFarads());report.put("numerical_state_count",count);
        report.put("samples",rows.size());report.put("event_duplicates",duplicates);report.put("network_solves",circuit.solves);report.put("network_iterations",circuit.iterations);report.put("maximum_network_iterations",circuit.maximumIterations);
        report.put("network_model","Two fixed internal-source equivalents with the synthetic source/branch impedances; event fault admittance solved with converter injections");
        report.put("recorded_trajectory_forcing",false);report.put("automatic_attachment_qualified",false);
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"),30000)){JSON.writerWithDefaultPrettyPrinter().writeValue(w,report);}
        assertEquals(2,duplicates);var gates=new ArrayList<org.junit.jupiter.api.function.Executable>();for(int k=0;k<channels;k++){int channel=k;gates.add(()->assertTrue(maximum[channel]<limits[channel],"Boundary/state "+channel+" error "+maximum[channel]+" at "+times[channel]));}assertAll(tag+" / "+staggered,gates);
    }
}
