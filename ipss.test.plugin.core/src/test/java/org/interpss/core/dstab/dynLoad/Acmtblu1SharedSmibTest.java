package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.google.gson.*;
import com.interpss.simu.SimuContext;
import com.interpss.dstab.algo.*;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.common.DStabSimuException;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.dstab.dynLoad.impl.LD1PACImpl;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Exact source-load ownership and coupled compressor controls through both shared integration paths. */
public class Acmtblu1SharedSmibTest {
    private static final TestConfig CONFIG=TestConfig.load("/test-config/ipss.test.plugin.core.json","Acmtblu1SharedSmibTest");
    private static final Path ARCHIVE=Path.of(CONFIG.string("reference.archive",
            "testData/private/verification-artifacts/cmldblu1-current-refresh-20261031/captures.zip"));
    private static final String ARCHIVE_SHA="8c11c1e85484c4be5d00a16fc9ea8b2220a52158336fd08374b1e743b2bd5157";
    private static final Path PREPARATION=Path.of(CONFIG.string("reference.preparation.archive",
            "testData/private/verification-artifacts/cmldblu1-severe-boundary-audit-20261101/preparation.zip"));
    private static final String PREPARATION_SHA="b834d90d43fe5640c832e1c18a060902a154c6ea47c7664c859ae6d7afacac1b";
    private static final Path FREQUENCY=Path.of(CONFIG.string("reference.frequency.archive",
            "testData/private/verification-artifacts/acmtblu1-frequency-20261101/captures.zip"));
    private static final String FREQUENCY_SHA="6653c1edfeb7b2d62d8475f020616487110a160b0491a7c8cb324901b3fcdc68";
    private static final Path ELECTRICAL=Path.of(CONFIG.string("reference.electrical.archive",
            "testData/private/verification-artifacts/acmtblu1-state-inputs-20261101/captures.zip"));
    private static final String ELECTRICAL_SHA="b0436c8f78b28646f065f6248b7641346c82eae82838db9a4ce575ea2658e06e";
    private static final Path RAW=Path.of("testData/adpter/psse/v33/HspModels/load-network.raw");
    private static final Path DYR=Path.of("testData/reference-fixtures/compressor-current/acmtblu1.dyr");
    private static final Map<String,Reference> REFERENCES=new TreeMap<>();
    record Reference(double h,double fault,double loading,String dyr,List<Map<String,Double>> rows) { }
    private record Loaded(PSSEMultiFileLoader loader,SimuContext context) { }
    private static String sha(byte[] raw) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)); }
    private static List<Map<String,Double>> csv(byte[] raw) {
        String[] lines=new String(raw,StandardCharsets.UTF_8).strip().split("\\R"),names=lines[0].split(",");
        var result=new ArrayList<Map<String,Double>>();assertEquals(51,names.length);
        for(int i=1;i<lines.length;i++){var row=new LinkedHashMap<String,Double>();String[] fields=lines[i].split(",");assertEquals(names.length,fields.length);
            for(int j=0;j<names.length;j++){double value=Double.parseDouble(fields[j]);assertTrue(Double.isFinite(value));row.put(names[j],value);}result.add(row);}
        return result;
    }
    @BeforeAll static void setup() throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        if(Files.isRegularFile(ARCHIVE))readReferences(ARCHIVE,ARCHIVE_SHA,false);
        if(Files.isRegularFile(PREPARATION))readReferences(PREPARATION,PREPARATION_SHA,true);
        if(Files.isRegularFile(FREQUENCY))readReferences(FREQUENCY,FREQUENCY_SHA,false,"frequency-");
        if(Files.isRegularFile(ELECTRICAL))readReferences(ELECTRICAL,ELECTRICAL_SHA,false,"electrical-");
    }
    @AfterAll static void releaseReferenceMaps(){REFERENCES.clear();}
    private static void readReferences(Path archive,String hash,boolean flat)throws Exception {
        readReferences(archive,hash,flat,"");
    }
    private static void readReferences(Path archive,String hash,boolean flat,String prefix)throws Exception {
        assertEquals(hash,sha(Files.readAllBytes(archive)));
        try(var zip=new ZipFile(archive.toFile())) {
            var manifest=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("references.json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            for(var e:manifest.getAsJsonObject("files").entrySet())assertEquals(e.getValue().getAsString(),sha(zip.getInputStream(zip.getEntry(e.getKey())).readAllBytes()));
            for(var e:manifest.getAsJsonObject("profiles").entrySet()) {
                var p=e.getValue().getAsJsonObject();String entry=p.get("entry").getAsString(),dir=entry.substring(0,entry.lastIndexOf('/')+1);
                byte[] raw=zip.getInputStream(zip.getEntry(entry)).readAllBytes();assertArrayEquals(raw,zip.getInputStream(zip.getEntry(entry.replace("-a-part","-b-part"))).readAllBytes());
                var rows=csv(raw);assertEquals(p.get("rows").getAsInt(),rows.size());int duplicates=0;double previous=Double.NEGATIVE_INFINITY;
                for(var row:rows){double time=row.get("time_s");assertTrue(time>=previous);if(time==previous)duplicates++;previous=time;assertEquals(row.get("V2"),row.get("VAR0"),0);}
                assertEquals(flat?2:4,duplicates);assertTrue(rows.getFirst().get("time_s")<0);
                var metadata=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry(dir+"manifest.json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                REFERENCES.put(prefix+(flat?"preparation-":"")+e.getKey(),new Reference(p.get("step_sec").getAsDouble(),flat?0:metadata.getAsJsonObject("settings").get("fault_x_pu").getAsDouble(),metadata.getAsJsonArray("initialized_constants").get(4).getAsDouble(),
                        new String(zip.getInputStream(zip.getEntry(dir+"dynamic.dyr")).readAllBytes(),StandardCharsets.UTF_8),rows.stream().filter(r->r.get("time_s")>=0).toList()));
            }
        }
    }
    private static Loaded load(String dyr,String raw) throws Exception {
        Path out=Files.createTempDirectory(Path.of("target"),"acmtblu1-import-");Files.writeString(out.resolve("network.raw"),raw);Files.writeString(out.resolve("dynamic.dyr"),dyr);
        var loader=new PSSEMultiFileLoader();var context=loader.loadDStab(out.resolve("network.raw").toString(),out.resolve("dynamic.dyr").toString());return new Loaded(loader,context);
    }
    private static LD1PACImpl motor(Loaded loaded) { return assertInstanceOf(LD1PACImpl.class,loaded.context.getDStabilityNet().getDStabBus("Bus2").getDynLoadModelList().getFirst()); }
    @Test void exactCatalogAndEveryConstantAreBoundWithoutUsingLoadingAsCoverage() throws Exception {
        assertEquals(42,DynamicModelCatalog.find("ACMTBLU1").orElseThrow().parameterCount());
        var loaded=load(Files.readString(DYR),Files.readString(RAW));assertTrue(loaded.loader.getLastDynamicImportReport().isStrictlyComplete());var m=motor(loaded);
        String[] getters={"Tstall","Trst","Tv","Tf","LoadFactor","PowerFactor","Vstall","Rstall","Xstall","LFadj","Kp1","Np1","Kq1","Nq1","Kp2","Np2","Kq2","Nq2","Vbrk","Frst","Vrst","CmpKpf","CmpKqf","Vc1off","Vc2off","Vc1on","Vc2on","Tth","Th1t","Th2t","Fuvr","UVtr1","Ttr1","UVtr2","Ttr2"};
        String text=Files.readString(DYR).replace("\r\n","\n");String values=text.substring(text.indexOf("16\n")+3).replace("/","").strip();String[] fields=values.split("\\s+");assertEquals(35,fields.length);
        for(int i=0;i<getters.length;i++)assertEquals(Double.parseDouble(fields[i]),((Number)m.getClass().getMethod("get"+getters[i]).invoke(m)).doubleValue(),0,getters[i]);
        assertEquals(100,m.getLoadPercent(),0);assertSame(loaded.context.getDStabilityNet().getDStabBus("Bus2").getContributeLoad("1"),m.getParentLoad());
        assertEquals(LD1PACImpl.AcceptedTimerPrecision.BINARY32,m.getAcceptedTimerPrecision());
        assertEquals(LD1PACImpl.AcceptedTimerPrecision.DOUBLE,new LD1PACImpl().getAcceptedTimerPrecision());
        assertEquals(LD1PACImpl.FrequencyInputMode.TERMINAL_PHASE_BINARY32,m.getFrequencyInputMode());
        assertEquals(LD1PACImpl.ElectricalInputMode.ACCEPTED_ENDPOINT,m.getElectricalInputMode());
        assertEquals(9,m.staggeredStateCount());assertEquals(9,m.getIntegrationStates().size());
    }
    @ParameterizedTest @ValueSource(strings={"short","long","allocation","missing","nonfinite"})
    void malformedOrUnownedRecordsCannotAttachPartialModels(String kind) throws Exception {
        String d=Files.readString(DYR);
        d=switch(kind){case "short"->d.replace("0.035 0 0.12 /","0.035 0 /");case "long"->d.replace("0.035 0 0.12 /","0.035 0 0.12 7 /");case "allocation"->d.replace("35 9 29 16","35 9 29 17");case "missing"->d.replace("'USRLOD' '1'","'USRLOD' '9'");default->d.replace("0.82 0.95","NaN 0.95");};
        var loaded=load(d,Files.readString(RAW));assertFalse(loaded.loader.getLastDynamicImportReport().isStrictlyComplete());assertTrue(loaded.context.getDStabilityNet().getDStabBus("Bus2").getDynLoadModelList().isEmpty());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void wildcardLoadsOwnTheirSolvedPowerAndRemainFlat(boolean shared) throws Exception {
        String raw=Files.readString(RAW).replace("2,'1',1,1,1,40.0,10.0,0,0,0,0,1,1,0","2,'1',1,1,1,40.0,10.0,0,0,0,0,1,1,0\n2,'2',1,1,1,20.0,7.0,0,0,0,0,1,1,0\n2,'3',0,1,1,30.0,10.0,0,0,0,0,1,1,0");
        var loaded=load(Files.readString(DYR).replace("'USRLOD' '1'","'USRLOD' '*'"),raw);assertTrue(loaded.loader.getLastDynamicImportReport().isStrictlyComplete());
        var a=loaded.context.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuStepSec(.0005);a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        if(shared)a.setSolver(new StaggeredEulerSolver(a));assertTrue(a.initialization());var bus=loaded.context.getDStabilityNet().getDStabBus("Bus2");assertEquals(2,bus.getDynLoadModelList().size());
        double v=bus.getVoltageMag();Complex total=Complex.ZERO;
        for(var item:bus.getDynLoadModelList()){var m=assertInstanceOf(LD1PACImpl.class,item);assertEquals(m.getParentLoad().getLoad(v),m.getInitLoadPQ());total=total.add(m.getInitLoadPQ());assertThrows(IllegalStateException.class,()->m.setParentLoad(m.getParentLoad()));assertThrows(IllegalStateException.class,()->m.setAcceptedTimerPrecision(LD1PACImpl.AcceptedTimerPrecision.DOUBLE));}
        assertEquals(.6,total.getReal(),1e-12);assertEquals(.17,total.getImaginary(),1e-12);
        while(a.getSimuTime()<.05-.00025)assertTrue(a.solveDEqnStep(true));assertEquals(v,bus.getVoltageMag(),2e-7);
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6})
    void everyAllocationFieldIsCheckedBeforeAttachment(int index)throws Exception {
        String original="12 1 0 35 9 29 16";String[] fields=original.split(" ");fields[index]=Integer.toString(Integer.parseInt(fields[index])+1);
        var loaded=load(Files.readString(DYR).replace(original,String.join(" ",fields)),Files.readString(RAW));
        assertFalse(loaded.loader.getLastDynamicImportReport().isStrictlyComplete());assertTrue(loaded.context.getDStabilityNet().getDStabBus("Bus2").getDynLoadModelList().isEmpty());
    }
    @Test void duplicateRecordsCannotCreateASecondOwner()throws Exception {
        String source=Files.readString(DYR);var loaded=load(source+source.substring(source.indexOf("2 'USRLOD'")),Files.readString(RAW));
        assertFalse(loaded.loader.getLastDynamicImportReport().isStrictlyComplete());assertEquals(1,loaded.context.getDStabilityNet().getDStabBus("Bus2").getDynLoadModelList().size());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void standaloneAndCompositeOwnershipConflictsAreRejectedInEitherOrder(boolean compositeFirst)throws Exception {
        String standalone=Files.readString(DYR),composite=Files.readString(Path.of("testData/adpter/psse/v33/HspModels/cmldblu1-restart.dyr"));
        String a=standalone.substring(standalone.indexOf("2 'USRLOD'")),b=composite.substring(composite.indexOf("2 'USRLOD'"));
        var loaded=load(standalone.substring(0,standalone.indexOf("2 'USRLOD'"))+(compositeFirst?b+a:a+b),Files.readString(RAW));
        assertFalse(loaded.loader.getLastDynamicImportReport().isStrictlyComplete());var bus=loaded.context.getDStabilityNet().getDStabBus("Bus2");
        assertEquals(compositeFirst?0:1,bus.getDynLoadModelList().size());assertEquals(compositeFirst,bus.getInfoOnlyDynModel()!=null);
    }
    static Stream<Arguments> profiles() { Assumptions.assumeFalse(REFERENCES.isEmpty(),"Retain the private independent compressor captures");return REFERENCES.keySet().stream().flatMap(p->Stream.of(false,true).map(s->Arguments.of(p,s))); }
    record RunSettings(int startupSteps,boolean holdInitialEndpoint,double networkTolerance,boolean generatorStates) {
        RunSettings {if(startupSteps<0||!Double.isFinite(networkTolerance)||networkTolerance<=0)throw new IllegalArgumentException("Invalid compressor SMIB run settings");}
    }
    private static final class Recorder {
        final DynamicSimuAlgorithm a;final List<Map<String,Double>> rows=new ArrayList<>(),published=new ArrayList<>();final boolean events;boolean enabled,holdInitialEndpoint;
        final boolean generatorStatesEnabled;
        Recorder(DynamicSimuAlgorithm a,boolean events,boolean generatorStates){this.a=a;this.events=events;generatorStatesEnabled=generatorStates;}
        void before(double t){if(enabled&&(event(t)||holdInitialEndpoint))capture(t);}
        void capture(double t){rows.add(snapshot(t));}
        void commit(double t){published.add(snapshot(t));}
        // The flat control also retains the two pause/resume observations.
        boolean event(double t){return Math.abs(t-.05)<1e-9||Math.abs(t-.1)<1e-9||events&&(Math.abs(t-.2)<1e-9||Math.abs(t-.25)<1e-9);}
        Map<String,Double> snapshot(double t){var n=a.getNetwork();var r=new LinkedHashMap<String,Double>();r.put("time_s",t);for(int i=1;i<=3;i++){var v=n.getDStabBus("Bus"+i).getVoltage();r.put("V"+i,v.abs());r.put("A"+i,Math.toDegrees(v.getArgument()));}
            var m=n.getMachine("Bus1-mach1");var v=m.getDStabBus().getVoltage();var p=v.multiply(m.getIgen().subtract(v.multiply(m.getYgen())).conjugate());r.put("P_PU",p.getReal());r.put("Q_PU",p.getImaginary());r.put("ANGLE_DEG",Math.toDegrees(m.getAngle()));r.put("SPEED",m.getSpeed()-1);
            if(generatorStatesEnabled){
                var generatorStates=((org.interpss.dstab.mach.IeeeVcRoundRotorMachine)m).getNamedStates();
                String[] keys={"E'q","E'd","Psi'd","Psi'q","Speed deviation","Angle"};
                for(int i=0;i<keys.length;i++)r.put("GEN_STATE"+i,generatorStates.get(keys[i]));
                r.put("GEN_EFD",m.getEfd());
            }
            var motor=(LD1PACImpl)n.getDStabBus("Bus2").getDynLoadModelList().getFirst();r.putAll(motor.getNamedStates());var history=motor.getIntegrationHistories();r.putAll(history);r.put("MotorCurrentA",Math.sqrt(history.get("ElectricalHeatingSquaredA")));r.put("MotorCurrentB",Math.sqrt(history.get("ElectricalHeatingSquaredB")));return r;}
    }
    private static final class Ordinary extends DefaultDynamicSimulationSolver {
        final Recorder r;Ordinary(DynamicSimuAlgorithm a,Recorder r,double tolerance){super(a);this.r=r;converge_tol=tolerance;}
        @Override public void beforeStep(double t)throws DStabSimuException{r.before(t);super.beforeStep(t);}
        @Override public boolean networkSolutionStep()throws DStabSimuException{boolean ok=super.networkSolutionStep();if(ok&&r.enabled&&"INITIAL".equals(networkSolutionStage)){r.capture(dstabAlgo.getSimuTime());if(r.event(dstabAlgo.getSimuTime()))r.commit(dstabAlgo.getSimuTime());}return ok;}
        @Override public void afterStep(double t)throws DStabSimuException{super.afterStep(t);if(r.enabled)r.commit(t);}
    }
    private static final class Shared extends StaggeredEulerSolver {
        final Recorder r;Shared(DynamicSimuAlgorithm a,Recorder r,boolean hold,double tolerance){super(a);this.r=r;r.holdInitialEndpoint=hold;setReuseNetworkEndpoint(hold);converge_tol=tolerance;}
        @Override public void beforeStep(double t)throws DStabSimuException{r.before(t);super.beforeStep(t);}
        @Override public boolean networkSolutionStep()throws DStabSimuException{boolean ok=super.networkSolutionStep();if(ok&&r.enabled&&"INITIAL".equals(networkSolutionStage)){if(!r.holdInitialEndpoint||r.event(dstabAlgo.getSimuTime()))r.capture(dstabAlgo.getSimuTime());if(r.event(dstabAlgo.getSimuTime()))r.commit(dstabAlgo.getSimuTime());}return ok;}
        @Override public void afterStep(double t)throws DStabSimuException{super.afterStep(t);if(r.enabled)r.commit(t);}
    }
    private static double networkTolerance() {
        double value=Double.parseDouble(CONFIG.string("network.tolerance","1.0e-4"));
        if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException("Invalid compressor SMIB network tolerance");
        return value;
    }
    private static Map<String,Object> error(List<Map<String,Double>> source,List<Map<String,Double>> actual,String key,String observed,double offset){double sum=0,referenceSquare=0,max=0,time=0,min=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
        for(int i=0;i<source.size();i++){double expected=source.get(i).get(key),delta=actual.get(i).get(observed)+offset-expected;assertTrue(Double.isFinite(delta));sum+=delta*delta;referenceSquare+=expected*expected;min=Math.min(min,expected);high=Math.max(high,expected);if(Math.abs(delta)>max){max=Math.abs(delta);time=source.get(i).get("time_s");}}
        double rmse=Math.sqrt(sum/source.size()),relative=referenceSquare==0?(sum==0?0:Double.POSITIVE_INFINITY):Math.sqrt(sum/referenceSquare);
        return Map.of("rmse",rmse,"maximum_absolute",max,"source_time_of_maximum",time,"range_normalized_rmse",high>min?rmse/(high-min):rmse,"relative_rmse",relative);}
    private static void run(String name,boolean shared,Path out,boolean enforce)throws Exception {
        runReference(name,REFERENCES.get(name),shared,out,enforce);
    }
    static Map<String,Object> runReference(String name,Reference reference,boolean shared,Path out,boolean enforce)throws Exception {
        return runReference(name,reference,shared,out,enforce,0);
    }
    static Map<String,Object> runReference(String name,Reference reference,boolean shared,Path out,boolean enforce,int startupSteps)throws Exception {
        return runReference(name,reference,shared,out,enforce,startupSteps,false);
    }
    static Map<String,Object> runReference(String name,Reference reference,boolean shared,Path out,boolean enforce,int startupSteps,boolean holdInitialEndpoint)throws Exception {
        return runReference(name,reference,shared,out,enforce,new RunSettings(startupSteps,holdInitialEndpoint,networkTolerance(),false));
    }
    static Map<String,Object> runReference(String name,Reference reference,boolean shared,Path out,boolean enforce,RunSettings settings)throws Exception {
        int startupSteps=settings.startupSteps();boolean holdInitialEndpoint=settings.holdInitialEndpoint();
        assertTrue(startupSteps>=0);double origin=-startupSteps*reference.h;
        assertEquals(origin,reference.rows.getFirst().get("time_s"),1e-9,"Explicit startup configuration must match the retained reference history");
        var loaded=load(reference.dyr,Files.readString(RAW));assertTrue(loaded.loader.getLastDynamicImportReport().isStrictlyComplete());var a=loaded.context.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuStepSec(reference.h);a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());var recorder=new Recorder(a,reference.fault>0,settings.generatorStates());a.setSolver(shared?new Shared(a,recorder,holdInitialEndpoint,settings.networkTolerance()):new Ordinary(a,recorder,settings.networkTolerance()));
        if(reference.fault>0)for(double t:new double[]{.05,.2})a.getNetwork().addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",a.getNetwork(),SimpleFaultCode.GROUND_3P,new Complex(0,reference.fault),null,t,.05),"fault-"+t);
        assertTrue(a.initialization());var m=motor(loaded);var first=reference.rows.getFirst();assertEquals(reference.loading,m.getLoadFactor(),1e-7);assertEquals(first.get("VAR26"),m.getMvaBase(),1e-4);assertEquals(first.get("VAR24"),m.getNamedStates().get("RunningP0"),2e-7);assertEquals(first.get("VAR25"),m.getNamedStates().get("RunningQ0"),2e-7);assertEquals(first.get("VAR27"),m.getVstall(),1e-7);assertEquals(first.get("VAR28"),m.getVbrk(),1e-7);
        a.setSimuTime(origin);recorder.commit(origin);recorder.enabled=true;while(a.getSimuTime()<.5-reference.h/2)assertTrue(a.solveDEqnStep(true));recorder.capture(a.getSimuTime());assertEquals(reference.rows.size(),recorder.rows.size());assertEquals(reference.rows.size(),recorder.published.size());
        var errors=new LinkedHashMap<String,Object>();var polished=new LinkedHashMap<String,Object>();for(String key:List.of("V1","V2","V3","P_PU","Q_PU","ANGLE_DEG","SPEED")){errors.put(key,error(reference.rows,recorder.published,key,key,0));polished.put(key,error(reference.rows,recorder.rows,key,key,0));}
        var stateErrors=new LinkedHashMap<String,Object>();String[] states={"Vmeas","Fmeas","TemperatureA","TemperatureB","UvTimer1","UvTimer2","StallTimer","StallTimerB","RestartTimer"};for(int i=0;i<states.length;i++)stateErrors.put(states[i],error(reference.rows,recorder.published,"STATE"+i,states[i],i==1?-1:0));stateErrors.put("FrequencyOutput",error(reference.rows,recorder.published,"VAR1","FrequencyOutput",0));
        for(var pair:Map.of("VAR13","ElectricalVoltage","VAR19","MotorCurrentA","VAR20","MotorCurrentB").entrySet())stateErrors.put(pair.getValue(),error(reference.rows,recorder.published,pair.getKey(),pair.getValue(),0));
        for(var pair:Map.of("VAR10","UvFraction","VAR11","ContactorFraction","VAR12","ContactorClosed","VAR14","ThermalFractionA","VAR18","ThermalFractionB").entrySet())stateErrors.put(pair.getValue(),error(reference.rows,recorder.published,pair.getKey(),pair.getValue(),0));
        if(reference.rows.getFirst().containsKey("GEN_STATE0"))for(int i=0;i<6;i++)stateErrors.put("GEN_STATE"+i,error(reference.rows,recorder.published,"GEN_STATE"+i,"GEN_STATE"+i,0));
        Files.createDirectories(out);var report=new LinkedHashMap<String,Object>();report.put("status","coupled-compressor-control-frequency-and-protection-qualification-incomplete");report.put("observation_boundary","accepted history commit, before the next network repolish; event-side solves retained at the same grid time");report.put("profile",name);report.put("method",shared?"staggered":"modified-euler");report.put("startup_steps",startupSteps);report.put("network_tolerance",settings.networkTolerance());report.put("reuse_initial_network_endpoint",shared&&holdInitialEndpoint);report.put("initial_network_observation",shared&&holdInitialEndpoint?"held accepted endpoint":"fresh INITIAL network solve");report.put("boundary_errors",errors);report.put("repolished_boundary_errors",polished);report.put("state_errors",stateErrors);
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"),100000)){new GsonBuilder().setPrettyPrinting().create().toJson(report,w);}
        writeCsv(out.resolve("actual.csv"),recorder.published);writeCsv(out.resolve("repolished.csv"),recorder.rows);
        if(enforce)for(var e:errors.entrySet()){
            var metric=(Map<?,?>)e.getValue();boolean flat=reference.fault==0;String key=e.getKey();
            double limit=flat?(key.equals("ANGLE_DEG")?2e-5:key.equals("SPEED")?2e-8:2e-6):key.equals("ANGLE_DEG")?.005:key.startsWith("V")?6e-5:key.equals("P_PU")?6e-4:key.equals("Q_PU")?8e-5:8e-4;
            String norm=flat||key.equals("ANGLE_DEG")?"rmse":"range_normalized_rmse";assertTrue(((Number)metric.get(norm)).doubleValue()<=limit,key+": "+metric);
        }
        if(enforce)for(int i=0;i<reference.rows.size();i++)for(var pair:Map.of("VAR16","RunA","VAR17","RunB").entrySet())assertEquals(reference.rows.get(i).get(pair.getKey()),recorder.published.get(i).get(pair.getValue()),"Accepted run/stall action at the same source grid");
        if(enforce&&shared)for(String key:List.of("Vmeas","TemperatureA","TemperatureB"))assertTrue(((Number)((Map<?,?>)stateErrors.get(key)).get("maximum_absolute")).doubleValue()<=(key.equals("Vmeas")?2.5e-5:2.5e-6),key+": "+stateErrors.get(key));
        if(enforce&&shared)for(String key:List.of("UvTimer1","UvTimer2","StallTimer","StallTimerB","RestartTimer"))assertTrue(((Number)((Map<?,?>)stateErrors.get(key)).get("maximum_absolute")).doubleValue()<=2e-8,key+": "+stateErrors.get(key));
        return report;
    }
    private static void writeCsv(Path file,List<Map<String,Double>> rows)throws Exception{var keys=rows.getFirst().keySet();try(var w=BenchmarkOutputGuard.newWriter(file,BenchmarkOutputGuard.estimateCsvBytes(rows.size(),keys.size()))){w.write(String.join(",",keys)+"\n");for(var row:rows){boolean first=true;for(String key:keys){if(!first)w.write(",");first=false;w.write(row.get(key).toString());}w.write("\n");}}}
    @ParameterizedTest @MethodSource("profiles")
    void fullImportedCompressorFollowsTheIndependentCoupledControl(String name,boolean shared)throws Exception{run(name,shared,Files.createTempDirectory(Path.of("target"),"acmtblu1-coupled-"),true);}
    public static void main(String[] args)throws Exception{setup();Path out=Path.of(args[0]);for(String name:REFERENCES.keySet())for(boolean shared:new boolean[]{false,true})run(name,shared,out.resolve(name+(shared?"-staggered":"-modified-euler")),false);}
}
