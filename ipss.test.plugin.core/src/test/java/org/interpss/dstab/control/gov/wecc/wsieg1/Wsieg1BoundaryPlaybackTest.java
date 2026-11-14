package org.interpss.dstab.control.gov.wecc.wsieg1;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabSimuException;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Diagnostic boundary replay; coupled governor acceptance remains in the independent conformance tests. */
public class Wsieg1BoundaryPlaybackTest {
    @BeforeAll static void setup(){IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");}

    @ParameterizedTest @ValueSource(booleans={false,true})
    void prescribedInputsReachTheirDeclaredStagesWithoutDroppingSamples(boolean prescribeRotor,@TempDir Path staged)throws Exception {
        var config=TestConfig.load("/test-config/ipss.test.plugin.core.json","Wsieg1BaseConformanceTest");
        var fixture=new Wsieg1ReferenceFixture(staged,config);
        Path output=Files.createTempDirectory(Path.of("target"),prescribeRotor?"wsieg1-boundary-power-rotor-":"wsieg1-boundary-power-");
        System.out.println("WSIEG1 boundary diagnostic "+output.getFileName());int profiles=0;
        for(var entry:fixture.matrix()) {
            String tag=entry.path("tag").asText();
            if(!tag.startsWith("secondary-high-dual-fault-")||!entry.path("repeat").asText().equals("a"))continue;
            Path input=fixture.stage(tag);var reference=Wsieg1StateConformanceTest.rows(Files.readAllBytes(input.resolve("native.csv")));
            double h=entry.path("step_s").asDouble();int ticks=(int)Math.round(reference.getLast().get("time_s")/h);
            var loader=new PSSEMultiFileLoader();var context=loader.loadDStab(input.resolve("network.raw").toString(),input.resolve("dynamic.dyr").toString());
            assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());var a=context.getDynSimuAlgorithm();var net=context.getDStabilityNet();
            assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(h);a.setTotalSimuTimeSec(ticks*h);
            a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            var recording=new Wsieg1SharedSmibTest.Recording(a,"fault",List.of());
            var solver=new PlaybackSolver(a,recording,reference,prescribeRotor);a.setSolver(solver);
            net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,new Complex(0.,.2*net.getBaseKva()/100000.),null,.05,.05),"fault");
            assertTrue(a.initialization());recording.initial=recording.capture(0.,"INITIAL");recording.enabled=true;
            while(a.getSimuTime()<a.getTotalSimuTimeSec()-h/2.)assertTrue(a.solveDEqnStep(true));
            recording.frames.add(recording.capture(a.getSimuTime(),"FINAL"));
            assertEquals(ticks,solver.predictorCalls);assertEquals(ticks,solver.correctorCalls);
            compare(output.resolve(tag),recording,reference,h,prescribeRotor);profiles++;
        }
        assertEquals(2,profiles);
    }

    private static final class PlaybackSolver extends DefaultDynamicSimulationSolver {
        final Wsieg1SharedSmibTest.Recording recording;final double[][] input;final boolean prescribeRotor;
        final double[] expectedSpeeds=new double[2];
        int predictorCalls,correctorCalls;
        PlaybackSolver(DynamicSimuAlgorithm a,Wsieg1SharedSmibTest.Recording recording,List<Map<String,Double>> reference,boolean prescribeRotor) {
            super(a);this.recording=recording;this.prescribeRotor=prescribeRotor;converge_tol=Wsieg1SharedSmibTest.TOLERANCE;
            var grid=new TreeMap<Integer,double[]>();
            for(var row:reference)if(row.get("time_s")>=0.) {
                int tick=(int)Math.round(row.get("time_s")/a.getSimuStepSec());
                double[] values={row.get("PMECH"),row.get("PMECH_LP"),row.get("ANGLE_DEG"),row.get("SPEED"),row.get("ANGLE_DEG_LP"),row.get("SPEED_LP")};
                double[] previous=grid.put(tick,values);if(previous!=null)assertArrayEquals(previous,values,0.,"Prescribed continuous boundaries agree on both event sides");
            }
            input=new double[grid.size()][];for(int i=0;i<input.length;i++){input[i]=grid.get(i);assertNotNull(input[i],"Contiguous reference integration grid");}
        }
        @Override public boolean initialization() {
            boolean initialized=super.initialization();
            if(initialized)for(int i=1;i<=2;i++)dstabAlgo.getNetwork().getMachine("Bus"+i+"-mach1").getGovernor().setStatus(false);
            return initialized;
        }
        private int tick(double time) {
            int tick=(int)Math.round(time/dstabAlgo.getSimuStepSec());
            assertTrue(tick>=0&&tick<input.length,"No extrapolated reference boundary");return tick;
        }
        @Override public void beforeStep(double time)throws DStabSimuException {recording.before(time);super.beforeStep(time);}
        @Override public void diffEqnIntegrationStep(double time,double h,DynamicSimuMethod method,int flag)throws DStabSimuException {
            // Predict from the left endpoint; correct with the right endpoint.
            // Replacing only a governor's displayed output would not guarantee
            // which power the machine's mechanical equations actually read.
            assertTrue(flag==0||flag==1);double[] values=input[tick(time+(flag==1?h:0.))];
            for(int i=1;i<=2;i++) {
                var m=(com.interpss.dstab.mach.impl.MachineImpl)dstabAlgo.getNetwork().getMachine("Bus"+i+"-mach1");
                assertInstanceOf(com.interpss.dstab.mach.ieeevc.IeeeVcRoundRotorMachine.class,m);
                var history=m.captureMechanicalIntegrationState();double speed=flag==0?m.getSpeed():history.predictedSpeed();
                double damping=m.getD()*.01*dstabAlgo.getNetwork().getFrequency();
                // The imported round-rotor realization uses electromagnetic
                // torque; convert prescribed mechanical power at actual speed.
                double rate=((values[i-1]-damping*(speed-1.))/speed-m.getPe()-m.getPe_NegSeq())/(2.*m.getH());
                expectedSpeeds[i-1]=m.getSpeed()+h*(flag==0?rate:.5*(rate-history.speedDerivative()));
                m.setPm(values[i-1]);
            }
            super.diffEqnIntegrationStep(time,h,method,flag);
            for(int i=1;i<=2;i++) {
                var m=dstabAlgo.getNetwork().getMachine("Bus"+i+"-mach1");
                assertEquals(values[i-1],m.getPm(),0.);
                assertEquals(expectedSpeeds[i-1],m.getSpeed(),1e-14,"The mechanical equation consumed the prescribed stage input");
            }
            if(h>0.){if(flag==0)predictorCalls++;else correctorCalls++;}
        }
        @Override public boolean networkSolutionStep()throws DStabSimuException {
            if(recording.enabled&&prescribeRotor) {
                double[] values=input[tick(networkEvaluationTime)];
                for(int i=1;i<=2;i++){var m=dstabAlgo.getNetwork().getMachine("Bus"+i+"-mach1");m.setAngle(Math.toRadians(values[2*i]));m.setSpeed(1.+values[2*i+1]);}
            }
            boolean solved=super.networkSolutionStep();
            if(solved&&recording.enabled&&"INITIAL".equals(networkSolutionStage))recording.frames.add(recording.capture(dstabAlgo.getSimuTime(),"GRID"));
            return solved;
        }
    }

    private static void compare(Path path,Wsieg1SharedSmibTest.Recording actual,List<Map<String,Double>> reference,double h,boolean prescribeRotor)throws Exception {
        var maxima=new LinkedHashMap<String,Double>();var times=new LinkedHashMap<String,Double>();var sums=new LinkedHashMap<String,Double>();
        for(String key:reference.getFirst().keySet())if(!key.equals("time_s")&&!key.equals("VAR2")){maxima.put(key,0.);times.put(key,0.);sums.put(key,0.);}
        int frame=0,startups=0;float clock=0f;long tick=0;
        try(var stream=new GZIPOutputStream(BenchmarkOutputGuard.newOutputStream(Path.of(path+".csv.gz"),16L*1024*1024));var writer=new BufferedWriter(new OutputStreamWriter(stream,StandardCharsets.UTF_8))) {
            writer.write("reference_time_s,actual_time_s,sample_kind,"+String.join(",",actual.initial.values().keySet()));
            for(String key:reference.getFirst().keySet())writer.write(",REFERENCE."+key);writer.write("\n");
            for(var ref:reference) {
                double time=ref.get("time_s");var row=time<0.?actual.initial:actual.frames.get(frame++);
                if(time<0.)startups++;else {
                    long next=Math.round(row.time()/h);assertTrue(next>=tick&&next-tick<=1);assertEquals(next*h,row.time(),1e-10);
                    while(tick<next){clock+=(float)h;tick++;}assertEquals((double)clock,time,1e-10,"Explicit binary32 reference clock");
                }
                writer.write(time+","+row.time()+","+(time<0.?"STARTUP_HELD_INITIAL":row.kind()));
                for(double value:row.values().values())writer.write(","+value);for(double value:ref.values())writer.write(","+value);writer.write("\n");
                for(String key:maxima.keySet()) {
                    String observed=key.startsWith("STATE")?"PUBLISHED"+key.substring(5):key;
                    double error=row.values().get(observed)-ref.get(key);assertTrue(Double.isFinite(error));
                    if(Math.abs(error)>maxima.get(key)){maxima.put(key,Math.abs(error));times.put(key,time);}sums.put(key,sums.get(key)+error*error);
                    if(time>0.&&key.startsWith("PMECH"))assertEquals(0.,error,0.,"Exact prescribed machine-stage power");
                    if(time>=0.&&prescribeRotor&&key.startsWith("ANGLE_DEG"))assertEquals(0.,error,1e-12,"Exact prescribed rotor boundary");
                    if(time>=0.&&prescribeRotor&&key.startsWith("SPEED"))assertEquals(0.,error,2e-16,"Exact prescribed speed boundary");
                }
            }
        }
        assertEquals(reference.size(),actual.frames.size()+startups);
        var norms=new LinkedHashMap<String,Object>();var gates=new LinkedHashMap<String,Boolean>();
        for(String key:maxima.keySet())if(key.matches("V[0-9]+")||key.startsWith("P_PU")||key.startsWith("Q_PU")||key.startsWith("ANGLE_DEG")) {
            double range=reference.stream().mapToDouble(r->r.get(key)).max().orElseThrow()-reference.stream().mapToDouble(r->r.get(key)).min().orElseThrow();
            double rmse=Math.sqrt(sums.get(key)/reference.size());var metric=new LinkedHashMap<String,Object>();
            metric.put("rmse",rmse);metric.put("reference_range",range);metric.put("range_nrmse",range==0.?null:rmse/range);norms.put(key,metric);
            gates.put(key,key.startsWith("ANGLE_DEG")?rmse<.01:range==0.?maxima.get(key)<1e-6:rmse/range<.001);
        }
        var result=new LinkedHashMap<String,Object>();result.put("purpose","Diagnostic localization only; governor states are initialized then held, not trajectory-qualified");
        result.put("prescribed_rotor",prescribeRotor);result.put("step_s",h);result.put("samples",reference.size());result.put("maximum_absolute_error",maxima);result.put("time_of_maximum_error",times);
        result.put("boundary_metrics",norms);result.put("unchanged_coupled_gate_diagnostics",gates);result.put("unmapped_reference_diagnostics",List.of("VAR2"));
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of(path+".json").toFile(),result);
    }
}
