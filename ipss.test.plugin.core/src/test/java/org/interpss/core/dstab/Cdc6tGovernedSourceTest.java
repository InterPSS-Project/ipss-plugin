package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;
import org.interpss.dstab.hvdc.Cdc6tParameters;
import org.interpss.fadapter.psse.dyr.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Independent active-governor startup and fault qualification at two intervals. */
public class Cdc6tGovernedSourceTest extends CorePluginTestSetup {
    @TempDir Path folder;
    private static final String[] SOURCE_KEYS={"ANGLE","SPEED","PMECH","GOV_STATE0","GOV_STATE1"};
    private record Reference(List<String> raw,String sources,Cdc6tParameters parameters,
            List<Map<String,Double>> rows,double step,double fault,double clear,double end) { }
    static java.util.stream.Stream<Arguments> profiles(){
        return java.util.stream.Stream.of("fine/current","fine/inverter-power","quarter/current","quarter/inverter-power",
                "fine/inverter-power-slower","quarter/inverter-power-slower",
                "fine/inverter-power-faster","quarter/inverter-power-faster")
                .flatMap(p->java.util.stream.Stream.of(false,true).map(s->Arguments.of(p,s)));
    }
    private static Reference reference(String profile)throws Exception {
        boolean rateVariant=profile.endsWith("-slower")||profile.endsWith("-faster");
        Path archive=Path.of("testData/private/verification-artifacts/cdc6t-governed"+(rateVariant?"-ramp":"")+"-20261102/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent governed-source captures");
        assertEquals(rateVariant?"8a27303c2a1f54b1ceb78cc02541c71ddb5dfd25f497e807346d95c4f2149c7f"
                        :"d23ec47224466c084a37cb8ba9ceead9d22656f7f789e277a35cf030096ad9c1",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        try(var z=new ZipFile(archive.toFile())){
            String prefix=profile+"/a/";
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
            var settings=mapper.readTree(z.getInputStream(z.getEntry(prefix+"manifest.json"))).get("settings");
            assertEquals("tgov1",settings.get("source_governor").asText());assertEquals(6,settings.get("source_inertia").doubleValue(),0);
            double h=settings.get("step").doubleValue();
            byte[] data=z.getInputStream(z.getEntry(prefix+"native.csv")).readAllBytes();
            assertArrayEquals(data,z.getInputStream(z.getEntry(profile+"/b/native.csv")).readAllBytes());
            var rows=Tiocr1ResetReferenceTest.csv(data);
            var raw=new ArrayList<>(new String(z.getInputStream(z.getEntry(prefix+"network.raw")).readAllBytes(),StandardCharsets.UTF_8).lines().toList());
            var dyr=new String(z.getInputStream(z.getEntry(prefix+"dynamic.dyr")).readAllBytes(),StandardCharsets.UTF_8);
            var records=PsseDyrRecordReader.read(new java.io.StringReader(dyr),"reference/dynamic.dyr");
            var parameters=Cdc6tRecordMapper.prepare(records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).findFirst().orElseThrow());
            String sources=String.join("\n",dyr.lines().filter(line->!line.contains("'CDC6T'")).toList())+"\n";
            double fault=Double.NaN,clear=Double.NaN;
            for(var snapshot:mapper.readTree(z.getInputStream(z.getEntry(prefix+"snapshots.json")))){
                String stage=snapshot.get("stage").asText();
                double t=Math.rint(snapshot.get("time").doubleValue()/h)*h;
                if(stage.equals("before-first-fault"))fault=t;if(stage.equals("before-first-clearing"))clear=t;
            }
            assertTrue(fault>0&&clear>fault);
            return new Reference(raw,sources,parameters,rows,h,fault,clear,Math.rint(rows.getLast().get("time_s")/h)*h);
        }
    }
    static java.util.stream.Stream<String> recoveryReplayProfiles(){
        return java.util.stream.Stream.of("fine/inverter-power-slower","quarter/inverter-power-slower");
    }
    @ParameterizedTest @MethodSource("recoveryReplayProfiles")
    void independentRecoveryHandoffRetainsNormalMeasurementDerivativeHistory(String profile)throws Exception {
        var r=reference(profile);int completion=-1;
        for(int i=1;i<r.rows().size()-1;i++){
            var row=r.rows().get(i);var prior=r.rows().get(i-1);
            if(row.get("time_s")>.2&&row.get("VAR2")==1&&row.get("VAR3")==1
                    &&(prior.get("VAR2")<1||prior.get("VAR3")<1)){completion=i;break;}
        }
        assertTrue(completion>0);
        var prior=r.rows().get(completion-1);var current=r.rows().get(completion);var next=r.rows().get(completion+1);
        double tau=r.parameters().copySourceConstants()[3];
        double previousRate=(prior.get("VAR11")-prior.get("STATE1"))/tau;
        double rightRate=(current.get("VAR11")-current.get("STATE1"))/tau;
        double normal=current.get("STATE1")+r.step()*(1.5*rightRate-.5*previousRate);
        assertEquals(next.get("STATE1"),normal,.0001,"Raw recorded state follows the unaveraged two-step equation");
        // The preceding recovery current is a conservative left-boundary
        // discriminator. Even this underestimates the completed ramp's jump.
        double leftRate=(prior.get("VAR11")-current.get("STATE1"))/tau;
        double averaged=current.get("STATE1")+r.step()*(.75*(rightRate+leftRate)-.5*previousRate);
        assertTrue(Math.abs(averaged-next.get("STATE1"))>.2,
                "Discontinuity averaging must be visibly distinguished by the independent capture");
    }
    @ParameterizedTest @MethodSource("profiles")
    void governedSourcesUseSettledPowerAndSharedControllerEquations(String profile,boolean staggered)throws Exception {
        var r=reference(profile);var actual=run(profile,r,staggered,true);
        if(!staggered){
            var generic=run(profile,r,false,false);assertEquals(actual.size(),generic.size());
            for(int i=0;i<actual.size();i++)assertArrayEquals(actual.get(i),generic.get(i),1e-12,
                    "Optimized controller binding must use the final initialized blocks");
        }
    }
    @org.junit.jupiter.api.Test
    void rejectedInternalBoundaryLeavesMachineAndGovernorHistoryUntouched()throws Exception {
        var r=reference("quarter/current");
        var f=Cdc6tCoupledNetworkTest.loadWithSourceModels(folder,new ArrayList<>(r.raw()),r.parameters(),r.sources());
        var a=f.context().getDynSimuAlgorithm();var n=f.context().getDStabilityNet();
        class RejectingSolver extends org.interpss.dstab.algo.StaggeredEulerSolver {
            boolean armed;int observed;
            RejectingSolver(){super(a);setNetworkSolutionTolerance(1e-6);setMaxIterationTimes(50);}
            @Override protected void afterConvergedNetworkAction()throws com.interpss.dstab.common.DStabSimuException {
                if(!armed){super.afterConvergedNetworkAction();return;}
                var snapshots=new ArrayList<double[]>();
                var history=new ArrayList<com.interpss.dstab.mach.impl.MachineImpl.MechanicalIntegrationState>();
                for(int i=1;i<=4;i++){
                    var m=(com.interpss.dstab.mach.impl.MachineImpl)n.getMachine("Bus"+i+"-mach1");
                    var g=(PsseTGov1SteamTurGovernor)m.getGovernor();
                    snapshots.add(new double[]{m.getAngle(),m.getSpeed(),m.getPe(),m.getPm(),g.getValvePosition(),g.getReheaterStorage()});
                    history.add(m.captureMechanicalIntegrationState());
                }
                super.afterConvergedNetworkAction();
                for(int i=1;i<=4;i++){
                    var m=(com.interpss.dstab.mach.impl.MachineImpl)n.getMachine("Bus"+i+"-mach1");
                    var g=(PsseTGov1SteamTurGovernor)m.getGovernor();
                    assertArrayEquals(snapshots.get(i-1),new double[]{m.getAngle(),m.getSpeed(),m.getPe(),m.getPm(),g.getValvePosition(),g.getReheaterStorage()},0);
                    assertEquals(history.get(i-1),m.captureMechanicalIntegrationState());
                }
                observed++;
                throw new com.interpss.dstab.common.DStabSimuException("Deliberately reject the provisional boundary");
            }
        }
        var solver=new RejectingSolver();a.setSolver(solver);a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        a.setSimuStepSec(r.step());a.setTotalSimuTimeSec(.2);a.setSimuOutputHandler(new StateMonitor());
        n.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus7",n,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,r.fault(),r.clear()-r.fault()),"BoundaryFault");
        assertTrue(a.initialization());a.setSimuTime(-2*r.step());solver.armed=true;
        boolean succeeded=true;
        while(a.getSimuTime()<.2&&succeeded)succeeded=a.solveDEqnStep(true);
        assertFalse(succeeded,"The outer algorithm must report the deliberately rejected solve");
        assertEquals(1,solver.observed);
        assertEquals("DEVICE_ACTION_BOUNDARY_CALLBACK_FAILURE",solver.getLastNetworkSolutionDiagnostics().reason());
        // Inspect private commit markers only to distinguish a discarded trial
        // from incorrectly retained input history after a failed solve.
        for(String name:List.of("trialMechanicalBoundary","acceptedMechanicalBoundary")){
            var field=org.interpss.dstab.algo.PsseStaggeredEulerSolver.class.getDeclaredField(name);field.setAccessible(true);
            assertFalse(field.getBoolean(solver),name);
        }
    }
    private List<double[]> run(String profile,Reference r,boolean staggered,boolean optimized)throws Exception {
        var f=Cdc6tCoupledNetworkTest.loadWithSourceModels(Files.createTempDirectory(folder,"source-"),new ArrayList<>(r.raw()),r.parameters(),r.sources());
        var a=f.context().getDynSimuAlgorithm();var n=f.context().getDStabilityNet();
        var recorder=new Cdc6tCoupledReferenceTest.Recorder(f,r.fault(),r.clear());recorder.recordInternalTransitions();
        var solver=staggered?new Cdc6tCoupledReferenceTest.Shared(recorder):new Cdc6tCoupledReferenceTest.Ordinary(recorder);
        solver.setControllerRuntimePreparationEnabled(optimized);
        a.setSolver(solver);a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(r.step());a.setTotalSimuTimeSec(r.end());a.setSimuOutputHandler(new StateMonitor());
        n.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus7",n,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,r.fault(),r.clear()-r.fault()),"SourceFault");
        assertTrue(a.initialization());a.setSimuTime(-2*r.step());recorder.enabled=true;
        if(!staggered&&optimized)assertEquals(4,solver.getControllerPreparationReport().compiledControllers());
        var machines=new Machine[4];var governors=new PsseTGov1SteamTurGovernor[4];
        for(int i=0;i<4;i++){
            machines[i]=n.getMachine("Bus"+(i+1)+"-mach1");governors[i]=assertInstanceOf(PsseTGov1SteamTurGovernor.class,machines[i].getGovernor());
            assertEquals(6,machines[i].getH(),0);
            assertEquals(machines[i].getPm(),governors[i].getOutput(machines[i]),1e-12,"Initial governor output must match settled shaft power");
            assertEquals(r.rows().getFirst().get("M"+(i+1)+"_PMECH"),machines[i].getPm(),6e-5);
        }
        Map<Long,double[]> samples=new LinkedHashMap<>();capture(samples,machines,governors,a.getSimuTime(),r.step());
        double recoveryCompletion=Double.NaN;
        while(a.getSimuTime()<r.end()-r.step()/2){
            var previousMode=f.protection().operatingMode();
            assertTrue(a.solveDEqnStep(true));assertTrue(solver.getLastNetworkSolutionDiagnostics().iterations()<=50);
            if(previousMode==org.interpss.dstab.hvdc.Cdc6tNormalRuntime.OperatingMode.RECOVERING
                    &&f.protection().operatingMode()==org.interpss.dstab.hvdc.Cdc6tNormalRuntime.OperatingMode.NORMAL)
                recoveryCompletion=a.getSimuTime();
            capture(samples,machines,governors,a.getSimuTime(),r.step());
        }
        recorder.capture(a.getSimuTime());
        String method=staggered?"staggered":optimized?"euler-compiled":"euler-generic";
        Path out=Files.createTempDirectory(Files.createDirectories(Path.of("target/cdc6t-governed-source")),profile.replace('/','-')+"-"+method+"-");
        // Sum per-addition bounds rather than charging the largest ulp to
        // every earlier interval. The resulting bound still separates ticks.
        double clockBound=2*Math.ulp((float)r.step());
        for(int tick=-1;tick<=Math.ceil(r.end()/r.step());tick++)clockBound+=.5*Math.ulp((float)(tick*r.step()));
        Cdc6tProtectedNetworkTest.writeComparison(out,r.rows(),recorder.rows,r.step(),clockBound);
        double[] peak=new double[20],sum=new double[20],minimum=new double[20],maximum=new double[20];
        Arrays.fill(minimum,Double.POSITIVE_INFINITY);Arrays.fill(maximum,Double.NEGATIVE_INFINITY);
        List<double[]> aligned=new ArrayList<>();
        try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("source-comparison.csv"),8*1024*1024)){
            writer.write("reference_time_s,local_time_s");
            for(int i=1;i<=4;i++)for(String key:SOURCE_KEYS)writer.write(",M"+i+"_"+key+"_reference,M"+i+"_"+key+"_local");writer.write("\n");
            for(var row:r.rows()){
                double time=row.get("time_s");double[] values=samples.get(Math.round(time/r.step()));assertNotNull(values);assertEquals(time,values[0],clockBound);
                aligned.add(values);writer.write(time+","+values[0]);
                for(int i=0;i<20;i++){
                    double expected=row.get("M"+(i/5+1)+"_"+SOURCE_KEYS[i%5]);double error=values[i+1]-expected;assertTrue(Double.isFinite(error));
                    peak[i]=Math.max(peak[i],Math.abs(error));sum[i]+=error*error;minimum[i]=Math.min(minimum[i],expected);maximum[i]=Math.max(maximum[i],expected);
                    writer.write(","+expected+","+values[i+1]);
                }
                writer.write("\n");
            }
        }
        try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("source-metrics.csv"),16384)){
            writer.write("channel,max_absolute,rmse,reference_range,nrmse\n");
            for(int i=0;i<20;i++){
                double range=maximum[i]-minimum[i],rmse=Math.sqrt(sum[i]/r.rows().size());assertTrue(range>0);
                writer.write("M"+(i/5+1)+"_"+SOURCE_KEYS[i%5]+","+peak[i]+","+rmse+","+range+","+rmse/range+"\n");
            }
        }
        for(int i=0;i<4;i++)assertTrue(maximum[i*5+3]-minimum[i*5+3]>.001,"Fault must excite each governor");
        // Separate finite-step envelopes; these do not establish full CDC6T
        // state acceptance. In particular the power-control angle discrepancy
        // remains visible and must not be hidden by time or angle offsets.
        boolean fine=profile.startsWith("fine/"),power=profile.contains("inverter-power");
        boolean slower=profile.endsWith("-slower");
        if(power){
            int completion=-1;
            for(int i=1;i<r.rows().size();i++){
                var row=r.rows().get(i);var prior=r.rows().get(i-1);
                if(row.get("time_s")>.2&&row.get("VAR2")==1&&row.get("VAR3")==1
                        &&(prior.get("VAR2")<1||prior.get("VAR3")<1)){completion=i;break;}
            }
            assertTrue(completion>0&&Double.isFinite(recoveryCompletion));
            long referenceTick=Math.round(r.rows().get(completion).get("time_s")/r.step());
            try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("recovery-completion.csv"),4096)){
                writer.write("reference_time,local_time,reference_tick,local_tick,left_reference_voltage_factor,left_reference_current_factor\n");
                writer.write(r.rows().get(completion).get("time_s")+","+recoveryCompletion+","+referenceTick+","+Math.round(recoveryCompletion/r.step())
                        +","+r.rows().get(completion-1).get("VAR2")+","+r.rows().get(completion-1).get("VAR3")+"\n");
            }
            assertEquals(slower?0:1,referenceTick-Math.round(recoveryCompletion/r.step()),
                    "Off-grid recovery agrees; the retained grid-boundary controls differ by exactly one interval");
            if(!slower){
                var left=r.rows().get(completion-1);
                assertTrue(left.get("VAR3")<=1&&left.get("VAR3")>1-1e-5);
                assertTrue(left.get("VAR2")<1&&left.get("VAR2")>1-1e-5,
                        "The reference voltage ramp must lie just below its endpoint, without time shifting");
            }
        }
        double[] bounds=fine
                ?new double[]{power?.008:.002,staggered?1.1e-6:4.5e-6,5.8e-5,5.9e-5,1.8e-5}
                :new double[]{power?.075:staggered?.007:.012,staggered?9e-6:3.8e-5,8.5e-5,1.25e-4,2.4e-5};
        if(power&&slower)bounds=fine
                ?new double[]{staggered?.002:.011,staggered?1e-7:4.5e-6,2.9e-5,2.9e-5,9e-6}
                :new double[]{staggered?.007:.065,staggered?1.1e-6:3.8e-5,5.2e-5,1.05e-4,1.1e-5};
        else if(power&&staggered)bounds=fine
                ?new double[]{.017,2.1e-6,4e-5,4.7e-5,1.2e-5}
                :new double[]{.145,1.8e-5,1.45e-4,2.05e-4,4e-5};
        for(int i=0;i<20;i++)assertTrue(peak[i]<bounds[i%5],
                profile+" "+method+" M"+(i/5+1)+" "+SOURCE_KEYS[i%5]+" error "+peak[i]);
        if(staggered&&!fine){
            int firstAfterBlock=(int)Math.round(.075/r.step());
            for(int i=0;i<r.rows().size();i++)if(Math.round(r.rows().get(i).get("time_s")/r.step())==firstAfterBlock)
                assertEquals(r.rows().get(i).get("M1_SPEED"),aligned.get(i)[2],2e-8,
                        "Internal blocking must use the converged left acceleration once");
        }
        return aligned;
    }
    private static void capture(Map<Long,double[]> rows,Machine[] machines,PsseTGov1SteamTurGovernor[] governors,double time,double step){
        double[] values=new double[21];values[0]=time;
        for(int i=0;i<4;i++){
            values[1+5*i]=Math.toDegrees(machines[i].getAngle());values[2+5*i]=machines[i].getSpeed()-1;values[3+5*i]=machines[i].getPm();
            values[4+5*i]=governors[i].getValvePosition();values[5+5*i]=governors[i].getReheaterStorage();
        }
        assertNull(rows.put(Math.round(time/step),values));
    }
}
