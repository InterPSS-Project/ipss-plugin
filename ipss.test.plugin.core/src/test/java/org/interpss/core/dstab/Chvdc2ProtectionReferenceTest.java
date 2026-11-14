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

/** Complete prescribed-input RAML comparisons; rejected network captures cannot qualify protection circuits. */
public class Chvdc2ProtectionReferenceTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/chvdc2-converter-20261102/protection-captures.zip");
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Map<String,JsonNode> VALID=new TreeMap<>();
    private static JsonNode matrix;
    private static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    @BeforeAll static void setup()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent protection captures");
        assertEquals("5245279e56e57373bdd8afc23fc5e033b73aef0b5e667f41a9d50c4e7ae53b16",sha(Files.readAllBytes(ARCHIVE)));VALID.clear();
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            matrix=JSON.readTree(z.getInputStream(z.getEntry("manifest.json")));
            for(var file:matrix.path("files").properties())assertEquals(file.getValue().asText(),sha(z.getInputStream(z.getEntry(file.getKey())).readAllBytes()));
            for(var profile:matrix.path("profiles")) {
                if(!profile.path("status").asText().equals("complete")||!profile.path("repeat").asText().equals("a"))continue;
                String key=profile.path("batch").asText()+"/"+profile.path("tag").asText();
                var m=JSON.readTree(z.getInputStream(z.getEntry(key+"/manifest.json")));
                assertEquals("raml",m.path("settings").path("controller_profile").asText());assertEquals(m.path("entered_constants"),m.path("initialized_constants"));
                assertTrue(m.path("initialization_and_network_valid").asBoolean());assertTrue(m.path("excitation_observed").asBoolean());
                assertArrayEquals(z.getInputStream(z.getEntry(key+"/native.csv")).readAllBytes(),z.getInputStream(z.getEntry(key.substring(0,key.length()-1)+"b/native.csv")).readAllBytes());
                assertNull(VALID.put(key,m));
            }
        }
        assertEquals(2,VALID.size());
    }
    static Stream<Arguments> profiles(){return VALID.keySet().stream().flatMap(k->Stream.of(false,true).map(shared->Arguments.of(k,shared)));}
    @ParameterizedTest @MethodSource("ramlCaptures")
    void capturedRamlPickupChangesLimitBeforeElectricalBoundary(String key)throws Exception {
        List<Map<String,Double>> rows;
        try(var z=new ZipFile(ARCHIVE.toFile())){
            rows=Tiocr1ResetReferenceTest.csv(z.getInputStream(z.getEntry(key+"/native.csv")).readAllBytes());
        }
        int pickup=-1;
        for(int i=1;i<rows.size();i++)if(rows.get(i-1).get("VAR18")==0&&rows.get(i).get("VAR18")>0){
            assertEquals(-1,pickup,"One first RAML pickup");pickup=i;
        }
        assertTrue(pickup>0&&pickup+1<rows.size());
        var before=rows.get(pickup-1);var marker=rows.get(pickup);var electrical=rows.get(pickup+1);
        assertEquals(marker.get("time_s"),marker.get("VAR18"),1e-9);
        assertTrue(marker.get("VAR19")>30&&before.get("VAR19")<10,"The RAML limit changes at pickup");
        assertEquals(0.,marker.get("STATE14"),1e-8,"The published washout is still its left state");
        assertTrue(marker.get("STATE10")>marker.get("VAR23"),"The published voltage has not crossed the RAML threshold");
        assertTrue(Math.abs(marker.get("STATE6")-before.get("STATE6"))<.01,"The firing angle remains on the old boundary");
        assertTrue(Math.abs(marker.get("VAR9")-before.get("VAR9"))<.3,"Rectifier power remains on the old boundary");
        assertTrue(electrical.get("STATE6")-marker.get("STATE6")>5,"The next row publishes the RAML firing angle");
        assertTrue(marker.get("VAR9")-electrical.get("VAR9")>40,"The next row publishes the electrical power change");
    }
    static Stream<String> ramlCaptures(){return VALID.keySet().stream();}
    @ParameterizedTest @MethodSource("profiles")
    void replayCompleteRamlStateAndMinimumAngle(String key,boolean staggered)throws Exception {
        var manifest=VALID.get(key);double h=manifest.path("settings").path("step").asDouble();
        var m=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2U1,JSON.convertValue(manifest.path("settings").path("flags"),int[].class),
                JSON.convertValue(manifest.path("entered_constants"),double[].class)).prepareProtection();
        List<Map<String,Double>> rows;try(var z=new ZipFile(ARCHIVE.toFile())){rows=Tiocr1ResetReferenceTest.csv(z.getInputStream(z.getEntry(key+"/native.csv")).readAllBytes());}
        assertEquals(manifest.path("samples").asInt(),rows.size());var first=rows.getFirst();
        m.initialize(first.get("V7"),first.get("V9"),first.get("VAR4"),first.get("time_s"));assertEquals(first.get("VAR23"),m.preparedRamVoltagePu(),1e-7);
        double[] x=new double[1],f=new double[1],left=new double[1],max=new double[3],times=new double[3];double old=0,prior=Double.NaN;
        boolean started=false;long observedRevision=0;int duplicates=0,activations=0,releases=0;
        Path out=Files.createTempDirectory(Path.of("target"),"chvdc2-protection-"+(staggered?"staggered-":"euler-"));
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("replay.csv"),BenchmarkOutputGuard.estimateCsvBytes(rows.size(),11))) {
            w.write("time_s,state_deg,reference_state_deg,state_error_deg,minimum_deg,reference_minimum_deg,minimum_error_deg,timer_s,reference_timer_s,timer_error_s,raml_active\n");
            for(int n=0;n<rows.size();n++) {
                var row=rows.get(n);double time=row.get("time_s");
                if(n>0&&time!=prior) {
                    if(staggered) {
                        m.readStaggeredState(x,0);m.evaluateStaggered(h,f,0);
                        if(!started)old=f[0];
                        long revision=m.staggeredInputDiscontinuityVersion();
                        if(revision!=observedRevision){assertTrue(m.readStaggeredInputDiscontinuityLeftRates(left,0));f[0]=.5*(f[0]+left[0]);observedRevision=revision;}
                        x[0]+=h*(1.5*f[0]-.5*old);old=f[0];m.applyStaggeredState(x,0,h);started=true;
                    }else{m.step(h,0);m.step(h,1);}
                }else if(n>0)duplicates++;
                boolean before=m.ramlActive();m.setInput(row.get("STATE10"),row.get("V9"),row.get("VAR4"));m.acceptBoundary(time,n);
                if(m.ramlActive()!=before){if(m.ramlActive())activations++;else releases++;}assertFalse(m.inverterShorted());
                m.readStaggeredState(x,0);double[] value={x[0],m.rectifierMinimumDegrees(),m.ramlTimerMarker()},reference={row.get("STATE14"),row.get("VAR19"),row.get("VAR18")};
                w.write(Double.toString(time));
                for(int channel=0;channel<3;channel++){double error=value[channel]-reference[channel];if(Math.abs(error)>max[channel]){max[channel]=Math.abs(error);times[channel]=time;}w.write(","+value[channel]+","+reference[channel]+","+error);}
                w.write(","+(m.ramlActive()?1:0)+"\n");prior=time;
            }
        }
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"),20000)) {
            JSON.writerWithDefaultPrettyPrinter().writeValue(w,Map.of("profile",key,"staggered",staggered,"maximum_absolute_errors",max,"maximum_error_time_s",times,
                    "limits",new double[]{.002,.002,h*1.01},"samples",rows.size(),"event_duplicates",duplicates,"activations",activations,"releases",releases,"full_coupled_acceptance_is_separate",true));
        }
        assertEquals(2,duplicates);assertEquals(1,activations);assertEquals(1,releases);
        assertAll(()->assertTrue(max[0]<.002,"RAML state discrepancy "+max[0]+" at "+times[0]),
                ()->assertTrue(max[1]<.002,"RAML output discrepancy "+max[1]+" at "+times[1]),
                ()->assertTrue(max[2]<h*1.01,"RAML timer discrepancy "+max[2]+" at "+times[2]));
    }
    @Test void nonconvergedCommutationCapturesRemainRejectedEvidence()throws Exception {
        int rejected=0;
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            for(var profile:matrix.path("profiles")) {
                if(!profile.path("status").asText().equals("rejected-quality"))continue;rejected++;
                String key=profile.path("batch").asText()+"/"+profile.path("tag").asText();
                var m=JSON.readTree(z.getInputStream(z.getEntry(key+"/manifest.json")));
                assertFalse(m.path("initialization_and_network_valid").asBoolean());assertTrue(profile.path("commutation_failure_active").asBoolean());
                var rows=Tiocr1ResetReferenceTest.csv(z.getInputStream(z.getEntry(key+"/native.csv")).readAllBytes());assertEquals(m.path("samples").asInt(),rows.size());
                assertTrue(rows.stream().anyMatch(r->r.get("VAR22")<0&&r.get("VAR8")==0&&r.get("VAR7")>0),"Shorting does not reset line current");
                assertTrue(rows.stream().anyMatch(r->r.get("VAR22")<0&&r.get("VAR8")==0&&Math.abs(r.get("VAR11"))>1),"Electrical publication during failure requires separate qualification");
            }
        }
        assertEquals(5,rejected);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void acceptedCommutationHistoryMatchesEverySampleOfTheValidControl(boolean staggered)throws Exception {
        Path archive=Path.of("testData/private/verification-artifacts/chvdc2-protection-20261102/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain cold-repeated commutation control");
        assertEquals("32079591003689230139fa893b5c3e07a315dc4d411442372838e10da3b658ea",sha(Files.readAllBytes(archive)));
        String key=null;JsonNode manifest=null,equipment=null;List<Map<String,Double>> rows=null;int rejected=0;
        try(var z=new ZipFile(archive.toFile())) {
            var root=JSON.readTree(z.getInputStream(z.getEntry("manifest.json")));
            for(var file:root.path("files").properties())assertEquals(file.getValue().asText(),sha(z.getInputStream(z.getEntry(file.getKey())).readAllBytes()));
            for(var profile:root.path("profiles")) {
                if(profile.path("status").asText().equals("rejected-quality")){rejected++;continue;}
                if(!profile.path("settings").path("repeat").asText().equals("a"))continue;assertNull(key);key=profile.path("key").asText();
                manifest=JSON.readTree(z.getInputStream(z.getEntry(key+"/manifest.json")));
                equipment=JSON.readTree(z.getInputStream(z.getEntry(key+"/solved-equipment.json"))).path("9");
                assertEquals("complete",manifest.path("status").asText());assertTrue(manifest.path("initialization_and_network_valid").asBoolean());
                assertEquals(manifest.path("entered_constants"),manifest.path("initialized_constants"));
                byte[] csv=z.getInputStream(z.getEntry(key+"/native.csv")).readAllBytes();
                assertArrayEquals(csv,z.getInputStream(z.getEntry(key.substring(0,key.length()-1)+"b/native.csv")).readAllBytes());rows=Tiocr1ResetReferenceTest.csv(csv);
            }
        }
        assertEquals(2,rejected);assertNotNull(manifest);assertNotNull(rows);
        double h=manifest.path("settings").path("step").asDouble();assertEquals(.0005,h);
        var m=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2U1,JSON.convertValue(manifest.path("settings").path("flags"),int[].class),
                JSON.convertValue(manifest.path("entered_constants"),double[].class)).prepareProtection();
        var first=rows.getFirst();m.initialize(first.get("V7"),first.get("V9"),first.get("VAR4"),first.get("time_s"));
        var nominalClockModel=new Chvdc2Parameters("clock-control",Chvdc2Parameters.Dialect.CHVDC2U1,
                JSON.convertValue(manifest.path("settings").path("flags"),int[].class),
                JSON.convertValue(manifest.path("entered_constants"),double[].class)).prepareProtection();
        double nominalTime=-2*h;int nominalClockMismatches=0;
        nominalClockModel.initialize(first.get("V7"),first.get("V9"),first.get("VAR4"),nominalTime);
        int mismatches=0,transitions=0,sourceTransitions=0,duplicates=0;boolean sourceFailed=false;double maximumClockError=0,maximumPowerClosure=0,prior=Double.NaN;
        assertNotNull(equipment);var inverter=new LccConverterBoundary(false,2,equipment.path("EBASE").asDouble()*1000,
                equipment.path("TR").asDouble(),equipment.path("TAP").asDouble(),equipment.path("XC").asDouble(),1e8);
        double[] x=new double[1],f=new double[1],power=new double[3],powerErrors=new double[2];Path out=Files.createTempDirectory(Path.of("target"),"chvdc2-commutation-"+(staggered?"staggered-":"euler-"));
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("replay.csv"),BenchmarkOutputGuard.estimateCsvBytes(rows.size(),13))) {
            w.write("time_s,shorted,reference_shorted,transition_time_s,reference_transition_time_s,source_ac_power_pu,source_dc_power_pu,history_P_pu,P_error_pu,history_Q_pu,Q_error_pu,nominal_time_s,nominal_clock_shorted\n");
            for(int n=0;n<rows.size();n++) {
                var row=rows.get(n);double time=row.get("time_s");
                if(n>0&&time!=prior) {
                    nominalTime+=h;
                    if(staggered){m.readStaggeredState(x,0);m.evaluateStaggered(h,f,0);x[0]+=h*f[0];m.applyStaggeredState(x,0,h);}
                    else{m.step(h,0);m.step(h,1);}
                }else if(n>0)duplicates++;
                double electricalVoltage=inverter.maximumDcVoltage(row.get("V9"),row.get("VAR7"),Math.toRadians(m.acceptedInverterExtinctionDegrees()));
                inverter.evaluateDcVoltage(row.get("V9"),electricalVoltage,row.get("VAR7"),power,0);
                double pError=power[0]-row.get("VAR11")/100,qError=power[1]-row.get("VAR12")/100;
                powerErrors[0]=Math.max(powerErrors[0],Math.abs(pError));powerErrors[1]=Math.max(powerErrors[1],Math.abs(qError));
                boolean before=m.inverterShorted();m.setInput(row.get("STATE10"),row.get("V9"),row.get("VAR4"));m.acceptBoundary(time,n);
                if(m.inverterShorted()!=before)transitions++;boolean reference=row.get("VAR22")<0;
                nominalClockModel.setInput(row.get("STATE10"),row.get("V9"),row.get("VAR4"));nominalClockModel.acceptBoundary(nominalTime,n);
                if(nominalClockModel.inverterShorted()!=reference)nominalClockMismatches++;
                if(reference!=sourceFailed)sourceTransitions++;sourceFailed=reference;if(reference!=m.inverterShorted())mismatches++;
                if(row.get("VAR21")!=999){assertTrue(m.hasCommutationTransition());maximumClockError=Math.max(maximumClockError,Math.abs(m.commutationTransitionTime()-row.get("VAR21")));}
                else assertFalse(m.hasCommutationTransition());
                assertFalse(m.ramlActive());assertEquals(0,m.getIntegrationStates().values().iterator().next());
                double sourcePower=row.get("VAR11")/100,dcPower=-row.get("VAR8")*1000*row.get("VAR7")/1e8;
                maximumPowerClosure=Math.max(maximumPowerClosure,Math.abs(row.get("M3_P")+row.get("M4_P")-sourcePower));
                w.write(time+","+(m.inverterShorted()?1:0)+","+(reference?1:0)+","+m.commutationTransitionTime()+","+row.get("VAR21")+","+sourcePower+","+dcPower+","+power[0]+","+pError+","+power[1]+","+qError+","+nominalTime+","+(nominalClockModel.inverterShorted()?1:0)+"\n");prior=time;
            }
        }
        var report=new LinkedHashMap<String,Object>();report.put("profile",key);report.put("staggered",staggered);report.put("samples",rows.size());report.put("event_duplicates",duplicates);
        report.put("flag_mismatches",mismatches);report.put("transitions",transitions);report.put("reference_transitions",sourceTransitions);report.put("maximum_clock_error_s",maximumClockError);
        report.put("maximum_source_ac_network_power_closure_pu",maximumPowerClosure);report.put("electrical_circuit_and_default_duration_remain_unqualified",true);
        report.put("maximum_accepted_history_power_errors_pu",powerErrors);
        report.put("nominal_double_clock_flag_mismatches",nominalClockMismatches);
        report.put("clock_control_uses_identical_recorded_inputs",true);
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"),20000)){JSON.writerWithDefaultPrettyPrinter().writeValue(w,report);}
        assertEquals(manifest.path("samples").asInt(),rows.size());assertEquals(2,duplicates);assertTrue(transitions>2);
        assertEquals(0,mismatches);assertEquals(sourceTransitions,transitions);assertEquals(0,maximumClockError,1e-10);assertTrue(maximumPowerClosure<1e-5);
        assertTrue(nominalClockMismatches>0,"The strict duration comparison must distinguish the two accumulated clocks");
        assertTrue(powerErrors[0]<1e-5,"Committed-history P error "+powerErrors[0]);assertTrue(powerErrors[1]<1e-5,"Committed-history Q error "+powerErrors[1]);
    }
}
