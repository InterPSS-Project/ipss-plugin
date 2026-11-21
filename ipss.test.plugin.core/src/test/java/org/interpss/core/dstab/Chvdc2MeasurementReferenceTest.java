package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.*;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import com.interpss.dstab.hvdc.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Independent memoryless observations and clock controls, separate from coupled trajectory acceptance. */
public class Chvdc2MeasurementReferenceTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/chvdc2-measurements-20261102/captures.zip");
    private static final Path CLOCK=Path.of("testData/private/verification-artifacts/chvdc2-boundary-clock-20261102/captures.zip");
    private static final Map<String,JsonNode> PROFILES=new TreeMap<>();
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static byte[] bytes(ZipFile z,String tag,String file)throws Exception{return z.getInputStream(z.getEntry(tag+"/"+file)).readAllBytes();}
    private static void verifyArchive(Path archive,String digest)throws Exception {
        assertEquals(digest,sha(Files.readAllBytes(archive)));
        try(var z=new ZipFile(archive.toFile())) {
            var matrix=JSON.readTree(z.getInputStream(z.getEntry("manifest.json")));
            for(var file:matrix.path("files").properties())assertEquals(file.getValue().asText(),sha(z.getInputStream(z.getEntry(file.getKey())).readAllBytes()));
        }
    }
    @BeforeAll static void setup()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE)&&Files.isRegularFile(CLOCK),"Retain independent measurement/clock controls");
        verifyArchive(ARCHIVE,"d9d055e905044a4de552955beb536267699184eae70fe0498746f1b3409ac707");
        verifyArchive(CLOCK,"3637371edc1a70378883617fac6f2cab04a4e62eb0f456fb71578745732b84f1");PROFILES.clear();
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            var matrix=JSON.readTree(z.getInputStream(z.getEntry("manifest.json")));
            for(var profile:matrix.path("profiles"))if(profile.path("repeat").asText().equals("a")) {
                String tag=profile.path("tag").asText();var m=JSON.readTree(bytes(z,tag,"manifest.json"));
                assertEquals("complete",m.path("status").asText());assertTrue(m.path("initialization_and_network_valid").asBoolean());assertTrue(m.path("finite_measurements").asBoolean());
                assertEquals(m.path("entered_constants"),m.path("initialized_constants"));
                assertArrayEquals(bytes(z,tag,"native.csv"),bytes(z,tag.substring(0,tag.length()-1)+"b","native.csv"));PROFILES.put(tag,m);
            }
        }
        assertEquals(4,PROFILES.size());
    }
    static Stream<Arguments> bypass(){return PROFILES.entrySet().stream().filter(e->e.getValue().path("entered_constants").get(17).asDouble()==0)
            .flatMap(e->Stream.of(false,true).map(staggered->Arguments.of(e.getKey(),staggered)));}
    private static void input(Chvdc2ControlAssembly model,Map<String,Double> row,double rated) {
        model.setInput(row.get("VAR5"),row.get("VAR7"),row.get("VAR6"),row.get("VAR8"),row.get("V7"),row.get("V9"),row.get("VAR15")*rated,row.get("VAR0"),row.get("VAR4"));
    }
    @ParameterizedTest @MethodSource("bypass")
    void completeBypassedMeasurementObservationsRemainPureUnderBothMethods(String tag,boolean staggered)throws Exception {
        var manifest=PROFILES.get(tag);double[] constants=JSON.convertValue(manifest.path("entered_constants"),double[].class);double h=manifest.path("settings").path("step").asDouble();
        List<Map<String,Double>> rows;JsonNode equipment;
        try(var z=new ZipFile(ARCHIVE.toFile())){rows=Tiocr1ResetReferenceTest.csv(bytes(z,tag,"native.csv"));equipment=JSON.readTree(bytes(z,tag,"solved-equipment.json"));}
        var p=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2U1,new int[]{0,0,1},constants);
        var m=p.prepareControls(equipment.path("9").path("TR").asDouble(),equipment.path("9").path("TAP").asDouble());var first=rows.getFirst();
        input(m,first,constants[28]);m.initialize(first.get("STATE6"),first.get("STATE7"),first.get("time_s"));
        double[] raw=new double[15],check=new double[15],published=new double[15],rate=new double[15],old=new double[15],maximum=new double[4];int[] indices={0,1,10,11};boolean started=false;int duplicates=0;
        Path out=Files.createTempDirectory(Path.of("target"),"chvdc2-measurement-observation-");
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.csv"),BenchmarkOutputGuard.estimateCsvBytes(rows.size(),17))) {
            w.write("time_s");for(int k:indices)w.write(",published_"+k+",reference_"+k+",error_"+k+",stored_"+k);w.write("\n");
            for(int n=0;n<rows.size();n++) {
                var row=rows.get(n);double time=row.get("time_s");
                if(n>0&&time!=rows.get(n-1).get("time_s")) {
                    if(staggered){m.readStaggeredState(raw,0);m.evaluateStaggered(h,rate,0);for(int k=0;k<15;k++)raw[k]+=h*(started?1.5*rate[k]-.5*old[k]:rate[k]);m.applyStaggeredState(raw,0,h);System.arraycopy(rate,0,old,0,15);started=true;}
                    else{m.step(h,0);m.step(h,1);}
                }else if(n>0)duplicates++;
                input(m,row,constants[28]);m.readStaggeredState(raw,0);var histories=m.getIntegrationHistories();
                m.readPublishedStates(published,0);m.readPublishedStates(check,0);assertArrayEquals(published,check);
                m.readStaggeredState(check,0);assertArrayEquals(raw,check);assertEquals(histories,m.getIntegrationHistories());
                w.write(Double.toString(time));
                for(int j=0;j<indices.length;j++){
                    int k=indices[j];double reference=row.get("STATE"+k),error=published[k]-reference;maximum[j]=Math.max(maximum[j],Math.abs(error));
                    w.write(","+published[k]+","+reference+","+error+","+raw[k]);
                }w.write("\n");
            }
        }
        var report=Map.of("profile",tag,"staggered",staggered,"samples",rows.size(),"event_duplicates",duplicates,"maximum_absolute_observation_error_pu",maximum,
                "physical_inputs_prescribed",true,"complete_controller_trajectory_qualified",false,"integration_coordinates_mutated_by_observation",false);
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"),20000)){JSON.writerWithDefaultPrettyPrinter().writeValue(w,report);}
        assertEquals(2,duplicates);for(double error:maximum)assertTrue(error<1e-7,"Zero-lag observation differs from independent source: "+error);
    }
    @ParameterizedTest @ValueSource(doubles={.0005,.00025})
    void algebraicCurrentUsesPreviousVoltageAndAcceptedAngles(double h)throws Exception {
        String tag=PROFILES.entrySet().stream().filter(e->e.getValue().path("settings").path("step").asDouble()==h&&e.getValue().path("settings").path("flags").get(2).asInt()==0).map(Map.Entry::getKey).findFirst().orElseThrow();
        var manifest=PROFILES.get(tag);double[] constants=JSON.convertValue(manifest.path("entered_constants"),double[].class);List<Map<String,Double>> rows;JsonNode equipment;
        try(var z=new ZipFile(ARCHIVE.toFile())){rows=Tiocr1ResetReferenceTest.csv(bytes(z,tag,"native.csv"));equipment=JSON.readTree(bytes(z,tag,"solved-equipment.json"));}
        var parameters=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2U1,new int[]{0,0,0},constants);
        var r=equipment.path("7");var i=equipment.path("9");
        var rectifier=new LccConverterBoundary(true,2,r.path("EBASE").asDouble()*1000,r.path("TR").asDouble(),r.path("TAP").asDouble(),r.path("XC").asDouble(),1e8);
        var inverter=new LccConverterBoundary(false,2,i.path("EBASE").asDouble()*1000,i.path("TR").asDouble(),i.path("TAP").asDouble(),i.path("XC").asDouble(),1e8);
        var line=parameters.prepareDcLine(5,2,r.path("XC").asDouble(),i.path("XC").asDouble(),60,h);
        var model=new Chvdc2CoupledRuntime(parameters,rectifier,inverter,line,i.path("TR").asDouble(),i.path("TAP").asDouble());
        var first=rows.getFirst();bind(model,first,constants[28]);model.initialize(first.get("VAR8")*1000+5*first.get("VAR5"),first.get("VAR8")*1000,first.get("time_s"));
        double[] state=new double[15],check=new double[15],boundary=new double[10];double maximum=0,rawMaximum=0,projectionMaximum=0,rawCurrent=first.get("VAR5");int duplicates=0;
        double resistance=5+3/Math.PI*2*(r.path("XC").asDouble()+i.path("XC").asDouble());
        Path out=Files.createTempDirectory(Path.of("target"),"chvdc2-algebraic-sampling-");
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.csv"),BenchmarkOutputGuard.estimateCsvBytes(rows.size(),8))) {
            w.write("time_s,current_A,reference_A,error_A,raw_angle_current_A,raw_angle_error_A,rectifier_angle_projection_deg,inverter_angle_projection_deg\n");
            for(int n=0;n<rows.size();n++) {
                var row=rows.get(n);
                if(n>0) {
                    var previous=rows.get(n-1);bind(model,previous,constants[28]);
                    if(!row.get("time_s").equals(previous.get("time_s"))) {
                        for(int k=0;k<15;k++)state[k]=row.get("STATE"+k);
                        model.applyStaggeredState(state,0,h);
                        model.completeStaggeredStep(h);
                        rawCurrent=(rectifier.dcVoltageAtFiringAngle(previous.get("V7"),0,Math.toRadians(row.get("STATE6")))
                                -inverter.dcVoltageAtFiringAngle(previous.get("V9"),0,Math.toRadians(row.get("STATE7"))))/resistance;
                    }else duplicates++;
                }
                bind(model,row,constants[28]);model.readStaggeredState(state,0);var histories=model.getIntegrationHistories();
                model.readBoundary(boundary,0);model.readStaggeredState(check,0);assertArrayEquals(state,check);assertEquals(histories,model.getIntegrationHistories());
                double error=boundary[2]-row.get("VAR5");maximum=Math.max(maximum,Math.abs(error));assertEquals(boundary[2],boundary[3]);
                rawMaximum=Math.max(rawMaximum,Math.abs(rawCurrent-row.get("VAR5")));projectionMaximum=Math.max(projectionMaximum,Math.abs(boundary[2]-rawCurrent));
                w.write(row.get("time_s")+","+boundary[2]+","+row.get("VAR5")+","+error+","+rawCurrent+","+(rawCurrent-row.get("VAR5"))+","+(state[6]-row.get("STATE6"))+","+(state[7]-row.get("STATE7"))+"\n");
            }
        }
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"),20000)) {
            JSON.writerWithDefaultPrettyPrinter().writeValue(w,Map.of("profile",tag,"samples",rows.size(),"event_duplicates",duplicates,
                    "maximum_current_error_A",maximum,"maximum_raw_angle_error_A",rawMaximum,"maximum_projection_contribution_A",projectionMaximum,
                    "reference_coordinates_and_inputs_prescribed",true,"complete_coupled_trajectory_qualified",false));
        }
        assertEquals(2,duplicates);assertTrue(rawMaximum<.0031,"Raw-angle sampling error: "+rawMaximum);
        assertTrue(maximum<.01,"Algebraic current sampling must satisfy the existing coupled-current gate: "+maximum);
    }
    private static void bind(Chvdc2CoupledRuntime model,Map<String,Double> row,double rated) {
        double ar=Math.toRadians(row.get("A7")),ai=Math.toRadians(row.get("A9"));
        model.setBoundary(row.get("V7")*Math.cos(ar),row.get("V7")*Math.sin(ar),row.get("V9")*Math.cos(ai),row.get("V9")*Math.sin(ai),row.get("VAR15")*rated,row.get("VAR0"));
    }
    @ParameterizedTest @ValueSource(doubles={.0005,.00025})
    void firstMeasurementIncrementIsIndependentOfDcLineDynamics(double h)throws Exception {
        String tag=PROFILES.entrySet().stream().filter(e->e.getValue().path("settings").path("step").asDouble()==h&&e.getValue().path("settings").path("flags").get(2).asInt()==0).map(Map.Entry::getKey).findFirst().orElseThrow();
        List<Map<String,Double>> algebraic,dynamic=null;
        try(var z=new ZipFile(ARCHIVE.toFile())){algebraic=Tiocr1ResetReferenceTest.csv(bytes(z,tag,"native.csv"));}
        try(var z=new ZipFile(CLOCK.toFile())) {
            var matrix=JSON.readTree(z.getInputStream(z.getEntry("manifest.json")));
            for(var profile:matrix.path("profiles"))if(profile.path("repeat").asText().equals("a")) {
                String name=profile.path("tag").asText();var settings=JSON.readTree(bytes(z,name,"manifest.json")).path("settings");
                if(settings.path("step").asDouble()==h&&!settings.hasNonNull("boundary_probe")){dynamic=Tiocr1ResetReferenceTest.csv(bytes(z,name,"native.csv"));break;}
            }
        }
        assertNotNull(dynamic);assertEquals(algebraic.size(),dynamic.size());int event=-1;
        for(int n=0;n<algebraic.size();n++){
            var row=algebraic.get(n);assertEquals(row.get("time_s"),dynamic.get(n).get("time_s"));
            assertEquals(0,row.get("STATE15"));assertEquals(0,row.get("STATE16"));assertEquals(0,row.get("STATE17"));
            if(n>0&&event<0&&row.get("time_s").equals(algebraic.get(n-1).get("time_s")))event=n;
        }
        assertTrue(event>0);var before=algebraic.get(event-1);var after=algebraic.get(event);var first=algebraic.get(event+1);
        assertEquals(before.get("VAR5"),after.get("VAR5"),"An algebraic current is still held through the zero-time event side");
        assertEquals(before.get("STATE0"),first.get("STATE0"));assertNotEquals(first.get("VAR5"),before.get("VAR5"));
        assertEquals(dynamic.get(event+1).get("STATE10"),first.get("STATE10"));
        assertTrue(Math.abs(dynamic.get(event+1).get("VAR5")-first.get("VAR5"))>1,"Line modes must be physically distinguished by the control");
    }
}
