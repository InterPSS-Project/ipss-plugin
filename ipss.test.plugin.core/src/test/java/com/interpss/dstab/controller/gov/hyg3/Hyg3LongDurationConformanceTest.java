package com.interpss.dstab.controller.gov.hyg3;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.interpss.IpssCorePlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.interpss.dstab.controller.gov.hyg3.PsseHyg3Governor;

/** Ten-second fault and input-reversal qualification with unchanged channel gates. */
public class Hyg3LongDurationConformanceTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/hyg3-long-duration-agent-20261104/captures.zip");
    private static final String SHA="0ca02f1095d2a8505158311eac18a9a3b42f674e17476383bad157e975be989b";
    private static Hyg3ReferenceFixture fixture(Path staged) {return new Hyg3ReferenceFixture(staged,ARCHIVE,SHA);}
    static Stream<Arguments> profiles() {
        return Stream.of(false,true).flatMap(staggered->Stream.of(0,1).map(mode->Arguments.of(staggered,mode)));
    }
    @Test void coldRepeatsPreserveAllChannelsAndEnteredConstants(@TempDir Path staged)throws Exception {
        var fixture=fixture(staged);int profiles=0;
        for(var entry:fixture.matrix()) {
            if(!entry.path("repeat").asText().equals("a"))continue;
            String tag=entry.path("tag").asText();var first=fixture.stage(tag);var repeat=fixture.stage(tag.substring(0,tag.length()-1)+"b");
            assertArrayEquals(Files.readAllBytes(first.resolve("native.csv")),Files.readAllBytes(repeat.resolve("native.csv")));
            var manifest=fixture.captureManifest(tag);assertEquals(manifest.path("entered_constants"),manifest.path("initialized_constants"));
            assertEquals(10.,manifest.path("settings").path("duration").asDouble());
            var rows=Hyg3StateConformanceTest.rows(first.resolve("native.csv"));
            assertTrue(rows.getLast().get("time_s")>9.99);profiles++;
        }
        assertEquals(6,profiles);
    }
    @ParameterizedTest @MethodSource("profiles")
    void completeFaultsRetainExistingFiveSecondStateAndBoundaryEnvelopes(boolean staggered,int mode,@TempDir Path staged)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var output=Files.createTempDirectory(Path.of("target"),"hyg3-long-fault-"+(staggered?"staggered-":"euler-"));
        Hyg3StateConformanceTest.compare(fixture(staged),staggered,output,manifest->new Hyg3StateConformanceTest.ComparisonLimits(
                Hyg3StateConformanceTest.stateTolerances(staggered,5.),Hyg3StateConformanceTest.boundaryTolerances(staggered,5.),true),
                entry->entry.path("profile").asText().equals("standard")&&entry.path("mode").asInt()==mode);
    }
    @ParameterizedTest @MethodSource("profiles")
    void completeBacklashReversalsRetainExistingStateAndHistoryEnvelopes(boolean staggered,int mode,@TempDir Path staged)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var output=Files.createTempDirectory(Path.of("target"),"hyg3-long-backlash-"+(staggered?"staggered-":"euler-"));
        Hyg3LimitConformanceTest.compare(fixture(staged),staggered,output,
                entry->entry.path("profile").asText().equals("backlash-large")&&entry.path("mode").asInt()==mode,true);
    }
    @Test void prescribedInputsLocalizeLateControllerStateDrift(@TempDir Path staged)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var fixture=fixture(staged);var output=Files.createTempDirectory(Path.of("target"),"hyg3-long-input-replay-");
        var diagnostics=new java.util.LinkedHashMap<String,Object>();var failures=new java.util.ArrayList<String>();int compared=0;
        for(var entry:fixture.matrix()) {
            if(!entry.path("repeat").asText().equals("a")||!entry.path("profile").asText().equals("standard"))continue;
            String tag=entry.path("tag").asText();var root=fixture.stage(tag);double h=entry.path("step_s").asDouble();
            var context=new org.interpss.fadapter.psse.PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(),root.resolve("dynamic.dyr").toString());
            var a=context.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuStepSec(h);
            a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(a.initialization());
            var machine=context.getDStabilityNet().getMachine("Bus1-mach1");var governor=(PsseHyg3Governor)machine.getGovernor();
            assertEquals(0.,governor.getData().getDb2());assertEquals(0.,machine.getRa());assertEquals(100.,governor.getGovernorBaseMva());
            double[] state=new double[9],rates=new double[9],history=new double[9],left=new double[9],published=new double[9],maximum=new double[9];
            governor.evaluateStaggered(h,history,0);var reference=Hyg3StateConformanceTest.rows(root.resolve("native.csv"));int events=0;
            try(var raw=org.interpss.core.dstab.reference.BenchmarkOutputGuard.newOutputStream(output.resolve(tag+".csv.gz"),
                    org.interpss.core.dstab.reference.BenchmarkOutputGuard.estimateCsvBytes(reference.size(),22));
                    var writer=new java.io.BufferedWriter(new java.io.OutputStreamWriter(new java.util.zip.GZIPOutputStream(raw),java.nio.charset.StandardCharsets.UTF_8))) {
                writer.write("time_s,speed_input,power_input");for(int k=0;k<9;k++)writer.write(",STATE"+k+",REFERENCE.STATE"+k);writer.write("\n");
                for(int j=0;j<reference.size();j++) {
                    var row=reference.get(j);double time=row.get("time_s");machine.setSpeed(1.+row.get("SPEED"));machine.setPe(row.get("P_PU"));
                    governor.evaluateStaggered(h,rates,0);governor.readPublishedState(published,0);
                    boolean event=time>0.&&j>0&&row.get("time_s").equals(reference.get(j-1).get("time_s"));
                    writer.write(time+","+row.get("SPEED")+","+row.get("P_PU"));
                    for(int k=0;k<9;k++){maximum[k]=Math.max(maximum[k],Math.abs(published[k]-row.get("STATE"+k)));writer.write(","+published[k]+","+row.get("STATE"+k));}writer.write("\n");
                    if(time<0.||j+1==reference.size())continue;
                    if(row.get("time_s").equals(reference.get(j+1).get("time_s"))){System.arraycopy(rates,0,left,0,9);continue;}
                    if(event)events++;governor.readStaggeredState(state,0);
                    for(int k=0;k<9;k++){double effective=event?.5*(rates[k]+left[k]):rates[k];state[k]+=h*(1.5*effective-.5*history[k]);history[k]=effective;}
                    governor.applyStaggeredState(state,0,h);governor.completeStaggeredStep(h);
                }
            }
            assertEquals(2,events);diagnostics.put(tag,java.util.Map.of("maximum_state_error",maximum,"samples",reference.size(),
                    "scope","Prescribed speed/power diagnostic, not full-network qualification"));
            new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("diagnostic.json").toFile(),diagnostics);
            double[] bounds=Hyg3StateConformanceTest.stateTolerances(true,5.);
            for(int k=0;k<9;k++)if(!(maximum[k]<bounds[k]))failures.add(tag+" STATE"+k+" "+maximum[k]);compared++;
        }
        assertEquals(4,compared);assertEquals(java.util.List.of(),failures);
    }

}
