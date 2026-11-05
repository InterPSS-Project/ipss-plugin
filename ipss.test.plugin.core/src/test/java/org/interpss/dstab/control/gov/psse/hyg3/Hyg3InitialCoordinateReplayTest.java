package org.interpss.dstab.control.gov.psse.hyg3;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interpss.IpssCorePlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Separates reported initial-coordinate offsets from subsequent controller recurrence errors. */
public class Hyg3InitialCoordinateReplayTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/hyg3-long-duration-agent-20261104/captures.zip");
    private static final String SHA="0ca02f1095d2a8505158311eac18a9a3b42f674e17476383bad157e975be989b";

    @Test void reportedInitialCoordinatesDoNotExplainLateStateDrift(@TempDir Path staged)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var fixture=new Hyg3ReferenceFixture(staged,ARCHIVE,SHA);
        var output=Files.createTempDirectory(Path.of("target"),"hyg3-initial-coordinate-replay-");
        var diagnostics=new LinkedHashMap<String,Object>();int profiles=0;
        for(var entry:fixture.matrix()) {
            if(!entry.path("repeat").asText().equals("a")||!entry.path("profile").asText().equals("standard"))continue;
            String tag=entry.path("tag").asText();var root=fixture.stage(tag);double h=entry.path("step_s").asDouble();
            var rows=Hyg3StateConformanceTest.rows(root.resolve("native.csv"));
            var manifest=fixture.captureManifest(tag);
            double[][] maxima=new double[2][];
            for(int seed=0;seed<2;seed++)maxima[seed]=replay(root,rows,h,seed==1,
                    manifest.path("initial_reference_from_equilibrium").asDouble(),output.resolve(tag+"-seed"+seed+".csv.gz"));
            diagnostics.put(tag,Map.of("computed_initial_state_maximum_error",maxima[0],
                    "reported_initial_state_maximum_error",maxima[1],"samples",rows.size()));
            for(int k=0;k<9;k++)assertEquals(maxima[0][k],maxima[1][k],1e-8,
                    tag+" reported initial coordinates must not be mistaken for the late discrepancy");
            double flowGate=Hyg3StateConformanceTest.stateTolerances(true,5.)[6];
            assertEquals(maxima[0][6]<=flowGate,maxima[1][6]<=flowGate,
                    "Reported-state seeding does not change the existing flow acceptance result");
            profiles++;
        }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("diagnostic.json").toFile(),diagnostics);
        System.out.println("HYG3 initial-coordinate replay "+output.getFileName()+" "+new ObjectMapper().writeValueAsString(diagnostics));
        assertEquals(4,profiles);
    }

    private static double[] replay(Path root,List<Map<String,Double>> rows,double h,boolean seed,
            double recordedReference,Path destination)throws Exception {
        var context=new org.interpss.fadapter.psse.PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(),root.resolve("dynamic.dyr").toString());
        var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuStepSec(h);algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(algorithm.initialization());
        var machine=context.getDStabilityNet().getMachine("Bus1-mach1");var governor=(PsseHyg3Governor)machine.getGovernor();
        assertEquals(0.,governor.getData().getDb2());
        double[] state=new double[9],rates=new double[9],history=new double[9],left=new double[9],published=new double[9],maximum=new double[9];
        if(seed){
            var initial=rows.getFirst();for(int k=0;k<9;k++)state[k]=initial.get("STATE"+k);
            double tf=governor.getEffectiveDerivativeFilterTime();
            state[2]*=tf/governor.getData().getK1();
            state[7]*=tf/governor.getData().getK2();state[8]*=tf/governor.getData().getK2();
            governor.setRefPoint(recordedReference);
            governor.applyStaggeredState(state,0,h);governor.completeStaggeredStep(h);
            governor.readPublishedState(published,0);
            for(int k=0;k<9;k++)assertEquals(initial.get("STATE"+k),published[k],1e-14,"Seed uses reported coordinates only");
        }
        governor.evaluateStaggered(h,history,0);int events=0,written=0;
        try(var raw=org.interpss.core.dstab.reference.BenchmarkOutputGuard.newOutputStream(destination,
                org.interpss.core.dstab.reference.BenchmarkOutputGuard.estimateCsvBytes(rows.size(),22));
                var writer=new java.io.BufferedWriter(new java.io.OutputStreamWriter(new java.util.zip.GZIPOutputStream(raw),java.nio.charset.StandardCharsets.UTF_8))){
            writer.write("time_s,speed_input,power_input");for(int k=0;k<9;k++)writer.write(",STATE"+k+",REFERENCE.STATE"+k);writer.write("\n");
            for(int j=0;j<rows.size();j++){
                var row=rows.get(j);double time=row.get("time_s");machine.setSpeed(1.+row.get("SPEED"));machine.setPe(row.get("P_PU"));
                governor.evaluateStaggered(h,rates,0);governor.readPublishedState(published,0);
                boolean event=time>0.&&j>0&&row.get("time_s").equals(rows.get(j-1).get("time_s"));
                writer.write(time+","+row.get("SPEED")+","+row.get("P_PU"));
                for(int k=0;k<9;k++){maximum[k]=Math.max(maximum[k],Math.abs(published[k]-row.get("STATE"+k)));writer.write(","+published[k]+","+row.get("STATE"+k));}writer.write("\n");written++;
                if(time<0.||j+1==rows.size())continue;
                if(row.get("time_s").equals(rows.get(j+1).get("time_s"))){System.arraycopy(rates,0,left,0,9);continue;}
                if(event)events++;governor.readStaggeredState(state,0);
                for(int k=0;k<9;k++){double effective=event?.5*(rates[k]+left[k]):rates[k];state[k]+=h*(1.5*effective-.5*history[k]);history[k]=effective;}
                governor.applyStaggeredState(state,0,h);governor.completeStaggeredStep(h);
            }
        }
        assertEquals(rows.size(),written);assertEquals(2,events);return maximum;
    }
}
