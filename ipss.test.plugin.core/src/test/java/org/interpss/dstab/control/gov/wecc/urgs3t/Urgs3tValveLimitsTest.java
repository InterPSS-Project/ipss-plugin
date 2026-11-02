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
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Physical valve limits and independently diagnosed infeasible initial operating points. */
public class Urgs3tValveLimitsTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/urgs3t-initial-20261102/valve-limit-captures.zip");
    private static final String SHA="9ce0fbb8bcd04e0611b800fd7477ff85ac1365393b0f04ccdc16b478a7992cd0";
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Map<String,JsonNode> PROFILES=new TreeMap<>();
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    @BeforeAll static void setup()throws Exception {
        Urgs3tReferenceTest.setup();
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent valve-limit captures");
        assertEquals(SHA,sha(Files.readAllBytes(ARCHIVE)));PROFILES.clear();
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            var matrix=JSON.readTree(z.getInputStream(z.getEntry("capture-matrix.json")));assertEquals(16,matrix.size());
            for(var entry:matrix) {
                String tag=entry.path("tag").asText();var m=JSON.readTree(z.getInputStream(z.getEntry(tag+"/manifest.json")));
                String expected=entry.path("profile").asText().equals("position-limits")?"complete":"rejected-quality";
                assertEquals(expected,m.path("status").asText());assertEquals(m.path("entered_constants"),m.path("initialized_constants"));
                for(var file:m.path("files").properties())assertEquals(file.getValue().asText(),sha(z.getInputStream(z.getEntry(tag+"/"+file.getKey())).readAllBytes()));
                String log=new String(z.getInputStream(z.getEntry(tag+"/native.log")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                assertFalse(log.contains("Network not converged"));assertFalse(log.contains("Incomplete data record"));
                if(expected.equals("rejected-quality")){assertTrue(log.contains("Initial conditions suspect:"));assertTrue(log.contains("Initialized out of limit"));}
                if(entry.path("repeat").asText().equals("a")) {
                    assertArrayEquals(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes(),z.getInputStream(z.getEntry(tag.substring(0,tag.length()-1)+"b/native.csv")).readAllBytes());
                    PROFILES.put(tag,entry);
                }
            }
        }
        assertEquals(8,PROFILES.size());
    }
    private static Path stage(String tag)throws Exception {
        Path out=Files.createTempDirectory(Path.of("target"),"urgs3t-valve-boundary-");
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            for(String name:List.of("manifest.json","network.raw","dynamic.dyr","native.csv")) {
                var e=z.getEntry(tag+"/"+name);assertNotNull(e);assertTrue(e.getSize()>=0&&e.getSize()<16*1024*1024);
                try(var in=z.getInputStream(e)){Files.copy(in,out.resolve(name));}
            }
        }
        return out;
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothInitializersKeepEnteredBoundsAndExposeNonEquilibrium(boolean shared)throws Exception {
        for(var entry:PROFILES.entrySet()) {
            Path out=stage(entry.getKey());var expected=Urgs3tReferenceTest.rows(out.resolve("native.csv")).getFirst();
            var loader=new PSSEMultiFileLoader();var context=loader.loadDStab(out.resolve("network.raw").toString(),out.resolve("dynamic.dyr").toString());
            assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());var a=context.getDynSimuAlgorithm();
            assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.0005);
            a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            a.setSolver(shared?new StaggeredEulerSolver(a):new DefaultDynamicSimulationSolver(a));
            var g=(Urgs3tGovernor)context.getDStabilityNet().getMachine("Bus1-mach1").getGovernor();double[] entered=g.getConstants();
            assertTrue(a.initialization());double[] states=new double[5],vars=new double[5];g.readStaggeredState(states,0);g.readPublishedVariables(vars,0);
            for(int i=0;i<5;i++){assertEquals(expected.get("STATE"+i),states[i],8e-8,entry.getKey()+" STATE"+i);assertEquals(expected.get("VAR"+i),vars[i],8e-8,entry.getKey()+" VAR"+i);}
            assertEquals(expected.get("PMECH"),g.getOutput(g.getMachine()),8e-8);assertArrayEquals(entered,g.getConstants(),0.);
            var histories=g.getIntegrationHistories();assertEquals(entered[6],histories.get("Prepared upper valve limit"),0.);assertEquals(entered[7],histories.get("Prepared lower valve limit"),0.);
            var rates=g.getStateDerivativeSnapshot().rates();boolean suspect=entry.getValue().path("status").asText().equals("rejected-quality");
            if(suspect){assertTrue(Math.abs(rates.get("Engine state"))>.1);assertTrue(Math.abs(rates.get("Valve average"))>.05);}
            else for(double rate:rates.values())assertEquals(0.,rate,1e-12);
            try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("initialization.json"),20000)) {
                JSON.writerWithDefaultPrettyPrinter().writeValue(writer,Map.of("profile",entry.getKey(),"method",shared?"staggered":"modified-euler","reference_status",entry.getValue().path("status").asText(),"states",g.getIntegrationStates(),"rates",rates,"histories",histories,"initial_output",g.getOutput(g.getMachine()),"full_trajectory_qualified",false));
            }
        }
    }
    static Stream<Arguments> acceptedProfiles(){return PROFILES.entrySet().stream().filter(e->e.getValue().path("status").asText().equals("complete")).flatMap(e->Stream.of(false,true).map(s->Arguments.of(e.getKey(),s)));}
    @ParameterizedTest @MethodSource("acceptedProfiles")
    void feasiblePositionLimitsRetainFullTrajectoryGates(String tag,boolean shared)throws Exception {
        Urgs3tReferenceTest.compareCompleteTrajectory(stage(tag),tag,shared);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void physicalLimitsSurviveIntegrationAndReinitialization(boolean shared)throws Exception {
        for(var convention:Urgs3tGovernor.RuntimeConvention.values()) {
        double[] c=Urgs3tSharedStateTest.BASE.clone();c[6]=.4;c[7]=.2;c[14]=.3;c[15]=1.2;
        var old=Urgs3tSharedStateTest.model(c);var m=old.getMachine();m.getControllerList().remove(old);
        var g=new Urgs3tGovernor("bounded",c,convention);g.setMachine(m);assertTrue(g.initStates(m.getDStabBus(),m));double[] states=new double[5],rates=new double[5];
        assertEquals(.475,g.getOutput(m),1e-14);assertEquals(.5,g.getRefPoint(),1e-14);
        for(double reference:new double[]{.8,-.2,.8}) {
            g.applyReferenceEvent(reference);
            for(int step=0;step<200;step++) {
                if(shared){g.readStaggeredState(states,0);g.evaluateStaggered(.0005,rates,0);for(int i=0;i<5;i++)states[i]+=.0005*rates[i];g.applyStaggeredState(states,0,.0005);g.completeStaggeredStep(.0005);}
                else {g.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,m,0);g.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,m,1);}
                g.readPublishedStates(states,0);assertTrue(states[0]>=.2&&states[0]<=.4);
            }
        }
        m.setPm(.3);assertTrue(g.initStates(m.getDStabBus(),m));g.readStaggeredState(states,0);assertEquals(.3,states[0],1e-14);assertEquals(.3,g.getOutput(m),1e-14);
        for(double rate:g.getStateDerivativeSnapshot().rates().values())assertEquals(0.,rate,1e-12);
        assertEquals(0,g.staggeredInputDiscontinuityVersion());assertArrayEquals(c,g.getConstants(),0.);
        }
    }
}
