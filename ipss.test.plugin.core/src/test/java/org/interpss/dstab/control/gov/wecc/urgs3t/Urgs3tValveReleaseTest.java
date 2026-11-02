package org.interpss.dstab.control.gov.wecc.urgs3t;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Independent valve-release controls distinguish numerical storage from bounded observations. */
public class Urgs3tValveReleaseTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/urgs3t-initial-20261102/valve-release-captures.zip");
    private static final String SHA="b99ecd2f0f032c904439216ffa5f2684b141e07f44994512d6b4938cd0846f42";
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Map<String,JsonNode> PROFILES=new TreeMap<>();
    private static final double[] COMMANDS={.2,-.4,.2};
    private static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    @BeforeAll static void setup()throws Exception {
        Urgs3tReferenceTest.setup();Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent valve-release captures");
        assertEquals(SHA,sha(Files.readAllBytes(ARCHIVE)));PROFILES.clear();
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            var matrix=JSON.readTree(z.getInputStream(z.getEntry("capture-matrix.json")));assertEquals(12,matrix.size());
            for(var entry:matrix) {
                String tag=entry.path("tag").asText();var m=JSON.readTree(z.getInputStream(z.getEntry(tag+"/manifest.json")));
                assertEquals("complete",m.path("status").asText());assertEquals(m.path("entered_constants"),m.path("initialized_constants"));
                for(var file:m.path("files").properties())assertEquals(file.getValue().asText(),sha(z.getInputStream(z.getEntry(tag+"/"+file.getKey())).readAllBytes()));
                if(entry.path("repeat").asText().equals("a")) {
                    assertArrayEquals(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes(),z.getInputStream(z.getEntry(tag.substring(0,tag.length()-1)+"b/native.csv")).readAllBytes());
                    PROFILES.put(tag,m);
                }
            }
        }
        assertEquals(6,PROFILES.size());
    }
    private static Path stage(String tag)throws Exception {
        Path out=Files.createTempDirectory(Path.of("target"),"urgs3t-valve-release-");
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            for(String name:List.of("manifest.json","network.raw","dynamic.dyr","native.csv")) {
                var e=z.getEntry(tag+"/"+name);assertNotNull(e);assertTrue(e.getSize()>=0&&e.getSize()<16*1024*1024);
                try(var in=z.getInputStream(e)){Files.copy(in,out.resolve(name));}
            }
        }
        return out;
    }
    static Stream<String> profiles(){return PROFILES.keySet().stream();}
    static Stream<Arguments> coupled(){return profiles().flatMap(tag->Stream.of(false,true).map(shared->Arguments.of(tag,shared)));}
    private static double clip(double x,double[] c){return Math.max(c[7],Math.min(c[6],x));}
    private static double rate(double x,double speed,double reference,double[] c) {
        x=clip(x,c);double f=(reference-speed/c[0]-x)/c[1];
        return x>=c[6]&&f>0||x<=c[7]&&f<0?0:f;
    }
    @ParameterizedTest @MethodSource("profiles")
    void recordedSpeedDistinguishesRetainedCoordinateFromProjectedState(String tag)throws Exception {
        Path out=stage(tag);var m=PROFILES.get(tag);double h=m.path("settings").path("step").asDouble();
        double[] c=JSON.convertValue(m.path("entered_constants"),double[].class);
        var rows=Urgs3tReferenceTest.rows(out.resolve("native.csv"));var g=Urgs3tSharedStateTest.model(c);
        g.getMachine().setPm(rows.getFirst().get("PMECH"));assertTrue(g.initStates(g.getMachine().getDStabBus(),g.getMachine()));
        double[] x=new double[5],f=new double[5],previous=new double[5],left=new double[5],published=new double[5];
        double control=rows.getFirst().get("STATE0"),controlPrevious=0,controlLeft=0,ref=g.getRefPoint();
        boolean event=false;int commands=0;double maximum=0,negativeMaximum=0,residualMaximum=0;
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("replay.csv"),BenchmarkOutputGuard.estimateCsvBytes(rows.size(),5))) {
            w.write("source_time_s,numerical_valve,published_valve,reference_valve,projected_control\n");
            for(int row=0;row<rows.size();row++) {
                var current=rows.get(row);double time=current.get("time_s");
                if(row>0&&rows.get(row-1).get("time_s")>=0) {
                    var before=rows.get(row-1);g.getMachine().setSpeed(1+before.get("SPEED"));
                    if(time==before.get("time_s")) {
                        controlLeft=rate(control,before.get("SPEED"),ref,c);ref+=COMMANDS[commands++];g.applyReferenceEvent(ref);
                        assertTrue(g.readStaggeredInputDiscontinuityLeftRates(left,0));event=true;
                    } else {
                        g.readStaggeredState(x,0);g.evaluateStaggered(h,f,0);double controlRate=rate(control,before.get("SPEED"),ref,c);
                        if(event){for(int i=0;i<5;i++)f[i]=.5*(f[i]+left[i]);controlRate=.5*(controlRate+controlLeft);event=false;}
                        for(int i=0;i<5;i++){x[i]+=h*(1.5*f[i]-.5*previous[i]);previous[i]=f[i];}
                        g.applyStaggeredState(x,0,h);g.completeStaggeredStep(h);
                        double trial=control+h*(1.5*controlRate-.5*controlPrevious);control=clip(trial,c);controlPrevious=control==trial?controlRate:0.;
                    }
                }
                g.readStaggeredState(x,0);g.readPublishedStates(published,0);
                maximum=Math.max(maximum,Math.abs(published[0]-current.get("STATE0")));
                negativeMaximum=Math.max(negativeMaximum,Math.abs(control-current.get("STATE0")));
                residualMaximum=Math.max(residualMaximum,Math.abs(x[0]-published[0]));
                w.write(time+","+x[0]+","+published[0]+","+current.get("STATE0")+","+control+"\n");
            }
        }
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("replay.json"),20000)) {
            JSON.writerWithDefaultPrettyPrinter().writeValue(w,Map.of("profile",tag,"maximum_valve_error",maximum,"projected_negative_control_error",negativeMaximum,"maximum_numerical_projection_residual",residualMaximum,"samples",rows.size(),"coupled_acceptance_is_separate",true));
        }
        assertEquals(3,commands);assertTrue(maximum<1e-6,"Recorded-input valve error "+maximum);
        assertTrue(negativeMaximum>1e-5,"The prior projection must fail independently: "+negativeMaximum);
        assertTrue(residualMaximum>1e-5,"Retained coordinate must be exercised");
    }
    @ParameterizedTest @MethodSource("coupled")
    void fullSolverReleaseRetainsUnchangedTrajectoryGates(String tag,boolean shared)throws Exception {
        Urgs3tReferenceTest.compareCompleteTrajectory(stage(tag),tag,shared);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void physicalObservationIsPureAndBothMethodsRetainTheirNumericalCoordinates(boolean shared)throws Exception {
        double[] c=Urgs3tSharedStateTest.BASE.clone();c[6]=.52;c[7]=.48;c[14]=.3;c[15]=1.2;
        for(var convention:Urgs3tGovernor.RuntimeConvention.values()) {
            var imported=Urgs3tSharedStateTest.model(c);var machine=imported.getMachine();machine.getControllerList().remove(imported);
            var g=new Urgs3tGovernor("release",c,convention);g.setMachine(machine);assertTrue(g.initStates(machine.getDStabBus(),machine));
            double[] raw=new double[5],f=new double[5],published=new double[5];g.readStaggeredState(raw,0);raw[0]=.5199;
            g.applyStaggeredState(raw,0,0);g.setRefPoint(.7);double h=.0005,rate=(.7-.5199)/.1;
            if(shared){g.evaluateStaggered(h,f,0);raw[0]+=h*rate;g.applyStaggeredState(raw,0,h);g.completeStaggeredStep(h);}
            else {g.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,machine,0);g.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,machine,1);}
            g.readStaggeredState(raw,0);g.readPublishedStates(published,0);assertEquals(.52,published[0],0.);
            double expected=convention==Urgs3tGovernor.RuntimeConvention.SOURCE_RECORD?.5199+h*rate*(shared?1:.5):.52;
            assertEquals(expected,raw[0],1e-14);assertEquals(raw[0],g.getIntegrationStates().get("Governor output"),0.);
            var states=g.getIntegrationStates();var histories=g.getIntegrationHistories();
            for(int i=0;i<5;i++){g.readPublishedStates(published,0);assertEquals(.52,g.getNamedStates().get("Governor output"),0.);g.evaluateStaggered(h,f,0);}
            assertEquals(states,g.getIntegrationStates());assertEquals(histories,g.getIntegrationHistories());assertEquals(0.,f[0],0.);
            assertEquals(((1-.3/1.2)*(.52-c[9])-raw[1])/(c[15]*c[2]),f[1],1e-14,"Engine sees the physical valve");
            g.applyReferenceEvent(.3);g.evaluateStaggered(h,f,0);assertEquals(-2.2,f[0],1e-14,"Release uses bounded physical feedback");
            machine.setPm(.5);assertTrue(g.initStates(machine.getDStabBus(),machine));g.readStaggeredState(raw,0);assertEquals(.5,raw[0],0.);
        }
    }
}
