package org.interpss.dstab.control.gov.wecc.urgs3t;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Complete independent synthetic fault, equilibrium and command-reversal comparisons. */
public class Urgs3tReferenceTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/urgs3t-initial-20261102/captures-v3.zip");
    private static final String ARCHIVE_SHA="6045fffa8c31aad9668cfb8f6ebad78dd50be034523d2ccaef5395ad4c47f289";
    private static final Map<String,JsonNode> PROFILES=new TreeMap<>();
    private static final ObjectMapper JSON=new ObjectMapper();
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    @BeforeAll static void setup()throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent synthetic governor captures");
        assertEquals(ARCHIVE_SHA,sha(Files.readAllBytes(ARCHIVE)));
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            var manifest=JSON.readTree(z.getInputStream(z.getEntry("capture-matrix.json")));
            for(var entry:manifest) {
                String tag=entry.path("tag").asText();
                if(!entry.path("repeat").asText().equals("a"))continue;
                assertArrayEquals(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes(),
                        z.getInputStream(z.getEntry(tag.substring(0,tag.length()-1)+"b/native.csv")).readAllBytes());
                PROFILES.put(tag,entry);
            }
        }
        assertEquals(35,PROFILES.size());
    }
    @AfterAll static void release(){PROFILES.clear();}
    static Stream<Arguments> profiles(){return PROFILES.keySet().stream().flatMap(n->Stream.of(false,true).map(s->Arguments.of(n,s)));}
    @Test void independentDeadbandWidthsDistinguishDirectionalBranchFromOrdinaryBacklash() throws Exception {
        for(var entry:PROFILES.entrySet()) {
            if(!entry.getValue().path("profile").asText().startsWith("power-deadband"))continue;
            Path out=stage(entry.getKey());var m=JSON.readTree(out.resolve("manifest.json").toFile());
            double db=m.path("initialized_constants").get(18).asDouble();var reference=rows(out.resolve("native.csv"));
            double sourceError=0,playError=0;
            for(int i=1;i<reference.size();i++) {
                var r=reference.get(i);var old=reference.get(i-1);
                if(r.get("time_s")<=0||r.get("time_s").equals(old.get("time_s")))continue;
                double input=r.get("VAR3"),memory=old.get("VAR4");
                double directional=input>memory?input-db:input<memory?input+db:memory;
                double play=Math.max(input-db,Math.min(input+db,memory));
                sourceError=Math.max(sourceError,Math.abs(directional-r.get("VAR4")));
                playError=Math.max(playError,Math.abs(play-r.get("VAR4")));
            }
            assertTrue(sourceError<3e-8,entry.getKey()+" directional branch residual "+sourceError);
            assertTrue(playError>db/2,entry.getKey()+" must distinguish the published backlash function");
        }
    }
    @Test void independentMechanicalIncrementsRequirePublishedPowerRatherThanLiveTurbineOutput() throws Exception {
        for(String profile:List.of("speed-deadband","rate-limits","temperature-limits")) {
            var reference=rows(stage(profile+"-h0p0005-t0p5-command-a").resolve("native.csv"));
            double correct=0,wrong=0;
            for(int i=3;i<reference.size()-1;i++) {
                var a=reference.get(i);var b=reference.get(i-1);var n=reference.get(i+1);double t=a.get("time_s"),h=.0005;
                if(t<=0||n.get("time_s").equals(t)||b.get("time_s").equals(t)
                        ||Math.abs(t-.05)<4*h||Math.abs(t-.25)<4*h||Math.abs(t-.4)<4*h)continue;
                double observed=n.get("SPEED")-a.get("SPEED");
                double fa=(a.get("PMECH")/(1+a.get("SPEED"))-a.get("P_PU"))/12.8;
                double fb=(b.get("PMECH")/(1+b.get("SPEED"))-b.get("P_PU"))/12.8;
                correct=Math.max(correct,Math.abs(observed-h*(1.5*fa-.5*fb)));
                fa=(a.get("VAR4")/(1+a.get("SPEED"))-a.get("P_PU"))/12.8;
                fb=(b.get("VAR4")/(1+b.get("SPEED"))-b.get("P_PU"))/12.8;
                wrong=Math.max(wrong,Math.abs(observed-h*(1.5*fa-.5*fb)));
            }
            assertTrue(correct<1e-10,profile+" accepted mechanical increment residual "+correct);
            assertTrue(wrong>5e-9,profile+" must distinguish live-output substitution");
        }
        var reference=rows(stage("rating-idle-h0p0005-t0p5-fault-a").resolve("native.csv"));
        double correct=0,wrong=0;
        for(int i=1;i<reference.size();i++) {
            var r=reference.get(i);var old=reference.get(i-1);
            if(r.get("time_s")<=0||r.get("time_s").equals(old.get("time_s")))continue;
            correct=Math.max(correct,Math.abs(.8*(old.get("VAR4")-.3*r.get("SPEED"))-r.get("PMECH")));
            wrong=Math.max(wrong,Math.abs(.8*old.get("VAR4")-.3*r.get("SPEED")-r.get("PMECH")));
        }
        assertTrue(correct<1e-7,"Damping and turbine output share the source rating conversion");
        assertTrue(wrong>2e-5,"Machine-base damping must be distinguished on a non-unit rating");
    }
    @Test void independentActiveTemperatureControlsDistinguishHeldStateFromUnobservedLag() throws Exception {
        String tag="temperature-limits-h0p0005-t0p5-command-a";
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            byte[] baseline=z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes();
            for(String profile:List.of("temperature-fast","temperature-slow"))
                assertArrayEquals(baseline,z.getInputStream(z.getEntry(profile+"-h0p0005-t0p5-command-a/native.csv")).readAllBytes());
        }
        Path out=stage(tag);var manifest=JSON.readTree(out.resolve("manifest.json").toFile());
        double[] c=JSON.convertValue(manifest.path("initialized_constants"),double[].class);
        var reference=rows(out.resolve("native.csv")).stream().filter(r->r.get("time_s")>=0).toList();
        double h=.0005,heat=reference.getFirst().get("STATE2"),previousHeatRate=0;
        double previousHeld=0,previousDynamic=0,maxHeld=0,maxDynamic=0;int active=0;
        for(int i=0;i<reference.size()-1;i++) {
            var r=reference.get(i);var next=reference.get(i+1);double t=r.get("time_s"),valve=r.get("STATE0");
            assertEquals(reference.getFirst().get("STATE2"),r.get("STATE2"));
            // Native serialized clock drifts; these boundaries are only used away from event-adjacent increments.
            double command=reference.getFirst().get("VAR0")+(t>.0500001?.2:0)-(t>.250001?.4:0)+(t>.400001?.2:0);
            double demand=command-r.get("VAR2")/c[0],limit=c[9]+c[4]+c[5]*(c[4]-r.get("STATE2"));
            if(demand>limit)active++;
            double held=(Math.min(demand,limit)-valve)/c[1];
            double dynamic=(Math.min(demand,c[9]+c[4]+c[5]*(c[4]-heat))-valve)/c[1];
            if(i>2&&next.get("time_s")-t>.9*h&&Math.abs(t-.05)>4*h&&Math.abs(t-.25)>4*h&&Math.abs(t-.4)>4*h) {
                double delta=next.get("STATE0")-valve;
                maxHeld=Math.max(maxHeld,Math.abs(delta-h*(1.5*held-.5*previousHeld)));
                maxDynamic=Math.max(maxDynamic,Math.abs(delta-h*(1.5*dynamic-.5*previousDynamic)));
            }
            double heatRate=(r.get("STATE1")-heat)/c[3];
            if(!next.get("time_s").equals(t)){heat+=h*(1.5*heatRate-.5*previousHeatRate);previousHeatRate=heatRate;}
            previousHeld=held;previousDynamic=dynamic;
        }
        assertTrue(active>500,"Temperature limiter must actually control the valve");
        assertTrue(maxHeld<1e-7,"Held-exhaust valve increment residual "+maxHeld);
        assertTrue(maxDynamic>2e-5,"A hidden dynamic lag must be rejected by the valve boundary");
    }
    static Path stage(String tag)throws Exception {
        Path out=Files.createTempDirectory(Path.of("target"),"urgs3t-reference-");
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            for(String name:List.of("manifest.json","network.raw","dynamic.dyr","native.csv")) {
                var e=z.getEntry(tag+"/"+name);assertNotNull(e);assertTrue(e.getSize()>=0&&e.getSize()<16*1024*1024);
                try(var in=z.getInputStream(e)){Files.copy(in,out.resolve(name));}
            }
        }
        var m=JSON.readTree(out.resolve("manifest.json").toFile());assertEquals("complete",m.path("status").asText());
        for(String n:List.of("network.raw","dynamic.dyr","native.csv"))assertEquals(m.path("files").path(n).asText(),sha(Files.readAllBytes(out.resolve(n))));
        return out;
    }
    static List<Map<String,Double>> rows(Path p)throws Exception {
        var lines=Files.readAllLines(p);String[] keys=lines.getFirst().split(",");var rows=new ArrayList<Map<String,Double>>();
        for(String line:lines.subList(1,lines.size())) {
            String[] fields=line.split(",");assertEquals(keys.length,fields.length);var row=new LinkedHashMap<String,Double>();
            for(int i=0;i<keys.length;i++){double v=Double.parseDouble(fields[i]);assertTrue(Double.isFinite(v));row.put(keys[i],v);}rows.add(row);
        }
        return rows;
    }
    @ParameterizedTest @MethodSource("profiles")
    void completeTrajectoriesRetainAllStatesAndEventSides(String tag,boolean shared)throws Exception {
        compareCompleteTrajectory(stage(tag),tag,shared);
    }
    static void compareCompleteTrajectory(Path out,String tag,boolean shared)throws Exception {
        var metadata=JSON.readTree(out.resolve("manifest.json").toFile());var settings=metadata.path("settings");
        double h=settings.path("step").asDouble();
        int tripBus=settings.path("trip_bus").asInt(0),inactiveSamples=0;
        var simulation=simulate(out,shared,1e-6);var initial=simulation.initial();var governor=simulation.governor();
        var reference=rows(out.resolve("native.csv"));var maximum=new TreeMap<String,Double>();var square=new TreeMap<String,Double>();
        var low=new TreeMap<String,Double>();var high=new TreeMap<String,Double>();var at=new TreeMap<String,Double>();
        var activeStateMaximum=new TreeMap<String,Double>();for(int i=0;i<5;i++)activeStateMaximum.put("STATE"+i,0.);
        for(String key:reference.getFirst().keySet())if(!key.equals("time_s")){maximum.put(key,0.);square.put(key,0.);low.put(key,Double.POSITIVE_INFINITY);high.put(key,Double.NEGATIVE_INFINITY);at.put(key,0.);}
        int index=0,startup=0;float clock=0;long tick=0;
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("actual.csv"),BenchmarkOutputGuard.estimateCsvBytes(reference.size(),2+2*maximum.size()))) {
            w.write("source_time_s,actual_time_s,"+String.join(",",maximum.keySet()));for(String k:maximum.keySet())w.write(",REFERENCE."+k);w.write("\n");
            for(var s:reference) {
                double time=s.get("time_s");var a=time<0?initial:simulation.frames().get(index++);
                boolean inactive=tripBus==1&&s.get("EFD")==0.;if(inactive)inactiveSamples++;
                if(tripBus>0)assertEquals(inactive?0.:1.,a.get("HOST_ACTIVE"),0.,"Reference event side and physical host status");
                if(time<0)startup++;else {
                    long next=Math.round(a.get("time_s")/h);assertTrue(next>=tick&&next-tick<=1);
                    while(tick<next){clock+=(float)h;tick++;}assertEquals((double)clock,time,1e-10,"Reference clock and event side");
                }
                w.write(time+","+a.get("time_s"));
                for(String k:maximum.keySet()) {
                    assertTrue(a.containsKey(k),k);double error=a.get(k)-s.get(k);assertTrue(Double.isFinite(error));
                    if(Math.abs(error)>maximum.get(k)){maximum.put(k,Math.abs(error));at.put(k,time);}
                    square.put(k,square.get(k)+error*error);low.put(k,Math.min(low.get(k),s.get(k)));high.put(k,Math.max(high.get(k),s.get(k)));w.write(","+a.get(k));
                    if(!inactive&&activeStateMaximum.containsKey(k))activeStateMaximum.put(k,Math.max(activeStateMaximum.get(k),Math.abs(error)));
                }
                for(String k:maximum.keySet())w.write(","+s.get(k));w.write("\n");
            }
        }
        assertEquals(2,startup);assertEquals(reference.size(),simulation.frames().size()+startup);
        var nrmse=new TreeMap<String,Double>();var checks=new ArrayList<Executable>();
        for(String k:List.of("V1","V2","P_PU","Q_PU")) {
            double range=high.get(k)-low.get(k),rmse=Math.sqrt(square.get(k)/reference.size());nrmse.put(k,range>1e-7?rmse/range:rmse);
            checks.add(()->assertTrue(nrmse.get(k)<.001,k+" NRMSE "+nrmse.get(k)));
        }
        double angle=Math.sqrt(square.get("ANGLE_DEG")/reference.size());checks.add(()->assertTrue(angle<.01,"Rotor-angle RMSE "+angle));
        double stateBound=shared?5e-6:2e-4*h/.0005;
        for(int i=0;i<5;i++){String k="STATE"+i;checks.add(()->assertTrue(activeStateMaximum.get(k)<=stateBound,k+" active maximum "+activeStateMaximum.get(k)));}
        boolean parity=angle<.01&&nrmse.values().stream().allMatch(v->v<.001);
        for(int i=0;i<5;i++)parity&=activeStateMaximum.get("STATE"+i)<=stateBound;
        var result=new LinkedHashMap<String,Object>(Map.of("profile",tag,"method",shared?"staggered":"modified-euler","maximum_absolute",maximum,"time_of_maximum",at,
                "range_nrmse",nrmse,"rotor_angle_rmse_deg",angle,"state_bound",stateBound,"state_and_boundary_parity",parity,
                "all_observation_channels_qualified",false,"runtime_convention",governor.getRuntimeConvention().name()));
        if(tripBus>0){result.put("active_state_maximum_for_gates",activeStateMaximum);result.put("inactive_host_samples",inactiveSamples);result.put("inactive_allocated_states_qualified",false);}
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"),100000)){JSON.writerWithDefaultPrettyPrinter().writeValue(w,result);}
        System.out.println("URGS3T "+tag+" shared="+shared+" diagnostics="+out.getFileName()+" maxima="+maximum);
        assertAll(tag+" shared="+shared,checks);
    }
    record Simulation(Map<String,Double> initial,List<Map<String,Double>> frames,Urgs3tGovernor governor,Recording recording) {}
    static int referenceIntervals(Path csv,double h)throws Exception {
        int intervals=0;double previous=Double.NaN;float clock=0;
        try(var reader=Files.newBufferedReader(csv)) {
            assertNotNull(reader.readLine());String line;
            while((line=reader.readLine())!=null) {
                double time=Double.parseDouble(line.substring(0,line.indexOf(',')));
                assertTrue(Double.isFinite(time));if(time<0)continue;
                if(Double.isNaN(previous))assertEquals(0.,time,0.);
                else if(time!=previous){assertTrue(time>previous);clock+=(float)h;intervals++;}
                assertEquals((double)clock,time,1e-10,"Retain the complete serialized reference grid, including its final interval");previous=time;
            }
        }
        assertTrue(intervals>0);return intervals;
    }
    static Simulation simulate(Path out,boolean shared,double tolerance)throws Exception {
        return simulate(out,shared,tolerance,null);
    }
    static Simulation simulate(Path out,boolean shared,double tolerance,
            java.util.function.BiFunction<DynamicSimuAlgorithm,Recording,com.interpss.dstab.algo.IDStabSolver> factory)throws Exception {
        if(!Double.isFinite(tolerance)||tolerance<=0)throw new IllegalArgumentException("Positive finite network tolerance required");
        var settings=JSON.readTree(out.resolve("manifest.json").toFile()).path("settings");
        double h=settings.path("step").asDouble(),requestedDuration=settings.path("duration").asDouble();
        double duration=referenceIntervals(out.resolve("native.csv"),h)*h;
        assertTrue(Math.abs(duration-requestedDuration)<=h+1e-9,"Recorded endpoint must remain within one interval of the requested stop");
        var loader=new PSSEMultiFileLoader();var context=loader.loadDStab(out.resolve("network.raw").toString(),out.resolve("dynamic.dyr").toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());var algorithm=context.getDynSimuAlgorithm();var net=context.getDStabilityNet();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(h);algorithm.setTotalSimuTimeSec(duration);algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        boolean flat=settings.path("flat").asBoolean(),commands=settings.path("reference_steps").asBoolean();int tripBus=settings.path("trip_bus").asInt(0);
        if(tripBus>0)net.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus"+tripBus,"1",net,.15),"machine-trip");
        else if(!flat&&!commands)net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"fault");
        var recording=new Recording(algorithm,flat,commands);
        recording.tripBus=tripBus;
        recording.extraPause=settings.path("extra_pause").asDouble(Double.NaN);
        algorithm.setSolver(factory==null?(shared?new Shared(algorithm,recording,tolerance):new Ordinary(algorithm,recording,tolerance)):factory.apply(algorithm,recording));
        assertTrue(algorithm.initialization());var initial=recording.capture(0);recording.enabled=true;
        assertEquals(tolerance,shared?((Shared)algorithm.getSolver()).getNetworkSolutionTolerance():((Ordinary)algorithm.getSolver()).tolerance(),0.);
        long initialEvaluations=((Urgs3tGovernor)net.getMachine("Bus1-mach1").getGovernor()).getStaggeredEvaluationCount();
        while(algorithm.getSimuTime()<duration-h/2)assertTrue(algorithm.solveDEqnStep(true));
        recording.frames.add(recording.capture(algorithm.getSimuTime()));
        assertEquals(tolerance,shared?((Shared)algorithm.getSolver()).getNetworkSolutionTolerance():((Ordinary)algorithm.getSolver()).tolerance(),0.);
        var governor=(Urgs3tGovernor)net.getMachine("Bus1-mach1").getGovernor();
        if(!governor.isActive())assertEquals(initialEvaluations,governor.getStaggeredEvaluationCount());
        else if(shared)assertTrue(governor.getStaggeredEvaluationCount()>(tripBus==1?.14:duration)/h);else assertEquals(0,governor.getStaggeredEvaluationCount());
        return new Simulation(initial,recording.frames,governor,recording);
    }
    static final class Recording {
        final DynamicSimuAlgorithm algorithm;final boolean flat,commands;boolean enabled;int nextCommand;
        double extraPause=Double.NaN;
        int tripBus;Map<String,Double> historiesAtTrip;long evaluationsAtTrip;
        final List<Map<String,Double>> frames=new ArrayList<>();final double[] states=new double[5],vars=new double[5];
        Recording(DynamicSimuAlgorithm a,boolean f,boolean c){algorithm=a;flat=f;commands=c;}
        boolean before(double t){
            if(!enabled)return false;
            boolean command=commands&&nextCommand<3&&Math.abs(t-new double[]{.05,.25,.4}[nextCommand])<1e-9
                    ||tripBus>0&&nextCommand==0&&Math.abs(t-.05)<1e-9;
            boolean boundary=command||Math.abs(t-extraPause)<1e-9||tripBus>0&&Math.abs(t-.15)<1e-9
                    ||tripBus==0&&!flat&&!commands&&(Math.abs(t-.05)<1e-9||Math.abs(t-.1)<1e-9);
            if(!boundary)return false;
            frames.add(capture(t));var machine=algorithm.getNetwork().getMachine("Bus1-mach1");
            var g=(Urgs3tGovernor)machine.getGovernor();
            if(g.isActive()) {
                if(algorithm.getSolver() instanceof StaggeredEulerSolver solver)solver.notifyAcceptedEndpointDiscontinuity();
                g.publishAcceptedRunBoundary();machine.setPm(g.getOutput(machine));
            }
            if(command) {
                g.applyReferenceEvent(g.getRefPoint()+new double[]{.2,-.4,.2}[nextCommand++]);return true;
            }
            return true;
        }
        Map<String,Double> capture(double t) {
            var n=algorithm.getNetwork();var m=n.getMachine("Bus1-mach1");var g=(Urgs3tGovernor)m.getGovernor();var v=m.getDStabBus().getVoltage();
            boolean active=m.isActive()&&m.getParentGen().isActive()&&m.getDStabBus().isActive();
            var power=active?v.multiply(m.getIgen().subtract(v.multiply(m.getYgen())).conjugate()):Complex.ZERO;var a=new LinkedHashMap<String,Double>();a.put("time_s",t);a.put("HOST_ACTIVE",active?1.:0.);
            if(tripBus==1&&!active&&historiesAtTrip==null){historiesAtTrip=g.getIntegrationHistories();evaluationsAtTrip=g.getStaggeredEvaluationCount();}
            g.readStaggeredState(states,0);g.readPublishedVariables(vars,0);
            for(int i=0;i<5;i++){a.put("STATE"+i,states[i]);a.put("VAR"+i,vars[i]);}
            a.put("V1",v.abs());a.put("V2",n.getDStabBus("Bus2").getVoltageMag());a.put("A1",Math.toDegrees(v.getArgument()));a.put("A2",Math.toDegrees(n.getDStabBus("Bus2").getVoltage().getArgument()));
            a.put("P_PU",power.getReal());a.put("Q_PU",power.getImaginary());a.put("ANGLE_DEG",active?Math.toDegrees(m.getAngle()):0.);a.put("SPEED",active?m.getSpeed()-1:0.);a.put("PMECH",active?m.getPm():0.);a.put("EFD",active?m.getEfd():0.);
            var round=(org.interpss.dstab.mach.IeeeVcRoundRotorMachine)m;
            a.put("GEN1_STATE0",round.getEq1());a.put("GEN1_STATE1",round.getEd1());
            a.put("GEN1_STATE2",round.getPsikd());a.put("GEN1_STATE3",round.getPsikq());
            a.put("GEN1_STATE4",m.getSpeed()-1);a.put("GEN1_STATE5",m.getAngle());
            var second=n.getMachine("Bus2-mach1");var v2=second.getDStabBus().getVoltage();
            boolean secondActive=second.isActive()&&second.getParentGen().isActive()&&second.getDStabBus().isActive();
            var p2=secondActive?v2.multiply(second.getIgen().subtract(v2.multiply(second.getYgen())).conjugate()):Complex.ZERO;
            a.put("GEN2_STATE0",second.getSpeed()-1);a.put("GEN2_STATE1",second.getAngle());
            a.put("P_PU_2",p2.getReal());a.put("Q_PU_2",p2.getImaginary());a.put("SPEED_2",secondActive?second.getSpeed()-1:0.);
            // The classical source's excitation channel is its fixed internal emf.
            a.put("ANGLE_DEG_2",secondActive?Math.toDegrees(second.getAngle()):0.);a.put("PMECH_2",secondActive?second.getPm():0.);a.put("EFD_2",secondActive?((com.interpss.dstab.mach.EConstMachine)second).getE():0.);return a;
        }
    }
    static class Ordinary extends DefaultDynamicSimulationSolver {
        final Recording r;Ordinary(DynamicSimuAlgorithm a,Recording r,double tolerance){super(a);this.r=r;converge_tol=tolerance;}
        double tolerance(){return converge_tol;}
        @Override public void beforeStep(double t)throws DStabSimuException{r.before(t);super.beforeStep(t);}
        @Override public boolean networkSolutionStep()throws DStabSimuException{boolean ok=super.networkSolutionStep();if(ok&&r.enabled&&"INITIAL".equals(networkSolutionStage))r.frames.add(r.capture(dstabAlgo.getSimuTime()));return ok;}
    }
    static class Shared extends StaggeredEulerSolver {
        final Recording r;Shared(DynamicSimuAlgorithm a,Recording r,double tolerance){super(a);this.r=r;setReuseNetworkEndpoint(false);setNetworkSolutionTolerance(tolerance);}
        @Override public void beforeStep(double t)throws DStabSimuException{r.before(t);super.beforeStep(t);}
        @Override public boolean networkSolutionStep()throws DStabSimuException{boolean ok=super.networkSolutionStep();if(ok&&r.enabled&&"INITIAL".equals(networkSolutionStage))r.frames.add(r.capture(dstabAlgo.getSimuTime()));return ok;}
    }
}
