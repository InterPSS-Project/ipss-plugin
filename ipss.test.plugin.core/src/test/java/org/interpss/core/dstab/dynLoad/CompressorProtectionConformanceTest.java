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
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.dynLoad.impl.LD1PACImpl;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Independent UV/contactor replay and coupled controls; thermal qualification remains separate. */
public class CompressorProtectionConformanceTest {
    private static final TestConfig CONFIG=TestConfig.load("/test-config/ipss.test.plugin.core.json","CompressorProtectionConformanceTest");
    private static final Path ARCHIVE=Path.of(CONFIG.string("reference.archive",
            "testData/private/verification-artifacts/acmtblu1-protection-20261101/accepted.zip"));
    private static final String ARCHIVE_SHA="f2464f2c2cd7c99cbc4f78ba5be6d298ae3904f75744963b853c8bd3f012c794";
    private static final Path RAW=Path.of("testData/adpter/psse/v33/HspModels/load-network.raw");
    private static final Map<String,Acmtblu1SharedSmibTest.Reference> REFERENCES=new TreeMap<>();

    private static String sha(byte[] bytes)throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    @BeforeAll static void setup()throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain the independent protection controls");
        assertEquals(ARCHIVE_SHA,sha(Files.readAllBytes(ARCHIVE)));
        try(var z=new ZipFile(ARCHIVE.toFile())){
            var manifest=JsonParser.parseString(new String(z.getInputStream(z.getEntry("references.json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            for(var e:manifest.getAsJsonObject("files").entrySet())
                assertEquals(e.getValue().getAsString(),sha(z.getInputStream(z.getEntry(e.getKey())).readAllBytes()));
            for(var e:manifest.getAsJsonObject("profiles").entrySet()){
                if(!e.getKey().startsWith("uv-")&&!e.getKey().startsWith("contactor-"))continue;
                var p=e.getValue().getAsJsonObject();String entry=p.get("entry").getAsString();
                byte[] bytes=z.getInputStream(z.getEntry(entry)).readAllBytes();
                assertArrayEquals(bytes,z.getInputStream(z.getEntry(entry.replace("-a-part","-b-part"))).readAllBytes());
                String[] lines=new String(bytes,StandardCharsets.UTF_8).strip().split("\\R"),keys=lines[0].split(",");
                assertEquals(51,keys.length);var rows=new ArrayList<Map<String,Double>>();
                double previous=Double.NEGATIVE_INFINITY;int duplicates=0;
                for(int i=1;i<lines.length;i++){
                    String[] fields=lines[i].split(",");assertEquals(keys.length,fields.length);var row=new LinkedHashMap<String,Double>();
                    for(int j=0;j<keys.length;j++){double v=Double.parseDouble(fields[j]);assertTrue(Double.isFinite(v));row.put(keys[j],v);}
                    double time=row.get("time_s");assertTrue(time>=previous);if(time==previous)duplicates++;previous=time;
                    assertEquals(row.get("V2"),row.get("VAR0"),0);rows.add(row);
                }
                assertEquals(p.get("rows").getAsInt(),rows.size());assertTrue(rows.getFirst().get("time_s")<0);assertEquals(4,duplicates);
                String dir=entry.substring(0,entry.lastIndexOf('/')+1);
                var metadata=JsonParser.parseString(new String(z.getInputStream(z.getEntry(dir+"manifest.json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                String dyr=new String(z.getInputStream(z.getEntry(dir+"dynamic.dyr")).readAllBytes(),StandardCharsets.UTF_8);
                REFERENCES.put(e.getKey(),new Acmtblu1SharedSmibTest.Reference(p.get("step_sec").getAsDouble(),
                        metadata.getAsJsonObject("settings").get("fault_x_pu").getAsDouble(),
                        metadata.getAsJsonArray("initialized_constants").get(4).getAsDouble(),dyr,
                        rows.stream().filter(r->r.get("time_s")>=0).toList()));
            }
        }
        assertEquals(12,REFERENCES.size(),"UV/contactor qualification is distinct from retained thermal diagnostics");
    }
    @AfterAll static void release(){REFERENCES.clear();}
    static Stream<Arguments> methods(){return REFERENCES.keySet().stream().flatMap(n->Stream.of(false,true).map(s->Arguments.of(n,s)));}
    private static Complex voltage(Map<String,Double> row){double angle=Math.toRadians(row.get("A2"));return new Complex(row.get("V2")*Math.cos(angle),row.get("V2")*Math.sin(angle));}

    private static final class Error {
        double maximum,sum,time;int samples;
        void add(double difference,double sourceTime){sum+=difference*difference;samples++;if(Math.abs(difference)>maximum){maximum=Math.abs(difference);time=sourceTime;}}
        Map<String,Object> report(){return Map.of("maximum_absolute",maximum,"rmse",Math.sqrt(sum/Math.max(1,samples)),"source_time_of_maximum",time,"samples",samples);}
    }
    private static void write(Path out,List<Map<String,Double>> rows,Map<String,Object> report)throws Exception {
        Files.createDirectories(out);
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"),100000)){new GsonBuilder().setPrettyPrinting().create().toJson(report,w);}
        var keys=rows.getFirst().keySet();
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("actual.csv"),BenchmarkOutputGuard.estimateCsvBytes(rows.size(),keys.size()))){
            w.write(String.join(",",keys)+"\n");for(var r:rows){boolean first=true;for(String key:keys){if(!first)w.write(",");first=false;w.write(r.get(key).toString());}w.write("\n");}
        }
    }
    private static LD1PACImpl motor(Acmtblu1SharedSmibTest.Reference ref)throws Exception {
        Path dir=Files.createTempDirectory(Path.of("target"),"compressor-protection-input-");Files.writeString(dir.resolve("dynamic.dyr"),ref.dyr());
        var loader=new PSSEMultiFileLoader();var c=loader.loadDStab(RAW.toString(),dir.resolve("dynamic.dyr").toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());var a=c.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());
        var m=assertInstanceOf(LD1PACImpl.class,c.getDStabilityNet().getDStabBus("Bus2").getDynLoadModelList().getFirst());
        m.setFrequencyInputMode(LD1PACImpl.FrequencyInputMode.BUS_FREQUENCY);a.setSimuStepSec(ref.h());
        a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(a.initialization());return m;
    }

    @ParameterizedTest @MethodSource("methods")
    void recordedStatesQualifyProtectionHistoriesAndCurrents(String name,boolean shared)throws Exception {
        var ref=REFERENCES.get(name);var m=motor(ref);var bus=m.getDStabBus();
        String[][] fields={{"VAR10","UvFraction"},{"VAR11","ContactorFraction"},{"VAR12","ContactorClosed"},
                {"STATE4","UvTimer1"},{"STATE5","UvTimer2"},{"VAR16","RunA"},{"VAR17","RunB"}};
        var errors=new LinkedHashMap<String,Error>();for(var f:fields)errors.put(f[1],new Error());
        for(String key:List.of("P","Q","MotorCurrentA","MotorCurrentB"))errors.put(key,new Error());
        var timers=new CompressorTimerReplay(m,shared);double[] rates=new double[m.staggeredStateCount()];int previous=0;var rows=new ArrayList<Map<String,Double>>();
        for(var source:ref.rows()){
            double time=source.get("time_s");int grid=(int)Math.round(time/ref.h());var v=voltage(source);
            boolean advance=grid>previous,event=!advance&&!rows.isEmpty();
            if(event)timers.beforeEvent(ref.h());
            double[] x=advance?timers.advance(ref.h()):timers.current();
            bus.setVoltage(v);bus.setFreq(source.get("VAR1"));
            x[0]=source.get("STATE0");x[1]=source.get("VAR1");x[2]=source.get("STATE2");x[3]=source.get("STATE3");m.applyStaggeredState(x,0,0);
            if(grid>previous){assertEquals(previous+1,grid);m.completeStaggeredStep(ref.h());assertTrue(m.afterStep(ref.h()));previous=grid;}
            else if(event){timers.acceptEvent(time);var history=m.getIntegrationHistories();m.acceptNetworkEventInput(time);assertEquals(history,m.getIntegrationHistories());}
            var state=m.getIntegrationStates();var history=m.getIntegrationHistories();
            var current=v.multiply(m.getPosSeqEquivY()).subtract(m.getNortonCurInj());
            var power=v.multiply(current.conjugate()).multiply(bus.getNetwork().getBaseMva()/m.getMvaBase());
            if(shared)m.evaluateStaggered(ref.h(),rates,0);else assertTrue(m.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,0));
            assertTrue(m.afterStep(0));assertEquals(state,m.getIntegrationStates());assertEquals(history,m.getIntegrationHistories());
            var row=new LinkedHashMap<String,Double>();row.put("source_time_s",time);
            for(var f:fields){double expected=source.get(f[0]),actual=history.get(f[1]);errors.get(f[1]).add(actual-expected,time);row.put("source_"+f[1],expected);row.put(f[1],actual);
                if(f[1].startsWith("UvTimer")){
                    boolean firstAfterEvent=false;for(double boundary:new double[]{.05,.1,.2,.25})firstAfterEvent|=grid==(int)Math.round(boundary/ref.h())+1;
                    double limit=!shared&&firstAfterEvent?.25*ref.h()+2e-9:2e-9;
                    assertEquals(expected,actual,limit,"Timer source-order difference is confined to the first sample after a network event");
                }
            }
            double[] actual={power.getReal(),power.getImaginary(),Math.sqrt(history.get("ElectricalHeatingSquaredA")),Math.sqrt(history.get("ElectricalHeatingSquaredB"))};
            double[] expected={source.get("VAR2")/source.get("VAR26"),source.get("VAR3")/source.get("VAR26"),source.get("VAR19"),source.get("VAR20")};
            String[] names={"P","Q","MotorCurrentA","MotorCurrentB"};for(int i=0;i<names.length;i++){errors.get(names[i]).add(actual[i]-expected[i],time);row.put("source_"+names[i],expected[i]);row.put(names[i],actual[i]);}
            assertEquals(source.get("VAR10")<1?1.:0.,history.get("UvTripped"));rows.add(row);
        }
        var report=new LinkedHashMap<String,Object>();report.put("status","recorded-continuous-state-UV-contactor-history-and-current-only-not-coupled-acceptance");
        report.put("profile",name);report.put("method",shared?"staggered":"modified-euler");for(var e:errors.entrySet())report.put(e.getKey(),e.getValue().report());
        write(Files.createTempDirectory(Path.of("target"),"compressor-protection-replay-"),rows,report);
        for(var e:errors.entrySet()){
            double limit=e.getKey().startsWith("UvTimer")?(shared?2e-9:.25*ref.h()+2e-9):List.of("RunA","RunB","ContactorClosed").contains(e.getKey())?0:2.5e-6;
            assertTrue(e.getValue().maximum<=limit,e.getKey()+": "+e.getValue().report());
        }
    }
    @ParameterizedTest @MethodSource("methods")
    void fullImportedCompressorQualifiesUvAndContactorTrajectories(String name,boolean shared)throws Exception {
        var report=Acmtblu1SharedSmibTest.runReference(name,REFERENCES.get(name),shared,
                Files.createTempDirectory(Path.of("target"),"compressor-protection-coupled-"),true);
        var errors=(Map<?,?>)report.get("state_errors");
        for(String key:List.of("UvFraction","ContactorFraction","ContactorClosed")){
            double limit=key.equals("ContactorClosed")?0:key.equals("UvFraction")?1e-7:5e-5;
            assertTrue(((Number)((Map<?,?>)errors.get(key)).get("maximum_absolute")).doubleValue()<=limit,key+": "+errors.get(key));
        }
    }
}
