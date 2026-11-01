package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.google.gson.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.dynLoad.impl.LD1PACImpl;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Independent sampled-phase/filter and running-current checks; not a substitute for coupled integration. */
public class CompressorFrequencyInputTest {
    private static final TestConfig CONFIG=TestConfig.load("/test-config/ipss.test.plugin.core.json","CompressorFrequencyInputTest");
    private static final Path ARCHIVE=Path.of(CONFIG.string("reference.archive","testData/private/verification-artifacts/acmtblu1-frequency-20261101/captures.zip"));
    private static final Path BASE=Path.of(CONFIG.string("reference.base.archive","testData/private/verification-artifacts/cmldblu1-current-refresh-20261031/captures.zip"));
    private static final Path RAW=Path.of("testData/adpter/psse/v33/HspModels/load-network.raw");
    private static final Map<String,Reference> REFERENCES=new TreeMap<>();
    private record Reference(double h,String dyr,List<Map<String,Double>> rows) { }
    private static String sha(byte[] raw)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
    @BeforeAll static void setup()throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        if(Files.isRegularFile(BASE))read(BASE,"8c11c1e85484c4be5d00a16fc9ea8b2220a52158336fd08374b1e743b2bd5157","base-");
        if(Files.isRegularFile(ARCHIVE))read(ARCHIVE,"6653c1edfeb7b2d62d8475f020616487110a160b0491a7c8cb324901b3fcdc68","");
    }
    @AfterAll static void releaseReferenceMaps(){REFERENCES.clear();}
    private static void read(Path archive,String hash,String prefix)throws Exception {
        assertEquals(hash,sha(Files.readAllBytes(archive)));
        try(var z=new ZipFile(archive.toFile())){
            var manifest=JsonParser.parseString(new String(z.getInputStream(z.getEntry("references.json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            for(var e:manifest.getAsJsonObject("files").entrySet())assertEquals(e.getValue().getAsString(),sha(z.getInputStream(z.getEntry(e.getKey())).readAllBytes()));
            for(var e:manifest.getAsJsonObject("profiles").entrySet()){
                var p=e.getValue().getAsJsonObject();String entry=p.get("entry").getAsString();byte[] raw=z.getInputStream(z.getEntry(entry)).readAllBytes();assertArrayEquals(raw,z.getInputStream(z.getEntry(entry.replace("-a-part","-b-part"))).readAllBytes());
                String[] lines=new String(raw,StandardCharsets.UTF_8).strip().split("\\R"),keys=lines[0].split(",");assertEquals(51,keys.length);var rows=new ArrayList<Map<String,Double>>();
                for(int i=1;i<lines.length;i++){var row=new LinkedHashMap<String,Double>();String[] v=lines[i].split(",");assertEquals(keys.length,v.length);for(int k=0;k<keys.length;k++){double value=Double.parseDouble(v[k]);assertTrue(Double.isFinite(value));row.put(keys[k],value);}rows.add(row);}
                assertEquals(p.get("rows").getAsInt(),rows.size());assertTrue(rows.getFirst().get("time_s")<0);int duplicates=0;double previous=Double.NEGATIVE_INFINITY;
                for(var r:rows){double t=r.get("time_s");assertTrue(t>=previous);if(t==previous)duplicates++;previous=t;assertEquals(r.get("V2"),r.get("VAR0"),0);}assertEquals(4,duplicates);
                String directory=entry.substring(0,entry.lastIndexOf('/')+1);REFERENCES.put(prefix+e.getKey(),new Reference(p.get("step_sec").getAsDouble(),new String(z.getInputStream(z.getEntry(directory+"dynamic.dyr")).readAllBytes(),StandardCharsets.UTF_8),rows.stream().filter(r->r.get("time_s")>=0).toList()));
            }
        }
    }
    static Stream<Arguments> methods(){Assumptions.assumeFalse(REFERENCES.isEmpty(),"Retain independent compressor controls");return REFERENCES.keySet().stream().flatMap(n->Stream.of(false,true).map(s->Arguments.of(n,s)));}
    static Stream<Arguments> currents(){return methods().filter(a->{String n=(String)a.get()[0];return n.startsWith("running-sensitive-")||n.startsWith("tf025-sensitive-");});}
    private static Complex phase(Map<String,Double> r){return new Complex(r.get("VAR21"),r.get("VAR22"));}
    private static Complex boundary(Map<String,Double> r){double theta=Math.toRadians(r.get("A2"));return new Complex(r.get("V2")*Math.cos(theta),r.get("V2")*Math.sin(theta));}
    private static LD1PACImpl motor(Reference reference)throws Exception {
        Path directory=Files.createTempDirectory(Path.of("target"),"compressor-frequency-input-");Files.writeString(directory.resolve("dynamic.dyr"),reference.dyr);
        var loader=new PSSEMultiFileLoader();var c=loader.loadDStab(RAW.toString(),directory.resolve("dynamic.dyr").toString());assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());var a=c.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuStepSec(reference.h);a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(a.initialization());
        var bus=c.getDStabilityNet().getDStabBus("Bus2");var m=assertInstanceOf(LD1PACImpl.class,bus.getDynLoadModelList().getFirst());bus.setVoltage(phase(reference.rows.getFirst()));assertTrue(m.initStates());return m;
    }
    private static final class Error {
        double max,sum,time;int count;
        void add(double value,double t){sum+=value*value;count++;if(Math.abs(value)>max){max=Math.abs(value);time=t;}}
        Map<String,Object> report(){return Map.of("maximum_absolute",max,"rmse",Math.sqrt(sum/Math.max(1,count)),"source_time_of_maximum",time,"samples",count);}
    }
    private static void write(Path out,List<Map<String,Double>> rows,Map<String,Object> report)throws Exception {
        Files.createDirectories(out);try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"),100000)){new GsonBuilder().setPrettyPrinting().create().toJson(report,w);}
        var keys=rows.getFirst().keySet();try(var w=BenchmarkOutputGuard.newWriter(out.resolve("actual.csv"),BenchmarkOutputGuard.estimateCsvBytes(rows.size(),keys.size()))){w.write(String.join(",",keys)+"\n");for(var row:rows){boolean first=true;for(String key:keys){if(!first)w.write(",");first=false;w.write(row.get(key).toString());}w.write("\n");}}
    }
    private static void replay(String name,boolean shared,Path out,boolean enforce)throws Exception {
        var ref=REFERENCES.get(name);var m=motor(ref);var bus=m.getDStabBus();var first=new TreeMap<Integer,Map<String,Double>>();var events=new HashSet<Integer>();
        for(var r:ref.rows){int grid=(int)Math.round(r.get("time_s")/ref.h);if(first.putIfAbsent(grid,r)!=null)events.add(grid);}
        // Recover the explicitly declared binary32 clock, rather than differentiating
        // decimal CSV serialization errors over a submillisecond interval.
        var input=new double[first.size()];for(int i=1;i<input.length;i++)input[i]=Math.IEEEremainder(phase(first.get(i)).getArgument()-phase(first.get(i-1)).getArgument(),2*Math.PI)/(2*Math.PI*60*((double)first.get(i).get("time_s").floatValue()-(double)first.get(i-1).get("time_s").floatValue()));
        var rows=new ArrayList<Map<String,Double>>();var stateError=new Error();var outputError=new Error();double[] x=new double[4],rates=new double[4];int previousGrid=0;double previousRate=0,exact=0,envelope=0,maximumAnalyticExcess=0;
        for(var r:ref.rows){double time=r.get("time_s");int grid=(int)Math.round(time/ref.h);
            if(grid>previousGrid){assertEquals(previousGrid+1,grid);m.readStaggeredState(x,0);
                var oldStates=m.getIntegrationStates();var oldHistory=m.getIntegrationHistories();m.evaluateStaggered(ref.h,rates,0);m.evaluateStaggered(ref.h,rates,0);assertTrue(m.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,0));m.getNortonCurInj();assertEquals(oldStates,m.getIntegrationStates());assertEquals(oldHistory,m.getIntegrationHistories(),"Only accepted callbacks may advance phase memory");
                double tau=m.getTf();if(tau>1e-12){double a=ref.h/tau,decay=Math.exp(-a),stability=1-a+.5*a*a,u=input[previousGrid]*(events.contains(previousGrid)?.5:1);envelope=stability*envelope+Math.abs(stability-decay)*Math.abs(exact-u);exact=decay*exact+(1-decay)*u;}
                if(shared){m.evaluateStaggered(ref.h,rates,0);x[1]+=ref.h*(1.5*rates[1]-.5*previousRate);previousRate=rates[1];}
                else{assertTrue(m.nextStep(ref.h,DynamicSimuMethod.MODIFIED_EULER,0));bus.setVoltage(phase(r));assertTrue(m.nextStep(ref.h,DynamicSimuMethod.MODIFIED_EULER,1));x[1]=m.getMeasuredFrequency();}
                x[0]=r.get("STATE0");x[2]=r.get("STATE2");x[3]=r.get("STATE3");m.applyStaggeredState(x,0,0);if(shared)m.completeStaggeredStep(ref.h);bus.setVoltage(phase(r));assertTrue(m.afterStep(ref.h));previousGrid=grid;
                if(enforce&&tau<=1e-12)assertEquals(input[grid],m.getMeasuredFrequency()-1,3e-15,"Bypass must publish the declared phase derivative without another filter or fitted constant");
                if(!shared&&tau>1e-12){double excess=Math.abs(m.getMeasuredFrequency()-1-exact)-envelope;maximumAnalyticExcess=Math.max(maximumAnalyticExcess,excess);if(enforce)assertTrue(excess<=3e-9,"Modified Euler exceeded the analytic held-input truncation bound: "+excess);}
            }else if(!rows.isEmpty()){
                bus.setVoltage(boundary(r));m.getNortonCurInj();m.acceptNetworkEventInput(time);var state=m.getIntegrationStates();var history=m.getIntegrationHistories();m.acceptNetworkEventInput(time);assertEquals(state,m.getIntegrationStates());assertEquals(history,m.getIntegrationHistories());
            }
            assertEquals(time,m.getIntegrationHistories().get("FrequencySampleTime"),6e-13,"Prepared source clock is distinct from the nominal integration clock");
            double error=m.getMeasuredFrequency()-1-r.get("STATE1"),signal=m.getFrequencyOutput()-r.get("VAR1");stateError.add(error,time);outputError.add(signal,time);
            var row=new LinkedHashMap<String,Double>();row.put("source_time_s",time);row.put("source_state",r.get("STATE1"));row.put("source_output",r.get("VAR1"));row.put("state",m.getMeasuredFrequency()-1);row.put("output",m.getFrequencyOutput());row.put("state_error",error);row.put("output_error",signal);row.put("analytic_held_state",exact);row.put("analytic_truncation_bound",envelope);row.putAll(m.getIntegrationHistories());rows.add(row);
        }
        var report=new LinkedHashMap<String,Object>();report.put("status","recorded-phase-frequency-only-other-continuous-coordinates-fed-not-coupled-acceptance");report.put("profile",name);report.put("method",shared?"staggered":"modified-euler");report.put("step_sec",ref.h);report.put("filter_time_sec",m.getTf());report.put("source_state_error",stateError.report());report.put("source_output_error",outputError.report());report.put("analytic_bound_excess",maximumAnalyticExcess);write(out,rows,report);
        if(enforce&&(shared||m.getTf()<=1e-12)){
            // The raw bypass coordinate is unbounded at a phase discontinuity.
            // Its independent observation differs by up to 1.45e-6; retain that
            // error explicitly rather than fitting the phase conversion constant.
            // Filtered states and the physically applied limited output remain
            // gated separately at the tighter observation precision.
            assertTrue(stateError.max<=(m.getTf()<=1e-12?2e-6:2e-7),stateError.report().toString());
            assertTrue(outputError.max<=2e-7,outputError.report().toString());
        }
    }
    @ParameterizedTest @MethodSource("methods")
    void recordedPhaseQualifiesTheFilterAndBypassWithoutABusFrequencyFilter(String name,boolean shared)throws Exception{replay(name,shared,Files.createTempDirectory(Path.of("target"),"compressor-frequency-replay-"),true);}
    @ParameterizedTest @MethodSource("currents")
    void independentRunningAndStalledCurrentsUseThePublishedFrequencyFactors(String name,boolean shared)throws Exception {
        var ref=REFERENCES.get(name);var m=motor(ref);double[] x=new double[4],rates=new double[4];var errors=new Error[]{new Error(),new Error(),new Error(),new Error()};var rows=new ArrayList<Map<String,Double>>();int previous=0;
        for(var r:ref.rows){double time=r.get("time_s");int grid=(int)Math.round(time/ref.h);var v=boundary(r);m.getDStabBus().setVoltage(v);x[0]=r.get("STATE0");x[1]=r.get("VAR1");x[2]=r.get("STATE2");x[3]=r.get("STATE3");m.applyStaggeredState(x,0,0);if(grid>previous){assertTrue(m.afterStep(ref.h));previous=grid;}
            assertEquals(r.get("VAR16"),m.getIntegrationHistories().get("RunA"));assertEquals(r.get("VAR17"),m.getIntegrationHistories().get("RunB"));
            var y=m.getPosSeqEquivY().subtract(m.getCompensateShuntY());var current=v.multiply(y).subtract(m.getNortonCurInj());var power=v.multiply(current.conjugate()).multiply(m.getDStabBus().getNetwork().getBaseMva()/m.getMvaBase());
            if(shared)m.evaluateStaggered(ref.h,rates,0);else{assertTrue(m.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,0));rates[2]=m.getStateDerivativeSnapshot().rates().get("TemperatureA");rates[3]=m.getStateDerivativeSnapshot().rates().get("TemperatureB");}
            double[] diff={power.getReal()-r.get("VAR2")/r.get("VAR26"),power.getImaginary()-(r.get("VAR3")/r.get("VAR26")-r.get("VAR15")*v.abs()*v.abs()),rates[2]-(m.getRstall()*Math.pow(r.get("VAR19"),2)-r.get("STATE2"))/m.getTth(),rates[3]-(m.getRstall()*Math.pow(r.get("VAR20"),2)-r.get("STATE3"))/m.getTth()};
            var row=new LinkedHashMap<String,Double>();row.put("source_time_s",time);for(int i=0;i<diff.length;i++){errors[i].add(diff[i],time);row.put("error_"+i,diff[i]);}rows.add(row);
        }
        var report=new LinkedHashMap<String,Object>();report.put("status","recorded-voltage-and-frequency-current-heating-only");report.put("profile",name);report.put("method",shared?"staggered":"modified-euler");for(int i=0;i<errors.length;i++)report.put("error_"+i,errors[i].report());write(Files.createTempDirectory(Path.of("target"),"compressor-frequency-current-"),rows,report);
        for(int i=0;i<errors.length;i++)assertTrue(errors[i].max<=(i<2?2e-6:2e-7),errors[i].report().toString());
    }
    private static Reference example(){Assumptions.assumeFalse(REFERENCES.isEmpty());return REFERENCES.values().iterator().next();}
    @Test void phaseWrapEventsAndPureEvaluationsRespectHistoryOwnership()throws Exception {
        var m=motor(example());var bus=m.getDStabBus();double amplitude=bus.getVoltageMag();bus.setVoltage(new Complex(amplitude*Math.cos(Math.toRadians(179)),amplitude*Math.sin(Math.toRadians(179))));assertTrue(m.initStates());bus.setVoltage(new Complex(amplitude*Math.cos(Math.toRadians(-179)),amplitude*Math.sin(Math.toRadians(-179))));m.completeStaggeredStep(.0005);assertTrue(m.afterStep(.0005));var h=m.getIntegrationHistories();double interval=h.get("FrequencySampleTime");assertEquals(Math.toRadians(2)/(2*Math.PI*60*interval),h.get("FrequencySampleDeviation"),1e-12);
        m.acceptNetworkEventInput(.0005);var state=m.getIntegrationStates();h=m.getIntegrationHistories();m.acceptNetworkEventInput(.0005);double[] rates=new double[4];for(int i=0;i<5;i++){m.evaluateStaggered(.0005,rates,0);m.getNortonCurInj();assertTrue(m.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,0));}assertEquals(state,m.getIntegrationStates());assertEquals(h,m.getIntegrationHistories());assertThrows(IllegalArgumentException.class,()->m.acceptNetworkEventInput(0));assertThrows(IllegalArgumentException.class,()->m.acceptNetworkEventInput(Double.NaN));assertThrows(IllegalStateException.class,()->m.setFrequencyInputMode(LD1PACImpl.FrequencyInputMode.BUS_FREQUENCY));
        assertTrue(m.initStates());assertEquals(0,m.getIntegrationHistories().get("FrequencySampleTime"));assertEquals(0,m.getIntegrationHistories().get("FrequencyEventSeen"));assertEquals(0,m.getIntegrationHistories().get("FrequencyEventPending"));assertEquals(1,m.getMeasuredFrequency());
    }
    @Test void liveReactiveGainChangesDoNotUseAStaleNormalization()throws Exception {
        var m=motor(example());m.setCmpKqf(0);m.setPowerFactor(1);assertThrows(IllegalArgumentException.class,()->m.setCmpKqf(-2.7));assertEquals(0,m.getCmpKqf());m.setPowerFactor(.95);m.setCmpKqf(-2.7);assertEquals(-2.7,m.getCmpKqf());assertThrows(IllegalArgumentException.class,()->m.setPowerFactor(1));assertEquals(.95,m.getPowerFactor());
    }
    public static void main(String[] args)throws Exception{setup();Path out=Path.of(args[0]);for(String n:REFERENCES.keySet())for(boolean shared:new boolean[]{false,true})replay(n,shared,out.resolve(n+(shared?"-staggered":"-modified-euler")),false);}
}
